package vn.tmc.edge.gatestation.domain;

import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2. Kết quả Turnstile theo CONTRACT-V1.md §5 (mục "Turnstile mock"):
 * {commandId, status:UNLOCK_ACCEPTED|FAILED|TIMEOUT, completedAt, code?}.
 * UNLOCK_ACCEPTED chỉ là thiết bị đã nhận lệnh, không chứng minh người đã đi qua.
 */
public record CommandResult(UUID commandId, CommandStatus status, Instant completedAt, String code) {
    public CommandResult {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(completedAt, "completedAt");
        if (status == CommandStatus.NOT_SENT) {
            throw new IllegalArgumentException("NOT_SENT không phải kết quả hoàn tất của thiết bị");
        }
    }
}
