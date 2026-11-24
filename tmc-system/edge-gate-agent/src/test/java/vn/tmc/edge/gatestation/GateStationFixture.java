package vn.tmc.edge.gatestation;

import com.fasterxml.jackson.databind.ObjectMapper;
import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.contracts.BootstrapResponse;
import vn.tmc.contracts.BootstrapResponse.EmployeePolicy;
import vn.tmc.contracts.BootstrapResponse.Gate;
import vn.tmc.contracts.BootstrapResponse.KeySet;
import vn.tmc.contracts.BootstrapResponse.KeyState;
import vn.tmc.contracts.BootstrapResponse.Mode;
import vn.tmc.contracts.BootstrapResponse.PolicySnapshot;
import vn.tmc.contracts.BootstrapResponse.PolicyWindow;
import vn.tmc.contracts.BootstrapResponse.SigningKey;
import vn.tmc.contracts.JwsCompact;
import vn.tmc.contracts.QrClaims;
import vn.tmc.contracts.QrKeySet;
import vn.tmc.contracts.QrKeySetEntry;
import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;
import vn.tmc.edge.edgesync.domain.BootstrapCache;
import vn.tmc.edge.edgesync.infrastructure.SqliteStore;
import vn.tmc.edge.gatestation.application.GateDecisionRecorder;
import vn.tmc.edge.gatestation.application.GateOverrideUseCase;
import vn.tmc.edge.gatestation.application.RecentScanResults;
import vn.tmc.edge.gatestation.application.ScanQrUseCase;
import vn.tmc.edge.gatestation.domain.ActionGrantVerificationPort;
import vn.tmc.edge.gatestation.domain.CommandResult;
import vn.tmc.edge.gatestation.domain.KioskContext;
import vn.tmc.edge.gatestation.domain.ScanRequest;
import vn.tmc.edge.gatestation.domain.ScanResult;
import vn.tmc.edge.gatestation.domain.TurnstileControlPort;
import vn.tmc.edge.gatestation.infrastructure.CachedAccessPolicy;
import vn.tmc.edge.gatestation.infrastructure.InMemoryPresenceTracker;
import vn.tmc.edge.gatestation.infrastructure.InMemoryQrKeyCache;
import vn.tmc.edge.gatestation.infrastructure.InMemoryQrRevocationCache;
import vn.tmc.edge.gatestation.infrastructure.JwsVerifier;
import vn.tmc.edge.gatestation.infrastructure.MockTurnstileAdapter;
import vn.tmc.edge.gatestation.infrastructure.ScanController;
import vn.tmc.edge.gatestation.infrastructure.SystemTrustedClock;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dựng toàn bộ luồng Gate Station với thành phần THẬT của các task khác để test end-to-end:
 * SqliteStore (Task 7), BootstrapCache (Task 7), JwsVerifier + cache khóa/thu hồi (Task 6).
 * Chỉ turnstile (mock có đếm), đồng hồ và bộ xác minh action grant là giả lập.
 */
