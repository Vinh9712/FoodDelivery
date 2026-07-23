-- V5: Seed demo customer profiles and default address
INSERT INTO customers (id, auth_user_id, email, full_name, phone, customer_type, loyalty_points, created_at, updated_at)
VALUES
(
    '019f7567-133e-7bfa-bd16-e788321cec44',
    '019f7567-133e-7bfa-bd16-e788321cec44',
    'testuser@example.com',
    'Nguyễn Văn Customer',
    '0988555666',
    'REGULAR',
    100,
    NOW(),
    NOW()
),
(
    '10000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000002',
    'owner.demo@food.local',
    'Chủ Quán Demo',
    '0988111222',
    'REGULAR',
    0,
    NOW(),
    NOW()
)
ON CONFLICT (auth_user_id) DO NOTHING;

INSERT INTO addresses (id, customer_id, label, address_line, district, city, is_default, created_at, updated_at)
VALUES
(
    '50000000-0000-0000-0000-000000000001',
    '019f7567-133e-7bfa-bd16-e788321cec44',
    'Nhà riêng',
    '123 Lê Lợi',
    'Quận 1',
    'Hồ Chí Minh',
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;
