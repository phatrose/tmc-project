package vn.tmc.central.usermgmt.internal.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tmc.central.usermgmt.api.*;
import vn.tmc.central.usermgmt.internal.model.Department;
import vn.tmc.central.usermgmt.internal.model.Employee;
import vn.tmc.central.usermgmt.internal.model.PolicyAssignment;
import vn.tmc.central.usermgmt.internal.model.UserAccount;
import vn.tmc.central.usermgmt.internal.repository.DepartmentRepository;
import vn.tmc.central.usermgmt.internal.repository.EmployeeRepository;
import vn.tmc.central.usermgmt.internal.repository.PolicyAssignmentRepository;
import vn.tmc.central.usermgmt.internal.repository.UserAccountRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PHỤ TRÁCH: Task 1.
 * Triển khai đầy đủ các nghiệp vụ quản lý nhân viên, chính sách, phòng ban, tài khoản.
 * Theo CONTRACT-V1.md §4, §10.
 */
@Service
@Transactional
public class EmployeeService implements EmployeeManagement {

    private final EmployeeRepository employeeRepository;
    private final PolicyAssignmentRepository policyAssignmentRepository;
    private final DepartmentRepository departmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(EmployeeRepository employeeRepository,
                           PolicyAssignmentRepository policyAssignmentRepository,
                           DepartmentRepository departmentRepository,
                           UserAccountRepository userAccountRepository,
                           PasswordEncoder passwordEncoder) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository);
        this.policyAssignmentRepository = Objects.requireNonNull(policyAssignmentRepository);
        this.departmentRepository = Objects.requireNonNull(departmentRepository);
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
    }

    // ─────────────────────────── Employees ────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EmployeeView> listEmployees(ActorContext actor, String factoryId, String departmentId, Boolean active, int page, int size) {
        validateScope(actor, factoryId);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));

        Page<Employee> pageResult;
        if (factoryId != null && !factoryId.isBlank()) {
            pageResult = employeeRepository.findByFactoryId(factoryId, pageable);
        } else {
            pageResult = employeeRepository.findAll(pageable);
        }

        List<EmployeeView> items = pageResult.getContent().stream()
                .filter(e -> departmentId == null || departmentId.isBlank() || departmentId.equals(e.getDepartmentId()))
                .filter(e -> active == null || e.isActive() == active)
                .map(Employee::toView)
                .collect(Collectors.toList());

        return new PageResponse<>(items, pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeView getEmployee(ActorContext actor, String id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        if ("SELF".equals(actor.scope()) && !id.equals(actor.employeeId())) {
            throw new ForbiddenException("Chỉ được phép xem hồ sơ của chính mình.");
        }
        return employee.toView();
    }

    @Override
    public EmployeeView createEmployee(ActorContext actor, CreateEmployeeCommand cmd) {
        requirePermission(actor, "employee.manage");
        Objects.requireNonNull(cmd, "cmd required");

        if (employeeRepository.existsByEmployeeCode(cmd.employeeCode())) {
            throw new IllegalArgumentException("Mã nhân viên đã tồn tại: " + cmd.employeeCode());
        }

        Employee employee = new Employee(
                cmd.employeeCode().trim(),
                cmd.displayName().trim(),
                cmd.factoryId(),
                cmd.departmentId(),
                cmd.active()
        );

        employeeRepository.save(employee);
        return employee.toView();
    }

    @Override
    public EmployeeView updateEmployee(ActorContext actor, String id, UpdateEmployeeCommand cmd) {
        requirePermission(actor, "employee.manage");
        Objects.requireNonNull(cmd, "cmd required");

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        employee.update(
                cmd.employeeCode(),
                cmd.displayName(),
                cmd.factoryId(),
                cmd.departmentId(),
                cmd.active(),
                cmd.version()
        );

        employeeRepository.save(employee);
        return employee.toView();
    }

    @Override
    public ImportResult importEmployees(ActorContext actor, List<EmployeeImportItem> records) {
        requirePermission(actor, "employee.manage");
        if (records == null || records.isEmpty()) {
            return new ImportResult(List.of());
        }
        if (records.size() > 100) {
            throw new IllegalArgumentException("Số lượng bản ghi import tối đa là 100.");
        }

        List<ImportResult.ImportItemResult> results = new ArrayList<>();
        for (EmployeeImportItem item : records) {
            if (item.employeeCode() == null || item.employeeCode().isBlank()) {
                results.add(new ImportResult.ImportItemResult("", "REJECTED", "MISSING_EMPLOYEE_CODE"));
                continue;
            }

            Optional<Employee> existingOpt = employeeRepository.findByEmployeeCode(item.employeeCode().trim());
            if (existingOpt.isEmpty()) {
                Employee newEmp = new Employee(
                        item.employeeCode().trim(),
                        item.displayName(),
                        item.factoryId(),
                        item.departmentId(),
                        item.active()
                );
                employeeRepository.save(newEmp);
                results.add(new ImportResult.ImportItemResult(item.employeeCode(), "CREATED", null));
            } else {
                Employee existing = existingOpt.get();
                boolean changed = !Objects.equals(existing.getDisplayName(), item.displayName())
                        || !Objects.equals(existing.getFactoryId(), item.factoryId())
                        || !Objects.equals(existing.getDepartmentId(), item.departmentId())
                        || existing.isActive() != item.active();

                if (changed) {
                    existing.update(item.employeeCode(), item.displayName(), item.factoryId(), item.departmentId(), item.active(), existing.getVersion());
                    employeeRepository.save(existing);
                    results.add(new ImportResult.ImportItemResult(item.employeeCode(), "UPDATED", null));
                } else {
                    results.add(new ImportResult.ImportItemResult(item.employeeCode(), "UNCHANGED", null));
                }
            }
        }

        return new ImportResult(results);
    }

    // ─────────────────────────── Access Policy ────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public EmployeePolicy getAccessPolicy(ActorContext actor, String employeeId) {
        if ("SELF".equals(actor.scope()) && !employeeId.equals(actor.employeeId())) {
            throw new ForbiddenException("Không có quyền xem access policy của người khác.");
        }

        List<PolicyAssignment> list = policyAssignmentRepository.findByEmployeeId(employeeId);
        int version = list.stream().mapToInt(PolicyAssignment::getVersion).max().orElse(1);
        List<EmployeePolicy.Assignment> assignments = list.stream()
                .map(PolicyAssignment::toAssignment)
                .collect(Collectors.toList());

        return new EmployeePolicy(employeeId, assignments, version);
    }

    @Override
    public EmployeePolicy updateAccessPolicy(ActorContext actor, String employeeId, UpdatePolicyCommand cmd) {
        requirePermission(actor, "access-policy.manage");
        Objects.requireNonNull(cmd, "cmd required");

        policyAssignmentRepository.deleteByEmployeeId(employeeId);

        int newVersion = cmd.version() + 1;
        List<PolicyAssignment> entities = cmd.assignments().stream()
                .map(a -> new PolicyAssignment(employeeId, a.gateIds(), a.shiftIds(), a.validFrom(), a.validUntil(), newVersion))
                .collect(Collectors.toList());

        policyAssignmentRepository.saveAll(entities);

        return new EmployeePolicy(employeeId, cmd.assignments(), newVersion);
    }

    // ─────────────────────────── Departments ──────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DepartmentView> listDepartments(ActorContext actor, String factoryId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        Page<Department> pageResult;
        if (factoryId != null && !factoryId.isBlank()) {
            pageResult = departmentRepository.findByFactoryId(factoryId, pageable);
        } else {
            pageResult = departmentRepository.findAll(pageable);
        }

        List<DepartmentView> items = pageResult.getContent().stream()
                .map(Department::toView)
                .collect(Collectors.toList());

        return new PageResponse<>(items, pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentView getDepartment(ActorContext actor, String id) {
        return departmentRepository.findById(id)
                .map(Department::toView)
                .orElseThrow(() -> new EmployeeNotFoundException("Không tìm thấy phòng ban: " + id));
    }

    @Override
    public DepartmentView createDepartment(ActorContext actor, CreateDepartmentCommand cmd) {
        requirePermission(actor, "employee.manage");
        Department dept = new Department(cmd.factoryId(), cmd.name().trim());
        departmentRepository.save(dept);
        return dept.toView();
    }

    @Override
    public DepartmentView updateDepartment(ActorContext actor, String id, UpdateDepartmentCommand cmd) {
        requirePermission(actor, "employee.manage");
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Không tìm thấy phòng ban: " + id));

        dept.update(cmd.name().trim(), cmd.version());
        departmentRepository.save(dept);
        return dept.toView();
    }

    // ─────────────────────────── Accounts ─────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccountView> listAccounts(ActorContext actor, int page, int size) {
        requirePermission(actor, "system.manage");
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        Page<UserAccount> pageResult = userAccountRepository.findAll(pageable);

        List<AccountView> items = pageResult.getContent().stream()
                .map(UserAccount::toView)
                .collect(Collectors.toList());

        return new PageResponse<>(items, pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public AccountView getAccount(ActorContext actor, String id) {
        requirePermission(actor, "system.manage");
        return userAccountRepository.findById(id)
                .map(UserAccount::toView)
                .orElseThrow(() -> new EmployeeNotFoundException("Không tìm thấy tài khoản: " + id));
    }

    @Override
    public AccountView createAccount(ActorContext actor, CreateAccountCommand cmd) {
        requirePermission(actor, "system.manage");
        if (userAccountRepository.existsByUsername(cmd.username())) {
            throw new IllegalArgumentException("Username đã tồn tại: " + cmd.username());
        }

        String hash = passwordEncoder.encode(cmd.initialPassword());
        UserAccount account = new UserAccount(
                cmd.username().trim(),
                hash,
                cmd.employeeId(),
                cmd.roles(),
                cmd.factoryIds(),
                cmd.departmentIds(),
                cmd.scope()
        );

        userAccountRepository.save(account);
        return account.toView();
    }

    @Override
    public AccountView updateAccount(ActorContext actor, String id, UpdateAccountCommand cmd) {
        requirePermission(actor, "system.manage");
        UserAccount account = userAccountRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Không tìm thấy tài khoản: " + id));

        account.update(cmd.roles(), cmd.factoryIds(), cmd.departmentIds(), cmd.scope(), cmd.active(), cmd.version());
        userAccountRepository.save(account);
        return account.toView();
    }

    // ─────────────────────────── Scope & Permission Helpers ───────────────────

    private void requirePermission(ActorContext actor, String perm) {
        if (actor == null || !actor.permissions().contains(perm)) {
            throw new ForbiddenException("Thao tác yêu cầu quyền: " + perm);
        }
    }

    private void validateScope(ActorContext actor, String factoryId) {
        if (actor == null) return;
        if ("FACTORY".equals(actor.scope()) && factoryId != null && !factoryId.isBlank()) {
            if (!actor.factoryIds().contains(factoryId)) {
                throw new ForbiddenException("Không có quyền truy cập nhà máy: " + factoryId);
            }
        }
    }
}
