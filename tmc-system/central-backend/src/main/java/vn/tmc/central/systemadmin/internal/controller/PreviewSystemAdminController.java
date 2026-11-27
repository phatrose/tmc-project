package vn.tmc.central.systemadmin.internal.controller;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vn.tmc.central.systemadmin.api.*;
import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.central.usermgmt.api.Role;

/**
 * PHỤ TRÁCH: Task 5 / Task 8. REST API Quản trị hệ thống & Cổng trong profile integration-preview
 * với kiểm tra RBAC rõ ràng (ADMIN / AUDITOR / GUARD / HR).
 */
@RestController
@Profile("integration-preview")
@RequestMapping("/api/v1")
public final class PreviewSystemAdminController {

    private final Administration administration;
    private final GateManagement gateManagement;

    public PreviewSystemAdminController(Administration administration, GateManagement gateManagement) {
        this.administration = Objects.requireNonNull(administration, "administration required");
        this.gateManagement = Objects.requireNonNull(gateManagement, "gateManagement required");
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
    public GateView updateGateStatus(@PathVariable("gateId") String gateId, @RequestBody Map<String, String> body) {
        requireAdmin();
        String status = body == null ? null : body.get("status");
        return gateManagement.updateGateStatus(gateId, status);
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

    @PutMapping("/admin/configurations/{key}")
    public SystemConfigurationView updateConfiguration(@PathVariable("key") String key, @RequestBody Map<String, String> body) {
        requireAdmin();
        String value = body == null ? null : body.get("value");
        return administration.updateConfiguration(key, value);
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
}
