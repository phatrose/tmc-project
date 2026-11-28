package vn.tmc.central.systemadmin.internal.controller;

import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vn.tmc.central.systemadmin.api.*;
import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.central.usermgmt.api.Authentication;
import vn.tmc.central.usermgmt.api.Role;
import vn.tmc.contracts.ApiError;

/**
 * PHỤ TRÁCH: Task 5 / Task 8. REST API Quản trị hệ thống & Cổng trong profile integration-preview
 * với kiểm tra RBAC rõ ràng (ADMIN / AUDITOR / GUARD / HR), optimistic version (FE-ADMIN-05)
 * và re-auth 5 phút cho tác vụ vận hành nhạy cảm (FE-ADMIN-08).
 */
@RestController
@Profile("integration-preview")
@RequestMapping("/api/v1")
public final class PreviewSystemAdminController {

    private static final Set<String> ALLOWED_OPERATION_JOBS = Set.of("BACKUP", "RESTORE", "ARCHIVE", "ANONYMIZE", "RETENTION_AUDIT", "DATA_ARCHIVAL_DRY_RUN");
    private static final Set<String> ALLOWED_PRIVACY_TYPES = Set.of("ACCESS", "RECTIFY", "ANONYMIZE");
    private static final java.util.regex.Pattern UUID4_PATTERN = java.util.regex.Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final Map<String, String> IMMUTABLE_CONTRACT_CONFIGS = Map.of(
            "qr.ttl.seconds", "60",
            "session.ttl.minutes", "15",
            "clock.skew.seconds", "5",
            "duplicate.scan.window.seconds", "30",
            "revocation.poll.seconds", "30",
            "offline.target.hours", "24"
    );

    public record SystemConfigDto(int version, int duplicateScanWindowSeconds, int qrTtlSeconds, int revocationPollSeconds, int offlineTargetHours) {}
    public record OperationJobDto(String id, String type, String state, Instant createdAt, Instant completedAt, String errorCode) {}
    public record PrivacyRequestDto(String id, String userId, String type, String reason, String state, Instant createdAt, String decisionReason, int version) {}

    private final Administration administration;
    private final GateManagement gateManagement;
    private final Authentication authentication;
    private final Map<String, Integer> resourceVersions = new ConcurrentHashMap<>();
    private final Map<String, OperationJobDto> operationJobs = new ConcurrentHashMap<>();
    private final Map<String, PrivacyRequestDto> privacyRequests = new ConcurrentHashMap<>();

    public PreviewSystemAdminController(Administration administration, GateManagement gateManagement) {
        this(administration, gateManagement, null);
    }

    @Autowired
    public PreviewSystemAdminController(
            Administration administration,
            GateManagement gateManagement,
            @Autowired(required = false) Authentication authentication
    ) {
        this.administration = Objects.requireNonNull(administration, "administration required");
        this.gateManagement = Objects.requireNonNull(gateManagement, "gateManagement required");
        this.authentication = authentication;
    }

