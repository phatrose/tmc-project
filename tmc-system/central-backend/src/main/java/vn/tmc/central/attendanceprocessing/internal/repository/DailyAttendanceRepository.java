package vn.tmc.central.attendanceprocessing.internal.repository;

import vn.tmc.central.attendanceprocessing.internal.model.DailyAttendanceRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * Port lưu trữ bản ghi tổng hợp công ngày (daily_attendance_records).
 */
public interface DailyAttendanceRepository {
    Optional<DailyAttendanceRecord> findByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);
    List<DailyAttendanceRecord> findByEmployeeIdAndDateRange(UUID employeeId, LocalDate fromDate, LocalDate toDate);
    List<DailyAttendanceRecord> findByFactoryIdAndDateRange(UUID factoryId, LocalDate fromDate, LocalDate toDate);
    void save(DailyAttendanceRecord record);
}
