package vn.tmc.contracts;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO EVT-03: gate.command.result theo CONTRACT-V1.md §6.
 * Payload JSON canonical:
 * {commandId:UUID, attendanceEventId:UUID?, mode:"QR"|"MANUAL"|"EMERGENCY", operatorId:UUID?, reason:string?, status:"UNLOCK_ACCEPTED"|"FAILED"|"TIMEOUT", completedAt:Instant, code:string?}
 */
public record GateCommandResultPayload(
        UUID commandId,
        UUID attendanceEventId,
        Mode mode,
        UUID operatorId,
        String reason,
        Status status,
        Instant completedAt,
        String code
) {
    public enum Mode { QR, MANUAL, EMERGENCY }
    public enum Status { UNLOCK_ACCEPTED, FAILED, TIMEOUT }

    public GateCommandResultPayload {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
    }
}
