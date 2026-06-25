-- V6: Create user_sessions table
CREATE TABLE user_sessions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_name     VARCHAR(255),
    device_type     VARCHAR(30),
    browser         VARCHAR(100),
    os              VARCHAR(100),
    ip_address      VARCHAR(45),
    last_used_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    is_current      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions (user_id);
CREATE INDEX idx_user_sessions_is_current ON user_sessions (is_current);
