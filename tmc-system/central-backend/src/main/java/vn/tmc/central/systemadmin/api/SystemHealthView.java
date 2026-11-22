package vn.tmc.central.systemadmin.api;

import java.time.Instant;
import java.util.List;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Public View Record cho System Health.
 * NGUỒN: main.pdf, FR-SA-04, FR-GS-04.
 */
public record SystemHealthView(
    String status,
    int edgeNodesOnline,
    int edgeNodesTotal,
    int gatesActive,
    int gatesTotal,
    int syncQueuePendingCount,
    List<String> activeAlerts,
    Instant checkedAt
) {}
