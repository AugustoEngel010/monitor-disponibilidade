CREATE TABLE monitor.check_attempts (
    monitor_id uuid PRIMARY KEY REFERENCES monitor.monitors(id),
    execution_id uuid NOT NULL UNIQUE,
    configuration_version bigint NOT NULL CHECK (configuration_version >= 1),
    execution_generation bigint NOT NULL CHECK (execution_generation >= 1),
    scheduled_at timestamptz NOT NULL,
    started_at timestamptz NOT NULL
);

CREATE INDEX monitors_due_idx ON monitor.monitors (next_check_at, id) WHERE lifecycle = 'ACTIVE';
