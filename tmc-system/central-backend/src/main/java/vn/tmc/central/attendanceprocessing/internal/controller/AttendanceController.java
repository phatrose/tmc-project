package vn.tmc.central.attendanceprocessing.internal.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.tmc.central.attendanceprocessing.api.Adjustment;
import vn.tmc.central.attendanceprocessing.api.AdjustmentDecision;
import vn.tmc.central.attendanceprocessing.api.AdjustmentRequest;
import vn.tmc.central.attendanceprocessing.api.AdjustmentRequestCommand;
import vn.tmc.central.attendanceprocessing.api.AdjustmentReviewCommand;
import vn.tmc.central.attendanceprocessing.api.AttendanceAdjustmentPort;
import vn.tmc.central.attendanceprocessing.api.AttendanceAdjustmentView;
import vn.tmc.central.attendanceprocessing.api.AttendanceEventView;
import vn.tmc.central.attendanceprocessing.api.AttendanceFilter;
import vn.tmc.central.attendanceprocessing.api.AttendanceQueries;
import vn.tmc.central.attendanceprocessing.api.AttendanceView;
import vn.tmc.central.attendanceprocessing.api.DailyAttendance;
import vn.tmc.central.attendanceprocessing.api.MonthlyAttendanceSummaryView;
import vn.tmc.central.attendanceprocessing.api.PresenceView;
import vn.tmc.central.attendanceprocessing.internal.service.IdempotencyConflictException;
import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.contracts.Page;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: Controller REST API chấm công theo CONTRACT-V1.md §9.
 */
@RestController
@org.springframework.context.annotation.Profile("!platform")
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(AttendanceQueries.class)
@RequestMapping("/api/v1/attendance")
public final class AttendanceController {

    private record CachedAdjustment(String payloadHash, Adjustment adjustment, Instant expiresAt) {}

    private final AttendanceQueries queryService;
    private final AttendanceAdjustmentPort adjustmentService;
    private final Map<String, CachedAdjustment> adjustmentIdempotency = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public AttendanceController(AttendanceQueries queryService, AttendanceAdjustmentPort adjustmentService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
        this.adjustmentService = adjustmentService;
    }

    public AttendanceController(AttendanceQueries queryService) {
        this(queryService, queryService instanceof AttendanceAdjustmentPort a ? a : null);
    }

