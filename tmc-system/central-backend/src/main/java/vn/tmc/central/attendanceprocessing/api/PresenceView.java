package vn.tmc.central.attendanceprocessing.api;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO danh sách hiện diện tại nhà máy theo CONTRACT-V1.md §9.
 * GET /api/v1/attendance/presence -> {asOf, coverage:"CENTRAL_SYNCED", items:[{employeeId, lastDirection, lastOccurredAt}], offlineEdgeIds:UUID[]}
 */
public record PresenceView(
        Instant asOf,
        String coverage,
        List<PresenceItem> items,
        List<UUID> offlineEdgeIds
) {
    public record PresenceItem(
            UUID employeeId,
            String lastDirection,
            Instant lastOccurredAt
    ) {
        public PresenceItem {
            Objects.requireNonNull(employeeId, "employeeId must not be null");
            Objects.requireNonNull(lastDirection, "lastDirection must not be null");
            Objects.requireNonNull(lastOccurredAt, "lastOccurredAt must not be null");
        }
    }

    public PresenceView {
        Objects.requireNonNull(asOf, "asOf must not be null");
        if (coverage == null) coverage = "CENTRAL_SYNCED";
        items = items != null ? Collections.unmodifiableList(items) : List.of();
        offlineEdgeIds = offlineEdgeIds != null ? Collections.unmodifiableList(offlineEdgeIds) : List.of();
    }
}
