package vn.tmc.edge.edgesync.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteErrorCode;
import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.edge.edgesync.api.CommittedEvent;
import vn.tmc.edge.edgesync.api.EventDraft;
import vn.tmc.edge.edgesync.api.EventPayload;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.api.EventStoreException.Code;
import vn.tmc.edge.edgesync.api.LocalEventStorePort;
import vn.tmc.edge.edgesync.api.ReplayReservation;
import vn.tmc.edge.edgesync.api.ScanInputFingerprint;
import vn.tmc.edge.edgesync.api.ScanOutcome;
import vn.tmc.edge.gatestation.domain.TurnstileControlPort;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteEventStoreIT.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteSchemaIT.scalar;

class SqliteAllowIT {
    @TempDir
    Path tempDir;

    @Test
    void reserveAndAppend_persistsReplayEventDeliveryScanOutcomeAndCommandIntentAtomically() throws SQLException {
        var attempt = firstAttempt();
        var store = store(COMMITTED_AT);
        var committed = reserve(store, attempt);
        assertCommittedAggregate(attempt, committed);
        var command = committed.newCommand().orElseThrow();
        assertEquals(COMMAND_ID, command.commandId());
        assertEquals(GATE_ID, command.gateId());
        assertEquals(EventPayload.CommandMode.QR, command.mode());
        assertEquals(EXPECTED_HASH, committed.envelope().payloadSha256());
        assertEquals("attendance.event.recorded", committed.envelope().eventType());
        assertEquals(SCAN_ID.toString(), committed.envelope().correlationId());
    }

    @Test
    void concurrentSameJti_exactlyOneTransactionWins() throws Exception {
        var results = race(firstAttempt(), attempt(fixtureId(25), JTI, fixtureId(26), OCCURRED_AT));
        assertEquals(1, results.stream().filter(RaceResult::newCommand).count());
        assertEquals(1, results.stream().filter(r -> r.rejection() == Code.REPLAY).count());
        assertCounts(1);
    }

    @Test
    void concurrentSameScanId_onlyFirstCommitMakesCommandEligible() throws Exception {
        var results = race(firstAttempt(), attempt(SCAN_ID, JTI, fixtureId(26), OCCURRED_AT));
        assertEquals(1, results.stream().filter(RaceResult::newCommand).count());
        assertTrue(results.stream().allMatch(r -> r.rejection() == null));
        assertEquals(results.get(0).eventId(), results.get(1).eventId());
        assertCounts(1);
    }

