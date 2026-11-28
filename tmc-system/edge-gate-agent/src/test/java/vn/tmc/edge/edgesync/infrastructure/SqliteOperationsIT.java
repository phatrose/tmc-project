package vn.tmc.edge.edgesync.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.tmc.contracts.AttendancePayload;
import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.edge.edgesync.api.CommandCompletion;
import vn.tmc.edge.edgesync.api.CommittedEvent;
import vn.tmc.edge.edgesync.api.EventDraft;
import vn.tmc.edge.edgesync.api.EventPayload;
import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;
import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.api.ReplayReservation;
import vn.tmc.edge.edgesync.api.ScanInputFingerprint;
import vn.tmc.edge.edgesync.application.BootstrapWorker;
import vn.tmc.edge.edgesync.application.CachedAccess;
import vn.tmc.edge.edgesync.application.ConnectivityMonitor;
import vn.tmc.edge.edgesync.application.EdgeOperations;
import vn.tmc.edge.edgesync.application.HeartbeatWorker;
import vn.tmc.edge.edgesync.application.KeyRefreshWorker;
import vn.tmc.edge.edgesync.application.SyncWorker;
import vn.tmc.edge.edgesync.domain.HeartbeatPort;
import vn.tmc.edge.edgesync.domain.SyncTransportException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteBatchIT.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteBootstrapIT.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteEventStoreIT.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteSchemaIT.scalar;

class SqliteOperationsIT {
    @TempDir
    Path tempDir;
    private final SqliteRecoveryIT.TestClock clock = new SqliteRecoveryIT.TestClock();
    private SqliteStore store;
    private ConnectivityMonitor connectivity;

    @BeforeEach
    void provision() {
        store = SqliteStore.provisionNew(file(), clock, IDENTITY);
        connectivity = monitor(store);
    }

    @Test
    void wanLoss_withValidTrustedCache_doesNotPreventLocalPersistence() {
        var cache = store.bootstrapCache(IDENTITY);
        cache.apply(full(clock.instant()));
        var worker = new BootstrapWorker(after -> { throw offline(); }, cache, clock);
        assertThrows(SyncTransportException.class, worker::refresh);
        var keyWorker = new KeyRefreshWorker(() -> { throw offline(); }, cache);
        var access = new CachedAccess(cache, keyWorker::requestRefresh);
        var allowed = access.evaluate(EMPLOYEE_ID, JTI, KID, OCCURRED_AT, GATE_ID, clock.instant());
        assertEquals(CachedAccess.Decision.ALLOW, allowed.decision());
        assertEquals(Direction.IN, allowed.gate().direction());
        var event = allow(SCAN_ID, JTI, COMMAND_ID, allowed.gate().id(), clock.instant());
        assertEquals(1, event.envelope().sequenceNo());
        assertTrue(event.envelope().payload().contains("\"source\":\"OFFLINE\""));
        assertEquals(1, operations().status().queue().pendingCount());
    }

    @Test
    void startupBeforeContact_isOfflineEvenWithPersistedPreviousContact() {
        connectivity.recordSuccess();
        assertEquals(ScanMode.ONLINE, connectivity.mode());
        var reopened = SqliteStore.openProvisioned(file(), clock, IDENTITY);
        assertEquals(clock.instant(), reopened.queue(EDGE_ID).lastCentralContactAt());
        assertEquals(ScanMode.OFFLINE, monitor(reopened).mode());
    }

    @Test
    void sourceOnlineOnlyWithin60SecondsOfSuccessfulContact() {
        assertEquals(ScanMode.OFFLINE, connectivity.mode());
        connectivity.recordSuccess();
        clock.advance(Duration.ofMillis(59_999));
        assertEquals(ScanMode.ONLINE, connectivity.mode());
        clock.advance(Duration.ofMillis(1));
        assertEquals(ScanMode.OFFLINE, connectivity.mode());
    }

    @Test
    void singleTimeoutInside60SecondWindow_doesNotFlipSource() {
        connectivity.recordSuccess();
        var successfulAt = connectivity.lastSuccessfulContact();
        clock.advance(Duration.ofSeconds(30));
        var heartbeat = new HeartbeatWorker(operations(), message -> { throw offline(); }, connectivity);
        assertThrows(SyncTransportException.class, heartbeat::sendOnce);
        assertEquals(ScanMode.ONLINE, connectivity.mode());
        assertEquals(successfulAt, store.queue(EDGE_ID).lastCentralContactAt());
    }

