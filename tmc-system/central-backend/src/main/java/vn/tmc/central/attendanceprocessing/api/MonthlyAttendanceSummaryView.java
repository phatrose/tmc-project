package vn.tmc.central.attendanceprocessing.api;

/**
 * PHỤ TRÁCH: Task 3.
 * View DTO cho tổng hợp chấm công tháng / kỳ payroll.
 */
public record MonthlyAttendanceSummaryView(
        String employeeId,
        String factoryId,
        String period, // e.g. "2026-10"
        int totalWorkDays,
        double totalHoursWorked,
        int lateCount,
        int earlyLeaveCount,
        int missingSwipeCount,
        int anomalyCount,
        String status
) {}
