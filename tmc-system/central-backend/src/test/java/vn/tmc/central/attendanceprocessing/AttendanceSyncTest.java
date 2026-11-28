package vn.tmc.central.attendanceprocessing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.tmc.central.attendanceprocessing.internal.model.AttendanceEvent;
import vn.tmc.central.attendanceprocessing.internal.repository.InMemoryAttendanceRepository;
import vn.tmc.central.attendanceprocessing.internal.service.AttendanceProcessingService;
import vn.tmc.contracts.DeviceContext;
import vn.tmc.contracts.EventEnvelope;
import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchAck.Status;
import vn.tmc.contracts.SyncBatchRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHỤ TRÁCH: Task 3.
 * Kiểm thử tiếp nhận sự kiện đồng bộ từ Edge theo CONTRACT-V1.md §7:
 * - Per-event transaction
 * - Idempotency (DUPLICATE) khi trùng eventId cùng hash (code = null, acceptedAt = commit time)
 * - Xung đột (CONFLICT) khi trùng eventId khác hash (code = "EVENT_CONTENT_CONFLICT", acceptedAt = null)
 * - Từ chối (REJECTED) khi payload hash không hợp lệ (code = "HASH_MISMATCH", acceptedAt = null)
 * - Giới hạn kích thước lô 1 <= size <= 50, sequence tăng dần
 * - Cursor sequence liên tục
 */
public class AttendanceSyncTest {

