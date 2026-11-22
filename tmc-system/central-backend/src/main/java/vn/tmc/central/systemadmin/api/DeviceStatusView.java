package vn.tmc.central.systemadmin.api;

import java.time.Instant;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Public View Record cho Device Status.
 * NGUỒN: main.pdf, FR-GS-04, FR-SA-04.
 */
public record DeviceStatusView(
    String deviceId,
    String deviceType,
    String name,
    String factoryId,
    String status,
    Instant lastHeartbeatAt,
    String firmwareVersion,
    String ipAddress,
    String errorMessage
) {}
