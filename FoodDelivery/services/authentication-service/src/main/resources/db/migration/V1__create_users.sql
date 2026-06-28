-- V1: Create users table
CREATE TABLE users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    phone           VARCHAR(20)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(30)  NOT NULL DEFAULT 'CUSTOMER',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'RESTAURANT_OWNER', 'DRIVER', 'ADMIN'))
);

CREATE INDEX idx_users_email      ON users (email);
CREATE INDEX idx_users_phone      ON users (phone);
CREATE INDEX idx_users_role       ON users (role);
CREATE INDEX idx_users_is_active  ON users (is_active);
