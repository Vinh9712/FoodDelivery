ALTER TABLE customers RENAME COLUMN user_id TO auth_user_id;

ALTER INDEX idx_customers_user_id RENAME TO idx_customers_auth_user_id;
