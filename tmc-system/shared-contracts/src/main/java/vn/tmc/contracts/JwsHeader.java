package vn.tmc.contracts;



/**
 * PHỤ TRÁCH: Task 6.
 * Khung triển khai theo docs/DECISIONS.md; chưa có nghiệp vụ hoạt động.
 * TODO: issuer/verifier chỉ cho phép ES256; kid thuộc protected header.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Protected header của token: alg và kid để chọn thuật toán/khóa tin cậy.
 * - Cần làm: Issuer phải ký bằng ES256; verifier từ chối thuật toán khác kể cả header do client khai báo.
 * - Lưu ý bàn giao: Không tìm khóa qua URL không tin cậy trong token; kid không chuyển thành payload claim.
 */
public record JwsHeader(String alg, String kid) {
    public static final String ALGORITHM = "ES256";
}
