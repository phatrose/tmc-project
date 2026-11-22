package vn.tmc.central.systemadmin.internal.controller;

import vn.tmc.central.systemadmin.api.GateManagement;
import vn.tmc.central.systemadmin.api.GateView;

import java.util.List;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Controller tiếp nhận và xử lý yêu cầu cho Gate Management.
 * NGUỒN: main.pdf, FR-GS-01..06; §4.2.1; §5.1.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 */
public final class GateController {
    private final GateManagement service;

    public GateController(GateManagement service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    // GET /api/v1/gates/{gateId}
    public GateView findGate(String gateId) {
        return service.findGate(gateId);
    }

    // GET /api/v1/gates
    public List<GateView> findAllGates() {
        return service.findAllGates();
    }

    // GET /api/v1/gates/factory/{factoryId}
    public List<GateView> findGatesByFactory(String factoryId) {
        return service.findGatesByFactory(factoryId);
    }

    // POST /api/v1/gates
    public GateView registerGate(String id, String factoryId, String edgeId, String name, String direction, String turnstileApiId) {
        return service.registerGate(id, factoryId, edgeId, name, direction, turnstileApiId);
    }

    // PUT /api/v1/gates/{gateId}/status
    public GateView updateGateStatus(String gateId, String status) {
        return service.updateGateStatus(gateId, status);
    }

    // POST /api/v1/gates/{gateId}/heartbeat
    public void recordGateHeartbeat(String gateId, String ipAddress) {
        service.recordGateHeartbeat(gateId, ipAddress);
    }
}
