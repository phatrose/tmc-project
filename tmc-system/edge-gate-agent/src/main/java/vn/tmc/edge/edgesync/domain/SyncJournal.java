package vn.tmc.edge.edgesync.domain;

import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchRequest;

import java.time.Instant;
import java.util.UUID;

/** Durable lifecycle of a sync request. The attempt number fences a stale completion after recovery. */
public interface SyncJournal {
    record Attempt(SyncBatchRequest request, int number) {}
    record Schedule(Instant retryAt, String stopReason) {}

    Attempt claim(UUID edgeId);
    void complete(Attempt attempt, SyncBatchAck response);
    void defer(Attempt attempt, Schedule schedule);
    Schedule schedule(UUID edgeId);
    void resume(UUID edgeId);
}
