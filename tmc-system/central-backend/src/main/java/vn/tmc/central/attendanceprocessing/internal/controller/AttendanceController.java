package vn.tmc.central.attendanceprocessing.internal.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.tmc.central.attendanceprocessing.api.*;
import vn.tmc.central.attendanceprocessing.internal.service.AttendanceIdempotencyService;
import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.contracts.Page;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: Controller REST API chấm công theo CONTRACT-V1.md §9.
 * Persistence & khóa idempotency được tách xuống AttendanceIdempotencyService.
 */
@RestController
@org.springframework.context.annotation.Profile("!platform")
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(AttendanceQueries.class)
@RequestMapping("/api/v1/attendance")
public final class AttendanceController {

    private final AttendanceQueries queryService;
    private final AttendanceAdjustmentPort adjustmentService;
    private final AttendanceIdempotencyService idempotencyService;

    @Autowired
    public AttendanceController(
            AttendanceQueries queryService,
            @Autowired(required = false) AttendanceAdjustmentPort adjustmentService,
            @Autowired(required = false) AttendanceIdempotencyService idempotencyService
    ) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
        this.adjustmentService = adjustmentService;
        this.idempotencyService = idempotencyService != null
                ? idempotencyService
                : new AttendanceIdempotencyService(null, null, null);
    }

    public AttendanceController(AttendanceQueries queryService, AttendanceAdjustmentPort adjustmentService) {
        this(queryService, adjustmentService, null);
    }

    public AttendanceController(AttendanceQueries queryService) {
        this(queryService, queryService instanceof AttendanceAdjustmentPort a ? a : null, null);
    }

    // PHỤ TRÁCH: Task 8. Use the verified session, never a fabricated HR actor.
    private ActorContext resolveActor() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof ActorContext actor)) {
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Session required");
        }
        return actor;
    }

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
        return ResponseEntity.ok(queryService.queryEvents(context, employeeId, factoryId, from, to, page, size));
    }

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
        return ResponseEntity.ok(queryService.dailyRecords(context, filter));
    }

    @GetMapping("/presence")
    public ResponseEntity<PresenceView> getPresence(@RequestParam("factoryId") UUID factoryId) {
        ActorContext context = resolveActor();
        if (!ActorContext.SCOPE_GLOBAL.equals(context.scope())
                && (context.factoryIds().isEmpty() || !context.factoryIds().contains(factoryId.toString()))) {
            throw new SecurityException("Không có quyền xem hiện diện của nhà máy ngoài phạm vi được gán.");
        }
        return ResponseEntity.ok(queryService.getPresence(factoryId));
    }

    @PostMapping("/adjustments")
    public ResponseEntity<Adjustment> createAdjustment(
            @RequestBody AdjustmentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        ActorContext context = resolveActor();
        Adjustment created = idempotencyService.execute(
                context.userId(),
                "POST:/api/v1/attendance/adjustments",
                idempotencyKey,
                request,
                HttpStatus.CREATED.value(),
                Adjustment.class,
                () -> queryService.createAdjustment(context, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/adjustments")
    public ResponseEntity<Page<Adjustment>> queryAdjustments(
            @RequestParam(value = "employeeId", required = false) UUID employeeId,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        ActorContext context = resolveActor();
        return ResponseEntity.ok(queryService.queryAdjustments(context, employeeId, state, page, size));
    }

    public ResponseEntity<Adjustment> decideAdjustment(UUID id, AdjustmentDecision decision) {
        return decideAdjustment(id, decision, null);
    }

    @PostMapping("/adjustments/{id}/decision")
    public ResponseEntity<Adjustment> decideAdjustment(
            @PathVariable("id") UUID id,
            @RequestBody AdjustmentDecision decision,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        ActorContext context = resolveActor();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Adjustment updated = idempotencyService.execute(
                    context.userId(),
                    "POST:/api/v1/attendance/adjustments/" + id + "/decision",
                    idempotencyKey,
                    Map.of("id", id.toString(), "decision", decision),
                    HttpStatus.OK.value(),
                    Adjustment.class,
                    () -> queryService.decideAdjustment(context, id, decision));
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.ok(queryService.decideAdjustment(context, id, decision));
    }

    @GetMapping("/view/{employeeId}")
    public ResponseEntity<AttendanceView> findAttendance(@PathVariable("employeeId") String employeeId) {
        ActorContext context = resolveActor();
        enforceLegacyReadScope(context, employeeId, null);
        return ResponseEntity.ok(queryService.findAttendance(employeeId));
    }

    @GetMapping("/summary")
    public ResponseEntity<MonthlyAttendanceSummaryView> getMonthlySummary(
            @RequestParam("employeeId") String employeeId,
            @RequestParam("period") String periodStr,
            @RequestParam(value = "factoryId", defaultValue = "F01") String factoryId
    ) {
        ActorContext context = resolveActor();
        enforceLegacyReadScope(context, employeeId, factoryId);
        return ResponseEntity.ok(queryService.getMonthlySummary(employeeId, YearMonth.parse(periodStr), factoryId));
    }

    @PostMapping("/adjustments/legacy-request")
    public ResponseEntity<AttendanceAdjustmentView> requestAdjustmentLegacy(@RequestBody AdjustmentRequestCommand command) {
        resolveActor();
        if (adjustmentService == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.status(HttpStatus.CREATED).body(adjustmentService.requestAdjustment(command));
    }

    @PostMapping("/adjustments/legacy-review")
    public ResponseEntity<AttendanceAdjustmentView> reviewAdjustmentLegacy(@RequestBody AdjustmentReviewCommand command) {
        resolveActor();
        if (adjustmentService == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(adjustmentService.reviewAdjustment(command));
    }

    private static void enforceLegacyReadScope(ActorContext context, String employeeId, String factoryId) {
        if (context == null || context.scope() == null || context.scope().isBlank()) {
            throw new SecurityException("Thiếu ngữ cảnh phân quyền.");
        }
        if (ActorContext.SCOPE_GLOBAL.equals(context.scope())) {
            return;
        }
        if (ActorContext.SCOPE_SELF.equals(context.scope())) {
            if (context.employeeId() == null || !employeeId.equals(context.employeeId())) {
                throw new SecurityException("Tài khoản SELF chỉ được xem dữ liệu của chính mình.");
            }
            return;
        }
        if (ActorContext.SCOPE_FACTORY.equals(context.scope())) {
            if (context.factoryIds().isEmpty()
                    || (factoryId != null && !context.factoryIds().contains(factoryId))) {
                throw new SecurityException("Không có quyền xem dữ liệu nhà máy ngoài phạm vi được gán.");
            }
            return;
        }
        if (ActorContext.SCOPE_DEPARTMENT.equals(context.scope())) {
            if (context.departmentIds().isEmpty()) {
                throw new SecurityException("Tài khoản DEPARTMENT chưa được gán phòng ban.");
            }
            return;
        }
        throw new SecurityException("Phạm vi phân quyền không hợp lệ.");
    }
}
