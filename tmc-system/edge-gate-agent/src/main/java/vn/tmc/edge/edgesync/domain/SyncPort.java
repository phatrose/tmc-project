package vn.tmc.edge.edgesync.domain;

import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchRequest;

/** PHỤ TRÁCH: Task 7. TODO: Hoàn thiện contract và nghiệp vụ theo PDF.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận lô có ID/membership ổn định; trả ACK gắn đúng batch và event.
 * - Cần làm: Quy định rõ lỗi transport, lỗi xác thực và kết quả ACK để worker quyết định retry.
 * - Lưu ý bàn giao: Không đổi payload hoặc bỏ event bị từ chối khỏi phản hồi; HTTP thành công khác với ACK thành công.
 */
public interface SyncPort {
    SyncBatchAck send(SyncBatchRequest batch);
}
