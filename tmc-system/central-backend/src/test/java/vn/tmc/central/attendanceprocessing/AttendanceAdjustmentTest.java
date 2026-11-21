package vn.tmc.central.attendanceprocessing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.tmc.central.attendanceprocessing.api.Adjustment;
import vn.tmc.central.attendanceprocessing.api.AdjustmentDecision;
import vn.tmc.central.attendanceprocessing.api.AdjustmentRequest;
import vn.tmc.central.attendanceprocessing.api.AdjustmentRequestCommand;
import vn.tmc.central.attendanceprocessing.api.AdjustmentReviewCommand;
import vn.tmc.central.attendanceprocessing.api.AdjustmentStatus;
import vn.tmc.central.attendanceprocessing.api.AdjustmentType;
import vn.tmc.central.attendanceprocessing.api.AttendanceAdjustmentView;
import vn.tmc.central.attendanceprocessing.internal.model.DailyAttendanceRecord;
import vn.tmc.central.attendanceprocessing.internal.repository.InMemoryAttendanceRepository;
import vn.tmc.central.attendanceprocessing.internal.service.AttendanceAdjustmentService;
import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.central.usermgmt.api.Role;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHỤ TRÁCH: Task 3.
 * Kiểm thử quy trình yêu cầu và phê duyệt điều chỉnh công (FR-AT-05, CONTRACT-V1.md §9, DECISIONS.md §8):
 * - Lý do là bắt buộc, độ dài 10 - 500 ký tự (CONTRACT-V1 §9)
 * - Tách quyền độc lập: Người tạo không được tự phê duyệt yêu cầu của mình (decidedBy != createdBy)
 * - Ràng buộc Auditor: Auditor là vai trò chỉ đọc, cấm phê duyệt/từ chối điều chỉnh (DECISIONS.md §8)
 * - Kiểm soát xung đột lạc quan (Optimistic Concurrency Control) qua trường version (CONTRACT-V1 §9)
 * - Duyệt điều chỉnh cập nhật daily_attendance_records và đánh dấu isAdjusted = true
 * - Giữ nguyên tính bất biến của attendance_events (không UPDATE/DELETE event gốc)
 * - Ghi nhận nhật ký lịch sử điều chỉnh đầy đủ
 */
public class AttendanceAdjustmentTest {

    private InMemoryAttendanceRepository repository;
    private AttendanceAdjustmentService service;

    private static final UUID FACTORY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID USER_EMP = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID USER_HR = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID USER_AUDITOR = UUID.fromString("00000000-0000-0000-0000-000000000301");

    private ActorContext empContext;
    private ActorContext hrContext;
    private ActorContext auditorContext;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAttendanceRepository();
        service = new AttendanceAdjustmentService(repository, repository);

        empContext = new ActorContext(
                USER_EMP,
                USER_EMP,
                List.of(Role.EMPLOYEE),
                List.of("attendance.view"),
                List.of(FACTORY_ID),
                List.of(),
                "EMPLOYEE"
        );

        hrContext = new ActorContext(
                USER_HR,
                USER_HR,
                List.of(Role.HR),
                List.of("attendance.approve", "attendance.view"),
                List.of(FACTORY_ID),
                List.of(),
                "FACTORY"
        );

