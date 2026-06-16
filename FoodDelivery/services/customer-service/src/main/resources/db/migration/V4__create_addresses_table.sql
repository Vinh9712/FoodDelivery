CREATE TABLE addresses (
    id            UUID PRIMARY KEY,
    customer_id   UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    label         VARCHAR(50) NOT NULL,
    address_line  VARCHAR(255) NOT NULL,
    district      VARCHAR(100) NOT NULL,
    city          VARCHAR(100) NOT NULL,
    latitude      NUMERIC(9,6),
    longitude     NUMERIC(9,6),
    is_default    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
