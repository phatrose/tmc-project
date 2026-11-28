package vn.tmc.central.attendanceprocessing.internal.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tmc.central.attendanceprocessing.internal.model.Attendance;
import vn.tmc.central.attendanceprocessing.internal.model.AttendanceEvent;
import vn.tmc.central.attendanceprocessing.internal.model.DailyAttendanceRecord;
import vn.tmc.central.attendanceprocessing.internal.model.SyncConflict;
import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.contracts.AttendancePayload.Source;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 3.
 * Adapter PostgreSQL (JdbcTemplate) cho AttendanceRepository (append-only attendance_events),
 * DailyAttendanceRepository (daily_attendance_records), và SyncConflictRepository (sync_conflicts, edge_sync_cursors)
 * theo migration V010.
 */
public class JdbcAttendanceRepository implements
        AttendanceRepository,
        DailyAttendanceRepository,
        SyncConflictRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_STRING = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final Map<String, Attendance> legacyAttendances = new ConcurrentHashMap<>();

    public JdbcAttendanceRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    // ─────────────────────────── AttendanceRepository ─────────────────────────

    @Override
    public Optional<AttendanceEvent> findByEventId(UUID eventId) {
        if (eventId == null) return Optional.empty();
        List<AttendanceEvent> list = jdbc.query(
                "SELECT * FROM attendance_events WHERE event_id = ?",
                this::mapEvent, eventId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public boolean existsByEventId(UUID eventId) {
        if (eventId == null) return false;
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM attendance_events WHERE event_id = ?",
                Integer.class, eventId);
        return count != null && count > 0;
    }

    @Override
    public Optional<AttendanceEvent> findByEdgeIdAndSequenceNo(UUID edgeId, long sequenceNo) {
        if (edgeId == null) return Optional.empty();
        List<AttendanceEvent> list = jdbc.query(
                "SELECT * FROM attendance_events WHERE edge_id = ? AND sequence_no = ?",
                this::mapEvent, edgeId, sequenceNo);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public void saveEvent(AttendanceEvent e) {
        if (e == null) return;
        jdbc.update("""
                INSERT INTO attendance_events (
                    event_id, event_type, schema_version, edge_id, gate_id, factory_id,
                    sequence_no, correlation_id, observed_at, received_at, employee_id,
                    direction, source, jti, kid, token_issued_at, command_id, operator_id,
                    reason, flags_json, payload_sha256, raw_payload
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                e.getEventId(),
                e.getEventType(),
                e.getSchemaVersion(),
                e.getEdgeId(),
                e.getGateId(),
                e.getFactoryId(),
                e.getSequenceNo(),
                e.getCorrelationId(),
                Timestamp.from(e.getObservedAt()),
                Timestamp.from(e.getReceivedAt()),
                e.getEmployeeId(),
                e.getDirection() != null ? e.getDirection().name() : null,
                e.getSource() != null ? e.getSource().name() : Source.ONLINE.name(),
                e.getJti(),
                e.getKid(),
                e.getTokenIssuedAt() != null ? Timestamp.from(e.getTokenIssuedAt()) : null,
                e.getCommandId(),
                e.getOperatorId(),
                e.getReason(),
                toJson(e.getFlags()),
                e.getPayloadSha256(),
                e.getRawPayload());
    }

    @Override
    public List<AttendanceEvent> findByEmployeeIdAndDateRange(UUID employeeId, Instant from, Instant to) {
        StringBuilder sql = new StringBuilder("SELECT * FROM attendance_events WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (employeeId != null) {
            sql.append(" AND employee_id = ?");
            params.add(employeeId);
        }
        if (from != null) {
            sql.append(" AND observed_at >= ?");
            params.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND observed_at < ?");
            params.add(Timestamp.from(to));
        }
        sql.append(" ORDER BY observed_at DESC, event_id DESC");
        return jdbc.query(sql.toString(), this::mapEvent, params.toArray());
    }

    @Override
    public List<AttendanceEvent> findByFactoryIdAndDateRange(UUID factoryId, Instant from, Instant to) {
        StringBuilder sql = new StringBuilder("SELECT * FROM attendance_events WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (factoryId != null) {
            sql.append(" AND factory_id = ?");
            params.add(factoryId);
        }
        if (from != null) {
            sql.append(" AND observed_at >= ?");
            params.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND observed_at < ?");
            params.add(Timestamp.from(to));
        }
        sql.append(" ORDER BY observed_at DESC, event_id DESC");
        return jdbc.query(sql.toString(), this::mapEvent, params.toArray());
    }

    @Override
    public List<AttendanceEvent> findByJti(UUID jti) {
        if (jti == null) return List.of();
        return jdbc.query("SELECT * FROM attendance_events WHERE jti = ?", this::mapEvent, jti);
    }

    @Override
    public Optional<AttendanceEvent> findLastByEmployeeId(UUID employeeId) {
        if (employeeId == null) return Optional.empty();
        List<AttendanceEvent> list = jdbc.query(
                "SELECT * FROM attendance_events WHERE employee_id = ? ORDER BY observed_at DESC LIMIT 1",
                this::mapEvent, employeeId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<AttendanceEvent> findLatestEventsByFactoryId(UUID factoryId) {
        if (factoryId == null) {
            return jdbc.query("SELECT * FROM attendance_events ORDER BY observed_at DESC, event_id DESC", this::mapEvent);
        }
        return jdbc.query(
                "SELECT * FROM attendance_events WHERE factory_id = ? ORDER BY observed_at DESC, event_id DESC",
                this::mapEvent, factoryId);
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

    // ─────────────────────────── DailyAttendanceRepository ────────────────────

    @Override
    public Optional<DailyAttendanceRecord> findByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate) {
        if (employeeId == null || workDate == null) return Optional.empty();
        List<DailyAttendanceRecord> list = jdbc.query(
                "SELECT * FROM daily_attendance_records WHERE employee_id = ? AND work_date = ?",
                this::mapDaily, employeeId, Date.valueOf(workDate));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<DailyAttendanceRecord> findByEmployeeIdAndDateRange(UUID employeeId, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder("SELECT * FROM daily_attendance_records WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (employeeId != null) {
            sql.append(" AND employee_id = ?");
            params.add(employeeId);
        }
        if (fromDate != null) {
            sql.append(" AND work_date >= ?");
            params.add(Date.valueOf(fromDate));
        }
        if (toDate != null) {
            sql.append(" AND work_date < ?");
            params.add(Date.valueOf(toDate));
        }
        sql.append(" ORDER BY work_date ASC");
        return jdbc.query(sql.toString(), this::mapDaily, params.toArray());
    }

    @Override
    public List<DailyAttendanceRecord> findByFactoryIdAndDateRange(UUID factoryId, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder("SELECT * FROM daily_attendance_records WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (factoryId != null) {
            sql.append(" AND factory_id = ?");
            params.add(factoryId);
        }
        if (fromDate != null) {
            sql.append(" AND work_date >= ?");
            params.add(Date.valueOf(fromDate));
        }
        if (toDate != null) {
            sql.append(" AND work_date < ?");
            params.add(Date.valueOf(toDate));
        }
        sql.append(" ORDER BY work_date ASC");
        return jdbc.query(sql.toString(), this::mapDaily, params.toArray());
    }

    @Override
    public void save(DailyAttendanceRecord r) {
        if (r == null) return;
        jdbc.update("""
                INSERT INTO daily_attendance_records (
                    id, employee_id, factory_id, work_date, shift_id, shift_code,
                    first_in, last_out, worked_minutes, late_minutes, early_minutes,
                    is_missing_swipe, is_adjusted, flags_json, status, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (employee_id, work_date) DO UPDATE SET
                    shift_id = EXCLUDED.shift_id,
                    shift_code = EXCLUDED.shift_code,
                    first_in = EXCLUDED.first_in,
                    last_out = EXCLUDED.last_out,
                    worked_minutes = EXCLUDED.worked_minutes,
                    late_minutes = EXCLUDED.late_minutes,
                    early_minutes = EXCLUDED.early_minutes,
                    is_missing_swipe = EXCLUDED.is_missing_swipe,
                    is_adjusted = EXCLUDED.is_adjusted,
                    flags_json = EXCLUDED.flags_json,
                    status = EXCLUDED.status,
                    version = EXCLUDED.version
                """,
                r.getId(),
                r.getEmployeeId(),
                r.getFactoryId(),
                Date.valueOf(r.getWorkDate()),
                r.getShiftId(),
                r.getShiftCode(),
                r.getFirstIn() != null ? Timestamp.from(r.getFirstIn()) : null,
                r.getLastOut() != null ? Timestamp.from(r.getLastOut()) : null,
                r.getWorkedMinutes(),
                r.getLateMinutes(),
                r.getEarlyMinutes(),
                r.isMissingSwipe(),
                r.isAdjusted(),
                toJson(r.getFlags()),
                r.getStatus(),
                r.getVersion());
    }

    // ─────────────────────────── SyncConflictRepository ───────────────────────

    @Override
    public void recordConflict(SyncConflict c) {
        if (c == null) return;
        jdbc.update("""
                INSERT INTO sync_conflicts (
                    id, event_id, edge_id, existing_hash, incoming_hash,
                    occurred_at, reported_at, details
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                c.getId(),
                c.getEventId(),
                c.getEdgeId(),
                c.getExistingHash(),
                c.getIncomingHash(),
                c.getOccurredAt() != null ? Timestamp.from(c.getOccurredAt()) : null,
                Timestamp.from(c.getReportedAt()),
                c.getDetails());
    }

    @Override
    public List<SyncConflict> findConflicts() {
        return jdbc.query("SELECT * FROM sync_conflicts ORDER BY reported_at DESC", (rs, rowNum) -> {
            Timestamp occ = rs.getTimestamp("occurred_at");
            return new SyncConflict(
                    rs.getString("id"),
                    rs.getObject("event_id", UUID.class),
                    rs.getString("edge_id"),
                    rs.getString("existing_hash"),
                    rs.getString("incoming_hash"),
                    occ != null ? occ.toInstant() : null,
                    rs.getTimestamp("reported_at").toInstant(),
                    rs.getString("details"));
        });
    }

    @Override
    public long getLastContiguousSequenceNo(String edgeId) {
        if (edgeId == null) return 0L;
        List<Long> res = jdbc.query(
                "SELECT last_contiguous_sequence_no FROM edge_sync_cursors WHERE edge_id = ?",
                (rs, rowNum) -> rs.getLong("last_contiguous_sequence_no"),
                edgeId);
        return res.isEmpty() ? 0L : res.get(0);
    }

    @Override
    public void updateLastContiguousSequenceNo(String edgeId, long sequenceNo) {
        if (edgeId == null) return;
        jdbc.update("""
                INSERT INTO edge_sync_cursors (edge_id, last_contiguous_sequence_no, updated_at)
                VALUES (?, ?, NOW())
                ON CONFLICT (edge_id) DO UPDATE SET
                    last_contiguous_sequence_no = GREATEST(edge_sync_cursors.last_contiguous_sequence_no, EXCLUDED.last_contiguous_sequence_no),
                    updated_at = NOW()
                """,
                edgeId, sequenceNo);
    }

    // ─────────────────────────── Row Mappers ──────────────────────────────────

    private AttendanceEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        String dirStr = rs.getString("direction");
        String srcStr = rs.getString("source");
        Timestamp tokenIssuedAt = rs.getTimestamp("token_issued_at");
        return new AttendanceEvent(
                rs.getObject("event_id", UUID.class),
                rs.getString("event_type"),
                rs.getInt("schema_version"),
                rs.getObject("edge_id", UUID.class),
                rs.getObject("gate_id", UUID.class),
                rs.getObject("factory_id", UUID.class),
                rs.getLong("sequence_no"),
                rs.getObject("correlation_id", UUID.class),
                rs.getTimestamp("observed_at").toInstant(),
                rs.getTimestamp("received_at").toInstant(),
                rs.getObject("employee_id", UUID.class),
                dirStr != null ? Direction.valueOf(dirStr) : null,
                srcStr != null ? Source.valueOf(srcStr) : Source.ONLINE,
                rs.getObject("jti", UUID.class),
                rs.getString("kid"),
                tokenIssuedAt != null ? tokenIssuedAt.toInstant() : null,
                rs.getObject("command_id", UUID.class),
                rs.getObject("operator_id", UUID.class),
                rs.getString("reason"),
                fromJson(rs.getString("flags_json")),
                rs.getString("payload_sha256"),
                rs.getString("raw_payload"));
    }

    private DailyAttendanceRecord mapDaily(ResultSet rs, int rowNum) throws SQLException {
        DailyAttendanceRecord r = new DailyAttendanceRecord(
                rs.getString("id"),
                rs.getObject("employee_id", UUID.class),
                rs.getObject("factory_id", UUID.class),
                rs.getDate("work_date").toLocalDate(),
                rs.getObject("shift_id", UUID.class),
                rs.getString("shift_code"));
        Timestamp firstIn = rs.getTimestamp("first_in");
        Timestamp lastOut = rs.getTimestamp("last_out");
        r.setFirstIn(firstIn != null ? firstIn.toInstant() : null);
        r.setLastOut(lastOut != null ? lastOut.toInstant() : null);
        Object wm = rs.getObject("worked_minutes");
        r.setWorkedMinutes(wm != null ? ((Number) wm).intValue() : null);
        r.setLateMinutes(rs.getInt("late_minutes"));
        r.setEarlyMinutes(rs.getInt("early_minutes"));
        r.setMissingSwipe(rs.getBoolean("is_missing_swipe"));
        r.setAdjusted(rs.getBoolean("is_adjusted"));
        for (String flag : fromJson(rs.getString("flags_json"))) {
            r.addFlag(flag);
        }
        r.setStatus(rs.getString("status"));
        r.setVersion(rs.getInt("version"));
        return r;
    }

    private static String toJson(List<String> flags) {
        try {
            return JSON.writeValueAsString(flags == null ? List.of() : flags);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static List<String> fromJson(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return JSON.readValue(raw, LIST_STRING);
        } catch (Exception e) {
            return List.of();
        }
    }
}
