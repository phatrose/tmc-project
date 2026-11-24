package vn.tmc.central.attendanceprocessing.internal.repository;

import vn.tmc.central.attendanceprocessing.api.AdjustmentStatus;
import vn.tmc.central.attendanceprocessing.internal.model.AdjustmentHistory;
import vn.tmc.central.attendanceprocessing.internal.model.AttendanceAdjustment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * Port lưu trữ yêu cầu và lịch sử điều chỉnh công (attendance_adjustments, adjustment_histories).
 */
public interface AttendanceAdjustmentRepository {
    Optional<AttendanceAdjustment> findAdjustmentById(UUID id);
    List<AttendanceAdjustment> findByEmployeeId(UUID employeeId);
    List<AttendanceAdjustment> findByState(String state);
    List<AttendanceAdjustment> findAll();
    void save(AttendanceAdjustment adjustment);

    void saveHistory(AdjustmentHistory history);
    List<AdjustmentHistory> findHistoriesByAdjustmentId(UUID adjustmentId);
}
