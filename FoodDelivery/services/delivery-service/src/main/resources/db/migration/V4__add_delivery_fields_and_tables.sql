-- V4: Add columns to drivers and deliveries, create tracking, reviews, and processed_events tables
ALTER TABLE drivers ADD COLUMN user_id UUID;
ALTER TABLE drivers ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE drivers ADD COLUMN is_online BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE drivers ADD COLUMN current_latitude NUMERIC(9,6);
ALTER TABLE drivers ADD COLUMN current_longitude NUMERIC(9,6);
ALTER TABLE drivers ADD COLUMN location_updated_at TIMESTAMPTZ;
ALTER TABLE drivers ADD COLUMN total_reviews INT NOT NULL DEFAULT 0;

ALTER TABLE deliveries ADD COLUMN pickup_address VARCHAR(255);
ALTER TABLE deliveries ADD COLUMN pickup_latitude NUMERIC(9,6);
ALTER TABLE deliveries ADD COLUMN pickup_longitude NUMERIC(9,6);
ALTER TABLE deliveries ADD COLUMN dropoff_address VARCHAR(255);
ALTER TABLE deliveries ADD COLUMN dropoff_latitude NUMERIC(9,6);
ALTER TABLE deliveries ADD COLUMN dropoff_longitude NUMERIC(9,6);
ALTER TABLE deliveries ADD COLUMN estimated_arrival_at TIMESTAMPTZ;
ALTER TABLE deliveries ADD COLUMN driver_assigned_at TIMESTAMPTZ;
ALTER TABLE deliveries ADD COLUMN picked_up_at TIMESTAMPTZ;
ALTER TABLE deliveries ADD COLUMN delivered_at TIMESTAMPTZ;
ALTER TABLE deliveries ADD COLUMN distance_km NUMERIC(5,2);

CREATE TABLE delivery_tracking (
    id               UUID PRIMARY KEY,
    delivery_id      UUID NOT NULL REFERENCES deliveries(id) ON DELETE CASCADE,
    latitude         NUMERIC(9,6) NOT NULL,
    longitude        NUMERIC(9,6) NOT NULL,
    status_snapshot  VARCHAR(30) NOT NULL,
    recorded_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE driver_reviews (
    id           UUID PRIMARY KEY,
    driver_id    UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    customer_id  UUID NOT NULL,
    order_id     UUID UNIQUE NOT NULL,
    rating       NUMERIC(3,2) NOT NULL,
    comment      VARCHAR(500),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE processed_events (
    event_id     UUID NOT NULL,
    consumer     VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer)
);
