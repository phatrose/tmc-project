package vn.tmc.central.systemadmin.api;

import java.time.LocalTime;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Public View Record cho Shift.
 * NGUỒN: main.pdf, FR-SA-02; §5.1; DECISIONS.md §1.
 */
public record ShiftView(
    String id,
    String factoryId,
    String name,
    LocalTime startsAt,
    LocalTime endsAt,
    boolean isOvernight,
    int gracePeriodMinutesLate,
    int gracePeriodMinutesEarly,
    boolean active
) {}
