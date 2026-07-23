CREATE TABLE ainer_workspace_owner_recovery_request (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    workspace_id UUID NOT NULL,
    new_owner_subject_id VARCHAR(128) NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    approved_by VARCHAR(128),
    incident_reference VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    executed_at TIMESTAMPTZ,
    CONSTRAINT ck_ainer_workspace_recovery_tenant
        CHECK (tenant_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
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
            OR (
                status = 'EXECUTED'
                AND approved_by IS NOT NULL
                AND approved_by <> requested_by
                AND executed_at IS NOT NULL
            )
        )
);

CREATE UNIQUE INDEX uk_ainer_workspace_recovery_open_workspace
    ON ainer_workspace_owner_recovery_request (tenant_id, workspace_id)
    WHERE status = 'REQUESTED';

CREATE INDEX idx_ainer_workspace_recovery_tenant_time
    ON ainer_workspace_owner_recovery_request (tenant_id, requested_at DESC, id DESC);

CREATE TABLE ainer_workspace_security_operation_audit (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    workspace_id UUID,
    target_subject_id VARCHAR(128),
    operation_type VARCHAR(48) NOT NULL,
    phase VARCHAR(24) NOT NULL,
    actor_service_id VARCHAR(128) NOT NULL,
    incident_reference VARCHAR(128),
    record_count INTEGER,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_workspace_security_audit_tenant
        CHECK (tenant_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
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
            (
                operation_type = 'OWNER_RECOVERY'
                AND phase IN ('REQUESTED', 'EXECUTED')
                AND workspace_id IS NOT NULL
                AND target_subject_id IS NOT NULL
                AND incident_reference IS NOT NULL
                AND record_count IS NULL
            )
            OR (
                operation_type = 'AUTHORIZATION_AUDIT_EXPORT'
                AND phase = 'EXPORTED'
                AND target_subject_id IS NULL
                AND incident_reference IS NULL
                AND record_count IS NOT NULL
            )
        )
);

CREATE UNIQUE INDEX uk_ainer_workspace_security_operation_phase
    ON ainer_workspace_security_operation_audit (operation_id, phase);

CREATE INDEX idx_ainer_workspace_security_operation_tenant_time
    ON ainer_workspace_security_operation_audit (tenant_id, occurred_at DESC, id DESC);
