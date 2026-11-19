package vn.tmc.contracts;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 7.
 * VAI TRÒ: DTO ACK.
 * NGUỒN: main.pdf, §4.3, §5.2, §6.3.2.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 7]: syncBatchId, acks theo eventId/status/acceptedAt/code, lastContiguousSequenceNo.
 * TODO[Task 7]: ACCEPTED/DUPLICATE/CONFLICT/REJECTED riêng từng event; HTTP 200 không có nghĩa cả lô thành công.
 * PHỐI HỢP: Task 2, Task 3, Task 6, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Central trả syncBatchId, ACK theo eventId và lastContiguousSequenceNo.
 * - Cần làm: ACCEPTED/DUPLICATE cho phép ACKNOWLEDGED; CONFLICT/REJECTED thành FAILED, giữ lại để điều tra.
 * - Lưu ý bàn giao: Kiểm tra ACK thuộc lô đã gửi; không tăng cursor theo max sequence khi vẫn còn khoảng trống.
 */
public record SyncBatchAck(UUID syncBatchId, List<EventAck> acks, long lastContiguousSequenceNo) {
    public SyncBatchAck {
        acks = acks == null ? null : List.copyOf(acks);
    }

    public enum Status { ACCEPTED, DUPLICATE, CONFLICT, REJECTED }
    public record EventAck(UUID eventId, Status status, Instant acceptedAt, String code) {}
}
