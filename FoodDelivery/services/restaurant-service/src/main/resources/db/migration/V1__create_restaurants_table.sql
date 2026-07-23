CREATE TABLE restaurants (
    id                             UUID PRIMARY KEY,
    owner_id                       UUID NOT NULL,
    name                           VARCHAR(255) NOT NULL,
    description                    VARCHAR(500),
    phone                          VARCHAR(20) NOT NULL,
    address_line                   VARCHAR(255) NOT NULL,
    district                       VARCHAR(100) NOT NULL,
    city                           VARCHAR(100) NOT NULL,
    status                         VARCHAR(30) NOT NULL,
    open_time                      TIME NOT NULL,
    close_time                     TIME NOT NULL,
    avg_rating                     NUMERIC(3,2) NOT NULL DEFAULT 0.00,
    total_reviews                  INT NOT NULL DEFAULT 0,
    min_order_amount               NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    estimated_delivery_time_min    INT NOT NULL,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
