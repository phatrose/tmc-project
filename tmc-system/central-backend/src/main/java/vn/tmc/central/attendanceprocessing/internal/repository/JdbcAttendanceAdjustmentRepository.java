package vn.tmc.central.attendanceprocessing.internal.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tmc.central.attendanceprocessing.internal.model.AdjustmentHistory;
import vn.tmc.central.attendanceprocessing.internal.model.AttendanceAdjustment;

/**
 * PHỤ TRÁCH: Task 3 / Task 8 — tích hợp với Task 3.
 * Adapter PostgreSQL JDBC bền vững cho attendance_adjustments và adjustment_histories (V009).
 */
public final class JdbcAttendanceAdjustmentRepository implements AttendanceAdjustmentRepository {

    private final JdbcTemplate jdbc;

    public JdbcAttendanceAdjustmentRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    @Override
    public Optional<AttendanceAdjustment> findAdjustmentById(UUID id) {
        List<AttendanceAdjustment> rows = jdbc.query(
                "SELECT id, event_id, employee_id, created_by, proposed_occurred_at, proposed_direction, "
                        + "reason, state, decided_by, decision_reason, created_at, decided_at, version "
                        + "FROM attendance_adjustments WHERE id = ?",
                JdbcAttendanceAdjustmentRepository::mapAdjustment,
                id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<AttendanceAdjustment> findByEmployeeId(UUID employeeId) {
        return jdbc.query(
                "SELECT id, event_id, employee_id, created_by, proposed_occurred_at, proposed_direction, "
                        + "reason, state, decided_by, decision_reason, created_at, decided_at, version "
                        + "FROM attendance_adjustments WHERE employee_id = ? ORDER BY created_at DESC, id DESC",
                JdbcAttendanceAdjustmentRepository::mapAdjustment,
                employeeId);
    }

    @Override
    public List<AttendanceAdjustment> findByState(String state) {
        return jdbc.query(
                "SELECT id, event_id, employee_id, created_by, proposed_occurred_at, proposed_direction, "
                        + "reason, state, decided_by, decision_reason, created_at, decided_at, version "
                        + "FROM attendance_adjustments WHERE UPPER(state) = UPPER(?) ORDER BY created_at DESC, id DESC",
                JdbcAttendanceAdjustmentRepository::mapAdjustment,
                state);
    }

    @Override
    public List<AttendanceAdjustment> findAll() {
        return jdbc.query(
                "SELECT id, event_id, employee_id, created_by, proposed_occurred_at, proposed_direction, "
                        + "reason, state, decided_by, decision_reason, created_at, decided_at, version "
                        + "FROM attendance_adjustments ORDER BY created_at DESC, id DESC",
                JdbcAttendanceAdjustmentRepository::mapAdjustment);
    }

    @Override
    public void save(AttendanceAdjustment a) {
        jdbc.update(
                "INSERT INTO attendance_adjustments "
                        + "(id, event_id, employee_id, created_by, proposed_occurred_at, proposed_direction, "
                        + "reason, state, decided_by, decision_reason, created_at, decided_at, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET "
                        + "state = EXCLUDED.state, decided_by = EXCLUDED.decided_by, "
                        + "decision_reason = EXCLUDED.decision_reason, decided_at = EXCLUDED.decided_at, "
                        + "version = EXCLUDED.version",
                a.getId(),
                a.getEventId(),
                a.getEmployeeId(),
                a.getCreatedBy(),
                toTs(a.getProposedOccurredAt()),
                a.getProposedDirection(),
                a.getReason(),
                a.getState(),
                a.getDecidedBy(),
                a.getDecisionReason(),
                toTs(a.getCreatedAt()),
                toTs(a.getDecidedAt()),
                a.getVersion());
    }

    @Override
    public void saveHistory(AdjustmentHistory history) {
        jdbc.update(
                "INSERT INTO adjustment_histories (id, adjustment_id, action, actor_id, occurred_at, note) "
                        + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                history.getId(),
                UUID.fromString(history.getAdjustmentId()),
                history.getAction(),
                history.getActorId(),
                toTs(history.getTimestamp()),
                history.getNote());
    }

    @Override
    public List<AdjustmentHistory> findHistoriesByAdjustmentId(UUID adjustmentId) {
        return jdbc.query(
                "SELECT id, adjustment_id, action, actor_id, occurred_at, note "
                        + "FROM adjustment_histories WHERE adjustment_id = ? ORDER BY occurred_at ASC",
                (rs, rowNum) -> new AdjustmentHistory(
                        rs.getString("id"),
                        rs.getObject("adjustment_id", UUID.class).toString(),
                        rs.getString("action"),
                        rs.getString("actor_id"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("note")),
                adjustmentId);
    }

    private static AttendanceAdjustment mapAdjustment(ResultSet rs, int rowNum) throws SQLException {
        AttendanceAdjustment adj = new AttendanceAdjustment(
                rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getObject("employee_id", UUID.class),
                rs.getObject("created_by", UUID.class),
                fromTs(rs.getTimestamp("proposed_occurred_at")),
                rs.getString("proposed_direction"),
                rs.getString("reason"),
                fromTs(rs.getTimestamp("created_at")));
        adj.setState(rs.getString("state"));
        adj.setDecidedBy(rs.getObject("decided_by", UUID.class));
        adj.setDecisionReason(rs.getString("decision_reason"));
        adj.setDecidedAt(fromTs(rs.getTimestamp("decided_at")));
        adj.setVersion(rs.getInt("version"));
        return adj;
    }

    private static Timestamp toTs(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static Instant fromTs(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
