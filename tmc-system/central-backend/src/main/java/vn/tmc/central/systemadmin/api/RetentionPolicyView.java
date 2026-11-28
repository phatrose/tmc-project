package vn.tmc.central.systemadmin.api;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Public View Record cho Retention Policy.
 * NGUỒN: main.pdf, FR-SA-05; §5.1; BR-11.
 */
public record RetentionPolicyView(
    int onlineRetentionYears,
    int coldArchiveRetentionYears,
    int anonymizeInactiveMonths,
    String description
) {}
