package vn.tmc.central.attendanceprocessing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.tmc.central.attendanceprocessing.api.AttendanceAnomaly;
import vn.tmc.central.attendanceprocessing.internal.model.AttendanceEvent;
import vn.tmc.central.attendanceprocessing.internal.model.DailyAttendanceRecord;
import vn.tmc.central.attendanceprocessing.internal.repository.InMemoryAttendanceRepository;
import vn.tmc.central.attendanceprocessing.internal.service.AttendanceProcessingService;
import vn.tmc.contracts.EventEnvelope;
import vn.tmc.contracts.SyncBatchRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHỤ TRÁCH: Task 3.
 * Kiểm thử phát hiện bất thường trong chấm công (FR-AT-04 & CONTRACT-V1.md):
 * - Vào liên tiếp (CONSECUTIVE_ENTRY)
 * - Ra liên tiếp (CONSECUTIVE_EXIT)
 * - Quét lặp nhanh trong 30s (RAPID_REPEAT_SCAN - BR-03)
 * - Replay JTI giữa các cổng khác nhau (CROSS_GATE_REPLAY - BR-13)
 */
public class AnomalyDetectionTest {

    private InMemoryAttendanceRepository repository;
    private AttendanceProcessingService service;
    private static final ZoneId ZONE = AttendanceProcessingService.FACTORY_ZONE;

    private static final UUID EDGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GATE_1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID GATE_2 = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID FACTORY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    @BeforeEach
    void setUp() {
        repository = new InMemoryAttendanceRepository();
        service = new AttendanceProcessingService(repository, repository, repository);
    }

    private EventEnvelope createEnvelope(UUID empId, String dir, UUID gateId, UUID jti, Instant time, long seq) {
        UUID id = UUID.randomUUID();
        String jtiStr = jti != null ? jti.toString() : UUID.randomUUID().toString();
        String payload = String.format("{\"employeeId\":\"%s\",\"direction\":\"%s\",\"source\":\"ONLINE\",\"jti\":\"%s\"}",
                empId, dir, jtiStr);
        String sha256 = EventEnvelope.computeSha256(payload);
        return new EventEnvelope(
                id,
                EventEnvelope.EVT_ATTENDANCE_RECORDED,
                1,
                EDGE_ID,
                gateId,
                FACTORY_ID,
                seq,
                UUID.randomUUID(),
                time,
                sha256,
                payload
        );
    }

    @Test
    @DisplayName("FR-AT-04: Phát hiện vào liên tiếp khi có 2 sự kiện IN mà không có OUT")
    void testConsecutiveEntryDetection() {
        UUID empId = UUID.nameUUIDFromBytes("EMP_ANOMALY_1".getBytes());
        LocalDate workDate = LocalDate.of(2026, 11, 12);
        Instant in1 = ZonedDateTime.of(workDate, LocalTime.of(6, 0), ZONE).toInstant();
        Instant in2 = ZonedDateTime.of(workDate, LocalTime.of(8, 0), ZONE).toInstant();

        EventEnvelope env1 = createEnvelope(empId, "IN", GATE_1, null, in1, 1);
        EventEnvelope env2 = createEnvelope(empId, "IN", GATE_1, null, in2, 2);

        service.synchronize(new SyncBatchRequest(UUID.randomUUID(), List.of(env1, env2)));

        DailyAttendanceRecord record = repository.findByEmployeeIdAndWorkDate(empId, workDate).orElseThrow();
        assertTrue(record.getFlags().contains(AttendanceAnomaly.CONSECUTIVE_ENTRY.name()),
                "Phải phát hiện bất thường CONSECUTIVE_ENTRY");
    }

    @Test
    @DisplayName("FR-AT-04: Phát hiện ra liên tiếp khi có sự kiện OUT mà chưa có IN")
    void testConsecutiveExitDetection() {
        UUID empId = UUID.nameUUIDFromBytes("EMP_ANOMALY_2".getBytes());
        LocalDate workDate = LocalDate.of(2026, 11, 12);
        Instant out1 = ZonedDateTime.of(workDate, LocalTime.of(14, 0), ZONE).toInstant();

        EventEnvelope env1 = createEnvelope(empId, "OUT", GATE_1, null, out1, 1);

        service.synchronize(new SyncBatchRequest(UUID.randomUUID(), List.of(env1)));

        DailyAttendanceRecord record = repository.findByEmployeeIdAndWorkDate(empId, workDate).orElseThrow();
        assertTrue(record.getFlags().contains(AttendanceAnomaly.CONSECUTIVE_EXIT.name()),
                "Phải phát hiện bất thường CONSECUTIVE_EXIT");
    }

    @Test
    @DisplayName("BR-03: Phát hiện quét lặp nghiệp vụ trong khoảng thời gian < 30 giây")
    void testRapidRepeatScanDetection() {
        UUID empId = UUID.nameUUIDFromBytes("EMP_RAPID".getBytes());
        Instant now = Instant.now();
        Instant fiveSecondsLater = now.plusSeconds(5);

        EventEnvelope env1 = createEnvelope(empId, "IN", GATE_1, UUID.randomUUID(), now, 1);
        EventEnvelope env2 = createEnvelope(empId, "IN", GATE_1, UUID.randomUUID(), fiveSecondsLater, 2);

        service.synchronize(new SyncBatchRequest(UUID.randomUUID(), List.of(env1)));
        service.synchronize(new SyncBatchRequest(UUID.randomUUID(), List.of(env2)));

        AttendanceEvent secondEvent = repository.findByEventId(env2.eventId()).orElseThrow();
        assertTrue(secondEvent.getFlags().contains(AttendanceAnomaly.RAPID_REPEAT_SCAN.name()),
                "Sự kiện thứ hai quét cách 5 giây phải bị gắn cờ RAPID_REPEAT_SCAN");
    }

    @Test
    @DisplayName("BR-13: Phát hiện token cùng JTI được quét tại 2 cổng khác nhau (CROSS_GATE_REPLAY)")
    void testCrossGateReplayDetection() {
        UUID emp1 = UUID.nameUUIDFromBytes("EMP_REPLAY_1".getBytes());
        UUID emp2 = UUID.nameUUIDFromBytes("EMP_REPLAY_2".getBytes());
        Instant time1 = Instant.now();
        Instant time2 = time1.plusSeconds(10);
        UUID sharedJti = UUID.randomUUID();

        // Quét tại GATE_1
        EventEnvelope envGate1 = createEnvelope(emp1, "IN", GATE_1, sharedJti, time1, 1);
        // Quét lại cùng JTI tại GATE_2
        EventEnvelope envGate2 = createEnvelope(emp2, "IN", GATE_2, sharedJti, time2, 2);

        service.synchronize(new SyncBatchRequest(UUID.randomUUID(), List.of(envGate1)));
        service.synchronize(new SyncBatchRequest(UUID.randomUUID(), List.of(envGate2)));

        AttendanceEvent secondEvent = repository.findByEventId(envGate2.eventId()).orElseThrow();
        assertTrue(secondEvent.getFlags().contains(AttendanceAnomaly.CROSS_GATE_REPLAY.name()),
                "Sự kiện thứ hai dùng cùng JTI tại cổng khác phải bị gắn cờ CROSS_GATE_REPLAY");
    }
}
