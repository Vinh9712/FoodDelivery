-- ============================================================
-- Payment Service – initial schema
-- ============================================================

CREATE TABLE IF NOT EXISTS payments (
    id             UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id       UUID         NOT NULL UNIQUE,
    amount         NUMERIC(12,2) NOT NULL,
    currency       VARCHAR(10)  NOT NULL DEFAULT 'VND',
    status         VARCHAR(20)  NOT NULL,
    failure_reason VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_status   ON payments (status);
