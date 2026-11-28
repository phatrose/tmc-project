package vn.tmc.edge.gatestation.domain;

import vn.tmc.edge.edgesync.api.EventPayload.CommandMode;
import vn.tmc.edge.gatestation.domain.GateStationException.Kind;

import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2. Đầu vào POST /edge/v1/overrides (CONTRACT-V1.md §5):
 * {scanId, gateId, employeeId?, reason, mode:MANUAL|EMERGENCY, grant}.
 * grant là action grant do Central cấp sau re-auth; không có trong toString.
 */
public record OverrideRequest(UUID scanId, UUID gateId, UUID employeeId, String reason, CommandMode mode, String grant) {
    public static final int MIN_REASON_CHARS = 10;
    public static final int MAX_REASON_CHARS = 500;
    public static final int MAX_GRANT_CHARS = 4096;

    public OverrideRequest {
        Uuids.requireV4(scanId, "scanId");
        Objects.requireNonNull(gateId, "gateId");
        if (mode != CommandMode.MANUAL && mode != CommandMode.EMERGENCY) {
            throw new GateStationException(Kind.VALIDATION_ERROR, "mode phải là MANUAL hoặc EMERGENCY");
        }
        if (mode == CommandMode.MANUAL && employeeId == null) {
            throw new GateStationException(Kind.VALIDATION_ERROR, "MANUAL cần employeeId");
        }
        if (mode == CommandMode.EMERGENCY && employeeId != null) {
            throw new GateStationException(Kind.VALIDATION_ERROR, "EMERGENCY không có employeeId");
        }
        int length = reason == null ? 0 : reason.codePointCount(0, reason.length());
        if (length < MIN_REASON_CHARS || length > MAX_REASON_CHARS) {
            throw new GateStationException(Kind.VALIDATION_ERROR,
                    "reason phải từ " + MIN_REASON_CHARS + " đến " + MAX_REASON_CHARS + " ký tự");
        }
        if (grant == null || grant.isBlank() || grant.length() > MAX_GRANT_CHARS) {
            throw new GateStationException(Kind.VALIDATION_ERROR, "grant phải khác rỗng và tối đa " + MAX_GRANT_CHARS + " ký tự");
        }
    }

    @Override
    public String toString() {
        return "OverrideRequest[scanId=" + scanId + ", gateId=" + gateId + ", mode=" + mode + ", grant=***]";
    }
}
