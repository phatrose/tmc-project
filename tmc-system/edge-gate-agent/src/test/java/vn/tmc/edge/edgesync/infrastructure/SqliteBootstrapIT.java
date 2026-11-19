package vn.tmc.edge.edgesync.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.contracts.BootstrapResponse;
import vn.tmc.contracts.BootstrapResponse.*;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.application.BootstrapWorker;
import vn.tmc.edge.edgesync.application.CachedAccess;
import vn.tmc.edge.edgesync.domain.BootstrapCache;
import vn.tmc.edge.edgesync.domain.BootstrapPort.FetchResult;
import vn.tmc.edge.edgesync.domain.BootstrapProtocolException;

import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static vn.tmc.edge.edgesync.application.CachedAccess.Decision.*;
import static vn.tmc.edge.edgesync.domain.BootstrapCache.ApplyResult.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteEventStoreIT.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteSchemaIT.scalar;

class SqliteBootstrapIT {
    static final BootstrapCache.Identity IDENTITY = new BootstrapCache.Identity(EDGE_ID, FACTORY_ID);
    static final String KID = "qr-key-2026-11";
    private static final ECParameterSpec CURVE = curve();
    private static final UUID REVOCATION_ID = fixtureId(51);

    @TempDir
    Path tempDir;
    private final SqliteRecoveryIT.TestClock clock = new SqliteRecoveryIT.TestClock();

    @Test
    void fullBootstrap_atomicallyPersistsCompleteSnapshotAndRevision() throws Exception {
        var response = bundle(Mode.FULL, 0, 1, clock.instant(), List.of(revoke(RevocationKind.JTI, JTI.toString())));
        var cache = cache();
        assertEquals(APPLIED, cache.apply(response));
        var snapshot = cache.snapshot().orElseThrow();
        assertEquals(1, snapshot.revision());
        assertEquals(response.trustedUntil(), snapshot.trustedUntil());
        assertEquals(response.keySet(), snapshot.keySet());
        assertEquals(response.policy(), snapshot.policy());
        assertEquals(response.gates(), snapshot.gates());
        assertEquals(response.revocations(), snapshot.revocations());
        for (String table : List.of("cached_revocations", "trusted_signing_keys", "cached_access_policy", "cached_gates")) {
            assertEquals("1", read("SELECT count(*) FROM " + table));
        }
        assertEquals("1", read("SELECT bootstrap_revision FROM edge_runtime_state"));
    }

    @Test
    void firstBootstrap_afterLocalEventInitializesTrustWithoutResettingSequence() {
        var store = new SqliteStore(file(), clock);
        SqliteBatchIT.append(store, EDGE_ID, "existing local event");
        var cache = store.bootstrapCache(IDENTITY);
        assertTrue(cache.snapshot().isEmpty());
        assertEquals(APPLIED, cache.apply(full(clock.instant())));
        assertEquals(1, cache.snapshot().orElseThrow().revision());
        assertEquals(2, SqliteBatchIT.append(store, EDGE_ID, "after bootstrap").sequenceNo());
    }

    @Test
    void fullBootstrap_failureRollsBackAllCacheAndRevision() throws Exception {
        var cache = cache();
        installFault();
        assertThrows(EventStoreException.class, () -> cache.apply(full(clock.instant())));
        assertTrue(cache.snapshot().isEmpty());
        for (String table : List.of("cached_revocations", "trusted_signing_keys", "cached_access_policy",
                "cached_gates", "edge_runtime_state")) {
            assertEquals("0", read("SELECT count(*) FROM " + table));
        }
    }

