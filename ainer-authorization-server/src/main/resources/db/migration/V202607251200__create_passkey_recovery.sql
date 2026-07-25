CREATE TABLE ainer_passkey_recovery_code (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL REFERENCES ainer_identity_user(id) ON DELETE RESTRICT,
    tenant_id UUID NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    status VARCHAR(12) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    CONSTRAINT ck_ainer_passkey_recovery_code_status
        CHECK (status IN ('ACTIVE', 'USED', 'SUPERSEDED')),
    CONSTRAINT ck_ainer_passkey_recovery_code_used_time
        CHECK (
            (status = 'ACTIVE' AND used_at IS NULL)
            OR (status IN ('USED', 'SUPERSEDED') AND used_at IS NOT NULL)
        )
);

CREATE INDEX idx_ainer_passkey_recovery_code_subject_status
    ON ainer_passkey_recovery_code(subject_id, status);

CREATE TABLE ainer_passkey_recovery_lockout (
    subject_id UUID PRIMARY KEY REFERENCES ainer_identity_user(id) ON DELETE RESTRICT,
    tenant_id UUID NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_passkey_recovery_lockout_attempts
        CHECK (failed_attempts >= 0)
);

CREATE TABLE ainer_passkey_recovery_request (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subject_id UUID NOT NULL REFERENCES ainer_identity_user(id) ON DELETE RESTRICT,
    requested_by VARCHAR(128) NOT NULL,
    approved_by VARCHAR(128),
    incident_reference VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    executed_at TIMESTAMPTZ,
    CONSTRAINT ck_ainer_passkey_recovery_request_status
        CHECK (status IN ('REQUESTED', 'EXECUTED', 'EXPIRED')),
    CONSTRAINT ck_ainer_passkey_recovery_request_reference
        CHECK (incident_reference ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_passkey_recovery_request_actor
        CHECK (
            requested_by ~ '^[A-Za-z0-9._:@/-]{1,128}$'
            AND (approved_by IS NULL OR approved_by ~ '^[A-Za-z0-9._:@/-]{1,128}$')
        ),
    CONSTRAINT ck_ainer_passkey_recovery_request_expiry
        CHECK (expires_at > requested_at),
    CONSTRAINT ck_ainer_passkey_recovery_request_execution
        CHECK (
            (status IN ('REQUESTED', 'EXPIRED')
                AND approved_by IS NULL
                AND executed_at IS NULL)
            OR (status = 'EXECUTED'
                AND approved_by IS NOT NULL
                AND approved_by <> requested_by
                AND executed_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uk_ainer_passkey_recovery_request_open_subject
    ON ainer_passkey_recovery_request(tenant_id, subject_id)
    WHERE status = 'REQUESTED';

CREATE INDEX idx_ainer_passkey_recovery_request_tenant_time
    ON ainer_passkey_recovery_request(tenant_id, requested_at DESC, id DESC);

CREATE TABLE ainer_passkey_security_operation_audit (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    subject_id UUID NOT NULL REFERENCES ainer_identity_user(id) ON DELETE RESTRICT,
    operation_type VARCHAR(32) NOT NULL,
    phase VARCHAR(16) NOT NULL,
    actor_type VARCHAR(8) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    incident_reference VARCHAR(128),
    request_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_passkey_security_audit_type
        CHECK (operation_type IN ('RECOVERY_CODE_ISSUED', 'SELF_RECOVERY', 'ADMIN_RECOVERY')),
    CONSTRAINT ck_ainer_passkey_security_audit_phase
        CHECK (phase IN ('ISSUED', 'REDEEMED', 'REQUESTED', 'EXECUTED')),
    CONSTRAINT ck_ainer_passkey_security_audit_actor
        CHECK (
            actor_type IN ('USER', 'SERVICE')
            AND actor_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'
        ),
    CONSTRAINT ck_ainer_passkey_security_audit_reference
        CHECK (incident_reference IS NULL OR incident_reference ~ '^[A-Za-z0-9._:@/-]{1,128}$')
);

CREATE UNIQUE INDEX uk_ainer_passkey_security_audit_phase
    ON ainer_passkey_security_operation_audit(operation_id, phase);

CREATE INDEX idx_ainer_passkey_security_audit_subject_time
    ON ainer_passkey_security_operation_audit(subject_id, occurred_at, id);
