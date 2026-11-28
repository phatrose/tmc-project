package vn.tmc.central.usermgmt.internal.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tmc.central.usermgmt.internal.model.EmployeeRevocationOutboxEntry;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 1 & Task 6 (DATA-07).
 * Repository JPA cho bảng employee_revocation_outbox (V012).
 */
public interface EmployeeRevocationOutboxRepository extends JpaRepository<EmployeeRevocationOutboxEntry, UUID> {
    List<EmployeeRevocationOutboxEntry> findByStatusOrderByCreatedAtAsc(String status);
    List<EmployeeRevocationOutboxEntry> findByEmployeeId(String employeeId);
}
