-- Notification template change audit (ADR-0040 management hardening). Append-only.
-- Business database: ainer (owned by ainer-server).

CREATE TABLE ainer_notification_audit (
    id UUID NOT NULL DEFAULT uuidv7(),
    operation VARCHAR(32) NOT NULL,
    template_id UUID NOT NULL,
    actor_issuer VARCHAR(256) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_notification_audit PRIMARY KEY (id),
    CONSTRAINT ck_ainer_notification_audit_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_notification_audit_operation CHECK (operation IN (
        'TEMPLATE_CREATED', 'TEMPLATE_UPDATED', 'TEMPLATE_STATUS_CHANGED')),
    CONSTRAINT ck_ainer_notification_audit_actor_type CHECK (actor_type IN ('USER', 'SERVICE')),
    CONSTRAINT fk_ainer_notification_audit_template
        FOREIGN KEY (template_id) REFERENCES ainer_notification_template (id) ON DELETE RESTRICT
);

CREATE INDEX idx_ainer_notification_audit_template
    ON ainer_notification_audit (template_id, occurred_at DESC);
