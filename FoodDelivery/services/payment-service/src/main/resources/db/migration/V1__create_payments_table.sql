-- ============================================================
-- Payment Service – initial schema
-- ============================================================

CREATE TABLE IF NOT EXISTS payments (
    id                     UUID PRIMARY KEY,
    order_id               UUID UNIQUE NOT NULL,
    customer_id            UUID NOT NULL,
    amount                 NUMERIC(12,2) NOT NULL,
    payment_method         VARCHAR(20) NOT NULL,
    status                 VARCHAR(20) NOT NULL,
    gateway_transaction_id VARCHAR(100),
    gateway_response       JSONB,
    paid_at                TIMESTAMPTZ,
    failed_reason          VARCHAR(255),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_status   ON payments (status);
