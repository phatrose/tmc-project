package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.contracts.EventEnvelope;
import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.edge.edgesync.domain.SyncProtocolException;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

final class SqliteBatches {
    private static final Set<String> FAILURE_CODES = Set.of("EVENT_CONTENT_CONFLICT", "SEQUENCE_CONFLICT",
            "UNSUPPORTED_SCHEMA", "UNSUPPORTED_EVENT_TYPE", "INVALID_PAYLOAD", "HASH_MISMATCH", "UNKNOWN_GATE");

    private SqliteBatches() {}

    static SyncBatchRequest pending(Connection db, String edgeId, int maxEvents, Instant now)
            throws SQLException, IOException {
        try (var statement = db.prepareStatement("""
                SELECT batch_id FROM local_sync_batches
                WHERE state <> 'COMPLETED' AND (? IS NULL OR edge_id = ?) ORDER BY created_at, batch_id LIMIT 1
                """)) {
            statement.setString(1, edgeId);
            statement.setString(2, edgeId);
            try (var row = statement.executeQuery()) {
                if (row.next()) {
                    return loadActive(db, UUID.fromString(row.getString(1)));
                }
            }
        }

        var batchId = UUID.randomUUID();
        var members = new ArrayList<EventEnvelope>();
        SyncBatchRequest selected = null;
        byte[] selectedBody = null;
        try (var statement = db.prepareStatement("""
                SELECT e.* FROM local_events e JOIN event_delivery d USING (event_id)
                WHERE d.state = 'PENDING' AND d.batch_id IS NULL AND e.edge_id = (
                    SELECT candidate.edge_id FROM local_events candidate JOIN event_delivery delivery USING (event_id)
                    WHERE delivery.state = 'PENDING' AND delivery.batch_id IS NULL
                      AND (? IS NULL OR candidate.edge_id = ?)
                    ORDER BY candidate.committed_at, candidate.edge_id LIMIT 1
                )
                ORDER BY e.sequence_no LIMIT ?
                """)) {
            statement.setString(1, edgeId);
            statement.setString(2, edgeId);
            statement.setInt(3, maxEvents);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    members.add(SqliteEvents.envelope(rows));
                    var candidate = new SyncBatchRequest(batchId, members);
                    byte[] body = WireJson.write(candidate);
                    if (body.length > SyncBatchRequest.MAX_BODY_BYTES) {
                        if (selected == null) {
                            throw new SyncProtocolException("Pending event exceeds the sync body limit");
                        }
                        break;
                    }
                    selected = candidate;
                    selectedBody = body;
                }
            }
        }
        if (selected == null) {
            return null;
        }

        try (var statement = db.prepareStatement("""
                INSERT INTO local_sync_batches(batch_id, edge_id, state, member_count, membership_fingerprint, created_at)
                VALUES (?, ?, 'READY', ?, ?, ?)
                """)) {
            statement.setString(1, batchId.toString());
            statement.setString(2, selected.events().getFirst().edgeId().toString());
            statement.setInt(3, selected.events().size());
            statement.setString(4, CanonicalPayload.sha256(selectedBody));
            statement.setLong(5, now.toEpochMilli());
            statement.executeUpdate();
        }
        try (var statement = db.prepareStatement("""
                UPDATE event_delivery SET batch_id = ? WHERE event_id = ? AND state = 'PENDING' AND batch_id IS NULL
                """)) {
            statement.setString(1, batchId.toString());
            for (var event : selected.events()) {
                statement.setString(2, event.eventId().toString());
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Batch member is no longer pending");
                }
            }
        }
        return selected;
    }

    private static SyncBatchRequest loadActive(Connection db, UUID batchId) throws SQLException, IOException {
        String edgeId;
        String fingerprint;
        int count;
        try (var statement = db.prepareStatement("""
                SELECT edge_id, member_count, membership_fingerprint FROM local_sync_batches
                WHERE batch_id = ? AND state <> 'COMPLETED'
                """)) {
            statement.setString(1, batchId.toString());
            try (var row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SyncProtocolException("ACK does not identify an active batch");
                }
                edgeId = row.getString("edge_id");
                count = row.getInt("member_count");
                fingerprint = row.getString("membership_fingerprint");
            }
        }
        var members = new ArrayList<EventEnvelope>(count);
        try (var statement = db.prepareStatement("""
                SELECT e.* FROM local_events e JOIN event_delivery d USING (event_id)
                WHERE d.batch_id = ? AND e.edge_id = ? AND d.state = 'PENDING' ORDER BY e.sequence_no
                """)) {
            statement.setString(1, batchId.toString());
            statement.setString(2, edgeId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    members.add(SqliteEvents.envelope(rows));
                }
            }
        }
        if (members.size() != count) {
            throw new SQLException("Persisted sync batch membership is incomplete");
        }
        var batch = new SyncBatchRequest(batchId, members);
        if (!fingerprint.equals(CanonicalPayload.sha256(WireJson.write(batch)))) {
            throw new SQLException("Persisted sync batch fingerprint differs");
        }
        return batch;
    }

    static void apply(Connection db, UUID expectedBatchId, SyncBatchAck ack) throws SQLException, IOException {
        if (ack == null || expectedBatchId == null || !expectedBatchId.equals(ack.syncBatchId())) {
            throw new SyncProtocolException("ACK batch ID differs from the request");
        }
        var batch = loadActive(db, expectedBatchId);
        validate(batch, ack);
        try (var statement = db.prepareStatement("""
                UPDATE event_delivery SET state = ?, ack_status = ?, accepted_at = ?, failure_code = ?
                WHERE event_id = ? AND batch_id = ? AND state = 'PENDING'
                """)) {
            statement.setString(6, expectedBatchId.toString());
            for (var member : ack.acks()) {
                statement.setString(1, accepted(member.status()) ? "ACKNOWLEDGED" : "FAILED");
                statement.setString(2, member.status().name());
                if (member.acceptedAt() == null) {
                    statement.setNull(3, java.sql.Types.INTEGER);
                } else {
                    statement.setLong(3, member.acceptedAt().toEpochMilli());
                }
                statement.setString(4, member.code());
                statement.setString(5, member.eventId().toString());
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("ACK did not update exactly one event");
                }
            }
        }
        advanceCursor(db, batch.events().getFirst().edgeId().toString(), ack.lastContiguousSequenceNo());
        try (var statement = db.prepareStatement("UPDATE local_sync_batches SET state = 'COMPLETED' WHERE batch_id = ?")) {
            statement.setString(1, expectedBatchId.toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("ACK did not complete the batch");
            }
        }
    }

    private static void validate(SyncBatchRequest batch, SyncBatchAck ack) {
        if (ack.acks() == null || ack.acks().size() != batch.events().size()
                || ack.lastContiguousSequenceNo() < 0 || ack.lastContiguousSequenceNo() > 9_007_199_254_740_991L) {
            throw new SyncProtocolException("ACK member count or cursor is invalid");
        }
        for (int index = 0; index < batch.events().size(); index++) {
            var member = ack.acks().get(index);
            if (member == null || !batch.events().get(index).eventId().equals(member.eventId()) || member.status() == null) {
                throw new SyncProtocolException("ACK members must match request order");
            }
            if (accepted(member.status())) {
                if (member.acceptedAt() == null || member.code() != null) {
                    throw new SyncProtocolException("Successful ACK requires acceptedAt and null code");
                }
                try {
                    member.acceptedAt().toEpochMilli();
                } catch (ArithmeticException invalid) {
                    throw new SyncProtocolException("ACK acceptedAt is outside the supported range", invalid);
                }
            } else if (member.acceptedAt() != null || member.code() == null || !FAILURE_CODES.contains(member.code())) {
                throw new SyncProtocolException("Failed ACK requires a contract error code and null acceptedAt");
            }
        }
    }

    private static boolean accepted(SyncBatchAck.Status status) {
        return status == SyncBatchAck.Status.ACCEPTED || status == SyncBatchAck.Status.DUPLICATE;
    }

    private static void advanceCursor(Connection db, String edgeId, long reported) throws SQLException {
        long cursor;
        try (var statement = db.prepareStatement("SELECT last_contiguous_ack_sequence_no FROM edge_runtime_state WHERE edge_id = ?")) {
            statement.setString(1, edgeId);
            try (var row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("Batch Edge identity is missing");
                }
                cursor = row.getLong(1);
            }
        }
        try (var statement = db.prepareStatement("""
                SELECT e.sequence_no, d.state FROM local_events e LEFT JOIN event_delivery d USING (event_id)
                WHERE e.edge_id = ? AND e.sequence_no > ? AND e.sequence_no <= ? ORDER BY e.sequence_no
                """)) {
            statement.setString(1, edgeId);
            statement.setLong(2, cursor);
            statement.setLong(3, reported);
            try (var rows = statement.executeQuery()) {
                while (rows.next() && rows.getLong(1) == cursor + 1 && "ACKNOWLEDGED".equals(rows.getString(2))) {
                    cursor++;
                }
            }
        }
        try (var statement = db.prepareStatement("UPDATE edge_runtime_state SET last_contiguous_ack_sequence_no = ? WHERE edge_id = ?")) {
            statement.setLong(1, cursor);
            statement.setString(2, edgeId);
            statement.executeUpdate();
        }
    }
}
