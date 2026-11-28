package vn.tmc.central.attendanceprocessing.internal.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * Bảng: sync_conflicts.
 * Lưu vết xung đột khi nhận cùng eventId nhưng khác payload hash.
 */
public final class SyncConflict {
    private final String id;
    private final UUID eventId;
    private final String edgeId;
    private final String existingHash;
    private final String incomingHash;
    private final Instant occurredAt;
    private final Instant reportedAt;
    private final String details;

    public SyncConflict(
            String id,
            UUID eventId,
            String edgeId,
            String existingHash,
            String incomingHash,
            Instant occurredAt,
            Instant reportedAt,
            String details
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.edgeId = edgeId;
        this.existingHash = existingHash;
        this.incomingHash = incomingHash;
        this.occurredAt = occurredAt;
        this.reportedAt = Objects.requireNonNull(reportedAt, "reportedAt must not be null");
        this.details = details;
    }

    public String getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getEdgeId() { return edgeId; }
    public String getExistingHash() { return existingHash; }
    public String getIncomingHash() { return incomingHash; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReportedAt() { return reportedAt; }
    public String getDetails() { return details; }
}
