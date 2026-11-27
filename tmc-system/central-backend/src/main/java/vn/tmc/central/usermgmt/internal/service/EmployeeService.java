package vn.tmc.central.usermgmt.internal.service;

import org.springframework.beans.factory.annotation.Autowired;
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
@org.springframework.context.annotation.Profile("!platform")
@Transactional
public class EmployeeService implements EmployeeManagement {

    private static final Set<String> VALID_SCOPES = Set.of(
            ActorContext.SCOPE_SELF,
            ActorContext.SCOPE_DEPARTMENT,
            ActorContext.SCOPE_FACTORY,
            ActorContext.SCOPE_GLOBAL
    );

    private final EmployeeRepository employeeRepository;
    private final PolicyAssignmentRepository policyAssignmentRepository;
    private final DepartmentRepository departmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Authentication authentication;

    public EmployeeService(EmployeeRepository employeeRepository,
                           PolicyAssignmentRepository policyAssignmentRepository,
                           DepartmentRepository departmentRepository,
                           UserAccountRepository userAccountRepository,
                           PasswordEncoder passwordEncoder) {
        this(employeeRepository, policyAssignmentRepository, departmentRepository,
                userAccountRepository, passwordEncoder, null);
    }

    @Autowired
    public EmployeeService(EmployeeRepository employeeRepository,
                           PolicyAssignmentRepository policyAssignmentRepository,
                           DepartmentRepository departmentRepository,
                           UserAccountRepository userAccountRepository,
                           PasswordEncoder passwordEncoder,
                           Authentication authentication) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository);
        this.policyAssignmentRepository = Objects.requireNonNull(policyAssignmentRepository);
        this.departmentRepository = Objects.requireNonNull(departmentRepository);
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.authentication = authentication;
    }

    // ─────────────────────────── Employees ────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EmployeeView> listEmployees(ActorContext actor, String factoryId, String departmentId, Boolean active, int page, int size) {
        validatePaging(page, size);
        validateScope(actor, factoryId, departmentId);

        if (actor != null && ActorContext.SCOPE_SELF.equals(actor.scope())) {
            if (actor.employeeId() == null || actor.employeeId().isBlank()) {
                return new PageResponse<>(List.of(), page, size, 0);
            }
            if (page > 0) {
                return new PageResponse<>(List.of(), page, size, 0);
            }
            Optional<Employee> selfOpt = employeeRepository.findById(actor.employeeId());
            List<EmployeeView> selfItems = selfOpt.stream()
                    .filter(e -> factoryId == null || factoryId.isBlank() || factoryId.equals(e.getFactoryId()))
                    .filter(e -> departmentId == null || departmentId.isBlank() || departmentId.equals(e.getDepartmentId()))
                    .filter(e -> active == null || e.isActive() == active)
                    .map(Employee::toView)
                    .toList();
            return new PageResponse<>(selfItems, page, size, selfItems.size());
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Employee> pageResult;
        if (factoryId != null && !factoryId.isBlank()) {
            pageResult = employeeRepository.findByFactoryId(factoryId, pageable);
        } else {
            pageResult = employeeRepository.findAll(pageable);
        }

        List<EmployeeView> items = pageResult.getContent().stream()
                .filter(e -> isEmployeeVisible(actor, e))
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

        if (!isEmployeeVisible(actor, employee)) {
            throw new ForbiddenException("Không có quyền xem hồ sơ nhân viên ngoài phạm vi.");
        }
        return employee.toView();
    }

    @Override
    public EmployeeView createEmployee(ActorContext actor, CreateEmployeeCommand cmd) {
        requirePermission(actor, "employee.manage");
        Objects.requireNonNull(cmd, "cmd required");
        validateEmployeeFields(cmd.employeeCode(), cmd.displayName());
        validateFactoryMutationScope(actor, cmd.factoryId());

        String code = cmd.employeeCode().trim();
        if (employeeRepository.existsByEmployeeCode(code)) {
            throw new IllegalArgumentException("Mã nhân viên đã tồn tại: " + code);
        }

        Employee employee = new Employee(
                code,
                cmd.displayName().trim(),
                cmd.factoryId().trim(),
                cmd.departmentId() != null && !cmd.departmentId().isBlank() ? cmd.departmentId().trim() : null,
                cmd.active()
        );

        employeeRepository.save(employee);
        return employee.toView();
    }

    @Override
    public EmployeeView updateEmployee(ActorContext actor, String id, UpdateEmployeeCommand cmd) {
        requirePermission(actor, "employee.manage");
        Objects.requireNonNull(cmd, "cmd required");
        validateEmployeeFields(cmd.employeeCode(), cmd.displayName());

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        validateFactoryMutationScope(actor, employee.getFactoryId());
        validateFactoryMutationScope(actor, cmd.factoryId());

        employee.update(
                cmd.employeeCode().trim(),
                cmd.displayName().trim(),
                cmd.factoryId().trim(),
                cmd.departmentId() != null && !cmd.departmentId().isBlank() ? cmd.departmentId().trim() : null,
                cmd.active(),
                cmd.version()
        );

        employeeRepository.save(employee);
        if (!cmd.active() && authentication != null) {
            userAccountRepository.findByEmployeeId(id)
                    .ifPresent(acc -> authentication.revokeSessionsForUser(acc.getId()));
        }
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
            if (item.employeeCode() == null || item.employeeCode().isBlank() || item.employeeCode().trim().length() > 64) {
                results.add(new ImportResult.ImportItemResult("", "REJECTED", "INVALID_EMPLOYEE_CODE"));
                continue;
            }
            if (item.displayName() == null || item.displayName().isBlank() || item.displayName().trim().length() > 200) {
                results.add(new ImportResult.ImportItemResult(item.employeeCode().trim(), "REJECTED", "INVALID_DISPLAY_NAME"));
                continue;
            }
            if (item.factoryId() == null || item.factoryId().isBlank()) {
                results.add(new ImportResult.ImportItemResult(item.employeeCode().trim(), "REJECTED", "MISSING_FACTORY_ID"));
                continue;
            }
            if (actor != null && ActorContext.SCOPE_FACTORY.equals(actor.scope())
                    && !actor.factoryIds().contains(item.factoryId().trim())) {
                results.add(new ImportResult.ImportItemResult(item.employeeCode().trim(), "REJECTED", "SCOPE_MISMATCH"));
                continue;
            }

            String code = item.employeeCode().trim();
            Optional<Employee> existingOpt = employeeRepository.findByEmployeeCode(code);
            if (existingOpt.isEmpty()) {
                Employee newEmp = new Employee(
                        code,
                        item.displayName().trim(),
                        item.factoryId().trim(),
                        item.departmentId(),
                        item.active()
                );
                employeeRepository.save(newEmp);
                results.add(new ImportResult.ImportItemResult(code, "CREATED", null));
            } else {
                Employee existing = existingOpt.get();
                if (item.sourceUpdatedAt() != null && existing.getUpdatedAt() != null
                        && !item.sourceUpdatedAt().isAfter(existing.getUpdatedAt())) {
                    results.add(new ImportResult.ImportItemResult(code, "UNCHANGED", null));
                    continue;
                }
                boolean changed = !Objects.equals(existing.getDisplayName(), item.displayName().trim())
                        || !Objects.equals(existing.getFactoryId(), item.factoryId().trim())
                        || !Objects.equals(existing.getDepartmentId(), item.departmentId())
                        || existing.isActive() != item.active();

                if (changed) {
                    existing.update(code, item.displayName().trim(), item.factoryId().trim(), item.departmentId(), item.active(), existing.getVersion());
                    employeeRepository.save(existing);
                    if (!item.active() && authentication != null) {
                        userAccountRepository.findByEmployeeId(existing.getId())
                                .ifPresent(acc -> authentication.revokeSessionsForUser(acc.getId()));
                    }
                    results.add(new ImportResult.ImportItemResult(code, "UPDATED", null));
                } else {
                    results.add(new ImportResult.ImportItemResult(code, "UNCHANGED", null));
                }
            }
        }

        return new ImportResult(results);
    }

    // ─────────────────────────── Access Policy ────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public EmployeePolicy getAccessPolicy(ActorContext actor, String employeeId) {
        if (actor != null) {
            if (ActorContext.SCOPE_SELF.equals(actor.scope())) {
                if (!employeeId.equals(actor.employeeId())) {
                    throw new ForbiddenException("Không có quyền xem access policy của người khác.");
                }
            } else if (!ActorContext.SCOPE_GLOBAL.equals(actor.scope())) {
                if (!actor.permissions().contains("access-policy.manage") && !employeeId.equals(actor.employeeId())) {
                    throw new ForbiddenException("Không có quyền xem access policy của nhân viên khác.");
                }
                Employee employee = employeeRepository.findById(employeeId)
                        .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
                if (!isEmployeeVisible(actor, employee)) {
                    throw new ForbiddenException("Nhân viên nằm ngoài phạm vi quản lý.");
                }
            }
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
        if (cmd.assignments() == null) {
            throw new IllegalArgumentException("assignments bắt buộc");
        }
        for (EmployeePolicy.Assignment assignment : cmd.assignments()) {
            if (assignment == null || assignment.gateIds() == null || assignment.gateIds().isEmpty()
                    || assignment.shiftIds() == null || assignment.shiftIds().isEmpty()
                    || assignment.validFrom() == null) {
                throw new IllegalArgumentException("Mỗi assignment phải có ít nhất 1 gateId, 1 shiftId và validFrom");
            }
            if (assignment.validUntil() != null && !assignment.validUntil().isAfter(assignment.validFrom())) {
                throw new IllegalArgumentException("validUntil phải sau validFrom");
            }
        }
        if (actor != null && !ActorContext.SCOPE_GLOBAL.equals(actor.scope())) {
            employeeRepository.findById(employeeId).ifPresent(emp -> validateFactoryMutationScope(actor, emp.getFactoryId()));
        }

        List<PolicyAssignment> existing = policyAssignmentRepository.findByEmployeeId(employeeId);
        int currentVersion = existing.stream().mapToInt(PolicyAssignment::getVersion).max().orElse(1);
        if (!existing.isEmpty() && currentVersion != cmd.version()) {
            throw new VersionConflictException(cmd.version(), currentVersion);
        }

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
        validatePaging(page, size);
        validateScope(actor, factoryId, null);
        Pageable pageable = PageRequest.of(page, size);
        Page<Department> pageResult;
        if (factoryId != null && !factoryId.isBlank()) {
            pageResult = departmentRepository.findByFactoryId(factoryId, pageable);
        } else {
            pageResult = departmentRepository.findAll(pageable);
        }

        List<DepartmentView> items = pageResult.getContent().stream()
                .filter(d -> actor == null || ActorContext.SCOPE_GLOBAL.equals(actor.scope())
                        || actor.factoryIds().isEmpty() || actor.factoryIds().contains(d.getFactoryId()))
                .map(Department::toView)
                .collect(Collectors.toList());

        return new PageResponse<>(items, pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentView getDepartment(ActorContext actor, String id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Không tìm thấy phòng ban: " + id));
        if (actor != null && ActorContext.SCOPE_FACTORY.equals(actor.scope())
                && !actor.factoryIds().contains(dept.getFactoryId())) {
            throw new ForbiddenException("Không có quyền xem phòng ban ngoài nhà máy được gán.");
        }
        return dept.toView();
    }

    @Override
    public DepartmentView createDepartment(ActorContext actor, CreateDepartmentCommand cmd) {
        requirePermission(actor, "employee.manage");
        validateFactoryMutationScope(actor, cmd.factoryId());
        Department dept = new Department(cmd.factoryId().trim(), cmd.name().trim());
        departmentRepository.save(dept);
        return dept.toView();
    }

    @Override
    public DepartmentView updateDepartment(ActorContext actor, String id, UpdateDepartmentCommand cmd) {
        requirePermission(actor, "employee.manage");
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Không tìm thấy phòng ban: " + id));
        validateFactoryMutationScope(actor, dept.getFactoryId());

        dept.update(cmd.name().trim(), cmd.version());
        departmentRepository.save(dept);
        return dept.toView();
    }

    // ─────────────────────────── Accounts ─────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccountView> listAccounts(ActorContext actor, int page, int size) {
        validatePaging(page, size);
        requirePermission(actor, "system.manage");
        Pageable pageable = PageRequest.of(page, size);
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
        Objects.requireNonNull(cmd, "cmd required");
        String username = cmd.username() == null ? "" : cmd.username().trim();
        if (username.length() < 3 || username.length() > 64) {
            throw new IllegalArgumentException("username phải dài từ 3 đến 64 ký tự.");
        }
        if (cmd.initialPassword() == null || cmd.initialPassword().length() < 12 || cmd.initialPassword().length() > 128) {
            throw new IllegalArgumentException("initialPassword phải dài từ 12 đến 128 ký tự.");
        }
        String scope = validateAccountScope(actor, cmd.scope());

        if (userAccountRepository.existsByUsername(username) || userAccountRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username đã tồn tại: " + username);
        }

        String hash = passwordEncoder.encode(cmd.initialPassword());
        UserAccount account = new UserAccount(
                username,
                hash,
                cmd.employeeId(),
                cmd.roles(),
                cmd.factoryIds(),
                cmd.departmentIds(),
                scope
        );

        userAccountRepository.save(account);
        return account.toView();
    }

    @Override
    public AccountView updateAccount(ActorContext actor, String id, UpdateAccountCommand cmd) {
        requirePermission(actor, "system.manage");
        Objects.requireNonNull(cmd, "cmd required");
        UserAccount account = userAccountRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Không tìm thấy tài khoản: " + id));
        String scope = cmd.scope() != null ? validateAccountScope(actor, cmd.scope()) : null;

        account.update(cmd.roles(), cmd.factoryIds(), cmd.departmentIds(), scope, cmd.active(), cmd.version());
        userAccountRepository.save(account);
        if (authentication != null) {
            authentication.revokeSessionsForUser(id);
        }
        return account.toView();
    }

    // ─────────────────────────── Scope & Permission Helpers ───────────────────

    private static void validatePaging(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page phải >= 0 và size phải từ 1 đến 100.");
        }
    }

    private static void validateEmployeeFields(String employeeCode, String displayName) {
        if (employeeCode == null || employeeCode.isBlank() || employeeCode.trim().length() > 64) {
            throw new IllegalArgumentException("employeeCode phải dài từ 1 đến 64 ký tự.");
        }
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 200) {
            throw new IllegalArgumentException("displayName phải dài từ 1 đến 200 ký tự.");
        }
    }

    private static String validateAccountScope(ActorContext actor, String scope) {
        String resolved = (scope == null || scope.isBlank()) ? ActorContext.SCOPE_SELF : scope.trim();
        if (!VALID_SCOPES.contains(resolved)) {
            throw new IllegalArgumentException("scope không hợp lệ: " + resolved);
        }
        if (ActorContext.SCOPE_GLOBAL.equals(resolved) && actor != null && !ActorContext.SCOPE_GLOBAL.equals(actor.scope())) {
            throw new ForbiddenException("Chỉ tài khoản GLOBAL mới được phép cấp scope GLOBAL.");
        }
        return resolved;
    }

    private void requirePermission(ActorContext actor, String perm) {
        if (actor == null || actor.isAuditor() || !actor.permissions().contains(perm)) {
            throw new ForbiddenException("Thao tác yêu cầu quyền: " + perm);
        }
    }

    private void validateFactoryMutationScope(ActorContext actor, String factoryId) {
        if (actor == null || ActorContext.SCOPE_GLOBAL.equals(actor.scope())) {
            return;
        }
        if (factoryId == null || !actor.factoryIds().contains(factoryId.trim())) {
            throw new ForbiddenException("Không có quyền thao tác ngoài phạm vi nhà máy được gán.");
        }
    }

    private void validateScope(ActorContext actor, String factoryId, String departmentId) {
        if (actor == null || ActorContext.SCOPE_GLOBAL.equals(actor.scope())) {
            return;
        }
        if (ActorContext.SCOPE_FACTORY.equals(actor.scope()) && factoryId != null && !factoryId.isBlank()) {
            if (!actor.factoryIds().contains(factoryId)) {
                throw new ForbiddenException("Không có quyền truy cập nhà máy: " + factoryId);
            }
        }
        if (ActorContext.SCOPE_DEPARTMENT.equals(actor.scope()) && departmentId != null && !departmentId.isBlank()) {
            if (!actor.departmentIds().contains(departmentId)) {
                throw new ForbiddenException("Không có quyền truy cập phòng ban: " + departmentId);
            }
        }
    }

    private boolean isEmployeeVisible(ActorContext actor, Employee employee) {
        if (actor == null || ActorContext.SCOPE_GLOBAL.equals(actor.scope())) {
            return true;
        }
        if (employee.getId().equals(actor.employeeId())) {
            return true;
        }
        return switch (actor.scope()) {
            case ActorContext.SCOPE_SELF -> false;
            case ActorContext.SCOPE_DEPARTMENT -> employee.getDepartmentId() != null
                    && actor.departmentIds().contains(employee.getDepartmentId());
            case ActorContext.SCOPE_FACTORY -> actor.factoryIds().contains(employee.getFactoryId());
            default -> false;
        };
    }
}