    @Test
    void reconnect_doesNotRewriteOldEventSource() {
        var offline = allow(SCAN_ID, JTI, COMMAND_ID, GATE_ID, clock.instant()).envelope();
        connectivity.recordSuccess();
        clock.advance(Duration.ofSeconds(31));
        var online = allow(fixtureId(31), fixtureId(32), fixtureId(33), GATE_ID, clock.instant()).envelope();
        assertTrue(online.payload().contains("\"source\":\"ONLINE\""));
        var batch = store.pendingBatch(50);
        assertEquals(List.of(offline, online), batch.events());
        store.applyAcknowledgements(new SyncBatchAck(batch.syncBatchId(), batch.events().stream()
                .map(SqliteBatchIT::accept).toList(), 2));
        assertTrue(offline.payload().contains("\"source\":\"OFFLINE\""));
        assertEquals(offline, store.findEvent(offline.eventId()).orElseThrow().envelope());
    }

    @Test
    void heartbeat_containsCountsRevisionDiskAndOldestPendingAndCreatesNoEvent() throws Exception {
        store.bootstrapCache(IDENTITY).apply(full(clock.instant()));
        var failed = append(store, EDGE_ID, "failed");
        var batch = store.pendingBatch(1);
        store.applyAcknowledgements(new SyncBatchAck(batch.syncBatchId(), List.of(
                new SyncBatchAck.EventAck(failed.eventId(), SyncBatchAck.Status.REJECTED, null, "INVALID_PAYLOAD")), 0));
        var pending = append(store, EDGE_ID, "pending");
        var messages = new ArrayList<HeartbeatPort.Heartbeat>();
        clock.advance(Duration.ofSeconds(30));
        var heartbeat = new HeartbeatWorker(operations(), message -> {
            messages.add(message);
            return new HeartbeatPort.Receipt(clock.instant());
        }, connectivity);
        assertTrue(heartbeat.sendOnce());
        assertEquals(List.of(new HeartbeatPort.Heartbeat(1, 1, 1, 8192, pending.occurredAt(), clock.instant())), messages);
        assertEquals(clock.instant(), store.queue(EDGE_ID).lastCentralContactAt());
        assertEquals(ScanMode.ONLINE, connectivity.mode());
        assertEquals("2", read("SELECT count(*) FROM local_events"));
        assertEquals("2", read("SELECT last_sequence_no FROM edge_runtime_state"));
        assertEquals(Duration.ofSeconds(30), HeartbeatWorker.DEFAULT_CYCLE);
    }

    @Test
    void missingDiskProbe_doesNotSendAFalseDiskSample() {
        var ops = new EdgeOperations(EDGE_ID, store, store, store.bootstrapCache(IDENTITY), connectivity,
                clock, OptionalLong::empty);
        var worker = new HeartbeatWorker(ops, message -> fail("No disk value was measured"), connectivity);
        assertFalse(worker.sendOnce());
        assertNull(ops.status().diskFreeBytes());
        assertEquals(ScanMode.OFFLINE, connectivity.mode());
    }

    @Test
    void cleanup_removesOnlyExpiredReplayAndEligibleTerminalDedupData() throws Exception {
        var old = allow(SCAN_ID, JTI, COMMAND_ID, GATE_ID, clock.instant());
        clock.advance(Duration.ofSeconds(31));
        var uncertain = allow(fixtureId(31), fixtureId(32), fixtureId(33), GATE_ID, clock.instant());
        var batch = store.pendingBatch(50);
        store.applyAcknowledgements(new SyncBatchAck(batch.syncBatchId(), List.of(accept(old.envelope()),
                accept(uncertain.envelope())), 2));
        recordResolvedCommand(COMMAND_ID, COMMITTED_AT);
        store.recover(); // The second command becomes TIMEOUT and remains forensic evidence.
        clock.advance(Duration.ofHours(48));
        var recent = allow(fixtureId(41), fixtureId(42), fixtureId(43), GATE_ID, clock.instant());
        recordResolvedCommand(fixtureId(43), clock.instant());
        var recentBatch = store.pendingBatch(50);
        store.applyAcknowledgements(new SyncBatchAck(recentBatch.syncBatchId(), List.of(accept(recent.envelope())), 3));

        store.cleanup();
        assertEquals("1", read("SELECT count(*) FROM local_replay_cache"));
        assertEquals(fixtureId(42).toString(), read("SELECT jti FROM local_replay_cache"));
        assertEquals("2", read("SELECT count(*) FROM local_scan_outcomes"));
        assertEquals("2", read("SELECT count(*) FROM local_gate_commands"));
        assertEquals("0", read("SELECT count(*) FROM local_gate_commands WHERE command_id = '" + COMMAND_ID + "'"));
        assertEquals("1", read("SELECT count(*) FROM local_gate_commands WHERE status = 'TIMEOUT'"));
        assertEquals("3", read("SELECT count(*) FROM local_events"));
    }

