package vn.tmc.central.reportingaudit.internal.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.tmc.central.reportingaudit.api.ReportingSources.AttendanceFilter;
import vn.tmc.central.reportingaudit.api.ReportingSources.DailyAttendance;
import vn.tmc.central.reportingaudit.api.*;
import vn.tmc.central.reportingaudit.internal.model.ExportJob;
import vn.tmc.central.reportingaudit.internal.service.*;
import vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext;
import vn.tmc.central.reportingaudit.api.ReportingSources.SessionQuery;
import vn.tmc.central.usermgmt.api.Role;
import vn.tmc.central.reportingaudit.api.ReportingSources.Page;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import vn.tmc.central.reportingaudit.internal.controller.ReportingHttp;

@RestController
@RequestMapping("/api/v1")
@Profile("!platform")
public final class ReportAuditController {
    public record AttendanceExportRequest(String factoryId, String period, ReportExport.Format format) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String key,Object value){throw new IllegalArgumentException("Unknown field: "+key);}
    }
    public record AuditExportRequest(String factoryId, String from, String to, ReportExport.Format format) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String key,Object value){throw new IllegalArgumentException("Unknown field: "+key);}
    }
    public record CloseRequest(String factoryId, String reason) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String key,Object value){throw new IllegalArgumentException("Unknown field: "+key);}
    }
    public record ReopenRequest(String factoryId, String reason, Integer version) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String key,Object value){throw new IllegalArgumentException("Unknown field: "+key);}
    }

    private final ObjectProvider<SessionQuery> authentication;
    private final ReportingQueries reports;
    private final ReportExportService exports;
    private final AuditQueryService audit;
    private final ReportingAccess access;
    private final PayrollExportService payroll;
    private final DashboardService dashboard;

    public ReportAuditController(ObjectProvider<SessionQuery> authentication, ReportingQueries reports,
            ReportExportService exports, AuditQueryService audit, ReportingAccess access,PayrollExportService payroll,DashboardService dashboard) {
        this.authentication = authentication;
        this.reports = reports;
        this.exports = exports;
        this.audit = audit;
        this.access = access;
        this.payroll=payroll;this.dashboard=dashboard;
    }

    @GetMapping("/reports/attendance")
    public Page<DailyAttendance> attendance(@RequestHeader(value="Authorization", required=false) String auth,
            @RequestParam String factoryId, @RequestParam String from, @RequestParam String to,
            @RequestParam(required=false) String employeeId, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        ActorContext actor = actor(auth);
        paging(page, size);
        return reports.dailyRecords(actor, new AttendanceFilter(id(factoryId), employeeId == null ? null : id(employeeId),
                date(from), date(to), page, size));
    }

    @GetMapping("/reports/dashboard")
    public Dashboard dashboard(@RequestHeader(value="Authorization", required=false) String auth,
            @RequestParam String factoryId, @RequestParam String date) {
        var actor = actor(auth);
        access.require(actor, "report.read");
        access.factory(actor, id(factoryId));
        return dashboard.dashboard(actor,id(factoryId),date(date));
    }

    @PostMapping("/reports/exports")
    public ResponseEntity<ReportExport> exportAttendance(@RequestHeader(value="Authorization", required=false) String auth,
            @RequestHeader(value="Idempotency-Key", required=false) String key, @RequestBody AttendanceExportRequest request) {
        var actor = actor(auth);
        format(request.format());
        return ResponseEntity.accepted().body(exports.attendance(actor, id(request.factoryId()), request.period(), request.format(), id(key)));
    }

    @GetMapping("/reports/exports/{id}")
    public ReportExport reportStatus(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable String id) {
        return exports.status(actor(auth), id(id), ExportJob.Kind.ATTENDANCE);
    }

    @GetMapping("/reports/exports/{id}/file")
    public ResponseEntity<byte[]> reportFile(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable String id) {
        return download(actor(auth), id(id), ExportJob.Kind.ATTENDANCE);
    }

    @GetMapping("/audit/events")
    public Page<AuditEntry> auditEvents(@RequestHeader(value="Authorization", required=false) String auth,
            @RequestParam(required=false) String actorId, @RequestParam(required=false) String action,
            @RequestParam(required=false) String from, @RequestParam(required=false) String to,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        var actor = actor(auth);
        paging(page, size);
        return audit.events(actor, actorId == null ? null : id(actorId), action,
                from == null ? null : instant(from), to == null ? null : instant(to), page, size);
    }

    @PostMapping("/audit/exports")
    public ResponseEntity<ReportExport> exportAudit(@RequestHeader(value="Authorization", required=false) String auth,
            @RequestHeader(value="Idempotency-Key", required=false) String key, @RequestBody AuditExportRequest request) {
        var actor = actor(auth);
        format(request.format());
        return ResponseEntity.accepted().body(exports.audit(actor, id(request.factoryId()), instant(request.from()),
                instant(request.to()), request.format(), id(key)));
    }

    @GetMapping("/audit/exports/{id}")
    public ReportExport auditStatus(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable String id) {
        return exports.status(actor(auth), id(id), ExportJob.Kind.AUDIT);
    }

    @GetMapping("/audit/exports/{id}/file")
    public ResponseEntity<byte[]> auditFile(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable String id) {
        return download(actor(auth), id(id), ExportJob.Kind.AUDIT);
    }

    @PostMapping("/payroll/periods/{period}/close")
    public ResponseEntity<PayrollBatch> close(@RequestHeader(value="Authorization", required=false) String auth,
            @RequestHeader(value="Idempotency-Key", required=false) String key, @PathVariable String period,
            @RequestBody CloseRequest request,HttpServletRequest servlet) {
        return ResponseEntity.accepted().body(payroll.close(actor(auth),id(request.factoryId()),ReportExportService.validatePeriod(period),
                request.reason(),id(key),UUID.fromString(ReportingHttp.requestId(servlet))));
    }

    @PostMapping("/payroll/periods/{period}/reopen")
    public Period reopen(@RequestHeader(value="Authorization", required=false) String auth,
            @RequestHeader(value="Idempotency-Key", required=false) String key, @PathVariable String period,
            @RequestBody ReopenRequest request,HttpServletRequest servlet) {
        if (request.version() == null || request.version() < 1) throw new IllegalArgumentException("version phải từ 1");
        return payroll.reopen(actor(auth),id(request.factoryId()),ReportExportService.validatePeriod(period),request.reason(),request.version(),id(key),
                UUID.fromString(ReportingHttp.requestId(servlet)));
    }

    @GetMapping("/payroll/batches/{id}")
    public PayrollBatch batch(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable String id) {
        return payroll.batch(actor(auth),id(id));
    }

    @GetMapping("/payroll/periods/{period}/history")
    public Page<PeriodHistory> history(@RequestHeader(value="Authorization", required=false) String auth,
            @PathVariable String period, @RequestParam String factoryId,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        paging(page, size);
        return payroll.history(actor(auth),id(factoryId),ReportExportService.validatePeriod(period),page,size);
    }

    private ResponseEntity<byte[]> download(ActorContext actor, UUID id, ExportJob.Kind kind) {
        var file = exports.file(actor, id, kind);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mediaType()))
                .header("Content-Disposition", ContentDisposition.attachment().filename("tmc-" + id + "." + file.extension()).build().toString())
                .header("Cache-Control", "no-store").body(file.bytes());
    }

    private ActorContext actor(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7)
            throw new ReportingException("UNAUTHENTICATED", "Cần phiên Bearer hợp lệ", 401);
        var sessions = authentication.getIfAvailable();
        if (sessions == null) throw ReportingException.unavailable("SessionQuery");
        try {
            var actor = sessions.getActorContext(header);
            if (actor == null) throw new IllegalStateException();
            id(actor.userId());
            return actor;
        } catch (ReportingException failure) { throw failure; }
        catch (RuntimeException invalid) { throw new ReportingException("UNAUTHENTICATED", "Phiên không hợp lệ hoặc đã hết hạn", 401); }
    }

    private static UUID id(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("Định danh UUIDv4 chữ thường bắt buộc");
        return UUID.fromString(value);
    }

    private static LocalDate date(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("Ngày phải có dạng YYYY-MM-DD");
        return LocalDate.parse(value);
    }

    private static Instant instant(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z"))
            throw new IllegalArgumentException("Thời gian phải là UTC đúng milliseconds");
        return Instant.parse(value);
    }

    private static void paging(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page từ 0, size từ 1 đến 100");
    }
    private static void format(ReportExport.Format format) {
        if (format == null) throw new IllegalArgumentException("format bắt buộc");
    }
    private static void reason(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason bắt buộc");
    }
}
