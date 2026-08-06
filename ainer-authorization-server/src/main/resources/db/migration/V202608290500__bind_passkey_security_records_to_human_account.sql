ALTER TABLE ainer_passkey_recovery_code
    ALTER COLUMN subject_id DROP NOT NULL,
    ALTER COLUMN tenant_id DROP NOT NULL,
    ADD COLUMN account_id UUID
        REFERENCES ainer_identity_human_account(id) ON DELETE RESTRICT;

ALTER TABLE ainer_passkey_recovery_code
    ADD CONSTRAINT ck_ainer_passkey_recovery_code_identity_owner
        CHECK (subject_id IS NOT NULL OR account_id IS NOT NULL);

CREATE INDEX idx_ainer_passkey_recovery_code_account_status
    ON ainer_passkey_recovery_code(account_id, status);

ALTER TABLE ainer_passkey_recovery_lockout
    DROP CONSTRAINT ainer_passkey_recovery_lockout_pkey,
    ALTER COLUMN subject_id DROP NOT NULL,
    ALTER COLUMN tenant_id DROP NOT NULL,
    ADD COLUMN account_id UUID
        REFERENCES ainer_identity_human_account(id) ON DELETE RESTRICT;

ALTER TABLE ainer_passkey_recovery_lockout
    ADD CONSTRAINT ck_ainer_passkey_recovery_lockout_identity_owner
        CHECK (subject_id IS NOT NULL OR account_id IS NOT NULL);

CREATE UNIQUE INDEX uk_ainer_passkey_recovery_lockout_subject
    ON ainer_passkey_recovery_lockout(subject_id)
    WHERE subject_id IS NOT NULL;

CREATE UNIQUE INDEX uk_ainer_passkey_recovery_lockout_account
    ON ainer_passkey_recovery_lockout(account_id)
    WHERE account_id IS NOT NULL;

ALTER TABLE ainer_passkey_recovery_request
    ALTER COLUMN subject_id DROP NOT NULL,
    ALTER COLUMN tenant_id DROP NOT NULL,
    ADD COLUMN account_id UUID
        REFERENCES ainer_identity_human_account(id) ON DELETE RESTRICT;

ALTER TABLE ainer_passkey_recovery_request
    ADD CONSTRAINT ck_ainer_passkey_recovery_request_identity_owner
        CHECK (subject_id IS NOT NULL OR account_id IS NOT NULL);

CREATE UNIQUE INDEX uk_ainer_passkey_recovery_request_open_account
    ON ainer_passkey_recovery_request(account_id)
    WHERE account_id IS NOT NULL AND status = 'REQUESTED';

ALTER TABLE ainer_passkey_security_operation_audit
    ALTER COLUMN subject_id DROP NOT NULL,
    ALTER COLUMN tenant_id DROP NOT NULL,
    ADD COLUMN account_id UUID
        REFERENCES ainer_identity_human_account(id) ON DELETE RESTRICT;

ALTER TABLE ainer_passkey_security_operation_audit
    ADD CONSTRAINT ck_ainer_passkey_security_audit_identity_owner
        CHECK (subject_id IS NOT NULL OR account_id IS NOT NULL);

CREATE INDEX idx_ainer_passkey_security_audit_account_time
    ON ainer_passkey_security_operation_audit(account_id, occurred_at, id);

ALTER TABLE ainer_passkey_enrollment_grant
    DROP CONSTRAINT ainer_passkey_enrollment_grant_pkey,
    ALTER COLUMN subject_id DROP NOT NULL,
    ALTER COLUMN tenant_id DROP NOT NULL,
    ADD COLUMN account_id UUID
        REFERENCES ainer_identity_human_account(id) ON DELETE RESTRICT;

ALTER TABLE ainer_passkey_enrollment_grant
    ADD CONSTRAINT ck_ainer_passkey_enrollment_identity_owner
        CHECK (subject_id IS NOT NULL OR account_id IS NOT NULL);

CREATE UNIQUE INDEX uk_ainer_passkey_enrollment_subject
    ON ainer_passkey_enrollment_grant(subject_id)
    WHERE subject_id IS NOT NULL;

CREATE UNIQUE INDEX uk_ainer_passkey_enrollment_account
    ON ainer_passkey_enrollment_grant(account_id)
    WHERE account_id IS NOT NULL;

CREATE INDEX idx_ainer_passkey_enrollment_account_status
    ON ainer_passkey_enrollment_grant(account_id, status);
