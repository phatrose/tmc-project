package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.contracts.AttendancePayload;
import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.edge.edgesync.api.CommandIntent;
import vn.tmc.edge.edgesync.api.CommittedEvent;
import vn.tmc.edge.edgesync.api.EventDraft;
import vn.tmc.edge.edgesync.api.EventPayload;
import vn.tmc.edge.edgesync.api.EventPayload.CommandMode;
import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.api.LocalEventStorePort;
import vn.tmc.edge.edgesync.api.ReplayReservation;
import vn.tmc.edge.edgesync.api.ScanInputFingerprint;
import vn.tmc.edge.edgesync.api.ScanOutcome;
import vn.tmc.edge.edgesync.domain.SyncJournal;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static vn.tmc.edge.edgesync.api.EventStoreException.Code.*;

/** File-backed Edge event store. Each operation owns and closes its SQLite connection. */
public final class SqliteStore implements LocalEventStorePort, SyncJournal {
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
    public CommittedEvent reserveAndAppend(UUID scanId, EventDraft draft, ReplayReservation reservation) {
        Objects.requireNonNull(scanId, "scanId");
        if (!scanId.equals(draft.correlationId())) {
            throw new IllegalArgumentException("The event correlationId must equal scanId");
        }
        Objects.requireNonNull(reservation, "reservation");
        return database.write(connection -> {
            var previous = SqliteScans.findOutcome(connection, scanId, reservation.inputFingerprint());
            if (previous.isPresent()) {
                var outcome = previous.orElseThrow();
                if (!outcome.gateId().equals(draft.gateId())) {
                    throw new EventStoreException(IDEMPOTENCY_CONFLICT);
                }
                if (outcome.eventId() == null) {
                    throw new SQLException("Stored scan has no committed event");
                }
                return SqliteEvents.find(connection, outcome.eventId())
                        .orElseThrow(() -> new SQLException("Stored scan event is missing"));
            }

            var attendance = requireQrAttendance(draft, reservation);
            var command = qrCommand(draft.gateId(), attendance.commandId());
            // A reused jti is REPLAY even when the same employee also matches the business window.
            if (SqliteScans.hasReplay(connection, reservation)) {
                throw new EventStoreException(REPLAY);
            }
            Instant now = now();
            if (SqliteScans.hasRecentAllow(connection, reservation, draft.occurredAt())) {
                throw new EventStoreException(DUPLICATE_SCAN);
            }

            SqliteScans.reserveQr(connection, reservation, now);
            var event = SqliteEvents.insert(connection, draft, now);
            UUID eventId = event.envelope().eventId();
            SqliteScans.linkQrEvent(connection, reservation.jti(), eventId);
            SqliteScans.insertAllow(connection, scanId, reservation, ScanMode.valueOf(attendance.source().name()),
                    draft.occurredAt(), eventId, command, now);
            SqliteEvents.advanceSequence(connection, event.envelope());
            return new CommittedEvent(event.envelope(), event.committedAt(), Optional.of(command));
        });
    }

    @Override
    public Optional<ScanOutcome> findScanOutcome(UUID scanId, ScanInputFingerprint fingerprint) {
        Objects.requireNonNull(scanId, "scanId");
        Objects.requireNonNull(fingerprint, "fingerprint");
        try (var connection = database.open()) {
            return SqliteScans.findOutcome(connection, scanId, fingerprint);
        } catch (SQLException failure) {
            throw new EventStoreException(STORAGE_UNAVAILABLE, failure);
        }
    }

    private static AttendancePayload requireQrAttendance(EventDraft draft, ReplayReservation reservation) {
        if (!(draft.payload() instanceof EventPayload.Attendance payload)
                || reservation.kind() != ReplayReservation.Kind.QR) {
            throw new IllegalArgumentException("This transaction requires QR attendance and a QR reservation");
        }
        var attendance = payload.value();
        if (attendance.source() == null || attendance.source() == AttendancePayload.Source.MANUAL
                || attendance.kid() == null || attendance.kid().isBlank() || attendance.tokenIssuedAt() == null
                || attendance.operatorId() != null || attendance.reason() != null
                || !reservation.jti().toString().equals(attendance.jti())
                || reservation.employeeId() == null || !reservation.employeeId().toString().equals(attendance.employeeId())
                || reservation.direction() == null || reservation.direction() != attendance.direction()
                || !reservation.gateId().equals(draft.gateId())) {
            throw new IllegalArgumentException("QR attendance and replay reservation must describe the same verified scan");
        }
        return attendance;
    }

    private static CommandIntent qrCommand(UUID gateId, String suppliedId) {
        if (suppliedId == null) {
            throw new IllegalArgumentException("commandId is required");
        }
        UUID commandId = UUID.fromString(suppliedId);
        if (commandId.version() != 4 || commandId.variant() != 2 || !commandId.toString().equals(suppliedId)) {
            throw new IllegalArgumentException("commandId must be a lowercase UUIDv4");
        }
        return new CommandIntent(commandId, gateId, null, null, CommandMode.QR);
    }

    @Override
    public SyncBatchRequest pendingBatch(int maxEvents) {
        return pendingBatch(null, maxEvents);
    }

    public SyncBatchRequest pendingBatch(UUID edgeId, int maxEvents) {
        if (maxEvents < 1 || maxEvents > SyncBatchRequest.MAX_EVENTS) {
            throw new IllegalArgumentException("Batch limits must be between 1 and 50");
        }
        return database.write(connection -> SqliteBatches.pending(connection,
                edgeId == null ? null : edgeId.toString(), maxEvents, now()));
    }
    @Override
    public void applyAcknowledgements(SyncBatchAck acknowledgement) {
        applyAcknowledgements(acknowledgement == null ? null : acknowledgement.syncBatchId(), acknowledgement);
    }

    public void applyAcknowledgements(UUID expectedBatchId, SyncBatchAck acknowledgement) {
        database.write(connection -> {
            SqliteBatches.apply(connection, expectedBatchId, acknowledgement);
            return null;
        });
    }

    @Override
    public Attempt claim(UUID edgeId) {
        return database.write(connection -> SqliteSyncJournal.claim(connection, edgeId, now()));
    }

    @Override
    public void complete(Attempt attempt, SyncBatchAck response) {
        database.write(connection -> {
            SqliteSyncJournal.requireInFlight(connection, attempt);
            SqliteBatches.apply(connection, attempt.request().syncBatchId(), response);
            return null;
        });
    }

    @Override
    public void defer(Attempt attempt, Schedule schedule) {
        database.write(connection -> {
            SqliteSyncJournal.defer(connection, attempt, schedule);
            return null;
        });
    }

    @Override
    public Schedule schedule(UUID edgeId) {
        try (var connection = database.open()) {
            return SqliteSyncJournal.schedule(connection, edgeId);
        } catch (SQLException failure) {
            throw new EventStoreException(STORAGE_UNAVAILABLE, failure);
        }
    }

    @Override
    public void resume(UUID edgeId) {
        database.write(connection -> {
            SqliteSyncJournal.resume(connection, edgeId);
            return null;
        });
    }

    public void recover() {
        database.write(connection -> {
            SqliteSyncJournal.recover(connection, now());
            return null;
        });
    }
}
