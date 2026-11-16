package vn.tmc.edge.gatestation.domain;

/** PHỤ TRÁCH: Task 2. TODO: Hoàn thiện contract và nghiệp vụ theo PDF.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận commandId ổn định cho một lệnh mở; không dùng eventId như kết quả mở cổng.
 * - Cần làm: Chỉ được gọi sau khi transaction lưu event hoàn tất; chốt timeout và ACK thiết bị với Task 2.
 * - Lưu ý bàn giao: Lệnh được nhận không chứng minh người đã đi qua; crash/retry không được tự mở lại một cách mù quáng.
 */
public interface TurnstileControlPort {
    void unlock(String commandId);
}
