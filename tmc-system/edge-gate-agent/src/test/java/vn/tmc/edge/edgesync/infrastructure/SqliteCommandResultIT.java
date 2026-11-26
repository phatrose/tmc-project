package vn.tmc.edge.edgesync.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sqlite.SQLiteErrorCode;
import vn.tmc.contracts.AttendancePayload;
import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.edge.edgesync.api.CommandCompletion;
import vn.tmc.edge.edgesync.api.CommandIntent;
import vn.tmc.edge.edgesync.api.CommittedEvent;
import vn.tmc.edge.edgesync.api.EventDraft;
import vn.tmc.edge.edgesync.api.EventPayload;
import vn.tmc.edge.edgesync.api.EventPayload.CommandMode;
import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;
import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.api.EventStoreException.Code;
import vn.tmc.edge.edgesync.api.ReplayReservation;
import vn.tmc.edge.edgesync.api.ScanInputFingerprint;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.time.Clock.fixed;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteBootstrapIT.IDENTITY;
import static vn.tmc.edge.edgesync.infrastructure.SqliteEventStoreIT.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteSchemaIT.scalar;

class SqliteCommandResultIT {
    private static final UUID OPERATOR = fixtureId(401);
    private static final String REASON = "Verified on-site by duty supervisor";
    private static final String GRANT = "synthetic-action-grant";
    private static final Instant COMPLETED_AT = COMMITTED_AT.plusMillis(120);
    private static final Instant EXPIRY = OCCURRED_AT.plusSeconds(60);

    @TempDir
    Path tempDir;
    private SqliteStore store;

    @BeforeEach
    void provision() {
        store = SqliteStore.provisionNew(file(), fixed(COMMITTED_AT, UTC), IDENTITY);
    }

    @Test
    void recordCommandResult_unlockAccepted_updatesCommandOutcomeAndAppendsResultAtomically() throws Exception {
        var attendance = qrAllow(SCAN_ID, JTI, COMMAND_ID);
        var fingerprint = ScanInputFingerprint.ofQr(GATE_ID, "qr." + JTI);
        var resultStore = reopenAt(COMPLETED_AT);

        var committed = resultStore.recordCommandResult(SCAN_ID,
                new CommandCompletion(COMMAND_ID, CommandStatus.UNLOCK_ACCEPTED, COMPLETED_AT, null));

        assertTrue(committed.newCommand().isEmpty());
        assertEquals("gate.command.result", committed.envelope().eventType());
        assertEquals(CommandStatus.UNLOCK_ACCEPTED,
                resultStore.findScanOutcome(SCAN_ID, fingerprint).orElseThrow().commandStatus());
        assertEquals("UNLOCK_ACCEPTED", read("SELECT status FROM local_gate_commands"));
        assertEquals(Long.toString(COMPLETED_AT.toEpochMilli()), read("SELECT completed_at FROM local_gate_commands"));
        assertNull(read("SELECT code FROM local_gate_commands"));
        assertEquals(committed.envelope().eventId().toString(), read("SELECT result_event_id FROM local_gate_commands"));
        assertEquals("UNLOCK_ACCEPTED", read("SELECT command_status FROM local_scan_outcomes"));
        assertEquals("2", read("SELECT count(*) FROM local_events"));
        assertEquals("2", read("SELECT count(*) FROM event_delivery"));
        assertEquals(attendance.envelope().eventId().toString(),
                new ObjectMapper().readTree(read("SELECT payload_text FROM local_events WHERE sequence_no = 2"))
                        .get("attendanceEventId").asText());
    }

    @Test
    void recordCommandResult_failed_updatesCommandOutcomeAndAppendsResultAtomically() throws Exception {
        qrAllow(SCAN_ID, JTI, COMMAND_ID);
        var fingerprint = ScanInputFingerprint.ofQr(GATE_ID, "qr." + JTI);
        var resultStore = reopenAt(COMPLETED_AT);

        var committed = resultStore.recordCommandResult(SCAN_ID,
                new CommandCompletion(COMMAND_ID, CommandStatus.FAILED, COMPLETED_AT, "RELAY_FAULT"));

        assertTrue(committed.newCommand().isEmpty());
        assertEquals(CommandStatus.FAILED,
                resultStore.findScanOutcome(SCAN_ID, fingerprint).orElseThrow().commandStatus());
        assertEquals("FAILED", read("SELECT status FROM local_gate_commands"));
        assertEquals(Long.toString(COMPLETED_AT.toEpochMilli()), read("SELECT completed_at FROM local_gate_commands"));
        assertEquals("RELAY_FAULT", read("SELECT code FROM local_gate_commands"));
        assertEquals("FAILED", read("SELECT command_status FROM local_scan_outcomes"));
        var payload = new ObjectMapper().readTree(read("SELECT payload_text FROM local_events WHERE sequence_no = 2"));
        assertEquals("FAILED", payload.get("status").asText());
        assertEquals("RELAY_FAULT", payload.get("code").asText());
        assertEquals(COMPLETED_AT.toString(), payload.get("completedAt").asText());
    }

