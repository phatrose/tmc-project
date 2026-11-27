package vn.tmc.central.systemadmin.internal.controller;

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

/**
 * PHỤ TRÁCH: Task 5 / Task 8. REST API cho Quản trị hệ thống & Cổng trong profile integration-preview.
 */
@RestController
@Profile("integration-preview")
@RequestMapping("/api/v1/admin")
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

    private ActorContext requireNonAuditor() {
        ActorContext actor = requireActor();
        if (actor.isAuditor()) {
            throw new AccessDeniedException("Auditor không được phép thay đổi cấu hình hệ thống hoặc cổng.");
        }
        return actor;
    }

    @GetMapping("/gates")
    public List<GateView> listGates(@RequestParam(value = "factoryId", required = false) String factoryId) {
        requireActor();
        return (factoryId == null || factoryId.isBlank() || "ALL".equalsIgnoreCase(factoryId))
                ? gateManagement.findAllGates()
                : gateManagement.findGatesByFactory(factoryId);
    }

    @PutMapping("/gates/{gateId}/status")
    public GateView updateGateStatus(@PathVariable("gateId") String gateId, @RequestBody Map<String, String> body) {
        requireNonAuditor();
        String status = body == null ? null : body.get("status");
        return gateManagement.updateGateStatus(gateId, status);
    }

    @GetMapping("/configurations")
    public List<SystemConfigurationView> listConfigurations() {
        requireActor();
        return administration.findAllConfigurations();
    }

    @PutMapping("/configurations/{key}")
    public SystemConfigurationView updateConfiguration(@PathVariable("key") String key, @RequestBody Map<String, String> body) {
        requireNonAuditor();
        String value = body == null ? null : body.get("value");
        return administration.updateConfiguration(key, value);
    }

    @GetMapping("/factories")
    public List<FactoryView> listFactories() {
        requireActor();
        return administration.findAllFactories();
    }

    @GetMapping("/devices")
    public List<DeviceStatusView> listDevices(@RequestParam(value = "factoryId", required = false) String factoryId) {
        requireActor();
        return administration.getDeviceStatuses(factoryId);
    }

    @GetMapping("/dsr")
    public List<DataSubjectRequestView> listDsr() {
        requireActor();
        return administration.findAllDataSubjectRequests();
    }

    @PostMapping("/dsr")
    public ResponseEntity<DataSubjectRequestView> submitDsr(@RequestBody Map<String, String> body) {
        requireNonAuditor();
        DataSubjectRequestView created = administration.submitDataSubjectRequest(
                body.get("employeeId"),
                body.get("requestType"),
                body.get("reason")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
