-- V10: Seed menu categories and link existing menu items
INSERT INTO menu_categories (
    id, restaurant_id, name, description, sort_order, display_order, is_available, is_active, created_at, updated_at
)
VALUES
(
    '40000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'Món Bún Nóng Hổi',
    'Các món bún bò truyền thống đặc sản',
    1, 1, TRUE, TRUE, NOW(), NOW()
),
(
    '40000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000002',
    'Phở Hà Nội',
    'Phở bò và nước dùng gia truyền',
    1, 1, TRUE, TRUE, NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

UPDATE menu_items
SET category_id = '40000000-0000-0000-0000-000000000001'
WHERE restaurant_id = '20000000-0000-0000-0000-000000000001';

UPDATE menu_items
SET category_id = '40000000-0000-0000-0000-000000000002'
WHERE restaurant_id = '20000000-0000-0000-0000-000000000002';
