package vn.tmc.edge.edgesync.application;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 7.
 * VAI TRÒ: Use case / worker.
 * NGUỒN: main.pdf, §6.2.2; FR-GS-04; FR-SA-04.
 * TÀI LIỆU PDF: docs/tasks/06-edge-offline.md; docs/REQUIREMENTS.md.
 * TODO[Task 7]: Theo dõi WAN/heartbeat và backlog ở nền; source phản ánh thời điểm tạo event.
 * TODO[Task 7]: Không ping Central đồng bộ trong mỗi lượt quét để quyết định ONLINE/OFFLINE.
 * PHỐI HỢP: Task 2, Task 3, Task 6, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Theo dõi kết nối và lỗi nền để cung cấp trạng thái WAN cho worker/UI.
 * - Cần làm: Bổ sung timeout, thời gian quan sát và trạng thái chưa biết; không suy ra online chỉ từ một lần ping.
 * - Lưu ý bàn giao: Không ping Central đồng bộ trong mỗi lượt quét; source của event phản ánh lúc event được tạo.
 */
public final class ConnectivityMonitor {

    public boolean isOnline() {
        throw new UnsupportedOperationException("TODO: isOnline");
    }
}
