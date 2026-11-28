package vn.tmc.edge.gatestation.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.tmc.contracts.JwsCompact;
import vn.tmc.contracts.JwsHeader;
import vn.tmc.contracts.QrKeySet;
import vn.tmc.contracts.QrKeySetEntry;
import vn.tmc.contracts.RevocationChange;
import vn.tmc.edge.edgesync.api.EventPayload.CommandMode;
import vn.tmc.edge.gatestation.domain.ActionGrantVerificationPort.VerifiedGrant;
import vn.tmc.edge.gatestation.domain.QrRejectReason;
import vn.tmc.edge.gatestation.domain.QrVerificationRejectException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 2 & Task 6.
 * Kiểm thử đơn vị cho Es256ActionGrantVerifier (GRANT-09):
 * xác minh ES256, aud=tmc-edge-action, từ chối aud=tmc-gate, hết hạn, chữ ký sai, thu hồi và ràng buộc employeeId.
 */
class Es256ActionGrantVerifierTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder B64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Instant NOW = Instant.parse("2026-11-28T10:00:00Z");

    private KeyPair keyPair;
    private InMemoryQrKeyCache keyCache;
    private InMemoryQrRevocationCache revocationCache;
    private Es256ActionGrantVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = gen.generateKeyPair();

        ECPublicKey ecPub = (ECPublicKey) keyPair.getPublic();
        byte[] x = toFixed32(ecPub.getW().getAffineX().toByteArray());
        byte[] y = toFixed32(ecPub.getW().getAffineY().toByteArray());

        keyCache = new InMemoryQrKeyCache();
        keyCache.replaceAll(new QrKeySet(1L, NOW.minusSeconds(60), NOW.plusSeconds(3600), List.of(new QrKeySetEntry(
                "grant-kid-1",
                JwsHeader.KEY_TYPE,
                JwsHeader.CURVE,
                JwsHeader.ALGORITHM,
                JwsHeader.USE,
                B64_URL.encodeToString(x),
                B64_URL.encodeToString(y),
                QrKeySetEntry.STATE_ACTIVE,
                NOW.minusSeconds(300),
                NOW.plusSeconds(3600)))));
        revocationCache = new InMemoryQrRevocationCache();
        verifier = new Es256ActionGrantVerifier(keyCache, revocationCache);
    }

    @Test
    void verifiesValidManualOpenAndEmergencyGrants() throws Exception {
        UUID opId = UUID.randomUUID();
        UUID edgeId = UUID.randomUUID();
        UUID gateId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();
        UUID jti1 = UUID.randomUUID();

        String manualToken = sign(Map.of("alg", "ES256", "kid", "grant-kid-1"),
                baseClaims(opId, edgeId, gateId, empId, jti1, "MANUAL", "tmc-edge-action", NOW.getEpochSecond(), 60));

        VerifiedGrant manual = verifier.verify(manualToken, NOW.plusSeconds(10));
        assertEquals(jti1, manual.jti());
        assertEquals(opId, manual.operatorId());
        assertEquals(CommandMode.MANUAL, manual.action());
        assertEquals(edgeId, manual.edgeId());
        assertEquals(gateId, manual.gateId());
        assertEquals(empId, manual.employeeId());

        UUID jti2 = UUID.randomUUID();
        String emergencyToken = sign(Map.of("alg", "ES256", "kid", "grant-kid-1"),
                baseClaims(opId, edgeId, gateId, null, jti2, "EMERGENCY", "tmc-edge-action", NOW.getEpochSecond(), 60));
        VerifiedGrant emergency = verifier.verify(emergencyToken, NOW.plusSeconds(10));
        assertEquals(CommandMode.EMERGENCY, emergency.action());
        assertNull(emergency.employeeId());
    }

    @Test
    void rejectsWrongAudienceExpiredAndRevokedGrants() throws Exception {
        UUID opId = UUID.randomUUID();
        UUID edgeId = UUID.randomUUID();
        UUID gateId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();
        UUID jti = UUID.randomUUID();

        // aud=tmc-gate (Dynamic QR) bị từ chối
        String wrongAud = sign(Map.of("alg", "ES256", "kid", "grant-kid-1"),
                baseClaims(opId, edgeId, gateId, empId, jti, "MANUAL", "tmc-gate", NOW.getEpochSecond(), 60));
        var audEx = assertThrows(QrVerificationRejectException.class, () -> verifier.verify(wrongAud, NOW));
        assertEquals(QrRejectReason.INVALID_TOKEN, audEx.reason());

        // action=QR không hợp lệ cho Action Grant
        String qrAction = sign(Map.of("alg", "ES256", "kid", "grant-kid-1"),
                baseClaims(opId, edgeId, gateId, empId, UUID.randomUUID(), "QR", "tmc-edge-action", NOW.getEpochSecond(), 60));
        assertEquals(QrRejectReason.INVALID_TOKEN,
                assertThrows(QrVerificationRejectException.class, () -> verifier.verify(qrAction, NOW)).reason());

        // Quá hạn exp + 5s skew
        String expired = sign(Map.of("alg", "ES256", "kid", "grant-kid-1"),
                baseClaims(opId, edgeId, gateId, empId, UUID.randomUUID(), "MANUAL", "tmc-edge-action", NOW.getEpochSecond() - 70, 60));
        var expEx = assertThrows(QrVerificationRejectException.class, () -> verifier.verify(expired, NOW));
        assertEquals(QrRejectReason.TOKEN_EXPIRED, expEx.reason());

        // MANUAL thiếu employeeId bị từ chối
        String missingEmp = sign(Map.of("alg", "ES256", "kid", "grant-kid-1"),
                baseClaims(opId, edgeId, gateId, null, UUID.randomUUID(), "MANUAL", "tmc-edge-action", NOW.getEpochSecond(), 60));
        assertEquals(QrRejectReason.INVALID_TOKEN,
                assertThrows(QrVerificationRejectException.class, () -> verifier.verify(missingEmp, NOW)).reason());

        // JTI bị thu hồi
        UUID revokedJti = UUID.randomUUID();
        revocationCache.replaceAll(List.of(new RevocationChange(
                UUID.randomUUID().toString(), RevocationChange.OP_UPSERT, RevocationChange.KIND_JTI,
                revokedJti.toString(), null, NOW)));
        String revokedToken = sign(Map.of("alg", "ES256", "kid", "grant-kid-1"),
                baseClaims(opId, edgeId, gateId, empId, revokedJti, "MANUAL", "tmc-edge-action", NOW.getEpochSecond(), 60));
        assertEquals(QrRejectReason.REVOKED,
                assertThrows(QrVerificationRejectException.class, () -> verifier.verify(revokedToken, NOW)).reason());
    }

    private static Map<String, Object> baseClaims(
            UUID opId, UUID edgeId, UUID gateId, UUID empId, UUID jti,
            String action, String aud, long iat, long ttl) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sub", opId.toString());
        map.put("iss", "tmc-central");
        map.put("aud", aud);
        map.put("iat", iat);
        map.put("nbf", iat);
        map.put("exp", iat + ttl);
        map.put("jti", jti.toString());
        map.put("action", action);
        map.put("edgeId", edgeId.toString());
        map.put("gateId", gateId.toString());
        map.put("employeeId", empId != null ? empId.toString() : null);
        map.put("reason", "Mo cong thu cong cho khach bao tri thiet bi");
        return map;
    }

    private String sign(Map<String, Object> header, Map<String, Object> payload) throws Exception {
        String h = B64_URL.encodeToString(JSON.writeValueAsBytes(header));
        String p = B64_URL.encodeToString(JSON.writeValueAsBytes(payload));
        String input = h + "." + p;
        Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
        sig.initSign(keyPair.getPrivate());
        sig.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + B64_URL.encodeToString(sig.sign());
    }

    private static byte[] toFixed32(byte[] raw) {
        byte[] out = new byte[32];
        if (raw.length == 32) return raw;
        if (raw.length > 32) {
            System.arraycopy(raw, raw.length - 32, out, 0, 32);
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        }
        return out;
    }
}
