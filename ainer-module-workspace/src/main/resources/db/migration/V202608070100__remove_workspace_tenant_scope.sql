ALTER TABLE ainer_workspace_member
    DROP CONSTRAINT IF EXISTS fk_ainer_workspace_member_tenant_workspace,
    DROP CONSTRAINT IF EXISTS ck_ainer_workspace_member_tenant;

DROP INDEX IF EXISTS idx_ainer_workspace_tenant_created;
DROP INDEX IF EXISTS idx_ainer_workspace_member_tenant_subject_status;
DROP INDEX IF EXISTS uq_ainer_workspace_member_active_owner;

ALTER TABLE ainer_workspace
    DROP CONSTRAINT IF EXISTS ck_ainer_workspace_tenant,
    DROP CONSTRAINT IF EXISTS uq_ainer_workspace_tenant_id,
    DROP COLUMN IF EXISTS tenant_id;

ALTER TABLE ainer_workspace_member
    DROP COLUMN IF EXISTS tenant_id;

DROP INDEX IF EXISTS idx_ainer_workspace_audit_tenant_time;
DROP INDEX IF EXISTS idx_ainer_workspace_audit_workspace_time;
ALTER TABLE ainer_workspace_authorization_audit
    DROP CONSTRAINT IF EXISTS ck_ainer_workspace_audit_tenant,
    DROP COLUMN IF EXISTS tenant_id;

DROP INDEX IF EXISTS idx_ainer_workspace_audit_archive_tenant_time;
DROP INDEX IF EXISTS idx_ainer_workspace_audit_archive_workspace_time;
ALTER TABLE ainer_workspace_authorization_audit_archive
    DROP CONSTRAINT IF EXISTS ck_ainer_workspace_audit_archive_tenant,
    DROP COLUMN IF EXISTS tenant_id;

DROP INDEX IF EXISTS idx_ainer_workspace_identity_event_subject;
ALTER TABLE ainer_workspace_identity_event_receipt
    DROP COLUMN IF EXISTS tenant_id;

DROP INDEX IF EXISTS uk_ainer_workspace_recovery_open_workspace;
DROP INDEX IF EXISTS idx_ainer_workspace_recovery_tenant_time;
ALTER TABLE ainer_workspace_owner_recovery_request
    DROP CONSTRAINT IF EXISTS ck_ainer_workspace_recovery_tenant,
    DROP COLUMN IF EXISTS tenant_id;

DROP INDEX IF EXISTS idx_ainer_workspace_security_operation_tenant_time;
ALTER TABLE ainer_workspace_security_operation_audit
    DROP CONSTRAINT IF EXISTS ck_ainer_workspace_security_audit_tenant,
    DROP COLUMN IF EXISTS tenant_id;

CREATE INDEX idx_ainer_workspace_created
    ON ainer_workspace (created_at DESC, id DESC);

CREATE INDEX idx_ainer_workspace_member_subject
    ON ainer_workspace_member (subject_id, workspace_id);

CREATE UNIQUE INDEX ux_ainer_workspace_member_active_owner
    ON ainer_workspace_member (workspace_id)
    WHERE role = 'OWNER' AND status = 'ACTIVE';

CREATE INDEX idx_ainer_workspace_audit_workspace_time
    ON ainer_workspace_authorization_audit (workspace_id, occurred_at DESC, id DESC)
    WHERE workspace_id IS NOT NULL;

CREATE INDEX idx_ainer_workspace_audit_archive_workspace_time
    ON ainer_workspace_authorization_audit_archive (workspace_id, occurred_at DESC, id DESC)
    WHERE workspace_id IS NOT NULL;

CREATE INDEX idx_ainer_workspace_identity_event_subject
    ON ainer_workspace_identity_event_receipt (subject_id, occurred_at DESC);

CREATE UNIQUE INDEX uk_ainer_workspace_recovery_open_workspace
    ON ainer_workspace_owner_recovery_request (workspace_id)
    WHERE status = 'REQUESTED';

CREATE INDEX idx_ainer_workspace_recovery_time
    ON ainer_workspace_owner_recovery_request (requested_at DESC, id DESC);

CREATE INDEX idx_ainer_workspace_security_operation_time
    ON ainer_workspace_security_operation_audit (occurred_at DESC, id DESC);
