package vn.tmc.edge.edgesync.application;

import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Uses successful background interactions observed by this process. */
public final class ConnectivityMonitor {
    private static final Duration ONLINE_WINDOW = Duration.ofSeconds(60);
    private final Clock clock;
    private final Consumer<Instant> saveContact;
    private final AtomicReference<Instant> latest = new AtomicReference<>();

    public ConnectivityMonitor(Clock clock, Consumer<Instant> saveContact) {
        this.clock = Objects.requireNonNull(clock);
        this.saveContact = Objects.requireNonNull(saveContact);
    }

    public void recordSuccess() {
        Instant at = Instant.ofEpochMilli(clock.millis());
        saveContact.accept(at);
        latest.accumulateAndGet(at, (previous, observed) ->
                previous == null || observed.isAfter(previous) ? observed : previous);
    }

    public Instant lastSuccessfulContact() {
        return latest.get();
    }

    public ScanMode mode() {
        var last = latest.get();
        var now = clock.instant();
        return last != null && !now.isBefore(last) && now.isBefore(last.plus(ONLINE_WINDOW))
                ? ScanMode.ONLINE : ScanMode.OFFLINE;
    }

    public boolean isOnline() {
        return mode() == ScanMode.ONLINE;
    }
}
