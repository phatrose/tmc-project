package vn.tmc.central.systemadmin.internal.repository;

import vn.tmc.central.systemadmin.internal.model.DeviceStatus;
import java.util.List;
import java.util.Optional;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Repository Port cho DeviceStatus.
 * NGUỒN: main.pdf, FR-GS-04, FR-SA-04.
 */
public interface DeviceStatusRepository {
    Optional<DeviceStatus> findById(String id);
    List<DeviceStatus> findByFactoryId(String factoryId);
    List<DeviceStatus> findAll();
    void save(DeviceStatus status);
    void delete(String id);
}
