package vn.tmc.central.attendanceprocessing.internal.repository;

import vn.tmc.central.attendanceprocessing.internal.model.AdjustmentHistory;
import vn.tmc.central.attendanceprocessing.internal.model.Attendance;
import vn.tmc.central.attendanceprocessing.internal.model.AttendanceAdjustment;
import vn.tmc.central.attendanceprocessing.internal.model.AttendanceEvent;
import vn.tmc.central.attendanceprocessing.internal.model.DailyAttendanceRecord;
import vn.tmc.central.attendanceprocessing.internal.model.SyncConflict;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * PHỤ TRÁCH: Task 3.
 * Triển khai in-memory thread-safe của các repository chấm công theo CONTRACT-V1.md.
 */
public class InMemoryAttendanceRepository implements
        AttendanceRepository,
        DailyAttendanceRepository,
        AttendanceAdjustmentRepository,
        SyncConflictRepository {

    private static final Comparator<AttendanceEvent> EVENT_WIRE_ORDER =
            Comparator.comparing(AttendanceEvent::getObservedAt, Comparator.reverseOrder())
                    .thenComparing(AttendanceEvent::getEventId, Comparator.reverseOrder());

    private final Map<UUID, AttendanceEvent> eventsByUuid = new ConcurrentHashMap<>();
    private final Map<String, AttendanceEvent> eventsByEdgeAndSeq = new ConcurrentHashMap<>();
    private final Map<UUID, AttendanceEvent> eventsByJti = new ConcurrentHashMap<>();
    private final Map<String, DailyAttendanceRecord> dailyRecords = new ConcurrentHashMap<>();
    private final Map<UUID, AttendanceAdjustment> adjustments = new ConcurrentHashMap<>();
    private final List<AdjustmentHistory> adjustmentHistories = new CopyOnWriteArrayList<>();
    private final List<SyncConflict> syncConflicts = new CopyOnWriteArrayList<>();
    private final Map<String, Long> lastContiguousSequence = new ConcurrentHashMap<>();
    private final Map<String, Attendance> legacyAttendances = new ConcurrentHashMap<>();

    // --- AttendanceRepository ---

    @Override
    public Optional<AttendanceEvent> findByEventId(UUID eventId) {
        if (eventId == null) return Optional.empty();
        return Optional.ofNullable(eventsByUuid.get(eventId));
    }

    @Override
    public boolean existsByEventId(UUID eventId) {
        if (eventId == null) return false;
        return eventsByUuid.containsKey(eventId);
    }

    @Override
    public Optional<AttendanceEvent> findByEdgeIdAndSequenceNo(UUID edgeId, long sequenceNo) {
        if (edgeId == null) return Optional.empty();
        return Optional.ofNullable(eventsByEdgeAndSeq.get(edgeId + ":" + sequenceNo));
    }

    @Override
    public void saveEvent(AttendanceEvent event) {
        if (event == null) return;
        eventsByUuid.putIfAbsent(event.getEventId(), event);
        eventsByEdgeAndSeq.putIfAbsent(event.getEdgeId() + ":" + event.getSequenceNo(), event);
        if (event.getJti() != null) {
            eventsByJti.putIfAbsent(event.getJti(), event);
        }
    }

    @Override
    public List<AttendanceEvent> findByEmployeeIdAndDateRange(UUID employeeId, Instant from, Instant to) {
        return eventsByUuid.values().stream()
                .filter(e -> employeeId == null || employeeId.equals(e.getEmployeeId()))
                .filter(e -> (from == null || !e.getObservedAt().isBefore(from)) &&
                             (to == null || e.getObservedAt().isBefore(to)))
                .sorted(EVENT_WIRE_ORDER)
                .toList();
    }

    @Override
    public List<AttendanceEvent> findByFactoryIdAndDateRange(UUID factoryId, Instant from, Instant to) {
        return eventsByUuid.values().stream()
                .filter(e -> factoryId == null || factoryId.equals(e.getFactoryId()))
                .filter(e -> (from == null || !e.getObservedAt().isBefore(from)) &&
                             (to == null || e.getObservedAt().isBefore(to)))
                .sorted(EVENT_WIRE_ORDER)
                .toList();
    }

    @Override
    public List<AttendanceEvent> findByJti(UUID jti) {
        if (jti == null) return List.of();
        return eventsByUuid.values().stream()
                .filter(e -> jti.equals(e.getJti()))
                .toList();
    }

    @Override
    public Optional<AttendanceEvent> findLastByEmployeeId(UUID employeeId) {
        if (employeeId == null) return Optional.empty();
        return eventsByUuid.values().stream()
                .filter(e -> employeeId.equals(e.getEmployeeId()))
                .max(Comparator.comparing(AttendanceEvent::getObservedAt));
    }

    @Override
    public List<AttendanceEvent> findLatestEventsByFactoryId(UUID factoryId) {
        return eventsByUuid.values().stream()
                .filter(e -> factoryId == null || factoryId.equals(e.getFactoryId()))
                .sorted(EVENT_WIRE_ORDER)
                .toList();
    }

    @Override
    public Optional<Attendance> findById(String id) {
        return Optional.ofNullable(legacyAttendances.get(id));
    }

    @Override
    public void save(Attendance value) {
        if (value != null && value.eventId() != null) {
            legacyAttendances.put(value.eventId(), value);
        }
    }

    // --- DailyAttendanceRepository ---

    @Override
    public Optional<DailyAttendanceRecord> findByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate) {
        String key = employeeId + "_" + workDate;
        return Optional.ofNullable(dailyRecords.get(key));
    }

    @Override
    public List<DailyAttendanceRecord> findByEmployeeIdAndDateRange(UUID employeeId, LocalDate fromDate, LocalDate toDate) {
        return dailyRecords.values().stream()
                .filter(r -> employeeId == null || employeeId.equals(r.getEmployeeId()))
                .filter(r -> (fromDate == null || !r.getWorkDate().isBefore(fromDate)) &&
                             (toDate == null || r.getWorkDate().isBefore(toDate)))
                .sorted(Comparator.comparing(DailyAttendanceRecord::getWorkDate))
                .toList();
    }

    @Override
    public List<DailyAttendanceRecord> findByFactoryIdAndDateRange(UUID factoryId, LocalDate fromDate, LocalDate toDate) {
        return dailyRecords.values().stream()
                .filter(r -> factoryId == null || factoryId.equals(r.getFactoryId()))
                .filter(r -> (fromDate == null || !r.getWorkDate().isBefore(fromDate)) &&
                             (toDate == null || r.getWorkDate().isBefore(toDate)))
                .sorted(Comparator.comparing(DailyAttendanceRecord::getWorkDate))
                .toList();
    }

    @Override
    public void save(DailyAttendanceRecord record) {
        if (record == null) return;
        String key = record.getEmployeeId() + "_" + record.getWorkDate();
        dailyRecords.put(key, record);
    }

    // --- AttendanceAdjustmentRepository ---

    @Override
    public Optional<AttendanceAdjustment> findAdjustmentById(UUID id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(adjustments.get(id));
    }

    @Override
    public List<AttendanceAdjustment> findByEmployeeId(UUID employeeId) {
        return adjustments.values().stream()
                .filter(a -> employeeId == null || employeeId.equals(a.getEmployeeId()))
                .sorted(Comparator.comparing(AttendanceAdjustment::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public List<AttendanceAdjustment> findByState(String state) {
        return adjustments.values().stream()
                .filter(a -> state == null || a.getState().equalsIgnoreCase(state))
                .sorted(Comparator.comparing(AttendanceAdjustment::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public List<AttendanceAdjustment> findAll() {
        return adjustments.values().stream()
                .sorted(Comparator.comparing(AttendanceAdjustment::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public void save(AttendanceAdjustment adjustment) {
        if (adjustment != null && adjustment.getId() != null) {
            adjustments.put(adjustment.getId(), adjustment);
        }
    }

    @Override
    public void saveHistory(AdjustmentHistory history) {
        if (history != null) {
            adjustmentHistories.add(history);
        }
    }

    @Override
    public List<AdjustmentHistory> findHistoriesByAdjustmentId(UUID adjustmentId) {
        return adjustmentHistories.stream()
                .filter(h -> adjustmentId.toString().equals(h.getAdjustmentId()))
                .sorted(Comparator.comparing(AdjustmentHistory::getTimestamp))
                .toList();
    }

    // --- SyncConflictRepository ---

    @Override
    public void recordConflict(SyncConflict conflict) {
        if (conflict != null) {
            syncConflicts.add(conflict);
        }
    }

    @Override
    public List<SyncConflict> findConflicts() {
        return List.copyOf(syncConflicts);
    }

    @Override
    public long getLastContiguousSequenceNo(String edgeId) {
        if (edgeId == null) return 0;
        return lastContiguousSequence.getOrDefault(edgeId, 0L);
    }

    @Override
    public void updateLastContiguousSequenceNo(String edgeId, long sequenceNo) {
        if (edgeId != null) {
            lastContiguousSequence.put(edgeId, sequenceNo);
        }
    }
}
