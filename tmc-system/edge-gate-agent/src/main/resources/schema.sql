CREATE TABLE edge_runtime_state (
    edge_id TEXT NOT NULL PRIMARY KEY,
    factory_id TEXT NOT NULL,
    last_sequence_no INTEGER NOT NULL DEFAULT 0 CHECK (last_sequence_no BETWEEN 0 AND 9007199254740991),
    bootstrap_revision INTEGER NOT NULL DEFAULT 0,
    bootstrap_trusted_until INTEGER,
    last_bootstrap_at INTEGER,
    last_central_contact_at INTEGER,
    last_contiguous_ack_sequence_no INTEGER NOT NULL DEFAULT 0,
    UNIQUE (edge_id, factory_id)
) STRICT;

-- All timestamps are UTC epoch milliseconds.
CREATE TABLE local_events (
    event_id TEXT NOT NULL PRIMARY KEY,
    event_type TEXT NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    edge_id TEXT NOT NULL,
    gate_id TEXT NOT NULL,
    factory_id TEXT NOT NULL,
    sequence_no INTEGER NOT NULL CHECK (sequence_no BETWEEN 1 AND 9007199254740991),
    correlation_id TEXT NOT NULL,
    occurred_at INTEGER NOT NULL,
    payload_sha256 TEXT NOT NULL CHECK (length(payload_sha256) = 64 AND payload_sha256 NOT GLOB '*[^0-9a-f]*'),
    payload_text TEXT NOT NULL,
    committed_at INTEGER NOT NULL,
    UNIQUE (edge_id, sequence_no),
    FOREIGN KEY (edge_id, factory_id) REFERENCES edge_runtime_state (edge_id, factory_id)
) STRICT;

CREATE TABLE event_delivery (
    event_id TEXT NOT NULL PRIMARY KEY REFERENCES local_events (event_id),
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'ACKNOWLEDGED', 'FAILED')),
    batch_id TEXT,
    ack_status TEXT,
    accepted_at INTEGER,
    failure_code TEXT,
    retention_until INTEGER
) STRICT;

CREATE TABLE local_replay_cache (
    kind TEXT NOT NULL CHECK (kind IN ('QR', 'ACTION')),
    jti TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    employee_id TEXT,
    direction TEXT CHECK (direction IN ('IN', 'OUT')),
    gate_id TEXT NOT NULL,
    event_id TEXT REFERENCES local_events (event_id) ON DELETE SET NULL,
    reserved_at INTEGER NOT NULL,
    purge_after INTEGER NOT NULL CHECK (purge_after >= expires_at + CASE kind WHEN 'QR' THEN 5000 ELSE 0 END),
    PRIMARY KEY (kind, jti)
) STRICT;

CREATE TABLE local_scan_outcomes (
    scan_id TEXT NOT NULL PRIMARY KEY,
    gate_id TEXT NOT NULL,
    input_fingerprint TEXT NOT NULL CHECK (length(input_fingerprint) = 64 AND input_fingerprint NOT GLOB '*[^0-9a-f]*'),
    employee_id TEXT,
    direction TEXT CHECK (direction IN ('IN', 'OUT')),
    decision TEXT NOT NULL CHECK (decision IN ('ALLOW', 'DENY', 'EMERGENCY')),
    reason_code TEXT NOT NULL,
    event_id TEXT REFERENCES local_events (event_id) ON DELETE SET NULL,
    command_id TEXT UNIQUE,
    command_status TEXT NOT NULL CHECK (command_status IN ('NOT_SENT', 'UNLOCK_ACCEPTED', 'FAILED', 'TIMEOUT')),
    occurred_at INTEGER NOT NULL,
    mode TEXT NOT NULL CHECK (mode IN ('ONLINE', 'OFFLINE')),
    retain_until INTEGER NOT NULL,
    FOREIGN KEY (command_id) REFERENCES local_gate_commands (command_id) DEFERRABLE INITIALLY DEFERRED
) STRICT;

CREATE INDEX local_scan_outcomes_last_allow
    ON local_scan_outcomes (employee_id, gate_id, direction, occurred_at DESC)
    WHERE decision = 'ALLOW';

CREATE TABLE local_gate_commands (
    command_id TEXT NOT NULL PRIMARY KEY,
    scan_id TEXT NOT NULL UNIQUE REFERENCES local_scan_outcomes (scan_id),
    gate_id TEXT NOT NULL,
    mode TEXT NOT NULL CHECK (mode IN ('QR', 'MANUAL', 'EMERGENCY')),
    operator_id TEXT,
    reason TEXT,
    status TEXT NOT NULL CHECK (status IN ('NOT_SENT', 'UNLOCK_ACCEPTED', 'FAILED', 'TIMEOUT')),
    completed_at INTEGER,
    code TEXT,
    retain_until INTEGER NOT NULL
) STRICT;
