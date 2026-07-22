-- Reliable fulfillment: ordered outbox relay index (V8 owns sequence columns/constraints).
CREATE INDEX IF NOT EXISTS idx_order_outbox_due_sequence
    ON outbox_events (aggregate_type, aggregate_id, aggregate_sequence)
    WHERE published_at IS NULL AND dead_lettered = FALSE;
