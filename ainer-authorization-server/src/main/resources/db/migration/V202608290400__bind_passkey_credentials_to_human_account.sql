ALTER TABLE ainer_passkey_credential
    ALTER COLUMN subject_id DROP NOT NULL;

ALTER TABLE ainer_passkey_credential
    ADD COLUMN account_id UUID
        REFERENCES ainer_identity_human_account(id) ON DELETE RESTRICT;

ALTER TABLE ainer_passkey_credential
    ADD CONSTRAINT ck_ainer_passkey_identity_owner
        CHECK (subject_id IS NOT NULL OR account_id IS NOT NULL);

CREATE INDEX idx_ainer_passkey_account_status
    ON ainer_passkey_credential(account_id, status);

ALTER TABLE ainer_passkey_credential_audit
    ALTER COLUMN subject_id DROP NOT NULL;

ALTER TABLE ainer_passkey_credential_audit
    ADD COLUMN account_id UUID
        REFERENCES ainer_identity_human_account(id) ON DELETE RESTRICT;

ALTER TABLE ainer_passkey_credential_audit
    ADD CONSTRAINT ck_ainer_passkey_audit_identity_owner
        CHECK (subject_id IS NOT NULL OR account_id IS NOT NULL);

CREATE INDEX idx_ainer_passkey_audit_account_time
    ON ainer_passkey_credential_audit(account_id, occurred_at, id);
