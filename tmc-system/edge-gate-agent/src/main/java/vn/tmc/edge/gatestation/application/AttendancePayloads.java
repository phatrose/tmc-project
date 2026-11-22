package vn.tmc.edge.gatestation.application;

import vn.tmc.contracts.AttendancePayload;
import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.contracts.AttendancePayload.Source;
import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;

import java.time.Instant;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2. Điểm DUY NHẤT dựng AttendancePayload (EVT-01, CONTRACT-V1.md §6).
 * AttendancePayload do Task 3 chủ trì và hiện có hai hình dạng khác nhau giữa các nhánh (Task 7 dùng String cho ID,
 * Task 3 dùng UUID kèm validation). Gom việc dựng vào đây để khi nhánh Task 3 vào main chỉ phải sửa file này,
 * không phải sửa use case.
 * QR: bắt buộc jti/kid/tokenIssuedAt, operatorId/reason = null. MANUAL: bắt buộc operatorId/reason, ba trường token = null.
 */
final class AttendancePayloads {
    private AttendancePayloads() {}

    static AttendancePayload qr(UUID employeeId, Direction direction, ScanMode mode, UUID jti, String kid,
                                Instant tokenIssuedAt, UUID commandId) {
        Source source = mode == ScanMode.OFFLINE ? Source.OFFLINE : Source.ONLINE;
        return new AttendancePayload(employeeId.toString(), direction, source, jti.toString(), kid, tokenIssuedAt,
                commandId.toString(), null, null);
    }

    static AttendancePayload manual(UUID employeeId, Direction direction, UUID operatorId, String reason, UUID commandId) {
        return new AttendancePayload(employeeId.toString(), direction, Source.MANUAL, null, null, null,
                commandId.toString(), operatorId.toString(), reason);
    }
}
