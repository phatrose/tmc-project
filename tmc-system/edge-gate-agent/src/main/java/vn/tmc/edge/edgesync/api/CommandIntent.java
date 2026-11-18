package vn.tmc.edge.edgesync.api;

import vn.tmc.edge.edgesync.api.EventPayload.CommandMode;

import java.util.UUID;

public record CommandIntent(UUID commandId, UUID gateId, UUID operatorId, String reason, CommandMode mode) {}
