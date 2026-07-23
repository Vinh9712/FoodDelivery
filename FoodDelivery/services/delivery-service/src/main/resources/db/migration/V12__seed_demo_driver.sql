-- V12: Seed the delivery profile used by driver.demo@food.local.
INSERT INTO drivers (
    id,
    user_id,
    full_name,
    phone,
    vehicle_type,
    license_plate,
    avg_rating,
    available,
    status,
    is_online,
    total_reviews
)
SELECT
    '30000000-0000-0000-0000-000000000001',
    '019f7567-133e-7bfa-bd16-e788321cec33',
    'Demo Driver',
    '0988333444',
    'MOTORBIKE',
    '59A1-123.45',
    5.00,
    TRUE,
    'ACTIVE',
    FALSE,
    0
WHERE NOT EXISTS (
    SELECT 1
    FROM drivers
    WHERE user_id = '019f7567-133e-7bfa-bd16-e788321cec33'
)
ON CONFLICT (id) DO NOTHING;
