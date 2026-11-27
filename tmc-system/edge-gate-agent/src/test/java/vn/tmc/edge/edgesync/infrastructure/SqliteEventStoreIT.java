package vn.tmc.edge.edgesync.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.tmc.contracts.AttendancePayload;
import vn.tmc.edge.edgesync.api.EventDraft;
import vn.tmc.edge.edgesync.api.EventPayload;
import vn.tmc.edge.edgesync.api.EventStoreException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteSchemaIT.scalar;

class SqliteEventStoreIT {
    static final Instant OCCURRED_AT = Instant.parse("2026-11-17T02:00:00.000Z");
    static final Instant COMMITTED_AT = OCCURRED_AT.plusMillis(650);
    static final UUID EMPLOYEE_ID = fixtureId(11);
    static final UUID JTI = fixtureId(12);
    static final UUID COMMAND_ID = fixtureId(13);
    static final UUID EDGE_ID = fixtureId(21);
    static final UUID GATE_ID = fixtureId(22);
    static final UUID FACTORY_ID = fixtureId(23);
    static final UUID SCAN_ID = fixtureId(24);
    static final String EXPECTED_HASH = "5889002999b73c833ff21dfe5ca348309c44f227c59e8b981d80e9e4a96b9c02";

    @TempDir
    Path tempDir;

    @Test
    void append_commitsEventEnvelopeAndPendingDelivery() throws SQLException {
        var store = store();
        EventDraft draft = deniedDraft(EDGE_ID, SCAN_ID);
        var committed = store.append(draft);
        var envelope = committed.envelope();
        assertEquals("access.attempt.denied", envelope.eventType());
        assertEquals(1, envelope.schemaVersion());
        assertEquals(EDGE_ID, envelope.edgeId());
        assertEquals(GATE_ID, envelope.gateId());
        assertEquals(FACTORY_ID, envelope.factoryId());
        assertEquals(OCCURRED_AT, envelope.occurredAt());
        assertEquals(COMMITTED_AT, committed.committedAt());
        assertEquals(committed, store.findEvent(envelope.eventId()).orElseThrow());
        try (var connection = new SqliteDatabase(file()).open()) {
            assertEquals(envelope.eventId().toString(), scalar(connection, "SELECT event_id FROM event_delivery"));
            assertEquals("PENDING", scalar(connection, "SELECT state FROM event_delivery"));
        }
    }

    @Test
    void append_allocatesSequenceStartingAtOne() {
        assertEquals(1, store().append(deniedDraft(EDGE_ID, SCAN_ID)).envelope().sequenceNo());
    }

    @Test
    void append_twoEvents_allocatesConsecutiveSequences() {
        var store = store();
        var first = store.append(deniedDraft(EDGE_ID, SCAN_ID)).envelope();
        var second = store.append(deniedDraft(EDGE_ID, fixtureId(25))).envelope();
        var otherEdge = store.append(deniedDraft(fixtureId(26), fixtureId(27))).envelope();
        assertEquals(1, first.sequenceNo());
        assertEquals(2, second.sequenceNo());
        assertEquals(1, otherEdge.sequenceNo());
        assertNotEquals(first.eventId(), second.eventId());
    }

    @Test
    void restart_preservesSequenceAndCommittedEvent() {
        var first = store().append(deniedDraft(EDGE_ID, SCAN_ID));
        var reopened = store();
        var next = reopened.append(deniedDraft(EDGE_ID, fixtureId(25)));
        assertEquals(1, first.envelope().sequenceNo());
        assertEquals(2, next.envelope().sequenceNo());
        assertEquals(first, reopened.findEvent(first.envelope().eventId()).orElseThrow());
    }

