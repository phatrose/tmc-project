package vn.tmc.contracts;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 8.
 * VAI TRÒ: DTO lỗi.
 * NGUỒN: main.pdf, §4.2.3.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 8]: code, message, requestId, retryable; không stack trace/secret.
 * TODO[Task 8]: Phân biệt lỗi HTTP request 400/401/403/429/500/503 với lỗi ACK riêng event.
 * PHỐI HỢP: Task 1–7 theo contract liên quan.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: DTO lỗi gồm code ổn định, message hiển thị, requestId để tra cứu và retryable.
 * - Cần làm: Thống nhất mã lỗi giữa controller và HTTP client; client không suy luận quyền retry chỉ từ message.
 * - Lưu ý bàn giao: Không trả stack trace, token hoặc thông tin nội bộ nhạy cảm cho người dùng.
 */
public record ApiError(String code, String message, String requestId, boolean retryable) {}
