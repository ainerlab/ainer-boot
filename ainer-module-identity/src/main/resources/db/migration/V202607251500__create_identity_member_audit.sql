CREATE TABLE ainer_identity_member_audit (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES ainer_identity_tenant(id) ON DELETE RESTRICT,
    actor_subject_id UUID NOT NULL REFERENCES ainer_identity_user(id) ON DELETE RESTRICT,
    target_subject_id UUID NOT NULL REFERENCES ainer_identity_user(id) ON DELETE RESTRICT,
    operation VARCHAR(16) NOT NULL,
    role VARCHAR(16),
    request_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_identity_member_audit_operation
        CHECK (operation IN ('ADDED', 'REMOVED', 'ROLE_CHANGED')),
    CONSTRAINT ck_ainer_identity_member_audit_role
        CHECK (role IS NULL OR role IN ('OWNER', 'ADMIN', 'MEMBER'))
);

CREATE INDEX idx_ainer_identity_member_audit_tenant_time
    ON ainer_identity_member_audit(tenant_id, occurred_at DESC, id DESC);