final class GateStationFixture {
    static final UUID EDGE_ID = id(21);
    static final UUID FACTORY_ID = id(23);
    static final UUID GATE_ID = id(22);
    static final UUID OTHER_GATE_ID = id(27);
    static final UUID EMPLOYEE_ID = id(11);
    static final UUID OPERATOR_ID = id(30);
    static final String KID = "qr-key-2026-11";
    static final Instant T0 = Instant.parse("2026-11-17T02:00:00.000Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Số lần use case thật sự gọi turnstile (tách khỏi số lần "mở thật" của mock). */
    static final class CountingTurnstile implements TurnstileControlPort {
        final MockTurnstileAdapter mock;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<RuntimeException> failure = new AtomicReference<>();

        CountingTurnstile(MockTurnstileAdapter mock) {
            this.mock = mock;
        }

        @Override
        public CommandResult unlock(UUID commandId, UUID gateId) {
            calls.incrementAndGet();
            RuntimeException boom = failure.get();
            if (boom != null) {
                throw boom;
            }
            return mock.unlock(commandId, gateId);
        }
    }

    static final class TestClock extends Clock {
        private volatile Instant now;

        TestClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    final Path dbFile;
    final TestClock clock = new TestClock(T0);
    final KeyPair signingKey = generateP256();
    final BootstrapCache.Identity identity = new BootstrapCache.Identity(EDGE_ID, FACTORY_ID);
    final KioskContext kiosk = new KioskContext("kiosk-1", EDGE_ID, FACTORY_ID, Set.of(GATE_ID));
    final InMemoryQrKeyCache keyCache = new InMemoryQrKeyCache();
    final InMemoryQrRevocationCache revocationCache = new InMemoryQrRevocationCache();
    final CountingTurnstile turnstile = new CountingTurnstile(new MockTurnstileAdapter(clock));
    final AtomicReference<ScanMode> mode = new AtomicReference<>(ScanMode.ONLINE);
    final AtomicBoolean clockHealthy = new AtomicBoolean(true);
    final AtomicReference<ActionGrantVerificationPort> grantVerifier = new AtomicReference<>(
            (grant, now) -> { throw new AssertionError("grantVerifier chưa được cấu hình trong test"); });
    final InMemoryPresenceTracker presence = new InMemoryPresenceTracker(clock);

    SqliteStore store;
    BootstrapCache cache;
    ScanQrUseCase scanUseCase;
    GateOverrideUseCase overrideUseCase;
    GateDecisionRecorder recorder;
    ScanController controller;

    /** applyBootstrap=false mô phỏng Edge vừa provision, chưa có cache tin cậy. */
    GateStationFixture(Path directory, boolean gateEnabled, boolean applyBootstrap) {
        this.dbFile = directory.resolve("edge.db");
        store = SqliteStore.provisionNew(dbFile, clock, identity);
        cache = store.bootstrapCache(identity);
        if (applyBootstrap) {
            cache.apply(bootstrap(T0, gateEnabled));
        }
        keyCache.replaceAll(keySet(T0));
        wire();
    }

    /** Dựng lại use case trên cùng file SQLite với bộ nhớ trống, như sau khi khởi động lại tiến trình. */
    void restart() {
        store = SqliteStore.openProvisioned(dbFile, clock, identity);
        store.recover();
        cache = store.bootstrapCache(identity);
        wire();
    }

    private void wire() {
        var policy = new CachedAccessPolicy(cache);
        var trustedClock = new SystemTrustedClock(clock, clockHealthy::get);
        var memo = new RecentScanResults(clock);
        recorder = new GateDecisionRecorder(store, turnstile, mode::get, memo, presence, clock);
        var verifier = new JwsVerifier(keyCache, revocationCache);
        scanUseCase = new ScanQrUseCase(verifier, policy, policy, store, trustedClock, recorder);
        overrideUseCase = new GateOverrideUseCase((grant, now) -> grantVerifier.get().verify(grant, now),
                policy, policy, store, trustedClock, recorder);
        controller = new ScanController(scanUseCase, overrideUseCase, recorder, presence);
    }

    /** Use case quét dùng store khác (ví dụ store "mù" để mô phỏng cửa sổ đua), bộ nhớ kết quả trống. */
    ScanQrUseCase scanUseCaseOver(vn.tmc.edge.edgesync.api.LocalEventStorePort other) {
        var policy = new CachedAccessPolicy(cache);
        var rec = new GateDecisionRecorder(other, turnstile, mode::get, new RecentScanResults(clock), presence, clock);
        return new ScanQrUseCase(new JwsVerifier(keyCache, revocationCache), policy, policy, other,
                new SystemTrustedClock(clock, clockHealthy::get), rec);
    }

    GateOverrideUseCase overrideUseCaseOver(vn.tmc.edge.edgesync.api.LocalEventStorePort other) {
        var policy = new CachedAccessPolicy(cache);
        var rec = new GateDecisionRecorder(other, turnstile, mode::get, new RecentScanResults(clock), presence, clock);
        return new GateOverrideUseCase((grant, now) -> grantVerifier.get().verify(grant, now), policy, policy, other,
                new SystemTrustedClock(clock, clockHealthy::get), rec);
    }

    /** Store không tìm thấy outcome cũ: tái tạo khoảnh khắc hai request cùng scanId cùng vượt qua bước kiểm tra retry. */
    vn.tmc.edge.edgesync.api.LocalEventStorePort blindStore() {
        return (vn.tmc.edge.edgesync.api.LocalEventStorePort) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{vn.tmc.edge.edgesync.api.LocalEventStorePort.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findScanOutcome")) {
                        return java.util.Optional.empty();
                    }
                    try {
                        return method.invoke(store, args);
                    } catch (java.lang.reflect.InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }

    ScanResult scan(UUID scanId, String token) {
        return scanUseCase.scan(new ScanRequest(scanId, GATE_ID, token), kiosk);
    }

    static UUID scanId() {
        return UUID.randomUUID();
    }

    String token(UUID employeeId) {
        return token(employeeId, UUID.randomUUID(), clock.instant().getEpochSecond(), KID, signingKey);
    }

    String token(UUID employeeId, UUID jti) {
        return token(employeeId, jti, clock.instant().getEpochSecond(), KID, signingKey);
    }

    String token(UUID employeeId, UUID jti, long iat, String kid, KeyPair key) {
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "ES256");
            header.put("kid", kid);
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", employeeId.toString());
            claims.put("iss", QrClaims.ISSUER);
            claims.put("aud", QrClaims.AUDIENCE);
            claims.put("iat", iat);
            claims.put("nbf", iat);
            claims.put("exp", iat + QrClaims.DEFAULT_TTL_SECONDS);
            claims.put("jti", jti.toString());
            String headerB64 = JwsCompact.encodeBase64Url(JSON.writeValueAsBytes(header));
            String payloadB64 = JwsCompact.encodeBase64Url(JSON.writeValueAsBytes(claims));
            byte[] signature = JwsCompact.signEs256(JwsCompact.signingInput(headerB64, payloadB64), key.getPrivate());
            return headerB64 + "." + payloadB64 + "." + JwsCompact.encodeBase64Url(signature);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    BootstrapResponse bootstrap(Instant at, boolean gateEnabled) {
        Instant trust = at.plus(Duration.ofHours(48));
        ECPublicKey publicKey = (ECPublicKey) signingKey.getPublic();
        var key = new SigningKey(KID, "EC", "P-256", "ES256", "sig",
                JwsCompact.encodeBase64Url(JwsCompact.extractX(publicKey)),
                JwsCompact.encodeBase64Url(JwsCompact.extractY(publicKey)),
                KeyState.ACTIVE, at.minusSeconds(10), at.plus(Duration.ofDays(30)));
        var policy = new PolicySnapshot(1, List.of(new EmployeePolicy(EMPLOYEE_ID, true,
                List.of(new PolicyWindow(GATE_ID, Direction.IN, at.minusSeconds(3600), trust)))));
        var gate = new Gate(GATE_ID, 1, "North entrance", FACTORY_ID, EDGE_ID, Direction.IN, "north", gateEnabled);
        return new BootstrapResponse(Mode.FULL, 0, 1, at, trust, FACTORY_ID, EDGE_ID, List.of(),
                new KeySet(1, at, trust, List.of(key)), policy, List.of(gate));
    }

    private QrKeySet keySet(Instant at) {
        ECPublicKey publicKey = (ECPublicKey) signingKey.getPublic();
        var entry = new QrKeySetEntry(KID, "EC", "P-256", "ES256", "sig",
                JwsCompact.encodeBase64Url(JwsCompact.extractX(publicKey)),
                JwsCompact.encodeBase64Url(JwsCompact.extractY(publicKey)),
                QrKeySetEntry.STATE_ACTIVE, at.minusSeconds(10), at.plus(Duration.ofDays(30)));
        return new QrKeySet(1, at, at.plus(Duration.ofHours(48)), List.of(entry));
    }

    /** Số event đã commit theo loại, đọc trực tiếp từ SQLite để kiểm chứng điều use case đã ghi. */
    long events(String eventType) {
        return scalar("SELECT count(*) FROM local_events WHERE event_type = '" + eventType + "'");
    }

    String lastPayload(String eventType) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT payload_text FROM local_events WHERE event_type = '" + eventType
                     + "' ORDER BY sequence_no DESC LIMIT 1")) {
            return rows.next() ? rows.getString(1) : null;
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    long scalar(String sql) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             var statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    void execute(String sql) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    static UUID id(int n) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(n));
    }

    private static KeyPair generateP256() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
            return generator.generateKeyPair();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
