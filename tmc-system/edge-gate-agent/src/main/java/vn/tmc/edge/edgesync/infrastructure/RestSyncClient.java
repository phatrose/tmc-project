package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.edge.edgesync.domain.SyncPort;
import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchRequest;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 7.
 * VAI TRÒ: Adapter / Controller.
 * NGUỒN: main.pdf, §4.2–4.3; §6.3.3.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 7]: HTTPS/mTLS với identity thiết bị; endpoint /api/v1/attendance/sync.
 * TODO[Task 7]: Giữ batch payload ổn định; parse ACK từng event, phân biệt lỗi request/event.
 * PHỐI HỢP: Task 2, Task 3, Task 6, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận SyncBatchRequest; trả SyncBatchAck từ Central sau khi xác thực phản hồi/contract.
 * - Cần làm: Bổ sung HTTPS/mTLS, timeout và ánh xạ lỗi 400/401/403/429/5xx; credentials lấy từ cấu hình ngoài Git.
 * - Lưu ý bàn giao: Không cập nhật SQLite trong adapter HTTP; không coi HTTP 200 là toàn bộ event đã được chấp nhận.
 */
public final class RestSyncClient implements SyncPort {
    @Override
    public SyncBatchAck send(SyncBatchRequest batch) {
        throw new UnsupportedOperationException("TODO: send");
    }
}
