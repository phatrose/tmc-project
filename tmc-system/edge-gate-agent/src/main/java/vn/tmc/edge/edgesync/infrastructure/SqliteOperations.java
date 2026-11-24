package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.edge.edgesync.domain.EdgeStatus.Queue;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

final class SqliteOperations {
    private static final Duration DEDUP_MINIMUM = Duration.ofHours(48);

    private SqliteOperations() {}

    static Queue queue(Connection db, UUID edgeId) throws SQLException {
        try (var statement = db.prepareStatement("""
                SELECT es.bootstrap_revision, es.last_central_contact_at,
                    count(*) FILTER (WHERE d.state = 'PENDING') AS pending_count,
                    count(*) FILTER (WHERE d.state = 'FAILED') AS failed_count,
                    min(e.occurred_at) FILTER (WHERE d.state = 'PENDING') AS oldest_pending,
                    min(e.occurred_at) FILTER (WHERE d.state IN ('PENDING', 'FAILED')) AS oldest_unresolved
                FROM edge_runtime_state es
                LEFT JOIN local_events e ON e.edge_id = es.edge_id
                LEFT JOIN event_delivery d ON d.event_id = e.event_id
                WHERE es.edge_id = ? GROUP BY es.edge_id
                """)) {
            statement.setString(1, edgeId.toString());
            try (var row = statement.executeQuery()) {
                if (!row.next()) {
                    return new Queue(0, 0, 0, null, null, null);
                }
                return new Queue(row.getLong("bootstrap_revision"), row.getLong("pending_count"), row.getLong("failed_count"),
                        SqliteTime.nullableInstant(row, "oldest_pending"),
                        SqliteTime.nullableInstant(row, "oldest_unresolved"),
                        SqliteTime.nullableInstant(row, "last_central_contact_at"));
            }
        }
    }

    static void recordContact(Connection db, UUID edgeId, Instant at) throws SQLException {
        try (var statement = db.prepareStatement("""
                UPDATE edge_runtime_state SET last_central_contact_at = ?
                WHERE edge_id = ? AND (last_central_contact_at IS NULL OR last_central_contact_at < ?)
                """)) {
            statement.setLong(1, at.toEpochMilli());
            statement.setString(2, edgeId.toString());
            statement.setLong(3, at.toEpochMilli());
            statement.executeUpdate();
        }
    }

    static void cleanup(Connection db, Instant now) throws SQLException {
        try (var statement = db.prepareStatement("DELETE FROM local_replay_cache WHERE purge_after < ?")) {
            statement.setLong(1, now.toEpochMilli());
            statement.executeUpdate();
        }

        long cutoff = now.minus(DEDUP_MINIMUM).toEpochMilli();
        var scans = new ArrayList<String>();
        // Keep ambiguous commands and command evidence linked to unresolved event delivery.
        try (var statement = db.prepareStatement("""
                DELETE FROM local_gate_commands WHERE command_id IN (
                    SELECT c.command_id FROM local_gate_commands c JOIN local_scan_outcomes s USING (scan_id)
                    LEFT JOIN event_delivery d ON d.event_id = s.event_id
                    WHERE c.command_id = s.command_id AND c.status = s.command_status
                      AND c.status IN ('UNLOCK_ACCEPTED', 'FAILED')
                      AND c.completed_at <= ? AND s.occurred_at <= ?
                      AND c.retain_until <= ? AND s.retain_until <= ?
                      AND d.state = 'ACKNOWLEDGED'
                    ORDER BY c.retain_until LIMIT 1000
                )
                RETURNING scan_id
                """)) {
            statement.setLong(1, cutoff);
            statement.setLong(2, cutoff);
            statement.setLong(3, now.toEpochMilli());
            statement.setLong(4, now.toEpochMilli());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    scans.add(rows.getString(1));
                }
            }
        }
        try (var statement = db.prepareStatement("DELETE FROM local_scan_outcomes WHERE scan_id = ?")) {
            for (String scan : scans) {
                statement.setString(1, scan);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (var statement = db.prepareStatement("""
                DELETE FROM local_scan_outcomes WHERE scan_id IN (
                    SELECT s.scan_id FROM local_scan_outcomes s LEFT JOIN event_delivery d ON d.event_id = s.event_id
                    WHERE s.decision = 'DENY' AND s.command_id IS NULL AND s.retain_until <= ?
                      AND s.occurred_at <= ? AND (s.event_id IS NULL OR d.state = 'ACKNOWLEDGED')
                      AND NOT EXISTS (SELECT 1 FROM local_gate_commands c WHERE c.scan_id = s.scan_id)
                    ORDER BY s.retain_until LIMIT 1000
                )
                """)) {
            statement.setLong(1, now.toEpochMilli());
            statement.setLong(2, cutoff);
            statement.executeUpdate();
        }
        // Keep all event envelopes, delivery rows and batch history under the V1 retention policy.
    }

}
