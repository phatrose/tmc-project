package vn.tmc.central.systemadmin.api;

import java.time.Instant;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Public View Record cho Data Subject Request (DSR).
 * NGUỒN: main.pdf, FR-SA-06; BR-11.
 */
public record DataSubjectRequestView(
    String id,
    String employeeId,
    String requestType,
    String status,
    String reason,
    Instant requestedAt,
    String processedBy,
    Instant processedAt,
    String notes
) {}
