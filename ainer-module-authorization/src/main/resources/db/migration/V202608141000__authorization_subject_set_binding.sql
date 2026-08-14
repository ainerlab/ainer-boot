-- Subject-set bindings (ADR-0042 O2): assign a Role + structured Scope to a product-owned
-- subject set (first family: workforce.position#assignee). Additive to the published
-- V202608070340 baseline; direct bindings are untouched. GLOBAL scopes and system-only/HIGH-risk
-- permissions are rejected at creation (application guard) and defensively by the engine.

CREATE TABLE ainer_authorization_subject_set_binding (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    set_object_type VARCHAR(64) NOT NULL,
    set_object_id UUID NOT NULL,
    set_relation VARCHAR(64) NOT NULL,
    set_workspace_id UUID NOT NULL,
    set_directory_id UUID,
    role_id UUID NOT NULL,
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
    CONSTRAINT ck_ainer_authorization_set_binding_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_authorization_set_binding_scope_kind
        CHECK (scope_kind IN ('WORKSPACE', 'RESOURCE')),
    CONSTRAINT ck_ainer_authorization_set_binding_scope_workspace
        CHECK ((scope_kind <> 'WORKSPACE')
               OR (workspace_id IS NOT NULL AND resource_type IS NULL AND resource_id IS NULL)),
    CONSTRAINT ck_ainer_authorization_set_binding_scope_resource
        CHECK ((scope_kind <> 'RESOURCE')
               OR (workspace_id IS NOT NULL AND resource_type IS NOT NULL AND resource_id IS NOT NULL)),
    CONSTRAINT ck_ainer_authorization_set_binding_period
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT ck_ainer_authorization_set_binding_scope_set_workspace
        CHECK (workspace_id = set_workspace_id),
    CONSTRAINT fk_ainer_authorization_set_binding_role
        FOREIGN KEY (role_id) REFERENCES ainer_authorization_role (id)
);

CREATE INDEX idx_ainer_authorization_set_binding_live
    ON ainer_authorization_subject_set_binding (status, valid_from, valid_until);

CREATE INDEX idx_ainer_authorization_set_binding_set
    ON ainer_authorization_subject_set_binding (set_object_type, set_object_id, set_relation);
