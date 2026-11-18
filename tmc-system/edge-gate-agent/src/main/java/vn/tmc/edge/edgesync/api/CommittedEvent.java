package vn.tmc.edge.edgesync.api;

import vn.tmc.contracts.EventEnvelope;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record CommittedEvent(EventEnvelope envelope, Instant committedAt, Optional<CommandIntent> newCommand) {
    public CommittedEvent {
        Objects.requireNonNull(newCommand, "newCommand");
    }

    public CommittedEvent(EventEnvelope envelope, Instant committedAt) {
        this(envelope, committedAt, Optional.empty());
    }
}
