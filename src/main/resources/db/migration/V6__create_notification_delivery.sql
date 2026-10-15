ALTER TABLE monitor.notification_intents DROP CONSTRAINT notification_intents_status_check;
ALTER TABLE monitor.notification_intents ALTER COLUMN status TYPE varchar(9);
ALTER TABLE monitor.notification_intents
    ADD COLUMN attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 5),
    ADD COLUMN next_attempt_at timestamptz DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN completed_at timestamptz,
    ADD CONSTRAINT notification_delivery_status CHECK (status IN ('PENDING','SENDING','ACCEPTED','EXHAUSTED')),
    ADD CONSTRAINT notification_delivery_schedule CHECK (
        (status = 'PENDING' AND next_attempt_at IS NOT NULL AND completed_at IS NULL AND attempt_count < 5)
        OR (status = 'SENDING' AND next_attempt_at IS NULL AND completed_at IS NULL AND attempt_count > 0)
        OR (status IN ('ACCEPTED','EXHAUSTED') AND next_attempt_at IS NULL
            AND completed_at IS NOT NULL AND attempt_count > 0));

CREATE INDEX notification_pending_idx ON monitor.notification_intents (next_attempt_at, id)
    WHERE status = 'PENDING';

CREATE TABLE monitor.notification_attempts (
    notification_id uuid NOT NULL REFERENCES monitor.notification_intents(id),
    attempt_number integer NOT NULL CHECK (attempt_number BETWEEN 1 AND 5),
    started_at timestamptz NOT NULL,
    finished_at timestamptz,
    outcome varchar(9) NOT NULL CHECK (outcome IN ('SENDING','ACCEPTED','FAILED','UNCERTAIN')),
    error_code varchar(24) CHECK (error_code IN ('SMTP_FAILURE','SMTP_UNCERTAIN','RESTART_UNCERTAIN')),
    PRIMARY KEY (notification_id, attempt_number),
    CHECK ((outcome = 'SENDING' AND finished_at IS NULL AND error_code IS NULL)
        OR (outcome = 'ACCEPTED' AND finished_at IS NOT NULL AND error_code IS NULL)
        OR (outcome IN ('FAILED','UNCERTAIN') AND finished_at IS NOT NULL AND error_code IS NOT NULL))
);
CREATE UNIQUE INDEX notification_one_open_attempt_idx ON monitor.notification_attempts (notification_id)
    WHERE outcome = 'SENDING';
