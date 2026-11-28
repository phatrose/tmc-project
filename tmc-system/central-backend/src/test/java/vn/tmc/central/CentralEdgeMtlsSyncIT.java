package vn.tmc.central;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.tmc.contracts.AttendancePayload;
import vn.tmc.contracts.EventEnvelope;
import vn.tmc.contracts.SyncBatchRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 3, 5, 7 (DEVICE-01, 03, 04, 06, 10, E2E-06).
 * Kiểm thử mTLS trực tiếp thật trên Tomcat Central (cổng HTTPS riêng, certificateVerification=required)
 * cùng PostgreSQL 16 thật:
 * - CA test riêng, cert server có SAN IP:127.0.0.1,DNS:localhost (không tắt hostname/trust validation).
 * - Cert Edge hợp lệ có SAN URI urn:tmc:edge:<id> + urn:tmc:factory:<id> khớp bảng provisioning:
 *   1. GET /api/v1/edge/bootstrap -> 200, bỏ qua header X-Edge-Id/X-Factory-Id giả mạo.
 *   2. POST /api/v1/edge/heartbeat -> 200, ghi nhận đúng edgeId/factoryId từ SAN URI.
 *   3. POST /api/v1/attendance/sync -> 200 ACCEPTED, sự kiện khớp SAN được lưu bền vững.
 *   4. POST /api/v1/attendance/sync với sự kiện mạo danh edgeId khác -> 403 DEVICE_BINDING_MISMATCH.
 * - Cert ký bởi cùng CA nhưng SAN URI chưa provisioning -> 403 DEVICE_BINDING_MISMATCH.
 * - Kết nối không có client cert -> bị từ chối ở tầng TLS handshake.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-preview")
class CentralEdgeMtlsSyncIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tmc_mtls_sync_test")
            .withUsername("tmc_test")
            .withPassword("tmc_test_pass");

    private static final UUID PROVISIONED_EDGE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROVISIONED_FACTORY_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID GATE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID SPOOFED_EDGE_ID = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final UUID SPOOFED_FACTORY_ID = UUID.fromString("88888888-8888-4888-8888-888888888888");

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    private static Path certDir;
    private static int mtlsPort;
    private static Path trustP12;
    private static Path validEdgeP12;
    private static Path unprovisionedEdgeP12;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int httpPort;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        certDir = Files.createTempDirectory("tmc-central-mtls-it-");
        try (ServerSocket ss = new ServerSocket(0)) {
            mtlsPort = ss.getLocalPort();
        }
        Path caP12 = certDir.resolve("ca.p12");
        Path caPem = certDir.resolve("ca.pem");
        trustP12 = certDir.resolve("trust.p12");
        Path serverP12 = certDir.resolve("central-server.p12");
        validEdgeP12 = certDir.resolve("edge-valid.p12");
        unprovisionedEdgeP12 = certDir.resolve("edge-unprovisioned.p12");

        keytool("-genkeypair", "-alias", "ca", "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", "CN=TMC Central Test CA", "-ext", "bc:c=ca:true",
                "-keystore", caP12.toString(), "-storetype", "PKCS12", "-storepass", "changeit", "-validity", "2");
        keytool("-exportcert", "-rfc", "-alias", "ca", "-keystore", caP12.toString(),
                "-storepass", "changeit", "-file", caPem.toString());
        keytool("-importcert", "-noprompt", "-alias", "ca", "-file", caPem.toString(),
                "-keystore", trustP12.toString(), "-storetype", "PKCS12", "-storepass", "changeit");

        issueSignedCert(certDir, caP12, "central", "CN=localhost", "san=ip:127.0.0.1,dns:localhost", serverP12);
        issueSignedCert(certDir, caP12, "edge-valid", "CN=edge-valid",
                "san=uri:urn:tmc:edge:" + PROVISIONED_EDGE_ID + ",uri:urn:tmc:factory:" + PROVISIONED_FACTORY_ID,
                validEdgeP12);
        issueSignedCert(certDir, caP12, "edge-unprovisioned", "CN=edge-unprovisioned",
                "san=uri:urn:tmc:edge:" + UUID.randomUUID() + ",uri:urn:tmc:factory:" + PROVISIONED_FACTORY_ID,
                unprovisionedEdgeP12);

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("tmc.mtls.enabled", () -> "true");
        registry.add("tmc.mtls.port", () -> String.valueOf(mtlsPort));
        registry.add("tmc.mtls.keystore", serverP12::toString);
        registry.add("tmc.mtls.keystore-password", () -> "changeit");
        registry.add("tmc.mtls.truststore", trustP12::toString);
        registry.add("tmc.mtls.truststore-password", () -> "changeit");
        registry.add("tmc.mtls.provisioned-edges", () -> PROVISIONED_EDGE_ID + "=" + PROVISIONED_FACTORY_ID);
        registry.add("TMC_QR_KEYSTORE_PATH", () -> certDir.resolve("qr-keys.json").toString());
    }

    @Test
    void centralDirectMtlsAuthenticatesSanUriIgnoresSpoofedHeadersAndSyncsEvents() throws Exception {
        String base = "https://127.0.0.1:" + mtlsPort;

        HttpClient validEdgeClient = HttpClient.newBuilder()
                .sslContext(buildSslContext(validEdgeP12, trustP12))
                .build();
        HttpClient unprovisionedClient = HttpClient.newBuilder()
                .sslContext(buildSslContext(unprovisionedEdgeP12, trustP12))
                .build();
        HttpClient noClientCertClient = HttpClient.newBuilder()
                .sslContext(buildSslContext(null, trustP12))
                .build();

        // 1. Bootstrap qua mTLS hợp lệ + cố tình gửi header X-Edge-Id/X-Factory-Id giả mạo -> Central PHẢI dùng SAN URI
        var bootRes = validEdgeClient.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/edge/bootstrap?afterRevision=0"))
                        .header("X-Edge-Id", SPOOFED_EDGE_ID.toString())
                        .header("X-Factory-Id", SPOOFED_FACTORY_ID.toString())
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, bootRes.statusCode(), bootRes.body());
        JsonNode bootJson = JSON.readTree(bootRes.body());
        assertEquals(PROVISIONED_EDGE_ID.toString(), bootJson.get("edgeId").asText());
        assertEquals(PROVISIONED_FACTORY_ID.toString(), bootJson.get("factoryId").asText());

        // 2. Heartbeat qua mTLS hợp lệ + header giả mạo -> Central PHẢI ghi đúng SAN URI
        var hbRes = validEdgeClient.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/edge/heartbeat"))
                        .header("Content-Type", "application/json")
                        .header("X-Edge-Id", SPOOFED_EDGE_ID.toString())
                        .header("X-Factory-Id", SPOOFED_FACTORY_ID.toString())
                        .POST(HttpRequest.BodyPublishers.ofString("{\"pendingCount\":0,\"failedCount\":0}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, hbRes.statusCode(), hbRes.body());
        JsonNode hbJson = JSON.readTree(hbRes.body());
        assertEquals(PROVISIONED_EDGE_ID.toString(), hbJson.get("edgeId").asText());
        assertEquals(PROVISIONED_FACTORY_ID.toString(), hbJson.get("factoryId").asText());

        // 3. Sync batch hợp lệ qua mTLS (dù gửi header giả mạo) -> 200 ACCEPTED
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        AttendancePayload payload = AttendancePayload.ofQr(
                UUID.randomUUID(), AttendancePayload.Direction.IN, AttendancePayload.Source.ONLINE,
                UUID.randomUUID(), "kid-mtls-1", now.minusSeconds(5), UUID.randomUUID());
        String canonical = payload.toCanonicalJson();
        String validBatchJson = buildSyncBatchJson(UUID.randomUUID(),
                UUID.randomUUID(), EventEnvelope.EVT_ATTENDANCE_RECORDED, 1,
                PROVISIONED_EDGE_ID, GATE_ID, PROVISIONED_FACTORY_ID, 1L,
                UUID.randomUUID(), now, EventEnvelope.computeSha256(canonical), canonical);

        var syncRes = validEdgeClient.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/attendance/sync"))
                        .header("Content-Type", "application/json")
                        .header("X-Edge-Id", SPOOFED_EDGE_ID.toString())
                        .header("X-Factory-Id", SPOOFED_FACTORY_ID.toString())
                        .POST(HttpRequest.BodyPublishers.ofString(validBatchJson)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, syncRes.statusCode(), syncRes.body());
        JsonNode syncJson = JSON.readTree(syncRes.body());
        assertEquals("ACCEPTED", syncJson.get("acks").get(0).get("status").asText(), syncRes.body());
        assertEquals(1L, syncJson.get("lastContiguousSequenceNo").asLong(), syncRes.body());

        // 4. Event mạo danh edgeId khác từ Edge đã xác thực -> bị từ chối 403 DEVICE_BINDING_MISMATCH
        String spoofedBatchJson = buildSyncBatchJson(UUID.randomUUID(),
                UUID.randomUUID(), EventEnvelope.EVT_ATTENDANCE_RECORDED, 1,
                SPOOFED_EDGE_ID, GATE_ID, PROVISIONED_FACTORY_ID, 2L,
                UUID.randomUUID(), now, EventEnvelope.computeSha256(canonical), canonical);
        var spoofSyncRes = validEdgeClient.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/attendance/sync"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(spoofedBatchJson)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, spoofSyncRes.statusCode(), spoofSyncRes.body());

        // 5. Cert ký bởi cùng CA nhưng SAN URI chưa provisioning -> 403 DEVICE_BINDING_MISMATCH
        var rogueRes = unprovisionedClient.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/edge/bootstrap?afterRevision=0")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, rogueRes.statusCode(), rogueRes.body());
        assertTrue(rogueRes.body().contains("DEVICE_BINDING_MISMATCH"), rogueRes.body());

        // 6. Không trình client cert -> Tomcat từ chối ngay ở tầng TLS handshake
        assertThrows(Exception.class, () -> noClientCertClient.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/edge/bootstrap?afterRevision=0")).GET().build(),
                HttpResponse.BodyHandlers.ofString()));

        // 7. DEVICE-03: Cố tình gửi header X-Edge-Auth / X-Edge-Id / X-Factory-Id trên cổng HTTP thường (không mTLS) -> bị từ chối 401
        HttpClient plainHttp = HttpClient.newHttpClient();
        var headerFallbackRes = plainHttp.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpPort + "/api/v1/edge/bootstrap?afterRevision=0"))
                        .header("X-Edge-Auth", "any-token")
                        .header("X-Edge-Id", PROVISIONED_EDGE_ID.toString())
                        .header("X-Factory-Id", PROVISIONED_FACTORY_ID.toString())
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, headerFallbackRes.statusCode(), headerFallbackRes.body());
    }

    private static String buildSyncBatchJson(
            UUID syncBatchId, UUID eventId, String eventType, int schemaVersion,
            UUID edgeId, UUID gateId, UUID factoryId, long sequenceNo,
            UUID correlationId, Instant occurredAt, String payloadSha256, String payload
    ) throws Exception {
        java.util.Map<String, Object> env = new java.util.LinkedHashMap<>();
        env.put("eventId", eventId.toString());
        env.put("eventType", eventType);
        env.put("schemaVersion", schemaVersion);
        env.put("edgeId", edgeId.toString());
        env.put("gateId", gateId.toString());
        env.put("factoryId", factoryId.toString());
        env.put("sequenceNo", sequenceNo);
        env.put("correlationId", correlationId.toString());
        env.put("occurredAt", occurredAt.toString());
        env.put("payloadSha256", payloadSha256);
        env.put("payload", payload);
        return JSON.writeValueAsString(java.util.Map.of(
                "syncBatchId", syncBatchId.toString(),
                "events", List.of(env)));
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

        KeyManagerFactory kmf = null;
        if (clientP12 != null) {
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(clientP12.toFile())) {
                ks.load(fis, "changeit".toCharArray());
            }
            kmf.init(ks, "changeit".toCharArray());
        }
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf == null ? null : kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }
}
