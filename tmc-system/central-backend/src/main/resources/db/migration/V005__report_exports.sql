CREATE TABLE report_exports (
    id uuid PRIMARY KEY,
    kind text NOT NULL CHECK (kind IN ('ATTENDANCE','AUDIT')),
    factory_id uuid NOT NULL,
    owner_id uuid NOT NULL,
    actor_context jsonb NOT NULL,
    period varchar(7),
    from_at timestamptz(3),
    to_at timestamptz(3),
    format text NOT NULL CHECK (format IN ('CSV','XLSX','PDF')),
    state text NOT NULL CHECK (state IN ('QUEUED','RUNNING','READY','FAILED')),
    created_at timestamptz(3) NOT NULL,
    expires_at timestamptz(3),
    error_code text,
    lease_id uuid,
    lease_until timestamptz,
    file_bytes bytea,
    media_type text,
    extension text,
    CHECK ((kind='ATTENDANCE' AND period IS NOT NULL AND from_at IS NULL AND to_at IS NULL)
        OR (kind='AUDIT' AND period IS NULL AND from_at IS NOT NULL AND to_at IS NOT NULL AND from_at<to_at)),
    CHECK (state<>'READY' OR (file_bytes IS NOT NULL AND media_type IS NOT NULL
        AND extension IS NOT NULL AND expires_at=created_at+interval '24 hours'))
);
CREATE INDEX ix_report_exports_due ON report_exports(state,lease_until,created_at,id);
CREATE INDEX ix_report_exports_owner ON report_exports(owner_id,created_at DESC);

CREATE TABLE reporting_idempotency (
    actor_id uuid NOT NULL,
    path text NOT NULL,
    key uuid NOT NULL,
    input_hash varchar(64) NOT NULL,
    outcome jsonb,
    expires_at timestamptz(3) NOT NULL,
    PRIMARY KEY (actor_id,path,key)
);
