package vn.tmc.central.systemadmin.internal.repository;

import vn.tmc.central.systemadmin.internal.model.SystemConfiguration;
import java.util.List;
import java.util.Optional;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Repository Port cho SystemConfiguration.
 * NGUỒN: main.pdf, FR-SA-02, FR-SA-03.
 */
public interface SystemConfigurationRepository {
    Optional<SystemConfiguration> findById(String id);
    List<SystemConfiguration> findByCategory(String category);
    List<SystemConfiguration> findAll();
    void save(SystemConfiguration value);
    void delete(String id);
}
