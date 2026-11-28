package vn.tmc.edge.edgesync.domain;

import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;

import java.time.Instant;

public record EdgeStatus(ScanMode mode, Instant lastSuccessfulContact, Queue queue, Long diskFreeBytes,
                         boolean readyForOffline24Hours, Instant trustedUntil, boolean backlogWarning,
                         String syncStopReason, boolean reprovisionRequired) {
    public record Queue(long cacheRevision, long pendingCount, long failedCount, Instant oldestPendingAt,
                        Instant oldestUnresolvedAt, Instant lastCentralContactAt) {}
}
