package vn.tmc.central.attendanceprocessing.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * PHỤ TRÁCH: Task 3.
 * View DTO cho bản ghi tổng hợp chấm công ngày (daily attendance).
 */
public record DailyAttendanceView(
        String id,
        String employeeId,
        String factoryId,
        LocalDate workDate,
        String shiftCode,
        Instant firstIn,
        Instant lastOut,
        double hoursWorked,
        boolean isLate,
        long lateMinutes,
        boolean isEarlyLeave,
        long earlyMinutes,
        boolean isMissingSwipe,
        boolean isAdjusted,
        Set<String> anomalyFlags,
        String status
) {}
