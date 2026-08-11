-- Ainer Authorization foundation baseline (ADR-0030 S1).
-- The database is rebuilt from empty; no prior schema is upgraded.
-- Scope follows the post-Greenfield domain (Scope.Workspace/Resource/Global), not the
-- pre-Greenfield tenant model from the original design doc.

-- 1. Permission catalog projection (management view of PermissionContributor registrations).
--    The authority at decision time is the in-memory PermissionRegistry; this table is a
--    management/audit projection. Administrators cannot create arbitrary permission strings.
CREATE TABLE ainer_authorization_permission (
    code VARCHAR(128) PRIMARY KEY,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(128) NOT NULL,
    risk_tier VARCHAR(8) NOT NULL,
    audit_level VARCHAR(16) NOT NULL,
    system_only BOOLEAN NOT NULL,
    agent_delegable BOOLEAN NOT NULL,
    source_module VARCHAR(128),
    definition_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_authorization_permission_risk
        CHECK (risk_tier IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_ainer_authorization_permission_audit
        CHECK (audit_level IN ('NONE', 'ON_DECISION', 'ALWAYS')),
    CONSTRAINT ck_ainer_authorization_permission_values
        CHECK (btrim(action) <> '' AND btrim(resource_type) <> '')
);

-- 2. Role — a named bundle of permissions (ADR-0030 §4.1).
CREATE TABLE ainer_authorization_role (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    code VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_authorization_role_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_ainer_authorization_role_values
        CHECK (btrim(code) <> '' AND btrim(name) <> '')
);

CREATE UNIQUE INDEX ux_ainer_authorization_role_code
    ON ainer_authorization_role (code)
    WHERE status = 'ACTIVE';

-- 3. Role-permission association (composite PK, no JSON conditions in the first version).
CREATE TABLE ainer_authorization_role_permission (
    role_id UUID NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_authorization_role_permission
        PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_ainer_authorization_role_permission_role
        FOREIGN KEY (role_id) REFERENCES ainer_authorization_role (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_ainer_authorization_role_permission_permission
        FOREIGN KEY (permission_code) REFERENCES ainer_authorization_permission (code)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE INDEX idx_ainer_authorization_role_permission_role
    ON ainer_authorization_role_permission (role_id);

-- 4. SubjectBinding — assigns a Role and precise Scope to a subject over a validity window (ADR-0030 §4.1).
--    scope_kind CHECK encodes the structured Scope invariant:
--      GLOBAL   — workspace_id, resource_type, resource_id all NULL
--      WORKSPACE — workspace_id NOT NULL, resource_type/resource_id NULL
--      RESOURCE  — workspace_id, resource_type, resource_id all NOT NULL
CREATE TABLE ainer_authorization_subject_binding (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    issuer VARCHAR(256) NOT NULL,
    subject_type VARCHAR(16) NOT NULL,
    subject_id VARCHAR(256) NOT NULL,
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
    CONSTRAINT ck_ainer_authorization_binding_subject_type
        CHECK (subject_type IN ('USER', 'SERVICE')),
    CONSTRAINT ck_ainer_authorization_binding_scope_kind
        CHECK (scope_kind IN ('GLOBAL', 'WORKSPACE', 'RESOURCE')),
    CONSTRAINT ck_ainer_authorization_binding_scope_global
        CHECK ((scope_kind <> 'GLOBAL')
               OR (workspace_id IS NULL AND resource_type IS NULL AND resource_id IS NULL)),
    CONSTRAINT ck_ainer_authorization_binding_scope_workspace
        CHECK ((scope_kind <> 'WORKSPACE')
               OR (workspace_id IS NOT NULL AND resource_type IS NULL AND resource_id IS NULL)),
    CONSTRAINT ck_ainer_authorization_binding_scope_resource
        CHECK ((scope_kind <> 'RESOURCE')
               OR (workspace_id IS NOT NULL AND resource_type IS NOT NULL AND resource_id IS NOT NULL)),
    CONSTRAINT ck_ainer_authorization_binding_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_ainer_authorization_binding_validity
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT ck_ainer_authorization_binding_revoked
        CHECK ((status <> 'REVOKED') OR revoked_at IS NOT NULL),
    CONSTRAINT ck_ainer_authorization_binding_values
        CHECK (btrim(issuer) <> '' AND btrim(subject_id) <> ''),
    CONSTRAINT fk_ainer_authorization_binding_role
        FOREIGN KEY (role_id) REFERENCES ainer_authorization_role (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE INDEX idx_ainer_authorization_binding_subject
    ON ainer_authorization_subject_binding (issuer, subject_type, subject_id, status);

CREATE INDEX idx_ainer_authorization_binding_role
    ON ainer_authorization_subject_binding (role_id);

-- 5. Change audit — append-only management action log (no update, no soft delete, no Token/body).
CREATE TABLE ainer_authorization_change_audit (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    actor_issuer VARCHAR(256),
    actor_type VARCHAR(16),
    actor_id VARCHAR(256),
    target_type VARCHAR(32) NOT NULL,
    target_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    before_version BIGINT,
    after_version BIGINT,
    request_id VARCHAR(128),
    trace_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_authorization_change_audit_actor_type
        CHECK (actor_type IS NULL OR actor_type IN ('USER', 'SERVICE')),
    CONSTRAINT ck_ainer_authorization_change_audit_values
        CHECK (btrim(target_type) <> '' AND btrim(action) <> '')
);

CREATE INDEX idx_ainer_authorization_change_audit_target
    ON ainer_authorization_change_audit (target_type, target_id, occurred_at DESC, id DESC);

-- 6. Decision audit — append-only per-decision log, written according to Permission.auditLevel.
CREATE TABLE ainer_authorization_decision_audit (
    decision_id UUID PRIMARY KEY,
    workspace_id UUID,
    requester_issuer VARCHAR(256) NOT NULL,
    requester_type VARCHAR(16) NOT NULL,
    requester_id VARCHAR(256) NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    resource_type VARCHAR(128),
    resource_id UUID,
    outcome VARCHAR(8) NOT NULL,
    reason_code VARCHAR(128) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    request_id VARCHAR(128),
    trace_id VARCHAR(128),
    evaluated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_authorization_decision_audit_requester_type
        CHECK (requester_type IN ('USER', 'SERVICE')),
    CONSTRAINT ck_ainer_authorization_decision_audit_outcome
        CHECK (outcome IN ('ALLOW', 'DENY', 'CHALLENGE')),
    CONSTRAINT ck_ainer_authorization_decision_audit_values
        CHECK (btrim(requester_issuer) <> '' AND btrim(requester_id) <> '' AND btrim(permission_code) <> '')
);

CREATE INDEX idx_ainer_authorization_decision_audit_workspace_time
    ON ainer_authorization_decision_audit (workspace_id, evaluated_at DESC, decision_id DESC)
    WHERE workspace_id IS NOT NULL;

CREATE INDEX idx_ainer_authorization_decision_audit_requester
    ON ainer_authorization_decision_audit (requester_issuer, requester_type, requester_id, evaluated_at DESC);
