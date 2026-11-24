ALTER TABLE edge_runtime_state ADD COLUMN bootstrap_generated_at INTEGER;
ALTER TABLE edge_runtime_state ADD COLUMN bootstrap_snapshot_hash TEXT;

CREATE TABLE trusted_signing_keys (
    edge_id TEXT NOT NULL PRIMARY KEY REFERENCES edge_runtime_state(edge_id),
    snapshot BLOB NOT NULL
) STRICT;

CREATE TABLE cached_access_policy (
    edge_id TEXT NOT NULL PRIMARY KEY REFERENCES edge_runtime_state(edge_id),
    snapshot BLOB NOT NULL
) STRICT;

CREATE TABLE cached_gates (
    edge_id TEXT NOT NULL PRIMARY KEY REFERENCES edge_runtime_state(edge_id),
    snapshot BLOB NOT NULL
) STRICT;

CREATE TABLE cached_revocations (
    edge_id TEXT NOT NULL REFERENCES edge_runtime_state(edge_id),
    id TEXT NOT NULL,
    entry BLOB NOT NULL,
    PRIMARY KEY (edge_id, id)
) STRICT;
