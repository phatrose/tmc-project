package vn.tmc.central.systemadmin.internal.repository;

import vn.tmc.central.systemadmin.internal.model.Factory;
import java.util.List;
import java.util.Optional;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Repository Port cho Factory.
 * NGUỒN: main.pdf, §1.2; §5.1; DECISIONS.md §1.
 */
public interface FactoryRepository {
    Optional<Factory> findById(String id);
    List<Factory> findAll();
    void save(Factory factory);
    void delete(String id);
}
