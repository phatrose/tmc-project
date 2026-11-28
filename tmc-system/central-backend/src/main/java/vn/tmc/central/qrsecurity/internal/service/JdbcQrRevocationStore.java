package vn.tmc.central.qrsecurity.internal.service;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tmc.contracts.RevocationChange;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 6.
 * Adapter PostgreSQL append-only cho QrRevocationStore theo migration V011 (bảng qr_revocations).
 * Mọi UPSERT/DELETE đều ghi thêm bản ghi lịch sử để phục vụ đối soát POST_REVOKE_VIOLATION (§8).
 */
public class JdbcQrRevocationStore implements QrRevocationStore {

    private final JdbcTemplate jdbc;

    public JdbcQrRevocationStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void append(RevocationChange change) {
        Objects.requireNonNull(change, "change");
        if (!RevocationChange.OP_UPSERT.equals(change.op()) && !RevocationChange.OP_DELETE.equals(change.op())) {
            throw new IllegalArgumentException("op không hợp lệ: " + change.op());
        }
        jdbc.update("""
                INSERT INTO qr_revocations (revocation_id, op, kind, value, revoked_before, effective_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                change.id(),
                change.op(),
                change.kind(),
                change.value(),
                change.revokedBefore() != null ? Timestamp.from(change.revokedBefore()) : null,
                Timestamp.from(change.effectiveAt()));
    }

    @Override
    public List<RevocationChange> activeChanges() {
        List<RevocationChange> history = jdbc.query(
                "SELECT revocation_id, op, kind, value, revoked_before, effective_at "
                        + "FROM qr_revocations ORDER BY seq ASC",
                (rs, rowNum) -> {
                    Timestamp rb = rs.getTimestamp("revoked_before");
                    return new RevocationChange(
                            rs.getString("revocation_id"),
                            rs.getString("op"),
                            rs.getString("kind"),
                            rs.getString("value"),
                            rb != null ? rb.toInstant() : null,
                            rs.getTimestamp("effective_at").toInstant());
                });
        Map<String, RevocationChange> active = new LinkedHashMap<>();
        for (RevocationChange change : history) {
            if (RevocationChange.OP_UPSERT.equals(change.op())) {
                active.put(change.id(), change);
            } else if (RevocationChange.OP_DELETE.equals(change.op())) {
                active.remove(change.id());
            }
        }
        return List.copyOf(active.values());
    }

    @Override
    public long count() {
        Long cnt = jdbc.queryForObject("SELECT COUNT(*) FROM qr_revocations", Long.class);
        return cnt == null ? 0L : cnt;
    }
}
