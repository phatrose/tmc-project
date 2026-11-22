package vn.tmc.central.usermgmt.internal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.tmc.central.usermgmt.internal.model.PolicyAssignment;

import java.util.List;

/**
 * PHỤ TRÁCH: Task 1.
 * Repository quản lý phân quyền khu vực / ca làm của nhân viên theo CONTRACT-V1.md §4.
 */
@Repository
public interface PolicyAssignmentRepository extends JpaRepository<PolicyAssignment, String> {

    List<PolicyAssignment> findByEmployeeId(String employeeId);

    void deleteByEmployeeId(String employeeId);
}
