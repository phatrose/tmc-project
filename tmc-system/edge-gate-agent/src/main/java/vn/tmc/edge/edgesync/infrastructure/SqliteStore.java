package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.edge.edgesync.api.LocalEventStorePort;
import vn.tmc.contracts.EventEnvelope;
import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.edge.edgesync.api.CommittedEvent;
import vn.tmc.edge.edgesync.api.EventDraft;
import vn.tmc.edge.edgesync.api.EventPayload;
import vn.tmc.edge.edgesync.api.EventStoreException;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static vn.tmc.edge.edgesync.api.EventStoreException.Code.STORAGE_UNAVAILABLE;

/** File-backed Edge event store. Each operation owns and closes its SQLite connection. */
public final class SqliteStore implements LocalEventStorePort {
    private final SqliteDatabase database;
    private final Clock clock;

    public SqliteStore(Path databaseFile, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        database = new SqliteDatabase(databaseFile);
    }

    @Override
    public CommittedEvent append(EventDraft draft) {
        if (draft.payload() instanceof EventPayload.Attendance) {
            throw new IllegalArgumentException("Attendance requires reserveAndAppend");
        }
        return database.write(connection -> {
            var event = SqliteEvents.insert(connection, draft, now());
            SqliteEvents.advanceSequence(connection, event.envelope());
            return event;
        });
    }

    public Optional<CommittedEvent> findEvent(UUID eventId) {
        try (var connection = database.open()) {
            return SqliteEvents.find(connection, eventId);
        } catch (SQLException failure) {
            throw new EventStoreException(STORAGE_UNAVAILABLE, failure);
        }
    }

    private Instant now() {
        return Instant.ofEpochMilli(clock.millis());
    }

    @Override
    public void reserveAndAppend(EventEnvelope event, String jti) {
        throw new UnsupportedOperationException("TODO: atomic replay reservation and persistence");
    }
    @Override
    public SyncBatchRequest pendingBatch() {
        throw new UnsupportedOperationException("TODO: durable batch selection");
    }
    @Override
    public void applyAcknowledgements(SyncBatchAck acknowledgement) {
        throw new UnsupportedOperationException("TODO: per-event ACK and cursor transaction");
    }
}
