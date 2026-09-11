-- Identity principal lifecycle control-plane audit (账号/服务主体生命周期写路径的同事务审计).
-- 与 ainer_identity_human_account / ainer_identity_service_principal 的 security_epoch 递增同事务写入：
-- 状态迁移、epoch 递增与审计三者要么一起提交，要么一起回滚，不存在"改了状态但审计缺失"的窗口。

CREATE TABLE ainer_identity_principal_lifecycle_audit (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    principal_type VARCHAR(24) NOT NULL,
    principal_id UUID NOT NULL,
    operation VARCHAR(32) NOT NULL,
    previous_status VARCHAR(16) NOT NULL,
    new_status VARCHAR(16) NOT NULL,
    previous_security_epoch BIGINT NOT NULL,
    new_security_epoch BIGINT NOT NULL,
    credential_type VARCHAR(32),
    actor_service_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    change_reference VARCHAR(200) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_identity_lifecycle_audit_principal_type
        CHECK (principal_type IN ('HUMAN_ACCOUNT', 'SERVICE_PRINCIPAL')),
    CONSTRAINT ck_ainer_identity_lifecycle_audit_operation
        CHECK (operation IN ('DISABLED', 'LOCKED', 'CLOSED', 'RESTORED',
                             'PASSWORD_ROTATED', 'CREDENTIAL_REVOKED')),
    CONSTRAINT ck_ainer_identity_lifecycle_audit_status
        CHECK (previous_status IN ('ACTIVE', 'LOCKED', 'DISABLED', 'CLOSED')
            AND new_status IN ('ACTIVE', 'LOCKED', 'DISABLED', 'CLOSED')),
    -- epoch 必须恰好前进一格：审计行不可能记录"状态变了但 epoch 没动"的变更
    CONSTRAINT ck_ainer_identity_lifecycle_audit_epoch
        CHECK (previous_security_epoch >= 0
            AND new_security_epoch = previous_security_epoch + 1),
    CONSTRAINT ck_ainer_identity_lifecycle_audit_credential
        CHECK (credential_type IS NULL
            OR (credential_type IN ('PASSWORD', 'WEBAUTHN_PUBLIC_KEY', 'OIDC_SUBJECT')
                AND operation IN ('PASSWORD_ROTATED', 'CREDENTIAL_REVOKED'))),
    CONSTRAINT ck_ainer_identity_lifecycle_audit_actor
        CHECK (actor_service_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'
            AND request_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'
            AND change_reference ~ '^[A-Za-z0-9._:@/-]{1,200}$')
);

CREATE INDEX idx_ainer_identity_lifecycle_audit_principal_time
    ON ainer_identity_principal_lifecycle_audit (principal_id, occurred_at DESC, id DESC);

CREATE INDEX idx_ainer_identity_lifecycle_audit_actor_time
    ON ainer_identity_principal_lifecycle_audit (actor_service_id, occurred_at DESC);
