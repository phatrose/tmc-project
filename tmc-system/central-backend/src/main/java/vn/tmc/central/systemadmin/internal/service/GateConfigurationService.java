package vn.tmc.central.systemadmin.internal.service;

import vn.tmc.central.systemadmin.api.GateManagement;
import vn.tmc.central.systemadmin.api.GateView;
import vn.tmc.central.systemadmin.internal.repository.GateRepository;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: service.
 * NGUỒN: main.pdf, FR-GS-01..06; BR-10.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 * TODO[Task 5]: Quản lý cổng/scanner/Edge và cảnh báo thiết bị.
 * TODO[Task 5]: Manual phải có người/lý do; emergency không tạo chấm công giả.
 * PHỐI HỢP: Task 1, Task 3, Task 6, Task 7.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Xử lý nghiệp vụ của module qua API interface và repository/port được inject.
 * - Cần làm: Thực hiện validation, quyền/scope và transaction trước khi đọc/ghi; hoàn thiện các phương thức TODO theo DECISIONS.md.
 * - Lưu ý bàn giao: Không trả dữ liệu thành công giả; kiểm thử luồng hợp lệ, dữ liệu thiếu, trái quyền và lỗi dependency.
 */
public final class GateConfigurationService implements GateManagement {
    private final GateRepository repository;

    public GateConfigurationService(GateRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    // TODO: use repository after authorization and business rules are implemented.
    @Override
    public GateView findGate(String gateId) {
        throw new UnsupportedOperationException("TODO: findGate");
    }
}
