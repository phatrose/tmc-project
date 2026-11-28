package vn.tmc.edge;

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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import vn.tmc.edge.edgesync.domain.BootstrapCache.Identity;
import vn.tmc.edge.edgesync.infrastructure.SqliteStore;
import vn.tmc.edge.gatestation.domain.KioskContext;
import vn.tmc.edge.gatestation.infrastructure.InMemoryQrKeyCache;
import vn.tmc.edge.gatestation.infrastructure.InMemoryQrRevocationCache;
import vn.tmc.edge.gatestation.infrastructure.MockTurnstileAdapter;
import vn.tmc.edge.gatestation.infrastructure.SanUriKioskResolver;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 2, 6, 7 (DEVICE-02, GRANT-09, E2E-04).
 * Kiểm thử HTTPS mTLS thật trên EdgeApplication.startWithHttps():
 * 1. CA test riêng + server cert có SAN IP:127.0.0.1,DNS:localhost + client cert Kiosk có SAN URI chuẩn.
 * 2. Kiosk hợp lệ gọi GET /edge/v1/presence (200) và POST /edge/v1/overrides với Action Grant ES256 thật (200 EMERGENCY).
 * 3. Kiosk chưa provisioning bị từ chối 403 DEVICE_BINDING_MISMATCH.
 * 4. Client không có certificate bị từ chối ngay tại TLS handshake.
 */
class EdgeMtlsHttpsIT {

    private static final char[] PASS = "changeit".toCharArray();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder B64_URL = Base64.getUrlEncoder().withoutPadding();

