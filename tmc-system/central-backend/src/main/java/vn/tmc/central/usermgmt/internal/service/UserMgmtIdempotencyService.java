package vn.tmc.central.usermgmt.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PHỤ TRÁCH: Task 1 / Task 8 — tích hợp với Task 1.
 * Dịch vụ idempotency bền vững trên bảng PostgreSQL api_idempotency (V008) cho EmployeeController:
 * dùng pg_advisory_xact_lock xuyên nhiều instance JVM, canonical JSON fingerprint (ổn định theo khóa),
 * và rollback cùng transaction khi side effect lỗi.
 */
@Service
@Profile("!platform")
public class UserMgmtIdempotencyService {

    public static final class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }

    private record CachedEntry(String payloadHash, int status, Object body, Instant expiresAt) {}

    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedEntry> fallbackStore = new ConcurrentHashMap<>();
    private final Map<String, Object> fallbackLocks = new ConcurrentHashMap<>();

    @Autowired
    public UserMgmtIdempotencyService(
            @Autowired(required = false) JdbcTemplate jdbcTemplate,
            @Autowired(required = false) PlatformTransactionManager txManager,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = txManager != null ? new TransactionTemplate(txManager) : null;
        this.objectMapper = objectMapper;
    }

    public <T> T execute(
            String actorUserId,
            String scopePath,
            String idempotencyKey,
            Object canonicalPayload,
            int httpStatus,
            Class<T> responseType,
            Supplier<T> action
    ) {
        String scopeKey = actorUserId + "|" + scopePath + "|" + idempotencyKey;
        String payloadHash = sha256(canonicalJson(canonicalPayload));

        if (jdbcTemplate != null && txTemplate != null && objectMapper != null) {
            return txTemplate.execute(tx -> {
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
                        throw new ConflictException("Idempotency-Key đã được sử dụng với nội dung khác.");
                    }
                    return responseType.cast(hit.body());
                }

                T result = action.get();
                try {
                    String json = objectMapper.writeValueAsString(result);
                    jdbcTemplate.update(
                            "INSERT INTO api_idempotency (scope_key, payload_hash, http_status, response_body, expires_at) "
                                    + "VALUES (?, ?, ?, ?, ?)",
                            scopeKey,
                            payloadHash,
                            httpStatus,
                            json,
                            Timestamp.from(now.plus(48, ChronoUnit.HOURS)));
                } catch (Exception e) {
                    throw new IllegalStateException("Không thể lưu idempotency vào PostgreSQL.", e);
                }
                return result;
            });
        }

        Object lock = fallbackLocks.computeIfAbsent(scopeKey, k -> new Object());
        synchronized (lock) {
            Instant now = Instant.now();
            CachedEntry existing = fallbackStore.get(scopeKey);
            if (existing != null && now.isBefore(existing.expiresAt())) {
                if (!existing.payloadHash().equals(payloadHash)) {
                    throw new ConflictException("Idempotency-Key đã được sử dụng với nội dung khác.");
                }
                return responseType.cast(existing.body());
            }
            T result = action.get();
            fallbackStore.put(scopeKey, new CachedEntry(payloadHash, httpStatus, result, now.plus(48, ChronoUnit.HOURS)));
            return result;
        }
    }

    private static String canonicalJson(Object payload) {
        if (payload == null) {
            return "null";
        }
        try {
            return CANONICAL_MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể chuẩn hóa payload cho idempotency fingerprint.", e);
        }
    }

    private static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
