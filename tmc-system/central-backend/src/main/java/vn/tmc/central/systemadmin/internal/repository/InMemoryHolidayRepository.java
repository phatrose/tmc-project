package vn.tmc.central.systemadmin.internal.repository;

import vn.tmc.central.systemadmin.internal.model.Holiday;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: In-memory Repository Implementation cho Holiday.
 * NGUỒN: main.pdf, FR-SA-02.
 */
public final class InMemoryHolidayRepository implements HolidayRepository {
    private final Map<String, Holiday> store = new ConcurrentHashMap<>();

    public InMemoryHolidayRepository() {
        int currentYear = LocalDate.now().getYear();
        save(new Holiday("HOL-01", null, "Tết Dương lịch", LocalDate.of(currentYear, 1, 1), true));
        save(new Holiday("HOL-02", null, "Giỗ Tổ Hùng Vương", LocalDate.of(currentYear, 4, 18), true));
        save(new Holiday("HOL-03", null, "Ngày Chiến thắng 30/4", LocalDate.of(currentYear, 4, 30), true));
        save(new Holiday("HOL-04", null, "Quốc tế Lao động 1/5", LocalDate.of(currentYear, 5, 1), true));
        save(new Holiday("HOL-05", null, "Quốc khánh 2/9", LocalDate.of(currentYear, 9, 2), true));
    }

    @Override
    public Optional<Holiday> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Holiday> findByFactoryId(String factoryId) {
        List<Holiday> result = new ArrayList<>();
        for (Holiday h : store.values()) {
            if (h.factoryId() == null || h.factoryId().equalsIgnoreCase(factoryId)) {
                result.add(h);
            }
        }
        return result;
    }

    @Override
    public List<Holiday> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void save(Holiday holiday) {
        store.put(holiday.id(), holiday);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
