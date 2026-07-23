-- Durable refund retry metadata for CANCELLATION_PENDING compensation.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS refund_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS next_refund_attempt_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS last_refund_error VARCHAR(1000);

CREATE INDEX IF NOT EXISTS idx_orders_refund_reconcile
    ON orders (next_refund_attempt_at, id)
    WHERE status = 'CANCELLATION_PENDING' AND refund_status = 'PENDING';