    @Test
    void deltaWithMatchingBase_appliesRevocationsAndReplacesSnapshotsAtomically() {
        var cache = cache();
        cache.apply(bundle(Mode.FULL, 0, 1, clock.instant(), List.of(revoke(RevocationKind.JTI, JTI.toString()))));
        var priorTrust = cache.snapshot().orElseThrow().trustedUntil();
        clock.advance(Duration.ofHours(1));
        var next = bundle(Mode.DELTA, 1, 2, clock.instant(), List.of(
                new RevocationChange(fixtureId(52), Operation.UPSERT, RevocationKind.KID, "other", null, COMMITTED_AT)));
        cache.apply(next);
        var saved = cache.snapshot().orElseThrow();
        assertEquals(2, saved.revision());
        assertEquals(priorTrust, saved.trustedUntil());
        assertEquals(next.keySet(), saved.keySet());
        assertEquals(next.policy(), saved.policy());
        assertEquals(next.gates(), saved.gates());
        assertEquals(2, saved.revocations().size());
    }

    @Test
    void deltaWithWrongBase_appliesNothingAndRequestsFull() {
        var cache = cache();
        cache.apply(full(clock.instant()));
        var prior = cache.snapshot().orElseThrow();
        var wrong = bundle(Mode.DELTA, 2, 3, clock.instant(), List.of(revoke(RevocationKind.KID, KID)));
        assertEquals(FULL_REQUIRED, cache.apply(wrong));
        assertEquals(prior, cache.snapshot().orElseThrow());
        var requests = new ArrayList<Long>();
        var worker = new BootstrapWorker(revision -> {
            requests.add(revision);
            assertEquals(prior, cache.snapshot().orElseThrow());
            return revision == 0 ? fetched(bundle(Mode.FULL, 0, 3, clock.instant(), List.of())) : fetched(wrong);
        }, cache, clock);
        worker.refresh();
        assertEquals(List.of(1L, 0L), requests);
        assertEquals(3, cache.snapshot().orElseThrow().revision());
        assertTrue(cache.snapshot().orElseThrow().revocations().isEmpty());
    }

    @Test
    void revisionRollback_appliesNothing() {
        var cache = cache();
        cache.apply(bundle(Mode.FULL, 0, 2, clock.instant(), List.of()));
        var prior = cache.snapshot().orElseThrow();
        assertThrows(BootstrapProtocolException.class, () -> cache.apply(full(clock.instant())));
        assertEquals(prior, cache.snapshot().orElseThrow());
    }

    @Test
    void mismatchedBinding_appliesNothing() {
        var cache = cache();
        cache.apply(full(clock.instant()));
        var prior = cache.snapshot().orElseThrow();
        var response = bundle(Mode.FULL, 0, 2, clock.instant(), List.of());
        var wrong = new BootstrapResponse(response.mode(), response.baseRevision(), response.revision(),
                response.generatedAt(), response.trustedUntil(), response.factoryId(), UUID.randomUUID(),
                response.revocations(), response.keySet(), response.policy(), response.gates());
        assertThrows(BootstrapProtocolException.class, () -> cache.apply(wrong));
        assertEquals(prior, cache.snapshot().orElseThrow());
    }

    @Test
    void failedDelta_restartsWithPreviousCoherentRevisionAndTrust() throws Exception {
        var cache = cache();
        cache.apply(bundle(Mode.FULL, 0, 1, clock.instant(), List.of(revoke(RevocationKind.JTI, JTI.toString()))));
        var previous = cache.snapshot().orElseThrow();
        installFault();
        clock.advance(Duration.ofHours(1));
        var next = bundle(Mode.DELTA, 1, 2, clock.instant(), List.of(
                new RevocationChange(REVOCATION_ID, Operation.DELETE, RevocationKind.JTI, JTI.toString(), null, clock.instant())));
        assertThrows(EventStoreException.class, () -> cache.apply(next));
        var reopened = cache();
        assertEquals(previous, reopened.snapshot().orElseThrow());
        assertEquals(REVOKED, evaluation(reopened, JTI, COMMITTED_AT));
        assertEquals(ALLOW, evaluation(reopened, fixtureId(58), COMMITTED_AT));
    }

