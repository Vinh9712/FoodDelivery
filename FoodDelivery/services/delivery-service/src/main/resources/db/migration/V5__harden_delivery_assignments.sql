-- Persist assignment concurrency metadata and prevent one driver serving two active deliveries.
ALTER TABLE deliveries ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE deliveries ALTER COLUMN dropoff_address TYPE TEXT;

CREATE INDEX idx_drivers_assignment_candidates
    ON drivers (available, status, id);

CREATE UNIQUE INDEX uq_deliveries_active_driver
    ON deliveries (driver_id)
    WHERE driver_id IS NOT NULL
      AND status IN ('DRIVER_ASSIGNED', 'PICKED_UP', 'DELIVERING');
