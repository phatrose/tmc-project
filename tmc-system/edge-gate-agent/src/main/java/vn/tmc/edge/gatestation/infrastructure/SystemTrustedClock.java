package vn.tmc.edge.gatestation.infrastructure;

import vn.tmc.edge.gatestation.domain.ClockReading;
import vn.tmc.edge.gatestation.domain.TrustedClockPort;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * PHỤ TRÁCH: Task 2. Đọc đồng hồ hệ thống, kèm cờ tin cậy do nguồn giám sát cấp (NFR-12: sai lệch NTP ≤ 1 giây).
 * Edge không tự đo NTP; wiring truyền BooleanSupplier phản ánh tình trạng đồng bộ thời gian của máy (Task 7/8
 * cung cấp khi có monitor). Mặc định coi là tin cậy để không chặn vận hành khi chưa có monitor.
 */
public final class SystemTrustedClock implements TrustedClockPort {
    private final Clock clock;
    private final BooleanSupplier healthy;

    public SystemTrustedClock(Clock clock, BooleanSupplier healthy) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.healthy = Objects.requireNonNull(healthy, "healthy");
    }

    public SystemTrustedClock(Clock clock) {
        this(clock, () -> true);
    }

    @Override
    public ClockReading read() {
        return new ClockReading(Instant.ofEpochMilli(clock.millis()), healthy.getAsBoolean());
    }
}
