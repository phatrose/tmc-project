package vn.tmc.edge.gatestation.domain;

import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;
import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;
import vn.tmc.edge.edgesync.api.ScanOutcome;
import vn.tmc.edge.edgesync.api.ScanOutcome.Decision;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2. ScanResult theo CONTRACT-V1.md §5. ALLOW nghĩa là đủ điều kiện và đã lưu event,
 * KHÔNG khẳng định người đã qua cổng: UI phải đọc commandStatus. Không chứa token thô.
 */
public record ScanResult(UUID scanId, Decision decision, String reasonCode, UUID eventId, UUID commandId,
                         CommandStatus commandStatus, Instant occurredAt, ScanMode mode) {
    public ScanResult {
        Objects.requireNonNull(scanId, "scanId");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(commandStatus, "commandStatus");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(mode, "mode");
    }

    /** DENY nghiệp vụ: không có event công, không có lệnh cổng. */
    public static ScanResult denied(UUID scanId, String reasonCode, Instant occurredAt, ScanMode mode) {
        return new ScanResult(scanId, Decision.DENY, reasonCode, null, null, CommandStatus.NOT_SENT, occurredAt, mode);
    }

    public static ScanResult from(ScanOutcome outcome) {
        return new ScanResult(outcome.scanId(), outcome.decision(), outcome.reasonCode(), outcome.eventId(),
                outcome.commandId(), outcome.commandStatus(), outcome.occurredAt(), outcome.mode());
    }

    public ScanResult withCommandStatus(CommandStatus status) {
        return new ScanResult(scanId, decision, reasonCode, eventId, commandId, status, occurredAt, mode);
    }
}
