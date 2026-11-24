package vn.tmc.central.attendanceprocessing.api;

import java.util.List;

/**
 * PHỤ TRÁCH: Task 3.
 * DTO đại diện cho tổng quan trạng thái chấm công của nhân viên.
 */
public record AttendanceView(
        String employeeId,
        String currentStatus,
        DailyAttendanceView todayRecord,
        List<DailyAttendanceView> recentRecords,
        MonthlyAttendanceSummaryView currentMonthSummary
) {
    public AttendanceView(String eventId, String employeeId, String direction) {
        this(employeeId, direction, null, List.of(), null);
    }
}
