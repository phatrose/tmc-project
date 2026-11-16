package vn.tmc.contracts;



/**
 * PHỤ TRÁCH: Task 6.
 * Khung triển khai theo docs/DECISIONS.md; chưa có nghiệp vụ hoạt động.
 * Thời gian NumericDate (giây UTC). aud là dịch vụ xác minh, không phải nhà máy.
 * Không thêm factoryId: quyền nhà máy do access-policy cache tại Edge kiểm tra.
 * TODO: mỗi lần cấp có jti mới; không ghi token/JWS vào log.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Bảy claims đã chốt; iat/nbf/exp là số giây UTC, không phải milliseconds.
 * - Cần làm: aud là dịch vụ xác minh; quyền nhà máy do access-policy cache kiểm tra, không thêm factoryId mặc định.
 * - Lưu ý bàn giao: Bổ sung validation iss/aud/time/TTL và jti mới mỗi lần cấp; record không tự bảo đảm token hợp lệ.
 */
public record QrClaims(String sub, String iss, String aud, long iat, long nbf, long exp, String jti) {
    public static final long DEFAULT_TTL_SECONDS = 60;
}
