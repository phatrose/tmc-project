package vn.tmc.contracts;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO EVT-04: replay.cache.reported theo CONTRACT-V1.md §6.
 * Payload JSON canonical: {items:[{jti:UUID,firstEventId:UUID,seenAt:Instant,expiresAt:Instant}]} tối đa 50 items.
 */
public record ReplayReportPayload(
        List<ReplayItem> items
) {
    public static final int MAX_ITEMS = 50;

    public record ReplayItem(
            UUID jti,
            UUID firstEventId,
            Instant seenAt,
            Instant expiresAt
    ) {
        public ReplayItem {
            Objects.requireNonNull(jti, "jti must not be null");
            Objects.requireNonNull(firstEventId, "firstEventId must not be null");
            Objects.requireNonNull(seenAt, "seenAt must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }
    }

    public ReplayReportPayload {
        Objects.requireNonNull(items, "items must not be null");
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("ReplayReport items cannot exceed " + MAX_ITEMS);
        }
    }
}