    // PHỤ TRÁCH: Task 8. Use the verified session, never a fabricated HR actor.
    private ActorContext resolveActor() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof ActorContext actor)) {
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Session required");
        }
        return actor;
    }

    /**
     * GET /api/v1/attendance/events: Nhật ký sự kiện quẹt thẻ bất biến (Page<AttendanceEventView>).
     */
    @GetMapping("/events")
    public ResponseEntity<Page<AttendanceEventView>> queryEvents(
            @RequestParam(value = "employeeId", required = false) UUID employeeId,
            @RequestParam(value = "factoryId", required = false) UUID factoryId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        ActorContext context = resolveActor();
        Page<AttendanceEventView> result = queryService.queryEvents(context, employeeId, factoryId, from, to, page, size);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/attendance/records: Bảng công ngày (Page<DailyAttendance>) theo khoảng [from, to).
     */
    @GetMapping(value = {"/records", "/daily"})
    public ResponseEntity<Page<DailyAttendance>> queryRecords(
            @RequestParam(value = "employeeId", required = false) UUID employeeId,
            @RequestParam(value = "factoryId", required = false) UUID factoryId,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        ActorContext context = resolveActor();
        AttendanceFilter filter = new AttendanceFilter(factoryId, employeeId, from, to, page, size);
        Page<DailyAttendance> result = queryService.dailyRecords(context, filter);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/attendance/presence: Danh sách hiện diện phục vụ an ninh/sơ tán.
     */
    @GetMapping("/presence")
    public ResponseEntity<PresenceView> getPresence(
            @RequestParam("factoryId") UUID factoryId
    ) {
        ActorContext context = resolveActor();
        if (!ActorContext.SCOPE_GLOBAL.equals(context.scope())
                && !context.factoryIds().isEmpty()
                && !context.factoryIds().contains(factoryId.toString())) {
            throw new SecurityException("Không có quyền xem hiện diện của nhà máy ngoài phạm vi được gán.");
        }
        PresenceView presence = queryService.getPresence(factoryId);
        return ResponseEntity.ok(presence);
    }

    /**
     * POST /api/v1/attendance/adjustments: Gửi yêu cầu điều chỉnh công (CONTRACT-V1 §9).
     */
    @PostMapping("/adjustments")
    public ResponseEntity<Adjustment> createAdjustment(
            @RequestBody AdjustmentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        ActorContext context = resolveActor();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Header Idempotency-Key bắt buộc.");
        }
        String cacheKey = context.userId() + "|POST:/api/v1/attendance/adjustments|" + idempotencyKey.trim();
        String hash = sha256(String.valueOf(request));
        Instant now = Instant.now();
        CachedAdjustment existing = adjustmentIdempotency.get(cacheKey);
        if (existing != null && now.isBefore(existing.expiresAt())) {
            if (!existing.payloadHash().equals(hash)) {
                throw new IdempotencyConflictException("Idempotency-Key đã được dùng với nội dung điều chỉnh khác.");
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(existing.adjustment());
        }
        Adjustment created = queryService.createAdjustment(context, request);
        adjustmentIdempotency.put(cacheKey, new CachedAdjustment(hash, created, now.plus(48, ChronoUnit.HOURS)));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/v1/attendance/adjustments: Danh sách yêu cầu điều chỉnh công (Page<Adjustment>).
     */
    @GetMapping("/adjustments")
    public ResponseEntity<Page<Adjustment>> queryAdjustments(
            @RequestParam(value = "employeeId", required = false) UUID employeeId,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        ActorContext context = resolveActor();
        Page<Adjustment> result = queryService.queryAdjustments(context, employeeId, state, page, size);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/v1/attendance/adjustments/{id}/decision: Phê duyệt hoặc từ chối điều chỉnh (CONTRACT-V1 §9).
     */
    @PostMapping("/adjustments/{id}/decision")
    public ResponseEntity<Adjustment> decideAdjustment(
            @PathVariable("id") UUID id,
            @RequestBody AdjustmentDecision decision
    ) {
        ActorContext context = resolveActor();
        Adjustment updated = queryService.decideAdjustment(context, id, decision);
        return ResponseEntity.ok(updated);
    }

    // --- Các endpoint bổ trợ và tương thích ngược ---

    @GetMapping("/view/{employeeId}")
    public ResponseEntity<AttendanceView> findAttendance(@PathVariable("employeeId") String employeeId) {
        ActorContext context = resolveActor();
        if (ActorContext.SCOPE_SELF.equals(context.scope()) && !employeeId.equals(context.employeeId())) {
            throw new SecurityException("Tài khoản SELF chỉ được xem dữ liệu của chính mình.");
        }
        AttendanceView view = queryService.findAttendance(employeeId);
        return ResponseEntity.ok(view);
    }

    @GetMapping("/summary")
    public ResponseEntity<MonthlyAttendanceSummaryView> getMonthlySummary(
            @RequestParam("employeeId") String employeeId,
            @RequestParam("period") String periodStr,
            @RequestParam(value = "factoryId", defaultValue = "F01") String factoryId
    ) {
        ActorContext context = resolveActor();
        if (ActorContext.SCOPE_SELF.equals(context.scope()) && !employeeId.equals(context.employeeId())) {
            throw new SecurityException("Tài khoản SELF chỉ được xem tổng hợp công của chính mình.");
        }
        YearMonth period = YearMonth.parse(periodStr);
        MonthlyAttendanceSummaryView summary = queryService.getMonthlySummary(employeeId, period, factoryId);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/adjustments/legacy-request")
    public ResponseEntity<AttendanceAdjustmentView> requestAdjustmentLegacy(@RequestBody AdjustmentRequestCommand command) {
        resolveActor();
        if (adjustmentService == null) return ResponseEntity.badRequest().build();
        AttendanceAdjustmentView view = adjustmentService.requestAdjustment(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @PostMapping("/adjustments/legacy-review")
    public ResponseEntity<AttendanceAdjustmentView> reviewAdjustmentLegacy(@RequestBody AdjustmentReviewCommand command) {
        resolveActor();
        if (adjustmentService == null) return ResponseEntity.badRequest().build();
        AttendanceAdjustmentView view = adjustmentService.reviewAdjustment(command);
        return ResponseEntity.ok(view);
    }

    private static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
