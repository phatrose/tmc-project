package vn.tmc.central.usermgmt.internal.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.tmc.central.usermgmt.api.*;
import vn.tmc.central.usermgmt.api.Authentication.ActionGrantRequest;
import vn.tmc.central.usermgmt.api.Authentication.LoginRequest;
import vn.tmc.central.usermgmt.api.EmployeeManagement.*;
import vn.tmc.central.usermgmt.internal.service.AccountLockedException;
import vn.tmc.central.usermgmt.internal.service.EmployeeNotFoundException;
import vn.tmc.central.usermgmt.internal.service.ForbiddenException;
import vn.tmc.central.usermgmt.internal.service.InvalidCredentialsException;
import vn.tmc.central.usermgmt.internal.service.VersionConflictException;
import vn.tmc.contracts.ApiError;

import java.util.List;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 1.
 * REST Controller cho module user-mgmt: xác thực phiên, nhân sự, phân quyền, phòng ban và tài khoản.
 * Theo CONTRACT-V1.md §2, §3, §4, §5.
 */
@RestController
@RequestMapping("/api/v1")
public class EmployeeController {

    private final EmployeeManagement employeeManagement;
    private final Authentication authentication;

    public EmployeeController(EmployeeManagement employeeManagement, Authentication authentication) {
        this.employeeManagement = java.util.Objects.requireNonNull(employeeManagement);
        this.authentication = java.util.Objects.requireNonNull(authentication);
    }

    // ─────────────────────────── 1. Auth Endpoints (§3, §5) ───────────────────

