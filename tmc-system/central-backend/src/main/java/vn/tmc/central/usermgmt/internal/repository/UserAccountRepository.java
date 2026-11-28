package vn.tmc.central.usermgmt.internal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.tmc.central.usermgmt.internal.model.UserAccount;

import java.util.Optional;

/**
 * PHỤ TRÁCH: Task 1.
 * Repository quản lý tài khoản người dùng theo CONTRACT-V1.md §3, §4.
 */
@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, String> {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    Optional<UserAccount> findByEmployeeId(String employeeId);

    boolean existsByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);
}
