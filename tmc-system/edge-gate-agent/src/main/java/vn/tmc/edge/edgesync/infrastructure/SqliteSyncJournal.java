package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.edge.edgesync.domain.SyncJournal.Attempt;
import vn.tmc.edge.edgesync.domain.SyncJournal.Schedule;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

final class SqliteSyncJournal {
    private SqliteSyncJournal() {}

    static Attempt claim(Connection db, UUID edgeId, Instant now) throws SQLException, IOException {
        try (var statement = db.prepareStatement("""
                SELECT 1 FROM local_sync_batches WHERE edge_id = ? AND state <> 'COMPLETED'
                  AND (state = 'IN_FLIGHT' OR stop_reason IS NOT NULL OR next_attempt_at > ?)
                """)) {
            statement.setString(1, edgeId.toString());
            statement.setLong(2, now.toEpochMilli());
            try (var row = statement.executeQuery()) {
                if (row.next()) {
                    return null;
                }
            }
        }
        var batch = SqliteBatches.pending(db, edgeId.toString(), SyncBatchRequest.MAX_EVENTS, now);
        if (batch == null) {
            return null;
        }
        try (var statement = db.prepareStatement("""
                UPDATE local_sync_batches
                SET state = 'IN_FLIGHT', attempt_count = attempt_count + 1, next_attempt_at = NULL
                WHERE batch_id = ? AND state IN ('READY', 'RETRY_WAIT') AND stop_reason IS NULL
                  AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                RETURNING attempt_count
                """)) {
            statement.setString(1, batch.syncBatchId().toString());
            statement.setLong(2, now.toEpochMilli());
            try (var row = statement.executeQuery()) {
                return row.next() ? new Attempt(batch, row.getInt(1) - 1) : null;
            }
        }
    }

    static void requireInFlight(Connection db, Attempt attempt) throws SQLException {
        try (var statement = db.prepareStatement("""
                SELECT 1 FROM local_sync_batches WHERE batch_id = ? AND state = 'IN_FLIGHT' AND attempt_count = ?
                """)) {
            statement.setString(1, attempt.request().syncBatchId().toString());
            statement.setLong(2, (long) attempt.number() + 1);
            try (var row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("Sync attempt is no longer in flight");
                }
            }
        }
    }

    static void defer(Connection db, Attempt attempt, Schedule schedule) throws SQLException {
        requireInFlight(db, attempt);
        try (var statement = db.prepareStatement("""
                UPDATE local_sync_batches SET state = 'RETRY_WAIT', next_attempt_at = ?, stop_reason = ?
                WHERE batch_id = ?
                """)) {
            if (schedule.retryAt() == null) {
                statement.setNull(1, Types.INTEGER);
            } else {
                statement.setLong(1, schedule.retryAt().toEpochMilli());
            }
            statement.setString(2, schedule.stopReason());
            statement.setString(3, attempt.request().syncBatchId().toString());
            statement.executeUpdate();
        }
    }

    static Schedule schedule(Connection db, UUID edgeId) throws SQLException {
        try (var statement = db.prepareStatement("""
                SELECT next_attempt_at, stop_reason FROM local_sync_batches WHERE edge_id = ? AND state <> 'COMPLETED'
                """)) {
            statement.setString(1, edgeId.toString());
            try (var row = statement.executeQuery()) {
                if (row.next()) {
                    long time = row.getLong(1);
                    return new Schedule(row.wasNull() ? null : Instant.ofEpochMilli(time), row.getString(2));
                }
                return new Schedule(null, null);
            }
        }
    }

    static void resume(Connection db, UUID edgeId) throws SQLException {
        try (var statement = db.prepareStatement("""
                UPDATE local_sync_batches SET stop_reason = NULL WHERE edge_id = ? AND state = 'RETRY_WAIT'
                """)) {
            statement.setString(1, edgeId.toString());
            statement.executeUpdate();
        }
    }

    static void recover(Connection db, Instant now) throws SQLException {
        try (var statement = db.createStatement()) {
            try (var row = statement.executeQuery("PRAGMA quick_check")) {
                if (!row.next() || !"ok".equals(row.getString(1))) {
                    throw new SQLException("Edge database failed its integrity check");
                }
            }
            try (var row = statement.executeQuery("PRAGMA foreign_key_check")) {
                if (row.next()) {
                    throw new SQLException("Edge database failed its foreign key check");
                }
            }
            statement.executeUpdate("""
                    UPDATE local_sync_batches SET state = 'RETRY_WAIT', next_attempt_at = NULL
                    WHERE state = 'IN_FLIGHT'
                    """);
            statement.executeUpdate("""
                    UPDATE local_scan_outcomes SET command_status = 'TIMEOUT'
                    WHERE command_status = 'NOT_SENT' AND command_id IN (
                        SELECT command_id FROM local_gate_commands WHERE status = 'NOT_SENT'
                    )
                    """);
        }
        try (var statement = db.prepareStatement("""
                UPDATE local_gate_commands SET status = 'TIMEOUT', completed_at = ?, code = 'RECOVERY_UNKNOWN'
                WHERE status = 'NOT_SENT'
                """)) {
            statement.setLong(1, now.toEpochMilli());
            statement.executeUpdate();
        }
    }
}
