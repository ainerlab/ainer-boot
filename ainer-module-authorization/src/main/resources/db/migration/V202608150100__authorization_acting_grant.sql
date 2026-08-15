-- ActingGrant baseline (ADR-0043 A1): one-layer principal→agent delegation with structured
-- permission subset and a single structured scope. GLOBAL is not delegable; grants are
-- non-delegable by construction. Decision audit gains nullable agent/grant correlation columns.

CREATE TABLE ainer_authorization_acting_grant (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    principal_issuer VARCHAR(256) NOT NULL,
    principal_subject_id VARCHAR(256) NOT NULL,
    principal_type VARCHAR(16) NOT NULL,
    agent_id UUID NOT NULL,
    agent_version VARCHAR(64) NOT NULL,
    scope_kind VARCHAR(16) NOT NULL,
    workspace_id UUID,
    resource_type VARCHAR(128),
    resource_id UUID,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_authorization_acting_grant_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_authorization_acting_grant_principal_type
        CHECK (principal_type = 'USER'),
    CONSTRAINT ck_ainer_authorization_acting_grant_scope_kind
        CHECK (scope_kind IN ('WORKSPACE', 'RESOURCE')),
    CONSTRAINT ck_ainer_authorization_acting_grant_scope_workspace
        CHECK ((scope_kind <> 'WORKSPACE')
               OR (workspace_id IS NOT NULL AND resource_type IS NULL AND resource_id IS NULL)),
    CONSTRAINT ck_ainer_authorization_acting_grant_scope_resource
        CHECK ((scope_kind <> 'RESOURCE')
               OR (workspace_id IS NOT NULL AND resource_type IS NOT NULL AND resource_id IS NOT NULL)),
    CONSTRAINT ck_ainer_authorization_acting_grant_period
        CHECK (valid_until IS NULL OR valid_until > valid_from)
);

CREATE INDEX idx_ainer_authorization_acting_grant_principal
    ON ainer_authorization_acting_grant (principal_issuer, principal_subject_id, status);

CREATE INDEX idx_ainer_authorization_acting_grant_agent
    ON ainer_authorization_acting_grant (agent_id, status);

CREATE TABLE ainer_authorization_acting_grant_permission (
    acting_grant_id UUID NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    CONSTRAINT pk_ainer_authorization_acting_grant_permission
        PRIMARY KEY (acting_grant_id, permission_code),
    CONSTRAINT fk_ainer_authorization_acting_grant_permission_grant
        FOREIGN KEY (acting_grant_id)
        REFERENCES ainer_authorization_acting_grant (id) ON DELETE CASCADE,
    CONSTRAINT fk_ainer_authorization_acting_grant_permission_code
        FOREIGN KEY (permission_code)
        REFERENCES ainer_authorization_permission (code)
);

ALTER TABLE ainer_authorization_decision_audit
    ADD COLUMN agent_id UUID NULL,
    ADD COLUMN acting_grant_id UUID NULL;

COMMENT ON COLUMN ainer_authorization_decision_audit.agent_id IS '委托检查点决策关联的 Agent（ADR-0043，可空）';
COMMENT ON COLUMN ainer_authorization_decision_audit.acting_grant_id IS '委托检查点决策关联的 ActingGrant（ADR-0043，可空）';
