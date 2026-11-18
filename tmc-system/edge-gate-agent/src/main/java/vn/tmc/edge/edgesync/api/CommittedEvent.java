package vn.tmc.edge.edgesync.api;

import vn.tmc.contracts.EventEnvelope;

import java.time.Instant;

public record CommittedEvent(EventEnvelope envelope, Instant committedAt) {}