    @Test
    void edgeHttpsServerEnforcesMtlsSanUriAndVerifiesActionGrant(@TempDir Path dir) throws Exception {
        UUID edgeId = UUID.randomUUID();
        UUID factoryId = UUID.randomUUID();
        UUID gateId = UUID.randomUUID();
        String kioskId = "kiosk-01";

        // 1. Tạo CA test riêng + server cert + 2 client cert bằng keytool (không lưu vào Git)
        Path caP12 = dir.resolve("ca.p12");
        Path caPem = dir.resolve("ca.pem");
        Path trustP12 = dir.resolve("trust.p12");
        Path serverP12 = dir.resolve("server.p12");
        Path validClientP12 = dir.resolve("kiosk-valid.p12");
        Path rogueClientP12 = dir.resolve("kiosk-rogue.p12");

        keytool("-genkeypair", "-alias", "ca", "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", "CN=TMC Test CA", "-ext", "bc:c=ca:true",
                "-keystore", caP12.toString(), "-storetype", "PKCS12", "-storepass", "changeit", "-validity", "2");
        keytool("-exportcert", "-rfc", "-alias", "ca", "-keystore", caP12.toString(),
                "-storepass", "changeit", "-file", caPem.toString());
        keytool("-importcert", "-noprompt", "-alias", "ca", "-file", caPem.toString(),
                "-keystore", trustP12.toString(), "-storetype", "PKCS12", "-storepass", "changeit");

        issueSignedCert(dir, caP12, "server", "CN=localhost", "san=ip:127.0.0.1,dns:localhost", serverP12);
        String validSan = String.format("san=uri:urn:tmc:kiosk:%s,uri:urn:tmc:edge:%s,uri:urn:tmc:factory:%s,uri:urn:tmc:gate:%s",
                kioskId, edgeId, factoryId, gateId);
        issueSignedCert(dir, caP12, "kiosk-valid", "CN=kiosk-valid", validSan, validClientP12);
        String rogueSan = String.format("san=uri:urn:tmc:kiosk:kiosk-unknown,uri:urn:tmc:edge:%s,uri:urn:tmc:factory:%s,uri:urn:tmc:gate:%s",
                edgeId, factoryId, gateId);
        issueSignedCert(dir, caP12, "kiosk-rogue", "CN=kiosk-rogue", rogueSan, rogueClientP12);

        SSLContext serverTls = buildSslContext(serverP12, trustP12);
        SSLContext validClientTls = buildSslContext(validClientP12, trustP12);
        SSLContext rogueClientTls = buildSslContext(rogueClientP12, trustP12);
        SSLContext noClientCertTls = buildSslContext(null, trustP12);

        // 2. Nạp khóa ký ES256 cho Action Grant vào keyCache tin cậy của Edge
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair grantKp = gen.generateKeyPair();
        ECPublicKey ecPub = (ECPublicKey) grantKp.getPublic();
        Instant now = Instant.now();
        var keyCache = new InMemoryQrKeyCache();
        keyCache.replaceAll(new QrKeySet(1L, now.minusSeconds(60), now.plusSeconds(3600), List.of(
                new QrKeySetEntry("grant-kid-mtls", JwsHeader.KEY_TYPE, JwsHeader.CURVE, JwsHeader.ALGORITHM, JwsHeader.USE,
                        B64_URL.encodeToString(toFixed32(ecPub.getW().getAffineX().toByteArray())),
                        B64_URL.encodeToString(toFixed32(ecPub.getW().getAffineY().toByteArray())),
                        QrKeySetEntry.STATE_ACTIVE, now.minusSeconds(300), now.plusSeconds(3600)))));
        var revCache = new InMemoryQrRevocationCache();

        var provisioned = Map.of(kioskId, new KioskContext(kioskId, edgeId, factoryId, Set.of(gateId)));
        var resolver = new SanUriKioskResolver(provisioned);

        Path edgeDb = dir.resolve("edge.db");
        Identity identity = new Identity(edgeId, factoryId);
        SqliteStore initialStore = SqliteStore.provisionNew(edgeDb, Clock.systemUTC(), identity);
        Instant generatedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).minusSeconds(5);
        Instant trustedUntil = generatedAt.plus(vn.tmc.edge.edgesync.domain.BootstrapCache.FULL_TRUST);
        var bootstrapGate = new BootstrapResponse.Gate(
                gateId, 1L, "Cong 1", factoryId, edgeId, AttendancePayload.Direction.IN, "TS-1", true);
        var bootstrapKeySet = new BootstrapResponse.KeySet(
                1L, generatedAt, trustedUntil, List.of(
                        new BootstrapResponse.SigningKey(
                                "grant-kid-mtls", JwsHeader.KEY_TYPE, JwsHeader.CURVE, JwsHeader.ALGORITHM, JwsHeader.USE,
                                B64_URL.encodeToString(toFixed32(ecPub.getW().getAffineX().toByteArray())),
                                B64_URL.encodeToString(toFixed32(ecPub.getW().getAffineY().toByteArray())),
                                BootstrapResponse.KeyState.ACTIVE, generatedAt.minusSeconds(300), trustedUntil)));
        initialStore.bootstrapCache(identity).apply(new BootstrapResponse(
                BootstrapResponse.Mode.FULL, 0L, 1L, generatedAt, trustedUntil,
                factoryId, edgeId, List.of(), bootstrapKeySet,
                new BootstrapResponse.PolicySnapshot(1L, List.of()), List.of(bootstrapGate)));

