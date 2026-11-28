package vn.tmc.central.systemadmin.internal.repository;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tmc.central.systemadmin.internal.model.*;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 5 (FE-ADMIN-02).
 * Tập hợp 7 adapter PostgreSQL (JdbcTemplate) độc lập theo migration V013__systemadmin.sql,
 * mỗi adapter chỉ implement đúng 1 interface repository để tránh xung đột bean Spring.
 */
public final class JdbcSystemAdminRepositories {

    private JdbcSystemAdminRepositories() {}

    public static final class JdbcFactoryRepository implements FactoryRepository {
        private final JdbcTemplate jdbc;
        public JdbcFactoryRepository(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

        @Override
        public Optional<Factory> findById(String id) {
            if (id == null) return Optional.empty();
            List<Factory> list = jdbc.query("SELECT * FROM admin_factories WHERE id = ?",
                    (rs, n) -> new Factory(rs.getString("id"), rs.getString("name"), rs.getString("zone_id"),
                            rs.getString("address"), rs.getString("status")), id);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }

        @Override
        public List<Factory> findAll() {
            return jdbc.query("SELECT * FROM admin_factories ORDER BY id ASC",
                    (rs, n) -> new Factory(rs.getString("id"), rs.getString("name"), rs.getString("zone_id"),
                            rs.getString("address"), rs.getString("status")));
        }

        @Override
        public void save(Factory f) {
            jdbc.update("""
                    INSERT INTO admin_factories (id, name, zone_id, address, status)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        name = EXCLUDED.name, zone_id = EXCLUDED.zone_id,
                        address = EXCLUDED.address, status = EXCLUDED.status
                    """, f.id(), f.name(), f.zoneId(), f.address() != null ? f.address() : "", f.status());
        }

        @Override
        public void delete(String id) {
            jdbc.update("DELETE FROM admin_factories WHERE id = ?", id);
        }
    }

    public static final class JdbcGateRepository implements GateRepository {
        private final JdbcTemplate jdbc;
        public JdbcGateRepository(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

        @Override
        public Optional<Gate> findById(String id) {
            if (id == null) return Optional.empty();
            List<Gate> list = jdbc.query("SELECT * FROM admin_gates WHERE id = ?", this::mapGate, id);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }

        @Override
        public List<Gate> findByFactoryId(String factoryId) {
            return jdbc.query("SELECT * FROM admin_gates WHERE factory_id = ? ORDER BY id ASC", this::mapGate, factoryId);
        }

        @Override
        public Optional<Gate> findByTurnstileApiId(String turnstileApiId) {
            if (turnstileApiId == null) return Optional.empty();
            List<Gate> list = jdbc.query("SELECT * FROM admin_gates WHERE turnstile_api_id = ?", this::mapGate, turnstileApiId);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }

        @Override
        public List<Gate> findAll() {
            return jdbc.query("SELECT * FROM admin_gates ORDER BY id ASC", this::mapGate);
        }

        @Override
        public void save(Gate g) {
            jdbc.update("""
                    INSERT INTO admin_gates (id, factory_id, edge_id, name, direction, turnstile_api_id, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        factory_id = EXCLUDED.factory_id, edge_id = EXCLUDED.edge_id,
                        name = EXCLUDED.name, direction = EXCLUDED.direction,
                        turnstile_api_id = EXCLUDED.turnstile_api_id, status = EXCLUDED.status
                    """, g.id(), g.factoryId(), g.edgeId(), g.name(), g.direction(), g.turnstileApiId(), g.status());
        }

        @Override
        public void delete(String id) {
            jdbc.update("DELETE FROM admin_gates WHERE id = ?", id);
        }

        private Gate mapGate(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
            return new Gate(rs.getString("id"), rs.getString("factory_id"), rs.getString("edge_id"),
                    rs.getString("name"), rs.getString("direction"), rs.getString("turnstile_api_id"), rs.getString("status"));
        }
    }

