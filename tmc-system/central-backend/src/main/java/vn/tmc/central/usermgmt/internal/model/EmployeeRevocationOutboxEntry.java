package vn.tmc.central.usermgmt.internal.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 1 & Task 6 (DATA-07).
 * Bản ghi outbox ghi trong cùng transaction khi nhân viên chuyển sang active=false
 * để thu hồi QR credential và access policy đồng bộ sang Edge.
 */
@Entity
@Table(name = "employee_revocation_outbox")
public class EmployeeRevocationOutboxEntry {

    public static final String EVENT_EMPLOYEE_DEACTIVATED = "EMPLOYEE_DEACTIVATED";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSED = "PROCESSED";

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false, length = 64)
    private String employeeId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "actor_user_id", length = 64)
    private String actorUserId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected EmployeeRevocationOutboxEntry() {}

    public EmployeeRevocationOutboxEntry(String employeeId, String actorUserId) {
        this.id = UUID.randomUUID();
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId");
        this.eventType = EVENT_EMPLOYEE_DEACTIVATED;
        this.actorUserId = actorUserId;
        this.status = STATUS_PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmployeeId() { return employeeId; }
    public String getEventType() { return eventType; }
    public String getActorUserId() { return actorUserId; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }

    public void markProcessed() {
        this.status = STATUS_PROCESSED;
        this.processedAt = Instant.now();
    }
}
