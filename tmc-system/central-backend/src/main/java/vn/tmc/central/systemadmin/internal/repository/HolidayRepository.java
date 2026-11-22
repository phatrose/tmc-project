package vn.tmc.central.systemadmin.internal.repository;

import vn.tmc.central.systemadmin.internal.model.Holiday;
import java.util.List;
import java.util.Optional;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Repository Port cho Holiday.
 * NGUỒN: main.pdf, FR-SA-02.
 */
public interface HolidayRepository {
    Optional<Holiday> findById(String id);
    List<Holiday> findByFactoryId(String factoryId);
    List<Holiday> findAll();
    void save(Holiday holiday);
    void delete(String id);
}
