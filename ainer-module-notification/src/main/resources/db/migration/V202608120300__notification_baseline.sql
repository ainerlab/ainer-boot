-- Ainer Notification baseline (ADR-0038 P3 commercial-grade enterprise base).
-- Multi-channel notification center: templates with JSONB variables, async delivery with retry.
-- Leverages PostgreSQL 18 JSONB for template variables and notification payload.

-- 1. Notification template — reusable message template with JSONB variables schema.
CREATE TABLE ainer_notification_template (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    code VARCHAR(128) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    title_template TEXT NOT NULL,
    body_template TEXT NOT NULL,
    variables_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_notification_template_channel
        CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'WEBHOOK')),
    CONSTRAINT ck_ainer_notification_template_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_ainer_notification_template_values
        CHECK (btrim(code) <> '' AND btrim(title_template) <> '' AND btrim(body_template) <> '')
);

CREATE UNIQUE INDEX ux_ainer_notification_template_code_active
    ON ainer_notification_template (code)
    WHERE status = 'ACTIVE';

-- 2. Notification record — append-only delivery log with retry tracking.
CREATE TABLE ainer_notification_record (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    template_code VARCHAR(128),
    channel VARCHAR(16) NOT NULL,
    recipient VARCHAR(512) NOT NULL,
    title TEXT,
    body TEXT,
    payload JSONB,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMPTZ,
    error_message TEXT,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_notification_record_channel
        CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'WEBHOOK')),
    CONSTRAINT ck_ainer_notification_record_status
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_ainer_notification_record_values
        CHECK (btrim(recipient) <> '')
);

CREATE INDEX idx_ainer_notification_record_status_retry
    ON ainer_notification_record (status, next_retry_at)
    WHERE status IN ('PENDING', 'SENDING');

CREATE INDEX idx_ainer_notification_record_created
    ON ainer_notification_record (created_at DESC, id DESC);
