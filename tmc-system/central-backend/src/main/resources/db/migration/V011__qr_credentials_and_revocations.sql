-- PHỤ TRÁCH: Task 8 — tích hợp với Task 6.
-- Migration additive V011: lưu trữ bền vững QR credentials (tối đa 1 ACTIVE/nhân viên)
-- và lịch sử thu hồi QR (append-only) trên PostgreSQL.

CREATE TABLE IF NOT EXISTS qr_credentials (
    id          VARCHAR(64) PRIMARY KEY,
    employee_id VARCHAR(64) NOT NULL,
    state       VARCHAR(16) NOT NULL CHECK (state IN ('ACTIVE', 'REVOKED')),
    created_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    CONSTRAINT chk_qr_credential_state_revoked_at CHECK (
        (state = 'ACTIVE' AND revoked_at IS NULL) OR
        (state = 'REVOKED' AND revoked_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_qr_credentials_one_active_per_employee
    ON qr_credentials (employee_id)
    WHERE state = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_qr_credentials_emp_created
    ON qr_credentials (employee_id, created_at DESC);

CREATE TABLE IF NOT EXISTS qr_revocations (
    seq            BIGSERIAL PRIMARY KEY,
    revocation_id  VARCHAR(128) NOT NULL,
    op             VARCHAR(16) NOT NULL CHECK (op IN ('UPSERT', 'DELETE')),
    kind           VARCHAR(16) NOT NULL CHECK (kind IN ('SUB', 'JTI', 'KID')),
    value          VARCHAR(256) NOT NULL,
    revoked_before TIMESTAMPTZ,
    effective_at   TIMESTAMPTZ NOT NULL,
    recorded_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_qr_revocation_sub_cutoff CHECK (
        (kind = 'SUB' AND revoked_before IS NOT NULL) OR
        (kind <> 'SUB' AND revoked_before IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_qr_revocations_revocation_id
    ON qr_revocations (revocation_id, seq DESC);

CREATE OR REPLACE FUNCTION prevent_qr_revocations_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'qr_revocations is append-only; UPDATE and DELETE are prohibited';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_qr_revocations_append_only ON qr_revocations;
CREATE TRIGGER trg_qr_revocations_append_only
    BEFORE UPDATE OR DELETE ON qr_revocations
    FOR EACH ROW EXECUTE FUNCTION prevent_qr_revocations_mutation();
