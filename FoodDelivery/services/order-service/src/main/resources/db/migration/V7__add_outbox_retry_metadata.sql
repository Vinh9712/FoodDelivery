-- Order outbox relay: retry metadata + due-event partial index.
-- published_at already exists (V5); extend for durable Kafka relay.

ALTER TABLE outbox_events ADD COLUMN attempts INT NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMPTZ;
ALTER TABLE outbox_events ADD COLUMN last_error VARCHAR(1000);
ALTER TABLE outbox_events ADD COLUMN dead_lettered BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE outbox_events ADD COLUMN dead_lettered_at TIMESTAMPTZ;

UPDATE outbox_events
SET next_attempt_at = created_at
WHERE published_at IS NULL;

CREATE INDEX idx_order_outbox_due
    ON outbox_events (next_attempt_at, created_at)
    WHERE published_at IS NULL AND dead_lettered = FALSE;
