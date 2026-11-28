package vn.tmc.central;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.tmc.central.attendanceprocessing.internal.model.AttendanceEvent;
import vn.tmc.central.attendanceprocessing.internal.model.DailyAttendanceRecord;
import vn.tmc.central.attendanceprocessing.internal.model.SyncConflict;
import vn.tmc.central.attendanceprocessing.internal.repository.JdbcAttendanceRepository;
import vn.tmc.central.qrsecurity.internal.model.QrCredential;
import vn.tmc.central.qrsecurity.internal.repository.JdbcQrCredentialRepository;
import vn.tmc.central.qrsecurity.internal.service.JdbcQrRevocationStore;
import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.contracts.AttendancePayload.Source;
import vn.tmc.contracts.RevocationChange;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 3 & Task 6.
 * Kiểm thử tích hợp PostgreSQL 16 thật (Flyway V001..V011) cho:
 * - DATA-02: attendance_events (append-only trigger), daily_attendance_records, sync_conflicts, edge_sync_cursors.
 * - DATA-04: qr_credentials (unique 1 ACTIVE/nhân viên, replaceActive nguyên tử) và qr_revocations (append-only trigger).
 */
@Testcontainers
class AttendanceAndQrPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tmc_persist_test")
            .withUsername("tmc_test")
            .withPassword("tmc_test_pass");

    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager txManager;

    @BeforeAll
    static void migrateSchema() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
        txManager = new DataSourceTransactionManager(ds);
    }

    @Test
    void attendanceEventsDailyAndConflictsSurviveRestartAndEnforceAppendOnly() {
        JdbcAttendanceRepository repo1 = new JdbcAttendanceRepository(jdbc);
        UUID eventId = UUID.randomUUID();
        UUID edgeId = UUID.randomUUID();
        UUID gateId = UUID.randomUUID();
        UUID factoryId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();
        Instant obs = Instant.parse("2026-11-28T07:55:00Z");

        AttendanceEvent event = new AttendanceEvent(
                eventId, "ATTENDANCE_RECORDED", 1, edgeId, gateId, factoryId,
                42L, UUID.randomUUID(), obs, obs.plusSeconds(1), empId,
                Direction.IN, Source.ONLINE, UUID.randomUUID(), "kid-1", obs.minusSeconds(10),
                null, null, null, List.of("LATE_CHECK"), "sha256-abc", "{\"direction\":\"IN\"}");
        repo1.saveEvent(event);

        DailyAttendanceRecord daily = new DailyAttendanceRecord(
                empId + "_2026-11-28", empId, factoryId, LocalDate.of(2026, 11, 28), null, "SHIFT_1");
        daily.setFirstIn(obs);
        daily.setWorkedMinutes(null); // CONTRACT-V1 §9: thiếu OUT thì workedMinutes = null
        daily.setMissingSwipe(true);
        daily.addFlag("MISSING_OUT");
        repo1.save(daily);

        SyncConflict conflict = new SyncConflict(
                UUID.randomUUID().toString(), eventId, edgeId.toString(),
                "sha256-abc", "sha256-xyz", obs, Instant.now(), "hash mismatch");
        repo1.recordConflict(conflict);
        repo1.updateLastContiguousSequenceNo(edgeId.toString(), 42L);

        // Hủy repo1, tạo repo2 hoàn toàn mới đọc cùng PostgreSQL
        JdbcAttendanceRepository repo2 = new JdbcAttendanceRepository(jdbc);
        var loadedEvent = repo2.findByEventId(eventId);
        assertTrue(loadedEvent.isPresent());
        assertEquals(42L, loadedEvent.get().getSequenceNo());
        assertEquals(List.of("LATE_CHECK"), loadedEvent.get().getFlags());

        var loadedDaily = repo2.findByEmployeeIdAndWorkDate(empId, LocalDate.of(2026, 11, 28));
        assertTrue(loadedDaily.isPresent());
        assertNull(loadedDaily.get().getWorkedMinutes(), "workedMinutes phải giữ nguyên null khi thiếu OUT");
        assertTrue(loadedDaily.get().isMissingSwipe());

        assertEquals(42L, repo2.getLastContiguousSequenceNo(edgeId.toString()));
        assertTrue(repo2.findConflicts().stream().anyMatch(c -> c.getEventId().equals(eventId)));

        // Xác minh trigger append-only trên attendance_events chặn UPDATE & DELETE
        assertThrows(DataAccessException.class, () ->
                jdbc.update("UPDATE attendance_events SET reason = 'tampered' WHERE event_id = ?", eventId));
        assertThrows(DataAccessException.class, () ->
                jdbc.update("DELETE FROM attendance_events WHERE event_id = ?", eventId));
    }

    @Test
    void qrCredentialsEnforceSingleActiveAndRevocationsAreAppendOnly() {
        JdbcQrCredentialRepository credRepo1 = new JdbcQrCredentialRepository(jdbc, txManager);
        JdbcQrRevocationStore revStore1 = new JdbcQrRevocationStore(jdbc);

        String empId = UUID.randomUUID().toString();
        Instant t1 = Instant.parse("2026-11-28T08:00:00Z");
        QrCredential active1 = new QrCredential(UUID.randomUUID().toString(), empId, QrCredential.State.ACTIVE, t1, null);
        credRepo1.save(active1);

        // Partial unique index phải chặn ACTIVE thứ 2 cho cùng nhân viên
        QrCredential duplicateActive = new QrCredential(UUID.randomUUID().toString(), empId, QrCredential.State.ACTIVE, t1.plusSeconds(5), null);
        assertThrows(DataAccessException.class, () -> credRepo1.save(duplicateActive));

        // replaceActive thu hồi cũ + tạo mới nguyên tử trong cùng transaction
        Instant t2 = t1.plusSeconds(10);
        QrCredential revoked1 = new QrCredential(active1.id(), empId, QrCredential.State.REVOKED, t1, t2);
        QrCredential active2 = new QrCredential(UUID.randomUUID().toString(), empId, QrCredential.State.ACTIVE, t2, null);
        credRepo1.replaceActive(revoked1, active2);

        String revId = UUID.randomUUID().toString();
        revStore1.append(new RevocationChange(revId, RevocationChange.OP_UPSERT, RevocationChange.KIND_SUB, empId, t2, t2));
        revStore1.append(new RevocationChange(revId, RevocationChange.OP_DELETE, RevocationChange.KIND_SUB, empId, t2, t2.plusSeconds(60)));

        // Instance mới đọc lại PostgreSQL
        JdbcQrCredentialRepository credRepo2 = new JdbcQrCredentialRepository(jdbc, txManager);
        JdbcQrRevocationStore revStore2 = new JdbcQrRevocationStore(jdbc);

        assertEquals(active2.id(), credRepo2.findActiveByEmployeeId(empId).orElseThrow().id());
        assertEquals(QrCredential.State.REVOKED, credRepo2.findById(active1.id()).orElseThrow().state());
        assertTrue(revStore2.activeChanges().stream().noneMatch(r -> r.id().equals(revId)),
                "OP_DELETE phải loại khỏi activeChanges");
        assertTrue(revStore2.count() >= 2, "Lịch sử qr_revocations vẫn phải được bảo toàn");

        // Trigger append-only trên qr_revocations chặn UPDATE & DELETE
        assertThrows(DataAccessException.class, () ->
                jdbc.update("UPDATE qr_revocations SET value = 'tampered' WHERE revocation_id = ?", revId));
        assertThrows(DataAccessException.class, () ->
                jdbc.update("DELETE FROM qr_revocations WHERE revocation_id = ?", revId));
    }
}
