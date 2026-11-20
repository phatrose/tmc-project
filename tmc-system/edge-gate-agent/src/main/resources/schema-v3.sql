ALTER TABLE local_sync_batches ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0);
ALTER TABLE local_sync_batches ADD COLUMN next_attempt_at INTEGER;
ALTER TABLE local_sync_batches ADD COLUMN stop_reason TEXT;
