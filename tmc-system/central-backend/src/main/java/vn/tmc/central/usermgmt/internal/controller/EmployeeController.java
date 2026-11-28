package vn.tmc.central.usermgmt.internal.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.tmc.central.usermgmt.api.*;
import vn.tmc.central.usermgmt.api.Authentication.ActionGrantRequest;
import vn.tmc.central.usermgmt.api.Authentication.LoginRequest;
import vn.tmc.central.usermgmt.api.Authentication.ReauthRequest;
import vn.tmc.central.usermgmt.api.Authentication.ReauthResponse;
import vn.tmc.central.usermgmt.api.EmployeeManagement.*;
import vn.tmc.central.usermgmt.internal.service.AccountLockedException;
import vn.tmc.central.usermgmt.internal.service.EmployeeNotFoundException;
import vn.tmc.central.usermgmt.internal.service.ForbiddenException;
import vn.tmc.central.usermgmt.internal.service.InvalidCredentialsException;
import vn.tmc.central.usermgmt.internal.service.VersionConflictException;
import vn.tmc.contracts.ApiError;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * PHỤ TRÁCH: Task 1.
 * REST Controller cho module user-mgmt: xác thực phiên, nhân sự, phân quyền, phòng ban và tài khoản.
 * Theo CONTRACT-V1.md §2, §3, §4, §5.
 */
@RestController
@org.springframework.context.annotation.Profile("!platform")
@RequestMapping("/api/v1")
public class EmployeeController {

    private static final Pattern UUID4 = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final String REQUEST_ID_ATTR = "vn.tmc.central.PlatformErrors.requestId";

    private static final class IdempotencyConflictException extends RuntimeException {
        IdempotencyConflictException(String message) { super(message); }
    }

    private final EmployeeManagement employeeManagement;
    private final Authentication authentication;
    private final vn.tmc.central.usermgmt.internal.service.UserMgmtIdempotencyService idempotencyService;

    @org.springframework.beans.factory.annotation.Autowired
    public EmployeeController(
            EmployeeManagement employeeManagement,
            Authentication authentication,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            vn.tmc.central.usermgmt.internal.service.UserMgmtIdempotencyService idempotencyService
    ) {
        this.employeeManagement = java.util.Objects.requireNonNull(employeeManagement);
        this.authentication = java.util.Objects.requireNonNull(authentication);
        this.idempotencyService = idempotencyService != null
                ? idempotencyService
                : new vn.tmc.central.usermgmt.internal.service.UserMgmtIdempotencyService(null, null, null);
    }

    public EmployeeController(EmployeeManagement employeeManagement, Authentication authentication) {
        this(employeeManagement, authentication, null);
    }

    // ─────────────────────────── 1. Auth Endpoints (§3, §5) ───────────────────

