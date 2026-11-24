package vn.tmc.central.attendanceprocessing.api;

import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.contracts.Page;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: Contract công khai truy vấn và xử lý chấm công theo CONTRACT-V1.md §9 & §10.
 * 
 * ENDPOINTS & QUYỀN (CONTRACT-V1 §9):
 * - GET /attendance/events: Page<AttendanceEventView>
 * - GET /attendance/records: Page<DailyAttendance>
 * - GET /attendance/presence: PresenceView
 * - POST /attendance/adjustments: Adjustment
 * - GET /attendance/adjustments: Page<Adjustment>
 * - POST /attendance/adjustments/{id}/decision: Adjustment
 */
public interface AttendanceQueries {
    /**
     * Truy vấn bản ghi tổng hợp công ngày theo khoảng [from, to) và scope danh tính tin cậy.
     * CONTRACT-V1 §10: ActorContext, AttendanceFilter -> Page<DailyAttendance>
     */
    Page<DailyAttendance> dailyRecords(ActorContext context, AttendanceFilter filter);

    /**
     * Tra cứu nhật ký sự kiện quẹt thẻ bất biến (sắp theo occurredAt giảm dần rồi id giảm dần).
     */
    Page<AttendanceEventView> queryEvents(ActorContext context, UUID employeeId, UUID factoryId, Instant from, Instant to, int page, int size);

    /**
     * Lấy danh sách hiện diện tại nhà máy phục vụ an ninh / sơ tán.
     */
    PresenceView getPresence(UUID factoryId);

    /**
     * Tra cứu danh sách yêu cầu điều chỉnh công.
     */
    Page<Adjustment> queryAdjustments(ActorContext context, UUID employeeId, String state, int page, int size);

    /**
     * Tạo yêu cầu điều chỉnh công mới (POST /attendance/adjustments).
     */
    Adjustment createAdjustment(ActorContext context, AdjustmentRequest request);

    /**
     * Quyết định phê duyệt hoặc từ chối điều chỉnh (POST /attendance/adjustments/{id}/decision).
     */
    Adjustment decideAdjustment(ActorContext context, UUID adjustmentId, AdjustmentDecision decision);

    // --- Các phương thức tương thích ngược và tiện ích tổng hợp ---
    AttendanceView findAttendance(String employeeId);
    List<DailyAttendanceView> queryDailyRecords(String employeeId, LocalDate fromDate, LocalDate toDate);
    MonthlyAttendanceSummaryView getMonthlySummary(String employeeId, YearMonth period, String factoryId);
}
