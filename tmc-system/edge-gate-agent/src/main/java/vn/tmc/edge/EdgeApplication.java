package vn.tmc.edge;

import vn.tmc.edge.edgesync.api.LocalEventStorePort;
import vn.tmc.edge.edgesync.application.BootstrapWorker;
import vn.tmc.edge.edgesync.application.CachedAccess;
import vn.tmc.edge.edgesync.application.ConnectivityMonitor;
import vn.tmc.edge.edgesync.application.EdgeOperations;
import vn.tmc.edge.edgesync.application.HeartbeatWorker;
import vn.tmc.edge.edgesync.application.RecoveryWorker;
import vn.tmc.edge.edgesync.application.SyncWorker;
import vn.tmc.edge.edgesync.domain.BootstrapCache.Identity;
import vn.tmc.edge.edgesync.infrastructure.RestBootstrapClient;
import vn.tmc.edge.edgesync.infrastructure.RestHeartbeatClient;
import vn.tmc.edge.edgesync.infrastructure.RestSyncClient;
import vn.tmc.edge.edgesync.infrastructure.SqliteStore;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;

/** Starts the Edge background services for a provisioned device. */
public final class EdgeApplication {
    private static final System.Logger LOGGER = System.getLogger(EdgeApplication.class.getName());

    private EdgeApplication() {}

    public static void main(String[] args) throws NoSuchAlgorithmException {
        if (args.length != 4) {
            throw new IllegalArgumentException("Expected database path, Edge ID, factory ID and Central HTTPS origin");
        }
        var running = start(Path.of(args[0]), new Identity(UUID.fromString(args[1]), UUID.fromString(args[2])),
                URI.create(args[3]), SSLContext.getDefault());
        Runtime.getRuntime().addShutdownHook(new Thread(running::close));
    }

    public static RunningEdge start(Path file, Identity identity, URI centralOrigin, SSLContext deviceTls) {
        var clock = Clock.systemUTC();
        var store = SqliteStore.openProvisioned(file, clock, identity);
        var cache = store.bootstrapCache(identity);
        var sync = new SyncWorker(identity.edgeId(), store,
                new RestSyncClient(centralOrigin.resolve("/api/v1/attendance/sync"), deviceTls),
                clock, RandomGenerator.getDefault());
        var bootstrap = new BootstrapWorker(
                new RestBootstrapClient(centralOrigin.resolve("/api/v1/edge/bootstrap"), deviceTls), cache, clock);
        var connectivity = new ConnectivityMonitor(clock, at -> store.recordCentralContact(identity.edgeId(), at));
        var operations = new EdgeOperations(identity.edgeId(), store, store, cache, connectivity, clock, () -> diskFree(file));
        var heartbeat = new HeartbeatWorker(operations,
                new RestHeartbeatClient(centralOrigin.resolve("/api/v1/edge/heartbeat"), deviceTls), connectivity);

        new RecoveryWorker(store::recover).recover();
        var scheduler = Executors.newScheduledThreadPool(3);
        schedule(scheduler, "sync", Duration.ofSeconds(1), () -> {
            if (sync.runOnce() == SyncWorker.Result.APPLIED) {
                connectivity.recordSuccess();
            }
        });
        schedule(scheduler, "bootstrap", BootstrapWorker.POLL_INTERVAL, () -> {
            bootstrap.refresh();
            connectivity.recordSuccess();
        });
        schedule(scheduler, "heartbeat", HeartbeatWorker.DEFAULT_CYCLE, heartbeat::sendOnce);
        schedule(scheduler, "housekeeping", Duration.ofHours(1), store::cleanup);
        return new RunningEdge(store, new CachedAccess(cache, bootstrap::requestKeyRefresh), connectivity, operations, scheduler);
    }

    public record RunningEdge(LocalEventStorePort eventStore, CachedAccess cache, ConnectivityMonitor connectivity,
                              EdgeOperations operations, ScheduledExecutorService scheduler) implements AutoCloseable {
        @Override
        public void close() {
            scheduler.shutdownNow();
        }
    }

    private static void schedule(ScheduledExecutorService scheduler, String name, Duration interval, Runnable work) {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                work.run();
            } catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.WARNING, "{0} failed ({1})", name, failure.getClass().getSimpleName());
            }
        }, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static OptionalLong diskFree(Path databaseFile) {
        try {
            return OptionalLong.of(Files.getFileStore(databaseFile).getUsableSpace());
        } catch (IOException unavailable) {
            return OptionalLong.empty();
        }
    }
}