    @PostMapping("/auth/login")
    public ResponseEntity<Session> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpReq) {
        Session session = authentication.authenticate(request);
        org.springframework.http.ResponseCookie cookie = org.springframework.http.ResponseCookie
                .from("tmc_session", session.accessToken())
                .httpOnly(true)
                .sameSite("Strict")
                .secure(httpReq != null && httpReq.isSecure())
                .path("/api/v1")
                .maxAge(900)
                .build();
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString())
                .body(session);
    }

    @GetMapping("/auth/me")
    public ResponseEntity<ActorContext> getMe(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        ActorContext actor = authentication.getActorContext(authHeader);
        return ResponseEntity.ok(actor);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpReq) {
        authentication.logout(authHeader);
        org.springframework.http.ResponseCookie clearCookie = org.springframework.http.ResponseCookie
                .from("tmc_session", "")
                .httpOnly(true)
                .sameSite("Strict")
                .secure(httpReq != null && httpReq.isSecure())
                .path("/api/v1")
                .maxAge(0)
                .build();
        return ResponseEntity.noContent()
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    @PostMapping("/auth/reauth")
    public ResponseEntity<ReauthResponse> reauth(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody ReauthRequest request) {
        return ResponseEntity.ok(authentication.reauth(authHeader, request));
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
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateEmployeeCommand cmd) {
        ActorContext actor = authentication.getActorContext(authHeader);
        return executeIdempotent(actor, "POST:/api/v1/employees", idempotencyKey, cmd,
                HttpStatus.CREATED, EmployeeView.class, () -> employeeManagement.createEmployee(actor, cmd));
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
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ImportBatchRequest request) {
        ActorContext actor = authentication.getActorContext(authHeader);
        return executeIdempotent(actor, "POST:/api/v1/employees/imports", idempotencyKey, request,
                HttpStatus.OK, ImportResult.class, () -> employeeManagement.importEmployees(actor, request.records()));
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
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateDepartmentCommand cmd) {
        ActorContext actor = authentication.getActorContext(authHeader);
        return executeIdempotent(actor, "POST:/api/v1/departments", idempotencyKey, cmd,
                HttpStatus.CREATED, DepartmentView.class, () -> employeeManagement.createDepartment(actor, cmd));
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
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateAccountCommand cmd) {
        ActorContext actor = authentication.getActorContext(authHeader);
        // Không lưu/log initialPassword thô nhưng vẫn phân biệt mật khẩu khác cho cùng Idempotency-Key (IDEM-09)
        Map<String, Object> sanitizedFingerprint = new java.util.LinkedHashMap<>();
        sanitizedFingerprint.put("username", cmd.username());
        sanitizedFingerprint.put("employeeId", cmd.employeeId());
        sanitizedFingerprint.put("roles", cmd.roles());
        sanitizedFingerprint.put("factoryIds", cmd.factoryIds());
        sanitizedFingerprint.put("departmentIds", cmd.departmentIds());
        sanitizedFingerprint.put("scope", cmd.scope());
        sanitizedFingerprint.put("passwordDigest", sha256Hex("tmc-idem-pw-v1:" + (cmd.initialPassword() == null ? "" : cmd.initialPassword())));
        return executeIdempotent(actor, "POST:/api/v1/accounts", idempotencyKey, sanitizedFingerprint,
                HttpStatus.CREATED, AccountView.class, () -> employeeManagement.createAccount(actor, cmd));
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

    // ─────────────────────────── Idempotency Helper (§2) ──────────────────────

    private <T> ResponseEntity<T> executeIdempotent(ActorContext actor, String scopePath, String idempotencyKey,
                                                    Object canonicalPayload, HttpStatus status, Class<T> responseType,
                                                    Supplier<T> action) {
        if (idempotencyKey == null || !UUID4.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("Header Idempotency-Key phải là UUIDv4 chữ thường.");
        }
        try {
            T body = idempotencyService.execute(
                    actor.userId(), scopePath, idempotencyKey, canonicalPayload, status.value(), responseType, action::get);
            return ResponseEntity.status(status).body(body);
        } catch (vn.tmc.central.usermgmt.internal.service.UserMgmtIdempotencyService.ConflictException ex) {
            throw new IdempotencyConflictException(ex.getMessage());
        }
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // ─────────────────────────── Exception Handlers (§2 ApiError) ─────────────

    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ApiError> handleVersionConflict(VersionConflictException ex, HttpServletRequest req, HttpServletResponse res) {
        return errorResponse(req, res, HttpStatus.CONFLICT, "VERSION_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(IdempotencyConflictException ex, HttpServletRequest req, HttpServletResponse res) {
        return errorResponse(req, res, HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex, HttpServletRequest req, HttpServletResponse res) {
        return errorResponse(req, res, HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EmployeeNotFoundException ex, HttpServletRequest req, HttpServletResponse res) {
        return errorResponse(req, res, HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiError> handleLocked(AccountLockedException ex, HttpServletRequest req, HttpServletResponse res) {
        return errorResponse(req, res, HttpStatus.UNAUTHORIZED, "ACCOUNT_LOCKED", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest req, HttpServletResponse res) {
        return errorResponse(req, res, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, HttpServletRequest req, HttpServletResponse res) {
        return errorResponse(req, res, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(org.springframework.web.bind.MethodArgumentNotValidException ex,
                                                     HttpServletRequest req, HttpServletResponse res) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Dữ liệu đầu vào không hợp lệ.");
        return errorResponse(req, res, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", msg);
    }

    private static ResponseEntity<ApiError> errorResponse(HttpServletRequest req, HttpServletResponse res,
                                                          HttpStatus status, String code, String message) {
        String requestId = resolveRequestId(req, res);
        return ResponseEntity.status(status)
                .header("X-Request-Id", requestId)
                .header("Cache-Control", "no-store")
                .body(new ApiError(code, message, requestId, false));
    }

    private static String resolveRequestId(HttpServletRequest req, HttpServletResponse res) {
        Object attr = req != null ? req.getAttribute(REQUEST_ID_ATTR) : null;
        if (attr != null) {
            return attr.toString();
        }
        String headerOut = res != null ? res.getHeader("X-Request-Id") : null;
        if (headerOut != null && UUID4.matcher(headerOut).matches()) {
            return headerOut;
        }
        String headerIn = req != null ? req.getHeader("X-Request-Id") : null;
        if (headerIn != null && UUID4.matcher(headerIn).matches()) {
            return headerIn;
        }
        return UUID.randomUUID().toString();
    }
}
