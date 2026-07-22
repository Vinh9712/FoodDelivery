-- Ordered payment outbox: aggregate event sequence + canonical outbox metadata.

ALTER TABLE payments ADD COLUMN event_sequence BIGINT NOT NULL DEFAULT 0;

ALTER TABLE outbox_events ADD COLUMN event_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE outbox_events ADD COLUMN aggregate_sequence BIGINT;
ALTER TABLE outbox_events ADD COLUMN partition_key VARCHAR(100);

UPDATE outbox_events
SET aggregate_sequence = ranked.sequence,
    partition_key = ranked.aggregate_id::text
FROM (
    SELECT id,
           aggregate_id,
           ROW_NUMBER() OVER (
               PARTITION BY aggregate_type, aggregate_id
               ORDER BY occurred_at, id
           ) AS sequence
    FROM outbox_events
) ranked
WHERE outbox_events.id = ranked.id;

UPDATE payments payment_row
SET event_sequence = GREATEST(
    payment_row.event_sequence,
    COALESCE((
        SELECT MAX(event.aggregate_sequence)
        FROM outbox_events event
        WHERE event.aggregate_type = 'Payment' AND event.aggregate_id = payment_row.id
    ), 0)
);

ALTER TABLE outbox_events ALTER COLUMN aggregate_sequence SET NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN partition_key SET NOT NULL;
ALTER TABLE outbox_events ADD CONSTRAINT uq_payment_outbox_aggregate_sequence
    UNIQUE (aggregate_type, aggregate_id, aggregate_sequence);

-- Keep boolean published in sync with published_at (canonical success marker).
UPDATE outbox_events
SET published = TRUE
WHERE published_at IS NOT NULL AND published = FALSE;

UPDATE outbox_events
SET published_at = COALESCE(published_at, occurred_at)
WHERE published = TRUE AND published_at IS NULL;

CREATE INDEX idx_payment_outbox_due_sequence
    ON outbox_events (aggregate_type, aggregate_id, aggregate_sequence)
    WHERE published_at IS NULL AND dead_lettered = FALSE;
