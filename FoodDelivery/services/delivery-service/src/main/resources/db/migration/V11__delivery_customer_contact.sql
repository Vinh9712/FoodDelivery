-- Snapshot contact for driver UI (avoid showing raw UUIDs).
ALTER TABLE deliveries ADD COLUMN IF NOT EXISTS customer_name VARCHAR(255);
ALTER TABLE deliveries ADD COLUMN IF NOT EXISTS customer_phone VARCHAR(30);
