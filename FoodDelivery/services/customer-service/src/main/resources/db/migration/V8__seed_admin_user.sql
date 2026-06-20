-- V8: Seed initial admin user (password: Admin@123)
-- Note: requires pgcrypto extension for crypt() function
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (id, email, phone, password_hash, role, is_active, email_verified, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'admin@fooddelivery.com',
    '0900000001',
    crypt('Admin@123', gen_salt('bf', 10)),
    'ADMIN',
    TRUE,
    TRUE,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;
