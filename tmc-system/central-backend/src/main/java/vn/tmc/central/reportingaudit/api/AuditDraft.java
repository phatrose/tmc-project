package vn.tmc.central.reportingaudit.api;

import java.time.Instant;
import java.util.UUID;

public record AuditDraft(UUID actorId, UUID deviceId, UUID factoryId, String action,
        String resourceType, String resourceId, AuditEntry.Result result, Instant occurredAt,
        UUID correlationId, String reason) {}
