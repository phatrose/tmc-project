package vn.tmc.central.reportingaudit.internal.controller;

import vn.tmc.central.reportingaudit.api.ReportingQueries;
import vn.tmc.central.reportingaudit.api.AttendanceReportView;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 4.
 * VAI TRÒ: controller.
 * NGUỒN: main.pdf, FR-RP-01..06; §4.2.1.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 4]: GET /api/v1/reports/attendance; thống nhất route audit/export còn lại.
 * TODO[Task 4]: Lọc kỳ/nhà máy/phòng ban theo RBAC, requestId và phân trang.
 * PHỐI HỢP: Task 3, Task 5, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận yêu cầu thuộc module, chuyển input đã kiểm tra cho service/API interface được inject.
 * - Cần làm: Bổ sung HTTP route, validation, danh tính và data scope; ánh xạ lỗi theo ApiError, không viết SQL tại controller.
 * - Lưu ý bàn giao: Hiện chỉ có method Java gọi service; cần bean wiring và test trái quyền trước khi coi endpoint hoạt động.
 */
public final class ReportAuditController {
    private final ReportingQueries service;

    public ReportAuditController(ReportingQueries service) {
        this.service = java.util.Objects.requireNonNull(service);
    }

    // TODO[Task 4]: HTTP mapping, authenticated identity, RBAC/scope and validation.
    public AttendanceReportView generateReport(String factoryId, String period) {
        return service.generateReport(factoryId, period);
    }
}
