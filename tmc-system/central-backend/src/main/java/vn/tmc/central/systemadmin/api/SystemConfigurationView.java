package vn.tmc.central.systemadmin.api;

import java.time.Instant;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Public View Record cho System Configuration.
 * NGUỒN: main.pdf, FR-SA-02, FR-SA-03.
 */
public record SystemConfigurationView(
    String key,
    String value,
    String category,
    String description,
    Instant updatedAt
) {
    public SystemConfigurationView(String key, String value) {
        this(key, value, "SYSTEM", "", Instant.now());
    }
}
