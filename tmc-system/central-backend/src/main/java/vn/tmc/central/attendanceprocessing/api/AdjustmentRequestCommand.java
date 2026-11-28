package vn.tmc.central.attendanceprocessing.api;

import java.time.Instant;
import java.time.LocalDate;

/**
 * PHỤ TRÁCH: Task 3.
 * Command gửi yêu cầu điều chỉnh công.
 */
public record AdjustmentRequestCommand(
        String employeeId,
        LocalDate workDate,
        AdjustmentType adjustmentType,
        Instant requestedTime,
        Double adjustedHours,
        String reason,
        String requestedBy
) {
    public AdjustmentRequestCommand {
        if (employeeId == null || employeeId.isBlank()) {
            throw new IllegalArgumentException("employeeId is required");
        }
        if (workDate == null) {
            throw new IllegalArgumentException("workDate is required");
        }
        if (adjustmentType == null) {
            throw new IllegalArgumentException("adjustmentType is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Lý do điều chỉnh (reason) là bắt buộc và không được để trống");
        }
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new IllegalArgumentException("requestedBy is required");
        }
    }
}
