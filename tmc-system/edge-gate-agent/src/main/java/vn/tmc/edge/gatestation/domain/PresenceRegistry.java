package vn.tmc.edge.gatestation.domain;

import vn.tmc.contracts.AttendancePayload.Direction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2. Danh sách hiện diện CỤC BỘ tại Edge (GET /edge/v1/presence, coverage=LOCAL_EDGE).
 * Không phải danh sách đầy đủ của nhà máy khi có nhiều Edge hoặc mất WAN (CONTRACT-V1.md §5).
 */
public interface PresenceRegistry {
    /** Ghi nhận lượt đã được thiết bị chấp nhận lệnh mở. */
    void recordPassage(UUID employeeId, Direction direction, Instant occurredAt);

    Snapshot snapshot();

    record Item(UUID employeeId, Direction lastDirection, Instant lastOccurredAt) {}

    /** trackedSince: thời điểm registry bắt đầu theo dõi (khởi động tiến trình); dữ liệu trước mốc này không có. */
    record Snapshot(Instant asOf, Instant trackedSince, List<Item> items) {
        public Snapshot {
            items = List.copyOf(items);
        }
    }
}
