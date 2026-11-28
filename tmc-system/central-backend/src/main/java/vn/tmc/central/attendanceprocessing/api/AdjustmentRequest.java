package vn.tmc.central.attendanceprocessing.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO request tạo điều chỉnh công theo CONTRACT-V1.md §9.
 * POST /api/v1/attendance/adjustments -> {eventId:UUID?, proposedOccurredAt:Instant?, proposedDirection:"IN"|"OUT"|null, reason:string}
 * 
 * QUY TẮC BẮT BUỘC (CONTRACT-V1 §9):
 * - Ít nhất một proposed field không null (proposedOccurredAt != null || proposedDirection != null).
 * - reason: độ dài từ 10 đến 500 ký tự.
 */
public record AdjustmentRequest(
        UUID eventId,
        Instant proposedOccurredAt,
        String proposedDirection,
        String reason
) {
    public AdjustmentRequest {
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.length() < 10 || reason.length() > 500) {
            throw new IllegalArgumentException("reason must be between 10 and 500 characters, got: " + reason.length());
        }
        if (proposedOccurredAt == null && proposedDirection == null) {
            throw new IllegalArgumentException("At least one proposed field (proposedOccurredAt or proposedDirection) must not be null");
        }
    }
}