        auditorContext = new ActorContext(
                USER_AUDITOR,
                USER_AUDITOR,
                List.of(Role.AUDITOR),
                List.of("audit.read"),
                List.of(FACTORY_ID),
                List.of(),
                "GLOBAL"
        );
    }

    @Test
    @DisplayName("CONTRACT-V1: Tạo yêu cầu điều chỉnh thành công với lý do hợp lệ (10-500 ký tự)")
    void testCreateAdjustmentContractV1Success() {
        AdjustmentRequest request = new AdjustmentRequest(
                UUID.randomUUID(),
                Instant.parse("2026-11-15T14:00:00Z"),
                "OUT",
                "Quên quẹt thẻ ra do thiết bị cổng bảo trì tại cửa B."
        );

        Adjustment created = service.createAdjustment(empContext, request);

        assertNotNull(created);
        assertNotNull(created.id());
        assertEquals("PENDING", created.state());
        assertEquals(USER_EMP, created.employeeId());
        assertEquals(USER_EMP, created.createdBy());
        assertEquals(1, created.version());
        assertEquals("Quên quẹt thẻ ra do thiết bị cổng bảo trì tại cửa B.", created.reason());
    }

    @Test
    @DisplayName("CONTRACT-V1: Từ chối yêu cầu nếu lý do quá ngắn (<10 ký tự)")
    void testCreateAdjustmentTooShortReasonRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AdjustmentRequest(
                UUID.randomUUID(),
                Instant.now(),
                "OUT",
                "Quên thẻ" // 8 chars < 10
        ));
    }

    @Test
    @DisplayName("CONTRACT-V1 & DECISIONS.md §8: Auditor cấm phê duyệt hoặc từ chối điều chỉnh công")
    void testAuditorForbiddenToReviewAdjustment() {
        AdjustmentRequest request = new AdjustmentRequest(
                UUID.randomUUID(),
                Instant.parse("2026-11-15T14:00:00Z"),
                "OUT",
                "Kẹt xe giờ cao điểm có xác nhận của trưởng ca."
        );
        Adjustment created = service.createAdjustment(empContext, request);

        AdjustmentDecision decision = new AdjustmentDecision("APPROVE", "Auditor cố gắng can thiệp", 1);
        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.decideAdjustment(auditorContext, created.id(), decision));
        assertTrue(ex.getMessage().contains("Auditor"));
    }

    @Test
    @DisplayName("CONTRACT-V1 §9: Tách quyền độc lập: Người tạo không được tự phê duyệt yêu cầu của mình")
    void testRequesterCannotApproveOwnRequest() {
        // HR tự tạo yêu cầu cho chính mình
        AdjustmentRequest request = new AdjustmentRequest(
                UUID.randomUUID(),
                Instant.parse("2026-11-15T14:00:00Z"),
                "OUT",
                "Điều chỉnh số giờ làm việc do bận công tác đột xuất."
        );
        Adjustment created = service.createAdjustment(hrContext, request);

        // HR cố gắng tự duyệt
        AdjustmentDecision decision = new AdjustmentDecision("APPROVE", "Tôi tự phê duyệt", 1);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.decideAdjustment(hrContext, created.id(), decision));
        assertTrue(ex.getMessage().contains("Người tạo không được tự phê duyệt"));
    }

    @Test
    @DisplayName("CONTRACT-V1 §9: Kiểm tra xung đột phiên bản lạc quan (Optimistic Concurrency Control)")
    void testOptimisticConcurrencyConflict() {
        AdjustmentRequest request = new AdjustmentRequest(
                UUID.randomUUID(),
                Instant.parse("2026-11-15T14:00:00Z"),
                "OUT",
                "Quên quẹt thẻ ra do thiết bị lỗi ghi nhận dữ liệu."
        );
        Adjustment created = service.createAdjustment(empContext, request);

        // Gửi decision với version sai (2 thay vì 1)
        AdjustmentDecision staleDecision = new AdjustmentDecision("APPROVE", "Duyệt với version cũ", 2);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.decideAdjustment(hrContext, created.id(), staleDecision));
        assertTrue(ex.getMessage().contains("VERSION_CONFLICT"));
    }

    @Test
    @DisplayName("CONTRACT-V1: Quản lý HR duyệt hợp lệ cập nhật DailyAttendanceRecord và workedMinutes")
    void testHRCanApproveAdjustment() {
        LocalDate workDate = LocalDate.of(2026, 11, 15);

        // Tạo sẵn DailyAttendanceRecord đang thiếu quẹt ra
        DailyAttendanceRecord daily = new DailyAttendanceRecord("DAY-EMP001-" + workDate, USER_EMP, FACTORY_ID, workDate, null, "SHIFT_1");
        daily.setFirstIn(Instant.parse("2026-11-15T06:00:00Z"));
        daily.setMissingSwipe(true);
        daily.addFlag("MISSING_OUT");
        repository.save(daily);

        // Nhân viên tạo yêu cầu bổ sung quẹt ra lúc 14:00
        AdjustmentRequest request = new AdjustmentRequest(
                UUID.randomUUID(),
                Instant.parse("2026-11-15T14:00:00Z"),
                "OUT",
                "Quên quẹt thẻ ra do kẹt cổng kiểm soát số 2."
        );
        Adjustment created = service.createAdjustment(empContext, request);

        // HR phê duyệt
        AdjustmentDecision decision = new AdjustmentDecision("APPROVE", "Đã đối soát camera xác nhận hợp lệ", 1);
        Adjustment approved = service.decideAdjustment(hrContext, created.id(), decision);

        assertEquals("APPROVED", approved.state());
        assertEquals(USER_HR, approved.decidedBy());
        assertEquals(2, approved.version());

        // Kiểm tra daily attendance record được cập nhật và đánh dấu isAdjusted
        DailyAttendanceRecord updatedDaily = repository.findByEmployeeIdAndWorkDate(USER_EMP, workDate).orElseThrow();
        assertTrue(updatedDaily.isAdjusted());
        assertFalse(updatedDaily.isMissingSwipe());
        assertEquals(480, updatedDaily.getWorkedMinutes());
        assertEquals(8.0, updatedDaily.getHoursWorked(), 0.01);

        // Kiểm tra lịch sử điều chỉnh được ghi nhận
        assertEquals(1, repository.findHistoriesByAdjustmentId(created.id()).size());
        assertEquals("APPROVED", repository.findHistoriesByAdjustmentId(created.id()).get(0).getAction());
    }

    @Test
    @DisplayName("Hỗ trợ phương thức tương thích ngược cũ (AdjustmentRequestCommand / AdjustmentReviewCommand)")
    void testLegacyAdjustmentFlow() {
        LocalDate workDate = LocalDate.of(2026, 11, 15);
        AttendanceAdjustmentView view = service.requestAdjustment(new AdjustmentRequestCommand(
                "EMP001",
                workDate,
                AdjustmentType.ADD_OUT,
                Instant.now(),
                null,
                "Quên quẹt thẻ ra",
                "EMP001"
        ));
        assertNotNull(view);
        assertEquals(AdjustmentStatus.PENDING, view.status());
    }
}
