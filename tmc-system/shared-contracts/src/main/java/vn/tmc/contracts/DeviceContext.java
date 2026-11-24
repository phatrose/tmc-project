package vn.tmc.contracts;

import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: Định danh thiết bị tin cậy từ TLS / mTLS certificate binding.
 * NGUỒN: CONTRACT-V1.md §10.
 */
public record DeviceContext(
        UUID edgeId,
        UUID factoryId
) {
    public DeviceContext {
        Objects.requireNonNull(edgeId, "edgeId must not be null");
        Objects.requireNonNull(factoryId, "factoryId must not be null");
    }

    public static DeviceContext of(String edgeId, String factoryId) {
        return new DeviceContext(
                UUID.fromString(edgeId),
                UUID.fromString(factoryId)
        );
    }
}
