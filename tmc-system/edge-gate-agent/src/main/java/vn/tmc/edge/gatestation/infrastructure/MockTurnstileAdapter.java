package vn.tmc.edge.gatestation.infrastructure;

import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;
import vn.tmc.edge.gatestation.domain.CommandResult;
import vn.tmc.edge.gatestation.domain.TurnstileControlPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2. Turnstile giả cho phát triển/kiểm thử (CONTRACT-V1.md §5 "Turnstile mock"); không gọi thiết bị thật.
 * Giữ đúng hợp đồng TurnstileControlPort để thay được bằng adapter thật (LSP):
 * - cùng commandId trả lại đúng kết quả cũ và KHÔNG mở thêm lần nữa (dedup 48 giờ);
 * - có kịch bản lỗi: ACCEPT (nhận lệnh), FAIL (thiết bị từ chối), TIMEOUT (không phản hồi, kết quả chưa rõ);
 * - không ném exception ra ngoài cho lỗi thiết bị.
 * Mặc định ACCEPT; script(gateId, ...) xếp hàng kịch bản dùng một lần cho từng cổng; latency mô phỏng độ trễ thiết bị.
 * UNLOCK_ACCEPTED chỉ nghĩa là đã nhận lệnh, không phải người đã đi qua cổng.
 */
public final class MockTurnstileAdapter implements TurnstileControlPort {
    public enum Behavior { ACCEPT, FAIL, TIMEOUT }

    static final Duration DEDUP_RETENTION = Duration.ofHours(48);

    private record Remembered(CommandResult result, Instant storedAt) {}

    private final Clock clock;
    private final Duration latency;
    private Behavior defaultBehavior = Behavior.ACCEPT;
    private final Map<UUID, Deque<Behavior>> scripts = new HashMap<>();
    private final Map<UUID, Remembered> byCommand = new HashMap<>();
    private final Map<UUID, Integer> physicalUnlocks = new HashMap<>();

    public MockTurnstileAdapter(Clock clock) {
        this(clock, Duration.ZERO);
    }

    public MockTurnstileAdapter(Clock clock, Duration latency) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.latency = Objects.requireNonNull(latency, "latency");
    }

    public synchronized MockTurnstileAdapter defaultBehavior(Behavior behavior) {
        this.defaultBehavior = Objects.requireNonNull(behavior);
        return this;
    }

    /** Xếp hàng kịch bản dùng một lần cho cổng; hết hàng thì dùng defaultBehavior. */
    public synchronized MockTurnstileAdapter script(UUID gateId, Behavior... next) {
        var queue = scripts.computeIfAbsent(gateId, id -> new ArrayDeque<>());
        for (Behavior behavior : next) {
            queue.add(Objects.requireNonNull(behavior));
        }
        return this;
    }

    /** Số lần "mở thật" của cổng (chỉ tính ACCEPT, không tính lần phát lại cùng commandId). */
    public synchronized int physicalUnlockCount(UUID gateId) {
        return physicalUnlocks.getOrDefault(gateId, 0);
    }

    @Override
    public synchronized CommandResult unlock(UUID commandId, UUID gateId) {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(gateId, "gateId");
        Instant now = clock.instant();
        byCommand.values().removeIf(entry -> entry.storedAt().plus(DEDUP_RETENTION).isBefore(now));

        Remembered previous = byCommand.get(commandId);
        if (previous != null) {
            return previous.result();
        }
        Deque<Behavior> queue = scripts.get(gateId);
        Behavior behavior = queue == null || queue.isEmpty() ? defaultBehavior : queue.poll();
        pause();
        Instant completedAt = Instant.ofEpochMilli(clock.millis());
        CommandResult result = switch (behavior) {
            case ACCEPT -> {
                physicalUnlocks.merge(gateId, 1, Integer::sum);
                yield new CommandResult(commandId, CommandStatus.UNLOCK_ACCEPTED, completedAt, null);
            }
            case FAIL -> new CommandResult(commandId, CommandStatus.FAILED, completedAt, "DEVICE_REJECTED");
            case TIMEOUT -> new CommandResult(commandId, CommandStatus.TIMEOUT, completedAt, "DEVICE_NO_RESPONSE");
        };
        byCommand.put(commandId, new Remembered(result, now));
        return result;
    }

    private void pause() {
        if (latency.isZero()) {
            return;
        }
        try {
            Thread.sleep(latency);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
