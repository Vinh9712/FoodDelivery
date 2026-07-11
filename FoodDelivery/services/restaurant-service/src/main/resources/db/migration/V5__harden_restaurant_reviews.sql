ALTER TABLE restaurant_reviews
    ALTER COLUMN rating TYPE INTEGER USING ROUND(rating)::INTEGER;

ALTER TABLE restaurant_reviews
    ADD COLUMN is_verified_purchase BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_restaurant_reviews_rating CHECK (rating BETWEEN 1 AND 5);
