package vn.tmc.edge.edgesync.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tmc.contracts.BootstrapResponse;
import vn.tmc.contracts.BootstrapResponse.KeySet;
import vn.tmc.contracts.BootstrapResponse.KeyState;
import vn.tmc.contracts.BootstrapResponse.Mode;
import vn.tmc.contracts.BootstrapResponse.RevocationChange;
import vn.tmc.contracts.BootstrapResponse.RevocationKind;
import vn.tmc.contracts.BootstrapResponse.SigningKey;
import vn.tmc.edge.EdgeApplication;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.application.BootstrapWorker;
import vn.tmc.edge.edgesync.application.CachedAccess;
import vn.tmc.edge.edgesync.application.KeyRefreshWorker;
import vn.tmc.edge.edgesync.domain.BootstrapCache;
import vn.tmc.edge.edgesync.domain.BootstrapCache.ApplyResult;
import vn.tmc.edge.edgesync.domain.BootstrapPort;
import vn.tmc.edge.edgesync.domain.BootstrapProtocolException;
import vn.tmc.edge.edgesync.domain.SyncTransportException;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static vn.tmc.edge.edgesync.application.CachedAccess.Decision.ALLOW;
import static vn.tmc.edge.edgesync.application.CachedAccess.Decision.CACHE_UNAVAILABLE;
import static vn.tmc.edge.edgesync.application.CachedAccess.Decision.UNKNOWN_KID;
import static vn.tmc.edge.edgesync.infrastructure.SqliteBootstrapIT.IDENTITY;
import static vn.tmc.edge.edgesync.infrastructure.SqliteBootstrapIT.KID;
import static vn.tmc.edge.edgesync.infrastructure.SqliteBootstrapIT.full;
import static vn.tmc.edge.edgesync.infrastructure.SqliteEventStoreIT.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteSchemaIT.scalar;

class SqliteKeyRefreshIT {
    private static final String ROTATED_KID = "qr-key-2026-12";

    @TempDir
    Path tempDir;
    private final SqliteRecoveryIT.TestClock clock = new SqliteRecoveryIT.TestClock();

    @Test
    void unknownKid_deniesCurrentLookupWithoutCallingNetworkSynchronously() {
        var cache = cache();
        cache.apply(full(clock.instant()));
        var fetches = new AtomicInteger();
        var worker = new KeyRefreshWorker(() -> {
            fetches.incrementAndGet();
            return rotatedKeySet(2, clock.instant(), clock.instant().plus(Duration.ofHours(48)));
        }, cache);
        var access = new CachedAccess(cache, worker::requestRefresh);

        var lookup = access.lookupKey(ROTATED_KID, clock.instant());

        assertEquals(UNKNOWN_KID, lookup.decision());
        assertNull(lookup.key());
        assertEquals(0, fetches.get());
    }

    @Test
    void unknownKid_signalsDedicatedKeyRefreshWorker() {
        var cache = cache();
        var initial = full(clock.instant());
        cache.apply(initial);
        var bootstrapRequests = new ArrayList<Long>();
        var bootstrapWorker = new BootstrapWorker(after -> {
            bootstrapRequests.add(after);
            return new BootstrapPort.FetchResult(true, initial.revision(), null);
        }, cache, clock);
        var keyRefreshCalls = new AtomicInteger();
        var rotated = rotatedKeySet(2, clock.instant(), clock.instant().plus(Duration.ofHours(48)));
        var keyWorker = new KeyRefreshWorker(() -> {
            keyRefreshCalls.incrementAndGet();
            return rotated;
        }, cache);
        var access = new CachedAccess(cache, keyWorker::requestRefresh);

        assertEquals(UNKNOWN_KID, access.lookupKey(ROTATED_KID, clock.instant()).decision());
        assertEquals(0, keyRefreshCalls.get());

        // Normal bootstrap tick must NOT fall back to afterRevision=0 FULL merely because of unknown kid.
        bootstrapWorker.refresh();
        assertEquals(List.of(1L), bootstrapRequests);

        assertEquals(KeyRefreshWorker.Result.APPLIED, keyWorker.refreshIfRequested());
        assertEquals(1, keyRefreshCalls.get());
        assertEquals(ALLOW, access.lookupKey(ROTATED_KID, clock.instant()).decision());
    }