    @Test
    void cleanup_retainsCommandUntilResultEventIsAcknowledged() throws Exception {
        var attendance = allow(SCAN_ID, JTI, COMMAND_ID, GATE_ID, clock.instant());
        var result = store.recordCommandResult(SCAN_ID,
                new CommandCompletion(COMMAND_ID, CommandStatus.UNLOCK_ACCEPTED, clock.instant().plusMillis(50), null));
        var firstBatch = store.pendingBatch(1);
        assertEquals(List.of(attendance.envelope()), firstBatch.events());
        store.applyAcknowledgements(new SyncBatchAck(firstBatch.syncBatchId(), List.of(accept(attendance.envelope())), 1));

        clock.advance(Duration.ofHours(49));
        store.cleanup();
        assertEquals("1", read("SELECT count(*) FROM local_gate_commands"));
        assertEquals("1", read("SELECT count(*) FROM local_scan_outcomes"));

        var secondBatch = store.pendingBatch(1);
        assertEquals(List.of(result.envelope()), secondBatch.events());
        store.applyAcknowledgements(new SyncBatchAck(secondBatch.syncBatchId(), List.of(accept(result.envelope())), 2));
        store.cleanup();
        assertEquals("0", read("SELECT count(*) FROM local_gate_commands"));
        assertEquals("0", read("SELECT count(*) FROM local_scan_outcomes"));
        assertEquals("2", read("SELECT count(*) FROM local_events"));
    }

    @Test
    void cleanup_neverDeletesPendingFailedOrAnActiveBatchEvenAfterAgeWarning() throws Exception {
        var failed = append(store, EDGE_ID, "failed");
        var first = store.pendingBatch(50);
        store.applyAcknowledgements(new SyncBatchAck(first.syncBatchId(), List.of(
                new SyncBatchAck.EventAck(failed.eventId(), SyncBatchAck.Status.CONFLICT, null, "SEQUENCE_CONFLICT")), 0));
        var pending = append(store, EDGE_ID, "pending");
        var active = store.claim(EDGE_ID);
        clock.advance(Duration.ofDays(30));
        assertTrue(operations().status().backlogWarning());
        store.cleanup();
        assertEquals(1, store.queue(EDGE_ID).failedCount());
        assertEquals(1, store.queue(EDGE_ID).pendingCount());
        assertEquals("IN_FLIGHT", read("SELECT state FROM local_sync_batches WHERE state <> 'COMPLETED'"));
        assertEquals(active.request(), store.pendingBatch(50));
        assertEquals(List.of(pending), active.request().events());
        assertEquals(failed, store.findEvent(failed.eventId()).orElseThrow().envelope());
    }

    @Test
    void warningThresholdAt50000_doesNotDeleteData() throws Exception {
        execute("""
                WITH RECURSIVE numbers(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM numbers WHERE n<50000)
                INSERT INTO local_events(event_id, event_type, schema_version, edge_id, gate_id, factory_id,
                    sequence_no, correlation_id, occurred_at, payload_sha256, payload_text, committed_at)
                SELECT printf('01a1e73b-8500-7000-8000-%%012d', n), 'access.attempt.denied', 1,
                    '%s', '%s', '%s', n, '%s', %d, '%s', '{}', %d FROM numbers
                """.formatted(EDGE_ID, GATE_ID, FACTORY_ID, SCAN_ID, clock.millis(), "0".repeat(64), clock.millis()));
        execute("INSERT INTO event_delivery(event_id, state) SELECT event_id, 'PENDING' FROM local_events");
        execute("UPDATE edge_runtime_state SET last_sequence_no = 50000");
        assertTrue(operations().status().backlogWarning());
        store.cleanup();
        assertEquals(50_000, operations().status().queue().pendingCount());
        assertEquals("50000", read("SELECT count(*) FROM local_events"));
    }

