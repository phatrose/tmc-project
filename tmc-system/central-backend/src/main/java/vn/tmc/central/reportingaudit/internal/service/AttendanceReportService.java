package vn.tmc.central.reportingaudit.internal.service;

import vn.tmc.central.reportingaudit.api.ReportingQueries;
import vn.tmc.central.reportingaudit.api.AttendanceReportView;
import vn.tmc.central.reportingaudit.internal.repository.AttendanceReportRepository;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 4.
 * VAI TRÒ: service.
 * NGUỒN: main.pdf, FR-RP-01/02/04/06.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 * TODO[Task 4]: Báo cáo ngày/tuần/tháng, dashboard, đối soát và CSV/Excel/PDF.
 * TODO[Task 4]: Dùng dữ liệu dẫn xuất qua query port, thể hiện dữ liệu trễ do offline.
 * PHỐI HỢP: Task 3, Task 5, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Xử lý nghiệp vụ của module qua API interface và repository/port được inject.
 * - Cần làm: Thực hiện validation, quyền/scope và transaction trước khi đọc/ghi; hoàn thiện các phương thức TODO theo DECISIONS.md.
 * - Lưu ý bàn giao: Không trả dữ liệu thành công giả; kiểm thử luồng hợp lệ, dữ liệu thiếu, trái quyền và lỗi dependency.
 */
public final class AttendanceReportService implements ReportingQueries {
    private final AttendanceReportRepository repository;

    public AttendanceReportService(AttendanceReportRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    // TODO: use repository after authorization and business rules are implemented.
    @Override
    public AttendanceReportView generateReport(String factoryId, String period) {
        throw new UnsupportedOperationException("TODO: generateReport");
    }
}
