package vn.tmc.edge.edgesync.domain;

import java.time.Instant;

@FunctionalInterface
public interface HeartbeatPort {
    record Heartbeat(long cacheRevision, long pendingCount, long failedCount, long diskFreeBytes,
                     Instant oldestPendingAt, Instant observedAt) {}
    record Receipt(Instant serverTime) {}

    Receipt send(Heartbeat heartbeat);
}
