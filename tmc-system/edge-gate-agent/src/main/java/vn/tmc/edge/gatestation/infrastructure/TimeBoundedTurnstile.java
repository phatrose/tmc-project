package vn.tmc.edge.gatestation.infrastructure;

import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;
import vn.tmc.edge.gatestation.domain.CommandResult;
import vn.tmc.edge.gatestation.domain.TurnstileControlPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * PHỤ TRÁCH: Task 2. Decorator áp timeout 1 giây (CONTRACT-V1.md §5) lên bất kỳ TurnstileControlPort nào, để adapter
 * thật chỉ lo giao thức còn việc "không để scan chờ vô hạn" nằm ở một chỗ (SRP, Open/Closed).
 * Không retry: quá hạn là TIMEOUT (kết quả chưa rõ, thiết bị có thể vẫn đã mở); exception của adapter là FAILED.
 * Mỗi lệnh chạy trên một virtual thread riêng nên một thiết bị treo không làm nghẽn các cổng khác.
 */
public final class TimeBoundedTurnstile implements TurnstileControlPort, AutoCloseable {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(1);

    private final TurnstileControlPort delegate;
    private final Duration timeout;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public TimeBoundedTurnstile(TurnstileControlPort delegate, Duration timeout, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout phải dương");
        }
    }

    public TimeBoundedTurnstile(TurnstileControlPort delegate, Clock clock) {
        this(delegate, DEFAULT_TIMEOUT, clock);
    }

    @Override
    public CommandResult unlock(UUID commandId, UUID gateId) {
        Future<CommandResult> call = executor.submit(() -> delegate.unlock(commandId, gateId));
        try {
            CommandResult result = call.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            return result != null ? result : outcome(commandId, CommandStatus.FAILED, "EMPTY_ADAPTER_RESULT");
        } catch (TimeoutException late) {
            call.cancel(true);
            return outcome(commandId, CommandStatus.TIMEOUT, "ADAPTER_TIMEOUT");
        } catch (ExecutionException failure) {
            return outcome(commandId, CommandStatus.FAILED, "ADAPTER_ERROR");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            call.cancel(true);
            return outcome(commandId, CommandStatus.TIMEOUT, "INTERRUPTED");
        }
    }

    private CommandResult outcome(UUID commandId, CommandStatus status, String code) {
        return new CommandResult(commandId, status, Instant.ofEpochMilli(clock.millis()), code);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