    private int checkAndBumpVersion(String resourceKey, Object expectedVersionRaw) {
        synchronized (resourceVersions) {
            int current = resourceVersions.getOrDefault(resourceKey, 1);
            if (expectedVersionRaw != null) {
                int expected;
                try {
                    expected = Integer.parseInt(String.valueOf(expectedVersionRaw).trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("version phải là số nguyên dương.");
                }
                if (expected != current) {
                    throw new OptimisticConflictException(
                            "Xung đột phiên bản tài nguyên " + resourceKey + ": gửi v" + expected + ", hiện tại v" + current);
                }
            }
            int next = current + 1;
            resourceVersions.put(resourceKey, next);
            return next;
        }
    }

    private static final class OptimisticConflictException extends RuntimeException {
        OptimisticConflictException(String message) { super(message); }
    }

    @ExceptionHandler(OptimisticConflictException.class)
    public ResponseEntity<ApiError> handleOptimisticConflict(OptimisticConflictException ex) {
        String requestId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("X-Request-Id", requestId)
                .header("Cache-Control", "no-store")
                .body(new ApiError("VERSION_CONFLICT", ex.getMessage(), requestId, false));
    }

    private ActorContext requireActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof ActorContext actor)) {
            throw new AuthenticationCredentialsNotFoundException("Session required");
        }
        return actor;
    }

    private ActorContext requireAdmin() {
        ActorContext actor = requireActor();
        if (actor.isAuditor() || !actor.roles().contains(Role.ADMIN)) {
            throw new AccessDeniedException("Chỉ quản trị viên (ADMIN) mới có quyền thay đổi cấu hình hệ thống hoặc cổng.");
        }
        return actor;
    }

    private ActorContext requireAdminOrAuditor() {
        ActorContext actor = requireActor();
        boolean allowed = actor.roles().contains(Role.ADMIN) || actor.roles().contains(Role.AUDITOR);
        if (!allowed) {
            throw new AccessDeniedException("Không có quyền truy cập mục Quản trị hệ thống.");
        }
        return actor;
    }

    private ActorContext requireGateReader() {
        ActorContext actor = requireActor();
        boolean allowed = actor.roles().contains(Role.ADMIN)
                || actor.roles().contains(Role.AUDITOR)
                || actor.roles().contains(Role.HR)
                || actor.roles().contains(Role.GUARD);
        if (!allowed) {
            throw new AccessDeniedException("Không có quyền xem danh sách cổng kiểm soát.");
        }
        return actor;
    }

    /** Endpoint chuẩn cho Guard Console (GET /api/v1/gates?factoryId=&page=&size=). */
    @GetMapping("/gates")
    public Map<String, Object> listGatesPaged(
            @RequestParam(value = "factoryId", required = false) String factoryId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "100") int size
    ) {
        requireGateReader();
        List<GateView> all = (factoryId == null || factoryId.isBlank() || "ALL".equalsIgnoreCase(factoryId))
                ? gateManagement.findAllGates()
                : gateManagement.findGatesByFactory(factoryId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));
        int fromIdx = Math.min(safePage * safeSize, all.size());
        int toIdx = Math.min(fromIdx + safeSize, all.size());
        return Map.of(
                "items", all.subList(fromIdx, toIdx),
                "page", safePage,
                "size", safeSize,
                "totalElements", all.size()
        );
    }

    @GetMapping("/admin/gates")
    public List<GateView> listGates(@RequestParam(value = "factoryId", required = false) String factoryId) {
        requireGateReader();
        return (factoryId == null || factoryId.isBlank() || "ALL".equalsIgnoreCase(factoryId))
                ? gateManagement.findAllGates()
                : gateManagement.findGatesByFactory(factoryId);
    }

    @PostMapping("/admin/gates")
    public ResponseEntity<GateView> registerGate(@RequestBody Map<String, String> body) {
        requireAdmin();
        if (body == null) throw new IllegalArgumentException("Thiếu dữ liệu đăng ký cổng.");
        GateView created = gateManagement.registerGate(
                body.get("id"),
                body.get("factoryId"),
                body.get("edgeId"),
                body.get("name"),
                body.get("direction"),
                body.get("turnstileApiId")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/admin/gates/{gateId}/status")
    public ResponseEntity<GateView> updateGateStatus(
            @PathVariable("gateId") String gateId,
            @RequestBody Map<String, Object> body
    ) {
        requireAdmin();
        String status = body == null || body.get("status") == null ? null : String.valueOf(body.get("status"));
        Object expectedVersion = body == null ? null : body.get("version");
        int nextVersion = checkAndBumpVersion("gate:" + gateId, expectedVersion);
        GateView updated = gateManagement.updateGateStatus(gateId, status);
        return ResponseEntity.ok()
                .header("X-Resource-Version", String.valueOf(nextVersion))
                .body(updated);
    }

    @GetMapping("/admin/shifts")
    public List<ShiftView> listShifts(@RequestParam(value = "factoryId", required = false) String factoryId) {
        requireAdminOrAuditor();
        if (factoryId != null && !factoryId.isBlank() && !"ALL".equalsIgnoreCase(factoryId)) {
            return administration.findShiftsByFactory(factoryId);
        }
        List<ShiftView> all = new ArrayList<>();
        for (FactoryView f : administration.findAllFactories()) {
            all.addAll(administration.findShiftsByFactory(f.id()));
        }
        return all;
    }

    @GetMapping("/admin/holidays")
    public List<HolidayView> listHolidays(
            @RequestParam(value = "factoryId", required = false) String factoryId,
            @RequestParam(value = "year", required = false) Integer year
    ) {
        requireAdminOrAuditor();
        int targetYear = year != null ? year : Year.now().getValue();
        if (factoryId != null && !factoryId.isBlank() && !"ALL".equalsIgnoreCase(factoryId)) {
            return administration.findHolidays(factoryId, targetYear);
        }
        List<HolidayView> all = new ArrayList<>();
        for (FactoryView f : administration.findAllFactories()) {
            all.addAll(administration.findHolidays(f.id(), targetYear));
        }
        return all;
    }

    @GetMapping("/admin/configurations")
    public List<SystemConfigurationView> listConfigurations() {
        requireAdminOrAuditor();
        return administration.findAllConfigurations();
    }

    /** CONTRACT-V1 §4: GET /api/v1/system/config trả object cố định v1 cùng version. */
    @GetMapping("/system/config")
    public SystemConfigDto getSystemConfig() {
        requireAdminOrAuditor();
        int version = resourceVersions.getOrDefault("system:config", 1);
        return new SystemConfigDto(version, 30, 60, 30, 24);
    }

    /** CONTRACT-V1 §4: PUT /api/v1/system/config kiểm tra version (409) và toàn bộ 4 tham số cố định v1 (400). */
    @PutMapping("/system/config")
    public ResponseEntity<SystemConfigDto> updateSystemConfig(@RequestBody Map<String, Object> body) {
        ActorContext admin = requireAdmin();
        if (!admin.permissions().contains("system.manage")) {
            throw new AccessDeniedException("Yêu cầu quyền system.manage.");
        }
        if (body == null || body.get("version") == null) {
            throw new IllegalArgumentException("PUT /system/config bắt buộc truyền version và đủ 4 tham số v1.");
        }
        int dup = parseIntField(body, "duplicateScanWindowSeconds");
        int qrTtl = parseIntField(body, "qrTtlSeconds");
        int revPoll = parseIntField(body, "revocationPollSeconds");
        int offlineHrs = parseIntField(body, "offlineTargetHours");
        if (dup != 30 || qrTtl != 60 || revPoll != 30 || offlineHrs != 24) {
            throw new IllegalArgumentException(
                    "V1 cố định duplicateScanWindowSeconds=30, qrTtlSeconds=60, revocationPollSeconds=30, offlineTargetHours=24.");
        }
        int nextVersion = checkAndBumpVersion("system:config", body.get("version"));
        return ResponseEntity.ok()
                .header("X-Resource-Version", String.valueOf(nextVersion))
                .body(new SystemConfigDto(nextVersion, 30, 60, 30, 24));
    }

    private static int parseIntField(Map<String, Object> body, String field) {
        Object v = body.get(field);
        if (v == null) throw new IllegalArgumentException("Thiếu trường bắt buộc: " + field);
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Trường " + field + " phải là số nguyên.");
        }
    }

    /** CONTRACT-V1 §4: GET /api/v1/operations/edges?factoryId=&page=&size= trả Page<EdgeHealth>, đánh OFFLINE nếu heartbeat > 90s. */
    @GetMapping("/operations/edges")
    public Map<String, Object> listOperationEdges(
            @RequestParam(value = "factoryId", required = false) String factoryId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        requireAdminOrAuditor();
        Instant now = Instant.now();
        List<Map<String, Object>> all = administration.getDeviceStatuses(factoryId).stream()
                .map(d -> {
                    Instant lastSeen = d.lastHeartbeatAt();
                    String mode = lastSeen == null ? "UNKNOWN"
                            : (lastSeen.isBefore(now.minusSeconds(90)) ? "OFFLINE" : "ONLINE");
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("edgeId", d.deviceId());
                    item.put("factoryId", d.factoryId());
                    item.put("lastSeenAt", lastSeen);
                    item.put("pendingCount", 0);
                    item.put("failedCount", 0);
                    item.put("diskFreeBytes", 0L);
                    item.put("cacheRevision", 0);
                    item.put("mode", mode);
                    return item;
                })
                .toList();
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        int fromIdx = Math.min(safePage * safeSize, all.size());
        int toIdx = Math.min(fromIdx + safeSize, all.size());
        return Map.of("items", all.subList(fromIdx, toIdx), "page", safePage, "size", safeSize, "totalElements", all.size());
    }

    @PutMapping({"/admin/configurations/{key}", "/system/config/{key}"})
    public ResponseEntity<SystemConfigurationView> updateConfiguration(
            @PathVariable("key") String key,
            @RequestBody Map<String, Object> body
    ) {
        requireAdmin();
        String value = body == null || body.get("value") == null ? null : String.valueOf(body.get("value"));
        if (IMMUTABLE_CONTRACT_CONFIGS.containsKey(key)
                && !IMMUTABLE_CONTRACT_CONFIGS.get(key).equals(value)) {
            throw new IllegalArgumentException(
                    "Tham số cố định theo CONTRACT-V1 (" + key + "=" + IMMUTABLE_CONTRACT_CONFIGS.get(key) + ") không được thay đổi.");
        }
        Object expectedVersion = body == null ? null : body.get("version");
        int nextVersion = checkAndBumpVersion("config:" + key, expectedVersion);
        SystemConfigurationView updated = administration.updateConfiguration(key, value);
        return ResponseEntity.ok()
                .header("X-Resource-Version", String.valueOf(nextVersion))
                .body(updated);
    }

    /**
     * CONTRACT-V1 §3 & §4 / FE-ADMIN-08: POST /api/v1/operations/jobs yêu cầu permission system.manage
     * (không phải AUDITOR), scope hợp lệ với factoryId (GLOBAL hoặc FACTORY chứa factoryId),
     * và re-auth gắn phiên trong 5 phút (AUTH-07). Validate type (BACKUP|RESTORE|ARCHIVE|ANONYMIZE),
     * RESTORE cần backupId UUIDv4, ANONYMIZE cần employeeId UUIDv4; không nhận shell command/file path.
     */
    @PostMapping("/operations/jobs")
    public ResponseEntity<?> triggerSensitiveOperationJob(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> body
    ) {
        ActorContext actor = requireActor();
        if (actor.isAuditor() || !actor.permissions().contains("system.manage")) {
            throw new AccessDeniedException("Tác vụ vận hành nhạy cảm yêu cầu quyền system.manage (AUDITOR bị cấm).");
        }
        String factoryId = body == null || body.get("factoryId") == null ? null : String.valueOf(body.get("factoryId")).trim();
        if (!ActorContext.SCOPE_GLOBAL.equals(actor.scope())) {
            boolean inFactoryScope = ActorContext.SCOPE_FACTORY.equals(actor.scope())
                    && factoryId != null && !factoryId.isBlank()
                    && actor.factoryIds().contains(factoryId);
            if (!inFactoryScope) {
                throw new AccessDeniedException("Không có quyền chạy tác vụ vận hành ngoài phạm vi nhà máy được gán.");
            }
        }
        if (authentication == null || !authentication.isSessionReauthenticated(authHeader)) {
            String requestId = UUID.randomUUID().toString();
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .header("X-Request-Id", requestId)
                    .header("Cache-Control", "no-store")
                    .body(new ApiError("REAUTH_REQUIRED", "Cần xác thực lại mật khẩu (/api/v1/auth/reauth) trong vòng 5 phút trước khi chạy tác vụ nhạy cảm.", requestId, false));
        }
        if (body == null) {
            throw new IllegalArgumentException("Thiếu body yêu cầu tác vụ vận hành.");
        }
        String rawType = body.get("type") != null ? String.valueOf(body.get("type"))
                : (body.get("jobType") != null ? String.valueOf(body.get("jobType")) : "");
        String jobType = rawType.trim();
        if (!ALLOWED_OPERATION_JOBS.contains(jobType)) {
            throw new IllegalArgumentException("type không hợp lệ. Chỉ chấp nhận: " + ALLOWED_OPERATION_JOBS);
        }
        if ("RESTORE".equals(jobType)) {
            String backupId = body.get("backupId") == null ? "" : String.valueOf(body.get("backupId")).trim();
            if (!UUID4_PATTERN.matcher(backupId).matches()) {
                throw new IllegalArgumentException("RESTORE bắt buộc backupId là UUIDv4.");
            }
        }
        if ("ANONYMIZE".equals(jobType)) {
            String employeeId = body.get("employeeId") == null ? "" : String.valueOf(body.get("employeeId")).trim();
            if (!UUID4_PATTERN.matcher(employeeId).matches()) {
                throw new IllegalArgumentException("ANONYMIZE bắt buộc employeeId là UUIDv4.");
            }
        }
        OperationJobDto job = new OperationJobDto(
                UUID.randomUUID().toString(), jobType, "QUEUED", Instant.now(), null, null);
        operationJobs.put(job.id(), job);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping("/operations/jobs/{id}")
    public ResponseEntity<?> getOperationJob(@PathVariable("id") String id) {
        requireAdminOrAuditor();
        OperationJobDto job = operationJobs.get(id);
        if (job == null) {
            String requestId = UUID.randomUUID().toString();
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Không tìm thấy tác vụ vận hành: " + id, requestId, false));
        }
        return ResponseEntity.ok(job);
    }

    @GetMapping("/admin/factories")
    public List<FactoryView> listFactories() {
        requireAdminOrAuditor();
        return administration.findAllFactories();
    }

    @GetMapping("/admin/devices")
    public List<DeviceStatusView> listDevices(@RequestParam(value = "factoryId", required = false) String factoryId) {
        requireAdminOrAuditor();
        return administration.getDeviceStatuses(factoryId);
    }

    @GetMapping("/admin/dsr")
    public List<DataSubjectRequestView> listDsr() {
        requireAdminOrAuditor();
        return administration.findAllDataSubjectRequests();
    }

    @PostMapping("/admin/dsr")
    public ResponseEntity<DataSubjectRequestView> submitDsr(@RequestBody Map<String, String> body) {
        requireAdmin();
        if (body == null) throw new IllegalArgumentException("Thiếu dữ liệu yêu cầu DSR.");
        DataSubjectRequestView created = administration.submitDataSubjectRequest(
                body.get("employeeId"),
                body.get("requestType"),
                body.get("reason")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/admin/dsr/{requestId}")
    public DataSubjectRequestView processDsr(
            @PathVariable("requestId") String requestId,
            @RequestBody Map<String, String> body
    ) {
        ActorContext admin = requireAdmin();
        if (body == null) throw new IllegalArgumentException("Thiếu trạng thái xử lý DSR.");
        return administration.processDataSubjectRequest(
                requestId,
                body.get("status"),
                admin.userId(),
                body.getOrDefault("notes", "")
        );
    }

    /** CONTRACT-V1 §4: POST /api/v1/privacy/requests → 201 PrivacyRequest (userId lấy từ phiên). */
    @PostMapping("/privacy/requests")
    public ResponseEntity<PrivacyRequestDto> createPrivacyRequest(@RequestBody Map<String, String> body) {
        ActorContext actor = requireActor();
        if (actor.isAuditor()) {
            throw new AccessDeniedException("AUDITOR không được tạo yêu cầu DSR.");
        }
        if (body == null) throw new IllegalArgumentException("Thiếu dữ liệu yêu cầu quyền riêng tư.");
        String type = body.get("type") == null ? "" : body.get("type").trim().toUpperCase();
        String reason = body.get("reason") == null ? "" : body.get("reason").trim();
        if (!ALLOWED_PRIVACY_TYPES.contains(type)) {
            throw new IllegalArgumentException("type phải thuộc ACCESS | RECTIFY | ANONYMIZE.");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason bắt buộc khác rỗng.");
        }
        PrivacyRequestDto dto = new PrivacyRequestDto(
                UUID.randomUUID().toString(), actor.userId(), type, reason, "SUBMITTED", Instant.now(), null, 1);
        privacyRequests.put(dto.id(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /** CONTRACT-V1 §4: GET /api/v1/privacy/requests → Page<PrivacyRequest> theo SELF hoặc ADMIN/AUDITOR. */
    @GetMapping("/privacy/requests")
    public Map<String, Object> listPrivacyRequests(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        ActorContext actor = requireActor();
        boolean canSeeAll = actor.roles().contains(Role.ADMIN) || actor.roles().contains(Role.AUDITOR);
        List<PrivacyRequestDto> filtered = privacyRequests.values().stream()
                .filter(r -> canSeeAll || actor.userId().equals(r.userId()))
                .sorted(java.util.Comparator.comparing(PrivacyRequestDto::id))
                .toList();
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        int fromIdx = Math.min(safePage * safeSize, filtered.size());
        int toIdx = Math.min(fromIdx + safeSize, filtered.size());
        return Map.of("items", filtered.subList(fromIdx, toIdx), "page", safePage, "size", safeSize, "totalElements", filtered.size());
    }

    /** CONTRACT-V1 §4: PUT /api/v1/privacy/requests/{id}/decision với optimistic version (409) & system.manage. */
    @PutMapping("/privacy/requests/{id}/decision")
    public ResponseEntity<?> decidePrivacyRequest(
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> body
    ) {
        ActorContext admin = requireAdmin();
        if (!admin.permissions().contains("system.manage")) {
            throw new AccessDeniedException("Yêu cầu quyền system.manage.");
        }
        PrivacyRequestDto existing = privacyRequests.get(id);
        if (existing == null) {
            String requestId = UUID.randomUUID().toString();
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Không tìm thấy yêu cầu DSR: " + id, requestId, false));
        }
        if (!"SUBMITTED".equals(existing.state())) {
            String requestId = UUID.randomUUID().toString();
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("INVALID_STATE", "Yêu cầu DSR đã được xử lý trước đó.", requestId, false));
        }
        int expectedVersion = parseIntField(body, "version");
        if (expectedVersion != existing.version()) {
            throw new OptimisticConflictException("Xung đột phiên bản privacy request " + id);
        }
        String decision = body.get("decision") == null ? "" : String.valueOf(body.get("decision")).trim().toUpperCase();
        String reason = body.get("reason") == null ? "" : String.valueOf(body.get("reason")).trim();
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            throw new IllegalArgumentException("decision phải là APPROVE hoặc REJECT.");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason bắt buộc khác rỗng.");
        }
        String nextState = "APPROVE".equals(decision) ? "APPROVED" : "REJECTED";
        PrivacyRequestDto updated = new PrivacyRequestDto(
                existing.id(), existing.userId(), existing.type(), existing.reason(),
                nextState, existing.createdAt(), reason, existing.version() + 1);
        privacyRequests.put(id, updated);
        return ResponseEntity.ok(updated);
    }
}
