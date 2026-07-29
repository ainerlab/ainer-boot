-- M4.8C decision 30：OWNER 丢失/不可达的恢复流程。与正常转移分离的独立表与端点。

CREATE TABLE ainer_identity_ownership_recovery (
    id UUID NOT NULL DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    target_subject_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    requester_service_id VARCHAR(128) NOT NULL,
    approver_service_id VARCHAR(128),
    incident_reference VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    executed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ainer_identity_ownership_recovery_tenant
        FOREIGN KEY (tenant_id) REFERENCES ainer_identity_tenant (id) ON DELETE CASCADE,
    CONSTRAINT fk_ainer_identity_ownership_recovery_target
        FOREIGN KEY (target_subject_id) REFERENCES ainer_identity_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_ainer_identity_ownership_recovery_status
        CHECK (status IN ('REQUESTED', 'EXECUTED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_ainer_identity_ownership_recovery_requester
        CHECK (requester_service_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_identity_ownership_recovery_approver
        CHECK (approver_service_id IS NULL OR approver_service_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_identity_ownership_recovery_incident
        CHECK (incident_reference ~ '^[A-Za-z0-9._:@/-]{1,128}$')
);

CREATE UNIQUE INDEX ux_ainer_identity_ownership_recovery_outstanding
    ON ainer_identity_ownership_recovery (tenant_id)
    WHERE status = 'REQUESTED';

CREATE INDEX idx_ainer_identity_ownership_recovery_tenant_time
    ON ainer_identity_ownership_recovery (tenant_id, created_at DESC, id DESC);

ALTER TABLE ainer_identity_security_operation_audit
    DROP CONSTRAINT ck_ainer_identity_security_operation_type,
    ADD CONSTRAINT ck_ainer_identity_security_operation_type
        CHECK (operation_type IN ('ACCESS_EVENT_REPLAY', 'OWNERSHIP_RECOVERY'));
