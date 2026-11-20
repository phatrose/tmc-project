package vn.tmc.edge.edgesync.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchAck.EventAck;
import vn.tmc.contracts.SyncBatchAck.Status;
import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.edge.edgesync.api.EventDraft;
import vn.tmc.edge.edgesync.api.EventPayload;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.api.ReplayReservation;
import vn.tmc.edge.edgesync.api.ScanInputFingerprint;
import vn.tmc.edge.edgesync.application.RecoveryWorker;
import vn.tmc.edge.edgesync.application.SyncWorker;
import vn.tmc.edge.edgesync.domain.SyncPort;
import vn.tmc.edge.edgesync.domain.SyncTransportException;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;
import static vn.tmc.edge.edgesync.application.SyncWorker.Result.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteBatchIT.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteEventStoreIT.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteSchemaIT.scalar;

class SqliteRecoveryIT {
    @TempDir
    Path tempDir;
    private final TestClock clock = new TestClock();
    private final RandomGenerator upperBound = new RandomGenerator() {
        @Override
        public long nextLong() { return 0; }
        @Override
        public long nextLong(long bound) { return bound - 1; }
    };

    @ParameterizedTest
    @ValueSource(strings = {"timeout", "connection", "500", "503", "429"})
    void retryableRequest_movesBatchToRetryWaitAndRetriesSameEnvelope(String failure) throws Exception {
        var store = store();
        append(store, EDGE_ID, "offline");
        var requests = new ArrayList<SyncBatchRequest>();
        var worker = worker(store, batch -> {
            requests.add(batch);
            if (requests.size() == 1) {
                switch (failure) {
                    case "timeout" -> throw new SyncTransportException(new HttpTimeoutException("deadline"));
                    case "connection" -> throw new SyncTransportException(new IOException("reset"));
                    default -> throw new SyncTransportException(Integer.parseInt(failure), 37);
                }
            }
            return accepted(batch);
        });
        assertEquals(RETRY_WAIT, worker.runOnce());
        assertEquals("RETRY_WAIT", read("SELECT state FROM local_sync_batches"));
        assertEquals("PENDING", read("SELECT state FROM event_delivery"));
        long seconds = failure.equals("429") ? 37 : 5;
        assertEquals(COMMITTED_AT.plusSeconds(seconds), store.schedule(EDGE_ID).retryAt());
        clock.advance(Duration.ofSeconds(seconds).minusMillis(1));
        assertEquals(IDLE, worker.runOnce());
        clock.advance(Duration.ofMillis(1));
        assertEquals(APPLIED, worker.runOnce());
        assertEquals(2, requests.size());
        assertEquals(requests.get(0), requests.get(1));
        assertArrayEquals(WireJson.write(requests.get(0)), WireJson.write(requests.get(1)));
        assertEquals("ACKNOWLEDGED", read("SELECT state FROM event_delivery"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403})
    void requestError_stopsAutomaticSyncAndPreservesBatchAcrossRestart(int status) throws Exception {
        var first = store();
        append(first, EDGE_ID, "offline");
        var batch = first.pendingBatch(50);
        assertEquals(STOPPED, worker(first, request -> { throw new SyncTransportException(status, 0); }).runOnce());
        var reopened = store();
        new RecoveryWorker(reopened::recover).recover();
        assertEquals("HTTP_" + status, reopened.schedule(EDGE_ID).stopReason());
        var worker = worker(reopened, request -> {
            assertEquals(batch, request);
            return accepted(request);
        });
        assertEquals(STOPPED, worker.runOnce());
        assertEquals(batch, reopened.pendingBatch(50));
        assertEquals("PENDING", read("SELECT state FROM event_delivery"));
        reopened.resume(EDGE_ID);
        assertEquals(APPLIED, worker.runOnce());
    }

    @Test
    void invalidAck_stopsWithoutApplyingAnyMember() throws Exception {
        var store = store();
        append(store, EDGE_ID, "offline");
        assertEquals(STOPPED, worker(store, batch -> new SyncBatchAck(batch.syncBatchId(), List.of(), 0)).runOnce());
        assertEquals("SYNC_PROTOCOL_ERROR", store.schedule(EDGE_ID).stopReason());
        assertEquals("PENDING", read("SELECT state FROM event_delivery"));
    }

    @Test
    void fullJitter_neverExceedsContractBoundAndSaturates() {
        var store = store();
        append(store, EDGE_ID, "offline");
        var worker = worker(store, batch -> { throw new SyncTransportException(503, 0); });
        for (long seconds : List.of(5L, 10L, 20L, 40L, 80L, 160L, 300L, 300L, 300L)) {
            assertEquals(RETRY_WAIT, worker.runOnce());
            assertEquals(clock.instant().plusSeconds(seconds), store.schedule(EDGE_ID).retryAt());
            clock.advance(Duration.ofSeconds(seconds));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void ambiguousCentralCommit_retriesIdenticalBatchAndAppliesDuplicatePlusAccepted(int committedCount) throws Exception {
        var store = store();
        append(store, EDGE_ID, "first");
        append(store, EDGE_ID, "second");
        var original = store.pendingBatch(50);
        Set<UUID> central = new java.util.HashSet<>();
        var worker = worker(store, request -> {
            assertEquals(original, request);
            if (central.isEmpty()) {
                request.events().stream().limit(committedCount).forEach(event -> central.add(event.eventId()));
                if (committedCount == 2) {
                    throw new SyncTransportException(new HttpTimeoutException("ACK lost"));
                }
                throw new SyncTransportException(503, 0);
            }
            var acks = request.events().stream().map(event -> new EventAck(event.eventId(),
                    central.add(event.eventId()) ? Status.ACCEPTED : Status.DUPLICATE, COMMITTED_AT, null)).toList();
            return new SyncBatchAck(request.syncBatchId(), acks, 2);
        });
        assertEquals(RETRY_WAIT, worker.runOnce());
        assertEquals("2", read("SELECT count(*) FROM event_delivery WHERE state = 'PENDING'"));
        clock.advance(Duration.ofSeconds(5));
        assertEquals(APPLIED, worker.runOnce());
        assertEquals("2", read("SELECT count(*) FROM local_events"));
        assertEquals(Integer.toString(committedCount), read("SELECT count(*) FROM event_delivery WHERE ack_status = 'DUPLICATE'"));
        assertEquals("2", read("SELECT last_contiguous_ack_sequence_no FROM edge_runtime_state"));
    }

    @Test
    void restartInflightBatch_recoversSameRequestAndContinuesSequenceAfterIt() throws Exception {
        var first = store();
        append(first, EDGE_ID, "in flight");
        var inFlight = first.claim(EDGE_ID);
        assertEquals("IN_FLIGHT", read("SELECT state FROM local_sync_batches"));
        assertNull(first.claim(EDGE_ID));
        var second = append(first, EDGE_ID, "queued");

        var reopened = store();
        new RecoveryWorker(reopened::recover).recover();
        assertEquals("RETRY_WAIT", read("SELECT state FROM local_sync_batches"));
        assertEquals(inFlight.request(), reopened.pendingBatch(50));
        var requests = new ArrayList<SyncBatchRequest>();
        var third = append(reopened, EDGE_ID, "after crash");
        assertEquals(3, third.sequenceNo());
        var worker = worker(reopened, request -> {
            requests.add(request);
            return accepted(request);
        });
        assertEquals(APPLIED, worker.runOnce());
        assertEquals(APPLIED, worker.runOnce());
        assertEquals(inFlight.request(), requests.getFirst());
        assertEquals(List.of(second, third), requests.getLast().events());
        assertEquals("3", read("SELECT last_contiguous_ack_sequence_no FROM edge_runtime_state"));
    }

    @Test
    void restart_staleAttemptCannotCompleteTheRecoveredAttempt() throws Exception {
        var first = store();
        append(first, EDGE_ID, "in flight");
        var stale = first.claim(EDGE_ID);
        var reopened = store();
        reopened.recover();
        var current = reopened.claim(EDGE_ID);
        assertEquals(stale.request(), current.request());
        assertThrows(EventStoreException.class, () -> first.complete(stale, accepted(stale.request())));
        assertEquals("PENDING", read("SELECT state FROM event_delivery"));
        reopened.complete(current, accepted(current.request()));
        assertEquals("ACKNOWLEDGED", read("SELECT state FROM event_delivery"));
    }

    @Test
    void restart_uncertainCommandRecordsTimeoutAndNeverReturnsAnotherUnlock() throws Exception {
        var fingerprint = ScanInputFingerprint.ofQr(GATE_ID, "fixture");
        var draft = new EventDraft(EDGE_ID, GATE_ID, FACTORY_ID, SCAN_ID, OCCURRED_AT,
                new EventPayload.Attendance(attendance(JTI, COMMAND_ID)));
        var reservation = new ReplayReservation(ReplayReservation.Kind.QR, JTI, OCCURRED_AT.plusSeconds(45),
                EMPLOYEE_ID, Direction.IN, GATE_ID, fingerprint);
        var first = store().reserveAndAppend(SCAN_ID, draft, reservation);
        assertTrue(first.newCommand().isPresent());
        var reopened = store();
        new RecoveryWorker(reopened::recover).recover();
        assertEquals("TIMEOUT", read("SELECT status FROM local_gate_commands"));
        assertEquals("RECOVERY_UNKNOWN", read("SELECT code FROM local_gate_commands"));
        assertEquals(EventPayload.CommandStatus.TIMEOUT,
                reopened.findScanOutcome(SCAN_ID, fingerprint).orElseThrow().commandStatus());
        var retry = reopened.reserveAndAppend(SCAN_ID, draft, reservation);
        assertTrue(retry.newCommand().isEmpty());
        assertEquals(first.envelope(), retry.envelope());
        assertEquals("1", read("SELECT count(*) FROM local_gate_commands"));
    }

    private SyncWorker worker(SqliteStore store, SyncPort transport) {
        return new SyncWorker(EDGE_ID, store, transport, clock, upperBound);
    }

    private static SyncBatchAck accepted(SyncBatchRequest batch) {
        return new SyncBatchAck(batch.syncBatchId(), batch.events().stream().map(SqliteBatchIT::accept).toList(),
                batch.events().getLast().sequenceNo());
    }

    private SqliteStore store() {
        return new SqliteStore(tempDir.resolve("edge.db"), clock);
    }

    private String read(String query) throws SQLException {
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("edge.db"))) {
            return scalar(db, query);
        }
    }

    static final class TestClock extends Clock {
        private Instant now = COMMITTED_AT;
        void advance(Duration elapsed) { now = now.plus(elapsed); }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
    }
}