    @Test
    void recordCommandResult_timeout_updatesCommandOutcomeAndAppendsResultAtomically() throws Exception {
        qrAllow(SCAN_ID, JTI, COMMAND_ID);
        var fingerprint = ScanInputFingerprint.ofQr(GATE_ID, "qr." + JTI);
        var resultStore = reopenAt(COMPLETED_AT);

        var committed = resultStore.recordCommandResult(SCAN_ID,
                new CommandCompletion(COMMAND_ID, CommandStatus.TIMEOUT, COMPLETED_AT, "ADAPTER_TIMEOUT"));

        assertTrue(committed.newCommand().isEmpty());
        assertEquals(CommandStatus.TIMEOUT,
                resultStore.findScanOutcome(SCAN_ID, fingerprint).orElseThrow().commandStatus());
        assertEquals("TIMEOUT", read("SELECT status FROM local_gate_commands"));
        assertEquals("ADAPTER_TIMEOUT", read("SELECT code FROM local_gate_commands"));
        assertEquals("TIMEOUT", read("SELECT command_status FROM local_scan_outcomes"));
        var payload = new ObjectMapper().readTree(read("SELECT payload_text FROM local_events WHERE sequence_no = 2"));
        assertEquals("TIMEOUT", payload.get("status").asText());
        assertEquals("ADAPTER_TIMEOUT", payload.get("code").asText());
        assertEquals(committed.envelope().eventId().toString(), read("SELECT result_event_id FROM local_gate_commands"));
    }

    @Test
    void recordCommandResult_qrResultReferencesAttendanceEvent() throws Exception {
        var attendance = qrAllow(SCAN_ID, JTI, COMMAND_ID);
        var result = reopenAt(COMPLETED_AT).recordCommandResult(SCAN_ID,
                new CommandCompletion(COMMAND_ID, CommandStatus.UNLOCK_ACCEPTED, COMPLETED_AT, null));

        var envelope = result.envelope();
        assertEquals("gate.command.result", envelope.eventType());
        assertEquals(1, envelope.schemaVersion());
        assertEquals(EDGE_ID.toString(), envelope.edgeId());
        assertEquals(GATE_ID.toString(), envelope.gateId());
        assertEquals(FACTORY_ID.toString(), envelope.factoryId());
        assertEquals(SCAN_ID.toString(), envelope.correlationId());
        assertEquals(COMPLETED_AT, envelope.occurredAt());

        var persisted = read("SELECT payload_text FROM local_events WHERE event_id = '" + envelope.eventId() + "'");
        assertEquals(envelope.payload(), persisted);
        assertEquals(envelope.payloadSha256(),
                read("SELECT payload_sha256 FROM local_events WHERE event_id = '" + envelope.eventId() + "'"));
        var json = new ObjectMapper().readTree(persisted);
        assertEquals(COMMAND_ID.toString(), json.get("commandId").asText());
        assertEquals(attendance.envelope().eventId().toString(), json.get("attendanceEventId").asText());
        assertEquals("QR", json.get("mode").asText());
        assertTrue(json.has("operatorId") && json.get("operatorId").isNull());
        assertTrue(json.has("reason") && json.get("reason").isNull());
        assertEquals("UNLOCK_ACCEPTED", json.get("status").asText());
        assertEquals(COMPLETED_AT.toString(), json.get("completedAt").asText());
        assertTrue(json.has("code") && json.get("code").isNull());
        assertFalse(persisted.contains("qr." + JTI));
        assertFalse(persisted.contains(JTI.toString()));
    }

