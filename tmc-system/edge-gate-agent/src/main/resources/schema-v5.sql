ALTER TABLE local_gate_commands ADD COLUMN result_event_id TEXT REFERENCES local_events (event_id);

CREATE UNIQUE INDEX local_gate_commands_result_event
    ON local_gate_commands (result_event_id)
    WHERE result_event_id IS NOT NULL;
