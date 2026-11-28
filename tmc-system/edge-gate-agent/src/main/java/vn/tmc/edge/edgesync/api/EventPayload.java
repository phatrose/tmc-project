package vn.tmc.edge.edgesync.api;

import com.fasterxml.jackson.annotation.JsonValue;
import vn.tmc.contracts.AttendancePayload;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Typed payloads for the four Contract v1 event types. */
public sealed interface EventPayload {
    default String eventType() {
        return switch (this) {
            case Attendance ignored -> "attendance.event.recorded";
            case AccessDenied ignored -> "access.attempt.denied";
            case GateCommandResult ignored -> "gate.command.result";
            case ReplayReport ignored -> "replay.cache.reported";
        };
    }

    record Attendance(AttendancePayload value) implements EventPayload {
        public Attendance {
            Objects.requireNonNull(value, "attendance payload");
        }

        @JsonValue
        public AttendancePayload wireValue() {
            return value;
        }
    }

    record AccessDenied(UUID employeeId, String reasonCode, UUID jti, String kid, ScanMode source)
            implements EventPayload {
        public AccessDenied {
            Objects.requireNonNull(reasonCode, "reasonCode");
            Objects.requireNonNull(source, "source");
        }
    }

    record GateCommandResult(UUID commandId, UUID attendanceEventId, CommandMode mode, UUID operatorId,
                             String reason, CommandStatus status, Instant completedAt, String code)
            implements EventPayload {
        public GateCommandResult {
            Objects.requireNonNull(commandId, "commandId");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(completedAt, "completedAt");
            if (commandId.version() != 4 || commandId.variant() != 2 || status == CommandStatus.NOT_SENT) {
                throw new IllegalArgumentException("A command result requires a UUIDv4 commandId and a completion status");
            }
        }
    }

    record ReplayReport(List<ReplayItem> items) implements EventPayload {
        public ReplayReport {
            items = List.copyOf(items);
            if (items.size() > 50) {
                throw new IllegalArgumentException("Replay reports support up to 50 items");
            }
        }
    }

    record ReplayItem(UUID jti, UUID firstEventId, Instant seenAt, Instant expiresAt) {
        public ReplayItem {
            Objects.requireNonNull(jti, "jti");
            Objects.requireNonNull(firstEventId, "firstEventId");
            Objects.requireNonNull(seenAt, "seenAt");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    enum ScanMode { ONLINE, OFFLINE }
    enum CommandMode { QR, MANUAL, EMERGENCY }
    enum CommandStatus { NOT_SENT, UNLOCK_ACCEPTED, FAILED, TIMEOUT }
}
