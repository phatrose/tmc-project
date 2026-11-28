package vn.tmc.edge.gatestation.infrastructure;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import vn.tmc.contracts.JwsCompact;
import vn.tmc.contracts.JwsHeader;
import vn.tmc.contracts.QrClaims;
import vn.tmc.contracts.QrKeySetEntry;
import vn.tmc.edge.edgesync.api.EventPayload.CommandMode;
import vn.tmc.edge.gatestation.api.QrKeyCachePort;
import vn.tmc.edge.gatestation.api.QrRevocationCachePort;
import vn.tmc.edge.gatestation.api.QrTrustedKey;
import vn.tmc.edge.gatestation.domain.ActionGrantVerificationPort;
import vn.tmc.edge.gatestation.domain.QrRejectReason;
import vn.tmc.edge.gatestation.domain.QrVerificationRejectException;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 2 & Task 6.
 * Adapter xác minh ES256 Action Grant tại Edge theo CONTRACT-V1.md §5 ("Re-auth cho override"):
 * - allowlist alg=ES256, header chỉ gồm alg & kid;
 * - iss="tmc-central", aud="tmc-edge-action" (từ chối QR aud="tmc-gate");
 * - hạn 60 giây (exp - iat == 60), clock skew tối đa ±5 giây;
 * - xác minh chữ ký P-256 qua QrKeyCachePort (fail-closed khi cache rỗng/unknown kid/revoked);
 * - kiểm tra jti UUIDv4, sub/edgeId/gateId UUID, action in {MANUAL_OPEN, EMERGENCY},
 *   employeeId bắt buộc với MANUAL_OPEN (null được phép với EMERGENCY), reason 10..500 ký tự;
 * - đối soát QrRevocationCachePort (SUB/JTI/KID).
 */
public final class Es256ActionGrantVerifier implements ActionGrantVerificationPort {

    public static final String EXPECTED_ISSUER = "tmc-central";
    public static final String EXPECTED_AUDIENCE = "tmc-edge-action";
    public static final long GRANT_TTL_SECONDS = 60L;

