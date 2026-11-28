package vn.tmc.central;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.tmc.central.systemadmin.internal.model.*;
import vn.tmc.central.systemadmin.internal.repository.JdbcSystemAdminRepositories.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 5 (FE-ADMIN-02).
 * Kiểm thử tích hợp PostgreSQL 16 thật (Flyway V001..V013) cho toàn bộ 7 repository của systemadmin:
 * lưu bền vững factories, gates, shifts, holidays, system configurations, device statuses, DSR
 * và xác minh dữ liệu sống nguyên qua restart instance repository mới.
 */
@Testcontainers
class SystemAdminPostgresIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tmc_sysadmin_test")
            .withUsername("tmc_test")
            .withPassword("tmc_test_pass");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateSchema() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
    }

    @Test
    void allSevenSystemAdminRepositoriesPersistAcrossInstanceRestart() {
        // Instance 1 ghi dữ liệu vào PostgreSQL 16
        new JdbcFactoryRepository(jdbc).save(new Factory("F-PG-1", "Nha may PG", "Asia/Ho_Chi_Minh", "Binh Duong", "ACTIVE"));
        new JdbcGateRepository(jdbc).save(new Gate("G-PG-1", "F-PG-1", "EDGE-PG-1", "Cong Chinh PG", "IN", "TS-PG-1", "ACTIVE"));
        new JdbcShiftRepository(jdbc).save(new Shift("S-PG-NIGHT", "F-PG-1", "Ca dem", LocalTime.of(22, 0), LocalTime.of(6, 0), true, 15, 10, true));
        new JdbcHolidayRepository(jdbc).save(new Holiday("H-PG-1", "F-PG-1", "Quoc Khanh", LocalDate.of(2026, 9, 2), true));
        new JdbcSystemConfigurationRepository(jdbc).save(new SystemConfiguration("turnstile.timeout", "1000", "GATE", "Timeout ms", Instant.parse("2026-11-28T00:00:00Z")));
        new JdbcDeviceStatusRepository(jdbc).save(new DeviceStatus("EDGE-PG-1", "EDGE", "Edge Binh Duong", "F-PG-1", "ONLINE", Instant.parse("2026-11-28T03:00:00Z"), "1.2.0", "10.0.1.5", null));
        new JdbcDataSubjectRequestRepository(jdbc).save(new DataSubjectRequest("DSR-PG-1", "EMP-PG-1", "ACCESS", "PENDING", "Trich xuat du lieu", Instant.parse("2026-11-28T03:05:00Z"), null, null, null));

        // Instance 2 hoàn toàn mới đọc lại từ PostgreSQL 16
        assertEquals("Nha may PG", new JdbcFactoryRepository(jdbc).findById("F-PG-1").orElseThrow().name());
        assertEquals("TS-PG-1", new JdbcGateRepository(jdbc).findByTurnstileApiId("TS-PG-1").orElseThrow().turnstileApiId());
        assertTrue(new JdbcShiftRepository(jdbc).findById("S-PG-NIGHT").orElseThrow().isOvernight());
        assertEquals(LocalDate.of(2026, 9, 2), new JdbcHolidayRepository(jdbc).findByFactoryId("F-PG-1").get(0).date());
        assertEquals("1000", new JdbcSystemConfigurationRepository(jdbc).findById("turnstile.timeout").orElseThrow().value());
        assertEquals("10.0.1.5", new JdbcDeviceStatusRepository(jdbc).findById("EDGE-PG-1").orElseThrow().ipAddress());
        assertEquals("PENDING", new JdbcDataSubjectRequestRepository(jdbc).findById("DSR-PG-1").orElseThrow().status());

        // Cập nhật (upsert) và xác minh thay đổi bền vững
        new JdbcGateRepository(jdbc).save(new Gate("G-PG-1", "F-PG-1", "EDGE-PG-1", "Cong Chinh PG", "IN", "TS-PG-1", "MAINTENANCE"));
        assertEquals("MAINTENANCE", new JdbcGateRepository(jdbc).findById("G-PG-1").orElseThrow().status());
    }
}
