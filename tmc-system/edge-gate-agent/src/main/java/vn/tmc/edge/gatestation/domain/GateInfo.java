package vn.tmc.edge.gatestation.domain;

import vn.tmc.contracts.AttendancePayload.Direction;

import java.util.Objects;
import java.util.UUID;

/** PHỤ TRÁCH: Task 2. Cấu hình một cổng do Task 5 quản lý ở Central và Task 7 cache xuống Edge. */
public record GateInfo(UUID gateId, UUID edgeId, UUID factoryId, Direction direction, boolean enabled) {
    public GateInfo {
        Objects.requireNonNull(gateId, "gateId");
        Objects.requireNonNull(edgeId, "edgeId");
        Objects.requireNonNull(factoryId, "factoryId");
        Objects.requireNonNull(direction, "direction");
    }
}
