-- PHỤ TRÁCH: Task 8 — tích hợp với Task 1 & Task 6 (DATA-07).
-- Migration additive V012: bảng outbox thu hồi credential/policy khi nhân viên bị tắt (active=false)
-- trong cùng transaction với cập nhật bảng employees.

CREATE TABLE IF NOT EXISTS employee_revocation_outbox (
    id            UUID PRIMARY KEY,
    employee_id   VARCHAR(64) NOT NULL,
    event_type    VARCHAR(64) NOT NULL CHECK (event_type IN ('EMPLOYEE_DEACTIVATED')),
    actor_user_id VARCHAR(64),
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at  TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_emp_revocation_outbox_pending
    ON employee_revocation_outbox (status, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_emp_revocation_outbox_employee
    ON employee_revocation_outbox (employee_id, created_at DESC);
