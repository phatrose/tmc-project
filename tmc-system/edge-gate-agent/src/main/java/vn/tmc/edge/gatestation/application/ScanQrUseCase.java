package vn.tmc.edge.gatestation.application;

import vn.tmc.edge.gatestation.domain.QrVerificationPort;
import vn.tmc.edge.gatestation.domain.AccessPolicyPort;
import vn.tmc.edge.edgesync.api.LocalEventStorePort;
import vn.tmc.edge.gatestation.domain.TurnstileControlPort;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 2.
 * VAI TRÒ: Use case / worker.
 * NGUỒN: main.pdf, UC-02/03/04; §6.2; §7.3.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 * TODO[Task 2]: Áp dụng policy/ca và chống trùng nghiệp vụ 30 giây, độc lập replay jti.
 * TODO[Task 2]: Reserve jti + cấp sequence + ghi event/delivery trong một transaction trước mở.
 * TODO[Task 2]: Kết quả command gửi riêng EVT-03; denied attempt EVT-02; không sửa event.
 * PHỐI HỢP: Task 1, Task 3, Task 6, Task 7.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận token QR thô và gateId từ scanner đã được xác thực ở LAN.
 * - Cần làm: Điều phối verifier → policy cache → reserve jti/lưu event → lệnh cổng; chỉ mở sau commit thành công.
 * - Lưu ý bàn giao: Tách lỗi xác minh, bị từ chối và lỗi thiết bị; không chờ Central và không sửa event sau kết quả mở.
 */
public final class ScanQrUseCase {
    private final QrVerificationPort qrVerificationPort;
    private final AccessPolicyPort accessPolicyPort;
    private final LocalEventStorePort localEventStorePort;
    private final TurnstileControlPort turnstileControlPort;

    public ScanQrUseCase(QrVerificationPort qrVerificationPort, AccessPolicyPort accessPolicyPort, LocalEventStorePort localEventStorePort, TurnstileControlPort turnstileControlPort) {
        this.qrVerificationPort = java.util.Objects.requireNonNull(qrVerificationPort);
        this.accessPolicyPort = java.util.Objects.requireNonNull(accessPolicyPort);
        this.localEventStorePort = java.util.Objects.requireNonNull(localEventStorePort);
        this.turnstileControlPort = java.util.Objects.requireNonNull(turnstileControlPort);
    }

    public void scan(String token, String gateId) {
        throw new UnsupportedOperationException("TODO: scan");
    }
}
