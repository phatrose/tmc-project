package vn.tmc.edge.edgesync.application;

import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.domain.BootstrapCache;
import vn.tmc.edge.edgesync.domain.EdgeStatus;
import vn.tmc.edge.edgesync.domain.HeartbeatPort.Heartbeat;
import vn.tmc.edge.edgesync.domain.OperationsJournal;
import vn.tmc.edge.edgesync.domain.SyncJournal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;

public final class EdgeOperations {
    private static final long BACKLOG_COUNT_WARNING = 50_000;
    private static final Duration BACKLOG_AGE_WARNING = Duration.ofDays(30);
    private final UUID edgeId;
    private final OperationsJournal journal;
    private final SyncJournal sync;
    private final BootstrapCache cache;
    private final ConnectivityMonitor connectivity;
    private final Clock clock;
    private final Supplier<OptionalLong> diskProbe;

    public EdgeOperations(UUID edgeId, OperationsJournal journal, SyncJournal sync, BootstrapCache cache,
                          ConnectivityMonitor connectivity, Clock clock, Supplier<OptionalLong> diskProbe) {
        this.edgeId = Objects.requireNonNull(edgeId);
        this.journal = Objects.requireNonNull(journal);
        this.sync = Objects.requireNonNull(sync);
        this.cache = Objects.requireNonNull(cache);
        this.connectivity = Objects.requireNonNull(connectivity);
        this.clock = Objects.requireNonNull(clock);
        this.diskProbe = Objects.requireNonNull(diskProbe);
    }

    public EdgeStatus status() {
        Instant now = clock.instant();
        var disk = diskProbe.get();
        Long diskBytes = disk.isPresent() ? disk.getAsLong() : null;
        try {
            var queue = journal.queue(edgeId);
            var schedule = sync.schedule(edgeId);
            var snapshot = cache.snapshot().orElse(null);
            boolean warning = queue.pendingCount() + queue.failedCount() >= BACKLOG_COUNT_WARNING
                    || (queue.oldestUnresolvedAt() != null && !now.isBefore(queue.oldestUnresolvedAt().plus(BACKLOG_AGE_WARNING)));
            return new EdgeStatus(connectivity.mode(), connectivity.lastSuccessfulContact(), queue, diskBytes,
                    snapshot != null && CachedAccess.readyForOffline24Hours(snapshot, now),
                    snapshot == null ? null : snapshot.trustDeadline(), warning, schedule.stopReason(), false);
        } catch (EventStoreException failure) {
            if (failure.code() != EventStoreException.Code.REPROVISION_REQUIRED) {
                throw failure;
            }
            return new EdgeStatus(ScanMode.OFFLINE, connectivity.lastSuccessfulContact(), null, diskBytes,
                    false, null, false, null, true);
        }
    }

    /** Call after an operator has corrected the request or credential problem. */
    public void resumeSync() {
        sync.resume(edgeId);
    }

    /** An unavailable disk sample is reported locally and leaves this heartbeat cycle unsent. */
    public Optional<Heartbeat> heartbeat() {
        var free = diskProbe.get();
        if (free.isEmpty()) {
            return Optional.empty();
        }
        var queue = journal.queue(edgeId);
        return Optional.of(new Heartbeat(queue.cacheRevision(), queue.pendingCount(), queue.failedCount(),
                free.getAsLong(), queue.oldestPendingAt(), Instant.ofEpochMilli(clock.millis())));
    }
}
