package vn.tmc.edge.gatestation.infrastructure;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.tmc.contracts.JwsCompact;
import vn.tmc.contracts.JwsHeader;
import vn.tmc.contracts.QrClaims;
import vn.tmc.contracts.QrKeySetEntry;
import vn.tmc.edge.gatestation.api.QrKeyCachePort;
import vn.tmc.edge.gatestation.api.QrRevocationCachePort;
import vn.tmc.edge.gatestation.api.QrTrustedKey;
import vn.tmc.edge.gatestation.domain.QrRejectReason;
import vn.tmc.edge.gatestation.domain.QrVerificationPort;
import vn.tmc.edge.gatestation.domain.QrVerificationRejectException;
import vn.tmc.edge.gatestation.domain.VerifiedQr;

/**
 * PHỤ TRÁCH: Task 6. Adapter xác minh Dynamic QR ES256 tại Edge.
 * NGUỒN: CONTRACT-V1.md §4 (header/claims/skew), §5 (reasonCode), §8
 * (KeySet/revocation), §10 (QrVerificationPort.verify(rawToken, now)),
 * docs/tasks/07-qr-security.md; FR-QR-01/02/06/07/08.
 *
 * Thứ tự kiểm tra (fail closed, đúng §11.2 "sai alg/kid/iss/aud/time bị từ chối"):
 * 1) cấu trúc compact/base64url + giới hạn kích thước;
 * 2) cache khóa khả dụng (rỗng → CACHE_UNAVAILABLE, không chờ Central);
 * 3) header đúng hai trường, alg allowlist ES256;
 * 4) kid trong cache tin cậy, state/cửa sổ notBefore/notAfter (không hợp lệ →
 *    UNKNOWN_KID; state REVOKED → REVOKED);
 * 5) chữ ký r||s 64 byte với public key P-256;
 * 6) payload đúng 7 claims, iss/aud khớp, lifetime đúng 60;
 * 7) thời gian: iat<=now+5, nbf<=now+5, now<exp+5 (skew 5 giây);
 * 8) revocation cache SUB/JTI/KID → REVOKED.
 *
 * Bảo mật: không log token/claims; JSON parse bật STRICT_DUPLICATE_DETECTION;
 * từ chối base64 padding; không tải khóa từ URL trong token; không gọi
 * Central trong lượt quét (unknown kid từ chối lượt hiện tại, refresh nền).
 * CLOCK_UNTRUSTED thuộc luồng gọi (use case Task 2) khi Edge mất thời gian
 * tin cậy; verify luôn nhận now đã tin cậy theo §10.
 * PHỐI HỢP: Task 2 (ScanQrUseCase bắt QrVerificationRejectException), Task 7
 * (nạp QrKeyCachePort/QrRevocationCachePort sau bootstrap/refresh).
 */
public final class JwsVerifier implements QrVerificationPort {

    private static final int MAX_TOKEN_CHARS = 4096;
    private static final int MAX_HEADER_BYTES = 512;
    private static final int MAX_PAYLOAD_BYTES = 2048;
    private static final int MAX_SIGNATURE_BYTES = 512;

    private static final Set<String> HEADER_FIELDS = Set.of("alg", "kid");
    private static final Set<String> CLAIM_FIELDS = Set.of("sub", "iss", "aud", "iat", "nbf", "exp", "jti");

