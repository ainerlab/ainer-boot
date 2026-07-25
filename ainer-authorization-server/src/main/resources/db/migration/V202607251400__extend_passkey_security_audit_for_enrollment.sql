ALTER TABLE ainer_passkey_security_operation_audit
    DROP CONSTRAINT ck_ainer_passkey_security_audit_type;

ALTER TABLE ainer_passkey_security_operation_audit
    ADD CONSTRAINT ck_ainer_passkey_security_audit_type
        CHECK (operation_type IN (
            'RECOVERY_CODE_ISSUED', 'SELF_RECOVERY', 'ADMIN_RECOVERY', 'ENROLLMENT_GRANT'));

ALTER TABLE ainer_passkey_security_operation_audit
    DROP CONSTRAINT ck_ainer_passkey_security_audit_phase;

ALTER TABLE ainer_passkey_security_operation_audit
    ADD CONSTRAINT ck_ainer_passkey_security_audit_phase
        CHECK (phase IN (
            'ISSUED', 'REDEEMED', 'REQUESTED', 'EXECUTED', 'GRANTED', 'REVOKED'));
