CREATE FUNCTION payroll_payload_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.payload IS DISTINCT FROM OLD.payload OR NEW.source_snapshot IS DISTINCT FROM OLD.source_snapshot
        OR NEW.checksum IS DISTINCT FROM OLD.checksum OR NEW.factory_id IS DISTINCT FROM OLD.factory_id
        OR NEW.period IS DISTINCT FROM OLD.period OR NEW.period_version IS DISTINCT FROM OLD.period_version
        OR NEW.record_count IS DISTINCT FROM OLD.record_count OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR NEW.factory_zone IS DISTINCT FROM OLD.factory_zone OR NEW.deadline IS DISTINCT FROM OLD.deadline THEN
        RAISE EXCEPTION 'payroll snapshot and payload are immutable';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payroll_no_payload_change BEFORE UPDATE ON export_batches FOR EACH ROW EXECUTE FUNCTION payroll_payload_immutable();
