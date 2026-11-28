package vn.tmc.edge.edgesync.api;

import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** Terminal Turnstile outcome persisted after Task 2 executes an unlocked command. */
public record CommandCompletion(UUID commandId, CommandStatus status, Instant completedAt, String code) {
    public CommandCompletion {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(completedAt, "completedAt");
        if (commandId.version() != 4 || commandId.variant() != 2 || status == CommandStatus.NOT_SENT) {
            throw new IllegalArgumentException("Command completion requires a UUIDv4 commandId and a terminal status");
        }
        completedAt = completedAt.truncatedTo(ChronoUnit.MILLIS);
        try {
            completedAt.toEpochMilli();
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException("completedAt is outside millisecond range", invalid);
        }
        if (code != null && code.isBlank()) {
            throw new IllegalArgumentException("Command completion code must be null or non-blank");
        }
    }
}
