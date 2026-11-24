package vn.tmc.edge.gatestation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.tmc.contracts.JwsCompact;
import vn.tmc.contracts.QrClaims;
import vn.tmc.contracts.QrKeySet;
import vn.tmc.contracts.QrKeySetEntry;
import vn.tmc.contracts.RevocationChange;
import vn.tmc.edge.gatestation.api.QrKeyCachePort;
import vn.tmc.edge.gatestation.domain.QrRejectReason;
import vn.tmc.edge.gatestation.domain.QrVerificationRejectException;
import vn.tmc.edge.gatestation.domain.VerifiedQr;
import vn.tmc.edge.gatestation.infrastructure.InMemoryQrKeyCache;
import vn.tmc.edge.gatestation.infrastructure.InMemoryQrRevocationCache;
import vn.tmc.edge.gatestation.infrastructure.JwsVerifier;

/**
 * PHỤ TRÁCH: Task 6. Unit test JwsVerifier theo CONTRACT-V1.md §4 (skew/lifetime),
 * §5 (reasonCode), §8 (KeySet/revocation) và điều kiện kiểm thử §11.2:
 * "Sai alg/kid/iss/aud/time bị từ chối" (race cùng jti thuộc Task 2/7).
 * Chạy: mvn -pl edge-gate-agent test (JDK 21). Fixture khóa sinh trong test.
 */
class JwsVerifierTest {

    /** Mốc thời gian mẫu khớp CONTRACT-EXAMPLES.md mục 2. */
    private static final Instant T0 = Instant.ofEpochSecond(1794880785L);
    private static final String KID = "qr-key-2026-11";
    private static final String SUB = "00000000-0000-4000-8000-000000000011";
    private static final String JTI = "00000000-0000-4000-8000-000000000012";

    private static final ObjectMapper JSON = new ObjectMapper();

    private KeyPair signingKey;
    private InMemoryQrKeyCache keyCache;
    private InMemoryQrRevocationCache revocationCache;
    private JwsVerifier verifier;

    @BeforeEach
    void setUp() {
        signingKey = generateP256();
        keyCache = new InMemoryQrKeyCache();
        revocationCache = new InMemoryQrRevocationCache();
        keyCache.replaceAll(keySetOf(KID, QrKeySetEntry.STATE_ACTIVE,
                (ECPublicKey) signingKey.getPublic(), T0.minusSeconds(3600), T0.plusSeconds(86400)));
        verifier = new JwsVerifier(keyCache, revocationCache);
    }