        try (var running = EdgeApplication.startWithHttps(
                edgeDb,
                identity,
                URI.create("https://127.0.0.1:1"),
                validClientTls,
                new InetSocketAddress("127.0.0.1", 0),
                serverTls,
                resolver,
                new MockTurnstileAdapter(Clock.systemUTC()),
                keyCache,
                revCache)) {

            String base = "https://127.0.0.1:" + running.boundPort();

            // A. Kiosk hợp lệ gọi GET /edge/v1/presence -> 200
            HttpClient validHttp = HttpClient.newBuilder().sslContext(validClientTls).build();
            var presRes = validHttp.send(HttpRequest.newBuilder(URI.create(base + "/edge/v1/presence")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, presRes.statusCode(), presRes.body());

            // B. Kiosk hợp lệ gọi POST /edge/v1/overrides với Action Grant ES256 thật -> 200 EMERGENCY + UNLOCK_ACCEPTED
            UUID operatorId = UUID.randomUUID();
            String grant = signEmergencyGrant(grantKp, "grant-kid-mtls", operatorId, edgeId, gateId, now.getEpochSecond());
            String overrideBody = JSON.writeValueAsString(Map.of(
                    "scanId", UUID.randomUUID().toString(),
                    "gateId", gateId.toString(),
                    "reason", "Mo khan cap kiem thu mTLS Edge thuc te",
                    "mode", "EMERGENCY",
                    "grant", grant));
            var ovRes = validHttp.send(HttpRequest.newBuilder(URI.create(base + "/edge/v1/overrides"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(overrideBody)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ovRes.statusCode(), ovRes.body());
            assertTrue(ovRes.body().contains("\"decision\":\"EMERGENCY\""), ovRes.body());
            assertTrue(ovRes.body().contains("\"commandStatus\":\"UNLOCK_ACCEPTED\""), ovRes.body());

            // C. Kiosk chưa provisioning (SAN URI lạ) -> 403 DEVICE_BINDING_MISMATCH
            HttpClient rogueHttp = HttpClient.newBuilder().sslContext(rogueClientTls).build();
            var rogueRes = rogueHttp.send(HttpRequest.newBuilder(URI.create(base + "/edge/v1/presence")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, rogueRes.statusCode());
            assertTrue(rogueRes.body().contains("DEVICE_BINDING_MISMATCH"));

            // D. Client không có client cert -> bị chặn ngay tại TLS handshake
            HttpClient noCertHttp = HttpClient.newBuilder().sslContext(noClientCertTls).build();
            assertThrows(Exception.class, () -> noCertHttp.send(
                    HttpRequest.newBuilder(URI.create(base + "/edge/v1/presence")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()));
        }
    }

    private static void issueSignedCert(Path dir, Path caP12, String alias, String dname, String ext, Path outP12) throws Exception {
        Path csr = dir.resolve(alias + ".csr");
        Path crt = dir.resolve(alias + ".crt");
        Path caPem = dir.resolve("ca.pem");
        keytool("-genkeypair", "-alias", alias, "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", dname, "-keystore", outP12.toString(), "-storetype", "PKCS12", "-storepass", "changeit", "-validity", "2");
        keytool("-certreq", "-alias", alias, "-keystore", outP12.toString(), "-storepass", "changeit", "-file", csr.toString());
        keytool("-gencert", "-alias", "ca", "-keystore", caP12.toString(), "-storepass", "changeit",
                "-infile", csr.toString(), "-outfile", crt.toString(), "-ext", ext, "-validity", "2");
        keytool("-importcert", "-noprompt", "-alias", "ca", "-file", caPem.toString(),
                "-keystore", outP12.toString(), "-storepass", "changeit");
        keytool("-importcert", "-noprompt", "-alias", alias, "-file", crt.toString(),
                "-keystore", outP12.toString(), "-storepass", "changeit");
    }

    private static void keytool(String... args) throws Exception {
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "keytool failed: " + output);
    }

    private static SSLContext buildSslContext(Path keyStorePath, Path trustStorePath) throws Exception {
        KeyManagerFactory kmf = null;
        if (keyStorePath != null) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (var in = new FileInputStream(keyStorePath.toFile())) { ks.load(in, PASS); }
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, PASS);
        }
        KeyStore ts = KeyStore.getInstance("PKCS12");
        try (var in = new FileInputStream(trustStorePath.toFile())) { ts.load(in, PASS); }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf != null ? kmf.getKeyManagers() : null, tmf.getTrustManagers(), null);
        return ctx;
    }

    private static String signEmergencyGrant(KeyPair kp, String kid, UUID opId, UUID edgeId, UUID gateId, long iat) throws Exception {
        Map<String, Object> header = Map.of("alg", "ES256", "kid", kid);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", opId.toString());
        payload.put("iss", "tmc-central");
        payload.put("aud", "tmc-edge-action");
        payload.put("iat", iat);
        payload.put("nbf", iat);
        payload.put("exp", iat + 60);
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("action", "EMERGENCY");
        payload.put("edgeId", edgeId.toString());
        payload.put("gateId", gateId.toString());
        payload.put("employeeId", null);
        payload.put("reason", "Mo khan cap kiem thu mTLS Edge thuc te");
        String input = B64_URL.encodeToString(JSON.writeValueAsBytes(header)) + "."
                + B64_URL.encodeToString(JSON.writeValueAsBytes(payload));
        Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
        sig.initSign(kp.getPrivate());
        sig.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + B64_URL.encodeToString(sig.sign());
    }

    private static byte[] toFixed32(byte[] raw) {
        byte[] out = new byte[32];
        if (raw.length == 32) return raw;
        if (raw.length > 32) System.arraycopy(raw, raw.length - 32, out, 0, 32);
        else System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        return out;
    }
}
