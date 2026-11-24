package vn.tmc.edge.gatestation.infrastructure;

import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.edge.edgesync.domain.BootstrapCache;
import vn.tmc.edge.edgesync.domain.BootstrapCache.Snapshot;
import vn.tmc.edge.gatestation.domain.AccessPolicyPort;
import vn.tmc.edge.gatestation.domain.GateCatalogPort;
import vn.tmc.edge.gatestation.domain.GateInfo;
import vn.tmc.edge.gatestation.domain.PolicyDecision;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2. Adapter chính sách + cấu hình cổng đọc từ BootstrapCache của Task 7 (snapshot bất biến đã được
 * xác thực và commit). Chỉ đọc cục bộ, không gọi mạng. Fail closed theo contract §8:
 * - chưa có snapshot hoặc snapshot hết tin cậy tại thời điểm hỏi → CACHE_UNAVAILABLE;
 * - cổng không có trong cache → CACHE_UNAVAILABLE; cổng bị tắt → GATE_DISABLED;
 * - nhân viên không active hoặc không có window phủ (gate, hướng, thời điểm) → OUTSIDE_POLICY.
 * Window là khoảng [startsAt, endsAt) UTC do Central sinh từ Assignment/Shift/timezone nên Edge không tự tính ca.
 * Không kiểm tra revocation: việc đó thuộc verifier (Task 6) với cache thu hồi.
 */
public final class CachedAccessPolicy implements AccessPolicyPort, GateCatalogPort {
    private final BootstrapCache cache;

    public CachedAccessPolicy(BootstrapCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override
    public Optional<GateInfo> find(UUID gateId, Instant now) {
        return trusted(now).flatMap(snapshot -> snapshot.gates().stream()
                        .filter(gate -> gate.id().equals(gateId)).findFirst())
                .map(gate -> new GateInfo(gate.id(), gate.edgeId(), gate.factoryId(), gate.direction(), gate.enabled()));
    }

    @Override
    public PolicyDecision evaluate(UUID employeeId, UUID gateId, Direction direction, Instant occurredAt) {
        Optional<Snapshot> current = trusted(occurredAt);
        if (current.isEmpty()) {
            return PolicyDecision.deny("CACHE_UNAVAILABLE", 0);
        }
        Snapshot snapshot = current.get();
        long version = snapshot.policy().version();
        var gate = snapshot.gates().stream().filter(candidate -> candidate.id().equals(gateId)).findFirst();
        if (gate.isEmpty()) {
            return PolicyDecision.deny("CACHE_UNAVAILABLE", version);
        }
        if (!gate.get().enabled()) {
            return PolicyDecision.deny("GATE_DISABLED", version);
        }
        boolean allowed = snapshot.policy().employees().stream()
                .filter(employee -> employee.active() && employee.employeeId().equals(employeeId))
                .flatMap(employee -> employee.windows().stream())
                .anyMatch(window -> window.gateId().equals(gateId) && window.direction() == direction
                        && !occurredAt.isBefore(window.startsAt()) && occurredAt.isBefore(window.endsAt()));
        return allowed ? PolicyDecision.allow(version) : PolicyDecision.deny("OUTSIDE_POLICY", version);
    }

    private Optional<Snapshot> trusted(Instant at) {
        return cache.snapshot().filter(snapshot -> snapshot.trustedAt(at));
    }
}
