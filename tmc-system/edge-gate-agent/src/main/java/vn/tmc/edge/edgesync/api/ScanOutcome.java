package vn.tmc.edge.edgesync.api;

import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;
import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;

import java.time.Instant;
import java.util.UUID;

/** Durable scan result. Command status alone never authorizes an unlock. */
public record ScanOutcome(UUID scanId, UUID gateId, Decision decision, String reasonCode, UUID eventId,
                          UUID commandId, CommandStatus commandStatus, Instant occurredAt, ScanMode mode) {
    public enum Decision { ALLOW, DENY, EMERGENCY }
}
