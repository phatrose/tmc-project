package vn.tmc.central.qrsecurity.internal.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tmc.central.qrsecurity.internal.model.QrCredential;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 6.
 * Adapter PostgreSQL (JdbcTemplate) cho QrCredentialRepository theo migration V011:
 * bảo đảm tối đa 1 ACTIVE/nhân viên (partial unique index) và replaceActive nguyên tử trong 1 transaction.
 */
public class JdbcQrCredentialRepository implements QrCredentialRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate txTemplate;

    public JdbcQrCredentialRepository(JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.txTemplate = txManager != null ? new TransactionTemplate(txManager) : null;
    }

    @Override
    public Optional<QrCredential> findById(String id) {
        if (id == null) return Optional.empty();
        List<QrCredential> list = jdbc.query(
                "SELECT id, employee_id, state, created_at, revoked_at FROM qr_credentials WHERE id = ?",
                this::mapRow, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<QrCredential> findActiveByEmployeeId(String employeeId) {
        if (employeeId == null) return Optional.empty();
        List<QrCredential> list = jdbc.query(
                "SELECT id, employee_id, state, created_at, revoked_at FROM qr_credentials "
                        + "WHERE employee_id = ? AND state = 'ACTIVE' LIMIT 1",
                this::mapRow, employeeId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<QrCredential> findLatestByEmployeeId(String employeeId) {
        if (employeeId == null) return Optional.empty();
        List<QrCredential> list = jdbc.query(
                "SELECT id, employee_id, state, created_at, revoked_at FROM qr_credentials "
                        + "WHERE employee_id = ? ORDER BY created_at DESC LIMIT 1",
                this::mapRow, employeeId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public void save(QrCredential credential) {
        Objects.requireNonNull(credential, "credential");
        upsert(credential);
    }

    @Override
    public void replaceActive(QrCredential revokedOld, QrCredential createdNew) {
        Objects.requireNonNull(revokedOld, "revokedOld");
        Objects.requireNonNull(createdNew, "createdNew");
        Runnable work = () -> {
            upsert(revokedOld);
            upsert(createdNew);
        };
        if (txTemplate != null) {
            txTemplate.executeWithoutResult(status -> work.run());
        } else {
            work.run();
        }
    }

    private void upsert(QrCredential c) {
        jdbc.update("""
                INSERT INTO qr_credentials (id, employee_id, state, created_at, revoked_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    state = EXCLUDED.state,
                    revoked_at = EXCLUDED.revoked_at
                """,
                c.id(),
                c.employeeId(),
                c.state().name(),
                Timestamp.from(c.createdAt()),
                c.revokedAt() != null ? Timestamp.from(c.revokedAt()) : null);
    }

    private QrCredential mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp revokedAt = rs.getTimestamp("revoked_at");
        return new QrCredential(
                rs.getString("id"),
                rs.getString("employee_id"),
                QrCredential.State.valueOf(rs.getString("state")),
                rs.getTimestamp("created_at").toInstant(),
                revokedAt != null ? revokedAt.toInstant() : null);
    }
}
