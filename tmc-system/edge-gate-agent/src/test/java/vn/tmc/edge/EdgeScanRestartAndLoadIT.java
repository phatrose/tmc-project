package vn.tmc.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.tmc.contracts.AttendancePayload;
import vn.tmc.contracts.BootstrapResponse;
import vn.tmc.contracts.JwsHeader;
import vn.tmc.contracts.QrKeySet;
import vn.tmc.contracts.QrKeySetEntry;
import vn.tmc.edge.edgesync.domain.BootstrapCache;
import vn.tmc.edge.edgesync.domain.BootstrapCache.Identity;
import vn.tmc.edge.edgesync.infrastructure.SqliteStore;
import vn.tmc.edge.gatestation.domain.KioskContext;
import vn.tmc.edge.gatestation.infrastructure.InMemoryQrKeyCache;
import vn.tmc.edge.gatestation.infrastructure.InMemoryQrRevocationCache;
import vn.tmc.edge.gatestation.infrastructure.MockTurnstileAdapter;
import vn.tmc.edge.gatestation.infrastructure.SanUriKioskResolver;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 2, 6, 7 (E2E-04, E2E-10 Edge restart, E2E-12 load test).
 * Kiểm thử thực tế trên EdgeApplication.startWithHttps() (mTLS thật + SAN URI + ES256 JWS + SQLite WAL):
 * 1. E2E-04: Quét QR ES256 hợp lệ qua mTLS Kiosk -> 200 ALLOW + UNLOCK_ACCEPTED; phát lại cùng QR -> DENY REPLAY_DETECTED.
 * 2. E2E-10: Tắt và khởi động lại Edge trên cùng file SQLite -> replay vẫn bị chặn, sự kiện cũ còn nguyên, sequenceNo tăng đều.
 * 3. E2E-12: Đo tải 800 lượt quét QR ES256 thật qua HTTPS mTLS (4 luồng đồng thời, giữ nguyên toàn bộ kiểm tra bảo mật & WAL)
 *    và xác nhận p95 <= 2000ms, tỉ lệ lỗi = 0%.
 */
class EdgeScanRestartAndLoadIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder B64_URL = Base64.getUrlEncoder().withoutPadding();

    @Test
    void qrScanRestartDurabilityAnd800ScanLoadBenchmarkOverRealMtls(@TempDir Path dir) throws Exception {
        UUID edgeId = UUID.randomUUID();
        UUID factoryId = UUID.randomUUID();
        UUID gateId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID secondEmployeeId = UUID.randomUUID();
        final int totalScans = 800;
        List<UUID> loadEmployeeIds = new ArrayList<>(totalScans);
        for (int i = 0; i < totalScans; i++) {
            loadEmployeeIds.add(UUID.randomUUID());
        }
        String kioskId = "kiosk-load-01";

        // 1. CA test riêng + server cert SAN IP:127.0.0.1,DNS:localhost + Kiosk client cert SAN URI
        Path caP12 = dir.resolve("ca.p12");
        Path caPem = dir.resolve("ca.pem");
        Path trustP12 = dir.resolve("trust.p12");
        Path serverP12 = dir.resolve("edge-server.p12");
        Path kioskP12 = dir.resolve("kiosk.p12");

        keytool("-genkeypair", "-alias", "ca", "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", "CN=TMC Load Test CA", "-ext", "bc:c=ca:true",
                "-keystore", caP12.toString(), "-storetype", "PKCS12", "-storepass", "changeit", "-validity", "2");
        keytool("-exportcert", "-rfc", "-alias", "ca", "-keystore", caP12.toString(),
                "-storepass", "changeit", "-file", caPem.toString());
        keytool("-importcert", "-noprompt", "-alias", "ca", "-file", caPem.toString(),
                "-keystore", trustP12.toString(), "-storetype", "PKCS12", "-storepass", "changeit");

        issueSignedCert(dir, caP12, "server", "CN=localhost", "san=ip:127.0.0.1,dns:localhost", serverP12);
        String validSan = String.format("san=uri:urn:tmc:kiosk:%s,uri:urn:tmc:edge:%s,uri:urn:tmc:factory:%s,uri:urn:tmc:gate:%s",
                kioskId, edgeId, factoryId, gateId);
        issueSignedCert(dir, caP12, "kiosk-valid", "CN=kiosk-valid", validSan, kioskP12);

        SSLContext serverTls = buildSslContext(serverP12, trustP12);
        SSLContext kioskTls = buildSslContext(kioskP12, trustP12);

        // 2. Khóa ký ES256 cho QR token (aud=tmc-gate)
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair qrKp = gen.generateKeyPair();
        ECPublicKey ecPub = (ECPublicKey) qrKp.getPublic();
        String x = B64_URL.encodeToString(toFixed32(ecPub.getW().getAffineX().toByteArray()));
        String y = B64_URL.encodeToString(toFixed32(ecPub.getW().getAffineY().toByteArray()));

        Instant generatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS).minusSeconds(10);
        Instant trustedUntil = generatedAt.plus(BootstrapCache.FULL_TRUST);

        var keyCache = new InMemoryQrKeyCache();
        keyCache.replaceAll(new QrKeySet(1L, generatedAt, trustedUntil, List.of(
                new QrKeySetEntry("qr-kid-load", JwsHeader.KEY_TYPE, JwsHeader.CURVE, JwsHeader.ALGORITHM, JwsHeader.USE,
                        x, y, QrKeySetEntry.STATE_ACTIVE, generatedAt.minusSeconds(300), trustedUntil))));
        var revCache = new InMemoryQrRevocationCache();

        Path edgeDb = dir.resolve("edge-load.db");
        Identity identity = new Identity(edgeId, factoryId);
        SqliteStore initialStore = SqliteStore.provisionNew(edgeDb, Clock.systemUTC(), identity);

        var gate = new BootstrapResponse.Gate(
                gateId, 1L, "Cong Chinh", factoryId, edgeId, AttendancePayload.Direction.IN, "TS-LOAD-1", true);
        var window = new BootstrapResponse.PolicyWindow(
                gateId, AttendancePayload.Direction.IN, generatedAt.minusSeconds(3600), generatedAt.plusSeconds(86400));
        List<BootstrapResponse.EmployeePolicy> allPolicies = new ArrayList<>(totalScans + 2);
        allPolicies.add(new BootstrapResponse.EmployeePolicy(employeeId, true, List.of(window)));
        allPolicies.add(new BootstrapResponse.EmployeePolicy(secondEmployeeId, true, List.of(window)));
        for (UUID empId : loadEmployeeIds) {
            allPolicies.add(new BootstrapResponse.EmployeePolicy(empId, true, List.of(window)));
        }
        var keySet = new BootstrapResponse.KeySet(1L, generatedAt, trustedUntil, List.of(
                new BootstrapResponse.SigningKey(
                        "qr-kid-load", JwsHeader.KEY_TYPE, JwsHeader.CURVE, JwsHeader.ALGORITHM, JwsHeader.USE,
                        x, y, BootstrapResponse.KeyState.ACTIVE, generatedAt.minusSeconds(300), trustedUntil)));
        initialStore.bootstrapCache(identity).apply(new BootstrapResponse(
                BootstrapResponse.Mode.FULL, 0L, 1L, generatedAt, trustedUntil,
                factoryId, edgeId, List.of(), keySet,
                new BootstrapResponse.PolicySnapshot(1L, allPolicies), List.of(gate)));

        var resolver = new SanUriKioskResolver(Map.of(kioskId, new KioskContext(kioskId, edgeId, factoryId, Set.of(gateId))));

        // === PHASE 1 (E2E-04): Quét QR hợp lệ + kiểm tra replay ===
        String firstQrToken = signQrToken(qrKp, "qr-kid-load", employeeId, factoryId, generatedAt.getEpochSecond() + 5);
        UUID firstEventId;
        try (var running1 = EdgeApplication.startWithHttps(
                edgeDb, identity, URI.create("https://127.0.0.1:1"), kioskTls,
                new InetSocketAddress("127.0.0.1", 0), serverTls, resolver,
                new MockTurnstileAdapter(Clock.systemUTC()), keyCache, revCache)) {

            HttpClient http = HttpClient.newBuilder().sslContext(kioskTls).build();
            String base = "https://127.0.0.1:" + running1.boundPort();

            var allowRes = postScan(http, base, UUID.randomUUID(), gateId, firstQrToken);
            assertEquals(200, allowRes.statusCode(), allowRes.body());
            JsonNode allowJson = JSON.readTree(allowRes.body());
            assertEquals("ALLOW", allowJson.get("decision").asText(), allowRes.body());
            assertEquals("UNLOCK_ACCEPTED", allowJson.get("commandStatus").asText(), allowRes.body());
            firstEventId = UUID.fromString(allowJson.get("eventId").asText());

            // Replay cùng QR với scanId mới -> DENY REPLAY_DETECTED
            var replayRes = postScan(http, base, UUID.randomUUID(), gateId, firstQrToken);
            assertEquals(200, replayRes.statusCode(), replayRes.body());
            JsonNode replayJson = JSON.readTree(replayRes.body());
            assertEquals("DENY", replayJson.get("decision").asText(), replayRes.body());
            assertEquals("REPLAY", replayJson.get("reasonCode").asText(), replayRes.body());
        }

        // === PHASE 2 (E2E-10 Edge restart): Khởi động lại Edge trên cùng edge.db ===
        try (var running2 = EdgeApplication.startWithHttps(
                edgeDb, identity, URI.create("https://127.0.0.1:1"), kioskTls,
                new InetSocketAddress("127.0.0.1", 0), serverTls, resolver,
                new MockTurnstileAdapter(Clock.systemUTC()), keyCache, revCache)) {

            HttpClient http = HttpClient.newBuilder().sslContext(kioskTls).build();
            String base = "https://127.0.0.1:" + running2.boundPort();

            // Sau restart, phát lại firstQrToken VẪN phải bị chặn (replay persisted trong SQLite)
            var replayAfterRestart = postScan(http, base, UUID.randomUUID(), gateId, firstQrToken);
            assertEquals(200, replayAfterRestart.statusCode(), replayAfterRestart.body());
            JsonNode rj = JSON.readTree(replayAfterRestart.body());
            assertEquals("DENY", rj.get("decision").asText(), replayAfterRestart.body());
            assertEquals("REPLAY", rj.get("reasonCode").asText(), replayAfterRestart.body());

            // Quét QR mới của nhân viên thứ 2 sau restart -> ALLOW và eventId mới khác eventId cũ, sequenceNo tăng dần trong SQLite
            String secondToken = signQrToken(qrKp, "qr-kid-load", secondEmployeeId, factoryId, generatedAt.getEpochSecond() + 6);
            var secondRes = postScan(http, base, UUID.randomUUID(), gateId, secondToken);
            assertEquals(200, secondRes.statusCode(), secondRes.body());
            JsonNode sj = JSON.readTree(secondRes.body());
            assertEquals("ALLOW", sj.get("decision").asText(), secondRes.body());
            UUID secondEventId = UUID.fromString(sj.get("eventId").asText());
            assertNotEquals(firstEventId, secondEventId);

            // Xác minh sự kiện trước restart vẫn tồn tại trong SQLite và sequence tăng dần
            var reopenedStore = SqliteStore.openProvisioned(edgeDb, Clock.systemUTC(), identity);
            var ev1 = reopenedStore.findEvent(firstEventId).orElseThrow();
            var ev2 = reopenedStore.findEvent(secondEventId).orElseThrow();
            assertTrue(ev2.envelope().sequenceNo() > ev1.envelope().sequenceNo(),
                    "sequenceNo must increase monotonically across Edge restart");

            // === PHASE 3 (E2E-12): Đo tải 800 lượt quét QR ES256 thật qua mTLS + SQLite WAL (800 nhân viên) ===
            final int workers = 4;
            final int perWorker = totalScans / workers;
            long[] latenciesNanos = new long[totalScans];
            AtomicInteger errors = new AtomicInteger(0);
            var pool = Executors.newFixedThreadPool(workers);
            long benchStart = System.nanoTime();
            List<Future<Void>> futures = new ArrayList<>();
            long baseIat = Instant.now().getEpochSecond();

            for (int w = 0; w < workers; w++) {
                final int workerIdx = w;
                Callable<Void> task = () -> {
                    HttpClient workerHttp = HttpClient.newBuilder().sslContext(kioskTls).build();
                    for (int i = 0; i < perWorker; i++) {
                        int idx = workerIdx * perWorker + i;
                        UUID empId = loadEmployeeIds.get(idx);
                        String tok = signQrToken(qrKp, "qr-kid-load", empId, factoryId, baseIat);
                        long t0 = System.nanoTime();
                        var resp = postScan(workerHttp, base, UUID.randomUUID(), gateId, tok);
                        long elapsed = System.nanoTime() - t0;
                        latenciesNanos[idx] = elapsed;
                        if (resp.statusCode() != 200 || !resp.body().contains("\"ALLOW\"")) {
                            errors.incrementAndGet();
                        }
                    }
                    return null;
                };
                futures.add(pool.submit(task));
            }
            for (Future<Void> f : futures) {
                f.get();
            }
            pool.shutdown();
            double totalSeconds = (System.nanoTime() - benchStart) / 1_000_000_000.0;

            Arrays.sort(latenciesNanos);
            double p50Ms = latenciesNanos[(int) (totalScans * 0.50)] / 1_000_000.0;
            double p95Ms = latenciesNanos[(int) (totalScans * 0.95)] / 1_000_000.0;
            double p99Ms = latenciesNanos[(int) (totalScans * 0.99)] / 1_000_000.0;
            double maxMs = latenciesNanos[totalScans - 1] / 1_000_000.0;
            double rps = totalScans / totalSeconds;
            double errorRate = (errors.get() * 100.0) / totalScans;

            System.out.printf(
                    "[E2E-12 LOAD] scans=%d workers=%d duration=%.2fs rps=%.1f p50=%.2fms p95=%.2fms p99=%.2fms max=%.2fms errors=%d (%.2f%%)%n",
                    totalScans, workers, totalSeconds, rps, p50Ms, p95Ms, p99Ms, maxMs, errors.get(), errorRate);

            assertEquals(0, errors.get(), "Load test error count must be 0");
            assertTrue(p95Ms <= 2000.0, "p95 latency must be <= 2000ms, got: " + p95Ms + "ms");
        }
    }

    private static HttpResponse<String> postScan(HttpClient http, String base, UUID scanId, UUID gateId, String token)
            throws Exception {
        String body = JSON.writeValueAsString(Map.of(
                "scanId", scanId.toString(),
                "gateId", gateId.toString(),
                "token", token));
        return http.send(HttpRequest.newBuilder(URI.create(base + "/edge/v1/scans"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String signQrToken(KeyPair kp, String kid, UUID employeeId, UUID factoryId, long iat) throws Exception {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "ES256");
        header.put("kid", kid);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", employeeId.toString());
        claims.put("iss", vn.tmc.contracts.QrClaims.ISSUER);
        claims.put("aud", vn.tmc.contracts.QrClaims.AUDIENCE);
        claims.put("iat", iat);
        claims.put("nbf", iat);
        claims.put("exp", iat + vn.tmc.contracts.QrClaims.DEFAULT_TTL_SECONDS);
        claims.put("jti", UUID.randomUUID().toString());
        String h = B64_URL.encodeToString(JSON.writeValueAsBytes(header));
        String p = B64_URL.encodeToString(JSON.writeValueAsBytes(claims));
        String input = h + "." + p;
        Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
        sig.initSign(kp.getPrivate());
        sig.update(input.getBytes(StandardCharsets.UTF_8));
        return input + "." + B64_URL.encodeToString(sig.sign());
    }

    private static byte[] toFixed32(byte[] raw) {
        byte[] out = new byte[32];
        if (raw.length >= 32) {
            System.arraycopy(raw, raw.length - 32, out, 0, 32);
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        }
        return out;
    }

    private static void issueSignedCert(Path dir, Path caP12, String alias, String dname, String ext, Path outP12)
            throws Exception {
        Path csr = dir.resolve(alias + ".csr");
        Path cert = dir.resolve(alias + ".pem");
        keytool("-genkeypair", "-alias", alias, "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", dname, "-keystore", outP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-validity", "2");
        keytool("-certreq", "-alias", alias, "-keystore", outP12.toString(),
                "-storepass", "changeit", "-file", csr.toString());
        keytool("-gencert", "-alias", "ca", "-keystore", caP12.toString(), "-storepass", "changeit",
                "-infile", csr.toString(), "-outfile", cert.toString(), "-ext", ext, "-validity", "2", "-rfc");
        keytool("-importcert", "-noprompt", "-alias", "ca", "-file", dir.resolve("ca.pem").toString(),
                "-keystore", outP12.toString(), "-storepass", "changeit");
        keytool("-importcert", "-noprompt", "-alias", alias, "-file", cert.toString(),
                "-keystore", outP12.toString(), "-storepass", "changeit");
    }

    private static void keytool(String... args) throws Exception {
        String kt = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        String[] cmd = new String[args.length + 1];
        cmd[0] = kt;
        System.arraycopy(args, 0, cmd, 1, args.length);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.waitFor(), "keytool failed: " + out);
    }

    private static SSLContext buildSslContext(Path clientP12, Path trustP12) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore ts = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(trustP12.toFile())) {
            ts.load(fis, "changeit".toCharArray());
        }
        tmf.init(ts);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(clientP12.toFile())) {
            ks.load(fis, "changeit".toCharArray());
        }
        kmf.init(ks, "changeit".toCharArray());

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }
}
