CREATE TABLE monitor.retention_state (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    history_available_from timestamptz
);
INSERT INTO monitor.retention_state (singleton) VALUES (true);

-- Minimal permanent deduplication receipts; payload/history are still eligible for deletion.
CREATE TABLE monitor.retired_executions (
    execution_id uuid PRIMARY KEY,
    monitor_id uuid NOT NULL REFERENCES monitor.monitors(id)
);
CREATE FUNCTION monitor.reject_retired_execution() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM monitor.retired_executions WHERE execution_id=NEW.execution_id) THEN
        RAISE EXCEPTION 'Execution history was retired';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER reject_retired_execution BEFORE INSERT ON monitor.check_results
    FOR EACH ROW EXECUTE FUNCTION monitor.reject_retired_execution();
CREATE INDEX check_results_retention_idx ON monitor.check_results (finished_at, execution_id);
CREATE INDEX check_results_snapshot_idx
    ON monitor.check_results (monitor_id, configuration_version, execution_generation);
CREATE INDEX result_applications_start_idx ON monitor.result_applications (logical_started_at, execution_id)
    WHERE disposition = 'APPLIED';
CREATE INDEX incidents_retention_idx ON monitor.incidents (recovered_at, id) WHERE recovered_at IS NOT NULL;
CREATE INDEX notification_transition_idx ON monitor.notification_intents (transition_id);
CREATE INDEX notification_status_idx ON monitor.notification_intents (status);
CREATE INDEX observation_epochs_retention_idx ON monitor.observation_epochs (ended_at)
    WHERE ended_at IS NOT NULL;
