ALTER TABLE ainer_identity_platform_operation_audit
    RENAME COLUMN actor_service_id TO actor_id;

ALTER TABLE ainer_identity_platform_operation_audit
    ADD COLUMN actor_type VARCHAR(24) NOT NULL DEFAULT 'SERVICE',
    ADD CONSTRAINT ck_ainer_identity_platform_audit_actor_type
        CHECK (actor_type IN ('SERVICE', 'USER', 'SYSTEM', 'ACTIVATION_GRANT'));

ALTER TABLE ainer_identity_platform_operation_audit
    ALTER COLUMN actor_type DROP DEFAULT;

CREATE TABLE ainer_identity_activation_grant (
    id UUID NOT NULL DEFAULT uuidv7(),
    provisioning_request_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    secret_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    last_attempt_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,

    CONSTRAINT pk_ainer_identity_activation_grant PRIMARY KEY (id),
    CONSTRAINT uk_ainer_identity_activation_grant_request
        UNIQUE (provisioning_request_id),
    CONSTRAINT fk_ainer_identity_activation_grant_request
        FOREIGN KEY (provisioning_request_id)
        REFERENCES ainer_identity_tenant_provisioning_request (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_ainer_identity_activation_grant_id_version
        CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_identity_activation_grant_tenant_version
        CHECK (uuid_extract_version(tenant_id) = 7),
    CONSTRAINT ck_ainer_identity_activation_grant_subject_version
        CHECK (uuid_extract_version(subject_id) = 7),
    CONSTRAINT ck_ainer_identity_activation_grant_hash
        CHECK (secret_hash ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_ainer_identity_activation_grant_status
        CHECK (status IN ('ACTIVE', 'CONSUMED', 'EXPIRED', 'LOCKED', 'CANCELLED')),
    CONSTRAINT ck_ainer_identity_activation_grant_attempts
        CHECK (
            max_attempts BETWEEN 1 AND 20
            AND attempt_count BETWEEN 0 AND max_attempts
        ),
    CONSTRAINT ck_ainer_identity_activation_grant_time
        CHECK (
            expires_at > created_at
            AND (last_attempt_at IS NULL OR last_attempt_at >= created_at)
            AND (consumed_at IS NULL OR consumed_at >= created_at)
        ),
    CONSTRAINT ck_ainer_identity_activation_grant_completion
        CHECK (
            (status = 'CONSUMED' AND consumed_at IS NOT NULL)
            OR (status <> 'CONSUMED' AND consumed_at IS NULL)
        ),
    CONSTRAINT ck_ainer_identity_activation_grant_locked
        CHECK (status <> 'LOCKED' OR attempt_count = max_attempts)
);

CREATE TABLE ainer_identity_notification_outbox (
    id UUID NOT NULL DEFAULT uuidv7(),
    provisioning_request_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    template_version SMALLINT NOT NULL,
    payload_key_version VARCHAR(32) NOT NULL,
    protected_payload BYTEA NOT NULL,
    publication_status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    lease_owner VARCHAR(128),
    lease_until TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    payload_destroyed_at TIMESTAMPTZ,
    last_error_code VARCHAR(96),
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_ainer_identity_notification_outbox PRIMARY KEY (id),
    CONSTRAINT uk_ainer_identity_notification_outbox_request_type
        UNIQUE (provisioning_request_id, notification_type),
    CONSTRAINT fk_ainer_identity_notification_outbox_request
        FOREIGN KEY (provisioning_request_id)
        REFERENCES ainer_identity_tenant_provisioning_request (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_ainer_identity_notification_outbox_id_version
        CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_identity_notification_outbox_tenant_version
        CHECK (uuid_extract_version(tenant_id) = 7),
    CONSTRAINT ck_ainer_identity_notification_outbox_type
        CHECK (notification_type IN (
            'NEW_USER_ACTIVATION',
            'EXISTING_USER_ACCEPTANCE'
        )),
    CONSTRAINT ck_ainer_identity_notification_outbox_template
        CHECK (template_version = 1),
    CONSTRAINT ck_ainer_identity_notification_outbox_key_version
        CHECK (payload_key_version ~ '^[A-Za-z0-9._-]{1,32}$'),
    CONSTRAINT ck_ainer_identity_notification_outbox_payload
        CHECK (octet_length(protected_payload) BETWEEN 32 AND 8192),
    CONSTRAINT ck_ainer_identity_notification_outbox_status
        CHECK (publication_status IN ('PENDING', 'PUBLISHED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_ainer_identity_notification_outbox_attempts
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_ainer_identity_notification_outbox_publication
        CHECK (
            (publication_status = 'PUBLISHED' AND published_at IS NOT NULL)
            OR (publication_status <> 'PUBLISHED' AND published_at IS NULL)
        ),
    CONSTRAINT ck_ainer_identity_notification_outbox_payload_lifecycle
        CHECK (
            (
                publication_status IN ('PENDING', 'FAILED')
                AND payload_key_version <> 'destroyed'
                AND payload_destroyed_at IS NULL
            )
            OR (
                publication_status IN ('PUBLISHED', 'CANCELLED')
                AND payload_key_version = 'destroyed'
                AND payload_destroyed_at IS NOT NULL
            )
        ),
    CONSTRAINT ck_ainer_identity_notification_outbox_lease
        CHECK (
            (lease_owner IS NULL AND lease_until IS NULL)
            OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL)
        ),
    CONSTRAINT ck_ainer_identity_notification_outbox_error
        CHECK (
            last_error_code IS NULL
            OR last_error_code ~ '^AINER\.[A-Z0-9_.]{1,89}$'
        )
);

CREATE INDEX idx_ainer_identity_notification_outbox_ready
    ON ainer_identity_notification_outbox
        (available_at, created_at, id)
    WHERE publication_status IN ('PENDING', 'FAILED');
