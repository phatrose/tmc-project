package vn.tmc.central.systemadmin.internal.model;

import java.time.Instant;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Domain Model cho SystemConfiguration.
 * NGUỒN: main.pdf, FR-SA-02, FR-SA-03; §5.1.
 */
public record SystemConfiguration(
    String key,
    String value,
    String category,
    String description,
    Instant updatedAt
) {
    public SystemConfiguration {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (category == null || category.isBlank()) {
            category = "SYSTEM";
        }
        if (description == null) {
            description = "";
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    public SystemConfiguration(String key, String value) {
        this(key, value, "SYSTEM", "", Instant.now());
    }
}
