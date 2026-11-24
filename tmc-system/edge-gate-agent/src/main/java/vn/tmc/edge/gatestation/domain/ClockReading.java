package vn.tmc.edge.gatestation.domain;

import java.time.Instant;
import java.util.Objects;

/** PHỤ TRÁCH: Task 2. Thời điểm hiện tại kèm cờ tin cậy (NFR-12 đồng bộ thời gian). */
public record ClockReading(Instant now, boolean trusted) {
    public ClockReading {
        Objects.requireNonNull(now, "now");
    }
}
