package vn.tmc.central.reportingaudit.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PayrollBatch(UUID id, UUID factoryId, String period, int periodVersion, State state,
        int recordCount, int acceptedCount, String checksum,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") Instant createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") Instant completedAt,
        String errorCode) {
    public enum State { QUEUED, SENDING, EXPORT_SUCCESS, PARTIAL, FAILED }
}