    @Test
    void http304_doesNotChangeTrustedUntilEvenAfterRestart() {
        var cache = cache();
        cache.apply(full(clock.instant()));
        var previous = cache.snapshot().orElseThrow();
        clock.advance(Duration.ofHours(10));
        var reopened = cache();
        new BootstrapWorker(after -> {
            assertEquals(1, after);
            return new FetchResult(true, 1, null);
        }, reopened, clock).refresh();
        assertEquals(previous, reopened.snapshot().orElseThrow());
    }

    @Test
    void renewalBelow36h_requestsFullAndRejects304ForFullRequest() {
        var cache = cache();
        cache.apply(full(clock.instant()));
        var previous = cache.snapshot().orElseThrow();
        clock.advance(Duration.ofHours(12).plusMillis(1));
        var malformed = new BootstrapWorker(after -> {
            assertEquals(0, after);
            return new FetchResult(true, 1, null);
        }, cache, clock);
        assertThrows(BootstrapProtocolException.class, malformed::refresh);
        assertEquals(previous, cache.snapshot().orElseThrow());
        new BootstrapWorker(after -> {
            assertEquals(0, after);
            return fetched(full(clock.instant()));
        }, cache, clock).refresh();
        assertEquals(clock.instant().plus(Duration.ofHours(48)), cache.snapshot().orElseThrow().trustedUntil());
    }

    @Test
    void offlineReady_requiresAtLeast24hRelevantTrust() {
        var cache = cache();
        cache.apply(full(clock.instant()));
        var access = access(cache);
        clock.advance(Duration.ofHours(24));
        assertTrue(access.readyForOffline24Hours(clock.instant()));
        clock.advance(Duration.ofMillis(1));
        assertFalse(access.readyForOffline24Hours(clock.instant()));
    }

    @Test
    void offlineReady_respectsEarlierKeyNotAfter() {
        var cache = cache();
        var keys = keySet(clock.instant(), KeyState.ACTIVE, clock.instant().plus(Duration.ofHours(23)));
        cache.apply(withKeys(full(clock.instant()), keys));
        assertFalse(access(cache).readyForOffline24Hours(clock.instant()));
        assertEquals(ALLOW, access(cache).lookupKey(KID, clock.instant()).decision());
    }

    @Test
    void revocationSub_respectsRevokedBefore() {
        var cache = cache();
        cache.apply(bundle(Mode.FULL, 0, 1, clock.instant(), List.of(revoke(RevocationKind.SUB, EMPLOYEE_ID.toString()))));
        assertEquals(REVOKED, evaluation(cache, JTI, OCCURRED_AT));
        assertEquals(ALLOW, evaluation(cache, JTI, OCCURRED_AT.plusMillis(1)));
    }

    @Test
    void revocationJti_matchesExactJti() {
        var cache = cache();
        cache.apply(bundle(Mode.FULL, 0, 1, clock.instant(), List.of(revoke(RevocationKind.JTI, JTI.toString()))));
        assertEquals(REVOKED, evaluation(cache, JTI, OCCURRED_AT));
        assertEquals(ALLOW, evaluation(cache, fixtureId(53), OCCURRED_AT));
    }

    @Test
    void revocationKid_matchesExactKid() {
        var cache = cache();
        cache.apply(bundle(Mode.FULL, 0, 1, clock.instant(), List.of(revoke(RevocationKind.KID, KID + "-old"))));
        assertEquals(ALLOW, evaluation(cache, JTI, OCCURRED_AT));
        cache.apply(bundle(Mode.FULL, 0, 2, clock.instant(), List.of(revoke(RevocationKind.KID, KID))));
        assertEquals(REVOKED, evaluation(cache, JTI, OCCURRED_AT));
    }

