package vn.tmc.edge.edgesync.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tmc.contracts.EventEnvelope;
import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchAck.EventAck;
import vn.tmc.contracts.SyncBatchAck.Status;
import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.edge.edgesync.api.EventDraft;
import vn.tmc.edge.edgesync.api.EventPayload;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.domain.SyncProtocolException;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteEventStoreIT.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteSchemaIT.scalar;

class SqliteBatchIT {
    @TempDir
    Path tempDir;

    @Test
    void pendingBatch_selectsSequenceAscendingAndCapsAt50() {
        var store = store();
        for (int i = 0; i < 51; i++) {
            append(store, EDGE_ID, "outside-policy");
        }
        var batch = store.pendingBatch(50);
        assertEquals(4, batch.syncBatchId().version());
        assertEquals(LongStream.rangeClosed(1, 50).boxed().toList(),
                batch.events().stream().map(EventEnvelope::sequenceNo).toList());
        assertThrows(IllegalArgumentException.class, () -> store.pendingBatch(51));
    }

    @Test
    void pendingBatch_respectsOneMiBBeforePersistingMembership() throws Exception {
        var store = store();
        var text = "é馬".repeat(75_000);
        var first = append(store, EDGE_ID, text);
        var second = append(store, EDGE_ID, text);
        append(store, EDGE_ID, text);
        var batch = store.pendingBatch(50);
        assertEquals(List.of(first, second), batch.events());
        assertTrue(WireJson.write(batch).length <= 1024 * 1024);
        assertTrue(WireJson.write(new SyncBatchRequest(batch.syncBatchId(),
                List.of(first, second, store.findEvent(UUID.fromString(read(
                        "SELECT event_id FROM local_events WHERE sequence_no = 3"))).orElseThrow().envelope())))
                .length > 1024 * 1024);
        assertEquals("2", read("SELECT count(*) FROM event_delivery WHERE batch_id IS NOT NULL"));
    }

    @Test
    void pendingBatch_persistsMembershipBeforeReturnAndRestartPreservesExactRequest() throws Exception {
        var first = store();
        var event = append(first, EDGE_ID, "outside-policy");
        var batch = first.pendingBatch(50);
        assertEquals(batch.syncBatchId().toString(), read("SELECT batch_id FROM event_delivery"));
        assertEquals("READY", read("SELECT state FROM local_sync_batches"));
        var fingerprint = read("SELECT membership_fingerprint FROM local_sync_batches");
        append(first, EDGE_ID, "later");
        var reopened = store().pendingBatch(50);
        assertEquals(batch, reopened);
        assertEquals(event, reopened.events().getFirst());
        assertEquals(fingerprint, read("SELECT membership_fingerprint FROM local_sync_batches"));
        assertEquals(64, fingerprint.length());
        assertArrayEquals(WireJson.write(batch), WireJson.write(reopened));
    }

