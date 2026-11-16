package vn.tmc.central.attendanceprocessing.internal.controller;

import vn.tmc.central.attendanceprocessing.api.AttendanceQueries;
import vn.tmc.central.attendanceprocessing.api.AttendanceView;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: controller.
 * NGUỒN: main.pdf, §4.2.1; FR-AT-05/06.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 3]: GET /api/v1/attendance/events với page/size và data scope.
 * TODO[Task 3]: POST /api/v1/attendance/adjustments, tách quyền tạo/duyệt.
 * PHỐI HỢP: Task 2, Task 4, Task 7.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận yêu cầu thuộc module, chuyển input đã kiểm tra cho service/API interface được inject.
 * - Cần làm: Bổ sung HTTP route, validation, danh tính và data scope; ánh xạ lỗi theo ApiError, không viết SQL tại controller.
 * - Lưu ý bàn giao: Hiện chỉ có method Java gọi service; cần bean wiring và test trái quyền trước khi coi endpoint hoạt động.
 */
public final class AttendanceController {
    private final AttendanceQueries service;

    public AttendanceController(AttendanceQueries service) {
        this.service = java.util.Objects.requireNonNull(service);
    }

    // TODO[Task 3]: HTTP mapping, authenticated identity, RBAC/scope and validation.
    public AttendanceView findAttendance(String employeeId) {
        return service.findAttendance(employeeId);
    }
}
