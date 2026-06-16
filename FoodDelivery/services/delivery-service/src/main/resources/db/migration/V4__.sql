CREATE TABLE deliveries
(
    id         UUID                        NOT NULL,
    order_id   UUID                        NOT NULL,
    driver_id  UUID,
    status     VARCHAR(30)                 NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_deliveries PRIMARY KEY (id)
);

CREATE TABLE drivers
(
    id            UUID         NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    vehicle_type  VARCHAR(20)  NOT NULL,
    license_plate VARCHAR(20)  NOT NULL,
    avg_rating    DECIMAL(3, 2),
    available     BOOLEAN      NOT NULL,
    CONSTRAINT pk_drivers PRIMARY KEY (id)
);

CREATE TABLE outbox_events
(
    id             UUID                        NOT NULL,
    aggregate_type VARCHAR(50)                 NOT NULL,
    aggregate_id   UUID                        NOT NULL,
    event_type     VARCHAR(100)                NOT NULL,
    payload        JSONB                       NOT NULL,
    occurred_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    published      BOOLEAN                     NOT NULL,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

ALTER TABLE deliveries
    ADD CONSTRAINT uc_deliveries_order UNIQUE (order_id);