    @Test
    void revocationDelete_removesActiveEntry() {
        var cache = cache();
        cache.apply(bundle(Mode.FULL, 0, 1, clock.instant(), List.of(revoke(RevocationKind.JTI, JTI.toString()))));
        var delete = new RevocationChange(REVOCATION_ID, Operation.DELETE, RevocationKind.JTI, JTI.toString(), null, COMMITTED_AT);
        cache.apply(bundle(Mode.DELTA, 1, 2, clock.instant(), List.of(delete)));
        assertTrue(cache.snapshot().orElseThrow().revocations().isEmpty());
        assertEquals(ALLOW, evaluation(cache, JTI, OCCURRED_AT));
    }

    @Test
    void activeKey_canVerifyWithinLifetimeAndPolicyUsesHalfOpenWindow() {
        var cache = cache();
        var full = full(clock.instant());
        var ends = clock.instant().plus(Duration.ofHours(1));
        var policy = new PolicySnapshot(1, List.of(new EmployeePolicy(EMPLOYEE_ID, true,
                List.of(new PolicyWindow(GATE_ID, Direction.IN, clock.instant(), ends)))));
        cache.apply(new BootstrapResponse(full.mode(), 0, 1, full.generatedAt(), full.trustedUntil(),
                FACTORY_ID, EDGE_ID, List.of(), full.keySet(), policy, full.gates()));
        assertEquals(KeyState.ACTIVE, access(cache).lookupKey(KID, clock.instant()).key().state());
        assertEquals(ALLOW, evaluation(cache, JTI, OCCURRED_AT));
        clock.advance(Duration.ofHours(1));
        assertEquals(OUTSIDE_POLICY, evaluation(cache, JTI, OCCURRED_AT));
    }

    @Test
    void verifyOnlyKey_canVerifyButDoesNotEstablishFutureReadiness() {
        var cache = cache();
        cache.apply(withKeys(full(clock.instant()), keySet(clock.instant(), KeyState.VERIFY_ONLY,
                clock.instant().plus(Duration.ofHours(48)))));
        assertEquals(KeyState.VERIFY_ONLY, access(cache).lookupKey(KID, clock.instant()).key().state());
        assertFalse(access(cache).readyForOffline24Hours(clock.instant()));
    }

    @Test
    void revokedKey_isRejected() {
        var cache = cache();
        cache.apply(withKeys(full(clock.instant()), keySet(clock.instant(), KeyState.REVOKED,
                clock.instant().plus(Duration.ofHours(48)))));
        assertEquals(REVOKED, access(cache).lookupKey(KID, clock.instant()).decision());
    }

