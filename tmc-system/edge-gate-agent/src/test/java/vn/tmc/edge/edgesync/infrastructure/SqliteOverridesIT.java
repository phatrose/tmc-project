package vn.tmc.edge.edgesync.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tmc.contracts.AttendancePayload;
import vn.tmc.edge.edgesync.api.CommandIntent;
import vn.tmc.edge.edgesync.api.CommittedCommand;
import vn.tmc.edge.edgesync.api.CommittedEvent;
import vn.tmc.edge.edgesync.api.EventDraft;
import vn.tmc.edge.edgesync.api.EventPayload;
import vn.tmc.edge.edgesync.api.EventPayload.CommandMode;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.api.EventStoreException.Code;
import vn.tmc.edge.edgesync.api.LocalEventStorePort;
import vn.tmc.edge.edgesync.api.ReplayReservation;
import vn.tmc.edge.edgesync.api.ScanInputFingerprint;
import vn.tmc.edge.edgesync.api.ScanOutcome;

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

class SqliteOverridesIT {
    private static final UUID OPERATOR = fixtureId(301);
    private static final String REASON = "Verified on-site by the duty manager";
    private static final String CHANGED_REASON = "Checked again by the duty manager";
    private static final String GRANT = "synthetic-action-grant";
    private static final Instant EXPIRY = OCCURRED_AT.plusSeconds(60);

    @TempDir
    Path tempDir;
    private SqliteStore store;

    @BeforeEach
    void provision() {
        store = SqliteStore.provisionNew(file(), fixed(COMMITTED_AT, UTC), IDENTITY);
    }

    @Test
    void manualAllow_persistsActionReplayEventDeliveryScanAndCommandAtomically() throws Exception {
        var grant = manualGrant(JTI, REASON);
        var committed = manual(store, SCAN_ID, COMMAND_ID, OCCURRED_AT, grant);
        var command = committed.newCommand().orElseThrow();
        assertEquals(COMMAND_ID, command.commandId());
        assertEquals(CommandMode.MANUAL, command.mode());
        assertEquals(OPERATOR, command.operatorId());
        assertEquals(REASON, command.reason());

        assertCounts(1, 1);
        assertEquals("ACTION", read("SELECT kind FROM local_replay_cache"));
        assertEquals(JTI.toString(), read("SELECT jti FROM local_replay_cache"));
        assertEquals(Long.toString(EXPIRY.plusSeconds(5).toEpochMilli()), read("SELECT purge_after FROM local_replay_cache"));
        assertEquals(committed.envelope().eventId().toString(), read("SELECT event_id FROM local_replay_cache"));
        assertEquals("PENDING", read("SELECT state FROM event_delivery"));
        assertEquals(grant.inputFingerprint().sha256(), read("SELECT input_fingerprint FROM local_scan_outcomes"));
        assertEquals("MANUAL", read("SELECT mode FROM local_gate_commands"));
        assertEquals(OPERATOR.toString(), read("SELECT operator_id FROM local_gate_commands"));
        assertEquals(REASON, read("SELECT reason FROM local_gate_commands"));
        assertEquals("NOT_SENT", read("SELECT status FROM local_gate_commands"));
        var outcome = store.findScanOutcome(SCAN_ID, grant.inputFingerprint()).orElseThrow();
        assertEquals(ScanOutcome.Decision.ALLOW, outcome.decision());
        assertEquals("OK", outcome.reasonCode());
        assertEquals(COMMAND_ID, outcome.commandId());
        assertEquals(committed.envelope().eventId(), outcome.eventId());
    }