    @Test
    void repeatedUnknownKid_coalescesBackgroundRefreshRequests() {
        var cache = cache();
        cache.apply(full(clock.instant()));
        var keyRefreshCalls = new AtomicInteger();
        var worker = new KeyRefreshWorker(() -> {
            keyRefreshCalls.incrementAndGet();
            return rotatedKeySet(2, clock.instant(), clock.instant().plus(Duration.ofHours(48)));
        }, cache);
        var access = new CachedAccess(cache, worker::requestRefresh);

        assertEquals(KeyRefreshWorker.Result.IDLE, worker.refreshIfRequested());
        assertEquals(0, keyRefreshCalls.get());

        for (int i = 0; i < 10; i++) {
            assertEquals(UNKNOWN_KID, access.lookupKey(ROTATED_KID, clock.instant()).decision());
        }
        assertEquals(0, keyRefreshCalls.get());
        assertEquals(KeyRefreshWorker.Result.APPLIED, worker.refreshIfRequested());
        assertEquals(1, keyRefreshCalls.get());
        assertEquals(KeyRefreshWorker.Result.IDLE, worker.refreshIfRequested());
        assertEquals(1, keyRefreshCalls.get());
    }

    @Test
    void keyRefresh_persistsNewKeyForLaterLookup() {
        var cache = cache();
        cache.apply(full(clock.instant()));
        var rotated = rotatedKeySet(2, clock.instant().plusSeconds(5), clock.instant().plus(Duration.ofHours(48)));
        var worker = new KeyRefreshWorker(() -> rotated, cache);
        var access = new CachedAccess(cache, worker::requestRefresh);

        assertEquals(UNKNOWN_KID, access.lookupKey(ROTATED_KID, clock.instant()).decision());
        clock.advance(Duration.ofSeconds(5));
        assertEquals(KeyRefreshWorker.Result.APPLIED, worker.refreshIfRequested());
        var lookup = access.lookupKey(ROTATED_KID, clock.instant());
        assertEquals(ALLOW, lookup.decision());
        assertEquals(ROTATED_KID, lookup.key().kid());
        assertEquals(ALLOW, access.evaluate(EMPLOYEE_ID, JTI, ROTATED_KID, OCCURRED_AT, GATE_ID, clock.instant()).decision());
    }

    @Test
    void keyRefresh_doesNotExtendBootstrapTrustedUntil() throws Exception {
        var cache = cache();
        var initial = full(clock.instant());
        cache.apply(initial);
        var originalTrust = initial.trustedUntil();

        clock.advance(Duration.ofHours(13));
        var extendedKeyTrust = initial.trustedUntil().plus(Duration.ofHours(48));
        var worker = new KeyRefreshWorker(() -> rotatedKeySet(2, clock.instant(), extendedKeyTrust), cache);
        worker.requestRefresh();
        assertEquals(KeyRefreshWorker.Result.APPLIED, worker.refreshIfRequested());

        var after = cache.snapshot().orElseThrow();
        assertEquals(originalTrust, after.trustedUntil());
        assertEquals(originalTrust, after.trustDeadline());
        assertEquals(Long.toString(originalTrust.toEpochMilli()),
                read("SELECT bootstrap_trusted_until FROM edge_runtime_state"));

        // Renewal below 36h still triggers FULL bootstrap (afterRevision=0).
        var requests = new ArrayList<Long>();
        var bootstrapWorker = new BootstrapWorker(afterRev -> {
            requests.add(afterRev);
            return new BootstrapPort.FetchResult(true, 1, null);
        }, cache, clock);
        assertThrows(BootstrapProtocolException.class, bootstrapWorker::refresh);
        assertEquals(List.of(0L), requests);

        // Advancing past original bootstrap trustedUntil still fails closed.
        clock.advance(Duration.ofHours(35));
        assertEquals(CACHE_UNAVAILABLE, new CachedAccess(cache, () -> {}).lookupKey(ROTATED_KID, clock.instant()).decision());
    }

    @Test
    void keyRefresh_doesNotChangeBootstrapRevision() throws Exception {
        var cache = cache();
        var initial = full(clock.instant());
        cache.apply(initial);
        clock.advance(Duration.ofMinutes(1));
        var worker = new KeyRefreshWorker(
                () -> rotatedKeySet(2, clock.instant(), clock.instant().plus(Duration.ofHours(48))), cache);
        worker.requestRefresh();

        assertEquals(KeyRefreshWorker.Result.APPLIED, worker.refreshIfRequested());
        assertEquals(1, cache.snapshot().orElseThrow().revision());
        assertEquals("1", read("SELECT bootstrap_revision FROM edge_runtime_state"));
        assertEquals(Long.toString(initial.generatedAt().toEpochMilli()),
                read("SELECT bootstrap_generated_at FROM edge_runtime_state"));
    }

