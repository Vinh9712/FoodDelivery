-- V5: Create outbox_events table for transactional outbox pattern
CREATE TABLE outbox_events (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100)  NOT NULL,
    aggregate_id    UUID          NOT NULL,
    event_type      VARCHAR(100)  NOT NULL,
    payload         JSONB         NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_events_aggregate  ON outbox_events (aggregate_type, aggregate_id);
CREATE INDEX idx_outbox_events_event_type ON outbox_events (event_type);
CREATE INDEX idx_outbox_events_unpublished ON outbox_events (created_at)
    WHERE published_at IS NULL;
