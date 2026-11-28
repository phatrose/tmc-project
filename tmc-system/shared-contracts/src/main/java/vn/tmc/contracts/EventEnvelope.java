package vn.tmc.contracts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO envelope theo CONTRACT-V1.md §6.
 * NGUỒN: CONTRACT-V1.md §6:
 * {eventId:UUIDv7, eventType:string, schemaVersion:1, edgeId:UUID, gateId:UUID, factoryId:UUID, sequenceNo:int, correlationId:UUID, occurredAt:Instant, payloadSha256:string, payload:string}
 * 
 * HƯỚNG DẪN TRIỂN KHAI:
 * - schemaVersion chốt là integer 1.
 * - sequenceNo: JSON integer trong [1, 9007199254740991], tăng dần theo edgeId.
 * - payload: chuỗi JSON canonical JCS RFC 8785, UTF-8.
 * - payloadSha256: hex SHA-256 chữ thường 64 ký tự.
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int schemaVersion,
        UUID edgeId,
        UUID gateId,
        UUID factoryId,
        long sequenceNo,
        UUID correlationId,
        Instant occurredAt,
        String payloadSha256,
        String payload
) {
    public static final String EVT_ATTENDANCE_RECORDED = "attendance.event.recorded";
    public static final String EVT_ACCESS_DENIED = "access.attempt.denied";
    public static final String EVT_COMMAND_RESULT = "gate.command.result";
    public static final String EVT_REPLAY_REPORTED = "replay.cache.reported";

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(edgeId, "edgeId must not be null");
        Objects.requireNonNull(gateId, "gateId must not be null");
        Objects.requireNonNull(factoryId, "factoryId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(payloadSha256, "payloadSha256 must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schemaVersion must be 1, got: " + schemaVersion);
        }
        if (sequenceNo <= 0) {
            throw new IllegalArgumentException("sequenceNo must be greater than 0, got: " + sequenceNo);
        }
    }

    /**
     * Constructor thuận tiện nhận UUID dạng String.
     */
    public EventEnvelope(
            UUID eventId,
            String eventType,
            int schemaVersion,
            String edgeId,
            String gateId,
            String factoryId,
            long sequenceNo,
            String correlationId,
            Instant occurredAt,
            String payloadSha256,
            String payload
    ) {
        this(
                eventId,
                eventType,
                schemaVersion,
                edgeId != null ? parseOrDeterministicUuid(edgeId) : null,
                gateId != null ? parseOrDeterministicUuid(gateId) : null,
                factoryId != null ? parseOrDeterministicUuid(factoryId) : null,
                sequenceNo,
                correlationId != null ? parseOrDeterministicUuid(correlationId) : null,
                occurredAt,
                payloadSha256,
                payload
        );
    }

    private static UUID parseOrDeterministicUuid(String val) {
        try {
            return UUID.fromString(val);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(val.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Tính mã băm SHA-256 chữ thường 64 ký tự.
     */
    public static String computeSha256(String text) {
        if (text == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm not available", e);
        }
    }

    /**
     * Xác thực tính toàn vẹn của chuỗi canonical JSON payload.
     */
    public boolean isHashValid() {
        if (payload == null || payloadSha256 == null) {
            return false;
        }
        String computed = computeSha256(payload);
        return payloadSha256.equalsIgnoreCase(computed);
    }
}
