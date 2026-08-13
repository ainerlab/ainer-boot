-- Standalone Workspace baseline. Membership, not an outer container, proves access.

CREATE TABLE ainer_workspace (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_workspace_name
        CHECK (char_length(btrim(name)) BETWEEN 2 AND 80),
    CONSTRAINT ck_ainer_workspace_version
        CHECK (version >= 0),
    CONSTRAINT ck_ainer_workspace_time
        CHECK (updated_at >= created_at)
);

CREATE TABLE ainer_workspace_member (
    workspace_id UUID NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    role VARCHAR(24) NOT NULL,
    status VARCHAR(16) NOT NULL,
    invited_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_workspace_member PRIMARY KEY (workspace_id, subject_id),
    CONSTRAINT fk_ainer_workspace_member_workspace
        FOREIGN KEY (workspace_id) REFERENCES ainer_workspace (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_ainer_workspace_member_subject
        CHECK (char_length(btrim(subject_id)) BETWEEN 1 AND 128),
    CONSTRAINT ck_ainer_workspace_member_role
        CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    CONSTRAINT ck_ainer_workspace_member_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
    CONSTRAINT ck_ainer_workspace_member_invited_by
        CHECK (invited_by ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_workspace_member_activation
        CHECK (
            (status = 'PENDING' AND activated_at IS NULL)
            OR (status = 'ACTIVE' AND activated_at IS NOT NULL)
            OR status = 'REVOKED'
        ),
    CONSTRAINT ck_ainer_workspace_member_owner_not_pending
        CHECK (role <> 'OWNER' OR status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_ainer_workspace_member_time
        CHECK (updated_at >= created_at AND (activated_at IS NULL OR activated_at >= created_at))
);

CREATE INDEX idx_ainer_workspace_created
    ON ainer_workspace (created_at DESC, id DESC);

CREATE INDEX idx_ainer_workspace_member_subject
    ON ainer_workspace_member (subject_id, workspace_id);

CREATE UNIQUE INDEX ux_ainer_workspace_member_active_owner
    ON ainer_workspace_member (workspace_id)
    WHERE role = 'OWNER' AND status = 'ACTIVE';

CREATE TABLE ainer_workspace_authorization_audit (
    id UUID PRIMARY KEY,
    workspace_id UUID,
    actor_subject_id VARCHAR(128) NOT NULL,
    target_subject_id VARCHAR(128),
    action VARCHAR(40) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reason_code VARCHAR(96) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_workspace_audit_actor
        CHECK (actor_subject_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_workspace_audit_target
        CHECK (target_subject_id IS NULL OR target_subject_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_workspace_audit_action
        CHECK (action IN (
            'WORKSPACE_CREATE', 'WORKSPACE_READ', 'WORKSPACE_PAGE', 'WORKSPACE_RENAME',
            'MEMBER_INVITE', 'MEMBERSHIP_ACCEPT', 'MEMBER_ROLE_CHANGE',
            'MEMBER_REMOVE', 'OWNERSHIP_TRANSFER', 'AUTHORIZATION_AUDIT_READ'
        )),
    CONSTRAINT ck_ainer_workspace_audit_decision
        CHECK (decision IN ('ALLOWED', 'DENIED')),
    CONSTRAINT ck_ainer_workspace_audit_reason
        CHECK (reason_code ~ '^AINER\.[A-Z0-9_.]{1,89}$')
);

CREATE INDEX idx_ainer_workspace_audit_workspace_time
    ON ainer_workspace_authorization_audit (workspace_id, occurred_at DESC, id DESC)
    WHERE workspace_id IS NOT NULL;

CREATE TABLE ainer_workspace_authorization_audit_archive (
    id UUID PRIMARY KEY,
    workspace_id UUID,
    actor_subject_id VARCHAR(128) NOT NULL,
    target_subject_id VARCHAR(128),
    action VARCHAR(40) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reason_code VARCHAR(96) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL,
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

CREATE INDEX idx_ainer_workspace_audit_archive_workspace_time
    ON ainer_workspace_authorization_audit_archive (workspace_id, occurred_at DESC, id DESC)
    WHERE workspace_id IS NOT NULL;

CREATE TABLE ainer_workspace_owner_recovery_request (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    new_owner_subject_id VARCHAR(128) NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    approved_by VARCHAR(128),
    incident_reference VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    executed_at TIMESTAMPTZ,
    CONSTRAINT fk_ainer_workspace_recovery_workspace
        FOREIGN KEY (workspace_id) REFERENCES ainer_workspace (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_ainer_workspace_recovery_subject
        CHECK (new_owner_subject_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_workspace_recovery_requested_by
        CHECK (requested_by ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_workspace_recovery_approved_by
        CHECK (approved_by IS NULL OR approved_by ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_workspace_recovery_incident
        CHECK (incident_reference ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_workspace_recovery_status
        CHECK (status IN ('REQUESTED', 'EXECUTED', 'EXPIRED')),
    CONSTRAINT ck_ainer_workspace_recovery_time
        CHECK (expires_at > requested_at),
    CONSTRAINT ck_ainer_workspace_recovery_execution
        CHECK (
            (status IN ('REQUESTED', 'EXPIRED') AND approved_by IS NULL AND executed_at IS NULL)
            OR (status = 'EXECUTED' AND approved_by IS NOT NULL
                AND approved_by <> requested_by AND executed_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX ux_ainer_workspace_recovery_open_workspace
    ON ainer_workspace_owner_recovery_request (workspace_id)
    WHERE status = 'REQUESTED';

CREATE INDEX idx_ainer_workspace_recovery_time
    ON ainer_workspace_owner_recovery_request (requested_at DESC, id DESC);

CREATE TABLE ainer_workspace_security_operation_audit (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL,
    workspace_id UUID,
    target_subject_id VARCHAR(128),
    operation_type VARCHAR(48) NOT NULL,
    phase VARCHAR(24) NOT NULL,
    actor_service_id VARCHAR(128) NOT NULL,
    incident_reference VARCHAR(128),
    record_count INTEGER,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ainer_workspace_security_audit_workspace
        FOREIGN KEY (workspace_id) REFERENCES ainer_workspace (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_ainer_workspace_security_audit_target
        CHECK (target_subject_id IS NULL OR target_subject_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_workspace_security_audit_type
        CHECK (operation_type IN ('OWNER_RECOVERY', 'AUTHORIZATION_AUDIT_EXPORT')),
    CONSTRAINT ck_ainer_workspace_security_audit_phase
        CHECK (phase IN ('REQUESTED', 'EXECUTED', 'EXPORTED')),
    CONSTRAINT ck_ainer_workspace_security_audit_actor
        CHECK (actor_service_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_workspace_security_audit_incident
        CHECK (incident_reference IS NULL OR incident_reference ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_workspace_security_audit_count
        CHECK (record_count IS NULL OR record_count >= 0),
    CONSTRAINT ck_ainer_workspace_security_audit_shape
        CHECK (
            (operation_type = 'OWNER_RECOVERY' AND phase IN ('REQUESTED', 'EXECUTED')
                AND workspace_id IS NOT NULL AND target_subject_id IS NOT NULL
                AND incident_reference IS NOT NULL AND record_count IS NULL)
            OR (operation_type = 'AUTHORIZATION_AUDIT_EXPORT' AND phase = 'EXPORTED'
                AND target_subject_id IS NULL AND incident_reference IS NULL AND record_count IS NOT NULL)
        )
);

CREATE UNIQUE INDEX ux_ainer_workspace_security_operation_phase
    ON ainer_workspace_security_operation_audit (operation_id, phase);

CREATE INDEX idx_ainer_workspace_security_operation_time
    ON ainer_workspace_security_operation_audit (occurred_at DESC, id DESC);
