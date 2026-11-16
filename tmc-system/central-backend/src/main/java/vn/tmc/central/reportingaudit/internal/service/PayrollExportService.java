package vn.tmc.central.reportingaudit.internal.service;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 4.
 * VAI TRÒ: service.
 * NGUỒN: main.pdf, FR-RP-03; BR-07; UC-05.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 * TODO[Task 4]: Chốt chính thức 06:30 ngày 01 theo PeriodClosingSchedule; 23:00 chỉ tổng hợp tạm.
 * TODO[Task 4]: Ca đêm kết thúc 06:00 ngày 01 thuộc kỳ tháng trước; đối soát/xuất HR trước 12:00.
 * TODO[Task 4]: Outbox, batch ID/checksum, retry, PARTIAL và đối soát trước 12:00 ngày 01.
 * TODO[Task 4]: Không tính lương/thuế; chỉ xuất công sang HR mock.
 * PHỐI HỢP: Task 3, Task 5, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận factoryId và kỳ cần chốt/xuất; lấy dữ liệu qua API công khai của module chấm công.
 * - Cần làm: Chốt chính thức 06:30 ngày 01 theo timezone nhà máy; hoàn tất đối soát/xuất HR trước 12:00.
 * - Lưu ý bàn giao: 23:00 chỉ tổng hợp tạm; outbox/batch checksum/retry phải idempotent, không tính lương hoặc thuế.
 */
public final class PayrollExportService {
    public void exportPeriod(String factoryId, String period) {
        throw new UnsupportedOperationException("TODO: exportPeriod");
    }
}
