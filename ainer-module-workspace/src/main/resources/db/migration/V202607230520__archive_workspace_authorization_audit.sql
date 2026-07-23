CREATE TABLE ainer_workspace_authorization_audit_archive (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    workspace_id UUID,
    actor_subject_id VARCHAR(128) NOT NULL,
    target_subject_id VARCHAR(128),
    action VARCHAR(40) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reason_code VARCHAR(96) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_workspace_audit_archive_tenant
        CHECK (tenant_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_workspace_audit_archive_actor
        CHECK (actor_subject_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_workspace_audit_archive_target
        CHECK (target_subject_id IS NULL OR target_subject_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_workspace_audit_archive_action
        CHECK (action IN (
            'WORKSPACE_CREATE', 'WORKSPACE_READ', 'WORKSPACE_PAGE', 'WORKSPACE_RENAME',
            'MEMBER_INVITE', 'MEMBERSHIP_ACCEPT', 'MEMBER_ROLE_CHANGE',
            'MEMBER_REMOVE', 'OWNERSHIP_TRANSFER', 'AUTHORIZATION_AUDIT_READ'
        )),
    CONSTRAINT ck_ainer_workspace_audit_archive_decision
        CHECK (decision IN ('ALLOWED', 'DENIED')),
    CONSTRAINT ck_ainer_workspace_audit_archive_reason
        CHECK (reason_code ~ '^AINER\.[A-Z0-9_.]{1,89}$'),
    CONSTRAINT ck_ainer_workspace_audit_archive_time
        CHECK (archived_at >= occurred_at)
);

CREATE INDEX idx_ainer_workspace_audit_archive_tenant_time
    ON ainer_workspace_authorization_audit_archive (tenant_id, occurred_at DESC, id DESC);

CREATE INDEX idx_ainer_workspace_audit_archive_workspace_time
    ON ainer_workspace_authorization_audit_archive (
        tenant_id, workspace_id, occurred_at DESC, id DESC
    ) WHERE workspace_id IS NOT NULL;