    @Test
    void manualAllow_attendancePayloadHasManualSourceAndNullQrFields() throws Exception {
        var committed = manual(store, SCAN_ID, COMMAND_ID, OCCURRED_AT, manualGrant(JTI, REASON));
        assertEquals("attendance.event.recorded", read("SELECT event_type FROM local_events"));
        var persisted = read("SELECT payload_text FROM local_events");
        var payload = new ObjectMapper().readTree(persisted);
        assertEquals("MANUAL", payload.get("source").asText());
        assertEquals(OPERATOR.toString(), payload.get("operatorId").asText());
        assertEquals(REASON, payload.get("reason").asText());
        assertEquals(EMPLOYEE_ID.toString(), payload.get("employeeId").asText());
        assertEquals("IN", payload.get("direction").asText());
        assertEquals(COMMAND_ID.toString(), payload.get("commandId").asText());
        for (String qrField : List.of("jti", "kid", "tokenIssuedAt")) {
            assertTrue(payload.has(qrField) && payload.get(qrField).isNull(), qrField);
        }
        assertFalse(persisted.contains(JTI.toString()));
        assertFalse(persisted.contains(GRANT));
        assertEquals(committed.envelope().payload(), persisted);
        assertEquals(committed.envelope().payloadSha256(), read("SELECT payload_sha256 FROM local_events"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"operator", "reason", "blank", "short", "long", "jti", "kid", "issuedAt", "kind"})
    void manualAllow_rejectsInvalidOperatorReasonAndCredentialFields(String invalid) throws Exception {
        var payload = new AttendancePayload(EMPLOYEE_ID.toString(), AttendancePayload.Direction.IN,
                AttendancePayload.Source.MANUAL, invalid.equals("jti") ? JTI.toString() : null,
                invalid.equals("kid") ? "unexpected" : null, invalid.equals("issuedAt") ? OCCURRED_AT : null,
                COMMAND_ID.toString(), invalid.equals("operator") ? null : OPERATOR.toString(),
                switch (invalid) {
                    case "reason" -> null;
                    case "blank" -> " ".repeat(20);
                    case "short" -> "short";
                    case "long" -> "a".repeat(501);
                    default -> REASON;
                });
        var grant = invalid.equals("kind") ? qrReservation(JTI) : manualGrant(JTI, REASON);
        assertThrows(IllegalArgumentException.class, () -> store.reserveAndAppend(SCAN_ID,
                new EventDraft(EDGE_ID, GATE_ID, FACTORY_ID, SCAN_ID, OCCURRED_AT, new EventPayload.Attendance(payload)), grant));
        assertCounts(0, 0);
    }

    @Test
    void sameActionJti_exactlyOneManualTransactionWins() throws Exception {
        var other = reopen();
        var grant = manualGrant(JTI, REASON);
        assertOneWins(() -> manual(store, SCAN_ID, COMMAND_ID, OCCURRED_AT, grant),
                () -> manual(other, fixtureId(302), fixtureId(303), OCCURRED_AT, grant));
        assertCounts(1, 1);
    }

    @Test
    void sameManualScanIdSameInput_returnsStoredOutcomeWithoutNewCommand() throws Exception {
        var grant = manualGrant(JTI, REASON);
        var original = manual(store, SCAN_ID, COMMAND_ID, OCCURRED_AT, grant);
        var outcome = store.findScanOutcome(SCAN_ID, grant.inputFingerprint()).orElseThrow();
        var retry = manual(store, SCAN_ID, fixtureId(303), OCCURRED_AT.plusSeconds(1), grant);
        assertEquals(original.envelope(), retry.envelope());
        assertEquals(original.committedAt(), retry.committedAt());
        assertTrue(retry.newCommand().isEmpty());
        assertEquals(outcome, store.findScanOutcome(SCAN_ID, grant.inputFingerprint()).orElseThrow());
        assertCounts(1, 1);
    }

    @Test
    void sameManualScanIdDifferentInput_returnsIdempotencyConflict() throws Exception {
        manual(store, SCAN_ID, COMMAND_ID, OCCURRED_AT, manualGrant(JTI, REASON));
        assertRejected(Code.IDEMPOTENCY_CONFLICT, () -> manual(store, SCAN_ID, COMMAND_ID, OCCURRED_AT,
                manualGrant(JTI, CHANGED_REASON), CHANGED_REASON));
        assertEquals(REASON, read("SELECT reason FROM local_gate_commands"));
        assertCounts(1, 1);
    }

    @Test
    void sameActionJtiWithDifferentManualScan_isReplayBeforeBusinessDuplicate() throws Exception {
        var grant = manualGrant(JTI, REASON);
        manual(store, SCAN_ID, COMMAND_ID, OCCURRED_AT, grant);
        assertRejected(Code.REPLAY, () -> manual(store, fixtureId(302), fixtureId(303), OCCURRED_AT, grant));
        assertCounts(1, 1);
    }

    @Test
    void manualBusinessDuplicate_usesOccurrenceTimeAndAllowsAtExactly30Seconds() throws Exception {
        manual(store, SCAN_ID, COMMAND_ID, OCCURRED_AT, manualGrant(JTI, REASON));
        var delayed = reopenAt(OCCURRED_AT.plusSeconds(31));
        var grant = manualGrant(fixtureId(304), REASON);
        assertRejected(Code.DUPLICATE_SCAN, () -> manual(delayed, fixtureId(302), fixtureId(303),
                OCCURRED_AT.plusSeconds(29), grant));
        assertCounts(1, 1);
        manual(delayed, fixtureId(302), fixtureId(303), OCCURRED_AT.plusSeconds(30), grant);
        assertCounts(2, 2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void manualAndQr_participateInSameBusinessDuplicateWindow(boolean manualFirst) throws Exception {
        // The same jti can exist in both namespaces. The attendance decision still shares one duplicate window.
        if (manualFirst) {
            manual(store, SCAN_ID, COMMAND_ID, OCCURRED_AT, manualGrant(JTI, REASON));
            assertRejected(Code.DUPLICATE_SCAN, () -> qr(fixtureId(302), fixtureId(303), OCCURRED_AT.plusSeconds(1)));
        } else {
            qr(SCAN_ID, COMMAND_ID, OCCURRED_AT);
            assertRejected(Code.DUPLICATE_SCAN, () -> manual(store, fixtureId(302), fixtureId(303),
                    OCCURRED_AT.plusSeconds(1), manualGrant(JTI, REASON)));
        }
        assertCounts(1, 1);
    }

    @Test
    void manualCommitFailure_rollsBackReplayEventDeliveryCommandAndSequence() throws Exception {
        failCommit(CommandMode.MANUAL);
        assertRejected(Code.STORAGE_UNAVAILABLE,
                () -> manual(store, SCAN_ID, COMMAND_ID, OCCURRED_AT, manualGrant(JTI, REASON)));
        assertCounts(0, 0);
        execute("DROP TRIGGER fail_override_commit");
        assertEquals(1, manual(store, SCAN_ID, COMMAND_ID, OCCURRED_AT, manualGrant(JTI, REASON)).envelope().sequenceNo());
    }

    @Test
    void manualRestart_preservesCommandIntentAndDoesNotReissueCommand() throws Exception {
        var grant = manualGrant(JTI, REASON);
        var original = manual(store, SCAN_ID, COMMAND_ID, OCCURRED_AT, grant);
        var restarted = reopen();
        restarted.recover();
        var retry = manual(restarted, SCAN_ID, fixtureId(303), OCCURRED_AT, grant);
        assertEquals(original.envelope(), retry.envelope());
        assertTrue(retry.newCommand().isEmpty());
        assertEquals("TIMEOUT", read("SELECT status FROM local_gate_commands"));
        assertEquals("RECOVERY_UNKNOWN", read("SELECT code FROM local_gate_commands"));
        assertEquals(REASON, read("SELECT reason FROM local_gate_commands"));
        assertCounts(1, 1);
    }

    @Test
    void commitEmergency_persistsActionReplayScanAndCommandWithoutAttendance() throws Exception {
        var grant = emergencyGrant(JTI, REASON);
        var committed = emergency(store, SCAN_ID, COMMAND_ID, grant, REASON);
        assertEquals(COMMAND_ID, committed.commandId());
        assertEquals(COMMITTED_AT, committed.committedAt());
        assertEquals(new CommandIntent(COMMAND_ID, GATE_ID, OPERATOR, REASON, CommandMode.EMERGENCY),
                committed.newCommand().orElseThrow());
        assertCounts(0, 1);
        assertEquals("ACTION", read("SELECT kind FROM local_replay_cache"));
        assertEquals(JTI.toString(), read("SELECT jti FROM local_replay_cache"));
        assertEquals(Long.toString(EXPIRY.plusSeconds(5).toEpochMilli()), read("SELECT purge_after FROM local_replay_cache"));
        assertNull(read("SELECT employee_id FROM local_replay_cache"));
        assertNull(read("SELECT event_id FROM local_replay_cache"));
        assertNull(read("SELECT employee_id FROM local_scan_outcomes"));
        assertEquals(grant.inputFingerprint().sha256(), read("SELECT input_fingerprint FROM local_scan_outcomes"));
        assertEquals("EMERGENCY", read("SELECT mode FROM local_gate_commands"));
        assertEquals(OPERATOR.toString(), read("SELECT operator_id FROM local_gate_commands"));
        assertEquals(REASON, read("SELECT reason FROM local_gate_commands"));
        var outcome = store.findScanOutcome(SCAN_ID, grant.inputFingerprint()).orElseThrow();
        assertEquals(ScanOutcome.Decision.EMERGENCY, outcome.decision());
        assertEquals("EMERGENCY", outcome.reasonCode());
        assertEquals(EventPayload.CommandStatus.NOT_SENT, outcome.commandStatus());
        assertEquals(GATE_ID, outcome.gateId());
        assertEquals(COMMAND_ID, outcome.commandId());
        assertNull(outcome.eventId());
        assertNull(store.pendingBatch(50));
    }

    @Test
    void commitEmergency_doesNotConsumeAttendanceSequence() throws Exception {
        qr(SCAN_ID, COMMAND_ID, OCCURRED_AT);
        emergency(store, fixtureId(302), fixtureId(303), emergencyGrant(fixtureId(304), REASON), REASON);
        assertEquals("1", read("SELECT last_sequence_no FROM edge_runtime_state"));
        assertEquals(2, manual(store, fixtureId(305), fixtureId(306), OCCURRED_AT.plusSeconds(30),
                manualGrant(fixtureId(307), REASON)).envelope().sequenceNo());
        assertCounts(2, 3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"kind", "employee", "gate", "mode", "operator", "reason", "command"})
    void emergency_rejectsMalformedActionAndIntent(String invalid) throws Exception {
        var grant = switch (invalid) {
            case "kind" -> qrReservation(JTI);
            case "employee" -> manualGrant(JTI, REASON);
            default -> emergencyGrant(JTI, REASON);
        };
        var command = new CommandIntent(invalid.equals("command") ? UUID.fromString("00000000-0000-0000-0000-000000000000")
                        : COMMAND_ID, invalid.equals("gate") ? fixtureId(304) : GATE_ID,
                invalid.equals("operator") ? null : OPERATOR, invalid.equals("reason") ? null : REASON,
                invalid.equals("mode") ? CommandMode.MANUAL : CommandMode.EMERGENCY);
        assertThrows(IllegalArgumentException.class, () -> store.commitEmergency(SCAN_ID, command, grant));
        assertCounts(0, 0);
    }

    @Test
    void sameEmergencyActionJti_isReplay() throws Exception {
        var grant = emergencyGrant(JTI, REASON);
        emergency(store, SCAN_ID, COMMAND_ID, grant, REASON);
        assertRejected(Code.REPLAY, () -> emergency(store, fixtureId(302), fixtureId(303), grant, REASON));
        assertCounts(0, 1);
    }

    @Test
    void concurrentSameEmergencyActionJti_exactlyOneWins() throws Exception {
        var other = reopen();
        var grant = emergencyGrant(JTI, REASON);
        assertOneWins(() -> emergency(store, SCAN_ID, COMMAND_ID, grant, REASON),
                () -> emergency(other, fixtureId(302), fixtureId(303), grant, REASON));
        assertCounts(0, 1);
    }

    @Test
    void manualAndEmergency_shareTheActionReplayNamespace() throws Exception {
        emergency(store, SCAN_ID, COMMAND_ID, emergencyGrant(JTI, REASON), REASON);
        assertRejected(Code.REPLAY, () -> manual(store, fixtureId(302), fixtureId(303),
                OCCURRED_AT, manualGrant(JTI, REASON)));
        assertCounts(0, 1);
    }

    @Test
    void sameEmergencyScanSameInput_returnsStoredOutcomeWithoutNewCommand() throws Exception {
        var grant = emergencyGrant(JTI, REASON);
        var first = emergency(store, SCAN_ID, COMMAND_ID, grant, REASON);
        var outcome = store.findScanOutcome(SCAN_ID, grant.inputFingerprint()).orElseThrow();
        var retry = emergency(store, SCAN_ID, fixtureId(303), grant, REASON);
        assertEquals(first.commandId(), retry.commandId());
        assertEquals(first.committedAt(), retry.committedAt());
        assertTrue(retry.newCommand().isEmpty());
        assertEquals(outcome, store.findScanOutcome(SCAN_ID, grant.inputFingerprint()).orElseThrow());
        assertCounts(0, 1);
    }

    @Test
    void sameEmergencyScanDifferentInput_isIdempotencyConflict() throws Exception {
        emergency(store, SCAN_ID, COMMAND_ID, emergencyGrant(JTI, REASON), REASON);
        assertRejected(Code.IDEMPOTENCY_CONFLICT, () -> emergency(store, SCAN_ID, COMMAND_ID,
                emergencyGrant(JTI, CHANGED_REASON), CHANGED_REASON));
        assertRejected(Code.IDEMPOTENCY_CONFLICT, () -> manual(store, SCAN_ID, COMMAND_ID, OCCURRED_AT,
                manualGrant(JTI, REASON)));
        assertEquals(REASON, read("SELECT reason FROM local_gate_commands"));
        assertCounts(0, 1);
    }

    @Test
    void emergencyCommitFailure_rollsBackEverything() throws Exception {
        failCommit(CommandMode.EMERGENCY);
        var grant = emergencyGrant(JTI, REASON);
        assertRejected(Code.STORAGE_UNAVAILABLE, () -> emergency(store, SCAN_ID, COMMAND_ID, grant, REASON));
        assertCounts(0, 0);
        execute("DROP TRIGGER fail_override_commit");
        assertTrue(emergency(store, SCAN_ID, COMMAND_ID, grant, REASON).newCommand().isPresent());
        assertCounts(0, 1);
    }

    @Test
    void restartAfterEmergencyCommit_doesNotMakeCommandEligibleAgain() throws Exception {
        var grant = emergencyGrant(JTI, REASON);
        var first = emergency(store, SCAN_ID, COMMAND_ID, grant, REASON);
        var restarted = reopen();
        var retry = emergency(restarted, SCAN_ID, fixtureId(303), grant, REASON);
        assertEquals(first.committedAt(), retry.committedAt());
        assertEquals(COMMAND_ID, retry.commandId());
        assertTrue(retry.newCommand().isEmpty());
        assertCounts(0, 1);
    }

    @Test
    void recoveryOfUncertainEmergencyCommand_marksTimeoutAndNeverUnlocksAgain() throws Exception {
        var grant = emergencyGrant(JTI, REASON);
        emergency(store, SCAN_ID, COMMAND_ID, grant, REASON);
        var restarted = reopen();
        restarted.recover();
        var retry = emergency(restarted, SCAN_ID, fixtureId(303), grant, REASON);
        assertTrue(retry.newCommand().isEmpty());
        assertEquals(EventPayload.CommandStatus.TIMEOUT,
                restarted.findScanOutcome(SCAN_ID, grant.inputFingerprint()).orElseThrow().commandStatus());
        assertEquals("TIMEOUT", read("SELECT status FROM local_gate_commands"));
        assertEquals("RECOVERY_UNKNOWN", read("SELECT code FROM local_gate_commands"));
        assertCounts(0, 1);
    }

    private static ReplayReservation emergencyGrant(UUID jti, String reason) {
        return new ReplayReservation(ReplayReservation.Kind.ACTION, jti, EXPIRY, null, null,
                GATE_ID, ScanInputFingerprint.ofAction(GATE_ID, CommandMode.EMERGENCY, null, reason, GRANT));
    }

    private static CommittedCommand emergency(LocalEventStorePort port, UUID scanId, UUID commandId,
                                              ReplayReservation grant, String reason) {
        return port.commitEmergency(scanId, new CommandIntent(commandId, GATE_ID, OPERATOR, reason,
                CommandMode.EMERGENCY), grant);
    }

    private CommittedEvent qr(UUID scanId, UUID commandId, Instant at) {
        return store.reserveAndAppend(scanId, new EventDraft(EDGE_ID, GATE_ID, FACTORY_ID, scanId, at,
                new EventPayload.Attendance(attendance(JTI, commandId))), qrReservation(JTI));
    }

    private static ReplayReservation qrReservation(UUID jti) {
        return new ReplayReservation(ReplayReservation.Kind.QR, jti, EXPIRY, EMPLOYEE_ID, AttendancePayload.Direction.IN,
                GATE_ID, ScanInputFingerprint.ofQr(GATE_ID, "synthetic-qr"));
    }

    private static ReplayReservation manualGrant(UUID jti, String reason) {
        return new ReplayReservation(ReplayReservation.Kind.ACTION, jti, EXPIRY, EMPLOYEE_ID, AttendancePayload.Direction.IN,
                GATE_ID, ScanInputFingerprint.ofAction(GATE_ID, CommandMode.MANUAL, EMPLOYEE_ID, reason, GRANT));
    }

    private static CommittedEvent manual(LocalEventStorePort port, UUID scanId, UUID commandId, Instant at,
                                         ReplayReservation grant) {
        return manual(port, scanId, commandId, at, grant, REASON);
    }

    private static CommittedEvent manual(LocalEventStorePort port, UUID scanId, UUID commandId, Instant at,
                                         ReplayReservation grant, String reason) {
        var payload = new AttendancePayload(EMPLOYEE_ID.toString(), AttendancePayload.Direction.IN,
                AttendancePayload.Source.MANUAL, null, null, null, commandId.toString(), OPERATOR.toString(), reason);
        return port.reserveAndAppend(scanId, new EventDraft(EDGE_ID, GATE_ID, FACTORY_ID, scanId, at,
                new EventPayload.Attendance(payload)), grant);
    }

    private void failCommit(CommandMode mode) throws SQLException {
        execute("""
                CREATE TRIGGER fail_override_commit AFTER INSERT ON local_gate_commands WHEN NEW.mode = '%s'
                BEGIN
                    UPDATE local_scan_outcomes SET command_id = 'missing' WHERE scan_id = NEW.scan_id;
                END
                """.formatted(mode.name()));
    }

    private void assertCounts(int events, int commands) throws SQLException {
        assertEquals(Integer.toString(events), read("SELECT count(*) FROM local_events"));
        assertEquals(Integer.toString(events), read("SELECT count(*) FROM event_delivery"));
        assertEquals(Integer.toString(events), read("SELECT last_sequence_no FROM edge_runtime_state"));
        assertEquals(Integer.toString(commands), read("SELECT count(*) FROM local_scan_outcomes"));
        assertEquals(Integer.toString(commands), read("SELECT count(*) FROM local_gate_commands"));
        assertEquals(Integer.toString(commands), read("SELECT count(*) FROM local_replay_cache"));
    }

    private static void assertRejected(Code code, Executable command) {
        assertEquals(code, assertThrows(EventStoreException.class, command).code());
    }

    private static void assertOneWins(Callable<?> first, Callable<?> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var left = pool.submit(() -> race(barrier, first));
            var right = pool.submit(() -> race(barrier, second));
            var results = List.of(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS));
            assertTrue(results.containsAll(List.of("COMMITTED", "REPLAY")), results.toString());
        }
    }

    private static String race(CyclicBarrier barrier, Callable<?> action) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            action.call();
            return "COMMITTED";
        } catch (EventStoreException rejected) {
            return rejected.code().name();
        }
    }

    private SqliteStore reopen() {
        return reopenAt(COMMITTED_AT.plusSeconds(1));
    }

    private SqliteStore reopenAt(Instant at) {
        return SqliteStore.openProvisioned(file(), fixed(at, UTC), IDENTITY);
    }

    private Path file() {
        return tempDir.resolve("edge.db");
    }

    private String read(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file())) {
            return SqliteSchemaIT.scalar(connection, sql);
        }
    }

    private void execute(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file());
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
