-- V3: Add assigned_driver_snapshot JSONB column to orders
ALTER TABLE orders
    ADD COLUMN assigned_driver_snapshot JSONB NULL;

COMMENT ON COLUMN orders.assigned_driver_snapshot IS
    'Denormalized snapshot of the driver assigned to deliver this order. '
    'Populated asynchronously via the driver.assigned Kafka event from Delivery Service. '
    'NULL until a driver has been assigned.';

-- Index for querying orders by assigned driver (admin/support tooling)
CREATE INDEX idx_orders_assigned_driver_id
    ON orders ((assigned_driver_snapshot ->> 'driverId'));
