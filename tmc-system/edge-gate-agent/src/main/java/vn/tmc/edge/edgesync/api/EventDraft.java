package vn.tmc.edge.edgesync.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** For scan-originated events the caller supplies scanId as correlationId. */
public record EventDraft(UUID edgeId, UUID gateId, UUID factoryId, UUID correlationId,
                         Instant occurredAt, EventPayload payload) {
    public EventDraft {
        Objects.requireNonNull(edgeId, "edgeId");
        Objects.requireNonNull(gateId, "gateId");
        Objects.requireNonNull(factoryId, "factoryId");
        Objects.requireNonNull(correlationId, "correlationId");
        if (correlationId.version() != 4 || correlationId.variant() != 2) {
            throw new IllegalArgumentException("correlationId must be a UUIDv4");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt").truncatedTo(ChronoUnit.MILLIS);
        Objects.requireNonNull(payload, "payload");
    }

    public String eventType() {
        return payload.eventType();
    }

    public int schemaVersion() {
        return 1;
    }
}
