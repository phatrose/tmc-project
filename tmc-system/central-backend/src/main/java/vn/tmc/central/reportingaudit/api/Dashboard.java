package vn.tmc.central.reportingaudit.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Dashboard(@JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone="UTC") Instant asOf,
        int expectedCount, int presentCount, int absentCount, int anomalyCount, List<UUID> offlineEdgeIds) {
    public Dashboard { offlineEdgeIds = List.copyOf(offlineEdgeIds); }
}
