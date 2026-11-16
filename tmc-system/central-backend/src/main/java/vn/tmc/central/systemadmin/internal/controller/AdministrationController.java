package vn.tmc.central.systemadmin.internal.controller;

import vn.tmc.central.systemadmin.api.Administration;
import vn.tmc.central.systemadmin.api.SystemConfigurationView;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: controller.
 * NGUỒN: main.pdf, FR-SA-01..06; §2.3.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 5]: API nhà máy/cổng/ca, cấu hình và vận hành/DSR; tài khoản/role thuộc user-mgmt.
 * TODO[Task 5]: Bearer auth và data scope; thao tác nhạy cảm có re-auth/audit.
 * PHỐI HỢP: Task 1, Task 4, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận yêu cầu thuộc module, chuyển input đã kiểm tra cho service/API interface được inject.
 * - Cần làm: Bổ sung HTTP route, validation, danh tính và data scope; ánh xạ lỗi theo ApiError, không viết SQL tại controller.
 * - Lưu ý bàn giao: Hiện chỉ có method Java gọi service; cần bean wiring và test trái quyền trước khi coi endpoint hoạt động.
 */
public final class AdministrationController {
    private final Administration service;

    public AdministrationController(Administration service) {
        this.service = java.util.Objects.requireNonNull(service);
    }

    // TODO[Task 5]: HTTP mapping, authenticated identity, RBAC/scope and validation.
    public SystemConfigurationView findConfiguration(String key) {
        return service.findConfiguration(key);
    }
}