    @Test
    void lostSequenceState_requiresReprovisionInsteadOfSequenceReset() throws Exception {
        append(store, EDGE_ID, "first");
        Files.move(file(), tempDir.resolve("offline-copy.db"));
        assertReprovision(() -> SqliteStore.openProvisioned(file(), clock, IDENTITY));
        assertReprovision(() -> append(store, EDGE_ID, "attempt after loss"));
        assertTrue(operations().status().reprovisionRequired());
        assertFalse(Files.exists(file()));
    }

    @Test
    void damagedSequenceState_preventsAppendAndReopen() throws Exception {
        append(store, EDGE_ID, "first");
        execute("UPDATE edge_runtime_state SET last_sequence_no = 0");
        assertReprovision(() -> append(store, EDGE_ID, "attempt after reset"));
        assertReprovision(() -> SqliteStore.openProvisioned(file(), clock, IDENTITY));
        assertEquals("1", read("SELECT count(*) FROM local_events"));
    }

    @Test
    void corruptDatabase_requiresReprovisionAndIsPreserved() throws Exception {
        var corrupt = tempDir.resolve("corrupt.db");
        Files.writeString(corrupt, "not a sqlite database");
        assertReprovision(() -> SqliteStore.openProvisioned(corrupt, clock, IDENTITY));
        assertEquals("not a sqlite database", Files.readString(corrupt));
    }

    @Test
    void operationalStatus_exposesTrustAndDurableSyncStop() {
        store.bootstrapCache(IDENTITY).apply(full(clock.instant()));
        append(store, EDGE_ID, "awaiting sync");
        var worker = new SyncWorker(EDGE_ID, store, request -> { throw new SyncTransportException(403, 0); },
                clock, new Random(1));
        assertEquals(SyncWorker.Result.STOPPED, worker.runOnce());
        var status = operations().status();
        assertEquals("HTTP_403", status.syncStopReason());
        assertTrue(status.readyForOffline24Hours());
        assertEquals(clock.instant().plus(Duration.ofHours(48)), status.trustedUntil());
        assertEquals(1, status.queue().pendingCount());
    }

    private CommittedEvent allow(UUID scanId, UUID jti, UUID commandId, UUID gateId, Instant occurredAt) {
        var base = attendance(jti, commandId);
        var payload = new AttendancePayload(base.employeeId(), base.direction(),
                AttendancePayload.Source.valueOf(connectivity.mode().name()), base.jti(), base.kid(),
                base.tokenIssuedAt(), base.commandId(), null, null);
        var draft = new EventDraft(EDGE_ID, gateId, FACTORY_ID, scanId, occurredAt, new EventPayload.Attendance(payload));
        var reservation = new ReplayReservation(ReplayReservation.Kind.QR, jti, occurredAt.plusSeconds(45),
                EMPLOYEE_ID, Direction.IN, gateId, ScanInputFingerprint.ofQr(gateId, "fixture." + jti));
        return store.reserveAndAppend(scanId, draft, reservation);
    }

    private void recordResolvedCommand(UUID commandId, Instant completedAt) throws SQLException {
        execute("UPDATE local_gate_commands SET status='UNLOCK_ACCEPTED', completed_at=" + completedAt.toEpochMilli()
                + " WHERE command_id='" + commandId + "'");
        execute("UPDATE local_scan_outcomes SET command_status='UNLOCK_ACCEPTED' WHERE command_id='" + commandId + "'");
    }

    private static void assertReprovision(org.junit.jupiter.api.function.Executable action) {
        assertEquals(EventStoreException.Code.REPROVISION_REQUIRED, assertThrows(EventStoreException.class, action).code());
    }

    private static SyncTransportException offline() {
        return new SyncTransportException(new IOException("WAN unavailable"));
    }

    private ConnectivityMonitor monitor(SqliteStore journal) {
        return new ConnectivityMonitor(clock, at -> journal.recordCentralContact(EDGE_ID, at));
    }

    private EdgeOperations operations() {
        return new EdgeOperations(EDGE_ID, store, store, store.bootstrapCache(IDENTITY), connectivity,
                clock, () -> OptionalLong.of(8192));
    }

    private Path file() {
        return tempDir.resolve("edge.db");
    }

    private void execute(String sql) throws SQLException {
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + file()); var statement = db.createStatement()) {
            statement.execute(sql);
        }
    }

    private String read(String sql) throws SQLException {
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + file())) {
            return scalar(db, sql);
        }
    }
}
