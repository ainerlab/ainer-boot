-- M4.8C：所有权转移状态机表、审计操作与访问事件类型扩展。

CREATE TABLE ainer_identity_ownership_transfer (
    id UUID NOT NULL DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    initiator_subject_id UUID NOT NULL,
    target_subject_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    reason_code VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    executed_at TIMESTAMPTZ,
    executed_by_subject_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ainer_identity_ownership_transfer_tenant
        FOREIGN KEY (tenant_id) REFERENCES ainer_identity_tenant (id) ON DELETE CASCADE,
    CONSTRAINT fk_ainer_identity_ownership_transfer_initiator
        FOREIGN KEY (initiator_subject_id) REFERENCES ainer_identity_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_ainer_identity_ownership_transfer_target
        FOREIGN KEY (target_subject_id) REFERENCES ainer_identity_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_ainer_identity_ownership_transfer_status
        CHECK (status IN ('REQUESTED', 'EXECUTED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_ainer_identity_ownership_transfer_distinct_parties
        CHECK (initiator_subject_id <> target_subject_id),
    CONSTRAINT ck_ainer_identity_ownership_transfer_reason
        CHECK (reason_code ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_identity_ownership_transfer_request
        CHECK (request_id ~ '^[A-Za-z0-9._:@/-]{1,128}$')
);

CREATE UNIQUE INDEX ux_ainer_identity_ownership_transfer_outstanding
    ON ainer_identity_ownership_transfer (tenant_id)
    WHERE status = 'REQUESTED';

CREATE INDEX idx_ainer_identity_ownership_transfer_tenant_time
    ON ainer_identity_ownership_transfer (tenant_id, created_at DESC, id DESC);

ALTER TABLE ainer_identity_member_audit
    ALTER COLUMN operation TYPE VARCHAR(32),
    DROP CONSTRAINT ck_ainer_identity_member_audit_operation,
    ADD CONSTRAINT ck_ainer_identity_member_audit_operation
        CHECK (operation IN ('ADDED', 'REACTIVATED', 'REMOVED', 'ROLE_CHANGED', 'OWNERSHIP_TRANSFERRED'));

ALTER TABLE ainer_identity_access_event
    DROP CONSTRAINT ck_ainer_identity_access_event_type,
    ADD CONSTRAINT ck_ainer_identity_access_event_type
        CHECK (event_type IN (
            'IDENTITY_USER_DISABLED',
            'IDENTITY_MEMBERSHIP_REVOKED',
            'IDENTITY_MEMBERSHIP_ROLE_CHANGED'));
