package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.contracts.AttendancePayload;
import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.edge.edgesync.api.CommandIntent;
import vn.tmc.edge.edgesync.api.CommittedCommand;
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
import vn.tmc.edge.edgesync.domain.BootstrapCache;
import vn.tmc.edge.edgesync.domain.BootstrapCache.Identity;
import vn.tmc.edge.edgesync.domain.EdgeStatus.Queue;
import vn.tmc.edge.edgesync.domain.OperationsJournal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static vn.tmc.edge.edgesync.api.EventStoreException.Code.*;

/** File-backed Edge event store. Each operation owns and closes its SQLite connection. */
public final class SqliteStore implements LocalEventStorePort, SyncJournal, OperationsJournal {
    private final SqliteDatabase database;
    private final Clock clock;
    private final Identity identity;

    SqliteStore(Path databaseFile, Clock clock) {
        this(new SqliteDatabase(databaseFile), clock, null);
    }

    private SqliteStore(SqliteDatabase database, Clock clock, Identity identity) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.database = database;
        this.identity = identity;
    }

    /** Provisioning must supply a newly allocated Edge identity. Existing files are never overwritten. */
    public static SqliteStore provisionNew(Path file, Clock clock, Identity newIdentity) {
        Objects.requireNonNull(newIdentity);
        Objects.requireNonNull(clock);
        try {
            Files.createFile(file);
        } catch (IOException failure) {
            throw new EventStoreException(STORAGE_UNAVAILABLE, failure);
        }
        new SqliteDatabase(file).write(db -> {
            try (var statement = db.prepareStatement("INSERT INTO edge_runtime_state(edge_id, factory_id) VALUES (?, ?)")) {
                statement.setString(1, newIdentity.edgeId().toString());
                statement.setString(2, newIdentity.factoryId().toString());
                statement.executeUpdate();
            }
            return null;
        });
        return openProvisioned(file, clock, newIdentity);
    }

    public static SqliteStore openProvisioned(Path file, Clock clock, Identity identity) {
        Objects.requireNonNull(identity);
        return new SqliteStore(new SqliteDatabase(file, identity), clock, identity);
    }

    @Override
    public CommittedEvent append(EventDraft draft) {
        requireScope(draft.edgeId(), draft.factoryId());
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
        return database.read(connection -> SqliteEvents.find(connection, eventId));
    }

    private Instant now() {
        return Instant.ofEpochMilli(clock.millis());
    }

    @Override
    public CommittedEvent reserveAndAppend(UUID scanId, EventDraft draft, ReplayReservation reservation) {
        requireScope(draft.edgeId(), draft.factoryId());
        Objects.requireNonNull(scanId, "scanId");
        if (!scanId.equals(draft.correlationId())) {
            throw new IllegalArgumentException("The event correlationId must equal scanId");
        }
        Objects.requireNonNull(reservation, "reservation");
        return database.write(connection -> {
            var previous = SqliteScans.findRetry(connection, scanId, reservation.inputFingerprint(), draft.gateId(),
                    ScanOutcome.Decision.ALLOW);
            if (previous.isPresent()) {
                var outcome = previous.orElseThrow();
                if (outcome.eventId() == null) {
                    throw new SQLException("Stored scan has no committed event");
                }
                return SqliteEvents.find(connection, outcome.eventId())
                        .orElseThrow(() -> new SQLException("Stored scan event is missing"));
            }

            var attendance = requireAttendance(draft, reservation);
            var command = attendanceCommand(draft.gateId(), attendance, reservation);
            // A reused jti is REPLAY even when the same employee also matches the business window.
            SqliteScans.rejectReplay(connection, reservation);
            Instant now = now();
            if (SqliteScans.hasRecentAllow(connection, reservation, draft.occurredAt())) {
                throw new EventStoreException(DUPLICATE_SCAN);
            }

            SqliteScans.reserve(connection, reservation, now);
            var event = SqliteEvents.insert(connection, draft, now);
            UUID eventId = event.envelope().eventId();
            SqliteScans.linkEvent(connection, reservation, eventId);
            var mode = attendance.source() == AttendancePayload.Source.OFFLINE ? ScanMode.OFFLINE : ScanMode.ONLINE;
            SqliteScans.insertCommand(connection, scanId, reservation, mode,
                    draft.occurredAt(), eventId, command, now);
            SqliteEvents.advanceSequence(connection, event.envelope());
            return new CommittedEvent(event.envelope(), event.committedAt(), Optional.of(command));
        });
    }

    @Override
    public CommittedCommand commitEmergency(UUID scanId, CommandIntent command, ReplayReservation reservation) {
        requireUuidV4(scanId, "scanId");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(reservation, "reservation");
        return database.write(connection -> {
            var previous = SqliteScans.findRetry(connection, scanId, reservation.inputFingerprint(), command.gateId(),
                    ScanOutcome.Decision.EMERGENCY);
            if (previous.isPresent()) {
                var outcome = previous.orElseThrow();
                if (outcome.commandId() == null || outcome.eventId() != null) {
                    throw new SQLException("Stored emergency scan has an invalid command outcome");
                }
                return new CommittedCommand(outcome.commandId(), outcome.occurredAt(), Optional.empty());
            }

            if (command.mode() != CommandMode.EMERGENCY || reservation.kind() != ReplayReservation.Kind.ACTION
                    || reservation.employeeId() != null || !reservation.gateId().equals(command.gateId())) {
                throw new IllegalArgumentException("EMERGENCY requires a matching ACTION reservation without an employee");
            }
            requireUuidV4(command.commandId(), "commandId");
            requireUuidV4(command.operatorId(), "operatorId");
            requireOverrideReason(command.reason());
            SqliteScans.rejectReplay(connection, reservation);
            Instant now = now();
            SqliteScans.reserve(connection, reservation, now);
            SqliteScans.insertCommand(connection, scanId, reservation, ScanMode.ONLINE, now, null, command, now);
            return new CommittedCommand(command.commandId(), now, Optional.of(command));
        });
    }

    @Override
    public Optional<ScanOutcome> findScanOutcome(UUID scanId, ScanInputFingerprint fingerprint) {
        Objects.requireNonNull(scanId, "scanId");
        Objects.requireNonNull(fingerprint, "fingerprint");
        return database.read(connection -> SqliteScans.findOutcome(connection, scanId, fingerprint));
    }

    private static AttendancePayload requireAttendance(EventDraft draft, ReplayReservation reservation) {
        if (!(draft.payload() instanceof EventPayload.Attendance payload)) {
            throw new IllegalArgumentException("This transaction requires attendance");
        }
        var attendance = payload.value();
        if (attendance.source() == null
                || reservation.employeeId() == null || !reservation.employeeId().toString().equals(attendance.employeeId())
                || reservation.direction() == null || reservation.direction() != attendance.direction()
                || !reservation.gateId().equals(draft.gateId())) {
            throw new IllegalArgumentException("Attendance and replay reservation must describe the same verified scan");
        }
        return attendance;
    }

    private static CommandIntent attendanceCommand(UUID gateId, AttendancePayload attendance, ReplayReservation reservation) {
        UUID commandId = requireUuidV4(attendance.commandId(), "commandId");
        if (attendance.source() == AttendancePayload.Source.MANUAL) {
            if (reservation.kind() != ReplayReservation.Kind.ACTION || attendance.jti() != null
                    || attendance.kid() != null || attendance.tokenIssuedAt() != null) {
                throw new IllegalArgumentException("MANUAL attendance requires an ACTION reservation and null QR fields");
            }
            requireUuidV4(reservation.employeeId(), "employeeId");
            return new CommandIntent(commandId, gateId, requireUuidV4(attendance.operatorId(), "operatorId"),
                    requireOverrideReason(attendance.reason()), CommandMode.MANUAL);
        }
        if (reservation.kind() != ReplayReservation.Kind.QR || attendance.kid() == null || attendance.kid().isBlank()
                || attendance.tokenIssuedAt() == null || attendance.operatorId() != null || attendance.reason() != null
                || !reservation.jti().toString().equals(attendance.jti())) {
            throw new IllegalArgumentException("QR attendance requires matching QR claims and null operator fields");
        }
        return new CommandIntent(commandId, gateId, null, null, CommandMode.QR);
    }

    private static UUID requireUuidV4(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        UUID id = requireUuidV4(UUID.fromString(value), field);
        if (!id.toString().equals(value)) {
            throw new IllegalArgumentException(field + " must be a lowercase UUIDv4");
        }
        return id;
    }

    private static UUID requireUuidV4(UUID id, String field) {
        if (id == null || id.version() != 4 || id.variant() != 2) {
            throw new IllegalArgumentException(field + " must be a UUIDv4");
        }
        return id;
    }

    private static String requireOverrideReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Override reason is required");
        }
        int length = reason.codePointCount(0, reason.length());
        if (length < 10 || length > 500) {
            throw new IllegalArgumentException("Override reason must contain 10 to 500 characters");
        }
        return reason;
    }

    @Override
    public SyncBatchRequest pendingBatch(int maxEvents) {
        return pendingBatch(null, maxEvents);
    }

    public SyncBatchRequest pendingBatch(UUID edgeId, int maxEvents) {
        if (edgeId != null) {
            requireEdge(edgeId);
        }
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
        requireEdge(edgeId);
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
        requireEdge(edgeId);
        return database.read(connection -> SqliteSyncJournal.schedule(connection, edgeId));
    }

    @Override
    public void resume(UUID edgeId) {
        requireEdge(edgeId);
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

    public BootstrapCache bootstrapCache(BootstrapCache.Identity identity) {
        requireScope(identity.edgeId(), identity.factoryId());
        return new SqliteBootstrapCache(database, identity, clock);
    }

    @Override
    public Queue queue(UUID edgeId) {
        requireEdge(edgeId);
        return database.read(connection -> SqliteOperations.queue(connection, edgeId));
    }

    @Override
    public void recordCentralContact(UUID edgeId, Instant observedAt) {
        requireEdge(edgeId);
        database.write(connection -> {
            SqliteOperations.recordContact(connection, edgeId, observedAt);
            return null;
        });
    }

    @Override
    public void cleanup() {
        database.write(connection -> {
            SqliteOperations.cleanup(connection, now());
            return null;
        });
    }

    private void requireScope(UUID edgeId, UUID factoryId) {
        requireEdge(edgeId);
        if (identity != null && !identity.factoryId().equals(factoryId)) {
            throw new IllegalArgumentException("Factory differs from the provisioned identity");
        }
    }

    private void requireEdge(UUID edgeId) {
        Objects.requireNonNull(edgeId, "edgeId");
        if (identity != null && !identity.edgeId().equals(edgeId)) {
            throw new IllegalArgumentException("Edge differs from the provisioned identity");
        }
    }
}
