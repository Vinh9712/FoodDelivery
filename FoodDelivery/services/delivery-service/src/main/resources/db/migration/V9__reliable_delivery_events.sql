-- Reliable delivery events: ordered outbox relay index + published_at as canonical success marker.
-- Keep boolean published temporarily; backfill both for consistency.

UPDATE outbox_events
SET published = TRUE
WHERE published_at IS NOT NULL AND published = FALSE;

UPDATE outbox_events
SET published_at = COALESCE(published_at, occurred_at)
WHERE published = TRUE AND published_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_delivery_outbox_due_sequence
    ON outbox_events (aggregate_type, aggregate_id, aggregate_sequence)
    WHERE published_at IS NULL AND dead_lettered = FALSE;