    public static final class JdbcShiftRepository implements ShiftRepository {
        private final JdbcTemplate jdbc;
        public JdbcShiftRepository(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

        @Override
        public Optional<Shift> findById(String id) {
            if (id == null) return Optional.empty();
            List<Shift> list = jdbc.query("SELECT * FROM admin_shifts WHERE id = ?", this::mapShift, id);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }

        @Override
        public List<Shift> findByFactoryId(String factoryId) {
            return jdbc.query("SELECT * FROM admin_shifts WHERE factory_id = ? ORDER BY id ASC", this::mapShift, factoryId);
        }

        @Override
        public List<Shift> findAll() {
            return jdbc.query("SELECT * FROM admin_shifts ORDER BY id ASC", this::mapShift);
        }

        @Override
        public void save(Shift s) {
            jdbc.update("""
                    INSERT INTO admin_shifts (
                        id, factory_id, name, starts_at, ends_at, is_overnight,
                        grace_period_minutes_late, grace_period_minutes_early, active
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        factory_id = EXCLUDED.factory_id, name = EXCLUDED.name,
                        starts_at = EXCLUDED.starts_at, ends_at = EXCLUDED.ends_at,
                        is_overnight = EXCLUDED.is_overnight,
                        grace_period_minutes_late = EXCLUDED.grace_period_minutes_late,
                        grace_period_minutes_early = EXCLUDED.grace_period_minutes_early,
                        active = EXCLUDED.active
                    """, s.id(), s.factoryId(), s.name(), Time.valueOf(s.startsAt()), Time.valueOf(s.endsAt()),
                    s.isOvernight(), s.gracePeriodMinutesLate(), s.gracePeriodMinutesEarly(), s.active());
        }

        @Override
        public void delete(String id) {
            jdbc.update("DELETE FROM admin_shifts WHERE id = ?", id);
        }

        private Shift mapShift(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
            return new Shift(rs.getString("id"), rs.getString("factory_id"), rs.getString("name"),
                    rs.getTime("starts_at").toLocalTime(), rs.getTime("ends_at").toLocalTime(),
                    rs.getBoolean("is_overnight"), rs.getInt("grace_period_minutes_late"),
                    rs.getInt("grace_period_minutes_early"), rs.getBoolean("active"));
        }
    }

    public static final class JdbcHolidayRepository implements HolidayRepository {
        private final JdbcTemplate jdbc;
        public JdbcHolidayRepository(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

        @Override
        public Optional<Holiday> findById(String id) {
            if (id == null) return Optional.empty();
            List<Holiday> list = jdbc.query("SELECT * FROM admin_holidays WHERE id = ?", this::mapHoliday, id);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }

        @Override
        public List<Holiday> findByFactoryId(String factoryId) {
            return jdbc.query("SELECT * FROM admin_holidays WHERE factory_id = ? ORDER BY holiday_date ASC", this::mapHoliday, factoryId);
        }

        @Override
        public List<Holiday> findAll() {
            return jdbc.query("SELECT * FROM admin_holidays ORDER BY holiday_date ASC", this::mapHoliday);
        }

        @Override
        public void save(Holiday h) {
            jdbc.update("""
                    INSERT INTO admin_holidays (id, factory_id, name, holiday_date, paid)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        factory_id = EXCLUDED.factory_id, name = EXCLUDED.name,
                        holiday_date = EXCLUDED.holiday_date, paid = EXCLUDED.paid
                    """, h.id(), h.factoryId(), h.name(), Date.valueOf(h.date()), h.paid());
        }

        @Override
        public void delete(String id) {
            jdbc.update("DELETE FROM admin_holidays WHERE id = ?", id);
        }

        private Holiday mapHoliday(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
            return new Holiday(rs.getString("id"), rs.getString("factory_id"), rs.getString("name"),
                    rs.getDate("holiday_date").toLocalDate(), rs.getBoolean("paid"));
        }
    }

    public static final class JdbcSystemConfigurationRepository implements SystemConfigurationRepository {
        private final JdbcTemplate jdbc;
        public JdbcSystemConfigurationRepository(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

        @Override
        public Optional<SystemConfiguration> findById(String id) {
            if (id == null) return Optional.empty();
            List<SystemConfiguration> list = jdbc.query(
                    "SELECT * FROM admin_system_configurations WHERE config_key = ?", this::mapConfig, id);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }

        @Override
        public List<SystemConfiguration> findByCategory(String category) {
            return jdbc.query("SELECT * FROM admin_system_configurations WHERE category = ? ORDER BY config_key ASC",
                    this::mapConfig, category);
        }

        @Override
        public List<SystemConfiguration> findAll() {
            return jdbc.query("SELECT * FROM admin_system_configurations ORDER BY config_key ASC", this::mapConfig);
        }

        @Override
        public void save(SystemConfiguration c) {
            jdbc.update("""
                    INSERT INTO admin_system_configurations (config_key, config_value, category, description, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (config_key) DO UPDATE SET
                        config_value = EXCLUDED.config_value, category = EXCLUDED.category,
                        description = EXCLUDED.description, updated_at = EXCLUDED.updated_at
                    """, c.key(), c.value(), c.category(), c.description(), Timestamp.from(c.updatedAt()));
        }

        @Override
        public void delete(String id) {
            jdbc.update("DELETE FROM admin_system_configurations WHERE config_key = ?", id);
        }

        private SystemConfiguration mapConfig(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
            return new SystemConfiguration(rs.getString("config_key"), rs.getString("config_value"),
                    rs.getString("category"), rs.getString("description"), rs.getTimestamp("updated_at").toInstant());
        }
    }

