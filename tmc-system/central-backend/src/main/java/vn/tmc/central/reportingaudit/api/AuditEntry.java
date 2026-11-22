package vn.tmc.central.reportingaudit.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AuditEntry(UUID id, UUID actorId, UUID deviceId, UUID factoryId, String action,
        String resourceType, String resourceId, Result result,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") Instant occurredAt,
        UUID correlationId, String reason) {
    public enum Result { SUCCESS, DENIED, FAILURE }
}
