package vn.tmc.central.usermgmt.api;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

/**
 * PHỤ TRÁCH: Task 1.
 * Contract công khai cho module user-mgmt — quản lý nhân viên, chính sách phân quyền, phòng ban và tài khoản.
 * Theo CONTRACT-V1.md §4, §10.
 */
public interface EmployeeManagement {

    // ─────────────────────────── Employee Operations ──────────────────────────
    PageResponse<EmployeeView> listEmployees(ActorContext actor, String factoryId, String departmentId, Boolean active, int page, int size);
    EmployeeView getEmployee(ActorContext actor, String id);
    EmployeeView createEmployee(ActorContext actor, CreateEmployeeCommand cmd);
    EmployeeView updateEmployee(ActorContext actor, String id, UpdateEmployeeCommand cmd);
    ImportResult importEmployees(ActorContext actor, List<EmployeeImportItem> records);

    // ─────────────────────────── Access Policy Operations ─────────────────────
    EmployeePolicy getAccessPolicy(ActorContext actor, String employeeId);
    EmployeePolicy updateAccessPolicy(ActorContext actor, String employeeId, UpdatePolicyCommand cmd);

    // ─────────────────────────── Department Operations ────────────────────────
    PageResponse<DepartmentView> listDepartments(ActorContext actor, String factoryId, int page, int size);
    DepartmentView getDepartment(ActorContext actor, String id);
    DepartmentView createDepartment(ActorContext actor, CreateDepartmentCommand cmd);
    DepartmentView updateDepartment(ActorContext actor, String id, UpdateDepartmentCommand cmd);

    // ─────────────────────────── Account Operations ───────────────────────────
    PageResponse<AccountView> listAccounts(ActorContext actor, int page, int size);
    AccountView getAccount(ActorContext actor, String id);
    AccountView createAccount(ActorContext actor, CreateAccountCommand cmd);
    AccountView updateAccount(ActorContext actor, String id, UpdateAccountCommand cmd);

    // ─────────────────────────── Commands & DTOs ──────────────────────────────

    record CreateEmployeeCommand(
            @NotBlank String employeeCode,
            @NotBlank String displayName,
            @NotBlank String factoryId,
            String departmentId,
            boolean active
    ) {}

    record UpdateEmployeeCommand(
            @NotBlank String employeeCode,
            @NotBlank String displayName,
            @NotBlank String factoryId,
            String departmentId,
            boolean active,
            int version
    ) {}

    record EmployeeImportItem(
            String employeeCode,
            String displayName,
            String factoryId,
            String departmentId,
            boolean active,
            Instant sourceUpdatedAt
    ) {}

    record ImportResult(List<ImportItemResult> results) {
        public record ImportItemResult(
                String employeeCode,
                String status, // "CREATED" | "UPDATED" | "UNCHANGED" | "REJECTED"
                String code
        ) {}
    }

    record UpdatePolicyCommand(
            List<EmployeePolicy.Assignment> assignments,
            int version
    ) {}

    record CreateDepartmentCommand(
            @NotBlank String factoryId,
            @NotBlank String name
    ) {}

    record UpdateDepartmentCommand(
            @NotBlank String factoryId,
            @NotBlank String name,
            int version
    ) {}

    record CreateAccountCommand(
            @NotBlank String username,
            String employeeId,
            List<Role> roles,
            List<String> factoryIds,
            List<String> departmentIds,
            String scope,
            @NotBlank String initialPassword
    ) {}

    record UpdateAccountCommand(
            List<Role> roles,
            List<String> factoryIds,
            List<String> departmentIds,
            String scope,
            boolean active,
            int version
    ) {}
}
