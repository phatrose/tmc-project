ALTER TABLE audit_logs ADD COLUMN employee_id uuid;
ALTER TABLE audit_logs ADD COLUMN department_id uuid;
CREATE INDEX ix_audit_employee_scope ON audit_logs(employee_id,occurred_at DESC,audit_id DESC);
CREATE INDEX ix_audit_department_scope ON audit_logs(factory_id,department_id,occurred_at DESC,audit_id DESC);

CREATE TABLE payroll_periods (
    factory_id uuid NOT NULL, period varchar(7) NOT NULL,
    state text NOT NULL CHECK (state IN ('OPEN','CLOSING','CLOSED','REOPENED')),
    version integer NOT NULL CHECK (version>0), closed_at timestamptz(3),
    PRIMARY KEY(factory_id,period)
);
CREATE TABLE export_batches (
    id uuid PRIMARY KEY, factory_id uuid NOT NULL, period varchar(7) NOT NULL,
    period_version integer NOT NULL CHECK (period_version>0),
    state text NOT NULL CHECK (state IN ('QUEUED','SENDING','EXPORT_SUCCESS','PARTIAL','FAILED')),
    record_count integer NOT NULL CHECK (record_count>=0),
    accepted_count integer NOT NULL DEFAULT 0 CHECK (accepted_count>=0 AND accepted_count<=record_count),
    checksum varchar(64) NOT NULL, payload bytea NOT NULL, source_snapshot jsonb NOT NULL,
    factory_zone text NOT NULL, deadline timestamptz(3) NOT NULL,
    created_at timestamptz(3) NOT NULL, completed_at timestamptz(3), error_code text,
    UNIQUE(factory_id,period,period_version)
);
CREATE TABLE outbox_messages (
    id uuid PRIMARY KEY, batch_id uuid NOT NULL UNIQUE REFERENCES export_batches(id),
    topic text NOT NULL DEFAULT 'export.batch.created', next_attempt_at timestamptz NOT NULL,
    attempts integer NOT NULL DEFAULT 0, lease_id uuid, lease_until timestamptz,
    done boolean NOT NULL DEFAULT false
);
CREATE INDEX ix_payroll_outbox_due ON outbox_messages(done,next_attempt_at,lease_until);
CREATE TABLE period_history (
    id uuid PRIMARY KEY, factory_id uuid NOT NULL, period varchar(7) NOT NULL,
    action text NOT NULL CHECK (action IN ('CLOSE','REOPEN')), actor_id uuid, reason text NOT NULL CHECK (btrim(reason)<>''),
    occurred_at timestamptz(3) NOT NULL, version integer NOT NULL
);
CREATE INDEX ix_period_history ON period_history(factory_id,period,occurred_at DESC,id DESC);
CREATE TABLE reporting_deadline_alerts (
    factory_id uuid NOT NULL, period varchar(7) NOT NULL, version integer NOT NULL,
    occurred_at timestamptz(3) NOT NULL, PRIMARY KEY(factory_id,period,version)
);
CREATE TABLE reporting_system_identity (name text PRIMARY KEY,id uuid NOT NULL UNIQUE);
INSERT INTO reporting_system_identity VALUES('payroll-scheduler',gen_random_uuid());
CREATE TABLE hr_mock_batches (id uuid PRIMARY KEY,body_hash varchar(64) NOT NULL,receipt jsonb NOT NULL);
