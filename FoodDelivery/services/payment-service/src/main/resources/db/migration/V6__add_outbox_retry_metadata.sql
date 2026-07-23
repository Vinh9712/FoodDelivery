ALTER TABLE outbox_events ADD COLUMN published_at TIMESTAMPTZ;
ALTER TABLE outbox_events ADD COLUMN attempts INT NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMPTZ;
ALTER TABLE outbox_events ADD COLUMN last_error VARCHAR(1000);
ALTER TABLE outbox_events ADD COLUMN dead_lettered BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE outbox_events ADD COLUMN dead_lettered_at TIMESTAMPTZ;

UPDATE outbox_events
SET next_attempt_at = occurred_at
WHERE published = FALSE;

UPDATE outbox_events
SET published_at = occurred_at
WHERE published = TRUE AND published_at IS NULL;

CREATE INDEX idx_payment_outbox_due
    ON outbox_events (next_attempt_at, occurred_at)
    WHERE published = FALSE AND dead_lettered = FALSE;
