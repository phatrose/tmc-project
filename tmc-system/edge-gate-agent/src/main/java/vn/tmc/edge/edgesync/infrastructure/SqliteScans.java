package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.edge.edgesync.api.CommandIntent;
import vn.tmc.edge.edgesync.api.EventPayload.CommandMode;
import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;
import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.api.ReplayReservation;
import vn.tmc.edge.edgesync.api.ScanInputFingerprint;
import vn.tmc.edge.edgesync.api.ScanOutcome;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
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
}
