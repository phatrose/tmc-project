package vn.tmc.central.reportingaudit.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReportExport(UUID id, State state, Format format,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") Instant createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") Instant expiresAt,
        String errorCode) {
    public enum State { QUEUED, RUNNING, READY, FAILED }
    public enum Format { CSV, XLSX, PDF }
}
