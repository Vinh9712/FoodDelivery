-- Schedule contract after READY_FOR_PICKUP: restaurant, hash, idempotency, sequences.

ALTER TABLE deliveries ADD COLUMN restaurant_id UUID;
ALTER TABLE deliveries ADD COLUMN schedule_request_hash VARCHAR(64);
ALTER TABLE deliveries ADD COLUMN schedule_idempotency_key VARCHAR(200);
ALTER TABLE deliveries ADD COLUMN event_sequence BIGINT NOT NULL DEFAULT 0;

ALTER TABLE outbox_events ADD COLUMN event_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE outbox_events ADD COLUMN aggregate_sequence BIGINT;
ALTER TABLE outbox_events ADD COLUMN partition_key VARCHAR(100);

WITH ranked AS (
    SELECT id, aggregate_id,
           ROW_NUMBER() OVER (PARTITION BY aggregate_type, aggregate_id ORDER BY occurred_at, id) AS sequence
    FROM outbox_events
)
UPDATE outbox_events event
SET aggregate_sequence = ranked.sequence,
    partition_key = ranked.aggregate_id::text
FROM ranked
WHERE event.id = ranked.id;

UPDATE deliveries delivery_row
SET event_sequence = GREATEST(
    delivery_row.event_sequence,
    COALESCE((
        SELECT MAX(event.aggregate_sequence)
        FROM outbox_events event
        WHERE event.aggregate_type = 'Delivery' AND event.aggregate_id = delivery_row.id
    ), 0)
);

ALTER TABLE outbox_events ALTER COLUMN aggregate_sequence SET NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN partition_key SET NOT NULL;
ALTER TABLE outbox_events ADD CONSTRAINT uq_delivery_outbox_aggregate_sequence
    UNIQUE (aggregate_type, aggregate_id, aggregate_sequence);

CREATE UNIQUE INDEX uq_deliveries_schedule_idempotency_key
    ON deliveries (schedule_idempotency_key)
    WHERE schedule_idempotency_key IS NOT NULL;

CREATE INDEX idx_deliveries_restaurant_id ON deliveries (restaurant_id);
