ALTER TABLE notifications ADD COLUMN request_key VARCHAR(64);
ALTER TABLE notifications ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING';
ALTER TABLE notifications ADD COLUMN next_attempt_at TIMESTAMPTZ;
ALTER TABLE notifications ADD COLUMN failed_at TIMESTAMPTZ;
ALTER TABLE notifications ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE notifications ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE notifications ALTER COLUMN last_error TYPE VARCHAR(1000);

UPDATE notifications
SET request_key = REPLACE(id::text, '-', ''),
    status = CASE WHEN sent_at IS NULL THEN 'PENDING' ELSE 'SENT' END,
    next_attempt_at = CASE WHEN sent_at IS NULL THEN COALESCE(scheduled_at, created_at) ELSE NULL END,
    updated_at = COALESCE(sent_at, created_at);

ALTER TABLE notifications ALTER COLUMN request_key SET NOT NULL;

CREATE UNIQUE INDEX uq_notifications_request_key ON notifications (request_key);
CREATE INDEX idx_notifications_dispatch_due
    ON notifications (next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'RETRY_SCHEDULED');
