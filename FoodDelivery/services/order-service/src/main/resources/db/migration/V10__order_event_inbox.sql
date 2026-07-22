-- Sequenced inbox: aggregate cursor + deferred gap buffer for order-service consumers.

CREATE TABLE consumed_aggregate_sequences (
    consumer_name         VARCHAR(100)  NOT NULL,
    aggregate_type        VARCHAR(50)   NOT NULL,
    aggregate_id          UUID          NOT NULL,
    last_applied_sequence BIGINT        NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (consumer_name, aggregate_type, aggregate_id)
);

CREATE TABLE deferred_integration_events (
    id                  UUID          PRIMARY KEY,
    consumer_name       VARCHAR(100)  NOT NULL,
    event_id            UUID          NOT NULL,
    aggregate_type      VARCHAR(50)   NOT NULL,
    aggregate_id        UUID          NOT NULL,
    aggregate_sequence  BIGINT        NOT NULL,
    event_json          JSONB         NOT NULL,
    received_at         TIMESTAMPTZ   NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'WAITING_FOR_PREDECESSOR',
    attempts            INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ   NOT NULL,
    last_error          VARCHAR(1000),
    dead_lettered_at    TIMESTAMPTZ,
    CONSTRAINT uq_deferred_consumer_event
        UNIQUE (consumer_name, event_id),
    CONSTRAINT uq_deferred_consumer_aggregate_sequence
        UNIQUE (consumer_name, aggregate_type, aggregate_id, aggregate_sequence),
    CONSTRAINT chk_deferred_status
        CHECK (status IN ('WAITING_FOR_PREDECESSOR', 'DEAD_LETTER'))
);

CREATE INDEX idx_deferred_due
    ON deferred_integration_events (next_attempt_at, id)
    WHERE status = 'WAITING_FOR_PREDECESSOR';
