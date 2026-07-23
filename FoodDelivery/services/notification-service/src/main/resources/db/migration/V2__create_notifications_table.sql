CREATE TABLE notifications (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL,
    type          VARCHAR(50) NOT NULL,
    channel       VARCHAR(20) NOT NULL,
    title         VARCHAR(255) NOT NULL,
    body          TEXT NOT NULL,
    entity_type   VARCHAR(50),
    entity_id     UUID,
    data          JSONB,
    is_read       BOOLEAN NOT NULL DEFAULT FALSE,
    read_at       TIMESTAMPTZ,
    scheduled_at  TIMESTAMPTZ,
    sent_at       TIMESTAMPTZ,
    retry_count   INT NOT NULL DEFAULT 0,
    last_error    VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
