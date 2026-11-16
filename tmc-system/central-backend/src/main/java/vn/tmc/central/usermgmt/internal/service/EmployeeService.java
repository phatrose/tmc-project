package vn.tmc.central.usermgmt.internal.service;

import vn.tmc.central.usermgmt.api.EmployeeManagement;
import vn.tmc.central.usermgmt.api.EmployeeView;
import vn.tmc.central.usermgmt.internal.repository.EmployeeRepository;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 1.
 * VAI TRÒ: service.
 * NGUỒN: main.pdf, FR-UQ-01..06.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 * TODO[Task 1]: Đồng bộ HR, cập nhật trạng thái, quyền khu vực/ca.
 * TODO[Task 1]: Cấp/thu hồi/cấp lại credential qua port QR; không ký token tại đây.
 * PHỐI HỢP: Task 2, Task 5, Task 6.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Xử lý nghiệp vụ của module qua API interface và repository/port được inject.
 * - Cần làm: Thực hiện validation, quyền/scope và transaction trước khi đọc/ghi; hoàn thiện các phương thức TODO theo DECISIONS.md.
 * - Lưu ý bàn giao: Không trả dữ liệu thành công giả; kiểm thử luồng hợp lệ, dữ liệu thiếu, trái quyền và lỗi dependency.
 */
public final class EmployeeService implements EmployeeManagement {
    private final EmployeeRepository repository;

    public EmployeeService(EmployeeRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    // TODO: use repository after authorization and business rules are implemented.
    @Override
    public EmployeeView findEmployee(String employeeId) {
        throw new UnsupportedOperationException("TODO: findEmployee");
    }
}
