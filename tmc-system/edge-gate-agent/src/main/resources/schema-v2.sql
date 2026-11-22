CREATE TABLE local_sync_batches (
    batch_id TEXT NOT NULL PRIMARY KEY,
    edge_id TEXT NOT NULL REFERENCES edge_runtime_state(edge_id),
    state TEXT NOT NULL CHECK (state IN ('READY', 'IN_FLIGHT', 'RETRY_WAIT', 'COMPLETED')),
    member_count INTEGER NOT NULL CHECK (member_count BETWEEN 1 AND 50),
    membership_fingerprint TEXT NOT NULL,
    created_at INTEGER NOT NULL
) STRICT;

CREATE UNIQUE INDEX one_active_batch_per_edge ON local_sync_batches(edge_id) WHERE state <> 'COMPLETED';
CREATE INDEX event_delivery_batch ON event_delivery(batch_id);
