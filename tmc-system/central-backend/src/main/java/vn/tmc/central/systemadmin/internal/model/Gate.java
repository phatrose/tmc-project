package vn.tmc.central.systemadmin.internal.model;

import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Domain Model cho Gate.
 * NGUỒN: main.pdf, FR-GS-01; §5.1.
 */
public record Gate(
    String id,
    String factoryId,
    String edgeId,
    String name,
    String direction,
    String turnstileApiId,
    String status
) {
    public Gate {
        Objects.requireNonNull(id, "gateId must not be null");
        Objects.requireNonNull(factoryId, "factoryId must not be null");
        if (direction == null || (!direction.equalsIgnoreCase("IN") && !direction.equalsIgnoreCase("OUT"))) {
            throw new IllegalArgumentException("direction must be 'IN' or 'OUT', was: " + direction);
        }
        direction = direction.toUpperCase();
        if (name == null || name.isBlank()) {
            name = id;
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
    }

    public Gate(String id, String factoryId, String direction) {
        this(id, factoryId, null, id, direction, null, "ACTIVE");
    }
}
