ALTER TABLE orders ADD COLUMN paid_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN restaurant_response_deadline TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN pickup_address_snapshot JSONB;
ALTER TABLE orders ADD COLUMN refund_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED';
ALTER TABLE orders ADD COLUMN cancellation_code VARCHAR(50);
ALTER TABLE orders ADD COLUMN cancellation_reason VARCHAR(500);
ALTER TABLE orders ADD COLUMN event_sequence BIGINT NOT NULL DEFAULT 0;

ALTER TABLE outbox_events ADD COLUMN event_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE outbox_events ADD COLUMN aggregate_sequence BIGINT;
ALTER TABLE outbox_events ADD COLUMN partition_key VARCHAR(100);

WITH ranked AS (
    SELECT id, aggregate_id,
           ROW_NUMBER() OVER (PARTITION BY aggregate_type, aggregate_id ORDER BY created_at, id) AS sequence
    FROM outbox_events
)
UPDATE outbox_events event
SET aggregate_sequence = ranked.sequence,
    partition_key = ranked.aggregate_id::text
FROM ranked
WHERE event.id = ranked.id;

UPDATE orders order_row
SET event_sequence = GREATEST(
    order_row.event_sequence,
    COALESCE((
        SELECT MAX(event.aggregate_sequence)
        FROM outbox_events event
        WHERE event.aggregate_type = 'Order' AND event.aggregate_id = order_row.id
    ), 0)
);

ALTER TABLE outbox_events ALTER COLUMN aggregate_sequence SET NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN partition_key SET NOT NULL;
ALTER TABLE outbox_events ADD CONSTRAINT uq_order_outbox_aggregate_sequence
    UNIQUE (aggregate_type, aggregate_id, aggregate_sequence);

CREATE INDEX idx_orders_restaurant_status_created
    ON orders (restaurant_id, status, created_at DESC);
CREATE INDEX idx_orders_restaurant_deadline
    ON orders (status, restaurant_response_deadline)
    WHERE status = 'PAID';
