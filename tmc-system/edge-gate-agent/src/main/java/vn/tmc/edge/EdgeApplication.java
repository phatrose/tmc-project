package vn.tmc.edge;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;
import vn.tmc.edge.edgesync.api.LocalEventStorePort;
import vn.tmc.edge.edgesync.application.BootstrapWorker;
import vn.tmc.edge.edgesync.application.CachedAccess;
import vn.tmc.edge.edgesync.application.ConnectivityMonitor;
import vn.tmc.edge.edgesync.application.EdgeOperations;
import vn.tmc.edge.edgesync.application.HeartbeatWorker;
import vn.tmc.edge.edgesync.application.KeyRefreshWorker;
import vn.tmc.edge.edgesync.application.RecoveryWorker;
import vn.tmc.edge.edgesync.application.SyncWorker;
import vn.tmc.edge.edgesync.domain.BootstrapCache.Identity;
import vn.tmc.edge.edgesync.infrastructure.RestBootstrapClient;
import vn.tmc.edge.edgesync.infrastructure.RestHeartbeatClient;
import vn.tmc.edge.edgesync.infrastructure.RestKeyRefreshClient;
import vn.tmc.edge.edgesync.infrastructure.RestSyncClient;
import vn.tmc.edge.edgesync.infrastructure.SqliteStore;
import vn.tmc.edge.gatestation.api.QrKeyCachePort;
import vn.tmc.edge.gatestation.api.QrRevocationCachePort;
import vn.tmc.edge.gatestation.application.GateDecisionRecorder;
import vn.tmc.edge.gatestation.application.GateOverrideUseCase;
import vn.tmc.edge.gatestation.application.RecentScanResults;
import vn.tmc.edge.gatestation.application.ScanQrUseCase;
import vn.tmc.edge.gatestation.domain.TurnstileControlPort;
import vn.tmc.edge.gatestation.infrastructure.CachedAccessPolicy;
import vn.tmc.edge.gatestation.infrastructure.EdgeHttpHandler;
import vn.tmc.edge.gatestation.infrastructure.Es256ActionGrantVerifier;
import vn.tmc.edge.gatestation.infrastructure.InMemoryPresenceTracker;
import vn.tmc.edge.gatestation.infrastructure.JwsVerifier;
import vn.tmc.edge.gatestation.infrastructure.KioskResolver;
import vn.tmc.edge.gatestation.infrastructure.ScanController;
import vn.tmc.edge.gatestation.infrastructure.SystemTrustedClock;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.InetSocketAddress;
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
    public static final String KEY_REFRESH_PATH = "/api/v1/qr/keys/refresh";
    private static final System.Logger LOGGER = System.getLogger(EdgeApplication.class.getName());

    private EdgeApplication() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Expected: <dbPath> <edgeId> <factoryId> <centralHttpsOrigin> (plus TMC_EDGE_SERVER_KEYSTORE, TMC_EDGE_TRUSTSTORE, TMC_EDGE_DEVICE_KEYSTORE env vars)");
        }
        Path dbPath = Path.of(args[0]);
        Identity identity = new Identity(UUID.fromString(args[1]), UUID.fromString(args[2]));
        URI centralOrigin = URI.create(args[3]);

        String listenHost = envOrDefault("TMC_EDGE_LISTEN_HOST", "127.0.0.1");
        int listenPort = Integer.parseInt(envOrDefault("TMC_EDGE_LISTEN_PORT", "9443"));
        Path serverKeystore = Path.of(requireEnv("TMC_EDGE_SERVER_KEYSTORE"));
        char[] serverKeystorePass = requireEnv("TMC_EDGE_SERVER_KEYSTORE_PASSWORD").toCharArray();
        Path deviceKeystore = Path.of(requireEnv("TMC_EDGE_DEVICE_KEYSTORE"));
        char[] deviceKeystorePass = requireEnv("TMC_EDGE_DEVICE_KEYSTORE_PASSWORD").toCharArray();
        Path truststore = Path.of(requireEnv("TMC_EDGE_TRUSTSTORE"));
        char[] truststorePass = requireEnv("TMC_EDGE_TRUSTSTORE_PASSWORD").toCharArray();

        SSLContext serverTls = loadMtlsContext(serverKeystore, serverKeystorePass, truststore, truststorePass);
        SSLContext deviceTls = loadMtlsContext(deviceKeystore, deviceKeystorePass, truststore, truststorePass);

        java.util.Map<String, vn.tmc.edge.gatestation.domain.KioskContext> provisionedKiosks =
                parseProvisionedKiosks(requireEnv("TMC_EDGE_PROVISIONED_KIOSKS"), identity);
        KioskResolver kioskResolver = new vn.tmc.edge.gatestation.infrastructure.SanUriKioskResolver(provisionedKiosks);

        var keyCache = new vn.tmc.edge.gatestation.infrastructure.InMemoryQrKeyCache();
        var revocationCache = new vn.tmc.edge.gatestation.infrastructure.InMemoryQrRevocationCache();
        var running = startWithHttps(
                dbPath,
                identity,
                centralOrigin,
                deviceTls,
                new InetSocketAddress(listenHost, listenPort),
                serverTls,
                kioskResolver,
                new vn.tmc.edge.gatestation.infrastructure.MockTurnstileAdapter(Clock.systemUTC()),
                keyCache,
                revocationCache);
        Runtime.getRuntime().addShutdownHook(new Thread(running::close));
    }

    private static String requireEnv(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing required Edge mTLS environment variable: " + key);
        }
        return v.trim();
    }

    private static String envOrDefault(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    private static SSLContext loadMtlsContext(Path keystorePath, char[] ksPass, Path truststorePath, char[] tsPass)
            throws Exception {
        var ks = java.security.KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(keystorePath)) {
            ks.load(in, ksPass);
        }
        var kmf = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, ksPass);

        var ts = java.security.KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(truststorePath)) {
            ts.load(in, tsPass);
        }
        var tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }

    private static java.util.Map<String, vn.tmc.edge.gatestation.domain.KioskContext> parseProvisionedKiosks(
            String raw, Identity identity) {
        // Format: kioskId=gateUuid1|gateUuid2,kioskId2=gateUuid3
        java.util.Map<String, vn.tmc.edge.gatestation.domain.KioskContext> map = new java.util.HashMap<>();
        for (String entry : raw.split(",")) {
            String[] parts = entry.trim().split("=");
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException("Invalid TMC_EDGE_PROVISIONED_KIOSKS entry: " + entry);
            }
            java.util.Set<UUID> gates = new java.util.HashSet<>();
            for (String g : parts[1].split("\\|")) {
                gates.add(UUID.fromString(g.trim()));
            }
            map.put(parts[0].trim(), new vn.tmc.edge.gatestation.domain.KioskContext(
                    parts[0].trim(), identity.edgeId(), identity.factoryId(), gates));
        }
        if (map.isEmpty()) {
            throw new IllegalStateException("TMC_EDGE_PROVISIONED_KIOSKS must contain at least one kiosk");
        }
        return map;
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
        var keyRefresh = new KeyRefreshWorker(
                new RestKeyRefreshClient(centralOrigin.resolve(KEY_REFRESH_PATH), deviceTls), cache);
        var connectivity = new ConnectivityMonitor(clock, at -> store.recordCentralContact(identity.edgeId(), at));
        var operations = new EdgeOperations(identity.edgeId(), store, store, cache, connectivity, clock, () -> diskFree(file));
        var heartbeat = new HeartbeatWorker(operations,
                new RestHeartbeatClient(centralOrigin.resolve("/api/v1/edge/heartbeat"), deviceTls), connectivity);

        new RecoveryWorker(store::recover).recover();
        var scheduler = Executors.newScheduledThreadPool(4);
        schedule(scheduler, "sync", Duration.ofSeconds(1), () -> {
            if (sync.runOnce() == SyncWorker.Result.APPLIED) {
                connectivity.recordSuccess();
            }
        });
        schedule(scheduler, "bootstrap", BootstrapWorker.POLL_INTERVAL, () -> {
            bootstrap.refresh();
            connectivity.recordSuccess();
        });
        schedule(scheduler, "key-refresh", KeyRefreshWorker.POLL_INTERVAL, () -> {
            if (keyRefresh.refreshIfRequested() == KeyRefreshWorker.Result.APPLIED) {
                connectivity.recordSuccess();
            }
        });
        schedule(scheduler, "heartbeat", HeartbeatWorker.DEFAULT_CYCLE, heartbeat::sendOnce);
        schedule(scheduler, "housekeeping", Duration.ofHours(1), store::cleanup);
        return new RunningEdge(store, new CachedAccess(cache, keyRefresh::requestRefresh), connectivity, operations, scheduler, null);
    }

    /**
     * PHỤ TRÁCH: Task 8 — tích hợp với Task 2, 6, 7 (DEVICE-02, GRANT-09).
     * Khởi chạy Edge đầy đủ kèm HttpsServer mTLS (needClientAuth=true) phục vụ /edge/v1/
     * và nối Es256ActionGrantVerifier (thay UnavailableActionGrantVerifier).
     */
    public static RunningEdge startWithHttps(
            Path file,
            Identity identity,
            URI centralOrigin,
            SSLContext deviceTls,
            InetSocketAddress listenAddress,
            SSLContext serverTls,
            KioskResolver kioskResolver,
            TurnstileControlPort turnstile,
            QrKeyCachePort keyCache,
            QrRevocationCachePort revocationCache
    ) throws IOException {
        var clock = Clock.systemUTC();
        var store = SqliteStore.openProvisioned(file, clock, identity);
        var cache = store.bootstrapCache(identity);
        var sync = new SyncWorker(identity.edgeId(), store,
                new RestSyncClient(centralOrigin.resolve("/api/v1/attendance/sync"), deviceTls),
                clock, RandomGenerator.getDefault());
        var bootstrap = new BootstrapWorker(
                new RestBootstrapClient(centralOrigin.resolve("/api/v1/edge/bootstrap"), deviceTls), cache, clock);
        var keyRefresh = new KeyRefreshWorker(
                new RestKeyRefreshClient(centralOrigin.resolve(KEY_REFRESH_PATH), deviceTls), cache);
        var connectivity = new ConnectivityMonitor(clock, at -> store.recordCentralContact(identity.edgeId(), at));
        var operations = new EdgeOperations(identity.edgeId(), store, store, cache, connectivity, clock, () -> diskFree(file));
        var heartbeat = new HeartbeatWorker(operations,
                new RestHeartbeatClient(centralOrigin.resolve("/api/v1/edge/heartbeat"), deviceTls), connectivity);

        new RecoveryWorker(store::recover).recover();

        var accessPolicy = new CachedAccessPolicy(cache);
        var presence = new InMemoryPresenceTracker(clock);
        var recorder = new GateDecisionRecorder(
                store,
                turnstile,
                () -> connectivity.isOnline() ? ScanMode.ONLINE : ScanMode.OFFLINE,
                new RecentScanResults(clock),
                presence,
                clock);
        var trustedClock = new SystemTrustedClock(clock);
        var scanUseCase = new ScanQrUseCase(
                new JwsVerifier(keyCache, revocationCache),
                accessPolicy,
                accessPolicy,
                store,
                trustedClock,
                recorder);
        var overrideUseCase = new GateOverrideUseCase(
                new Es256ActionGrantVerifier(keyCache, revocationCache),
                accessPolicy,
                accessPolicy,
                store,
                trustedClock,
                recorder);
        var controller = new ScanController(scanUseCase, overrideUseCase, recorder, presence);

        HttpsServer server = HttpsServer.create(listenAddress, 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverTls) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParams = getSSLContext().getDefaultSSLParameters();
                sslParams.setNeedClientAuth(true);
                params.setSSLParameters(sslParams);
            }
        });
        server.createContext("/edge/v1/", new EdgeHttpHandler(controller, kioskResolver));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        var scheduler = Executors.newScheduledThreadPool(4);
        schedule(scheduler, "sync", Duration.ofSeconds(1), () -> {
            if (sync.runOnce() == SyncWorker.Result.APPLIED) {
                connectivity.recordSuccess();
            }
        });
        schedule(scheduler, "bootstrap", BootstrapWorker.POLL_INTERVAL, () -> {
            bootstrap.refresh();
            syncCachesFromBootstrap(cache, keyCache, revocationCache);
            connectivity.recordSuccess();
        });
        // PHỤ TRÁCH: Task 8 — nối worker refresh khóa của Task 7 vào runtime HTTPS.
        schedule(scheduler, "key-refresh", KeyRefreshWorker.POLL_INTERVAL, () -> {
            if (keyRefresh.refreshIfRequested() == KeyRefreshWorker.Result.APPLIED) {
                syncCachesFromBootstrap(cache, keyCache, revocationCache);
                connectivity.recordSuccess();
            }
        });
        syncCachesFromBootstrap(cache, keyCache, revocationCache);
        schedule(scheduler, "heartbeat", HeartbeatWorker.DEFAULT_CYCLE, heartbeat::sendOnce);
        schedule(scheduler, "housekeeping", Duration.ofHours(1), store::cleanup);

        return new RunningEdge(store, new CachedAccess(cache, keyRefresh::requestRefresh), connectivity, operations, scheduler, server);
    }

    private static void syncCachesFromBootstrap(
            vn.tmc.edge.edgesync.domain.BootstrapCache cache,
            QrKeyCachePort keyCache,
            QrRevocationCachePort revocationCache
    ) {
        cache.snapshot().ifPresent(snap -> {
            var ks = snap.keySet();
            if (ks != null && !ks.keys().isEmpty() && ks.version() > keyCache.revision()) {
                var entries = ks.keys().stream()
                        .map(k -> new vn.tmc.contracts.QrKeySetEntry(
                                k.kid(), k.kty(), k.crv(), k.alg(), k.use(), k.x(), k.y(),
                                k.state().name(), k.notBefore(), k.notAfter()))
                        .toList();
                keyCache.replaceAll(new vn.tmc.contracts.QrKeySet(ks.version(), ks.generatedAt(), ks.trustedUntil(), entries));
            }
            if (snap.revocations() != null) {
                var changes = snap.revocations().stream()
                        .map(r -> new vn.tmc.contracts.RevocationChange(
                                r.id().toString(), r.op().name(), r.kind().name(),
                                r.value(), r.revokedBefore(), r.effectiveAt()))
                        .toList();
                revocationCache.replaceAll(changes);
            }
        });
    }

    public record RunningEdge(LocalEventStorePort eventStore, CachedAccess cache, ConnectivityMonitor connectivity,
                              EdgeOperations operations, ScheduledExecutorService scheduler, HttpsServer httpsServer) implements AutoCloseable {
        public int boundPort() {
            return httpsServer == null ? -1 : httpsServer.getAddress().getPort();
        }

        @Override
        public void close() {
            if (httpsServer != null) {
                httpsServer.stop(0);
            }
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
