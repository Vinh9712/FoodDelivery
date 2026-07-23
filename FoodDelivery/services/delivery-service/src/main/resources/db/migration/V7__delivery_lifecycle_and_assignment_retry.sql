-- Assignment retry metadata + customer ownership for authorization.

ALTER TABLE deliveries ADD COLUMN customer_id UUID;
ALTER TABLE deliveries ADD COLUMN assignment_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE deliveries ADD COLUMN next_assignment_at TIMESTAMPTZ;
ALTER TABLE deliveries ADD COLUMN last_assignment_error VARCHAR(1000);
ALTER TABLE deliveries ADD COLUMN failure_reason VARCHAR(1000);

UPDATE deliveries
SET next_assignment_at = created_at
WHERE status = 'FINDING_DRIVER' AND next_assignment_at IS NULL;

CREATE INDEX idx_deliveries_assignment_due
    ON deliveries (next_assignment_at, created_at)
    WHERE status = 'FINDING_DRIVER';

CREATE INDEX idx_deliveries_customer_id ON deliveries (customer_id);
CREATE INDEX idx_drivers_user_id ON drivers (user_id);
