package vn.tmc.edge.edgesync.api;

import vn.tmc.contracts.EventEnvelope;
import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.contracts.SyncBatchAck;

/**
 * PHỤ TRÁCH: Task 7.
 * The local transaction boundary for Gate Station and the sync worker.
 */
public interface LocalEventStorePort {
    // TODO: reserve jti + sequence + event + PENDING trong một transaction WAL.
    // Trùng jti/lưu lỗi phải từ chối, không mở cổng. jti null chỉ cho sự kiện không dùng QR.
    void reserveAndAppend(EventEnvelope event, String jti);
    /** Durably append a non-attendance event and initialize its delivery state. */
    CommittedEvent append(EventDraft draft);
    // TODO: lưu lô/membership bền vững; giới hạn 50 và giữ nguyên qua retry.
    SyncBatchRequest pendingBatch();
    // TODO: ACK và cursor cùng transaction; không ACK hàng loạt chỉ vì HTTP 200.
    void applyAcknowledgements(SyncBatchAck acknowledgement);
}
