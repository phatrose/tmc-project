package vn.tmc.edge.edgesync.domain;

import java.time.Instant;
import java.util.UUID;

public interface OperationsJournal {
    EdgeStatus.Queue queue(UUID edgeId);
    void recordCentralContact(UUID edgeId, Instant observedAt);
    void cleanup();
}
