package vn.tmc.central.systemadmin.internal.controller;

import vn.tmc.central.systemadmin.api.GateManagement;
import vn.tmc.central.systemadmin.api.GateView;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: controller.
 * NGUỒN: main.pdf, FR-GS-01..06; UC-04.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 5]: API cấu hình, heartbeat và yêu cầu manual/emergency có re-auth.
 * TODO[Task 5]: Chốt transport xuống Edge; web không tự gọi thiết bị cổng.
 * PHỐI HỢP: Task 1, Task 3, Task 6, Task 7.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận yêu cầu thuộc module, chuyển input đã kiểm tra cho service/API interface được inject.
 * - Cần làm: Bổ sung HTTP route, validation, danh tính và data scope; ánh xạ lỗi theo ApiError, không viết SQL tại controller.
 * - Lưu ý bàn giao: Hiện chỉ có method Java gọi service; cần bean wiring và test trái quyền trước khi coi endpoint hoạt động.
 */
public final class GateController {
    private final GateManagement service;

    public GateController(GateManagement service) {
        this.service = java.util.Objects.requireNonNull(service);
    }

    // TODO[Task 5]: HTTP mapping, authenticated identity, RBAC/scope and validation.
    public GateView findGate(String gateId) {
        return service.findGate(gateId);
    }
}
