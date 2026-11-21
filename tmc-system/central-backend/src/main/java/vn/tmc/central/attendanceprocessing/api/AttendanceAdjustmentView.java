package vn.tmc.central.attendanceprocessing.api;

import java.time.Instant;
import java.time.LocalDate;

/**
 * PHỤ TRÁCH: Task 3.
 * View DTO cho yêu cầu điều chỉnh chấm công.
 */
public record AttendanceAdjustmentView(
        String id,
        String employeeId,
        LocalDate workDate,
        AdjustmentType adjustmentType,
        Instant requestedTime,
        Double adjustedHours,
        String reason,
        AdjustmentStatus status,
        String requestedBy,
        Instant requestedAt,
        String reviewedBy,
        Instant reviewedAt,
        String reviewNote
) {}
