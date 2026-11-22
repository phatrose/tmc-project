package vn.tmc.central.systemadmin.internal.repository;

import vn.tmc.central.systemadmin.internal.model.Shift;
import java.util.List;
import java.util.Optional;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Repository Port cho Shift.
 * NGUỒN: main.pdf, FR-SA-02; §5.1.
 */
public interface ShiftRepository {
    Optional<Shift> findById(String id);
    List<Shift> findByFactoryId(String factoryId);
    List<Shift> findAll();
    void save(Shift shift);
    void delete(String id);
}
