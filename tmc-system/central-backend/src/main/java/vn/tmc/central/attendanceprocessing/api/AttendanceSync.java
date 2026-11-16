package vn.tmc.central.attendanceprocessing.api;

import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.contracts.SyncBatchAck;

/** PHỤ TRÁCH: Task 3. TODO: Hoàn thiện contract và nghiệp vụ theo PDF.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận SyncBatchRequest; trả SyncBatchAck có kết quả từng event và cursor liên tục.
 * - Cần làm: Chốt implementation per-event transaction: commit rồi mới ACK; cùng ID/hash là DUPLICATE, khác hash là CONFLICT.
 * - Lưu ý bàn giao: Một event lỗi không rollback cả batch; không cho Edge sửa factory/edge identity ngoài binding xác thực.
 */
public interface AttendanceSync {
    SyncBatchAck synchronize(SyncBatchRequest batch);
}
