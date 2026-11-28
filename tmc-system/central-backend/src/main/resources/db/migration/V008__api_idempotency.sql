-- PHỤ TRÁCH: Task 8 — tích hợp với Task 1 & Task 3.
-- Bảng lưu trữ Idempotency-Key bền vững tối thiểu 48 giờ (CONTRACT-V1 §2, IDEM-04/07/08).
-- Migration additive, không sửa đổi hoặc xóa dữ liệu hiện có.

CREATE TABLE IF NOT EXISTS api_idempotency (
    scope_key      VARCHAR(255) PRIMARY KEY,
    payload_hash   VARCHAR(64)  NOT NULL,
    http_status    INT          NOT NULL,
    response_body  TEXT         NOT NULL,
    created_at     TIMESTAMPTZ(3) NOT NULL DEFAULT NOW(),
    expires_at     TIMESTAMPTZ(3) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_api_idempotency_expires_at ON api_idempotency (expires_at);
