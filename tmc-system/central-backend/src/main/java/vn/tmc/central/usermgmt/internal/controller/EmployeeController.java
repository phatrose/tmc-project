package vn.tmc.central.usermgmt.internal.controller;

import vn.tmc.central.usermgmt.api.EmployeeManagement;
import vn.tmc.central.usermgmt.api.EmployeeView;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 1.
 * VAI TRÒ: controller.
 * NGUỒN: main.pdf, §2.4.1, 3.1.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 1]: Đặt API đồng bộ/nhập/xem hồ sơ và phân quyền khu vực, ca làm.
 * TODO[Task 1]: URL cụ thể chưa có trong bảng 4.2: thống nhất trước khi đăng ký route.
 * PHỐI HỢP: Task 2, Task 5, Task 6.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận yêu cầu thuộc module, chuyển input đã kiểm tra cho service/API interface được inject.
 * - Cần làm: Bổ sung HTTP route, validation, danh tính và data scope; ánh xạ lỗi theo ApiError, không viết SQL tại controller.
 * - Lưu ý bàn giao: Hiện chỉ có method Java gọi service; cần bean wiring và test trái quyền trước khi coi endpoint hoạt động.
 */
public final class EmployeeController {
    private final EmployeeManagement service;

    public EmployeeController(EmployeeManagement service) {
        this.service = java.util.Objects.requireNonNull(service);
    }

    // TODO[Task 1]: HTTP mapping, authenticated identity, RBAC/scope and validation.
    public EmployeeView findEmployee(String employeeId) {
        return service.findEmployee(employeeId);
    }
}
