package vn.tmc.central.systemadmin.internal.repository;

import vn.tmc.central.systemadmin.internal.model.DeviceStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: In-memory Repository Implementation cho DeviceStatus.
 * NGUỒN: main.pdf, FR-GS-04, FR-SA-04.
 */
public final class InMemoryDeviceStatusRepository implements DeviceStatusRepository {
    private final Map<String, DeviceStatus> store = new ConcurrentHashMap<>();

    public InMemoryDeviceStatusRepository() {
        // Thiết bị trạm cổng và Edge Agent tại 2 nhà máy
        save(new DeviceStatus("EDGE-BD-01", "EDGE_NODE", "Edge Station Bình Dương 01", "FACTORY-BD", "ONLINE", Instant.now(), "v1.2.0", "192.168.10.10", null));
        save(new DeviceStatus("GATE-BD-01", "GATE", "Turnstile Cổng Chính BD 01", "FACTORY-BD", "ONLINE", Instant.now(), "v2.0.4", "192.168.10.21", null));
        save(new DeviceStatus("GATE-BD-02", "GATE", "Turnstile Cổng Chính BD 02", "FACTORY-BD", "ONLINE", Instant.now(), "v2.0.4", "192.168.10.22", null));

        save(new DeviceStatus("EDGE-LA-01", "EDGE_NODE", "Edge Station Long An 01", "FACTORY-LA", "ONLINE", Instant.now(), "v1.2.0", "192.168.20.10", null));
        save(new DeviceStatus("GATE-LA-01", "GATE", "Turnstile Cổng Xưởng LA 01", "FACTORY-LA", "ONLINE", Instant.now(), "v2.0.4", "192.168.20.21", null));
        save(new DeviceStatus("GATE-LA-02", "GATE", "Turnstile Cổng Xưởng LA 02", "FACTORY-LA", "ONLINE", Instant.now(), "v2.0.4", "192.168.20.22", null));
    }

    @Override
    public Optional<DeviceStatus> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<DeviceStatus> findByFactoryId(String factoryId) {
        List<DeviceStatus> result = new ArrayList<>();
        for (DeviceStatus d : store.values()) {
            if (d.factoryId().equalsIgnoreCase(factoryId)) {
                result.add(d);
            }
        }
        return result;
    }

    @Override
    public List<DeviceStatus> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void save(DeviceStatus status) {
        store.put(status.deviceId(), status);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
