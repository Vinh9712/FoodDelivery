CREATE TABLE menu_items (
    id                 UUID PRIMARY KEY,
    category_id        UUID NOT NULL REFERENCES menu_categories(id) ON DELETE CASCADE,
    name               VARCHAR(100) NOT NULL,
    description        VARCHAR(255),
    price              NUMERIC(12,2) NOT NULL,
    is_available       BOOLEAN NOT NULL DEFAULT TRUE,
    image_url          VARCHAR(512),
    prep_time_minutes  INT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
