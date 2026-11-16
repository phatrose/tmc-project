package vn.tmc.edge.edgesync.application;

import vn.tmc.edge.edgesync.api.LocalEventStorePort;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 7.
 * VAI TRÒ: Use case / worker.
 * NGUỒN: main.pdf, §6.3.4.
 * TÀI LIỆU PDF: docs/tasks/06-edge-offline.md; docs/REQUIREMENTS.md.
 * TODO[Task 7]: Khôi phục sequence, batch IN_FLIGHT về RETRY_WAIT và cache revision.
 * TODO[Task 7]: Không tự phát lại lệnh mở cổng sau crash; đối soát commandId riêng.
 * TODO[Task 7]: Chỉ dọn ACKNOWLEDGED theo retention; giữ PENDING/FAILED, cảnh báo dung lượng.
 * PHỐI HỢP: Task 2, Task 3, Task 6, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Chạy sau khi mở SQLite và trước khi worker xử lý queue trở lại.
 * - Cần làm: Phục hồi sequence/cache cursor, đưa batch IN_FLIGHT về lịch retry với cùng ID và membership.
 * - Lưu ý bàn giao: Không phát lại lệnh mở cổng sau crash; chỉ dọn ACKNOWLEDGED theo retention đã thống nhất.
 */
public final class RecoveryWorker {
    private final LocalEventStorePort localEventStorePort;

    public RecoveryWorker(LocalEventStorePort localEventStorePort) {
        this.localEventStorePort = java.util.Objects.requireNonNull(localEventStorePort);
    }

    public void recover() {
        throw new UnsupportedOperationException("TODO: recover");
    }
}
