package vn.tmc.edge.gatestation.domain;

import vn.tmc.contracts.AttendancePayload.Direction;

import java.time.Instant;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2.
 * Kiểm tra nhân viên có quyền qua gate theo hướng tại thời điểm occurredAt, chỉ dựa trên cache cục bộ
 * (CONTRACT-V1.md §10: AccessPolicyPort.evaluate). Không gọi Central trong luồng scan.
 * Thiếu cache hoặc cache hết tin cậy phải trả deny CACHE_UNAVAILABLE, không được mặc định cho qua.
 */
public interface AccessPolicyPort {
    PolicyDecision evaluate(UUID employeeId, UUID gateId, Direction direction, Instant occurredAt);
}
