-- V7: Seed demo users for RESTAURANT_OWNER, DRIVER, and CUSTOMER roles
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (id, email, phone, password_hash, role, is_active, email_verified, created_at, updated_at)
VALUES 
(
    '10000000-0000-0000-0000-000000000002',
    'owner.demo@food.local',
    '0988111222',
    crypt('Demo@12345', gen_salt('bf', 10)),
    'RESTAURANT_OWNER',
    TRUE,
    TRUE,
    NOW(),
    NOW()
),
(
    '019f7567-133e-7bfa-bd16-e788321cec33',
    'driver.demo@food.local',
    '0988333444',
    crypt('Demo@12345', gen_salt('bf', 10)),
    'DRIVER',
    TRUE,
    TRUE,
    NOW(),
    NOW()
),
(
    '019f7567-133e-7bfa-bd16-e788321cec44',
    'testuser@example.com',
    '0988555666',
    crypt('Password123!', gen_salt('bf', 10)),
    'CUSTOMER',
    TRUE,
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (email) DO NOTHING;