    private static KeyPair generateP256() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Sinh keypair P-256 thất bại", e);
        }
    }

    private QrKeySet keySetOf(String kid, String state, ECPublicKey publicKey, Instant notBefore, Instant notAfter) {
        QrKeySetEntry entry = new QrKeySetEntry(kid, "EC", "P-256", "ES256", "sig",
                JwsCompact.encodeBase64Url(JwsCompact.extractX(publicKey)),
                JwsCompact.encodeBase64Url(JwsCompact.extractY(publicKey)),
                state, notBefore, notAfter);
        return new QrKeySet(1L, T0, T0.plus(48, ChronoUnit.HOURS), List.of(entry));
    }

    private Map<String, Object> header(String kid, String alg) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", alg);
        header.put("kid", kid);
        return header;
    }

    private Map<String, Object> claims(long iat, long exp) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", SUB);
        claims.put("iss", QrClaims.ISSUER);
        claims.put("aud", QrClaims.AUDIENCE);
        claims.put("iat", iat);
        claims.put("nbf", iat);
        claims.put("exp", exp);
        claims.put("jti", JTI);
        return claims;
    }

    private String token(Map<String, Object> header, Map<String, Object> claims, KeyPair key) throws Exception {
        String headerB64 = JwsCompact.encodeBase64Url(JSON.writeValueAsBytes(header));
        String payloadB64 = JwsCompact.encodeBase64Url(JSON.writeValueAsBytes(claims));
        byte[] signature = JwsCompact.signEs256(JwsCompact.signingInput(headerB64, payloadB64), key.getPrivate());
        return headerB64 + "." + payloadB64 + "." + JwsCompact.encodeBase64Url(signature);
    }

    private String standardToken(long iat) throws Exception {
        return token(header(KID, "ES256"), claims(iat, iat + QrClaims.DEFAULT_TTL_SECONDS), signingKey);
    }

    private QrVerificationRejectException rejectOf(String token, Instant now) {
        return assertThrows(QrVerificationRejectException.class, () -> verifier.verify(token, now));
    }

    @Test
    void validTokenVerifiesAndExposesClaimsForReserve() throws Exception {
        VerifiedQr verified = verifier.verify(standardToken(T0.getEpochSecond()), T0);
        assertEquals(SUB, verified.employeeId());
        assertEquals(JTI, verified.jti());
        assertEquals(KID, verified.kid());
        assertEquals(T0, verified.tokenIssuedAt());
        assertEquals(T0.plusSeconds(60 + QrClaims.MAX_CLOCK_SKEW_SECONDS), verified.replayReservationExpiresAt());
    }

    @Test
    void expiryBoundaryStillAcceptedButPastSkewExpired() throws Exception {
        verifier.verify(standardToken(T0.getEpochSecond()), T0.plusSeconds(64));
        QrVerificationRejectException expired = rejectOf(standardToken(T0.getEpochSecond()), T0.plusSeconds(65));
        assertEquals(QrRejectReason.TOKEN_EXPIRED, expired.reason());
    }

    @Test
    void wrongAlgorithmAndNoneRejected() throws Exception {
        long iat = T0.getEpochSecond();
        assertEquals(QrRejectReason.INVALID_TOKEN,
                rejectOf(token(header(KID, "HS256"), claims(iat, iat + 60), signingKey), T0).reason());
        assertEquals(QrRejectReason.INVALID_TOKEN,
                rejectOf(token(header(KID, "none"), claims(iat, iat + 60), signingKey), T0).reason());
    }

    @Test
    void unknownKidRejected() throws Exception {
        assertEquals(QrRejectReason.UNKNOWN_KID,
                rejectOf(token(header("kid-khac", "ES256"), claims(T0.getEpochSecond(), T0.getEpochSecond() + 60), signingKey), T0).reason());
    }

    @Test
    void signatureFromAnotherKeyRejected() throws Exception {
        KeyPair attackerKey = generateP256();
        assertEquals(QrRejectReason.INVALID_TOKEN,
                rejectOf(token(header(KID, "ES256"), claims(T0.getEpochSecond(), T0.getEpochSecond() + 60), attackerKey), T0).reason());
    }

    @Test
    void futureIatAndNbfRejectedBeyondSkew() throws Exception {
        long futureIat = T0.getEpochSecond() + 6;
        assertEquals(QrRejectReason.INVALID_TOKEN, rejectOf(standardToken(futureIat), T0).reason());
        Map<String, Object> claims = claims(T0.getEpochSecond(), T0.getEpochSecond() + 60);
        claims.put("nbf", T0.getEpochSecond() + 6);
        assertEquals(QrRejectReason.INVALID_TOKEN,
                rejectOf(token(header(KID, "ES256"), claims, signingKey), T0).reason());
    }

    @Test
    void wrongLifetimeIssuerAudienceRejected() throws Exception {
        long iat = T0.getEpochSecond();
        Map<String, Object> lifetime = claims(iat, iat + 61);
        assertEquals(QrRejectReason.INVALID_TOKEN,
                rejectOf(token(header(KID, "ES256"), lifetime, signingKey), T0).reason());
        Map<String, Object> issuer = claims(iat, iat + 60);
        issuer.put("iss", "other-issuer");
        assertEquals(QrRejectReason.INVALID_TOKEN,
                rejectOf(token(header(KID, "ES256"), issuer, signingKey), T0).reason());
        Map<String, Object> audience = claims(iat, iat + 60);
        audience.put("aud", "tmc-edge-action");
        assertEquals(QrRejectReason.INVALID_TOKEN,
                rejectOf(token(header(KID, "ES256"), audience, signingKey), T0).reason());
    }

    @Test
    void extraOrMissingClaimRejected() throws Exception {
        long iat = T0.getEpochSecond();
        Map<String, Object> extra = claims(iat, iat + 60);
        extra.put("factoryId", "khong-duoc-them-factoryId-vao-claims");
        assertEquals(QrRejectReason.INVALID_TOKEN,
                rejectOf(token(header(KID, "ES256"), extra, signingKey), T0).reason());
        Map<String, Object> missing = new HashMap<>(claims(iat, iat + 60));
        missing.remove("jti");
        assertEquals(QrRejectReason.INVALID_TOKEN,
                rejectOf(token(header(KID, "ES256"), missing, signingKey), T0).reason());
    }

    @Test
    void duplicateKeyInPayloadRejected() throws Exception {
        String headerB64 = JwsCompact.encodeBase64Url(JSON.writeValueAsBytes(header(KID, "ES256")));
        long iat = T0.getEpochSecond();
        String payload = "{\"sub\":\"" + SUB + "\",\"iss\":\"" + QrClaims.ISSUER + "\",\"aud\":\""
                + QrClaims.AUDIENCE + "\",\"iat\":" + iat + ",\"nbf\":" + iat + ",\"exp\":" + (iat + 60)
                + ",\"jti\":\"" + JTI + "\",\"jti\":\"" + JTI + "\"}";
        assertEquals(QrRejectReason.INVALID_TOKEN, rejectOf(rawToken(headerB64, payload), T0).reason());
    }

    @Test
    void nonIntegralNumericDateRejected() throws Exception {
        String headerB64 = JwsCompact.encodeBase64Url(JSON.writeValueAsBytes(header(KID, "ES256")));
        long iat = T0.getEpochSecond();
        String payload = "{\"sub\":\"" + SUB + "\",\"iss\":\"" + QrClaims.ISSUER + "\",\"aud\":\""
                + QrClaims.AUDIENCE + "\",\"iat\":" + iat + ".5,\"nbf\":" + iat
                + ",\"exp\":" + (iat + 60) + ",\"jti\":\"" + JTI + "\"}";
        assertEquals(QrRejectReason.INVALID_TOKEN, rejectOf(rawToken(headerB64, payload), T0).reason());
    }

    @Test
    void malformedTokenRejected() {
        assertEquals(QrRejectReason.INVALID_TOKEN, rejectOf("khong-phai-token", T0).reason());
        assertEquals(QrRejectReason.INVALID_TOKEN, rejectOf("a.b", T0).reason());
        assertEquals(QrRejectReason.INVALID_TOKEN, rejectOf("a.b.c=", T0).reason());
        assertEquals(QrRejectReason.INVALID_TOKEN, rejectOf(null, T0).reason());
    }

    @Test
    void jtiMustBeUuidV4() throws Exception {
        Map<String, Object> claims = claims(T0.getEpochSecond(), T0.getEpochSecond() + 60);
        claims.put("jti", "00000000-0000-7000-8000-000000000012"); // UUIDv7
        assertEquals(QrRejectReason.INVALID_TOKEN,
                rejectOf(token(header(KID, "ES256"), claims, signingKey), T0).reason());
    }

    @Test
    void keyStateRevokedRejectedImmediately() throws Exception {
        keyCache.replaceAll(keySetOf(KID, QrKeySetEntry.STATE_REVOKED,
                (ECPublicKey) signingKey.getPublic(), T0.minusSeconds(3600), T0.plusSeconds(86400)));
        assertEquals(QrRejectReason.REVOKED, rejectOf(standardToken(T0.getEpochSecond()), T0).reason());
    }

    @Test
    void keyOutsideTrustWindowIsUnknownKid() throws Exception {
        keyCache.replaceAll(keySetOf(KID, QrKeySetEntry.STATE_ACTIVE,
                (ECPublicKey) signingKey.getPublic(), T0.plusSeconds(3600), T0.plusSeconds(86400)));
        assertEquals(QrRejectReason.UNKNOWN_KID, rejectOf(standardToken(T0.getEpochSecond()), T0).reason());
    }

    @Test
    void emptyKeyCacheFailsClosedWithCacheUnavailable() throws Exception {
        QrKeyCachePort emptyCache = new InMemoryQrKeyCache();
        JwsVerifier coldVerifier = new JwsVerifier(emptyCache, revocationCache);
        QrVerificationRejectException error = assertThrows(QrVerificationRejectException.class,
                () -> coldVerifier.verify(standardToken(T0.getEpochSecond()), T0));
        assertEquals(QrRejectReason.CACHE_UNAVAILABLE, error.reason());
    }

    @Test
    void subRevocationRejectsTokensIssuedBeforeCutoff() throws Exception {
        Instant cutoff = T0.plusSeconds(10);
        revocationCache.replaceAll(List.of(new RevocationChange(
                "00000000-0000-4000-8000-000000000031", RevocationChange.OP_UPSERT,
                RevocationChange.KIND_SUB, SUB, cutoff, cutoff)));
        assertEquals(QrRejectReason.REVOKED,
                rejectOf(standardToken(T0.getEpochSecond()), T0.plusSeconds(20)).reason());
        // token phát hành SAU cutoff không bị SUB chặn
        long reissuedIat = cutoff.getEpochSecond() + 1;
        verifier.verify(standardToken(reissuedIat), Instant.ofEpochSecond(reissuedIat));
    }

    @Test
    void jtiRevocationCanBeDeletedById() throws Exception {
        String banId = "00000000-0000-4000-8000-000000000032";
        revocationCache.replaceAll(List.of(new RevocationChange(
                banId, RevocationChange.OP_UPSERT, RevocationChange.KIND_JTI, JTI, null, T0)));
        assertEquals(QrRejectReason.REVOKED, rejectOf(standardToken(T0.getEpochSecond()), T0).reason());
        revocationCache.replaceAll(List.of(new RevocationChange(
                banId, RevocationChange.OP_DELETE, RevocationChange.KIND_JTI, JTI, null, T0)));
        // Sau DELETE, token còn hạn verify bình thường lại được
        verifier.verify(standardToken(T0.getEpochSecond()), T0);
        assertEquals(0, revocationCache.size());
    }

    @Test
    void kidRevocationRejectsMatchingTokens() throws Exception {
        revocationCache.replaceAll(List.of(new RevocationChange(
                "00000000-0000-4000-8000-000000000033", RevocationChange.OP_UPSERT,
                RevocationChange.KIND_KID, KID, null, T0)));
        assertEquals(QrRejectReason.REVOKED, rejectOf(standardToken(T0.getEpochSecond()), T0).reason());
        assertTrue(revocationCache.size() == 1);
    }

    /** Ký trực tiếp một chuỗi payload tự viết (fixture JSON thô). */
    private String rawToken(String headerB64, String payload) throws Exception {
        String payloadB64 = JwsCompact.encodeBase64Url(payload.getBytes(StandardCharsets.UTF_8));
        byte[] signature = JwsCompact.signEs256(JwsCompact.signingInput(headerB64, payloadB64), signingKey.getPrivate());
        return headerB64 + "." + payloadB64 + "." + JwsCompact.encodeBase64Url(signature);
    }
}
