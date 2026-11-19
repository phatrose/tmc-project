package vn.tmc.edge.edgesync.application;

import vn.tmc.edge.edgesync.domain.HeartbeatPort;
import vn.tmc.edge.edgesync.domain.SyncProtocolException;

import java.time.Duration;
import java.util.Objects;

public final class HeartbeatWorker {
    public static final Duration DEFAULT_CYCLE = Duration.ofSeconds(30);
    private final EdgeOperations operations;
    private final HeartbeatPort transport;
    private final ConnectivityMonitor connectivity;

    public HeartbeatWorker(EdgeOperations operations, HeartbeatPort transport, ConnectivityMonitor connectivity) {
        this.operations = Objects.requireNonNull(operations);
        this.transport = Objects.requireNonNull(transport);
        this.connectivity = Objects.requireNonNull(connectivity);
    }

    public boolean sendOnce() {
        var heartbeat = operations.heartbeat();
        if (heartbeat.isEmpty()) {
            return false;
        }
        var receipt = transport.send(heartbeat.orElseThrow());
        if (receipt == null || receipt.serverTime() == null) {
            throw new SyncProtocolException("Heartbeat response does not include serverTime");
        }
        connectivity.recordSuccess();
        return true;
    }
}
