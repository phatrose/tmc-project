package vn.tmc.central.attendanceprocessing.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO yêu cầu điều chỉnh công theo CONTRACT-V1.md §9.
 * Adjustment = {id:UUID, eventId:UUID?, employeeId:UUID, createdBy:UUID,
 *   proposedOccurredAt:Instant?, proposedDirection:"IN"|"OUT"|null, reason:string,
 *   state:"PENDING"|"APPROVED"|"REJECTED", decidedBy:UUID?, decisionReason:string?,
 *   createdAt:Instant, decidedAt:Instant?, version:int}
 * 
 * QUY TẮC BẮT BUỘC:
 * - Ít nhất một proposed field không null.
 * - reason: 10–500 ký tự.
 * - state bắt đầu là PENDING.
 */
public record Adjustment(
        UUID id,
        UUID eventId,
        UUID employeeId,
        UUID createdBy,
        Instant proposedOccurredAt,
        String proposedDirection,
        String reason,
        String state, // "PENDING", "APPROVED", "REJECTED"
        UUID decidedBy,
        String decisionReason,
        Instant createdAt,
        Instant decidedAt,
        int version
) {
    public Adjustment {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.length() < 10 || reason.length() > 500) {
            throw new IllegalArgumentException("reason must be between 10 and 500 characters, got: " + reason.length());
        }
        if (proposedOccurredAt == null && proposedDirection == null) {
            throw new IllegalArgumentException("At least one proposed field (proposedOccurredAt or proposedDirection) must not be null");
        }
        if (state == null) {
            state = "PENDING";
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
