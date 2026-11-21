package vn.tmc.central.attendanceprocessing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.tmc.central.attendanceprocessing.api.Adjustment;
import vn.tmc.central.attendanceprocessing.api.AdjustmentDecision;
import vn.tmc.central.attendanceprocessing.api.AdjustmentRequest;
import vn.tmc.central.attendanceprocessing.api.DailyAttendance;
import vn.tmc.central.attendanceprocessing.api.PresenceView;
import vn.tmc.central.attendanceprocessing.internal.controller.AttendanceController;
import vn.tmc.central.attendanceprocessing.internal.controller.AttendanceSyncController;
import vn.tmc.central.attendanceprocessing.internal.repository.InMemoryAttendanceRepository;
import vn.tmc.central.attendanceprocessing.internal.service.AttendanceAdjustmentService;
import vn.tmc.central.attendanceprocessing.internal.service.AttendanceProcessingService;
import vn.tmc.contracts.DeviceContext;
import vn.tmc.contracts.EventEnvelope;
import vn.tmc.contracts.Page;
import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHỤ TRÁCH: Task 3.
 * Kiểm thử controller HTTP endpoints của module attendanceprocessing:
 * - AttendanceSyncController: POST /api/v1/attendance/sync (với DeviceContext và SyncBatchRequest)
 * - AttendanceController:
 *   + POST /api/v1/attendance/adjustments
 *   + POST /api/v1/attendance/adjustments/{id}/decision
 *   + GET /api/v1/attendance/adjustments
 *   + GET /api/v1/attendance/records
 *   + GET /api/v1/attendance/presence
 */
public class AttendanceControllerTest {

    private InMemoryAttendanceRepository repository;
    private AttendanceProcessingService processingService;
    private AttendanceAdjustmentService adjustmentService;
    private AttendanceSyncController syncController;
    private AttendanceController attendanceController;

    private static final UUID EDGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID FACTORY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID EMP_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @BeforeEach
    void setUp() {
        repository = new InMemoryAttendanceRepository();
        adjustmentService = new AttendanceAdjustmentService(repository, repository);
        processingService = new AttendanceProcessingService(repository, repository, repository, adjustmentService);
        syncController = new AttendanceSyncController(processingService);
        attendanceController = new AttendanceController(processingService, adjustmentService);
    }

    @Test
    @DisplayName("SyncController: Xử lý thành công POST /api/v1/attendance/sync với CONTRACT-V1 ACK")
    void testSyncControllerEndpoint() {
        UUID eventId = UUID.randomUUID();
        String payload = String.format("{\"employeeId\":\"%s\",\"direction\":\"IN\",\"source\":\"ONLINE\"}", EMP_ID);
        String sha256 = EventEnvelope.computeSha256(payload);

        EventEnvelope env = new EventEnvelope(
                eventId,
                EventEnvelope.EVT_ATTENDANCE_RECORDED,
                1,
                EDGE_ID,
                GATE_ID,
                FACTORY_ID,
                1,
                UUID.randomUUID(),
                Instant.now(),
                sha256,
                payload
        );

        SyncBatchRequest batch = new SyncBatchRequest(UUID.randomUUID(), List.of(env));
        ResponseEntity<SyncBatchAck> response = syncController.synchronize(batch, EDGE_ID.toString(), FACTORY_ID.toString());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().acks().size());
        SyncBatchAck.EventAck ack = response.getBody().acks().get(0);
        assertEquals(SyncBatchAck.Status.ACCEPTED, ack.status());
        assertNotNull(ack.acceptedAt(), "ACCEPTED phải có acceptedAt != null");
        assertNull(ack.code(), "ACCEPTED phải có code == null");
    }

    @Test
    @DisplayName("AttendanceController: CONTRACT-V1 REST APIs điều chỉnh công, truy vấn records và presence")
    void testAttendanceControllerContractV1Endpoints() {
        // 1. Gửi yêu cầu điều chỉnh công: POST /api/v1/attendance/adjustments
        AdjustmentRequest req = new AdjustmentRequest(
                UUID.randomUUID(),
                Instant.parse("2026-11-15T14:00:00Z"),
                "OUT",
                "Quên quẹt thẻ ra do thiết bị cổng bảo trì tại cửa B."
        );
        ResponseEntity<Adjustment> createResp = attendanceController.createAdjustment(req, "idemp-key-1");
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        assertNotNull(createResp.getBody());
        UUID adjId = createResp.getBody().id();
        assertEquals("PENDING", createResp.getBody().state());
        assertEquals(1, createResp.getBody().version());

        // 2. Tra cứu danh sách yêu cầu điều chỉnh: GET /api/v1/attendance/adjustments
        ResponseEntity<Page<Adjustment>> queryAdjResp = attendanceController.queryAdjustments(null, "PENDING", 0, 20);
        assertEquals(HttpStatus.OK, queryAdjResp.getStatusCode());
        assertNotNull(queryAdjResp.getBody());
        assertTrue(queryAdjResp.getBody().items().stream().anyMatch(a -> a.id().equals(adjId)));

        // 3. Phê duyệt yêu cầu điều chỉnh: POST /api/v1/attendance/adjustments/{id}/decision
        AdjustmentDecision decision = new AdjustmentDecision("APPROVE", "Đã xác minh camera bảo vệ", 1);
        ResponseEntity<Adjustment> decideResp = attendanceController.decideAdjustment(adjId, decision);
        assertEquals(HttpStatus.OK, decideResp.getStatusCode());
        assertNotNull(decideResp.getBody());
        assertEquals("APPROVED", decideResp.getBody().state());
        assertEquals(2, decideResp.getBody().version());

        // 4. Tra cứu bảng công ngày: GET /api/v1/attendance/records
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 30);
        ResponseEntity<Page<DailyAttendance>> recordsResp = attendanceController.queryRecords(EMP_ID, FACTORY_ID, from, to, 0, 20);
        assertEquals(HttpStatus.OK, recordsResp.getStatusCode());
        assertNotNull(recordsResp.getBody());

        // 5. Tra cứu hiện diện: GET /api/v1/attendance/presence
        ResponseEntity<PresenceView> presenceResp = attendanceController.getPresence(FACTORY_ID);
        assertEquals(HttpStatus.OK, presenceResp.getStatusCode());
        assertNotNull(presenceResp.getBody());
        assertEquals("CENTRAL_SYNCED", presenceResp.getBody().coverage());
    }
}
