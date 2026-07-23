-- V9: Seed demo restaurants and menu items for owner.demo@food.local and customer search
INSERT INTO restaurants (
    id, owner_id, name, description, phone, address_line, district, city,
    status, open_time, close_time, avg_rating, total_reviews, min_order_amount,
    estimated_delivery_time_min, is_accepting_orders, created_at, updated_at
)
VALUES
(
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    'Bun Bo Hue Ba Tuyet',
    'Đặc sản bún bò Huế truyền thống, nước dùng đậm đà thơm mùi sả mắm ruốc.',
    '0901234567',
    '123 Nguyễn Trãi',
    'Quận 1',
    'Hồ Chí Minh',
    'ACTIVE',
    '00:00:00',
    '23:59:59',
    4.8,
    125,
    30000.00,
    25,
    TRUE,
    NOW(),
    NOW()
),
(
    '20000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000002',
    'Demo Pho Kitchen',
    'Phở gia truyền Hà Nội, bò tái lăn, nạm gầu thơm ngon đặc biệt.',
    '0902345678',
    '45 Lý Tự Trọng',
    'Quận 1',
    'Hồ Chí Minh',
    'ACTIVE',
    '00:00:00',
    '23:59:59',
    4.6,
    89,
    40000.00,
    30,
    TRUE,
    NOW(),
    NOW()
),
(
    '20000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000002',
    'Com Tam Sai Gon 99',
    'Cơm tấm sườn nướng mật ong, chả trứng, bì giòn sần sật.',
    '0903456789',
    '78 Lê Văn Sỹ',
    'Quận 3',
    'Hồ Chí Minh',
    'ACTIVE',
    '00:00:00',
    '23:59:59',
    4.7,
    210,
    35000.00,
    20,
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO menu_items (
    id, restaurant_id, name, description, price, is_available, prep_time_minutes, preparation_time_min, display_order, created_at, updated_at
)
VALUES
(
    '30000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'Bún Bò Huế Đặc Biệt',
    'Tái, nạm, gầu, giò heo, chả cua, chả bò',
    65000.00,
    TRUE,
    15,
    15,
    1,
    NOW(),
    NOW()
),
(
    '30000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000001',
    'Bún Bò Huế Nạm Chả',
    'Nạm bò mềm và chả cua đặc sản',
    50000.00,
    TRUE,
    15,
    15,
    2,
    NOW(),
    NOW()
),
(
    '30000000-0000-0000-0000-000000000003',
    '20000000-0000-0000-0000-000000000002',
    'Phở Bò Tái Nạm',
    'Bánh phở tươi, bò tái nạm ngọt nước',
    55000.00,
    TRUE,
    15,
    15,
    1,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;
