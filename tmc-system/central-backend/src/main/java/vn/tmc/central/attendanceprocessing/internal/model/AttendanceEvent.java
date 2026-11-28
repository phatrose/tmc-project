package vn.tmc.central.attendanceprocessing.internal.model;

import vn.tmc.central.attendanceprocessing.api.AttendanceAnomaly;
import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.contracts.AttendancePayload.Source;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: Entity bất biến bảng attendance_events theo CONTRACT-V1.md §5, §6.
 */
public final class AttendanceEvent {
    private final UUID eventId;
    private final String eventType;
    private final int schemaVersion;
    private final UUID edgeId;
    private final UUID gateId;
    private final UUID factoryId;
    private final long sequenceNo;
    private final UUID correlationId;
    private final Instant observedAt;
    private final Instant receivedAt;
    private final UUID employeeId;
    private final Direction direction;
    private final Source source;
    private final UUID jti;
    private final String kid;
    private final Instant tokenIssuedAt;
    private final UUID commandId;
    private final UUID operatorId;
    private final String reason;
    private final List<String> flags;
    private final String payloadSha256;
    private final String rawPayload;

    public AttendanceEvent(
            UUID eventId,
            String eventType,
            int schemaVersion,
            UUID edgeId,
            UUID gateId,
            UUID factoryId,
            long sequenceNo,
            UUID correlationId,
            Instant observedAt,
            Instant receivedAt,
            UUID employeeId,
            Direction direction,
            Source source,
            UUID jti,
            String kid,
            Instant tokenIssuedAt,
            UUID commandId,
            UUID operatorId,
            String reason,
            List<String> flags,
            String payloadSha256,
            String rawPayload
    ) {
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.schemaVersion = schemaVersion;
        this.edgeId = Objects.requireNonNull(edgeId, "edgeId must not be null");
        this.gateId = Objects.requireNonNull(gateId, "gateId must not be null");
        this.factoryId = Objects.requireNonNull(factoryId, "factoryId must not be null");
        this.sequenceNo = sequenceNo;
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
        this.observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        this.employeeId = employeeId;
        this.direction = direction;
        this.source = source != null ? source : Source.ONLINE;
        this.jti = jti;
        this.kid = kid;
        this.tokenIssuedAt = tokenIssuedAt;
        this.commandId = commandId;
        this.operatorId = operatorId;
        this.reason = reason;
        this.flags = flags != null ? new ArrayList<>(flags) : new ArrayList<>();
        this.payloadSha256 = Objects.requireNonNull(payloadSha256, "payloadSha256 must not be null");
        this.rawPayload = Objects.requireNonNull(rawPayload, "rawPayload must not be null");
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public int getSchemaVersion() { return schemaVersion; }
    public UUID getEdgeId() { return edgeId; }
    public UUID getGateId() { return gateId; }
    public UUID getFactoryId() { return factoryId; }
    public long getSequenceNo() { return sequenceNo; }
    public UUID getCorrelationId() { return correlationId; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public UUID getEmployeeId() { return employeeId; }
    public Direction getDirection() { return direction; }
    public Source getSource() { return source; }
    public UUID getJti() { return jti; }
    public String getKid() { return kid; }
    public Instant getTokenIssuedAt() { return tokenIssuedAt; }
    public UUID getCommandId() { return commandId; }
    public UUID getOperatorId() { return operatorId; }
    public String getReason() { return reason; }
    public List<String> getFlags() { return Collections.unmodifiableList(flags); }
    public void addFlag(String flag) { if (flag != null && !this.flags.contains(flag)) this.flags.add(flag); }
    public String getPayloadSha256() { return payloadSha256; }
    public String getRawPayload() { return rawPayload; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AttendanceEvent that = (AttendanceEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
}
