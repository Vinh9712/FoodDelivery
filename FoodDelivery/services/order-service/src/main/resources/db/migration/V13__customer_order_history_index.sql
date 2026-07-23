CREATE INDEX IF NOT EXISTS idx_orders_customer_created_id
    ON orders (customer_id, created_at DESC, id DESC);
