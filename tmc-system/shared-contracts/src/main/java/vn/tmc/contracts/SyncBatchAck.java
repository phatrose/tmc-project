package vn.tmc.contracts;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO ACK đồng bộ theo CONTRACT-V1.md §7.
 * Response 200: {syncBatchId, acks:[{eventId, status, acceptedAt, code}], lastContiguousSequenceNo}
 * 
 * QUY TẮC BẮT BUỘC (CONTRACT-V1 §7):
 * - Đúng một ACK / event, cùng thứ tự request.
 * - ACCEPTED / DUPLICATE: có acceptedAt là thời điểm commit lần đầu, code = null.
 * - CONFLICT / REJECTED: acceptedAt = null, code bắt buộc!
 * - Codes chuẩn: EVENT_CONTENT_CONFLICT, SEQUENCE_CONFLICT, UNSUPPORTED_SCHEMA,
 *   UNSUPPORTED_EVENT_TYPE, INVALID_PAYLOAD, HASH_MISMATCH, UNKNOWN_GATE.
 */
public record SyncBatchAck(
        UUID syncBatchId,
        List<EventAck> acks,
        long lastContiguousSequenceNo
) {
    public enum Status { ACCEPTED, DUPLICATE, CONFLICT, REJECTED }

    public static final String CODE_EVENT_CONTENT_CONFLICT = "EVENT_CONTENT_CONFLICT";
    public static final String CODE_SEQUENCE_CONFLICT = "SEQUENCE_CONFLICT";
    public static final String CODE_UNSUPPORTED_SCHEMA = "UNSUPPORTED_SCHEMA";
    public static final String CODE_UNSUPPORTED_EVENT_TYPE = "UNSUPPORTED_EVENT_TYPE";
    public static final String CODE_INVALID_PAYLOAD = "INVALID_PAYLOAD";
    public static final String CODE_HASH_MISMATCH = "HASH_MISMATCH";
    public static final String CODE_UNKNOWN_GATE = "UNKNOWN_GATE";

    public record EventAck(
            UUID eventId,
            Status status,
            Instant acceptedAt,
            String code
    ) {
        public EventAck {
            Objects.requireNonNull(eventId, "eventId must not be null");
            Objects.requireNonNull(status, "status must not be null");

            if (status == Status.ACCEPTED || status == Status.DUPLICATE) {
                Objects.requireNonNull(acceptedAt, "acceptedAt is required for ACCEPTED/DUPLICATE status");
                if (code != null) {
                    throw new IllegalArgumentException("code must be null for ACCEPTED/DUPLICATE status");
                }
            } else {
                // CONFLICT or REJECTED
                if (acceptedAt != null) {
                    throw new IllegalArgumentException("acceptedAt must be null for CONFLICT/REJECTED status");
                }
                if (code == null || code.isBlank()) {
                    throw new IllegalArgumentException("code is required for CONFLICT/REJECTED status");
                }
            }
        }

        public static EventAck accepted(UUID eventId, Instant acceptedAt) {
            return new EventAck(eventId, Status.ACCEPTED, acceptedAt, null);
        }

        public static EventAck duplicate(UUID eventId, Instant acceptedAt) {
            return new EventAck(eventId, Status.DUPLICATE, acceptedAt, null);
        }

        public static EventAck conflict(UUID eventId, String code) {
            return new EventAck(eventId, Status.CONFLICT, null, code);
        }

        public static EventAck rejected(UUID eventId, String code) {
            return new EventAck(eventId, Status.REJECTED, null, code);
        }
    }

    public SyncBatchAck {
        Objects.requireNonNull(syncBatchId, "syncBatchId must not be null");
        Objects.requireNonNull(acks, "acks must not be null");
        acks = Collections.unmodifiableList(acks);
    }
}
