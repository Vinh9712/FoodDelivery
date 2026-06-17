-- V2: Create customers table
CREATE TABLE customers (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL UNIQUE REFERENCES users (id),
    full_name       VARCHAR(150) NOT NULL,
    phone           VARCHAR(20),
    avatar_url      VARCHAR(500),
    customer_type   VARCHAR(30)  NOT NULL DEFAULT 'REGULAR',
    loyalty_points  INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_customers_type CHECK (customer_type IN ('REGULAR', 'SILVER', 'GOLD', 'PLATINUM')),
    CONSTRAINT chk_customers_loyalty_points CHECK (loyalty_points >= 0)
);

CREATE INDEX idx_customers_user_id       ON customers (user_id);
CREATE INDEX idx_customers_customer_type ON customers (customer_type);
