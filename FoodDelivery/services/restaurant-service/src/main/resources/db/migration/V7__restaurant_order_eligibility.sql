ALTER TABLE restaurants ALTER COLUMN is_accepting_orders SET DEFAULT FALSE;

CREATE INDEX idx_restaurants_order_eligibility
    ON restaurants (status, is_accepting_orders, open_time, close_time);
