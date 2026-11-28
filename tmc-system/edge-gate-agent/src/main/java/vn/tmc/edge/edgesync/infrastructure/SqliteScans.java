package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.edge.edgesync.api.CommandCompletion;
import vn.tmc.edge.edgesync.api.CommandIntent;
import vn.tmc.edge.edgesync.api.EventPayload.CommandMode;
import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;
import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.api.ReplayReservation;
import vn.tmc.edge.edgesync.api.ScanInputFingerprint;
import vn.tmc.edge.edgesync.api.ScanOutcome;
import vn.tmc.edge.edgesync.domain.BootstrapCache.Identity;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static vn.tmc.edge.edgesync.api.EventStoreException.Code.IDEMPOTENCY_CONFLICT;
import static vn.tmc.edge.edgesync.api.EventStoreException.Code.REPLAY;

/** SQL for local replay protection, scan outcomes, and device intents. */
final class SqliteScans {
    private static final Duration DEDUP_RETENTION = Duration.ofHours(48);

    private SqliteScans() {}

    static Optional<ScanOutcome> findOutcome(Connection connection, UUID scanId, ScanInputFingerprint fingerprint)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT gate_id, input_fingerprint, decision, reason_code, event_id, command_id,
                    command_status, occurred_at, mode
                FROM local_scan_outcomes WHERE scan_id = ?
                """)) {
            statement.setString(1, scanId.toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                if (!result.getString("input_fingerprint").equals(fingerprint.sha256())) {
                    throw new EventStoreException(IDEMPOTENCY_CONFLICT);
                }
                return Optional.of(new ScanOutcome(scanId, UUID.fromString(result.getString("gate_id")),
                        ScanOutcome.Decision.valueOf(result.getString("decision")), result.getString("reason_code"),
                        nullableUuid(result.getString("event_id")), nullableUuid(result.getString("command_id")),
                        CommandStatus.valueOf(result.getString("command_status")),
                        Instant.ofEpochMilli(result.getLong("occurred_at")), ScanMode.valueOf(result.getString("mode"))));
            }
        }
    }

    private static UUID nullableUuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private static String uuidText(UUID value) {
        return value == null ? null : value.toString();
    }

    static Optional<ScanOutcome> findRetry(Connection connection, UUID scanId, ScanInputFingerprint fingerprint,
                                           UUID gateId, ScanOutcome.Decision decision) throws SQLException {
        var previous = findOutcome(connection, scanId, fingerprint);
        if (previous.isPresent()) {
            var outcome = previous.orElseThrow();
            if (!outcome.gateId().equals(gateId) || outcome.decision() != decision) {
                throw new EventStoreException(IDEMPOTENCY_CONFLICT);
            }
        }
        return previous;
    }

    static void rejectReplay(Connection connection, ReplayReservation reservation) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT 1 FROM local_replay_cache WHERE kind = ? AND jti = ?")) {
            statement.setString(1, reservation.kind().name());
            statement.setString(2, reservation.jti().toString());
            try (var result = statement.executeQuery()) {
                if (result.next()) {
                    throw new EventStoreException(REPLAY);
                }
            }
        }
    }

    static boolean hasRecentAllow(Connection connection, ReplayReservation reservation, Instant decisionTime) throws SQLException {
        // Manual attendance is also recorded as ALLOW.
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM local_scan_outcomes
                WHERE decision = 'ALLOW' AND employee_id = ? AND gate_id = ? AND direction = ?
                  AND occurred_at > ? AND occurred_at <= ?
                LIMIT 1
                """)) {
            statement.setString(1, reservation.employeeId().toString());
            statement.setString(2, reservation.gateId().toString());
            statement.setString(3, reservation.direction().name());
            statement.setLong(4, decisionTime.minusSeconds(30).toEpochMilli());
            statement.setLong(5, decisionTime.toEpochMilli());
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    static void reserve(Connection connection, ReplayReservation reservation, Instant now) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO local_replay_cache (kind, jti, expires_at, employee_id, direction, gate_id, reserved_at, purge_after)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, reservation.kind().name());
            statement.setString(2, reservation.jti().toString());
            statement.setLong(3, reservation.expiresAt().toEpochMilli());
            statement.setString(4, uuidText(reservation.employeeId()));
            statement.setString(5, reservation.direction() == null ? null : reservation.direction().name());
            statement.setString(6, reservation.gateId().toString());
            statement.setLong(7, now.toEpochMilli());
            // ACTION grants use the same expiry skew as QR credentials.
            statement.setLong(8, reservation.expiresAt().plusSeconds(5).toEpochMilli());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Replay reservation was not inserted");
            }
        }
    }

    static void linkEvent(Connection connection, ReplayReservation reservation, UUID eventId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                UPDATE local_replay_cache SET event_id = ? WHERE kind = ? AND jti = ?
                """)) {
            statement.setString(1, eventId.toString());
            statement.setString(2, reservation.kind().name());
            statement.setString(3, reservation.jti().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Replay reservation did not link to its event");
            }
        }
    }

    static void insertCommand(Connection connection, UUID scanId, ReplayReservation reservation, ScanMode mode,
                              Instant occurredAt, UUID eventId, CommandIntent command, Instant now) throws SQLException {
        long retainUntil = now.plus(DEDUP_RETENTION).toEpochMilli();
        boolean emergency = command.mode() == CommandMode.EMERGENCY;
        try (var statement = connection.prepareStatement("""
                INSERT INTO local_scan_outcomes (scan_id, gate_id, input_fingerprint, employee_id, direction,
                    decision, reason_code, event_id, command_id, command_status, occurred_at, mode, retain_until)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'NOT_SENT', ?, ?, ?)
                """)) {
            statement.setString(1, scanId.toString());
            statement.setString(2, reservation.gateId().toString());
            statement.setString(3, reservation.inputFingerprint().sha256());
            statement.setString(4, uuidText(reservation.employeeId()));
            statement.setString(5, reservation.direction() == null ? null : reservation.direction().name());
            statement.setString(6, emergency ? "EMERGENCY" : "ALLOW");
            statement.setString(7, emergency ? "EMERGENCY" : "OK");
            statement.setString(8, uuidText(eventId));
            statement.setString(9, command.commandId().toString());
            statement.setLong(10, occurredAt.toEpochMilli());
            statement.setString(11, mode.name());
            statement.setLong(12, retainUntil);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Scan outcome was not inserted");
            }
        }
        try (var statement = connection.prepareStatement("""
                INSERT INTO local_gate_commands
                    (command_id, scan_id, gate_id, mode, operator_id, reason, status, retain_until)
                VALUES (?, ?, ?, ?, ?, ?, 'NOT_SENT', ?)
                """)) {
            statement.setString(1, command.commandId().toString());
            statement.setString(2, scanId.toString());
            statement.setString(3, command.gateId().toString());
            statement.setString(4, command.mode().name());
            statement.setString(5, uuidText(command.operatorId()));
            statement.setString(6, command.reason());
            statement.setLong(7, retainUntil);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Command intent was not inserted");
            }
        }
    }

    record StoredCommand(UUID scanId, UUID gateId, ScanOutcome.Decision decision, UUID attendanceEventId,
                         UUID commandId, CommandMode mode, UUID operatorId, String reason,
                         CommandStatus status, Instant completedAt, String code, UUID resultEventId) {}

    static Optional<StoredCommand> loadCommandForScan(Connection connection, UUID scanId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT s.gate_id AS scan_gate_id, s.decision, s.event_id AS attendance_event_id,
                       s.command_id AS scan_command_id, s.command_status,
                       c.command_id, c.gate_id, c.mode, c.operator_id, c.reason,
                       c.status, c.completed_at, c.code, c.result_event_id
                FROM local_scan_outcomes s
                LEFT JOIN local_gate_commands c ON c.scan_id = s.scan_id
                WHERE s.scan_id = ?
                """)) {
            statement.setString(1, scanId.toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                UUID scanCommandId = nullableUuid(result.getString("scan_command_id"));
                UUID commandId = nullableUuid(result.getString("command_id"));
                if (commandId == null) {
                    if (scanCommandId != null) {
                        throw new SQLException("Scan outcome references a missing gate command");
                    }
                    return Optional.empty();
                }
                UUID scanGateId = UUID.fromString(result.getString("scan_gate_id"));
                UUID commandGateId = UUID.fromString(result.getString("gate_id"));
                var decision = ScanOutcome.Decision.valueOf(result.getString("decision"));
                UUID attendanceEventId = nullableUuid(result.getString("attendance_event_id"));
                var scanCommandStatus = CommandStatus.valueOf(result.getString("command_status"));
                var mode = CommandMode.valueOf(result.getString("mode"));
                var status = CommandStatus.valueOf(result.getString("status"));
                boolean validMode = mode == CommandMode.EMERGENCY
                        ? decision == ScanOutcome.Decision.EMERGENCY && attendanceEventId == null
                        : decision == ScanOutcome.Decision.ALLOW && attendanceEventId != null;
                if (!Objects.equals(commandId, scanCommandId) || !commandGateId.equals(scanGateId)
                        || status != scanCommandStatus || !validMode) {
                    throw new SQLException("Stored scan outcome and command intent are inconsistent");
                }
                return Optional.of(new StoredCommand(scanId, commandGateId, decision, attendanceEventId, commandId, mode,
                        nullableUuid(result.getString("operator_id")), result.getString("reason"), status,
                        SqliteTime.nullableInstant(result, "completed_at"), result.getString("code"),
                        nullableUuid(result.getString("result_event_id"))));
            }
        }
    }

    static void completeCommand(Connection connection, UUID scanId, UUID commandId, CommandCompletion completion,
                                UUID resultEventId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                UPDATE local_gate_commands
                SET status = ?, completed_at = ?, code = ?, result_event_id = ?
                WHERE command_id = ? AND scan_id = ? AND status = 'NOT_SENT' AND result_event_id IS NULL
                """)) {
            statement.setString(1, completion.status().name());
            statement.setLong(2, completion.completedAt().toEpochMilli());
            if (completion.code() == null) {
                statement.setNull(3, Types.VARCHAR);
            } else {
                statement.setString(3, completion.code());
            }
            statement.setString(4, resultEventId.toString());
            statement.setString(5, commandId.toString());
            statement.setString(6, scanId.toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Command intent was not updated");
            }
        }
        try (var statement = connection.prepareStatement("""
                UPDATE local_scan_outcomes
                SET command_status = ?
                WHERE scan_id = ? AND command_id = ? AND command_status = 'NOT_SENT'
                """)) {
            statement.setString(1, completion.status().name());
            statement.setString(2, scanId.toString());
            statement.setString(3, commandId.toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Scan command status was not updated");
            }
        }
    }

    static Identity singleRuntimeIdentity(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT edge_id, factory_id FROM edge_runtime_state");
             var result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Edge runtime identity is missing");
            }
            var identity = new Identity(UUID.fromString(result.getString(1)), UUID.fromString(result.getString(2)));
            if (result.next()) {
                throw new SQLException("Expected a single provisioned Edge runtime identity");
            }
            return identity;
        }
    }
}
