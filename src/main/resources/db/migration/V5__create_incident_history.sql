CREATE TABLE monitor.incidents (
    id uuid PRIMARY KEY,
    monitor_id uuid NOT NULL REFERENCES monitor.monitors(id),
    configuration_version bigint NOT NULL CHECK (configuration_version >= 1),
    first_failure_at timestamptz NOT NULL,
    down_confirmed_at timestamptz NOT NULL,
    recovery_started_at timestamptz,
    recovered_at timestamptz,
    UNIQUE (monitor_id, id),
    UNIQUE (monitor_id, id, configuration_version),
    CHECK (recovered_at IS NULL OR recovery_started_at IS NOT NULL)
);

CREATE UNIQUE INDEX incidents_one_open_per_monitor_idx
    ON monitor.incidents (monitor_id) WHERE recovered_at IS NULL;
CREATE INDEX incidents_history_idx ON monitor.incidents (monitor_id, down_confirmed_at, id);

ALTER TABLE monitor.monitors ADD CONSTRAINT monitors_open_incident_fk
    FOREIGN KEY (id, open_incident_id) REFERENCES monitor.incidents (monitor_id, id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE monitor.result_applications (
    execution_id uuid PRIMARY KEY REFERENCES monitor.check_results(execution_id),
    disposition varchar(12) NOT NULL CHECK (disposition IN ('APPLIED', 'HISTORY_ONLY')),
    logical_started_at timestamptz NOT NULL
);

-- Completed pre-MON-07 checks remain historical; pending reservations still need reconciliation.
INSERT INTO monitor.result_applications (execution_id, disposition, logical_started_at)
    SELECT r.execution_id, 'HISTORY_ONLY', r.started_at FROM monitor.check_results r
    WHERE NOT EXISTS (SELECT 1 FROM monitor.check_attempts a WHERE a.execution_id = r.execution_id);

ALTER TABLE monitor.check_results ADD CONSTRAINT check_results_snapshot_unique
    UNIQUE (execution_id, monitor_id, configuration_version);

CREATE TABLE monitor.state_transitions (
    id uuid PRIMARY KEY,
    execution_id uuid NOT NULL UNIQUE,
    monitor_id uuid NOT NULL,
    incident_id uuid NOT NULL,
    configuration_version bigint NOT NULL,
    type varchar(8) NOT NULL CHECK (type IN ('DOWN', 'RECOVERY')),
    confirmed_at timestamptz NOT NULL,
    UNIQUE (incident_id, type),
    FOREIGN KEY (execution_id, monitor_id, configuration_version)
        REFERENCES monitor.check_results(execution_id, monitor_id, configuration_version),
    FOREIGN KEY (monitor_id, incident_id, configuration_version)
        REFERENCES monitor.incidents(monitor_id, id, configuration_version)
);

CREATE TABLE monitor.notification_intents (
    id uuid PRIMARY KEY,
    transition_id uuid NOT NULL REFERENCES monitor.state_transitions(id),
    recipient varchar(254) NOT NULL,
    monitor_name varchar(100) NOT NULL,
    monitor_url varchar(2048) NOT NULL,
    first_failure_at timestamptz NOT NULL,
    down_confirmed_at timestamptz NOT NULL,
    recovery_started_at timestamptz,
    recovered_at timestamptz,
    status varchar(9) NOT NULL DEFAULT 'PENDING' CHECK (status = 'PENDING'),
    UNIQUE (transition_id, recipient)
);
