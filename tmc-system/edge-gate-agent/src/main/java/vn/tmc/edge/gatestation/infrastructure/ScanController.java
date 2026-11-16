package vn.tmc.edge.gatestation.infrastructure;

import vn.tmc.edge.gatestation.application.ScanQrUseCase;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 2.
 * VAI TRÒ: Adapter / Controller.
 * NGUỒN: main.pdf, §3.3.2; UC-02/03/04.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 2]: Chốt LAN transport scanner/kiosk và response lý do; gọi use case qua port.
 * TODO[Task 2]: Manual override có re-auth/operator/reason; không định nghĩa route hoạt động trong bản sườn.
 * PHỐI HỢP: Task 1, Task 3, Task 6, Task 7.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Khung chức năng được gọi qua contract cùng module.
 * - Cần làm: Hoàn thiện input/output, xử lý lỗi và phối hợp dependency theo DECISIONS.md trước tích hợp.
 * - Lưu ý bàn giao: Chưa có nghiệp vụ hoạt động; cần kiểm thử trước khi nối vào runtime.
 */
public final class ScanController {
    private final ScanQrUseCase useCase;
    public ScanController(ScanQrUseCase useCase) { this.useCase = java.util.Objects.requireNonNull(useCase); }
    public void scan(String token, String gateId) { useCase.scan(token, gateId); }
}
