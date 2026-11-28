package vn.tmc.central.systemadmin.internal.model;

import java.time.Instant;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Domain Model cho Data Subject Request (DSR).
 * NGUỒN: main.pdf, FR-SA-06; BR-11.
 */
public record DataSubjectRequest(
    String id,
    String employeeId,
    String requestType,
    String status,
    String reason,
    Instant requestedAt,
    String processedBy,
    Instant processedAt,
    String notes
) {
    public DataSubjectRequest {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(requestType, "requestType must not be null");
        if (status == null || status.isBlank()) {
            status = "PENDING";
        }
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }
}
