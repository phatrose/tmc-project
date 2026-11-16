package vn.tmc.edge.edgesync.application;

import vn.tmc.edge.edgesync.api.LocalEventStorePort;
import vn.tmc.edge.edgesync.domain.SyncPort;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 7.
 * VAI TRÒ: Use case / worker.
 * NGUỒN: main.pdf, FR-OS-03..10; §6.3.
 * TÀI LIỆU PDF: docs/tasks/06-edge-offline.md; docs/REQUIREMENTS.md.
 * TODO[Task 7]: FIFO sequenceNo, một batch hoạt động, tối đa 50; lưu ID và membership trước gửi.
 * TODO[Task 7]: Retry cùng event/hash/payload/syncBatchId; ACK áp dụng riêng từng event.
 * TODO[Task 7]: Backoff full jitter 5 giây tới 5 phút, tôn trọng Retry-After; FAILED không chặn queue sau.
 * PHỐI HỢP: Task 2, Task 3, Task 6, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Đọc lô đã lưu bền vững từ LocalEventStorePort và gửi qua SyncPort.
 * - Cần làm: Giữ nguyên syncBatchId/membership/payload khi retry; áp dụng ACK riêng từng event sau phản hồi.
 * - Lưu ý bàn giao: Timeout không đồng nghĩa Central chưa lưu; giữ PENDING, backoff/jitter và không tạo batch mới cho lần retry.
 */
public final class SyncWorker {
    private final LocalEventStorePort localEventStorePort;
    private final SyncPort syncPort;

    public SyncWorker(LocalEventStorePort localEventStorePort, SyncPort syncPort) {
        this.localEventStorePort = java.util.Objects.requireNonNull(localEventStorePort);
        this.syncPort = java.util.Objects.requireNonNull(syncPort);
    }

    public void runOnce() {
        throw new UnsupportedOperationException("TODO: runOnce");
    }
}
