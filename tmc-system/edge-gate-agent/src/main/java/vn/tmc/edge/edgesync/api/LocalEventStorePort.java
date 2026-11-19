package vn.tmc.edge.edgesync.api;

import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.contracts.SyncBatchAck;

import java.util.Optional;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 7.
 * The local transaction boundary for Gate Station and the sync worker.
 */
public interface LocalEventStorePort {
    /**
     * Commits an already verified QR ALLOW atomically.
     * Send a device command only from the returned newCommand, which is empty on retries.
     */
    CommittedEvent reserveAndAppend(UUID scanId, EventDraft draft, ReplayReservation reservation);

    /** Look up a retry before QR verification. A changed input raises IDEMPOTENCY_CONFLICT. */
    Optional<ScanOutcome> findScanOutcome(UUID scanId, ScanInputFingerprint fingerprint);

    /** Durably append a non-attendance event and initialize its delivery state. */
    CommittedEvent append(EventDraft draft);
    SyncBatchRequest pendingBatch(int maxEvents);

    void applyAcknowledgements(SyncBatchAck acknowledgement);
}
