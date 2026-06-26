CREATE TABLE refunds (
    id                 UUID PRIMARY KEY,
    payment_id         UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    amount             NUMERIC(12,2) NOT NULL,
    reason             VARCHAR(255),
    status             VARCHAR(20) NOT NULL,
    gateway_refund_id  VARCHAR(100),
    refunded_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
