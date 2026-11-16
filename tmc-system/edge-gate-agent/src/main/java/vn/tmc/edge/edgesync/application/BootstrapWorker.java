package vn.tmc.edge.edgesync.application;

import vn.tmc.edge.edgesync.domain.BootstrapPort;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 7.
 * VAI TRÒ: Use case / worker.
 * NGUỒN: main.pdf, §6.2.2–6.2.3; §7.3–7.4.
 * TÀI LIỆU PDF: docs/tasks/06-edge-offline.md; docs/REQUIREMENTS.md.
 * TODO[Task 7]: mTLS bootstrap delta revision, public keys và policy; chu kỳ dự kiến 30 giây; mục tiêu lan truyền revocation online ≤60 giây.
 * TODO[Task 7]: Áp dụng dữ liệu và cursor cùng transaction; không chấp nhận revision lùi.
 * TODO[Task 7]: Phân biệt revocation cache cũ hợp lệ với cache thiếu/hỏng và trusted key hết hạn.
 * TODO[Task 7]: Offline tối thiểu 24 giờ với cache tin cậy được chuẩn bị trước; chấp nhận cửa sổ thu hồi trễ.
 * TODO[Task 7]: Xác thực cập nhật trước commit; reconnect đồng bộ event/revocation và đối soát POST_REVOKE_VIOLATION.
 * PHỐI HỢP: Task 2, Task 3, Task 6, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận delta/snapshot public key, revocation và policy thông qua BootstrapPort.
 * - Cần làm: Xác thực dữ liệu rồi commit cache và revision cùng transaction; online mục tiêu cập nhật thu hồi ≤60 giây.
 * - Lưu ý bàn giao: Offline dùng cache tin cậy đã chuẩn bị cho ≥24 giờ; reconnect cập nhật và phối hợp đối soát vi phạm thu hồi.
 */
public final class BootstrapWorker {
    private final BootstrapPort bootstrapPort;

    public BootstrapWorker(BootstrapPort bootstrapPort) {
        this.bootstrapPort = java.util.Objects.requireNonNull(bootstrapPort);
    }

    public void refresh() {
        throw new UnsupportedOperationException("TODO: refresh");
    }
}
