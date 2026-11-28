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
     * Commits an authorized QR or MANUAL attendance ALLOW atomically.
     * QR uses a QR reservation and MANUAL uses an ACTION reservation.
     * Send a device command only from the returned newCommand, which is empty on retries.
     */
    CommittedEvent reserveAndAppend(UUID scanId, EventDraft draft, ReplayReservation reservation);

    /** Persists an authorized EMERGENCY command and verified ACTION reservation without an event.
     * Only the first successful commit returns a new command.
     */
    CommittedCommand commitEmergency(UUID scanId, CommandIntent command, ReplayReservation reservation);

    /**
     * Atomically records a terminal Turnstile outcome for a persisted command intent,
     * updates the scan outcome, and appends exactly one gate.command.result event.
     */
    CommittedEvent recordCommandResult(UUID scanId, CommandCompletion completion);

    /** Look up a retry before credential verification. A changed input raises IDEMPOTENCY_CONFLICT. */
    Optional<ScanOutcome> findScanOutcome(UUID scanId, ScanInputFingerprint fingerprint);

    /** Durably append a non-attendance event and initialize its delivery state. */
    CommittedEvent append(EventDraft draft);

    /** maxEvents limits new batches. An active batch retains its original membership. */
    SyncBatchRequest pendingBatch(int maxEvents);

    void applyAcknowledgements(SyncBatchAck acknowledgement);
}
