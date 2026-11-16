package vn.tmc.edge.edgesync.domain;



/**
 * PHỤ TRÁCH: Task 7.
 * Khung triển khai theo docs/DECISIONS.md; chưa có nghiệp vụ hoạt động.
 * Chỉ dùng cho event_delivery, không gắn vào event bất biến.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Ba trạng thái của một event trong event_delivery, không phải trạng thái của batch.
 * - Cần làm: PENDING khi chưa có ACK thành công, ACKNOWLEDGED khi ACCEPTED/DUPLICATE, FAILED khi lỗi vĩnh viễn.
 * - Lưu ý bàn giao: Timeout giữ PENDING; không thêm SYNCED/PENDING_SYNC làm tên thay thế.
 */
public enum DeliveryState { PENDING, ACKNOWLEDGED, FAILED }
