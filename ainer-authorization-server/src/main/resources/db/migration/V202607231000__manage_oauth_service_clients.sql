CREATE TABLE ainer_oauth_service_client (
    registered_client_id VARCHAR(100) PRIMARY KEY
        REFERENCES oauth2_registered_client (id),
    client_id VARCHAR(100) NOT NULL UNIQUE,
    tenant_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    replaces_client_id VARCHAR(100),
    created_by_service_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    retired_by_service_id VARCHAR(128),
    retired_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_ainer_oauth_service_client_status
        CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT ck_ainer_oauth_service_client_retirement
        CHECK (
            (status = 'ACTIVE' AND retired_by_service_id IS NULL AND retired_at IS NULL)
            OR
            (status = 'RETIRED' AND retired_by_service_id IS NOT NULL AND retired_at IS NOT NULL)
        ),
    CONSTRAINT fk_ainer_oauth_service_client_replaces
        FOREIGN KEY (replaces_client_id)
        REFERENCES ainer_oauth_service_client (client_id)
);

CREATE INDEX idx_ainer_oauth_service_client_tenant_status
    ON ainer_oauth_service_client (tenant_id, status, created_at DESC);

CREATE TABLE ainer_oauth_service_client_audit (
    id UUID PRIMARY KEY,
    operation VARCHAR(32) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    related_client_id VARCHAR(100),
    tenant_id UUID NOT NULL,
    actor_service_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    change_reference VARCHAR(200) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_oauth_service_client_audit_operation
        CHECK (operation IN ('CREATED', 'ROTATED', 'RETIRED'))
);

CREATE INDEX idx_ainer_oauth_service_client_audit_client_time
    ON ainer_oauth_service_client_audit (client_id, occurred_at DESC);

CREATE INDEX idx_ainer_oauth_service_client_audit_actor_time
    ON ainer_oauth_service_client_audit (actor_service_id, occurred_at DESC);
