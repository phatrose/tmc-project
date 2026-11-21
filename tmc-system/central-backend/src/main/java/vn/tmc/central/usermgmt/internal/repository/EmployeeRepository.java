package vn.tmc.central.usermgmt.internal.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.tmc.central.usermgmt.internal.model.Employee;

import java.util.List;
import java.util.Optional;

/**
 * PHỤ TRÁCH: Task 1.
 * Repository truy xuất nhân viên qua Spring Data JPA theo CONTRACT-V1.md §4.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, String>, JpaSpecificationExecutor<Employee> {

    List<Employee> findByFactoryId(String factoryId);

    Page<Employee> findByFactoryId(String factoryId, Pageable pageable);

    boolean existsByEmployeeCode(String code);

    Optional<Employee> findByEmployeeCode(String code);
}
