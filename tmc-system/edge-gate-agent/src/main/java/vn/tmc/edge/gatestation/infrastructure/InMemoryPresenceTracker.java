package vn.tmc.edge.gatestation.infrastructure;

import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.edge.gatestation.domain.PresenceRegistry;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PHỤ TRÁCH: Task 2. Hiện diện cục bộ trong bộ nhớ: mỗi nhân viên giữ lượt đã được thiết bị chấp nhận mới nhất.
 * Hạn chế đã biết (FR-AT-07 là mức C): mất khi khởi động lại nên Snapshot mang trackedSince để UI/bảo vệ biết dữ liệu
 * chỉ tính từ mốc đó; nhà máy nhiều Edge hoặc mất WAN thì không đầy đủ (coverage LOCAL_EDGE). Cách đúng lâu dài là
 * truy vấn lượt ALLOW mới nhất từ SQLite, cần Task 7 bổ sung port truy vấn.
 */
public final class InMemoryPresenceTracker implements PresenceRegistry {
    private final Clock clock;
    private final Instant trackedSince;
    private final Map<UUID, Item> latest = new ConcurrentHashMap<>();

    public InMemoryPresenceTracker(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.trackedSince = Instant.ofEpochMilli(clock.millis());
    }

    @Override
    public void recordPassage(UUID employeeId, Direction direction, Instant occurredAt) {
        latest.merge(employeeId, new Item(employeeId, direction, occurredAt),
                (current, next) -> next.lastOccurredAt().isBefore(current.lastOccurredAt()) ? current : next);
    }

    @Override
    public Snapshot snapshot() {
        var items = latest.values().stream().sorted(Comparator.comparing(item -> item.employeeId().toString())).toList();
        return new Snapshot(Instant.ofEpochMilli(clock.millis()), trackedSince, items);
    }
}