    public static final class JdbcDeviceStatusRepository implements DeviceStatusRepository {
        private final JdbcTemplate jdbc;
        public JdbcDeviceStatusRepository(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

        @Override
        public Optional<DeviceStatus> findById(String id) {
            if (id == null) return Optional.empty();
            List<DeviceStatus> list = jdbc.query("SELECT * FROM admin_device_statuses WHERE device_id = ?", this::mapDevice, id);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }

        @Override
        public List<DeviceStatus> findByFactoryId(String factoryId) {
            return jdbc.query("SELECT * FROM admin_device_statuses WHERE factory_id = ? ORDER BY device_id ASC",
                    this::mapDevice, factoryId);
        }

        @Override
        public List<DeviceStatus> findAll() {
            return jdbc.query("SELECT * FROM admin_device_statuses ORDER BY device_id ASC", this::mapDevice);
        }

        @Override
        public void save(DeviceStatus d) {
            jdbc.update("""
                    INSERT INTO admin_device_statuses (
                        device_id, device_type, name, factory_id, status,
                        last_heartbeat_at, firmware_version, ip_address, error_message
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (device_id) DO UPDATE SET
                        device_type = EXCLUDED.device_type, name = EXCLUDED.name,
                        factory_id = EXCLUDED.factory_id, status = EXCLUDED.status,
                        last_heartbeat_at = EXCLUDED.last_heartbeat_at,
                        firmware_version = EXCLUDED.firmware_version,
                        ip_address = EXCLUDED.ip_address, error_message = EXCLUDED.error_message
                    """, d.deviceId(), d.deviceType(), d.name(), d.factoryId(), d.status(),
                    Timestamp.from(d.lastHeartbeatAt()), d.firmwareVersion(), d.ipAddress(), d.errorMessage());
        }

        @Override
        public void delete(String id) {
            jdbc.update("DELETE FROM admin_device_statuses WHERE device_id = ?", id);
        }

        private DeviceStatus mapDevice(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
            return new DeviceStatus(rs.getString("device_id"), rs.getString("device_type"),
                    rs.getString("name"), rs.getString("factory_id"), rs.getString("status"),
                    rs.getTimestamp("last_heartbeat_at").toInstant(), rs.getString("firmware_version"),
                    rs.getString("ip_address"), rs.getString("error_message"));
        }
    }

    public static final class JdbcDataSubjectRequestRepository implements DataSubjectRequestRepository {
        private final JdbcTemplate jdbc;
        public JdbcDataSubjectRequestRepository(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

        @Override
        public Optional<DataSubjectRequest> findById(String id) {
            if (id == null) return Optional.empty();
            List<DataSubjectRequest> list = jdbc.query(
                    "SELECT * FROM admin_data_subject_requests WHERE id = ?", this::mapDsr, id);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }

        @Override
        public List<DataSubjectRequest> findByEmployeeId(String employeeId) {
            return jdbc.query("SELECT * FROM admin_data_subject_requests WHERE employee_id = ? ORDER BY requested_at DESC",
                    this::mapDsr, employeeId);
        }

        @Override
        public List<DataSubjectRequest> findAll() {
            return jdbc.query("SELECT * FROM admin_data_subject_requests ORDER BY requested_at DESC", this::mapDsr);
        }

        @Override
        public void save(DataSubjectRequest r) {
            jdbc.update("""
                    INSERT INTO admin_data_subject_requests (
                        id, employee_id, request_type, status, reason,
                        requested_at, processed_by, processed_at, notes
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        status = EXCLUDED.status, processed_by = EXCLUDED.processed_by,
                        processed_at = EXCLUDED.processed_at, notes = EXCLUDED.notes
                    """, r.id(), r.employeeId(), r.requestType(), r.status(), r.reason(),
                    Timestamp.from(r.requestedAt()), r.processedBy(),
                    r.processedAt() != null ? Timestamp.from(r.processedAt()) : null, r.notes());
        }

        @Override
        public void delete(String id) {
            jdbc.update("DELETE FROM admin_data_subject_requests WHERE id = ?", id);
        }

        private DataSubjectRequest mapDsr(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
            Timestamp proc = rs.getTimestamp("processed_at");
            return new DataSubjectRequest(rs.getString("id"), rs.getString("employee_id"),
                    rs.getString("request_type"), rs.getString("status"), rs.getString("reason"),
                    rs.getTimestamp("requested_at").toInstant(), rs.getString("processed_by"),
                    proc != null ? proc.toInstant() : null, rs.getString("notes"));
        }
    }
}
