-- V1: Create drivers table
CREATE TABLE drivers (
    id            UUID PRIMARY KEY,
    full_name     VARCHAR(255) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    vehicle_type  VARCHAR(20)  NOT NULL,
    license_plate VARCHAR(20)  NOT NULL,
    avg_rating    NUMERIC(3,2),
    available     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_drivers_available ON drivers (available);
