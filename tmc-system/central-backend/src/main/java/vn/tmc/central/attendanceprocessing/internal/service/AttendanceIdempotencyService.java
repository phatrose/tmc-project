package vn.tmc.central.attendanceprocessing.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PHỤ TRÁCH: Task 3 / Task 8 — tích hợp với Task 3.
 * Dịch vụ thực thi idempotency nguyên tử trong cùng transaction PostgreSQL với side effect:
 * - Dùng pg_advisory_xact_lock(hashtext(scope_key)) để chống trùng xuyên nhiều instance JVM (IDEM-07).
 * - Khi side effect ném lỗi, toàn bộ transaction rollback nên không lưu kết quả giả (IDEM-08).
 * - Lưu kết quả 48 giờ trong bảng api_idempotency (V008) (IDEM-04/05/06).
 */
public final class AttendanceIdempotencyService {

    private record CachedEntry(String payloadHash, int status, Object body, Instant expiresAt) {}

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedEntry> fallbackStore = new ConcurrentHashMap<>();
    private final Map<String, Object> fallbackLocks = new ConcurrentHashMap<>();

    public AttendanceIdempotencyService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager txManager,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = txManager != null ? new TransactionTemplate(txManager) : null;
        this.objectMapper = objectMapper;
    }

    public <T> T execute(
            String actorUserId,
            String scopePath,
            String rawIdempotencyKey,
            Object canonicalPayload,
            int httpStatus,
            Class<T> responseType,
            Supplier<T> action
    ) {
        String key = requireUuidV4(rawIdempotencyKey);
        String scopeKey = actorUserId + "|" + scopePath + "|" + key;
        String payloadHash = computeStableHash(canonicalPayload);

        if (jdbcTemplate != null && txTemplate != null && objectMapper != null) {
            return txTemplate.execute(status -> {
                // Khóa mức transaction trên PostgreSQL: an toàn xuyên nhiều instance JVM
                jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, scopeKey);

                Instant now = Instant.now();
                List<CachedEntry> existing = jdbcTemplate.query(
                        "SELECT payload_hash, http_status, response_body, expires_at "
                                + "FROM api_idempotency WHERE scope_key = ? AND expires_at > ?",
                        (rs, rowNum) -> {
                            try {
                                T decoded = objectMapper.readValue(rs.getString("response_body"), responseType);
                                return new CachedEntry(
                                        rs.getString("payload_hash"),
                                        rs.getInt("http_status"),
                                        decoded,
                                        rs.getTimestamp("expires_at").toInstant());
                            } catch (Exception e) {
                                throw new IllegalStateException("Không thể giải mã bản ghi idempotency.", e);
                            }
                        },
                        scopeKey,
                        Timestamp.from(now));

                if (!existing.isEmpty()) {
                    CachedEntry hit = existing.get(0);
                    if (!hit.payloadHash().equals(payloadHash)) {
                        throw new IdempotencyConflictException("Idempotency-Key đã được dùng với nội dung khác.");
                    }
                    return responseType.cast(hit.body());
                }

                // Thực thi side effect trong CÙNG transaction; nếu lỗi -> rollback cả side effect lẫn idempotency
                T created = action.get();
                try {
                    String json = objectMapper.writeValueAsString(created);
                    jdbcTemplate.update(
                            "INSERT INTO api_idempotency (scope_key, payload_hash, http_status, response_body, expires_at) "
                                    + "VALUES (?, ?, ?, ?, ?)",
                            scopeKey,
                            payloadHash,
                            httpStatus,
                            json,
                            Timestamp.from(now.plus(48, ChronoUnit.HOURS)));
                } catch (Exception e) {
                    throw new IllegalStateException("Không thể ghi kết quả idempotency vào PostgreSQL.", e);
                }
                return created;
            });
        }

        // Fallback cho unit test không có DataSource
        Object lock = fallbackLocks.computeIfAbsent(scopeKey, k -> new Object());
        synchronized (lock) {
            Instant now = Instant.now();
            CachedEntry existing = fallbackStore.get(scopeKey);
            if (existing != null && now.isBefore(existing.expiresAt())) {
                if (!existing.payloadHash().equals(payloadHash)) {
                    throw new IdempotencyConflictException("Idempotency-Key đã được dùng với nội dung khác.");
                }
                return responseType.cast(existing.body());
            }
            T created = action.get();
            fallbackStore.put(scopeKey, new CachedEntry(payloadHash, httpStatus, created, now.plus(48, ChronoUnit.HOURS)));
            return created;
        }
    }

    public static String requireUuidV4(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Header Idempotency-Key bắt buộc (UUIDv4).");
        }
        String trimmed = raw.trim();
        try {
            UUID parsed = UUID.fromString(trimmed);
            if (parsed.version() != 4) {
                throw new IllegalArgumentException("Header Idempotency-Key phải là UUIDv4 hợp lệ.");
            }
            return trimmed;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Header Idempotency-Key phải là UUIDv4 hợp lệ.", e);
        }
    }

    private String computeStableHash(Object payload) {
        String serialized;
        if (objectMapper != null) {
            try {
                serialized = objectMapper.writeValueAsString(payload);
            } catch (Exception ignored) {
                serialized = String.valueOf(payload);
            }
        } else {
            serialized = String.valueOf(payload);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(serialized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
