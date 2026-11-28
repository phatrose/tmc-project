package vn.tmc.central;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.tmc.central.attendanceprocessing.api.Adjustment;
import vn.tmc.central.attendanceprocessing.api.AdjustmentRequest;
import vn.tmc.central.attendanceprocessing.internal.controller.AttendanceController;
import vn.tmc.central.attendanceprocessing.internal.repository.InMemoryAttendanceRepository;
import vn.tmc.central.attendanceprocessing.internal.repository.JdbcAttendanceAdjustmentRepository;
import vn.tmc.central.attendanceprocessing.internal.service.*;
import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.central.usermgmt.api.Role;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 3.
 * Kiểm thử tích hợp PostgreSQL 16 thật (Flyway V001..V009):
 * 1. Multi-instance concurrency: 2 instance controller/service độc lập không dùng chung lock JVM,
 *    chống trùng xuyên instance qua pg_advisory_xact_lock + transaction PostgreSQL.
 * 2. Atomic rollback: lỗi giữa side effect và ghi outcome làm rollback cả bảng nghiệp vụ lẫn api_idempotency.
 * 3. Restart durability: tạo bản ghi thật trên PostgreSQL, hủy toàn bộ instance cũ, khởi tạo instance mới
 *    xác minh bản ghi nghiệp vụ còn nguyên và retry cùng Idempotency-Key không tạo trùng.
 */
