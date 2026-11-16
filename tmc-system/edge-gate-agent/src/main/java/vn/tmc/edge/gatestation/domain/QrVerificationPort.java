package vn.tmc.edge.gatestation.domain;



/**
 * PHỤ TRÁCH: Task 6.
 * Khung triển khai theo docs/DECISIONS.md; chưa có nghiệp vụ hoạt động.
 * Xác minh bằng khóa/cache cục bộ. Không chờ phản hồi Central trong lượt quét.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận JWS; trả VerifiedQr gồm claims đã xác minh và kid, chưa phải quyết định cho qua.
 * - Cần làm: Adapter phải kiểm tra ES256/chữ ký/issuer/audience/thời gian và revocation cache.
 * - Lưu ý bàn giao: Không trả chuỗi employeeId thay cho claims; use case còn cần jti để reserve replay và sub để kiểm tra quyền.
 */
public interface QrVerificationPort {
    VerifiedQr verify(String token);
}
