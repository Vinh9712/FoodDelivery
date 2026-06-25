-- V4: Add fields to orders table, create order_items and order_status_history tables
ALTER TABLE orders ADD COLUMN subtotal NUMERIC(12,2) NOT NULL DEFAULT 0.00;
ALTER TABLE orders ADD COLUMN delivery_fee NUMERIC(12,2) NOT NULL DEFAULT 0.00;
ALTER TABLE orders ADD COLUMN discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00;
ALTER TABLE orders ADD COLUMN delivery_address_snapshot JSONB;
ALTER TABLE orders ADD COLUMN payment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING';
ALTER TABLE orders ADD COLUMN promotion_code VARCHAR(50);
ALTER TABLE orders ADD COLUMN note TEXT;

CREATE TABLE order_items (
    id            UUID PRIMARY KEY,
    order_id      UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id  UUID NOT NULL,
    name          VARCHAR(255) NOT NULL,
    price         NUMERIC(12,2) NOT NULL,
    quantity      INT NOT NULL
);

CREATE TABLE order_status_history (
    id          UUID PRIMARY KEY,
    order_id    UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status   VARCHAR(30) NOT NULL,
    note        VARCHAR(255),
    changed_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
