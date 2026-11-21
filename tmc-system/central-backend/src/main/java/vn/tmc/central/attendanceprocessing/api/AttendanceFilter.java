package vn.tmc.central.attendanceprocessing.api;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO bộ lọc khoảng thời gian theo CONTRACT-V1.md §2 & §10.
 * AttendanceFilter = {factoryId:UUID?, employeeId:UUID?, from:LocalDate, to:LocalDate, page:int, size:int}
 * 
 * QUY ƯỚC:
 * - Khoảng [from, to) là nửa mở: từ đầu ngày from đến trước đầu ngày to trong timezone nhà máy.
 * - from < to, tối đa 366 ngày.
 */
public record AttendanceFilter(
        UUID factoryId,
        UUID employeeId,
        LocalDate from,
        LocalDate to,
        int page,
        int size
) {
    public AttendanceFilter {
        Objects.requireNonNull(from, "from date is required");
        Objects.requireNonNull(to, "to date is required");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be strictly before to date");
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days > 366) {
            throw new IllegalArgumentException("Query range cannot exceed 366 days, got: " + days);
        }
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
    }
}
