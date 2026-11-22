package vn.tmc.central.systemadmin.api;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Public View Record cho Gate.
 * NGUỒN: main.pdf, FR-GS-01; §5.1.
 */
public record GateView(
    String id,
    String factoryId,
    String edgeId,
    String name,
    String direction,
    String turnstileApiId,
    String status
) {
    public GateView(String id, String factoryId, String direction) {
        this(id, factoryId, null, id, direction, null, "ACTIVE");
    }
}
