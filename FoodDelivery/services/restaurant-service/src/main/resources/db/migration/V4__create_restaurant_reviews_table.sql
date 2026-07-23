CREATE TABLE restaurant_reviews (
    id             UUID PRIMARY KEY,
    restaurant_id  UUID NOT NULL,
    customer_id    UUID NOT NULL,
    order_id       UUID UNIQUE NOT NULL,
    rating         NUMERIC(3,2) NOT NULL,
    comment        VARCHAR(500),
    reply_text     VARCHAR(500),
    replied_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
