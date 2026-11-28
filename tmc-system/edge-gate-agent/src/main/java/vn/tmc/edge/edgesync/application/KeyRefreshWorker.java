package vn.tmc.edge.edgesync.application;

import vn.tmc.edge.edgesync.domain.BootstrapCache;
import vn.tmc.edge.edgesync.domain.BootstrapCache.ApplyResult;
import vn.tmc.edge.edgesync.domain.KeyRefreshPort;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KeyRefreshWorker {
    public static final Duration POLL_INTERVAL = Duration.ofSeconds(1);

    public enum Result { IDLE, APPLIED, FULL_REQUIRED }

    private final KeyRefreshPort transport;
    private final BootstrapCache cache;
    private final AtomicBoolean refreshRequested = new AtomicBoolean();

    public KeyRefreshWorker(KeyRefreshPort transport, BootstrapCache cache) {
        this.transport = Objects.requireNonNull(transport);
        this.cache = Objects.requireNonNull(cache);
    }

    /** Scan-side coalescing signal. Never performs synchronous network I/O. */
    public void requestRefresh() {
        refreshRequested.set(true);
    }

    public synchronized Result refreshIfRequested() {
        if (!refreshRequested.getAndSet(false)) {
            return Result.IDLE;
        }
        return cache.applyKeySet(transport.fetch()) == ApplyResult.FULL_REQUIRED
                ? Result.FULL_REQUIRED
                : Result.APPLIED;
    }
}
