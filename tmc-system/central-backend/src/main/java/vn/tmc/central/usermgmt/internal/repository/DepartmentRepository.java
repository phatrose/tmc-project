package vn.tmc.central.usermgmt.internal.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.tmc.central.usermgmt.internal.model.Department;

import java.util.List;
import java.util.Optional;

/**
 * PHỤ TRÁCH: Task 1.
 * Repository quản lý phòng ban theo CONTRACT-V1.md §4.
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, String> {

    Page<Department> findByFactoryId(String factoryId, Pageable pageable);

    List<Department> findByFactoryId(String factoryId);

    boolean existsByFactoryIdAndName(String factoryId, String name);
}