    @PostMapping("/auth/login")
    public ResponseEntity<Session> login(@Valid @RequestBody LoginRequest request) {
        Session session = authentication.authenticate(request);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/auth/me")
    public ResponseEntity<ActorContext> getMe(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        ActorContext actor = authentication.getActorContext(authHeader);
        return ResponseEntity.ok(actor);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        authentication.logout(authHeader);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/action-grants")
    public ResponseEntity<ActionGrantResponse> issueActionGrant(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody ActionGrantRequest request) {
        ActorContext actor = authentication.getActorContext(authHeader);
        ActionGrantResponse response = authentication.issueActionGrant(actor, request);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────── 2. Employee Endpoints (§4) ───────────────────

    @GetMapping("/employees")
    public ResponseEntity<PageResponse<EmployeeView>> listEmployees(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "factoryId", required = false) String factoryId,
            @RequestParam(value = "departmentId", required = false) String departmentId,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        ActorContext actor = authentication.getActorContext(authHeader);
        PageResponse<EmployeeView> result = employeeManagement.listEmployees(actor, factoryId, departmentId, active, page, size);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/employees")
    public ResponseEntity<EmployeeView> createEmployee(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody CreateEmployeeCommand cmd) {
        ActorContext actor = authentication.getActorContext(authHeader);
        EmployeeView view = employeeManagement.createEmployee(actor, cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping("/employees/{id}")
    public ResponseEntity<EmployeeView> getEmployee(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") String id) {
        ActorContext actor = authentication.getActorContext(authHeader);
        EmployeeView view = employeeManagement.getEmployee(actor, id);
        return ResponseEntity.ok(view);
    }

    @PutMapping("/employees/{id}")
    public ResponseEntity<EmployeeView> updateEmployee(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateEmployeeCommand cmd) {
        ActorContext actor = authentication.getActorContext(authHeader);
        EmployeeView view = employeeManagement.updateEmployee(actor, id, cmd);
        return ResponseEntity.ok(view);
    }

    public record ImportBatchRequest(List<EmployeeImportItem> records) {}

    @PostMapping("/employees/imports")
    public ResponseEntity<ImportResult> importEmployees(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody ImportBatchRequest request) {
        ActorContext actor = authentication.getActorContext(authHeader);
        ImportResult result = employeeManagement.importEmployees(actor, request.records());
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────── 3. Access Policy Endpoints (§4) ──────────────

    @GetMapping("/employees/{id}/access-policy")
    public ResponseEntity<EmployeePolicy> getAccessPolicy(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") String id) {
        ActorContext actor = authentication.getActorContext(authHeader);
        EmployeePolicy policy = employeeManagement.getAccessPolicy(actor, id);
        return ResponseEntity.ok(policy);
    }

    @PutMapping("/employees/{id}/access-policy")
    public ResponseEntity<EmployeePolicy> updateAccessPolicy(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") String id,
            @Valid @RequestBody UpdatePolicyCommand cmd) {
        ActorContext actor = authentication.getActorContext(authHeader);
        EmployeePolicy policy = employeeManagement.updateAccessPolicy(actor, id, cmd);
        return ResponseEntity.ok(policy);
    }

    // ─────────────────────────── 4. Department Endpoints (§4) ─────────────────

    @GetMapping("/departments")
    public ResponseEntity<PageResponse<DepartmentView>> listDepartments(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "factoryId", required = false) String factoryId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        ActorContext actor = authentication.getActorContext(authHeader);
        PageResponse<DepartmentView> result = employeeManagement.listDepartments(actor, factoryId, page, size);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/departments")
    public ResponseEntity<DepartmentView> createDepartment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody CreateDepartmentCommand cmd) {
        ActorContext actor = authentication.getActorContext(authHeader);
        DepartmentView view = employeeManagement.createDepartment(actor, cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping("/departments/{id}")
    public ResponseEntity<DepartmentView> getDepartment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") String id) {
        ActorContext actor = authentication.getActorContext(authHeader);
        DepartmentView view = employeeManagement.getDepartment(actor, id);
        return ResponseEntity.ok(view);
    }

    @PutMapping("/departments/{id}")
    public ResponseEntity<DepartmentView> updateDepartment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateDepartmentCommand cmd) {
        ActorContext actor = authentication.getActorContext(authHeader);
        DepartmentView view = employeeManagement.updateDepartment(actor, id, cmd);
        return ResponseEntity.ok(view);
    }

    // ─────────────────────────── 5. Account Endpoints (§4) ────────────────────

    @GetMapping("/accounts")
    public ResponseEntity<PageResponse<AccountView>> listAccounts(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        ActorContext actor = authentication.getActorContext(authHeader);
        PageResponse<AccountView> result = employeeManagement.listAccounts(actor, page, size);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountView> createAccount(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody CreateAccountCommand cmd) {
        ActorContext actor = authentication.getActorContext(authHeader);
        AccountView view = employeeManagement.createAccount(actor, cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @PutMapping("/accounts/{id}")
    public ResponseEntity<AccountView> updateAccount(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateAccountCommand cmd) {
        ActorContext actor = authentication.getActorContext(authHeader);
        AccountView view = employeeManagement.updateAccount(actor, id, cmd);
        return ResponseEntity.ok(view);
    }

    // ─────────────────────────── Exception Handlers (§2 ApiError) ─────────────

    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ApiError> handleVersionConflict(VersionConflictException ex) {
        ApiError error = new ApiError("VERSION_CONFLICT", ex.getMessage(), UUID.randomUUID().toString(), false);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        ApiError error = new ApiError("FORBIDDEN", ex.getMessage(), UUID.randomUUID().toString(), false);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EmployeeNotFoundException ex) {
        ApiError error = new ApiError("NOT_FOUND", ex.getMessage(), UUID.randomUUID().toString(), false);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiError> handleLocked(AccountLockedException ex) {
        ApiError error = new ApiError("ACCOUNT_LOCKED", ex.getMessage(), UUID.randomUUID().toString(), false);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        ApiError error = new ApiError("UNAUTHENTICATED", ex.getMessage(), UUID.randomUUID().toString(), false);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        ApiError error = new ApiError("VALIDATION_ERROR", ex.getMessage(), UUID.randomUUID().toString(), false);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Dữ liệu đầu vào không hợp lệ.");
        ApiError error = new ApiError("VALIDATION_ERROR", msg, UUID.randomUUID().toString(), false);
        return ResponseEntity.badRequest().body(error);
    }
}
