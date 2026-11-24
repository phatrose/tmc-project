package vn.tmc.contracts;

import java.util.UUID;
import java.util.List;
import java.util.HashSet;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 7.
 * VAI TRÒ: DTO request sync.
 * NGUỒN: main.pdf, §4.2–4.3.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 7]: syncBatchId và events tối đa 50; retry giữ ID/membership.
 * TODO[Task 7]: Chốt validation envelope với mục PDF 5, không dùng schema demo cũ.
 * PHỐI HỢP: Task 2, Task 3, Task 6, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: syncBatchId định danh lô; events chứa các envelope nguyên bản, tối đa MAX_EVENTS=50.
 * - Cần làm: Bổ sung kiểm tra null/rỗng/trùng ID và scope; batch ID/membership phải được lưu trước lần gửi đầu.
 * - Lưu ý bàn giao: Không tái tạo ID hoặc hash khi retry; record hiện chưa tự validation hoặc sao chép bất biến danh sách.
 */
public record SyncBatchRequest(UUID syncBatchId, List<EventEnvelope> events) {
    public static final int MAX_EVENTS = 50;
    public static final int MAX_BODY_BYTES = 1024 * 1024;

    public SyncBatchRequest {
        if (syncBatchId == null || syncBatchId.version() != 4 || syncBatchId.variant() != 2) {
            throw new IllegalArgumentException("syncBatchId must be UUIDv4");
        }
        events = List.copyOf(events);
        if (events.isEmpty() || events.size() > MAX_EVENTS) {
            throw new IllegalArgumentException("A sync batch must contain 1 to 50 events");
        }
        var first = events.getFirst();
        var ids = new HashSet<UUID>();
        long previous = 0;
        for (var event : events) {
            if (event.eventId() == null || !ids.add(event.eventId()) || event.sequenceNo() <= previous
                    || event.sequenceNo() > 9_007_199_254_740_991L
                    || first.edgeId() == null || !first.edgeId().equals(event.edgeId())
                    || first.factoryId() == null || !first.factoryId().equals(event.factoryId())) {
                throw new IllegalArgumentException("Sync batch members must have unique IDs, increasing sequences and one identity");
            }
            previous = event.sequenceNo();
        }
    }
}
