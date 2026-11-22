package vn.tmc.central.systemadmin.internal.repository;

import vn.tmc.central.systemadmin.internal.model.Gate;
import java.util.List;
import java.util.Optional;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Repository Port cho Gate.
 * NGUỒN: main.pdf, FR-GS-01; §5.1.
 */
public interface GateRepository {
    Optional<Gate> findById(String id);
    List<Gate> findByFactoryId(String factoryId);
    Optional<Gate> findByTurnstileApiId(String turnstileApiId);
    List<Gate> findAll();
    void save(Gate value);
    void delete(String id);
}