    @Test
    void keyRefresh_doesNotChangePolicyGatesOrRevocations() throws Exception {
        var cache = cache();
        var revocation = new RevocationChange(fixtureId(71), BootstrapResponse.Operation.UPSERT,
                RevocationKind.JTI, JTI.toString(), null, COMMITTED_AT);
        var initial = full(clock.instant());
        var seeded = new BootstrapResponse(Mode.FULL, 0, 1, initial.generatedAt(), initial.trustedUntil(),
                initial.factoryId(), initial.edgeId(), List.of(revocation), initial.keySet(),
                initial.policy(), initial.gates());
        cache.apply(seeded);

        byte[] policyBefore = readBytes("SELECT snapshot FROM cached_access_policy");
        byte[] gatesBefore = readBytes("SELECT snapshot FROM cached_gates");
        byte[] revocationBefore = readBytes("SELECT entry FROM cached_revocations");
        var hashBefore = read("SELECT bootstrap_snapshot_hash FROM edge_runtime_state");

        clock.advance(Duration.ofMinutes(5));
        var worker = new KeyRefreshWorker(
                () -> rotatedKeySet(2, clock.instant(), clock.instant().plus(Duration.ofHours(48))), cache);
        worker.requestRefresh();
        assertEquals(KeyRefreshWorker.Result.APPLIED, worker.refreshIfRequested());

        var after = cache.snapshot().orElseThrow();
        assertEquals(seeded.policy(), after.policy());
        assertEquals(seeded.gates(), after.gates());
        assertEquals(seeded.revocations(), after.revocations());
        assertArrayEquals(policyBefore, readBytes("SELECT snapshot FROM cached_access_policy"));
        assertArrayEquals(gatesBefore, readBytes("SELECT snapshot FROM cached_gates"));
        assertArrayEquals(revocationBefore, readBytes("SELECT entry FROM cached_revocations"));
        assertNotEquals(hashBefore, read("SELECT bootstrap_snapshot_hash FROM edge_runtime_state"));
    }

    @Test
    void keyRefresh_withoutTrustedBootstrap_doesNotCreateTrust() throws Exception {
        var fresh = cache();
        var freshWorker = new KeyRefreshWorker(
                () -> rotatedKeySet(1, clock.instant(), clock.instant().plus(Duration.ofHours(48))), fresh);
        freshWorker.requestRefresh();
        assertEquals(KeyRefreshWorker.Result.FULL_REQUIRED, freshWorker.refreshIfRequested());
        assertTrue(fresh.snapshot().isEmpty());
        assertEquals("0", read("SELECT count(*) FROM trusted_signing_keys"));

        // Expired bootstrap trust must also reject creating trust via key-only refresh.
        fresh.apply(full(clock.instant()));
        var previous = fresh.snapshot().orElseThrow();
        clock.advance(Duration.ofHours(48));
        var expiredWorker = new KeyRefreshWorker(
                () -> rotatedKeySet(2, clock.instant(), clock.instant().plus(Duration.ofHours(48))), fresh);
        expiredWorker.requestRefresh();
        assertEquals(KeyRefreshWorker.Result.FULL_REQUIRED, expiredWorker.refreshIfRequested());
        assertEquals(previous, fresh.snapshot().orElseThrow());
        assertEquals(CACHE_UNAVAILABLE, new CachedAccess(fresh, () -> {}).lookupKey(ROTATED_KID, clock.instant()).decision());
    }

