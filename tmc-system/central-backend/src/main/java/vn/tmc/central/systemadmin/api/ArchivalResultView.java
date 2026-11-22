package vn.tmc.central.systemadmin.api;

import java.time.Instant;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Public View Record cho Archival Result.
 * NGUỒN: main.pdf, FR-SA-05.
 */
public record ArchivalResultView(
    String batchId,
    int recordsArchived,
    int recordsAnonymized,
    Instant completedAt,
    String status
) {}
