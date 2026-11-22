package vn.tmc.central.systemadmin.internal.model;

import java.time.Instant;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Domain Model cho Device Status (Giám sát thiết bị/cổng/Edge).
 * NGUỒN: main.pdf, FR-GS-04, FR-SA-04.
 */
public record DeviceStatus(
    String deviceId,
    String deviceType,
    String name,
    String factoryId,
    String status,
    Instant lastHeartbeatAt,
    String firmwareVersion,
    String ipAddress,
    String errorMessage
) {
    public DeviceStatus {
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(deviceType, "deviceType must not be null");
        if (status == null || status.isBlank()) {
            status = "ONLINE";
        }
        if (lastHeartbeatAt == null) {
            lastHeartbeatAt = Instant.now();
        }
    }
}