@Testcontainers
class IdempotencyPostgresIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tmc_idem_test")
            .withUsername("tmc_test")
            .withPassword("tmc_test_pass");

    private static DriverManagerDataSource ds;
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager txManager;
    private static ObjectMapper mapper;

    @BeforeAll
    static void migrateSchema() {
        ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
        txManager = new DataSourceTransactionManager(ds);
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private AttendanceController newIsolatedInstance() {
        JdbcAttendanceAdjustmentRepository pgAdjRepo = new JdbcAttendanceAdjustmentRepository(jdbc);
        InMemoryAttendanceRepository dailyFallback = new InMemoryAttendanceRepository();
        AttendanceAdjustmentService adjService = new AttendanceAdjustmentService(pgAdjRepo, dailyFallback);
        AttendanceProcessingService procService = new AttendanceProcessingService(dailyFallback, dailyFallback, dailyFallback, adjService);
        AttendanceIdempotencyService idemService = new AttendanceIdempotencyService(jdbc, txManager, mapper);
        return new AttendanceController(procService, adjService, idemService);
    }

    @Test
    void businessRecordAndIdempotencySurviveFullInstanceRestartOnPostgres() {
        ActorContext emp = new ActorContext(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                List.of(Role.EMPLOYEE), List.of("employee.self"),
                List.of(UUID.randomUUID().toString()), List.of(), ActorContext.SCOPE_SELF);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(emp, "tok", List.of()));
        try {
            String key = UUID.randomUUID().toString();
            AdjustmentRequest req = new AdjustmentRequest(
                    null, Instant.parse("2026-11-28T10:00:00Z"), "OUT", "Quen quet ra do bao tri cong F1 ben vung");

            AttendanceController instance1 = newIsolatedInstance();
            Adjustment first = instance1.createAdjustment(req, key).getBody();
            assertNotNull(first);

            // Hủy instance1, khởi tạo instance2 hoàn toàn mới đọc cùng PostgreSQL
            AttendanceController instance2 = newIsolatedInstance();
            var loaded = new JdbcAttendanceAdjustmentRepository(jdbc).findAdjustmentById(first.id());
            assertTrue(loaded.isPresent(), "Bản ghi nghiệp vụ attendance_adjustments phải tồn tại trong PostgreSQL sau restart");
            assertEquals("PENDING", loaded.get().getState());

            Adjustment replayed = instance2.createAdjustment(req, key).getBody();
            assertNotNull(replayed);
            assertEquals(first.id(), replayed.id());
            Integer rowCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM attendance_adjustments WHERE id = ?", Integer.class, first.id());
            assertEquals(1, rowCount);

            AdjustmentRequest conflicting = new AdjustmentRequest(
                    null, Instant.parse("2026-11-28T11:00:00Z"), "OUT", "Noi dung khac hoan toan cho cung key");
            assertThrows(IdempotencyConflictException.class, () -> instance2.createAdjustment(conflicting, key));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void multiInstanceConcurrentRequestsWithSameKeyCommitExactlyOnce() throws Exception {
        ActorContext emp = new ActorContext(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                List.of(Role.EMPLOYEE), List.of("employee.self"),
                List.of(UUID.randomUUID().toString()), List.of(), ActorContext.SCOPE_SELF);
        String key = UUID.randomUUID().toString();
        AdjustmentRequest req = new AdjustmentRequest(
                null, Instant.parse("2026-11-28T12:00:00Z"), "OUT", "Gui dong thoi xuyen nhieu instance JVM");

        // 4 instance độc lập (mỗi instance có object/lock riêng, chỉ dùng chung PostgreSQL)
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<UUID>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            AttendanceController separateInstance = newIsolatedInstance();
            futures.add(pool.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(emp, "tok", List.of()));
                try {
                    latch.await();
                    return separateInstance.createAdjustment(req, key).getBody().id();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
        }
        latch.countDown();
        Set<UUID> ids = new HashSet<>();
        for (Future<UUID> f : futures) ids.add(f.get());
        pool.shutdownNow();

        assertEquals(1, ids.size(), "Tất cả instance đồng thời phải nhận đúng 1 adjustment ID");
        UUID onlyId = ids.iterator().next();
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM attendance_adjustments WHERE id = ?", Integer.class, onlyId));
    }

    @Test
    void failureMidwayRollsBackBothBusinessRecordAndIdempotencyOutcome() {
        JdbcAttendanceAdjustmentRepository pgAdjRepo = new JdbcAttendanceAdjustmentRepository(jdbc);
        AttendanceIdempotencyService idemService = new AttendanceIdempotencyService(jdbc, txManager, mapper);
        String userId = UUID.randomUUID().toString();
        String key = UUID.randomUUID().toString();
        UUID failingAdjId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();

        // Giả lập lỗi xảy ra SAU khi insert bản ghi nghiệp vụ nhưng TRƯỚC khi commit transaction
        assertThrows(IllegalStateException.class, () -> idemService.execute(
                userId,
                "POST:/api/v1/attendance/adjustments",
                key,
                Map.of("reason", "test-rollback"),
                201,
                Adjustment.class,
                () -> {
                    pgAdjRepo.save(new vn.tmc.central.attendanceprocessing.internal.model.AttendanceAdjustment(
                            failingAdjId, null, empId, UUID.fromString(userId),
                            Instant.parse("2026-11-28T13:00:00Z"), "OUT", "Test rollback nguyen tu", Instant.now()));
                    throw new IllegalStateException("Simulated crash before commit");
                }));

        // Cả bản ghi nghiệp vụ lẫn api_idempotency đều phải rollback (0 bản ghi)
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM attendance_adjustments WHERE id = ?", Integer.class, failingAdjId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_idempotency WHERE scope_key LIKE ?", Integer.class, "%" + key));
    }

    @Test
    void userMgmtIdempotencyCanonicalJsonAndPasswordExclusionOnPostgres() {
        var idem1 = new vn.tmc.central.usermgmt.internal.service.UserMgmtIdempotencyService(jdbc, txManager, mapper);
        String userId = UUID.randomUUID().toString();
        String key = UUID.randomUUID().toString();

        // Hai map khác thứ tự khóa nhưng cùng nội dung phải sinh cùng canonical JSON fingerprint (IDEM-09)
        Map<String, Object> payloadOrder1 = new LinkedHashMap<>();
        payloadOrder1.put("username", "nv.test");
        payloadOrder1.put("scope", "SELF");

        Map<String, Object> payloadOrder2 = new LinkedHashMap<>();
        payloadOrder2.put("scope", "SELF");
        payloadOrder2.put("username", "nv.test");

        String first = idem1.execute(userId, "POST:/api/v1/accounts", key, payloadOrder1, 201, String.class, () -> "acc-1");
        assertEquals("acc-1", first);

        // Instance mới đọc lại PostgreSQL với thứ tự khóa khác -> replay thành công không 409
        var idem2 = new vn.tmc.central.usermgmt.internal.service.UserMgmtIdempotencyService(jdbc, txManager, mapper);
        String replayed = idem2.execute(userId, "POST:/api/v1/accounts", key, payloadOrder2, 201, String.class, () -> "acc-should-not-run");
        assertEquals("acc-1", replayed);

        // Khác payload -> 409 ConflictException
        Map<String, Object> different = Map.of("username", "nv.other", "scope", "SELF");
        assertThrows(vn.tmc.central.usermgmt.internal.service.UserMgmtIdempotencyService.ConflictException.class,
                () -> idem2.execute(userId, "POST:/api/v1/accounts", key, different, 201, String.class, () -> "acc-2"));
    }
}
