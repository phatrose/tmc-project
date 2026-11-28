-- PHỤ TRÁCH: Task 8 — tích hợp với Task 3.
-- Migration additive V010: lưu trữ bền vững sự kiện chấm công (append-only),
-- tổng hợp công ngày, xung đột đồng bộ và con trỏ sequence của Edge trên PostgreSQL.

CREATE TABLE IF NOT EXISTS attendance_events (
    event_id        UUID PRIMARY KEY,
    event_type      VARCHAR(64) NOT NULL,
    schema_version  INT NOT NULL DEFAULT 1,
    edge_id         UUID NOT NULL,
    gate_id         UUID NOT NULL,
    factory_id      UUID NOT NULL,
    sequence_no     BIGINT NOT NULL,
    correlation_id  UUID NOT NULL,
    observed_at     TIMESTAMPTZ NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL,
    employee_id     UUID,
    direction       VARCHAR(16),
    source          VARCHAR(32) NOT NULL DEFAULT 'ONLINE',
    jti             UUID,
    kid             VARCHAR(128),
    token_issued_at TIMESTAMPTZ,
    command_id      UUID,
    operator_id     UUID,
    reason          TEXT,
    flags_json      TEXT NOT NULL DEFAULT '[]',
    payload_sha256  VARCHAR(128) NOT NULL,
    raw_payload     TEXT NOT NULL,
    CONSTRAINT uq_attendance_events_edge_seq UNIQUE (edge_id, sequence_no)
);

CREATE INDEX IF NOT EXISTS idx_attendance_events_emp_obs
    ON attendance_events (employee_id, observed_at DESC, event_id DESC);
CREATE INDEX IF NOT EXISTS idx_attendance_events_factory_obs
    ON attendance_events (factory_id, observed_at DESC, event_id DESC);
CREATE INDEX IF NOT EXISTS idx_attendance_events_jti
    ON attendance_events (jti) WHERE jti IS NOT NULL;

CREATE OR REPLACE FUNCTION prevent_attendance_events_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'attendance_events is append-only; UPDATE and DELETE are prohibited';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_attendance_events_append_only ON attendance_events;
CREATE TRIGGER trg_attendance_events_append_only
    BEFORE UPDATE OR DELETE ON attendance_events
    FOR EACH ROW EXECUTE FUNCTION prevent_attendance_events_mutation();

CREATE TABLE IF NOT EXISTS daily_attendance_records (
    id               VARCHAR(128) PRIMARY KEY,
    employee_id      UUID NOT NULL,
    factory_id       UUID NOT NULL,
    work_date        DATE NOT NULL,
    shift_id         UUID,
    shift_code       VARCHAR(64) NOT NULL DEFAULT 'SHIFT_1',
    first_in         TIMESTAMPTZ,
    last_out         TIMESTAMPTZ,
    worked_minutes   INT,
    late_minutes     INT NOT NULL DEFAULT 0,
    early_minutes    INT NOT NULL DEFAULT 0,
    is_missing_swipe BOOLEAN NOT NULL DEFAULT FALSE,
    is_adjusted      BOOLEAN NOT NULL DEFAULT FALSE,
    flags_json       TEXT NOT NULL DEFAULT '[]',
    status           VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    version          INT NOT NULL DEFAULT 1,
    CONSTRAINT uq_daily_attendance_emp_date UNIQUE (employee_id, work_date)
);

CREATE INDEX IF NOT EXISTS idx_daily_attendance_factory_date
    ON daily_attendance_records (factory_id, work_date);

CREATE TABLE IF NOT EXISTS sync_conflicts (
    id            VARCHAR(128) PRIMARY KEY,
    event_id      UUID NOT NULL,
    edge_id       VARCHAR(128),
    existing_hash VARCHAR(128),
    incoming_hash VARCHAR(128),
    occurred_at   TIMESTAMPTZ,
    reported_at   TIMESTAMPTZ NOT NULL,
    details       TEXT
);

CREATE TABLE IF NOT EXISTS edge_sync_cursors (
    edge_id                     VARCHAR(128) PRIMARY KEY,
    last_contiguous_sequence_no BIGINT NOT NULL DEFAULT 0,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
