package vn.tmc.contracts;

import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 6.
 * Protected header của Dynamic QR JWS (CONTRACT-V1.md §4: "Header
 * {alg:"ES256",kid:string}"). Đúng hai trường; kid chỉ nằm ở protected header,
 * không chuyển thành payload claim và không dùng làm URL tải khóa.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Header dữ liệu bất biến đi kèm token; issuer và verifier
 *   cùng dùng kiểu này để tránh lệch tên trường.
 * - Cần làm: Issuer chỉ ký với ES256; verifier từ chối giá trị alg khác kể cả
 *   khi client tự khai báo (allowlist, không tin header).
 * - Lưu ý bàn giao: Record không tự bảo đảm token hợp lệ; việc kiểm tra alg/kid
 *   nằm ở QrCredentialService (Central) và JwsVerifier (Edge).
 */
public record JwsHeader(String alg, String kid) {
    public static final String ALGORITHM = "ES256";
    public static final String KEY_TYPE = "EC";
    public static final String CURVE = "P-256";
    public static final String USE = "sig";

    public JwsHeader {
        Objects.requireNonNull(alg, "alg");
        Objects.requireNonNull(kid, "kid");
        if (kid.isBlank()) {
            throw new IllegalArgumentException("kid không được rỗng");
        }
    }
}
