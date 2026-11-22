package vn.tmc.central.systemadmin.internal.repository;

import vn.tmc.central.systemadmin.internal.model.Shift;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: In-memory Repository Implementation cho Shift.
 * NGUỒN: main.pdf, FR-SA-02; §5.1; DECISIONS.md §1 (Ca đêm kết thúc 06:00 thuộc tháng trước).
 */
public final class InMemoryShiftRepository implements ShiftRepository {
    private final Map<String, Shift> store = new ConcurrentHashMap<>();

    public InMemoryShiftRepository() {
        // Ca chuẩn cho Nhà máy Bình Dương
        save(new Shift("SHIFT-BD-01", "FACTORY-BD", "Ca 1 - Hành chính", LocalTime.of(8, 0), LocalTime.of(17, 0), false, 15, 15, true));
        save(new Shift("SHIFT-BD-02", "FACTORY-BD", "Ca 2 - Chiều", LocalTime.of(14, 0), LocalTime.of(22, 0), false, 15, 15, true));
        save(new Shift("SHIFT-BD-03", "FACTORY-BD", "Ca 3 - Ca đêm", LocalTime.of(22, 0), LocalTime.of(6, 0), true, 15, 15, true));

        // Ca chuẩn cho Nhà máy Long An
        save(new Shift("SHIFT-LA-01", "FACTORY-LA", "Ca 1 - Hành chính", LocalTime.of(7, 30), LocalTime.of(16, 30), false, 15, 15, true));
        save(new Shift("SHIFT-LA-02", "FACTORY-LA", "Ca 2 - Chiều", LocalTime.of(15, 0), LocalTime.of(23, 0), false, 15, 15, true));
        save(new Shift("SHIFT-LA-03", "FACTORY-LA", "Ca 3 - Ca đêm", LocalTime.of(23, 0), LocalTime.of(7, 0), true, 15, 15, true));
    }

    @Override
    public Optional<Shift> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Shift> findByFactoryId(String factoryId) {
        List<Shift> result = new ArrayList<>();
        for (Shift s : store.values()) {
            if (s.factoryId().equalsIgnoreCase(factoryId)) {
                result.add(s);
            }
        }
        return result;
    }

    @Override
    public List<Shift> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void save(Shift shift) {
        store.put(shift.id(), shift);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
