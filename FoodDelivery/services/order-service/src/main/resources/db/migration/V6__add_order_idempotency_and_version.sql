ALTER TABLE orders ADD COLUMN client_request_id VARCHAR(100);
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uq_orders_customer_client_request
    ON orders (customer_id, client_request_id)
    WHERE client_request_id IS NOT NULL;
