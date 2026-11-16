package vn.tmc.central;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 8.
 * VAI TRÒ: Bootstrap.
 * NGUỒN: main.pdf, §1.2, 1.4.
 * TÀI LIỆU PDF: docs/tasks/01-context-allocation.md; docs/REQUIREMENTS.md.
 * TODO[Task 8]: Tạo entry point Spring Boot và wiring sau khi có module implementation.
 * TODO[Task 8]: Đăng ký Spring Modulith/module API cùng mục PDF 3; cấu hình từ môi trường.
 * PHỐI HỢP: Task 1–7 theo contract liên quan.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Entry point khởi tạo tiến trình tương ứng; không phải nơi đặt luật nghiệp vụ.
 * - Cần làm: Bổ sung cấu hình môi trường, adapter/bean wiring và thứ tự khởi động; secret/certificate lấy ngoài mã nguồn.
 * - Lưu ý bàn giao: Cần database/schema/auth hoặc cache tin cậy trước khi chạy; compile thành công chưa chứng minh runtime hoạt động.
 */
@SpringBootApplication
public class CentralApplication {
    public static void main(String[] args) { SpringApplication.run(CentralApplication.class, args); }
    // TODO[Task 8]: database, migrations, auth, explicit bean wiring and HTTP routes.
}
