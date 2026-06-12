-- V1: Create orders table
CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    customer_id     UUID         NOT NULL,
    restaurant_id   UUID         NOT NULL,
    status          VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    total_amount    NUMERIC(12,2) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_restaurant_id ON orders (restaurant_id);
CREATE INDEX idx_orders_status ON orders (status);
