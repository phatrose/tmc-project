package vn.tmc.edge.edgesync.domain;

import java.util.UUID;
import java.time.Instant;
import vn.tmc.contracts.SyncBatchAck;

/**
 * PHỤ TRÁCH: Task 7.
 * Khung triển khai theo docs/DECISIONS.md; chưa có nghiệp vụ hoạt động.
 * TODO: ACCEPTED/DUPLICATE → ACKNOWLEDGED; CONFLICT/REJECTED → FAILED.
 * Timeout giữ PENDING; cập nhật trạng thái và cursor nguyên tử sau ACK.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Bản ghi trạng thái giao vận tách riêng, tham chiếu eventId bất biến.
 * - Cần làm: Cập nhật từ ACK hợp lệ cùng transaction với cursor; acknowledgedAt/failureCode cần quy ước nullability.
 * - Lưu ý bàn giao: Không chuyển FAILED về ACKNOWLEDGED nếu chưa có xử lý hợp lệ; không sửa event gốc để phản ánh delivery.
 */
public record EventDelivery(UUID eventId, DeliveryState state, SyncBatchAck.Status ackStatus, Instant acknowledgedAt, String failureCode) {}