    @Test
    void expiredTrust_failsClosedAfterReopen() {
        cache().apply(full(clock.instant()));
        clock.advance(Duration.ofHours(48));
        assertEquals(CACHE_UNAVAILABLE, evaluation(cache(), JTI, OCCURRED_AT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPDATE cached_gates SET snapshot = X'00'", "DELETE FROM cached_access_policy",
            "UPDATE edge_runtime_state SET bootstrap_revision = 2"})
    void corruptOrIncompleteCache_failsClosed(String damage) throws Exception {
        cache().apply(full(clock.instant()));
        execute(damage);
        assertEquals(CACHE_UNAVAILABLE, evaluation(cache(), JTI, OCCURRED_AT));
        assertFalse(access(cache()).readyForOffline24Hours(clock.instant()));
    }

    @Test
    void unprovisionedFullFixture_isStoredButCannotAuthorizeOrReportReadiness() {
        var cache = cache();
        var full = full(clock.instant());
        cache.apply(new BootstrapResponse(Mode.FULL, 0, 1, full.generatedAt(), full.trustedUntil(),
                FACTORY_ID, EDGE_ID, List.of(), new KeySet(1, full.generatedAt(), full.trustedUntil(), List.of()),
                new PolicySnapshot(1, List.of()), List.of()));
        assertEquals(1, cache.snapshot().orElseThrow().revision());
        assertFalse(access(cache).readyForOffline24Hours(clock.instant()));
        assertEquals(UNKNOWN_KID, access(cache).lookupKey(KID, clock.instant()).decision());
    }

    @Test
    void unknownKid_deniesCurrentDecisionAndOnlySignalsBackgroundRefresh() {
        var cache = cache();
        cache.apply(full(clock.instant()));
        var requests = new ArrayList<Long>();
        var worker = new BootstrapWorker(after -> {
            requests.add(after);
            return fetched(full(clock.instant()));
        }, cache, clock);
        var access = new CachedAccess(cache, worker::requestKeyRefresh);
        assertEquals(UNKNOWN_KID, access.lookupKey("uncached-key", clock.instant()).decision());
        assertTrue(requests.isEmpty());
        worker.refresh();
        assertEquals(List.of(0L), requests);
    }

    private CachedAccess.Decision evaluation(BootstrapCache cache, UUID jti, Instant issued) {
        return access(cache).evaluate(EMPLOYEE_ID, jti, KID, issued, GATE_ID, clock.instant()).decision();
    }

    private static CachedAccess access(BootstrapCache cache) {
        return new CachedAccess(cache, () -> {});
    }

    static BootstrapResponse full(Instant at) {
        return bundle(Mode.FULL, 0, 1, at, List.of());
    }

    private static BootstrapResponse bundle(Mode mode, long base, long revision, Instant at, List<RevocationChange> revoked) {
        var trust = at.plus(Duration.ofHours(48));
        var keys = keySet(at, KeyState.ACTIVE, trust);
        var policy = new PolicySnapshot(revision, List.of(new EmployeePolicy(EMPLOYEE_ID, true,
                List.of(new PolicyWindow(GATE_ID, Direction.IN, at.minusSeconds(10), trust)))));
        return new BootstrapResponse(mode, base, revision, at, trust, FACTORY_ID, EDGE_ID, revoked, keys, policy,
                List.of(new Gate(GATE_ID, revision, "North entrance", FACTORY_ID, EDGE_ID, Direction.IN, "north", true)));
    }

    private static BootstrapResponse withKeys(BootstrapResponse full, KeySet keys) {
        return new BootstrapResponse(full.mode(), full.baseRevision(), full.revision(), full.generatedAt(), full.trustedUntil(),
                full.factoryId(), full.edgeId(), full.revocations(), keys, full.policy(), full.gates());
    }

    private static KeySet keySet(Instant at, KeyState state, Instant keyExpiry) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var point = CURVE.getGenerator();
        String x = encoder.encodeToString(HexFormat.of().parseHex("%064x".formatted(point.getAffineX())));
        String y = encoder.encodeToString(HexFormat.of().parseHex("%064x".formatted(point.getAffineY())));
        return new KeySet(1, at, at.plus(Duration.ofHours(48)), List.of(
                new SigningKey(KID, "EC", "P-256", "ES256", "sig", x, y, state, at.minusSeconds(10), keyExpiry)));
    }

    private static ECParameterSpec curve() {
        try {
            var parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException failed) {
            throw new AssertionError(failed);
        }
    }

    private static RevocationChange revoke(RevocationKind kind, String value) {
        return new RevocationChange(REVOCATION_ID, Operation.UPSERT, kind, value,
                kind == RevocationKind.SUB ? OCCURRED_AT : null, COMMITTED_AT);
    }

    private static FetchResult fetched(BootstrapResponse response) {
        return new FetchResult(false, response.revision(), response);
    }

    private void installFault() throws SQLException {
        execute("""
                CREATE TRIGGER fail_bootstrap BEFORE UPDATE OF bootstrap_revision ON edge_runtime_state
                BEGIN SELECT RAISE(ABORT, 'fault'); END
                """);
    }

    private void execute(String sql) throws SQLException {
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + file()); var statement = db.createStatement()) {
            statement.execute(sql);
        }
    }

    private String read(String sql) throws SQLException {
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + file())) {
            return scalar(db, sql);
        }
    }

    private Path file() {
        return tempDir.resolve("edge.db");
    }

    private BootstrapCache cache() {
        return new SqliteStore(file(), clock).bootstrapCache(IDENTITY);
    }
}
