package vn.tmc.central.attendanceprocessing.api;

import vn.tmc.contracts.EventEnvelope;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO xem sự kiện công theo CONTRACT-V1.md §9.
 * AttendanceEventView = {envelope:EventEnvelope, receivedAt:Instant, flags:string[]}
 */
public record AttendanceEventView(
        EventEnvelope envelope,
        Instant receivedAt,
        List<String> flags
) {
    public AttendanceEventView {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        flags = flags != null ? Collections.unmodifiableList(flags) : List.of();
    }
}
