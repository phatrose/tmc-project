package vn.tmc.edge.edgesync.application;

import vn.tmc.contracts.BootstrapResponse.Mode;
import vn.tmc.edge.edgesync.domain.BootstrapCache;
import vn.tmc.edge.edgesync.domain.BootstrapPort;
import vn.tmc.edge.edgesync.domain.BootstrapProtocolException;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BootstrapWorker {
    public static final Duration POLL_INTERVAL = Duration.ofSeconds(30);

    private final BootstrapPort transport;
    private final BootstrapCache cache;
    private final Clock clock;
    private final AtomicBoolean refreshRequested = new AtomicBoolean();

    public BootstrapWorker(BootstrapPort transport, BootstrapCache cache, Clock clock) {
        this.transport = Objects.requireNonNull(transport);
        this.cache = Objects.requireNonNull(cache);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Scan-side signal. The background worker consumes it on its next tick. */
    public void requestKeyRefresh() {
        refreshRequested.set(true);
    }

    public void refresh() {
        boolean requested = refreshRequested.getAndSet(false);
        try {
            refresh(requested);
        } catch (RuntimeException failure) {
            if (requested) {
                refreshRequested.set(true);
            }
            throw failure;
        }
    }

    private void refresh(boolean requested) {
        var now = clock.instant();
        long afterRevision = requested ? 0 : cache.snapshot()
                .filter(snapshot -> snapshot.trustedAt(now)
                        && !snapshot.trustDeadline().isBefore(now.plus(BootstrapCache.RENEW_BELOW)))
                .map(BootstrapCache.Snapshot::revision).orElse(0L);
        var response = transport.fetch(afterRevision);
        if (response.notModified()) {
            if (afterRevision == 0 || response.revision() != afterRevision) {
                throw new BootstrapProtocolException("304 must confirm a requested trusted revision");
            }
            return;
        }
        if (afterRevision == 0) {
            applyFull(response);
        } else if (cache.apply(response.bundle()) == BootstrapCache.ApplyResult.FULL_REQUIRED) {
            applyFull(transport.fetch(0));
        }
    }

    private void applyFull(BootstrapPort.FetchResult response) {
        if (response.notModified() || response.bundle().mode() != Mode.FULL
                || cache.apply(response.bundle()) != BootstrapCache.ApplyResult.APPLIED) {
            throw new BootstrapProtocolException("Bootstrap renewal requires FULL");
        }
    }
}