    @ParameterizedTest
    @ValueSource(strings = {"kty", "crv", "alg", "use", "offCurve", "duplicateKid",
            "notBefore", "expiredTrust", "futureGenerated", "versionRollback"})
    void malformedRefreshedKeySet_isRejectedAndOldCacheRemainsUsable(String defect) throws Exception {
        var cache = cache();
        var initial = full(clock.instant());
        cache.apply(initial);
        var previous = cache.snapshot().orElseThrow();
        byte[] oldKeysBytes = readBytes("SELECT snapshot FROM trusted_signing_keys");
        var validKey = rotatedKey(clock.instant(), clock.instant().plus(Duration.ofHours(48)));

        SigningKey broken = switch (defect) {
            case "kty" -> new SigningKey(ROTATED_KID, "RSA", "P-256", "ES256", "sig", validKey.x(), validKey.y(),
                    KeyState.ACTIVE, validKey.notBefore(), validKey.notAfter());
            case "crv" -> new SigningKey(ROTATED_KID, "EC", "P-384", "ES256", "sig", validKey.x(), validKey.y(),
                    KeyState.ACTIVE, validKey.notBefore(), validKey.notAfter());
            case "alg" -> new SigningKey(ROTATED_KID, "EC", "P-256", "HS256", "sig", validKey.x(), validKey.y(),
                    KeyState.ACTIVE, validKey.notBefore(), validKey.notAfter());
            case "use" -> new SigningKey(ROTATED_KID, "EC", "P-256", "ES256", "enc", validKey.x(), validKey.y(),
                    KeyState.ACTIVE, validKey.notBefore(), validKey.notAfter());
            case "offCurve" -> new SigningKey(ROTATED_KID, "EC", "P-256", "ES256", "sig", validKey.x(),
                    Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]),
                    KeyState.ACTIVE, validKey.notBefore(), validKey.notAfter());
            case "notBefore" -> new SigningKey(ROTATED_KID, "EC", "P-256", "ES256", "sig", validKey.x(), validKey.y(),
                    KeyState.ACTIVE, validKey.notAfter(), validKey.notBefore());
            default -> validKey;
        };
        KeySet candidate = switch (defect) {
            case "duplicateKid" -> new KeySet(2, clock.instant(), clock.instant().plus(Duration.ofHours(48)),
                    List.of(validKey, validKey));
            case "expiredTrust" -> new KeySet(2, clock.instant().minusSeconds(10), clock.instant(), List.of(validKey));
            case "futureGenerated" -> new KeySet(2, clock.instant().plusSeconds(1),
                    clock.instant().plus(Duration.ofHours(48)), List.of(validKey));
            case "versionRollback" -> new KeySet(0, clock.instant(), clock.instant().plus(Duration.ofHours(48)),
                    List.of(validKey));
            default -> new KeySet(2, clock.instant(), clock.instant().plus(Duration.ofHours(48)), List.of(broken));
        };

        var worker = new KeyRefreshWorker(() -> candidate, cache);
        worker.requestRefresh();
        assertThrows(BootstrapProtocolException.class, worker::refreshIfRequested);
        assertEquals(previous, cache.snapshot().orElseThrow());
        assertArrayEquals(oldKeysBytes, readBytes("SELECT snapshot FROM trusted_signing_keys"));
        assertEquals(ALLOW, new CachedAccess(cache, () -> {}).lookupKey(KID, clock.instant()).decision());
    }

    @Test
    void keyRefreshCommitFailure_rollsBackToPreviousCoherentSnapshot() throws Exception {
        var cache = cache();
        cache.apply(full(clock.instant()));
        var previous = cache.snapshot().orElseThrow();
        execute("""
                CREATE TRIGGER fail_key_refresh BEFORE UPDATE OF bootstrap_snapshot_hash ON edge_runtime_state
                BEGIN SELECT RAISE(ABORT, 'key refresh write fault'); END
                """);

        clock.advance(Duration.ofMinutes(1));
        var worker = new KeyRefreshWorker(
                () -> rotatedKeySet(2, clock.instant(), clock.instant().plus(Duration.ofHours(48))), cache);
        worker.requestRefresh();
        assertThrows(EventStoreException.class, worker::refreshIfRequested);

        var reopened = cache();
        assertEquals(previous, reopened.snapshot().orElseThrow());
        var access = new CachedAccess(reopened, () -> {});
        assertEquals(ALLOW, access.lookupKey(KID, clock.instant()).decision());
        assertEquals(UNKNOWN_KID, access.lookupKey(ROTATED_KID, clock.instant()).decision());
    }

    @Test
    void restart_preservesRefreshedKeySetWithinOriginalTrustHorizon() {
        var cache = cache();
        var initial = full(clock.instant());
        cache.apply(initial);
        clock.advance(Duration.ofHours(2));
        var rotated = rotatedKeySet(2, clock.instant(), initial.trustedUntil().plus(Duration.ofHours(24)));
        var worker = new KeyRefreshWorker(() -> rotated, cache);
        worker.requestRefresh();
        assertEquals(KeyRefreshWorker.Result.APPLIED, worker.refreshIfRequested());

        var reopened = cache();
        var snapshot = reopened.snapshot().orElseThrow();
        assertEquals(rotated, snapshot.keySet());
        assertEquals(initial.trustedUntil(), snapshot.trustedUntil());
        assertEquals(initial.trustedUntil(), snapshot.trustDeadline());
        var access = new CachedAccess(reopened, () -> {});
        assertEquals(ALLOW, access.lookupKey(ROTATED_KID, clock.instant()).decision());
        assertEquals(ALLOW, access.evaluate(EMPLOYEE_ID, JTI, ROTATED_KID, OCCURRED_AT, GATE_ID, clock.instant()).decision());

        // Past original bootstrap trustedUntil still expires after restart.
        clock.advance(Duration.ofHours(46));
        assertEquals(CACHE_UNAVAILABLE, access.lookupKey(ROTATED_KID, clock.instant()).decision());
    }

    @Test
    void restKeyRefreshClient_usesDedicatedGetEndpointAndMapsResponses() throws Exception {
        assertEquals("/api/v1/qr/keys/refresh", EdgeApplication.KEY_REFRESH_PATH);
        var origin = URI.create("https://central.example.test");
        var endpoint = origin.resolve(EdgeApplication.KEY_REFRESH_PATH);

        assertThrows(IllegalArgumentException.class,
                () -> new RestKeyRefreshClient(URI.create("http://central.example.test/api/v1/qr/keys/refresh"),
                        req -> fail("Must reject plain HTTP")));
        assertThrows(IllegalArgumentException.class,
                () -> new RestKeyRefreshClient(URI.create("https://central.example.test/api/v1/qr/keys/refresh?kid=abc"),
                        req -> fail("Must reject query parameters")));

        var expected = rotatedKeySet(2, clock.instant(), clock.instant().plus(Duration.ofHours(48)));
        byte[] body = WireJson.write(expected);
        var seen = new ArrayList<HttpRequest>();
        var client = new RestKeyRefreshClient(endpoint, request -> {
            seen.add(request);
            return new StubResponse(200, body);
        });
        assertEquals(expected, client.fetch());
        assertEquals(1, seen.size());
        assertEquals("GET", seen.getFirst().method());
        assertEquals(endpoint, seen.getFirst().uri());

        var malformed = new RestKeyRefreshClient(endpoint,
                request -> new StubResponse(200, "{\"version\":2}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(BootstrapProtocolException.class, malformed::fetch);

        var unavailable = new RestKeyRefreshClient(endpoint, request -> new StubResponse(503, new byte[0]));
        assertEquals(503, assertThrows(SyncTransportException.class, unavailable::fetch).status());
    }

    private KeySet rotatedKeySet(long version, Instant generatedAt, Instant trustedUntil) {
        return new KeySet(version, generatedAt, trustedUntil, List.of(rotatedKey(generatedAt, trustedUntil)));
    }

    private static SigningKey rotatedKey(Instant at, Instant notAfter) {
        try {
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            var point = ((ECPublicKey) generator.generateKeyPair().getPublic()).getW();
            var encoder = Base64.getUrlEncoder().withoutPadding();
            String x = encoder.encodeToString(HexFormat.of().parseHex("%064x".formatted(point.getAffineX())));
            String y = encoder.encodeToString(HexFormat.of().parseHex("%064x".formatted(point.getAffineY())));
            return new SigningKey(ROTATED_KID, "EC", "P-256", "ES256", "sig",
                    x, y, KeyState.ACTIVE, at.minusSeconds(10), notAfter);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private BootstrapCache cache() {
        return new SqliteStore(file(), clock).bootstrapCache(IDENTITY);
    }

    private Path file() {
        return tempDir.resolve("edge.db");
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

    private byte[] readBytes(String sql) throws SQLException {
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + file());
             var statement = db.createStatement();
             var row = statement.executeQuery(sql)) {
            assertTrue(row.next(), sql);
            return row.getBytes(1);
        }
    }

    record StubResponse(int statusCode, byte[] body) implements HttpResponse<byte[]> {
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<byte[]>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return null; }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }
    }
}