    private List<RaceResult> race(Attempt firstAttempt, Attempt secondAttempt) throws Exception {
        var first = store(COMMITTED_AT);
        var second = store(COMMITTED_AT);
        var start = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> race(first, firstAttempt, start));
            var right = executor.submit(() -> race(second, secondAttempt, start));
            return List.of(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void reserveAndAppend_failureMidTransaction_rollsBackEverythingIncludingSequence() throws SQLException {
        var store = store(COMMITTED_AT);
        try (var connection = inspect(); var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TRIGGER fail_sequence_update BEFORE UPDATE OF last_sequence_no ON edge_runtime_state
                    BEGIN
                        SELECT RAISE(ABORT, 'test write failure after journaling command');
                    END
                    """);
            var failure = assertThrows(EventStoreException.class, () -> reserve(store, firstAttempt()));
            assertStorageError(failure, SQLiteErrorCode.SQLITE_CONSTRAINT_TRIGGER);
            assertCounts(0);
            statement.execute("DROP TRIGGER fail_sequence_update");
        }
        assertEquals(1, reserve(store, firstAttempt()).envelope().sequenceNo());
        assertCounts(1);
    }

    @Test
    void businessDuplicate_within30Seconds_returnsDuplicateScanAndCreatesNoCommand() throws SQLException {
        reserve(store(COMMITTED_AT), firstAttempt());
        var elapsed = Duration.ofMillis(29_999);
        var next = differentTokenAfter(elapsed);
        var failure = assertThrows(EventStoreException.class, () -> reserve(store(OCCURRED_AT.plus(elapsed)), next));
        assertEquals(Code.DUPLICATE_SCAN, failure.code());
        assertCounts(1);
    }

    @Test
    void businessDuplicate_after30Seconds_allowsNewEvent() throws SQLException {
        reserve(store(COMMITTED_AT), firstAttempt());
        var next = reserve(store(COMMITTED_AT.plusSeconds(31)), differentTokenAfter(Duration.ofSeconds(31)));
        assertEquals(2, next.envelope().sequenceNo());
        assertCounts(2);
    }

    @Test
    void businessDuplicate_atExactly30Seconds_allowsNewEvent() throws SQLException {
        reserve(store(COMMITTED_AT), firstAttempt());
        var next = reserve(store(OCCURRED_AT.plusSeconds(30)), differentTokenAfter(Duration.ofSeconds(30)));
        assertEquals(2, next.envelope().sequenceNo());
        assertCounts(2);
    }

    @Test
    void sameJti_isReplayNotBusinessDuplicate() throws SQLException {
        var store = store(COMMITTED_AT);
        reserve(store, firstAttempt());
        var retry = attempt(fixtureId(25), JTI, fixtureId(26), OCCURRED_AT);
        var failure = assertThrows(EventStoreException.class, () -> reserve(store, retry));
        assertEquals(Code.REPLAY, failure.code());
        assertCounts(1);
    }

    @Test
    void restart_afterCommittedAllow_preservesCommandIntentAndDoesNotRequireNewEventIdentity() throws SQLException {
        var original = reserve(store(COMMITTED_AT), firstAttempt());
        var later = COMMITTED_AT.plusSeconds(80);
        var reopened = store(later);
        // Simulate updated time and a new proposed command ID before the caller detects the retry.
        var retry = attempt(SCAN_ID, JTI, fixtureId(26), later);
        var returned = reserve(reopened, retry);
        assertEquals(original.envelope(), returned.envelope());
        assertEquals(original.committedAt(), returned.committedAt());
        assertEquals(returned, reopened.findEvent(original.envelope().eventId()).orElseThrow());
        assertTrue(returned.newCommand().isEmpty());
        assertCommittedAggregate(firstAttempt(), returned);
    }

    @Test
    void sameScanIdSameFingerprint_returnsStoredOutcome() throws SQLException {
        var attempt = firstAttempt();
        var first = store(COMMITTED_AT);
        assertTrue(first.findScanOutcome(SCAN_ID, attempt.reservation().inputFingerprint()).isEmpty());
        var committed = reserve(first, attempt);
        var expected = new ScanOutcome(SCAN_ID, GATE_ID, ScanOutcome.Decision.ALLOW, "OK",
                committed.envelope().eventId(), COMMAND_ID, EventPayload.CommandStatus.NOT_SENT,
                OCCURRED_AT, EventPayload.ScanMode.OFFLINE);
        var reopened = store(COMMITTED_AT.plusSeconds(90));
        assertEquals(expected, reopened.findScanOutcome(SCAN_ID, attempt.reservation().inputFingerprint()).orElseThrow());
        assertCounts(1);
    }

    @Test
    void sameScanIdDifferentFingerprint_isIdempotencyConflict() throws SQLException {
        reserve(store(COMMITTED_AT), firstAttempt());
        var changed = attempt(SCAN_ID, fixtureId(14), fixtureId(15), OCCURRED_AT.plusSeconds(1));
        var reopened = store(COMMITTED_AT.plusSeconds(1));
        var lookupError = assertThrows(EventStoreException.class,
                () -> reopened.findScanOutcome(SCAN_ID, changed.reservation().inputFingerprint()));
        var transactionError = assertThrows(EventStoreException.class, () -> reserve(reopened, changed));
        assertEquals(Code.IDEMPOTENCY_CONFLICT, lookupError.code());
        assertEquals(Code.IDEMPOTENCY_CONFLICT, transactionError.code());
        assertCounts(1);
    }

    @Test
    void sameScanIdSameTokenDifferentGate_isIdempotencyConflict() {
        var store = store(COMMITTED_AT);
        reserve(store, firstAttempt());
        var changedGate = ScanInputFingerprint.ofQr(fixtureId(42), fixtureToken(JTI));
        var failure = assertThrows(EventStoreException.class, () -> store.findScanOutcome(SCAN_ID, changedGate));
        assertEquals(Code.IDEMPOTENCY_CONFLICT, failure.code());
    }

    @Test
    void storeBeforeCommand_unlockObservesTheCompleteCommitAndRetryDoesNotUnlock() throws SQLException {
        var attempt = firstAttempt();
        var unlocks = new AtomicInteger();
        TurnstileControlPort device = commandId -> {
            assertEquals(COMMAND_ID.toString(), commandId);
            try {
                var reopened = store(COMMITTED_AT);
                var outcome = reopened.findScanOutcome(SCAN_ID, attempt.reservation().inputFingerprint()).orElseThrow();
                var event = reopened.findEvent(outcome.eventId()).orElseThrow();
                assertCommittedAggregate(attempt, event);
            } catch (SQLException failure) {
                throw new AssertionError("The device must observe committed SQLite state", failure);
            }
            unlocks.incrementAndGet();
        };

        commitThenUnlock(store(COMMITTED_AT), attempt, device);
        commitThenUnlock(store(COMMITTED_AT.plusSeconds(65)), attempt, device);
        assertEquals(1, unlocks.get());
        assertCounts(1);
    }

    @Test
    void failedAllowCommit_neverMakesCommandEligibleForInvocation() throws SQLException {
        var store = store(COMMITTED_AT);
        var unlocks = new AtomicInteger();
        try (var connection = inspect(); var statement = connection.createStatement()) {
            // The trigger succeeds. The deferred FK aborts COMMIT after every write has executed.
            statement.execute("""
                    CREATE TRIGGER fail_allow_commit AFTER UPDATE OF last_sequence_no ON edge_runtime_state
                    BEGIN
                        UPDATE local_scan_outcomes SET command_id = 'missing';
                    END
                    """);
            var failure = assertThrows(EventStoreException.class,
                    () -> commitThenUnlock(store, firstAttempt(), ignored -> unlocks.incrementAndGet()));
            assertStorageError(failure, SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY);
            assertEquals(0, unlocks.get());
            assertCounts(0);
            statement.execute("DROP TRIGGER fail_allow_commit");
        }
        assertEquals(1, reserve(store, firstAttempt()).envelope().sequenceNo());
    }

    private static void commitThenUnlock(LocalEventStorePort store, Attempt attempt, TurnstileControlPort device) {
        reserve(store, attempt).newCommand().ifPresent(command -> device.unlock(command.commandId().toString()));
    }

    private void assertCommittedAggregate(Attempt attempt, CommittedEvent committed) throws SQLException {
        assertCounts(1);
        var envelope = committed.envelope();
        var eventId = envelope.eventId().toString();
        var retainUntil = Long.toString(COMMITTED_AT.plus(Duration.ofHours(48)).toEpochMilli());
        try (var connection = inspect()) {
            assertEquals(eventId, scalar(connection, "SELECT event_id FROM local_events"));
            assertEquals(envelope.payload(), scalar(connection, "SELECT payload_text FROM local_events"));
            assertEquals(envelope.payloadSha256(), scalar(connection, "SELECT payload_sha256 FROM local_events"));
            assertEquals(eventId, scalar(connection, "SELECT event_id FROM event_delivery"));
            assertEquals("PENDING", scalar(connection, "SELECT state FROM event_delivery"));
            assertEquals(eventId, scalar(connection, "SELECT event_id FROM local_replay_cache"));
            assertEquals("QR", scalar(connection, "SELECT kind FROM local_replay_cache"));
            assertEquals(JTI.toString(), scalar(connection, "SELECT jti FROM local_replay_cache"));
            assertEquals("5000", scalar(connection, "SELECT purge_after - expires_at FROM local_replay_cache"));
            assertEquals(SCAN_ID.toString(), scalar(connection, "SELECT scan_id FROM local_scan_outcomes"));
            assertEquals(eventId, scalar(connection, "SELECT event_id FROM local_scan_outcomes"));
            assertEquals(COMMAND_ID.toString(), scalar(connection, "SELECT command_id FROM local_scan_outcomes"));
            assertEquals("ALLOW", scalar(connection, "SELECT decision FROM local_scan_outcomes"));
            assertEquals("OK", scalar(connection, "SELECT reason_code FROM local_scan_outcomes"));
            assertEquals("NOT_SENT", scalar(connection, "SELECT command_status FROM local_scan_outcomes"));
            assertEquals("OFFLINE", scalar(connection, "SELECT mode FROM local_scan_outcomes"));
            assertEquals(attempt.reservation().inputFingerprint().sha256(),
                    scalar(connection, "SELECT input_fingerprint FROM local_scan_outcomes"));
            assertEquals(retainUntil, scalar(connection, "SELECT retain_until FROM local_scan_outcomes"));
            assertEquals(COMMAND_ID.toString(), scalar(connection, "SELECT command_id FROM local_gate_commands"));
            assertEquals(SCAN_ID.toString(), scalar(connection, "SELECT scan_id FROM local_gate_commands"));
            assertEquals(GATE_ID.toString(), scalar(connection, "SELECT gate_id FROM local_gate_commands"));
            assertEquals("QR", scalar(connection, "SELECT mode FROM local_gate_commands"));
            assertEquals("NOT_SENT", scalar(connection, "SELECT status FROM local_gate_commands"));
            assertEquals(retainUntil, scalar(connection, "SELECT retain_until FROM local_gate_commands"));
        }
    }

    private void assertCounts(int expected) throws SQLException {
        try (var connection = inspect()) {
            var count = Integer.toString(expected);
            for (String table : List.of("local_events", "event_delivery", "local_replay_cache",
                    "local_scan_outcomes", "local_gate_commands")) {
                assertEquals(count, scalar(connection, "SELECT count(*) FROM " + table), table);
            }
            assertEquals(count, scalar(connection, "SELECT coalesce(max(last_sequence_no), 0) FROM edge_runtime_state"));
        }
    }

    private static RaceResult race(LocalEventStorePort store, Attempt attempt, CyclicBarrier start) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            var committed = reserve(store, attempt);
            return new RaceResult(committed.newCommand().isPresent(), committed.envelope().eventId(), null);
        } catch (EventStoreException rejected) {
            return new RaceResult(false, null, rejected.code());
        }
    }

    private static CommittedEvent reserve(LocalEventStorePort store, Attempt attempt) {
        return store.reserveAndAppend(attempt.draft().correlationId(), attempt.draft(), attempt.reservation());
    }

    private static Attempt firstAttempt() {
        return attempt(SCAN_ID, JTI, COMMAND_ID, OCCURRED_AT);
    }

    private static Attempt differentTokenAfter(Duration elapsed) {
        return attempt(fixtureId(25), fixtureId(26), fixtureId(27), OCCURRED_AT.plus(elapsed));
    }

    private static Attempt attempt(UUID scanId, UUID jti, UUID commandId, Instant occurredAt) {
        var draft = new EventDraft(EDGE_ID, GATE_ID, FACTORY_ID, scanId, occurredAt,
                new EventPayload.Attendance(attendance(jti, commandId)));
        var fingerprint = ScanInputFingerprint.ofQr(GATE_ID, fixtureToken(jti));
        var reservation = new ReplayReservation(ReplayReservation.Kind.QR, jti,
                OCCURRED_AT.plusSeconds(45), EMPLOYEE_ID, Direction.IN, GATE_ID, fingerprint);
        return new Attempt(draft, reservation);
    }

    private static String fixtureToken(UUID jti) {
        return "fixture-token." + jti;
    }

    private Path file() {
        return tempDir.resolve("edge.db");
    }

    private SqliteStore store(Instant now) {
        return new SqliteStore(file(), Clock.fixed(now, ZoneOffset.UTC));
    }

    private Connection inspect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file());
    }

    private record Attempt(EventDraft draft, ReplayReservation reservation) {}
    private record RaceResult(boolean newCommand, UUID eventId, Code rejection) {}
}
