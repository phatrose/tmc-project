package vn.tmc.edge.gatestation.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2. Danh tính kiosk đã xác thực bằng mTLS (CONTRACT-V1.md §10): kioskId, edgeId, factoryId và
 * các gate kiosk được phép điều khiển. Do adapter tạo từ chứng chỉ, tuyệt đối không lấy từ body request.
 */
public record KioskContext(String kioskId, UUID edgeId, UUID factoryId, Set<UUID> gateIds) {
    public KioskContext {
        Objects.requireNonNull(kioskId, "kioskId");
        Objects.requireNonNull(edgeId, "edgeId");
        Objects.requireNonNull(factoryId, "factoryId");
        gateIds = Set.copyOf(gateIds);
    }

    public boolean mayOperate(UUID gateId) {
        return gateIds.contains(gateId);
    }
}
