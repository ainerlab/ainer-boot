CREATE TABLE ainer_identity_tenant_provisioning_request (
    id UUID NOT NULL DEFAULT uuidv7(),
    tenant_id UUID NOT NULL UNIQUE,
    tenant_code VARCHAR(64) NOT NULL,
    tenant_name VARCHAR(80) NOT NULL,
    owner_subject_id UUID NOT NULL,
    owner_username VARCHAR(100) NOT NULL,
    owner_display_name VARCHAR(80) NOT NULL,
    owner_user_exists BOOLEAN NOT NULL,
    status VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    requested_by_service_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    change_reference VARCHAR(128) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_ainer_identity_tenant_provisioning_request PRIMARY KEY (id),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_request_id_version
        CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_tenant_id_version
        CHECK (uuid_extract_version(tenant_id) = 7),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_new_subject_version
        CHECK (owner_user_exists OR uuid_extract_version(owner_subject_id) = 7),
    CONSTRAINT uk_ainer_identity_tenant_provisioning_idempotency
        UNIQUE (requested_by_service_id, idempotency_key),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_tenant_code
        CHECK (tenant_code ~ '^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$'),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_tenant_name
        CHECK (char_length(btrim(tenant_name)) BETWEEN 2 AND 80),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_owner_username
        CHECK (owner_username ~ '^[a-z0-9][a-z0-9._@-]{2,99}$'),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_owner_display_name
        CHECK (char_length(btrim(owner_display_name)) BETWEEN 1 AND 80),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_status
        CHECK (status IN ('REQUESTED', 'ACTIVATED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_idempotency_key
        CHECK (idempotency_key ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_fingerprint
        CHECK (request_fingerprint ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_actor
        CHECK (requested_by_service_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_request_id
        CHECK (request_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_change_reference
        CHECK (change_reference ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_time
        CHECK (expires_at > requested_at),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_completion
        CHECK (
            (status = 'REQUESTED' AND completed_at IS NULL)
            OR (status <> 'REQUESTED' AND completed_at IS NOT NULL)
        ),
    CONSTRAINT ck_ainer_identity_tenant_provisioning_version
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX ux_ainer_identity_tenant_provisioning_open_code
    ON ainer_identity_tenant_provisioning_request (tenant_code)
    WHERE status = 'REQUESTED';

CREATE UNIQUE INDEX ux_ainer_identity_tenant_provisioning_open_new_user
    ON ainer_identity_tenant_provisioning_request (owner_username)
    WHERE status = 'REQUESTED' AND owner_user_exists = false;

CREATE INDEX idx_ainer_identity_tenant_provisioning_status_time
    ON ainer_identity_tenant_provisioning_request (status, expires_at, requested_at, id);

CREATE TABLE ainer_identity_platform_operation_audit (
    id UUID NOT NULL DEFAULT uuidv7(),
    operation_id UUID NOT NULL
        REFERENCES ainer_identity_tenant_provisioning_request (id) ON DELETE RESTRICT,
    tenant_id UUID NOT NULL,
    target_subject_id UUID NOT NULL,
    operation_type VARCHAR(48) NOT NULL,
    phase VARCHAR(24) NOT NULL,
    actor_service_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    change_reference VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_identity_platform_operation_audit PRIMARY KEY (id),
    CONSTRAINT ck_ainer_identity_platform_audit_id_version
        CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_identity_platform_audit_operation
        CHECK (operation_type = 'TENANT_PROVISIONING'),
    CONSTRAINT ck_ainer_identity_platform_audit_phase
        CHECK (phase IN ('REQUESTED', 'ACTIVATED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_ainer_identity_platform_audit_actor
        CHECK (actor_service_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_identity_platform_audit_request_id
        CHECK (request_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_identity_platform_audit_change_reference
        CHECK (change_reference ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT uk_ainer_identity_platform_audit_phase
        UNIQUE (operation_id, phase)
);

CREATE INDEX idx_ainer_identity_platform_audit_tenant_time
    ON ainer_identity_platform_operation_audit (tenant_id, occurred_at DESC, id DESC);
