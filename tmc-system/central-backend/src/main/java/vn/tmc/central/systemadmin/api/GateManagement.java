package vn.tmc.central.systemadmin.api;

import java.util.List;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Public API Port cho module Gate Management.
 * NGUỒN: main.pdf, FR-GS-01..06; §5.1; DECISIONS.md §2.
 * TÀI LIỆU PDF: docs/tasks/03-modules-solid.md; docs/REQUIREMENTS.md.
 */
public interface GateManagement {
    GateView findGate(String gateId);
    List<GateView> findGatesByFactory(String factoryId);
    List<GateView> findAllGates();
    GateView registerGate(String id, String factoryId, String edgeId, String name, String direction, String turnstileApiId);
    GateView updateGateStatus(String gateId, String newStatus);
    void recordGateHeartbeat(String gateId, String ipAddress);
}
