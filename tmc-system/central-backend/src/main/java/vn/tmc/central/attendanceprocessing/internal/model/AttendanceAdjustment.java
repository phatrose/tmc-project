package vn.tmc.central.attendanceprocessing.internal.model;

import vn.tmc.central.attendanceprocessing.api.Adjustment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: Entity bảng attendance_adjustments theo CONTRACT-V1.md §9.
 * 
 * QUY TẮC BẮT BUỘC:
 * - Ít nhất một proposed field không null (proposedOccurredAt != null || proposedDirection != null).
 * - reason: 10–500 ký tự.
 * - Chỉ quyết định từ state = PENDING.
 * - version quản lý optimistic concurrency (sai version trả 409).
 */
public final class AttendanceAdjustment {
    private final UUID id;
    private final UUID eventId;
    private final UUID employeeId;
    private final UUID createdBy;
    private Instant proposedOccurredAt;
    private String proposedDirection;
    private final String reason;
    private String state; // "PENDING" | "APPROVED" | "REJECTED"
    private UUID decidedBy;
    private String decisionReason;
    private final Instant createdAt;
    private Instant decidedAt;
    private int version;

    public AttendanceAdjustment(
            UUID id,
            UUID eventId,
            UUID employeeId,
            UUID createdBy,
            Instant proposedOccurredAt,
            String proposedDirection,
            String reason,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.eventId = eventId;
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        this.proposedOccurredAt = proposedOccurredAt;
        this.proposedDirection = proposedDirection;
        if (reason == null || reason.length() < 10 || reason.length() > 500) {
            throw new IllegalArgumentException("reason must be between 10 and 500 characters");
        }
        if (proposedOccurredAt == null && proposedDirection == null) {
            throw new IllegalArgumentException("At least one proposed field must not be null");
        }
        this.reason = reason;
        this.state = "PENDING";
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.version = 1;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getEmployeeId() { return employeeId; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getProposedOccurredAt() { return proposedOccurredAt; }
    public String getProposedDirection() { return proposedDirection; }
    public String getReason() { return reason; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public UUID getDecidedBy() { return decidedBy; }
    public void setDecidedBy(UUID decidedBy) { this.decidedBy = decidedBy; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public void incrementVersion() { this.version++; }

    public Adjustment toContractDto() {
        return new Adjustment(
                id,
                eventId,
                employeeId,
                createdBy,
                proposedOccurredAt,
                proposedDirection,
                reason,
                state,
                decidedBy,
                decisionReason,
                createdAt,
                decidedAt,
                version
        );
    }
}
