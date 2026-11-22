package vn.tmc.central.systemadmin.internal.repository;

import vn.tmc.central.systemadmin.internal.model.Factory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: In-memory Repository Implementation cho Factory.
 * NGUỒN: main.pdf, §1.2 (Bình Dương ~800, Long An ~600); DECISIONS.md §1.
 */
public final class InMemoryFactoryRepository implements FactoryRepository {
    private final Map<String, Factory> store = new ConcurrentHashMap<>();

    public InMemoryFactoryRepository() {
        save(new Factory("FACTORY-BD", "Nhà máy Bình Dương", "Asia/Ho_Chi_Minh", "KCN VSIP 1, Thuận An, Bình Dương", "ACTIVE"));
        save(new Factory("FACTORY-LA", "Nhà máy Long An", "Asia/Ho_Chi_Minh", "KCN Tân Đức, Đức Hòa, Long An", "ACTIVE"));
    }

    @Override
    public Optional<Factory> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Factory> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void save(Factory factory) {
        store.put(factory.id(), factory);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
