package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.contracts.EventEnvelope;
import vn.tmc.edge.edgesync.api.CommittedEvent;
import vn.tmc.edge.edgesync.api.EventDraft;
import vn.tmc.edge.edgesync.domain.BootstrapCache.Identity;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Immutable event rows and their sequence allocation within an existing write transaction. */
final class SqliteEvents {
    private SqliteEvents() {}

    static CommittedEvent insert(Connection connection, EventDraft draft, Instant now) throws SQLException, IOException {
        long nextSequence = nextSequence(connection, draft);
        UUID eventId = UuidV7.generate(now);
        CanonicalPayload payload = CanonicalPayload.encode(draft.payload());
        EventEnvelope envelope = new EventEnvelope(eventId, draft.eventType(), draft.schemaVersion(),
                draft.edgeId().toString(), draft.gateId().toString(), draft.factoryId().toString(), nextSequence,
                draft.correlationId().toString(), draft.occurredAt(), payload.sha256(), payload.text());
        try (var statement = connection.prepareStatement("""
                INSERT INTO local_events (event_id, event_type, schema_version, edge_id, gate_id, factory_id,
                    sequence_no, correlation_id, occurred_at, payload_sha256, payload_text, committed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, eventId.toString());
            statement.setString(2, envelope.eventType());
            statement.setInt(3, envelope.schemaVersion());
            statement.setString(4, envelope.edgeId());
            statement.setString(5, envelope.gateId());
            statement.setString(6, envelope.factoryId());
            statement.setLong(7, envelope.sequenceNo());
            statement.setString(8, envelope.correlationId());
            statement.setLong(9, envelope.occurredAt().toEpochMilli());
            statement.setString(10, envelope.payloadSha256());
            statement.setString(11, envelope.payload());
            statement.setLong(12, now.toEpochMilli());
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("INSERT INTO event_delivery (event_id, state) VALUES (?, 'PENDING')")) {
            statement.setString(1, eventId.toString());
            statement.executeUpdate();
        }
        return new CommittedEvent(envelope, now);
    }

    private static long nextSequence(Connection connection, EventDraft draft) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO edge_runtime_state (edge_id, factory_id) VALUES (?, ?)
                ON CONFLICT (edge_id) DO NOTHING
                """)) {
            statement.setString(1, draft.edgeId().toString());
            statement.setString(2, draft.factoryId().toString());
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("""
                SELECT last_sequence_no FROM edge_runtime_state WHERE edge_id = ? AND factory_id = ?
                """)) {
            statement.setString(1, draft.edgeId().toString());
            statement.setString(2, draft.factoryId().toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Stored Edge factory does not match the event draft");
                }
                return result.getLong(1) + 1;
            }
        }
    }

    static void advanceSequence(Connection connection, EventEnvelope envelope) throws SQLException {
        try (var statement = connection.prepareStatement("""
                UPDATE edge_runtime_state SET last_sequence_no = ? WHERE edge_id = ? AND last_sequence_no = ?
                """)) {
            statement.setLong(1, envelope.sequenceNo());
            statement.setString(2, envelope.edgeId());
            statement.setLong(3, envelope.sequenceNo() - 1);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Edge sequence state was not advanced");
            }
        }
    }

    static Optional<CommittedEvent> find(Connection connection, UUID eventId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT *
                FROM local_events WHERE event_id = ?
                """)) {
            statement.setString(1, eventId.toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CommittedEvent(envelope(result), Instant.ofEpochMilli(result.getLong("committed_at"))));
            }
        }
    }

    static Optional<Identity> findIdentity(Connection connection, UUID eventId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT edge_id, factory_id FROM local_events WHERE event_id = ?")) {
            statement.setString(1, eventId.toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Identity(UUID.fromString(result.getString(1)), UUID.fromString(result.getString(2))));
            }
        }
    }

    static EventEnvelope envelope(ResultSet row) throws SQLException {
        return new EventEnvelope(UUID.fromString(row.getString("event_id")), row.getString("event_type"),
                row.getInt("schema_version"), row.getString("edge_id"), row.getString("gate_id"), row.getString("factory_id"),
                row.getLong("sequence_no"), row.getString("correlation_id"), Instant.ofEpochMilli(row.getLong("occurred_at")),
                row.getString("payload_sha256"), row.getString("payload_text"));
    }
}
