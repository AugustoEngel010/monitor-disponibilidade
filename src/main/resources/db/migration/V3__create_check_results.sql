CREATE TABLE monitor.check_results (
    execution_id uuid PRIMARY KEY,
    monitor_id uuid NOT NULL REFERENCES monitor.monitors(id),
    configuration_version bigint NOT NULL CHECK (configuration_version >= 1),
    execution_generation bigint NOT NULL CHECK (execution_generation >= 1),
    started_at timestamptz NOT NULL,
    finished_at timestamptz NOT NULL,
    duration_nanos bigint NOT NULL CHECK (duration_nanos >= 0),
    outcome varchar(32) NOT NULL CHECK (outcome IN (
        'SUCCESS', 'HTTP_FAILURE', 'TIMEOUT', 'DNS_FAILURE', 'CONNECTION_FAILURE',
        'TLS_FAILURE', 'INTERNAL_ERROR', 'CANCELLED', 'DESTINATION_BLOCKED')),
    http_status integer CHECK (http_status BETWEEN 100 AND 599),
    error_message varchar(80),
    CHECK ((outcome IN ('SUCCESS', 'HTTP_FAILURE')) = (http_status IS NOT NULL)),
    CHECK (error_message IS NOT DISTINCT FROM CASE outcome
        WHEN 'SUCCESS' THEN NULL
        WHEN 'HTTP_FAILURE' THEN 'Unexpected HTTP status'
        WHEN 'TIMEOUT' THEN 'Attempt timed out'
        WHEN 'DNS_FAILURE' THEN 'DNS resolution failed'
        WHEN 'CONNECTION_FAILURE' THEN 'Connection failed'
        WHEN 'TLS_FAILURE' THEN 'TLS validation or handshake failed'
        WHEN 'INTERNAL_ERROR' THEN 'Internal execution error'
        WHEN 'CANCELLED' THEN 'Attempt cancelled'
        WHEN 'DESTINATION_BLOCKED' THEN 'Destination blocked by policy'
    END)
);

CREATE INDEX check_results_monitor_finished_idx
    ON monitor.check_results (monitor_id, finished_at, execution_id);
