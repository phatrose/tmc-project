package vn.tmc.central.attendanceprocessing.internal.repository;

import vn.tmc.central.attendanceprocessing.internal.model.Attendance;
import vn.tmc.central.attendanceprocessing.internal.model.AttendanceEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * Port lưu trữ sự kiện quẹt thẻ bất biến (attendance_events).
 */
public interface AttendanceRepository {
    Optional<AttendanceEvent> findByEventId(UUID eventId);
    boolean existsByEventId(UUID eventId);
    default Optional<AttendanceEvent> findByEdgeIdAndSequenceNo(UUID edgeId, long sequenceNo) {
        return Optional.empty();
    }
    void saveEvent(AttendanceEvent event);
    List<AttendanceEvent> findByEmployeeIdAndDateRange(UUID employeeId, Instant from, Instant to);
    List<AttendanceEvent> findByFactoryIdAndDateRange(UUID factoryId, Instant from, Instant to);
    List<AttendanceEvent> findByJti(UUID jti);
    Optional<AttendanceEvent> findLastByEmployeeId(UUID employeeId);
    List<AttendanceEvent> findLatestEventsByFactoryId(UUID factoryId);

    // Tương thích ngược
    Optional<Attendance> findById(String id);
    void save(Attendance value);
}
