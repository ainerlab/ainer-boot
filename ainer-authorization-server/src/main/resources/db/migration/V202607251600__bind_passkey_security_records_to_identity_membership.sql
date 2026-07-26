ALTER TABLE ainer_passkey_recovery_code
    ADD CONSTRAINT fk_ainer_passkey_recovery_code_membership
        FOREIGN KEY (tenant_id, subject_id)
        REFERENCES ainer_identity_membership(tenant_id, user_id)
        ON DELETE RESTRICT;

ALTER TABLE ainer_passkey_recovery_lockout
    ADD CONSTRAINT fk_ainer_passkey_recovery_lockout_membership
        FOREIGN KEY (tenant_id, subject_id)
        REFERENCES ainer_identity_membership(tenant_id, user_id)
        ON DELETE RESTRICT;

ALTER TABLE ainer_passkey_recovery_request
    ADD CONSTRAINT fk_ainer_passkey_recovery_request_membership
        FOREIGN KEY (tenant_id, subject_id)
        REFERENCES ainer_identity_membership(tenant_id, user_id)
        ON DELETE RESTRICT;

ALTER TABLE ainer_passkey_security_operation_audit
    ADD CONSTRAINT fk_ainer_passkey_security_audit_membership
        FOREIGN KEY (tenant_id, subject_id)
        REFERENCES ainer_identity_membership(tenant_id, user_id)
        ON DELETE RESTRICT;

ALTER TABLE ainer_passkey_enrollment_grant
    ADD CONSTRAINT fk_ainer_passkey_enrollment_grant_membership
        FOREIGN KEY (tenant_id, subject_id)
        REFERENCES ainer_identity_membership(tenant_id, user_id)
        ON DELETE RESTRICT;
