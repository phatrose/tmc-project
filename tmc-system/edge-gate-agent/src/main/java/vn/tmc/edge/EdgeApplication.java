package vn.tmc.edge;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 7.
 * VAI TRÒ: Bootstrap Edge.
 * NGUỒN: main.pdf, §6.
 * TÀI LIỆU PDF: docs/tasks/06-edge-offline.md; docs/REQUIREMENTS.md.
 * TODO[Task 7]: Wiring ports/adapters và startup recovery; không thêm Spring vào domain.
 * TODO[Task 7]: Load cấu hình thiết bị, SQLite, chứng thư và trusted public keys.
 * PHỐI HỢP: Task 2, Task 3, Task 6, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Entry point khởi tạo tiến trình tương ứng; không phải nơi đặt luật nghiệp vụ.
 * - Cần làm: Bổ sung cấu hình môi trường, adapter/bean wiring và thứ tự khởi động; secret/certificate lấy ngoài mã nguồn.
 * - Lưu ý bàn giao: Cần database/schema/auth hoặc cache tin cậy trước khi chạy; compile thành công chưa chứng minh runtime hoạt động.
 */
public final class EdgeApplication {
    private EdgeApplication() {}
    public static void main(String[] args) {
        throw new UnsupportedOperationException("TODO: configure SQLite, trusted keys, adapters and workers before starting Edge");
    }
}
