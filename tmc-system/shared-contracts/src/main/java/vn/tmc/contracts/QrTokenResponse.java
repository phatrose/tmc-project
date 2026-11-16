package vn.tmc.contracts;

import java.time.Instant;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 6.
 * VAI TRÒ: DTO QR.
 * NGUỒN: main.pdf, §4.2, §7.2.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 6]: Response GET /api/v1/me/qr-token: JWS và expiresAt; không log token.
 * TODO[Task 6]: Chốt tên field/kiểu với mục PDF 7, frontend không tự ký hoặc tự cấp quyền mở cổng.
 * PHỐI HỢP: Task 1, Task 2, Task 7.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Trả JWS và expiresAt cho màn hình QR sau khi backend xác thực người dùng.
 * - Cần làm: Frontend dùng expiresAt để đếm hạn/cấp lại; thời gian UI không thay cho kiểm tra exp tại Edge.
 * - Lưu ý bàn giao: Không gửi DTO này qua email/SMS; toString đã che token nhưng serializer/logger vẫn cần cấu hình tránh lộ.
 */
public record QrTokenResponse(String token, Instant expiresAt) {
    @Override public String toString() { return "QrTokenResponse[token=REDACTED, expiresAt=" + expiresAt + "]"; }
}