    private InMemoryAttendanceRepository repository;
    private AttendanceProcessingService service;
    private final UUID edgeId = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private final UUID factoryId = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private final UUID gateId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private DeviceContext deviceContext;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAttendanceRepository();
        service = new AttendanceProcessingService(repository, repository, repository);
        deviceContext = new DeviceContext(edgeId, factoryId);
    }

    private EventEnvelope createEnvelope(UUID eventId, String empId, String dir, long seq) {
        String payload = String.format("{\"commandId\":\"%s\",\"direction\":\"%s\",\"employeeId\":\"%s\",\"jti\":\"%s\",\"kid\":\"key-1\",\"source\":\"ONLINE\",\"tokenIssuedAt\":\"%s\"}",
                UUID.randomUUID(), dir, empId, UUID.randomUUID(), Instant.now());
        String sha256 = EventEnvelope.computeSha256(payload);
        return new EventEnvelope(
                eventId,
                EventEnvelope.EVT_ATTENDANCE_RECORDED,
                1,
                edgeId,
                gateId,
                factoryId,
                seq,
                UUID.randomUUID(),
                Instant.now(),
                sha256,
                payload
        );
    }

    @Test
    @DisplayName("CONTRACT-V1 §7: Đồng bộ lô hợp lệ thành công và commit từng sự kiện")
    void testBatchSyncAccepted() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        EventEnvelope env1 = createEnvelope(id1, UUID.randomUUID().toString(), "IN", 1);
        EventEnvelope env2 = createEnvelope(id2, UUID.randomUUID().toString(), "IN", 2);

        SyncBatchRequest batch = new SyncBatchRequest(UUID.randomUUID(), List.of(env1, env2));
        SyncBatchAck ack = service.synchronize(deviceContext, batch);

        assertNotNull(ack);
        assertEquals(2, ack.acks().size());
        assertEquals(Status.ACCEPTED, ack.acks().get(0).status());
        assertNotNull(ack.acks().get(0).acceptedAt());
        assertNull(ack.acks().get(0).code());

        assertEquals(Status.ACCEPTED, ack.acks().get(1).status());
        assertNotNull(ack.acks().get(1).acceptedAt());
        assertNull(ack.acks().get(1).code());

        assertEquals(2L, ack.lastContiguousSequenceNo());
        assertTrue(repository.existsByEventId(id1));
        assertTrue(repository.existsByEventId(id2));
    }

    @Test
    @DisplayName("CONTRACT-V1 §7 & FR-OS-06: Trùng eventId cùng hash trả DUPLICATE, code = null")
    void testIdempotencyDuplicate() {
        UUID id = UUID.randomUUID();
        EventEnvelope env = createEnvelope(id, UUID.randomUUID().toString(), "IN", 1);

        // Lần gửi 1: ACCEPTED
        SyncBatchRequest batch1 = new SyncBatchRequest(UUID.randomUUID(), List.of(env));
        SyncBatchAck ack1 = service.synchronize(deviceContext, batch1);
        assertEquals(Status.ACCEPTED, ack1.acks().get(0).status());
        assertNotNull(ack1.acks().get(0).acceptedAt());
        assertNull(ack1.acks().get(0).code());

        // Lần gửi 2 (Retry từ Edge): DUPLICATE
        SyncBatchRequest batch2 = new SyncBatchRequest(UUID.randomUUID(), List.of(env));
        SyncBatchAck ack2 = service.synchronize(deviceContext, batch2);
        assertEquals(Status.DUPLICATE, ack2.acks().get(0).status());
        assertNotNull(ack2.acks().get(0).acceptedAt());
        assertNull(ack2.acks().get(0).code());
    }

    @Test
    @DisplayName("CONTRACT-V1 §7: Payload sai hash SHA-256 trả REJECTED với code HASH_MISMATCH")
    void testHashMismatchRejected() {
        UUID id = UUID.randomUUID();
        String payload = "{\"employeeId\":\"" + UUID.randomUUID() + "\",\"direction\":\"IN\"}";
        String fakeSha256 = "0000000000000000000000000000000000000000000000000000000000000000";

        EventEnvelope env = new EventEnvelope(
                id,
                EventEnvelope.EVT_ATTENDANCE_RECORDED,
                1,
                edgeId,
                gateId,
                factoryId,
                1,
                UUID.randomUUID(),
                Instant.now(),
                fakeSha256,
                payload
        );

        SyncBatchRequest batch = new SyncBatchRequest(UUID.randomUUID(), List.of(env));
        SyncBatchAck ack = service.synchronize(deviceContext, batch);

        assertEquals(Status.REJECTED, ack.acks().get(0).status());
        assertEquals(SyncBatchAck.CODE_HASH_MISMATCH, ack.acks().get(0).code());
        assertNull(ack.acks().get(0).acceptedAt());
        assertFalse(repository.existsByEventId(id));
    }

    @Test
    @DisplayName("CONTRACT-V1 §7: Trùng eventId nhưng khác hash trả CONFLICT với code EVENT_CONTENT_CONFLICT")
    void testConflictOnDifferentHashForSameEventId() {
        UUID id = UUID.randomUUID();
        UUID empId = UUID.randomUUID();

        // Event 1
        EventEnvelope env1 = createEnvelope(id, empId.toString(), "IN", 1);
        service.synchronize(deviceContext, new SyncBatchRequest(UUID.randomUUID(), List.of(env1)));

        // Event 2: Cùng id nhưng thay đổi payload
        String payload2 = "{\"commandId\":\"" + UUID.randomUUID() + "\",\"direction\":\"OUT\",\"employeeId\":\"" + empId + "\",\"jti\":\"" + UUID.randomUUID() + "\",\"kid\":\"key-1\",\"source\":\"ONLINE\",\"tokenIssuedAt\":\"" + Instant.now() + "\"}";
        String sha256_2 = EventEnvelope.computeSha256(payload2);
        EventEnvelope env2 = new EventEnvelope(
                id,
                EventEnvelope.EVT_ATTENDANCE_RECORDED,
                1,
                edgeId,
                gateId,
                factoryId,
                2,
                UUID.randomUUID(),
                Instant.now(),
                sha256_2,
                payload2
        );

        SyncBatchRequest batch2 = new SyncBatchRequest(UUID.randomUUID(), List.of(env2));
        SyncBatchAck ack2 = service.synchronize(deviceContext, batch2);

        assertEquals(Status.CONFLICT, ack2.acks().get(0).status());
        assertEquals(SyncBatchAck.CODE_EVENT_CONTENT_CONFLICT, ack2.acks().get(0).code());
        assertNull(ack2.acks().get(0).acceptedAt());

        // Event gốc không bị sửa
        AttendanceEvent original = repository.findByEventId(id).orElseThrow();
        assertEquals(empId, original.getEmployeeId());

        // Ghi nhận conflict log
        assertEquals(1, repository.findConflicts().size());
    }

    @Test
    @DisplayName("CONTRACT-V1 §7: Per-event transaction cô lập lỗi giữa các sự kiện")
    void testPerEventTransactionIsolation() {
        UUID validId = UUID.randomUUID();
        UUID badHashId = UUID.randomUUID();

        EventEnvelope validEnv = createEnvelope(validId, UUID.randomUUID().toString(), "IN", 1);
        EventEnvelope badEnv = new EventEnvelope(
                badHashId,
                EventEnvelope.EVT_ATTENDANCE_RECORDED,
                1,
                edgeId,
                gateId,
                factoryId,
                2,
                UUID.randomUUID(),
                Instant.now(),
                "bad-hash-64-chars-0000000000000000000000000000000000000000000000000000000",
                "{\"employeeId\":\"" + UUID.randomUUID() + "\"}"
        );

        SyncBatchRequest batch = new SyncBatchRequest(UUID.randomUUID(), List.of(validEnv, badEnv));
        SyncBatchAck ack = service.synchronize(deviceContext, batch);

        assertEquals(Status.ACCEPTED, ack.acks().get(0).status());
        assertEquals(Status.REJECTED, ack.acks().get(1).status());
        assertTrue(repository.existsByEventId(validId));
        assertFalse(repository.existsByEventId(badHashId));
    }

    @Test
    @DisplayName("CONTRACT-V1 §7: Lô vượt quá 50 sự kiện hoặc sequence không tăng dần bị ném 400")
    void testBatchValidationRules() {
        // Vượt quá 50 events
        List<EventEnvelope> list51 = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            list51.add(createEnvelope(UUID.randomUUID(), UUID.randomUUID().toString(), "IN", i + 1));
        }
        assertThrows(IllegalArgumentException.class, () -> new SyncBatchRequest(UUID.randomUUID(), list51));

        // Sequence không tăng dần
        EventEnvelope e1 = createEnvelope(UUID.randomUUID(), UUID.randomUUID().toString(), "IN", 5);
        EventEnvelope e2 = createEnvelope(UUID.randomUUID(), UUID.randomUUID().toString(), "IN", 3);
        assertThrows(IllegalArgumentException.class, () -> new SyncBatchRequest(UUID.randomUUID(), List.of(e1, e2)));

        // Trùng eventId trong lô
        UUID dupId = UUID.randomUUID();
        EventEnvelope dup1 = createEnvelope(dupId, UUID.randomUUID().toString(), "IN", 1);
        EventEnvelope dup2 = createEnvelope(dupId, UUID.randomUUID().toString(), "IN", 2);
        assertThrows(IllegalArgumentException.class, () -> new SyncBatchRequest(UUID.randomUUID(), List.of(dup1, dup2)));
    }

    @Test
    @DisplayName("CONTRACT-EXAMPLES #4: Mixed ACK - event 1 ACCEPTED, event 2 direction=SIDEWAYS REJECTED INVALID_PAYLOAD, cursor=1")
    void testContractExample4MixedAckSidewaysDirectionRejected() {
        UUID ev1Id = UUID.fromString("01a1e73b-8500-7000-8000-000000000001");
        UUID ev2Id = UUID.fromString("01a1e73b-8500-7000-8000-000000000002");
        String empId = "00000000-0000-4000-8000-000000000011";

        EventEnvelope env1 = createEnvelope(ev1Id, empId, "IN", 1);
        String badPayload = "{\"commandId\":\"00000000-0000-4000-8000-000000000016\",\"direction\":\"SIDEWAYS\",\"employeeId\":\"" + empId + "\",\"jti\":\"00000000-0000-4000-8000-000000000015\",\"kid\":\"qr-key-2026-11\",\"operatorId\":null,\"reason\":null,\"source\":\"OFFLINE\",\"tokenIssuedAt\":\"2026-11-17T01:59:45.000Z\"}";
        EventEnvelope env2 = new EventEnvelope(
                ev2Id, EventEnvelope.EVT_ATTENDANCE_RECORDED, 1,
                edgeId, gateId, factoryId, 2,
                UUID.randomUUID(), Instant.parse("2026-11-17T02:00:00.000Z"),
                EventEnvelope.computeSha256(badPayload), badPayload
        );

        UUID batchId = UUID.randomUUID();
        SyncBatchRequest batch = new SyncBatchRequest(batchId, List.of(env1, env2));
        SyncBatchAck ack = service.synchronize(deviceContext, batch);

        assertEquals(Status.ACCEPTED, ack.acks().get(0).status());
        assertEquals(Status.REJECTED, ack.acks().get(1).status());
        assertEquals(SyncBatchAck.CODE_INVALID_PAYLOAD, ack.acks().get(1).code());
        assertNull(ack.acks().get(1).acceptedAt());
        assertEquals(1L, ack.lastContiguousSequenceNo());

        // Retry nguyên lô: event 1 thành DUPLICATE, event 2 vẫn REJECTED
        SyncBatchAck retryAck = service.synchronize(deviceContext, batch);
        assertEquals(Status.DUPLICATE, retryAck.acks().get(0).status());
        assertEquals(ack.acks().get(0).acceptedAt(), retryAck.acks().get(0).acceptedAt());
        assertEquals(Status.REJECTED, retryAck.acks().get(1).status());
        assertEquals(SyncBatchAck.CODE_INVALID_PAYLOAD, retryAck.acks().get(1).code());
    }

    @Test
    @DisplayName("CONTRACT-EXAMPLES #5: DeviceContext không khớp edgeId/factoryId ném DeviceBindingException (403)")
    void testDeviceBindingMismatchThrows403() {
        EventEnvelope env = createEnvelope(UUID.randomUUID(), UUID.randomUUID().toString(), "IN", 1);
        DeviceContext wrongDevice = new DeviceContext(UUID.randomUUID(), factoryId);
        assertThrows(vn.tmc.central.attendanceprocessing.internal.service.DeviceBindingException.class,
                () -> service.synchronize(wrongDevice, new SyncBatchRequest(UUID.randomUUID(), List.of(env))));
    }
}
