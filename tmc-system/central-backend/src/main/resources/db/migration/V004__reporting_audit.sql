CREATE TABLE audit_logs (
    audit_id uuid NOT NULL,
    actor_id uuid,
    device_id uuid,
    factory_id uuid,
    action text NOT NULL CHECK (btrim(action) <> ''),
    resource_type text NOT NULL CHECK (btrim(resource_type) <> ''),
    resource_id text NOT NULL CHECK (btrim(resource_id) <> ''),
    result text NOT NULL CHECK (result IN ('SUCCESS','DENIED','FAILURE')),
    occurred_at timestamptz(3) NOT NULL,
    correlation_id uuid NOT NULL,
    reason text CHECK (reason IS NULL OR btrim(reason) <> ''),
    PRIMARY KEY (audit_id,occurred_at)
) PARTITION BY RANGE (occurred_at);

DO $$ DECLARE month_start date; partition_name text; BEGIN
    FOR i IN -24..2 LOOP
        month_start := (date_trunc('month',CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + make_interval(months=>i))::date;
        partition_name := 'audit_logs_' || to_char(month_start,'YYYYMM');
        EXECUTE format('CREATE TABLE %I PARTITION OF audit_logs FOR VALUES FROM (%L) TO (%L)',
            partition_name,month_start::text || ' 00:00:00+00',
            (month_start+interval '1 month')::date::text || ' 00:00:00+00');
    END LOOP;
END $$;
CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;

CREATE INDEX ix_audit_time ON audit_logs(occurred_at DESC,audit_id DESC);
CREATE INDEX ix_audit_actor_time ON audit_logs(actor_id,occurred_at DESC,audit_id DESC);
CREATE INDEX ix_audit_factory_time ON audit_logs(factory_id,occurred_at DESC,audit_id DESC);
CREATE INDEX ix_audit_action_time ON audit_logs(action,occurred_at DESC,audit_id DESC);
CREATE INDEX ix_audit_resource ON audit_logs(resource_type,resource_id,occurred_at DESC);

CREATE FUNCTION reporting_forbid_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'audit data is append-only'; END $$;
CREATE TRIGGER audit_no_update_delete BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION reporting_forbid_audit_mutation();
CREATE TRIGGER audit_no_truncate BEFORE TRUNCATE ON audit_logs
    EXECUTE FUNCTION reporting_forbid_audit_mutation();
DO $$ DECLARE partition_id regclass; BEGIN
    FOR partition_id IN SELECT inhrelid::regclass FROM pg_inherits WHERE inhparent='audit_logs'::regclass LOOP
        EXECUTE format('CREATE TRIGGER audit_partition_no_truncate BEFORE TRUNCATE ON %s EXECUTE FUNCTION reporting_forbid_audit_mutation()',partition_id);
    END LOOP;
END $$;
