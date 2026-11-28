-- PHỤ TRÁCH: Task 3 / Task 8 — tích hợp với Task 3.
-- Migration additive (chưa từng áp dụng): bảng attendance_adjustments và adjustment_histories
-- cho lưu trữ bền vững yêu cầu điều chỉnh công trên PostgreSQL (DATA-02, IDEM-04/08).

CREATE TABLE IF NOT EXISTS attendance_adjustments (
    id                    UUID PRIMARY KEY,
    event_id              UUID NULL,
    employee_id           UUID NOT NULL,
    created_by            UUID NOT NULL,
    proposed_occurred_at  TIMESTAMPTZ(3) NULL,
    proposed_direction    VARCHAR(8) NULL,
    reason                VARCHAR(500) NOT NULL,
    state                 VARCHAR(16) NOT NULL,
    decided_by            UUID NULL,
    decision_reason       VARCHAR(500) NULL,
    created_at            TIMESTAMPTZ(3) NOT NULL,
    decided_at            TIMESTAMPTZ(3) NULL,
    version               INT NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_attendance_adjustments_emp ON attendance_adjustments (employee_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_attendance_adjustments_state ON attendance_adjustments (state, created_at DESC);

CREATE TABLE IF NOT EXISTS adjustment_histories (
    id             VARCHAR(64) PRIMARY KEY,
    adjustment_id  UUID NOT NULL REFERENCES attendance_adjustments(id),
    action         VARCHAR(32) NOT NULL,
    actor_id       VARCHAR(64) NOT NULL,
    occurred_at    TIMESTAMPTZ(3) NOT NULL,
    note           TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_adjustment_histories_adj ON adjustment_histories (adjustment_id, occurred_at ASC);
