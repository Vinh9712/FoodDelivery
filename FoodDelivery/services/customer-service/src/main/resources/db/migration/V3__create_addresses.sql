-- V3: Create addresses table
CREATE TABLE addresses (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID          NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    label           VARCHAR(50),
    address_line    TEXT          NOT NULL,
    district        VARCHAR(100),
    city            VARCHAR(100)  NOT NULL,
    latitude        DECIMAL(10, 8),
    longitude       DECIMAL(11, 8),
    is_default      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    is_deleted      BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_addresses_latitude  CHECK (latitude  IS NULL OR (latitude  BETWEEN -90  AND 90)),
    CONSTRAINT chk_addresses_longitude CHECK (longitude IS NULL OR (longitude BETWEEN -180 AND 180))
);

CREATE INDEX idx_addresses_customer_id ON addresses (customer_id);
CREATE INDEX idx_addresses_city        ON addresses (city);

-- Only one default address per customer (among non-deleted rows)
CREATE UNIQUE INDEX uk_addresses_customer_default
    ON addresses (customer_id)
    WHERE is_default = TRUE AND is_deleted = FALSE;
