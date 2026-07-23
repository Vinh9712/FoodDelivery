ALTER TABLE restaurants
    ALTER COLUMN description TYPE TEXT,
    ALTER COLUMN address_line TYPE TEXT,
    ADD COLUMN logo_url VARCHAR(255),
    ADD COLUMN banner_url VARCHAR(255),
    ADD COLUMN is_accepting_orders BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN created_by UUID,
    ADD COLUMN updated_by UUID;

ALTER TABLE menu_categories
    ALTER COLUMN name TYPE VARCHAR(256),
    ALTER COLUMN description TYPE TEXT,
    ADD COLUMN display_order INTEGER,
    ADD COLUMN is_active BOOLEAN,
    ADD COLUMN created_by UUID,
    ADD COLUMN updated_by UUID;

UPDATE menu_categories
SET display_order = sort_order,
    is_active = is_available;

ALTER TABLE menu_categories
    ALTER COLUMN display_order SET NOT NULL,
    ALTER COLUMN is_active SET DEFAULT TRUE;

ALTER TABLE menu_items
    ALTER COLUMN name TYPE VARCHAR(255),
    ALTER COLUMN description TYPE TEXT,
    ADD COLUMN restaurant_id UUID,
    ADD COLUMN discount_price NUMERIC(12,2),
    ADD COLUMN is_vegetarian BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN is_spicy BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN preparation_time_min INTEGER,
    ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN created_by UUID,
    ADD COLUMN updated_by UUID;

UPDATE menu_items item
SET restaurant_id = category.restaurant_id,
    preparation_time_min = item.prep_time_minutes
FROM menu_categories category
WHERE category.id = item.category_id;

ALTER TABLE menu_items
    ALTER COLUMN restaurant_id SET NOT NULL,
    ALTER COLUMN preparation_time_min SET DEFAULT 15,
    ADD CONSTRAINT fk_menu_items_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;

ALTER TABLE menu_items
    ALTER COLUMN category_id DROP NOT NULL;

ALTER TABLE restaurant_reviews
    ALTER COLUMN comment TYPE TEXT,
    ADD CONSTRAINT fk_restaurant_reviews_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE;