    /** JSON nghiêm ngặt: từ chối duplicate keys trong header/payload. */
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder().configure(StreamReadFeature.STRICT_DUPLICATE_DETECTION, true).build());

    private final QrKeyCachePort keyCache;
    private final QrRevocationCachePort revocationCache;

    public JwsVerifier(QrKeyCachePort keyCache, QrRevocationCachePort revocationCache) {
        this.keyCache = java.util.Objects.requireNonNull(keyCache, "keyCache");
        this.revocationCache = java.util.Objects.requireNonNull(revocationCache, "revocationCache");
    }

    @Override
    public VerifiedQr verify(String token, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now (thời gian tin cậy của Edge) bắt buộc");
        }
        if (token == null || token.isBlank()) {
            reject(QrRejectReason.INVALID_TOKEN, "token rỗng");
        }
        if (token.length() > MAX_TOKEN_CHARS) {
            reject(QrRejectReason.INVALID_TOKEN, "token vượt giới hạn độ dài");
        }
        String[] parts;
        try {
            parts = JwsCompact.splitCompact(token);
        } catch (IllegalArgumentException e) {
            throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN, "cấu trúc JWS compact sai");
        }
        byte[] headerBytes = decodePart(parts[0], MAX_HEADER_BYTES, "header");
        byte[] payloadBytes = decodePart(parts[1], MAX_PAYLOAD_BYTES, "payload");
        byte[] signatureBytes = decodePart(parts[2], MAX_SIGNATURE_BYTES, "signature");

        // 3) header: đúng hai trường, allowlist ES256
        Map<String, Object> header = parseJson(headerBytes);
        requireExactFields(header, HEADER_FIELDS, "header");
        String alg = stringField(header, "alg");
        String kid = stringField(header, "kid");
        if (!JwsHeader.ALGORITHM.equals(alg)) {
            reject(QrRejectReason.INVALID_TOKEN, "alg không nằm trong allowlist ES256");
        }

        // 4) khóa tin cậy trong cache
        Optional<QrTrustedKey> found = keyCache.findByKid(kid);
        if (found.isEmpty()) {
            if (!keyCache.hasUsableKeys()) {
                reject(QrRejectReason.CACHE_UNAVAILABLE, "cache khóa không khả dụng, fail closed");
            }
            reject(QrRejectReason.UNKNOWN_KID, "kid chưa có trong cache tin cậy; refresh chạy nền qua mTLS");
        }
        QrTrustedKey key = found.get();
        if (QrKeySetEntry.STATE_REVOKED.equals(key.state())) {
            reject(QrRejectReason.REVOKED, "khóa ký đã bị thu hồi");
        }
        if (now.isBefore(key.notBefore()) || !now.isBefore(key.notAfter())) {
            reject(QrRejectReason.UNKNOWN_KID, "khóa ngoài cửa sổ notBefore/notAfter tại thời điểm xác minh");
        }

        // 5) chữ ký
        byte[] signingInput = JwsCompact.signingInput(parts[0], parts[1]);
        if (!JwsCompact.verifyEs256(signingInput, signatureBytes, key.publicKey())) {
            reject(QrRejectReason.INVALID_TOKEN, "chữ ký ES256 không hợp lệ");
        }

        // 6) payload claims
        Map<String, Object> claims = parseJson(payloadBytes);
        requireExactFields(claims, CLAIM_FIELDS, "payload");
        String sub = stringField(claims, "sub");
        String iss = stringField(claims, "iss");
        String aud = stringField(claims, "aud");
        String jti = stringField(claims, "jti");
        long iat = integralField(claims, "iat");
        long nbf = integralField(claims, "nbf");
        long exp = integralField(claims, "exp");

        if (!QrClaims.ISSUER.equals(iss)) {
            reject(QrRejectReason.INVALID_TOKEN, "iss không khớp issuer tin cậy");
        }
        if (!QrClaims.AUDIENCE.equals(aud)) {
            reject(QrRejectReason.INVALID_TOKEN, "aud không khớp audience của gate");
        }
        if (exp - iat != QrClaims.DEFAULT_TTL_SECONDS) {
            reject(QrRejectReason.INVALID_TOKEN, "lifetime phải đúng 60 giây");
        }

        // 7) thời gian với skew tối đa 5 giây
        long nowSeconds = now.getEpochSecond();
        if (iat > nowSeconds + QrClaims.MAX_CLOCK_SKEW_SECONDS) {
            reject(QrRejectReason.INVALID_TOKEN, "iat lệch tương lai vượt skew");
        }
        if (nbf > nowSeconds + QrClaims.MAX_CLOCK_SKEW_SECONDS) {
            reject(QrRejectReason.INVALID_TOKEN, "token chưa hiệu lực ngoài skew");
        }
        if (nowSeconds >= exp + QrClaims.MAX_CLOCK_SKEW_SECONDS) {
            reject(QrRejectReason.TOKEN_EXPIRED, "token hết hạn (now >= exp+5s)");
        }

        String jtiCanonical = canonicalUuid(jti, "jti", true);
        String subCanonical = canonicalUuid(sub, "sub", false);

        // 8) revocation
        if (revocationCache.isRevoked(subCanonical, jtiCanonical, kid, Instant.ofEpochSecond(iat))) {
            reject(QrRejectReason.REVOKED, "token bị thu hồi theo cache SUB/JTI/KID");
        }

        return new VerifiedQr(new QrClaims(subCanonical, iss, aud, iat, nbf, exp, jtiCanonical), kid);
    }

    // ---------- helpers ----------

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
            return JSON.readValue(bytes, new TypeReference<Map<String, Object>>() { });
        } catch (IOException e) {
            throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN, "JSON không hợp lệ hoặc có duplicate key");
        }
    }

    private static void requireExactFields(Map<String, Object> map, Set<String> expected, String label) {
        if (!map.keySet().equals(expected)) {
            reject(QrRejectReason.INVALID_TOKEN, label + " sai bộ trường hợp đồng");
        }
    }

    private static String stringField(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN,
                    "trường " + field + " phải là chuỗi khác rỗng");
        }
        return text;
    }

    /** NumericDate phải là số nguyên JSON; số thập phân bị từ chối. */
    private static long integralField(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value instanceof Integer integer) {
            return integer.longValue();
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof BigInteger bigInteger) {
            try {
                return bigInteger.longValueExact();
            } catch (ArithmeticException e) {
                throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN,
                        "trường " + field + " ngoài phạm vi NumericDate");
            }
        }
        throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN,
                "trường " + field + " phải là số nguyên NumericDate");
    }

    private static String canonicalUuid(String value, String field, boolean requireV4) {
        try {
            UUID parsed = UUID.fromString(value);
            if (requireV4 && parsed.version() != 4) {
                throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN, field + " phải là UUIDv4");
            }
            return parsed.toString(); // chuẩn hóa chữ thường
        } catch (IllegalArgumentException e) {
            throw new QrVerificationRejectException(QrRejectReason.INVALID_TOKEN, field + " không phải UUID");
        }
    }
}
