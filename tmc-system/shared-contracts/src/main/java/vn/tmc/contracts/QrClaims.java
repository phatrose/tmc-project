package vn.tmc.contracts;

import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 6.
 * Bảy claims của Dynamic QR JWS theo CONTRACT-V1.md §4:
 * {sub:employeeId, iss:"tmc-central", aud:"tmc-gate", iat, nbf, exp, jti}.
 * Thời gian là NumericDate theo giây UTC (không phải milliseconds).
 * Không thêm factoryId: quyền nhà máy/cổng/ca do access-policy cache tại Edge
 * kiểm tra (DECISIONS.md §4). aud xác định dịch vụ xác minh, không phải nhà máy.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Payload đã giải mã; record chỉ là kiểu dữ liệu, không tự
 *   chứng minh token hợp lệ.
 * - Cần làm: Issuer sinh jti UUIDv4 mới mỗi lần cấp, exp = iat + 60.
 *   Verifier kiểm iat<=now+5, nbf<=now+5, now<exp+5, lifetime đúng 60
 *   (skew v1 tối đa 5 giây, CONTRACT-V1.md §4).
 * - Lưu ý bàn giao: Không log payload/claims đầy đủ; jti và kid được phép ghi
 *   vào event payload theo mục 6 của contract.
 */
public record QrClaims(String sub, String iss, String aud, long iat, long nbf, long exp, String jti) {
    public static final String ISSUER = "tmc-central";
    public static final String AUDIENCE = "tmc-gate";
    public static final long DEFAULT_TTL_SECONDS = 60;
    public static final long MAX_CLOCK_SKEW_SECONDS = 5;

    public QrClaims {
        Objects.requireNonNull(sub, "sub");
        Objects.requireNonNull(iss, "iss");
        Objects.requireNonNull(aud, "aud");
        Objects.requireNonNull(jti, "jti");
    }

    /** Thời điểm hết hạn dưới dạng Instant (exp là NumericDate theo giây). */
    public java.time.Instant expiresAt() {
        return java.time.Instant.ofEpochSecond(exp);
    }

    /** Thời điểm phát hành token (iat), dùng cho event payload tokenIssuedAt. */
    public java.time.Instant issuedAt() {
        return java.time.Instant.ofEpochSecond(iat);
    }
}
