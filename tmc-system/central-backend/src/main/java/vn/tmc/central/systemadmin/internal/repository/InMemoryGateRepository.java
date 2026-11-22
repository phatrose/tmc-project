package vn.tmc.central.systemadmin.internal.repository;

import vn.tmc.central.systemadmin.internal.model.Gate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: In-memory Repository Implementation cho Gate với seed data khởi tạo.
 * NGUỒN: main.pdf, §1.2; FR-GS-01; §5.1.
 */
public final class InMemoryGateRepository implements GateRepository {
    private final Map<String, Gate> store = new ConcurrentHashMap<>();

    public InMemoryGateRepository() {
        // Seed dữ liệu cổng cho 2 nhà máy Bình Dương và Long An theo §1.2
        save(new Gate("GATE-BD-01", "FACTORY-BD", "EDGE-BD-01", "Cổng chính 1 - Vào", "IN", "TURNSTILE-BD-01-IN", "ACTIVE"));
        save(new Gate("GATE-BD-02", "FACTORY-BD", "EDGE-BD-01", "Cổng chính 2 - Ra", "OUT", "TURNSTILE-BD-02-OUT", "ACTIVE"));
        save(new Gate("GATE-LA-01", "FACTORY-LA", "EDGE-LA-01", "Cổng xưởng A - Vào", "IN", "TURNSTILE-LA-01-IN", "ACTIVE"));
        save(new Gate("GATE-LA-02", "FACTORY-LA", "EDGE-LA-01", "Cổng xưởng A - Ra", "OUT", "TURNSTILE-LA-02-OUT", "ACTIVE"));
    }

    @Override
    public Optional<Gate> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Gate> findByFactoryId(String factoryId) {
        List<Gate> result = new ArrayList<>();
        for (Gate g : store.values()) {
            if (g.factoryId().equalsIgnoreCase(factoryId)) {
                result.add(g);
            }
        }
        return result;
    }

    @Override
    public Optional<Gate> findByTurnstileApiId(String turnstileApiId) {
        if (turnstileApiId == null) return Optional.empty();
        return store.values().stream()
            .filter(g -> turnstileApiId.equalsIgnoreCase(g.turnstileApiId()))
            .findFirst();
    }

    @Override
    public List<Gate> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void save(Gate value) {
        store.put(value.id(), value);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
