package vn.tmc.edge.edgesync.application;

import vn.tmc.contracts.BootstrapResponse.Mode;
import vn.tmc.edge.edgesync.domain.BootstrapCache;
import vn.tmc.edge.edgesync.domain.BootstrapPort;
import vn.tmc.edge.edgesync.domain.BootstrapProtocolException;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

public final class BootstrapWorker {
    public static final Duration POLL_INTERVAL = Duration.ofSeconds(30);

    private final BootstrapPort transport;
    private final BootstrapCache cache;
    private final Clock clock;

    public BootstrapWorker(BootstrapPort transport, BootstrapCache cache, Clock clock) {
        this.transport = Objects.requireNonNull(transport);
        this.cache = Objects.requireNonNull(cache);
        this.clock = Objects.requireNonNull(clock);
    }

    public void refresh() {
        var now = clock.instant();
        long afterRevision = cache.snapshot()
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
