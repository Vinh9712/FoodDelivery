-- V7: Add session_id column to refresh_tokens (nullable, backward compatible)
ALTER TABLE refresh_tokens
    ADD COLUMN session_id UUID REFERENCES user_sessions (id) ON DELETE SET NULL;

CREATE INDEX idx_refresh_tokens_session_id ON refresh_tokens (session_id);
