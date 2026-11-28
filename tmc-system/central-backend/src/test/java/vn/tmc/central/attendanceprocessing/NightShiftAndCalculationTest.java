package vn.tmc.central.attendanceprocessing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.tmc.central.attendanceprocessing.api.AttendanceAnomaly;
import vn.tmc.central.attendanceprocessing.internal.model.DailyAttendanceRecord;
import vn.tmc.central.attendanceprocessing.internal.repository.InMemoryAttendanceRepository;
import vn.tmc.central.attendanceprocessing.internal.service.AttendanceProcessingService;
import vn.tmc.contracts.EventEnvelope;
import vn.tmc.contracts.SyncBatchRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHỤ TRÁCH: Task 3.
 * Kiểm thử tính công, ca đêm và ca đêm qua ranh giới tháng (DECISIONS.md §1 & CONTRACT-V1.md):
 * - Ca đêm bắt đầu cuối tháng trước (22:00 ngày 31/10), kết thúc 06:00 ngày 01/11, vẫn thuộc tháng trước.
 * - workDate của ca đêm là ngày bắt đầu ca (31/10).
 * - Tính công: workedMinutes, đi trễ, về sớm, thiếu quẹt theo ca.
 */
public class NightShiftAndCalculationTest {

    private InMemoryAttendanceRepository repository;
    private AttendanceProcessingService service;
    private static final ZoneId ZONE = AttendanceProcessingService.FACTORY_ZONE;

    private static final UUID EDGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID FACTORY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    @BeforeEach
    void setUp() {
        repository = new InMemoryAttendanceRepository();
        service = new AttendanceProcessingService(repository, repository, repository);
    }

    private EventEnvelope createEvent(UUID empId, String dir, ZonedDateTime time, long seq) {
        UUID id = UUID.randomUUID();
        String payload = String.format("{\"employeeId\":\"%s\",\"direction\":\"%s\",\"source\":\"ONLINE\"}", empId, dir);
        String sha256 = EventEnvelope.computeSha256(payload);
        return new EventEnvelope(
                id,
                EventEnvelope.EVT_ATTENDANCE_RECORDED,
                1,
                EDGE_ID,
                GATE_ID,
                FACTORY_ID,
                seq,
                UUID.randomUUID(),
                time.toInstant(),
                sha256,
                payload
        );
    }

    @Test
    @DisplayName("FR-AT-03: Ca ngày chuẩn 06:00 - 14:00 tính đủ 8 giờ công (480 phút)")
    void testNormalDayShiftCalculation() {
        UUID emp001 = UUID.nameUUIDFromBytes("EMP001".getBytes());
        LocalDate workDate = LocalDate.of(2026, 11, 10);
        ZonedDateTime inTime = ZonedDateTime.of(workDate, LocalTime.of(6, 0), ZONE);
        ZonedDateTime outTime = ZonedDateTime.of(workDate, LocalTime.of(14, 0), ZONE);

        EventEnvelope inEnv = createEvent(emp001, "IN", inTime, 1);
        EventEnvelope outEnv = createEvent(emp001, "OUT", outTime, 2);

        service.synchronize(new SyncBatchRequest(UUID.randomUUID(), List.of(inEnv, outEnv)));

        DailyAttendanceRecord record = repository.findByEmployeeIdAndWorkDate(emp001, workDate).orElseThrow();
        assertEquals(8.0, record.getHoursWorked(), 0.01);
        assertEquals(480, record.getWorkedMinutes());
        assertFalse(record.isLate());
        assertFalse(record.isEarlyLeave());
        assertFalse(record.isMissingSwipe());
        assertEquals("COMPLETED", record.getStatus());
    }

