CREATE TABLE idempotency_keys (
    id               UUID PRIMARY KEY,
    idempotency_key  VARCHAR(255) UNIQUE NOT NULL,
    payment_id       UUID REFERENCES payments(id) ON DELETE SET NULL,
    request_hash     VARCHAR(255) NOT NULL,
    response         JSONB,
    expires_at       TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
