CREATE TABLE monitor.observation_epochs (
    monitor_id uuid NOT NULL REFERENCES monitor.monitors(id),
    execution_generation bigint NOT NULL,
    configuration_version bigint NOT NULL,
    lifecycle varchar(6) NOT NULL CHECK (lifecycle IN ('ACTIVE','PAUSED')),
    started_at timestamptz NOT NULL,
    ended_at timestamptz,
    freshness_seconds integer NOT NULL CHECK (freshness_seconds > 0),
    PRIMARY KEY (monitor_id, execution_generation),
    CHECK (ended_at IS NULL OR ended_at >= started_at)
);

-- A later generation has no reconstructible historical pause/policy boundaries.
INSERT INTO monitor.observation_epochs
    SELECT id, execution_generation, configuration_version, lifecycle,
        CASE WHEN execution_generation = 1 THEN created_at ELSE CURRENT_TIMESTAMP END,
        NULL, 2 * interval_seconds + timeout_seconds
    FROM monitor.monitors;

CREATE FUNCTION monitor.record_observation_epoch() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE boundary timestamptz;
BEGIN
    IF TG_OP = 'INSERT' THEN
        boundary := NEW.created_at;
    ELSIF NEW.execution_generation <> OLD.execution_generation THEN
        SELECT greatest(NEW.updated_at, started_at) INTO boundary
            FROM monitor.observation_epochs
            WHERE monitor_id = NEW.id AND execution_generation = OLD.execution_generation;
        boundary := coalesce(boundary, NEW.updated_at);
        UPDATE monitor.observation_epochs SET ended_at = boundary
            WHERE monitor_id = NEW.id AND execution_generation = OLD.execution_generation;
    ELSE
        RETURN NEW;
    END IF;
    INSERT INTO monitor.observation_epochs VALUES
        (NEW.id, NEW.execution_generation, NEW.configuration_version, NEW.lifecycle,
         boundary, NULL, 2 * NEW.interval_seconds + NEW.timeout_seconds);
    RETURN NEW;
END;
$$;

CREATE TRIGGER observation_epoch_change AFTER INSERT OR UPDATE ON monitor.monitors
    FOR EACH ROW EXECUTE FUNCTION monitor.record_observation_epoch();

CREATE INDEX observation_epochs_period_idx ON monitor.observation_epochs (monitor_id, started_at);
CREATE INDEX transitions_query_idx ON monitor.state_transitions (monitor_id, confirmed_at, id);