    @Test
    @DisplayName("FR-AT-03: Phát hiện đi trễ và về sớm khi vượt quá dung sai")
    void testLateArrivalAndEarlyDeparture() {
        UUID emp002 = UUID.nameUUIDFromBytes("EMP002".getBytes());
        LocalDate workDate = LocalDate.of(2026, 11, 10);
        // Ca 1 bắt đầu 06:00, tolerance 15 phút. Vào lúc 06:30 -> trễ 30 phút
        ZonedDateTime inTime = ZonedDateTime.of(workDate, LocalTime.of(6, 30), ZONE);
        // Ca 1 kết thúc 14:00, tolerance 15 phút. Ra lúc 13:30 -> về sớm 30 phút
        ZonedDateTime outTime = ZonedDateTime.of(workDate, LocalTime.of(13, 30), ZONE);

        service.synchronize(new SyncBatchRequest(UUID.randomUUID(), List.of(
                createEvent(emp002, "IN", inTime, 1),
                createEvent(emp002, "OUT", outTime, 2)
        )));

        DailyAttendanceRecord record = repository.findByEmployeeIdAndWorkDate(emp002, workDate).orElseThrow();
        assertTrue(record.isLate());
        assertEquals(30, record.getLateMinutes());
        assertTrue(record.isEarlyLeave());
        assertEquals(30, record.getEarlyMinutes());
        assertTrue(record.getFlags().contains(AttendanceAnomaly.LATE_ARRIVAL.name()));
        assertTrue(record.getFlags().contains(AttendanceAnomaly.EARLY_DEPARTURE.name()));
    }

    @Test
    @DisplayName("FR-AT-03: Phát hiện thiếu quẹt ra khi chỉ có sự kiện vào (workedMinutes = null)")
    void testMissingSwipeDetection() {
        UUID emp003 = UUID.nameUUIDFromBytes("EMP003".getBytes());
        LocalDate workDate = LocalDate.of(2026, 11, 10);
        ZonedDateTime inTime = ZonedDateTime.of(workDate, LocalTime.of(6, 0), ZONE);

        service.synchronize(new SyncBatchRequest(UUID.randomUUID(), List.of(
                createEvent(emp003, "IN", inTime, 1)
        )));

        DailyAttendanceRecord record = repository.findByEmployeeIdAndWorkDate(emp003, workDate).orElseThrow();
        assertTrue(record.isMissingSwipe());
        assertNull(record.getWorkedMinutes(), "CONTRACT-V1: Thiếu quẹt thì workedMinutes phải là null");
        assertTrue(record.getFlags().contains(AttendanceAnomaly.MISSING_OUT.name()));
        assertEquals("IN_PROGRESS", record.getStatus());
    }

    @Test
    @DisplayName("DECISIONS.md §1: Ca đêm bắt đầu cuối tháng trước (31/10 22:00) kết thúc (01/11 06:00) vẫn thuộc tháng 10")
    void testNightShiftCrossMonthBoundaryDECISIONS() {
        UUID empNight = UUID.nameUUIDFromBytes("EMP_NIGHT".getBytes());
        // Ngày cuối tháng 10: 2026-10-31
        LocalDate oct31 = LocalDate.of(2026, 10, 31);
        LocalDate nov01 = LocalDate.of(2026, 11, 1);

        // Vào ca đêm lúc 22:00 ngày 31/10
        ZonedDateTime nightIn = ZonedDateTime.of(oct31, LocalTime.of(22, 0), ZONE);
        // Ra ca đêm lúc 06:00 ngày 01/11 (sáng hôm sau)
        ZonedDateTime nightOut = ZonedDateTime.of(nov01, LocalTime.of(6, 0), ZONE);

        EventEnvelope inEnv = createEvent(empNight, "IN", nightIn, 1);
        EventEnvelope outEnv = createEvent(empNight, "OUT", nightOut, 2);

        service.synchronize(new SyncBatchRequest(UUID.randomUUID(), List.of(inEnv, outEnv)));

        // 1. Kiểm tra ngày làm việc (workDate) được gán là 2026-10-31
        DailyAttendanceRecord recordOct = repository.findByEmployeeIdAndWorkDate(empNight, oct31).orElse(null);
        assertNotNull(recordOct, "Bản ghi phải thuộc ngày 31/10");
        assertEquals(oct31, recordOct.getWorkDate());
        assertEquals("SHIFT_NIGHT", recordOct.getShiftCode());
        assertEquals(8.0, recordOct.getHoursWorked(), 0.01);
        assertEquals(480, recordOct.getWorkedMinutes());
        assertFalse(recordOct.isMissingSwipe());

        // 2. Kiểm tra ngày 01/11 KHÔNG có bản ghi tính đè
        DailyAttendanceRecord recordNov = repository.findByEmployeeIdAndWorkDate(empNight, nov01).orElse(null);
        assertNull(recordNov, "Ngày 01/11 không được tính là ngày bắt đầu của ca này");
    }
}