    private static final int MAX_TOKEN_CHARS = 4096;
    private static final int MAX_HEADER_BYTES = 512;
    private static final int MAX_PAYLOAD_BYTES = 2048;
    private static final int MAX_SIGNATURE_BYTES = 512;
    private static final Set<String> HEADER_FIELDS = Set.of("alg", "kid");

    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder().configure(StreamReadFeature.STRICT_DUPLICATE_DETECTION, true).build());

    private final QrKeyCachePort keyCache;
    private final QrRevocationCachePort revocationCache;

    public Es256ActionGrantVerifier(QrKeyCachePort keyCache, QrRevocationCachePort revocationCache) {
        this.keyCache = Objects.requireNonNull(keyCache, "keyCache");
        this.revocationCache = Objects.requireNonNull(revocationCache, "revocationCache");
    }

    @Override
    public VerifiedGrant verify(String grant, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now (thời gian tin cậy của Edge) bắt buộc");
        }
        if (grant == null || grant.isBlank() || grant.length() > MAX_TOKEN_CHARS) {
            reject(QrRejectReason.INVALID_TOKEN, "action grant rỗng hoặc vượt giới hạn kích thước");
        }

        String[] parts;
        try {
            parts = JwsCompact.splitCompact(grant);
        } catch (IllegalArgumentException e) {
            throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN, "cấu trúc JWS compact sai");
        }

        byte[] headerBytes = decodePart(parts[0], MAX_HEADER_BYTES, "header");
        byte[] payloadBytes = decodePart(parts[1], MAX_PAYLOAD_BYTES, "payload");
        byte[] signatureBytes = decodePart(parts[2], MAX_SIGNATURE_BYTES, "signature");

        Map<String, Object> header = parseJson(headerBytes);
        if (!header.keySet().equals(HEADER_FIELDS)) {
            reject(QrRejectReason.INVALID_TOKEN, "header action grant sai bộ trường hợp đồng");
        }
        String alg = requiredString(header, "alg");
        String kid = requiredString(header, "kid");
        if (!JwsHeader.ALGORITHM.equals(alg)) {
            reject(QrRejectReason.INVALID_TOKEN, "alg không nằm trong allowlist ES256");
        }

        Optional<QrTrustedKey> found = keyCache.findByKid(kid);
        if (found.isEmpty()) {
            if (!keyCache.hasUsableKeys()) {
                reject(QrRejectReason.CACHE_UNAVAILABLE, "cache khóa action grant không khả dụng, fail closed");
            }
            reject(QrRejectReason.UNKNOWN_KID, "kid action grant chưa có trong cache tin cậy");
        }
        QrTrustedKey key = found.get();
        if (QrKeySetEntry.STATE_REVOKED.equals(key.state())) {
            reject(QrRejectReason.REVOKED, "khóa ký action grant đã bị thu hồi");
        }
        if (now.isBefore(key.notBefore()) || !now.isBefore(key.notAfter())) {
            reject(QrRejectReason.UNKNOWN_KID, "khóa ký ngoài cửa sổ notBefore/notAfter");
        }

        byte[] signingInput = JwsCompact.signingInput(parts[0], parts[1]);
        if (!JwsCompact.verifyEs256(signingInput, signatureBytes, key.publicKey())) {
            reject(QrRejectReason.INVALID_TOKEN, "chữ ký ES256 action grant không hợp lệ");
        }

        Map<String, Object> claims = parseJson(payloadBytes);
        String iss = requiredString(claims, "iss");
        String aud = requiredString(claims, "aud");
        if (!EXPECTED_ISSUER.equals(iss)) {
            reject(QrRejectReason.INVALID_TOKEN, "iss action grant không khớp tmc-central");
        }
        if (!EXPECTED_AUDIENCE.equals(aud)) {
            reject(QrRejectReason.INVALID_TOKEN, "aud action grant phải là tmc-edge-action");
        }

        long iat = integralField(claims, "iat");
        long nbf = integralField(claims, "nbf");
        long exp = integralField(claims, "exp");
        if (exp - iat != GRANT_TTL_SECONDS) {
            reject(QrRejectReason.INVALID_TOKEN, "lifetime action grant phải đúng 60 giây");
        }

        long nowSec = now.getEpochSecond();
        if (iat > nowSec + QrClaims.MAX_CLOCK_SKEW_SECONDS) {
            reject(QrRejectReason.INVALID_TOKEN, "iat action grant lệch tương lai vượt skew");
        }
        if (nbf > nowSec + QrClaims.MAX_CLOCK_SKEW_SECONDS) {
            reject(QrRejectReason.INVALID_TOKEN, "action grant chưa hiệu lực ngoài skew");
        }
        if (nowSec >= exp + QrClaims.MAX_CLOCK_SKEW_SECONDS) {
            reject(QrRejectReason.TOKEN_EXPIRED, "action grant hết hạn (now >= exp+5s)");
        }

        UUID jti = parseUuid(requiredString(claims, "jti"), "jti", true);
        UUID operatorId = parseUuid(requiredString(claims, "sub"), "sub", false);
        UUID edgeId = parseUuid(requiredString(claims, "edgeId"), "edgeId", false);
        UUID gateId = parseUuid(requiredString(claims, "gateId"), "gateId", false);

        CommandMode action;
        try {
            action = CommandMode.valueOf(requiredString(claims, "action"));
        } catch (IllegalArgumentException e) {
            throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN, "action không hợp lệ");
        }
        if (action != CommandMode.MANUAL && action != CommandMode.EMERGENCY) {
            reject(QrRejectReason.INVALID_TOKEN, "action grant chỉ chấp nhận MANUAL hoặc EMERGENCY");
        }

        Object rawEmp = claims.get("employeeId");
        UUID employeeId = null;
        if (rawEmp != null) {
            if (!(rawEmp instanceof String empStr) || empStr.isBlank()) {
                reject(QrRejectReason.INVALID_TOKEN, "employeeId phải là UUID hợp lệ hoặc null");
            } else {
                employeeId = parseUuid(empStr, "employeeId", false);
            }
        }
        if (action == CommandMode.MANUAL && employeeId == null) {
            reject(QrRejectReason.INVALID_TOKEN, "MANUAL bắt buộc có employeeId");
        }

        String reason = requiredString(claims, "reason").trim();
        if (reason.length() < 10 || reason.length() > 500) {
            reject(QrRejectReason.INVALID_TOKEN, "reason phải từ 10 đến 500 ký tự");
        }

        if (revocationCache.isRevoked(operatorId.toString(), jti.toString(), kid, Instant.ofEpochSecond(iat))) {
            reject(QrRejectReason.REVOKED, "action grant đã bị thu hồi theo revocation cache");
        }

        return new VerifiedGrant(jti, operatorId, action, edgeId, gateId, employeeId, reason, Instant.ofEpochSecond(exp));
    }

    private static void reject(QrRejectReason reason, String detail) {
        throw new QrVerificationRejectException(reason, detail);
    }

    private static byte[] decodePart(String part, int maxBytes, String label) {
        try {
            byte[] decoded = JwsCompact.decodeBase64Url(part);
            if (decoded.length > maxBytes) {
                throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN, label + " vượt giới hạn kích thước");
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN, label + " base64url không hợp lệ");
        }
    }

    private static Map<String, Object> parseJson(byte[] bytes) {
        try {
            return JSON.readValue(bytes, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN, "JSON không hợp lệ hoặc duplicate key");
        }
    }

    private static String requiredString(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN, "thiếu hoặc sai kiểu trường " + field);
        }
        return text;
    }

    private static long integralField(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Long l) return l;
        if (value instanceof BigInteger bi) {
            try { return bi.longValueExact(); } catch (ArithmeticException ignored) {}
        }
        throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN, "trường " + field + " phải là số nguyên");
    }

    private static UUID parseUuid(String value, String field, boolean requireV4) {
        try {
            UUID parsed = UUID.fromString(value);
            if (requireV4 && parsed.version() != 4) {
                throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN, field + " phải là UUIDv4");
            }
            return parsed;
        } catch (IllegalArgumentException e) {
            throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN, field + " không phải UUID");
        }
    }
}
