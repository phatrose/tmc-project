package vn.tmc.contracts;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO request sync theo CONTRACT-V1.md §7.
 * Request: {syncBatchId:UUIDv4, events:EventEnvelope[]}
 * 
 * QUY TẮC BẮT BUỘC (CONTRACT-V1 §7):
 * - 1 đến 50 sự kiện (1 <= events.size() <= 50).
 * - sequence tăng dần liên tục theo edgeId.
 * - Trùng eventId hoặc sequence trong cùng một lô bị từ chối 400 VALIDATION_ERROR.
 */
public record SyncBatchRequest(
        UUID syncBatchId,
        List<EventEnvelope> events
) {
    public static final int MAX_EVENTS = 50;

    public SyncBatchRequest {
        Objects.requireNonNull(syncBatchId, "syncBatchId must not be null");
        Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty()) {
            throw new IllegalArgumentException("events must not be empty");
        }
        if (events.size() > MAX_EVENTS) {
            throw new IllegalArgumentException("events size cannot exceed " + MAX_EVENTS + ", got: " + events.size());
        }

        // Kiểm tra tính đơn điệu của sequence và trùng lặp eventId/sequence trong cùng lô
        Set<UUID> seenEventIds = new HashSet<>();
        long lastSeq = -1;
        for (EventEnvelope env : events) {
            if (!seenEventIds.add(env.eventId())) {
                throw new IllegalArgumentException("Duplicate eventId in batch: " + env.eventId());
            }
            if (lastSeq != -1 && env.sequenceNo() <= lastSeq) {
                throw new IllegalArgumentException("Sequence numbers must be strictly increasing, got " + env.sequenceNo() + " after " + lastSeq);
            }
            lastSeq = env.sequenceNo();
        }

        events = Collections.unmodifiableList(events);
    }
}