    @Test
    void recordCommandResult_manualResultReferencesAttendanceEventAndPreservesOperatorReason() throws Exception {
        var attendance = manualAllow(SCAN_ID, JTI, COMMAND_ID);
        var result = reopenAt(COMPLETED_AT).recordCommandResult(SCAN_ID,
                new CommandCompletion(COMMAND_ID, CommandStatus.UNLOCK_ACCEPTED, COMPLETED_AT, null));

        var persisted = read("SELECT payload_text FROM local_events WHERE event_id = '" + result.envelope().eventId() + "'");
        var json = new ObjectMapper().readTree(persisted);
        assertEquals(COMMAND_ID.toString(), json.get("commandId").asText());
        assertEquals(attendance.envelope().eventId().toString(), json.get("attendanceEventId").asText());
        assertEquals("MANUAL", json.get("mode").asText());
        assertEquals(OPERATOR.toString(), json.get("operatorId").asText());
        assertEquals(REASON, json.get("reason").asText());
        assertEquals("UNLOCK_ACCEPTED", json.get("status").asText());
        assertEquals(COMPLETED_AT.toString(), json.get("completedAt").asText());
        assertTrue(json.has("code") && json.get("code").isNull());
        assertFalse(persisted.contains(GRANT));
        assertFalse(persisted.contains(JTI.toString()));
    }

    @Test
    void recordCommandResult_emergencyResultHasNullAttendanceEventId() throws Exception {
        emergencyCommand(SCAN_ID, JTI, COMMAND_ID);
        var result = reopenAt(COMPLETED_AT).recordCommandResult(SCAN_ID,
                new CommandCompletion(COMMAND_ID, CommandStatus.UNLOCK_ACCEPTED, COMPLETED_AT, null));

        assertEquals(1, result.envelope().sequenceNo());
        assertEquals(SCAN_ID.toString(), result.envelope().correlationId());
        assertEquals(EDGE_ID.toString(), result.envelope().edgeId());
        assertEquals(GATE_ID.toString(), result.envelope().gateId());
        assertEquals(FACTORY_ID.toString(), result.envelope().factoryId());
        assertEquals("1", read("SELECT count(*) FROM local_events"));
        var json = new ObjectMapper().readTree(read("SELECT payload_text FROM local_events"));
        assertEquals(COMMAND_ID.toString(), json.get("commandId").asText());
        assertTrue(json.has("attendanceEventId") && json.get("attendanceEventId").isNull());
        assertEquals("EMERGENCY", json.get("mode").asText());
        assertEquals(OPERATOR.toString(), json.get("operatorId").asText());
        assertEquals(REASON, json.get("reason").asText());
        assertEquals("UNLOCK_ACCEPTED", json.get("status").asText());
        assertFalse(read("SELECT payload_text FROM local_events").contains(GRANT));
    }

    @Test
    void recordCommandResult_createsPendingDeliveryForResultEvent() throws Exception {
        var attendance = qrAllow(SCAN_ID, JTI, COMMAND_ID);
        var result = reopenAt(COMPLETED_AT).recordCommandResult(SCAN_ID,
                new CommandCompletion(COMMAND_ID, CommandStatus.UNLOCK_ACCEPTED, COMPLETED_AT, null));

        assertEquals("PENDING", read("SELECT state FROM event_delivery WHERE event_id = '"
                + result.envelope().eventId() + "'"));
        var batch = store.pendingBatch(50);
        assertEquals(List.of(attendance.envelope(), result.envelope()), batch.events());
    }

    @Test
    void recordCommandResult_advancesSequenceExactlyOnce() throws Exception {
        var attendance = qrAllow(SCAN_ID, JTI, COMMAND_ID);
        var result = reopenAt(COMPLETED_AT).recordCommandResult(SCAN_ID,
                new CommandCompletion(COMMAND_ID, CommandStatus.UNLOCK_ACCEPTED, COMPLETED_AT, null));

        assertEquals(1, attendance.envelope().sequenceNo());
        assertEquals(2, result.envelope().sequenceNo());
        assertEquals("2", read("SELECT last_sequence_no FROM edge_runtime_state"));
        assertEquals(3, qrAllow(fixtureId(410), fixtureId(411), fixtureId(412), OCCURRED_AT.plusSeconds(31))
                .envelope().sequenceNo());
    }

