-- Delivery scheduling reconciliation metadata for READY_FOR_PICKUP recovery.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_id UUID;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_schedule_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS next_delivery_schedule_attempt_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS last_delivery_schedule_error VARCHAR(1000);

-- Due rows: READY_FOR_PICKUP with null next attempt (legacy / first try) or next attempt <= now.
CREATE INDEX IF NOT EXISTS idx_orders_delivery_reconcile
    ON orders (next_delivery_schedule_attempt_at, id)
    WHERE status = 'READY_FOR_PICKUP';
