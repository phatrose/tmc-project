package vn.tmc.edge.edgesync.api;

import vn.tmc.contracts.EventEnvelope;
import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.contracts.SyncBatchAck;

/**
 * PHỤ TRÁCH: Task 7.
 * Khung triển khai theo docs/DECISIONS.md; chưa có nghiệp vụ hoạt động.
 *
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Port do edge-sync cung cấp cho luồng quét và worker; nhận event/batch ACK đã kiểm tra contract.
 * - Cần làm: reserveAndAppend là ranh giới transaction cho replay, sequence, event và delivery.
 * - Lưu ý bàn giao: Giao diện này không gửi HTTP hoặc điều khiển cổng; lưu lỗi phải truyền lỗi lên caller, không nuốt lỗi.
 */
public interface LocalEventStorePort {
    // TODO: reserve jti + sequence + event + PENDING trong một transaction WAL.
    // Trùng jti/lưu lỗi phải từ chối, không mở cổng. jti null chỉ cho sự kiện không dùng QR.
    void reserveAndAppend(EventEnvelope event, String jti);
    // TODO: lưu EVT-02/EVT-03 riêng; không sửa payload event gốc.
    void append(EventEnvelope event);
    // TODO: lưu lô/membership bền vững; giới hạn 50 và giữ nguyên qua retry.
    SyncBatchRequest pendingBatch();
    // TODO: ACK và cursor cùng transaction; không ACK hàng loạt chỉ vì HTTP 200.
    void applyAcknowledgements(SyncBatchAck acknowledgement);
}