    @Test
    void pendingBatch_allowsOnlyOneActiveBatchPerEdgeWithConcurrentSelectors() throws Exception {
        var first = store();
        var second = store();
        append(first, EDGE_ID, "one");
        append(first, EDGE_ID, "two");
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return first.pendingBatch(1); });
            var right = executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return second.pendingBatch(2); });
            assertEquals(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS));
        }
        assertEquals("1", read("SELECT count(*) FROM local_sync_batches"));
        var other = UUID.randomUUID();
        var otherEvent = append(first, other, "independent");
        assertEquals(List.of(otherEvent), first.pendingBatch(other, 50).events());
        assertEquals("2", read("SELECT count(*) FROM local_sync_batches WHERE state = 'READY'"));
    }

    @ParameterizedTest
    @EnumSource(Status.class)
    void acknowledgement_appliesIndividualStatusAndPreservesEvent(Status status) throws Exception {
        var store = store();
        var event = append(store, EDGE_ID, "fixture");
        var batch = store.pendingBatch(50);
        boolean success = status == Status.ACCEPTED || status == Status.DUPLICATE;
        String code = success ? null : status == Status.CONFLICT ? "EVENT_CONTENT_CONFLICT" : "INVALID_PAYLOAD";
        store.applyAcknowledgements(new SyncBatchAck(batch.syncBatchId(), List.of(
                new EventAck(event.eventId(), status, success ? COMMITTED_AT : null, code)), success ? 1 : 0));
        assertEquals(status.name(), read("SELECT ack_status FROM event_delivery"));
        assertEquals(success ? "ACKNOWLEDGED" : "FAILED", read("SELECT state FROM event_delivery"));
        assertEquals(code, read("SELECT failure_code FROM event_delivery"));
        assertEquals(success ? Long.toString(COMMITTED_AT.toEpochMilli()) : null,
                read("SELECT accepted_at FROM event_delivery"));
        assertEquals(success ? "1" : "0", read("SELECT last_contiguous_ack_sequence_no FROM edge_runtime_state"));
        assertEquals("COMPLETED", read("SELECT state FROM local_sync_batches"));
        assertEquals(event, store.findEvent(event.eventId()).orElseThrow().envelope());
    }

    @Test
    void mixedAck_appliesEveryMemberAndFailedGapDoesNotBlockTheNextBatch() throws Exception {
        var store = store();
        var first = append(store, EDGE_ID, "first");
        var rejected = append(store, EDGE_ID, "rejected");
        var later = append(store, EDGE_ID, "later");
        var batch = store.pendingBatch(2);
        // Mirrors the contract's ACCEPTED + REJECTED fixture shape with locally stored event IDs.
        store.applyAcknowledgements(new SyncBatchAck(batch.syncBatchId(),
                List.of(accept(first), new EventAck(rejected.eventId(), Status.REJECTED, null, "INVALID_PAYLOAD")), 1));
        assertEquals("1", read("SELECT last_contiguous_ack_sequence_no FROM edge_runtime_state"));
        var next = store.pendingBatch(50);
        assertEquals(List.of(later), next.events());
        store.applyAcknowledgements(new SyncBatchAck(next.syncBatchId(), List.of(accept(later)), 3));
        assertEquals("1", read("SELECT last_contiguous_ack_sequence_no FROM edge_runtime_state"));
        assertEquals("2", read("SELECT count(*) FROM event_delivery WHERE state = 'ACKNOWLEDGED'"));
        assertEquals("1", read("SELECT count(*) FROM event_delivery WHERE state = 'FAILED'"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "unknown", "duplicate", "order", "batch ID", "nullability", "code", "cursor"})
    void malformedAck_appliesNothing(String defect) throws Exception {
        var store = store();
        var first = append(store, EDGE_ID, "first");
        var second = append(store, EDGE_ID, "second");
        var batch = store.pendingBatch(50);
        var left = accept(first);
        var right = accept(second);
        List<EventAck> acks = switch (defect) {
            case "missing" -> List.of(left);
            case "unknown" -> List.of(left, new EventAck(UUID.randomUUID(), Status.ACCEPTED, COMMITTED_AT, null));
            case "duplicate" -> List.of(left, left);
            case "order" -> List.of(right, left);
            case "nullability" -> List.of(left, new EventAck(second.eventId(), Status.DUPLICATE, null, null));
            case "code" -> List.of(left, new EventAck(second.eventId(), Status.REJECTED, null, "UNRECOGNIZED"));
            default -> List.of(left, right);
        };
        var ack = new SyncBatchAck(defect.equals("batch ID") ? UUID.randomUUID() : batch.syncBatchId(),
                acks, defect.equals("cursor") ? -1 : 2);
        assertThrows(SyncProtocolException.class, () -> store.applyAcknowledgements(ack));
        assertUnchanged(store, batch);
    }

    @Test
    void ackWriteFailure_rollsBackDeliveryCursorAndBatch() throws Exception {
        var store = store();
        var first = append(store, EDGE_ID, "first");
        var second = append(store, EDGE_ID, "second");
        var batch = store.pendingBatch(50);
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + file()); var statement = db.createStatement()) {
            statement.execute("""
                    CREATE TRIGGER fail_batch BEFORE UPDATE OF state ON local_sync_batches
                    BEGIN SELECT RAISE(ABORT, 'fault'); END
                    """);
        }
        assertThrows(EventStoreException.class, () -> store.applyAcknowledgements(new SyncBatchAck(batch.syncBatchId(),
                List.of(accept(first), accept(second)), 2)));
        assertUnchanged(store, batch);
    }

    private void assertUnchanged(SqliteStore store, SyncBatchRequest batch) throws SQLException {
        assertEquals("2", read("SELECT count(*) FROM event_delivery WHERE state = 'PENDING' AND ack_status IS NULL"));
        assertEquals("0", read("SELECT last_contiguous_ack_sequence_no FROM edge_runtime_state"));
        assertEquals("READY", read("SELECT state FROM local_sync_batches"));
        assertEquals(batch, store.pendingBatch(50));
    }

    static EventAck accept(EventEnvelope event) {
        return new EventAck(event.eventId(), Status.ACCEPTED, COMMITTED_AT, null);
    }

    static EventEnvelope append(SqliteStore store, UUID edgeId, String reason) {
        return store.append(new EventDraft(edgeId, GATE_ID, FACTORY_ID, UUID.randomUUID(), OCCURRED_AT,
                new EventPayload.AccessDenied(null, reason, null, null, EventPayload.ScanMode.OFFLINE))).envelope();
    }

    private Path file() {
        return tempDir.resolve("edge.db");
    }

    private SqliteStore store() {
        return new SqliteStore(file(), Clock.fixed(COMMITTED_AT, ZoneOffset.UTC));
    }

    private String read(String query) throws SQLException {
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + file())) {
            return scalar(db, query);
        }
    }
}