    @Test
    void append_commitFailure_rollsBackEventDeliveryAndSequence() throws SQLException {
        var store = store();
        store.append(deniedDraft(EDGE_ID, SCAN_ID));
        try (var connection = new SqliteDatabase(file()).open(); var statement = connection.createStatement()) {
            // A deferred command FK detects this fault only when SQLite executes COMMIT.
            statement.execute("""
                    CREATE TRIGGER fail_append_commit AFTER UPDATE OF last_sequence_no ON edge_runtime_state
                    BEGIN
                        INSERT INTO local_scan_outcomes (scan_id, gate_id, input_fingerprint, decision,
                            reason_code, command_id, command_status, occurred_at, mode, retain_until)
                        VALUES ('fault', 'gate', '%s', 'ALLOW', 'OK', 'missing', 'NOT_SENT', 0, 'OFFLINE', 0);
                    END
                    """.formatted("0".repeat(64)));
            var failure = assertThrows(EventStoreException.class, () -> store.append(deniedDraft(EDGE_ID, fixtureId(25))));
            assertStorageError(failure, org.sqlite.SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY);
            assertEquals("1", scalar(connection, "SELECT count(*) FROM local_events"));
            assertEquals("1", scalar(connection, "SELECT count(*) FROM event_delivery"));
            assertEquals("0", scalar(connection, "SELECT count(*) FROM local_scan_outcomes"));
            assertEquals("1", scalar(connection, "SELECT last_sequence_no FROM edge_runtime_state"));
            statement.execute("DROP TRIGGER fail_append_commit");
        }
        assertEquals(2, store.append(deniedDraft(EDGE_ID, fixtureId(25))).envelope().sequenceNo());
    }

    @Test
    void append_preservesCanonicalPayloadAndHashAcrossReopen() {
        var original = store().append(deniedDraft(EDGE_ID, SCAN_ID)).envelope();
        var reopened = store().findEvent(original.eventId()).orElseThrow().envelope();
        assertArrayEquals(original.payload().getBytes(StandardCharsets.UTF_8),
                reopened.payload().getBytes(StandardCharsets.UTF_8));
        assertEquals(original.payloadSha256(), reopened.payloadSha256());
    }

    @Test
    void append_scanOrigin_usesScanIdAsCorrelationId() {
        assertEquals(SCAN_ID, store().append(deniedDraft(EDGE_ID, SCAN_ID)).envelope().correlationId());
    }

    @Test
    void append_generatesValidUuidV7() {
        UUID eventId = store().append(deniedDraft(EDGE_ID, SCAN_ID)).envelope().eventId();
        assertEquals(7, eventId.version());
        assertEquals(2, eventId.variant());
        assertEquals(COMMITTED_AT.toEpochMilli(), eventId.getMostSignificantBits() >>> 16);
    }

    @Test
    void append_cannotBypassAttendanceTransaction() {
        var draft = new EventDraft(EDGE_ID, GATE_ID, FACTORY_ID, SCAN_ID, OCCURRED_AT,
                new EventPayload.Attendance(attendance(JTI, COMMAND_ID)));
        var store = store();
        assertThrows(IllegalArgumentException.class, () -> store.append(draft));
        assertEquals(1, store.append(deniedDraft(EDGE_ID, SCAN_ID)).envelope().sequenceNo());
    }

    static AttendancePayload attendance(UUID jti, UUID commandId) {
        return new AttendancePayload(EMPLOYEE_ID.toString(), AttendancePayload.Direction.IN,
                AttendancePayload.Source.OFFLINE, jti.toString(), "qr-key-2026-11",
                Instant.parse("2026-11-17T01:59:45.000Z"), commandId.toString(), null, null);
    }

    static void assertStorageError(EventStoreException failure, org.sqlite.SQLiteErrorCode code) {
        assertEquals(EventStoreException.Code.STORAGE_UNAVAILABLE, failure.code());
        assertEquals(code, assertInstanceOf(org.sqlite.SQLiteException.class, failure.getCause()).getResultCode());
    }

    static UUID fixtureId(int suffix) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(suffix));
    }

    private EventDraft deniedDraft(UUID edgeId, UUID scanId) {
        var payload = new EventPayload.AccessDenied(EMPLOYEE_ID, "OUTSIDE_POLICY", JTI, "qr-key-2026-11",
                EventPayload.ScanMode.OFFLINE);
        return new EventDraft(edgeId, GATE_ID, FACTORY_ID, scanId, OCCURRED_AT, payload);
    }

    private Path file() {
        return tempDir.resolve("edge.db");
    }

    private SqliteStore store() {
        return new SqliteStore(file(), Clock.fixed(COMMITTED_AT, ZoneOffset.UTC));
    }
}
