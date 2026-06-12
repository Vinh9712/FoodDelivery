-- V2: Create deliveries table
CREATE TABLE deliveries (
    id         UUID PRIMARY KEY,
    order_id   UUID        NOT NULL UNIQUE,
    driver_id  UUID        REFERENCES drivers(id),
    status     VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_deliveries_order_id ON deliveries (order_id);
CREATE INDEX idx_deliveries_driver_id ON deliveries (driver_id);
CREATE INDEX idx_deliveries_status ON deliveries (status);
