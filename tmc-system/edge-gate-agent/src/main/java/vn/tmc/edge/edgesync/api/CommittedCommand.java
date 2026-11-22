package vn.tmc.edge.edgesync.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persisted command identity. Only the first successful commit carries a new command. */
public record CommittedCommand(UUID commandId, Instant committedAt, Optional<CommandIntent> newCommand) {
    public CommittedCommand {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(committedAt, "committedAt");
        Objects.requireNonNull(newCommand, "newCommand");
    }
}
