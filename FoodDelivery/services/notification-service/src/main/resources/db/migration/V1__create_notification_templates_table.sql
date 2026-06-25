CREATE TABLE notification_templates (
    id             UUID PRIMARY KEY,
    type           VARCHAR(50) UNIQUE NOT NULL,
    channel        VARCHAR(20) NOT NULL,
    title_template VARCHAR(255) NOT NULL,
    body_template  TEXT NOT NULL,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE
);
