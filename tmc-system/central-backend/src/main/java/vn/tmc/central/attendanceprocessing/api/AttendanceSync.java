package vn.tmc.central.attendanceprocessing.api;

import vn.tmc.contracts.DeviceContext;
import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.contracts.SyncBatchAck;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: Port tiếp nhận sự kiện đồng bộ từ Edge theo CONTRACT-V1.md §7 & §10.
 * Method: DeviceContext, SyncBatchRequest -> SyncBatchAck
 * 
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Nhận SyncBatchRequest (1–50 sự kiện, sequence tăng dần).
 * - Per-event transaction: commit rồi mới ACK.
 * - Trùng eventId và cùng hash trả DUPLICATE (code = null, acceptedAt = commit time).
 * - Trùng eventId khác hash trả CONFLICT (code = "EVENT_CONTENT_CONFLICT", acceptedAt = null).
 * - Payload hash không hợp lệ trả REJECTED (code = "HASH_MISMATCH").
 * - Trả lastContiguousSequenceNo là cursor liên tục không có khoảng trống.
 */
public interface AttendanceSync {
    SyncBatchAck synchronize(DeviceContext deviceContext, SyncBatchRequest batch);

    default SyncBatchAck synchronize(SyncBatchRequest batch) {
        return synchronize(null, batch);
    }
}