    @Test
    void sameCommandResultRetry_isIdempotentAndConsumesNoNewSequence() throws Exception {
        qrAllow(SCAN_ID, JTI, COMMAND_ID);
        var completion = new CommandCompletion(COMMAND_ID, CommandStatus.FAILED, COMPLETED_AT, "JAMMED");
        var first = reopenAt(COMPLETED_AT).recordCommandResult(SCAN_ID, completion);
        var retry = reopenAt(COMPLETED_AT.plusSeconds(5)).recordCommandResult(SCAN_ID, completion);

        assertEquals(first.envelope(), retry.envelope());
        assertEquals(first.committedAt(), retry.committedAt());
        assertTrue(retry.newCommand().isEmpty());
        assertEquals("2", read("SELECT count(*) FROM local_events"));
        assertEquals("2", read("SELECT count(*) FROM event_delivery"));
        assertEquals("2", read("SELECT last_sequence_no FROM edge_runtime_state"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "completedAt", "code", "afterRecovery"})
    void conflictingCommandResultRetry_isRejected(String conflict) throws Exception {
        qrAllow(SCAN_ID, JTI, COMMAND_ID);
        if (conflict.equals("afterRecovery")) {
            store.recover();
            var error = assertThrows(EventStoreException.class, () -> store.recordCommandResult(SCAN_ID,
                    new CommandCompletion(COMMAND_ID, CommandStatus.TIMEOUT, COMPLETED_AT, "RECOVERY_UNKNOWN")));
            assertEquals(Code.IDEMPOTENCY_CONFLICT, error.code());
            assertEquals("1", read("SELECT count(*) FROM local_events"));
            assertEquals("RECOVERY_UNKNOWN", read("SELECT code FROM local_gate_commands"));
            return;
        }

        var original = new CommandCompletion(COMMAND_ID, CommandStatus.UNLOCK_ACCEPTED, COMPLETED_AT, null);
        var first = store.recordCommandResult(SCAN_ID, original);
        var conflicting = switch (conflict) {
            case "status" -> new CommandCompletion(COMMAND_ID, CommandStatus.FAILED, COMPLETED_AT, null);
            case "completedAt" -> new CommandCompletion(COMMAND_ID, CommandStatus.UNLOCK_ACCEPTED,
                    COMPLETED_AT.plusMillis(1), null);
            case "code" -> new CommandCompletion(COMMAND_ID, CommandStatus.UNLOCK_ACCEPTED, COMPLETED_AT, "EXTRA");
            default -> throw new AssertionError(conflict);
        };
        var error = assertThrows(EventStoreException.class, () -> store.recordCommandResult(SCAN_ID, conflicting));
        assertEquals(Code.IDEMPOTENCY_CONFLICT, error.code());
        assertEquals("2", read("SELECT count(*) FROM local_events"));
        assertEquals("2", read("SELECT last_sequence_no FROM edge_runtime_state"));
        assertEquals("UNLOCK_ACCEPTED", read("SELECT status FROM local_gate_commands"));
        assertEquals(first.envelope().eventId().toString(), read("SELECT result_event_id FROM local_gate_commands"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrongCommand", "unknownScan", "denyScan"})
    void wrongCommandForScan_isRejected(String caseName) throws Exception {
        qrAllow(SCAN_ID, JTI, COMMAND_ID);
        UUID targetScan = caseName.equals("unknownScan") ? fixtureId(420) : SCAN_ID;
        UUID targetCommand = caseName.equals("wrongCommand") ? fixtureId(421) : COMMAND_ID;
        if (caseName.equals("denyScan")) {
            targetScan = fixtureId(422);
            try (var db = DriverManager.getConnection("jdbc:sqlite:" + file()); var statement = db.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO local_scan_outcomes (scan_id, gate_id, input_fingerprint, employee_id, direction,
                            decision, reason_code, event_id, command_id, command_status, occurred_at, mode, retain_until)
                        VALUES ('%s', '%s', '%s', NULL, NULL, 'DENY', 'INVALID_TOKEN', NULL, NULL, 'NOT_SENT', %d, 'OFFLINE', %d)
                        """.formatted(targetScan, GATE_ID, "a".repeat(64),
                        OCCURRED_AT.toEpochMilli(), COMMITTED_AT.plusSeconds(60).toEpochMilli()));
            }
        }

        UUID scan = targetScan;
        UUID command = targetCommand;
        var error = assertThrows(EventStoreException.class, () -> store.recordCommandResult(scan,
                new CommandCompletion(command, CommandStatus.UNLOCK_ACCEPTED, COMPLETED_AT, null)));
        assertEquals(Code.IDEMPOTENCY_CONFLICT, error.code());
        assertEquals("1", read("SELECT count(*) FROM local_events"));
        assertEquals("NOT_SENT", read("SELECT status FROM local_gate_commands"));
    }

    @Test
    void commandResultCommitFailure_rollsBackCommandOutcomeResultEventDeliveryAndSequence() throws Exception {
        qrAllow(SCAN_ID, JTI, COMMAND_ID);
        execute("""
                CREATE TRIGGER fail_command_result_commit BEFORE UPDATE OF last_sequence_no ON edge_runtime_state
                BEGIN
                    SELECT RAISE(ABORT, 'test write failure after updating command result');
                END
                """);

        var completion = new CommandCompletion(COMMAND_ID, CommandStatus.UNLOCK_ACCEPTED, COMPLETED_AT, null);
        var failure = assertThrows(EventStoreException.class, () -> store.recordCommandResult(SCAN_ID, completion));
        assertStorageError(failure, SQLiteErrorCode.SQLITE_CONSTRAINT_TRIGGER);

        assertEquals("NOT_SENT", read("SELECT status FROM local_gate_commands"));
        assertNull(read("SELECT completed_at FROM local_gate_commands"));
        assertNull(read("SELECT code FROM local_gate_commands"));
        assertNull(read("SELECT result_event_id FROM local_gate_commands"));
        assertEquals("NOT_SENT", read("SELECT command_status FROM local_scan_outcomes"));
        assertEquals("1", read("SELECT count(*) FROM local_events"));
        assertEquals("1", read("SELECT count(*) FROM event_delivery"));
        assertEquals("1", read("SELECT last_sequence_no FROM edge_runtime_state"));

        execute("DROP TRIGGER fail_command_result_commit");
        var committed = store.recordCommandResult(SCAN_ID, completion);
        assertEquals(2, committed.envelope().sequenceNo());
        assertEquals("UNLOCK_ACCEPTED", read("SELECT status FROM local_gate_commands"));
        assertEquals("UNLOCK_ACCEPTED", read("SELECT command_status FROM local_scan_outcomes"));
    }

    @Test
    void restartAfterCompletedCommand_preservesTerminalResultAndDoesNotCreateAnotherEvent() throws Exception {
        qrAllow(SCAN_ID, JTI, COMMAND_ID);
        var completion = new CommandCompletion(COMMAND_ID, CommandStatus.UNLOCK_ACCEPTED, COMPLETED_AT, null);
        var original = reopenAt(COMPLETED_AT).recordCommandResult(SCAN_ID, completion);

        var restarted = reopenAt(COMPLETED_AT.plusSeconds(30));
        var loaded = restarted.findEvent(original.envelope().eventId()).orElseThrow().envelope();
        assertArrayEquals(original.envelope().payload().getBytes(StandardCharsets.UTF_8),
                loaded.payload().getBytes(StandardCharsets.UTF_8));
        assertEquals(original.envelope().payloadSha256(), loaded.payloadSha256());

        var retry = restarted.recordCommandResult(SCAN_ID, completion);
        assertEquals(original.envelope(), retry.envelope());
        assertEquals("2", read("SELECT count(*) FROM local_events"));
        assertEquals("2", read("SELECT last_sequence_no FROM edge_runtime_state"));
        assertEquals("UNLOCK_ACCEPTED", read("SELECT status FROM local_gate_commands"));
        assertEquals("UNLOCK_ACCEPTED", read("SELECT command_status FROM local_scan_outcomes"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNLOCK_ACCEPTED", "FAILED", "TIMEOUT"})
    void recovery_doesNotOverwriteAlreadyTerminalCommand(CommandStatus status) throws Exception {
        qrAllow(SCAN_ID, JTI, COMMAND_ID);
        String code = status == CommandStatus.UNLOCK_ACCEPTED ? null : "DEVICE_" + status.name();
        var committed = store.recordCommandResult(SCAN_ID,
                new CommandCompletion(COMMAND_ID, status, COMPLETED_AT, code));

        var restarted = reopenAt(COMPLETED_AT.plusSeconds(60));
        restarted.recover();

        assertEquals(status.name(), read("SELECT status FROM local_gate_commands"));
        assertEquals(Long.toString(COMPLETED_AT.toEpochMilli()), read("SELECT completed_at FROM local_gate_commands"));
        assertEquals(code, read("SELECT code FROM local_gate_commands"));
        assertEquals(committed.envelope().eventId().toString(), read("SELECT result_event_id FROM local_gate_commands"));
        assertEquals(status.name(), read("SELECT command_status FROM local_scan_outcomes"));
        assertEquals("2", read("SELECT count(*) FROM local_events"));
    }

    @Test
    void concurrentSameCommandResult_isIdempotentAndConsumesNoSecondSequence() throws Exception {
        qrAllow(SCAN_ID, JTI, COMMAND_ID);
        var completion = new CommandCompletion(COMMAND_ID, CommandStatus.UNLOCK_ACCEPTED, COMPLETED_AT, null);
        var firstStore = reopenAt(COMPLETED_AT);
        var secondStore = reopenAt(COMPLETED_AT);
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<CommittedEvent> left = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return firstStore.recordCommandResult(SCAN_ID, completion);
            };
            Callable<CommittedEvent> right = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return secondStore.recordCommandResult(SCAN_ID, completion);
            };
            var leftFuture = pool.submit(left);
            var rightFuture = pool.submit(right);
            var leftResult = leftFuture.get(10, TimeUnit.SECONDS);
            var rightResult = rightFuture.get(10, TimeUnit.SECONDS);
            assertEquals(leftResult.envelope(), rightResult.envelope());
        }
        assertEquals("2", read("SELECT count(*) FROM local_events"));
        assertEquals("2", read("SELECT last_sequence_no FROM edge_runtime_state"));
    }

    @Test
    void recordCommandResult_rejectsInvalidCompletionInputs() {
        var v7 = UUID.fromString("01a1e73b-8500-7000-8000-000000000001");
        assertThrows(IllegalArgumentException.class,
                () -> new CommandCompletion(v7, CommandStatus.UNLOCK_ACCEPTED, COMPLETED_AT, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CommandCompletion(COMMAND_ID, CommandStatus.NOT_SENT, COMPLETED_AT, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CommandCompletion(COMMAND_ID, CommandStatus.FAILED, COMPLETED_AT, "   "));
        assertThrows(IllegalArgumentException.class,
                () -> store.recordCommandResult(v7, new CommandCompletion(COMMAND_ID, CommandStatus.UNLOCK_ACCEPTED, COMPLETED_AT, null)));
    }

    private CommittedEvent qrAllow(UUID scanId, UUID jti, UUID commandId) {
        return qrAllow(scanId, jti, commandId, OCCURRED_AT);
    }

    private CommittedEvent qrAllow(UUID scanId, UUID jti, UUID commandId, Instant occurredAt) {
        var draft = new EventDraft(EDGE_ID, GATE_ID, FACTORY_ID, scanId, occurredAt,
                new EventPayload.Attendance(attendance(jti, commandId)));
        var reservation = new ReplayReservation(ReplayReservation.Kind.QR, jti, EXPIRY, EMPLOYEE_ID, Direction.IN,
                GATE_ID, ScanInputFingerprint.ofQr(GATE_ID, "qr." + jti));
        return store.reserveAndAppend(scanId, draft, reservation);
    }

    private CommittedEvent manualAllow(UUID scanId, UUID jti, UUID commandId) {
        var payload = new AttendancePayload(EMPLOYEE_ID.toString(), Direction.IN, AttendancePayload.Source.MANUAL,
                null, null, null, commandId.toString(), OPERATOR.toString(), REASON);
        var draft = new EventDraft(EDGE_ID, GATE_ID, FACTORY_ID, scanId, OCCURRED_AT,
                new EventPayload.Attendance(payload));
        var reservation = new ReplayReservation(ReplayReservation.Kind.ACTION, jti, EXPIRY, EMPLOYEE_ID, Direction.IN,
                GATE_ID, ScanInputFingerprint.ofAction(GATE_ID, CommandMode.MANUAL, EMPLOYEE_ID, REASON, GRANT));
        return store.reserveAndAppend(scanId, draft, reservation);
    }

    private void emergencyCommand(UUID scanId, UUID jti, UUID commandId) {
        var intent = new CommandIntent(commandId, GATE_ID, OPERATOR, REASON, CommandMode.EMERGENCY);
        var reservation = new ReplayReservation(ReplayReservation.Kind.ACTION, jti, EXPIRY, null, null,
                GATE_ID, ScanInputFingerprint.ofAction(GATE_ID, CommandMode.EMERGENCY, null, REASON, GRANT));
        store.commitEmergency(scanId, intent, reservation);
    }

    private SqliteStore reopenAt(Instant at) {
        return SqliteStore.openProvisioned(file(), fixed(at, UTC), IDENTITY);
    }

    private Path file() {
        return tempDir.resolve("edge.db");
    }

    private void execute(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file());
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String read(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file())) {
            return scalar(connection, sql);
        }
    }
}
