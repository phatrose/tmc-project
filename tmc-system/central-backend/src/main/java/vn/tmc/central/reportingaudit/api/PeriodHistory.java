package vn.tmc.central.reportingaudit.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PeriodHistory(UUID id, UUID factoryId, String period, Action action, UUID actorId,
        String reason,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") Instant occurredAt,
        int version) {
    public enum Action { CLOSE, REOPEN }
}
