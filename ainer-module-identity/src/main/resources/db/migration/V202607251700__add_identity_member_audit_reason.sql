ALTER TABLE ainer_identity_member_audit
    ADD COLUMN reason_code VARCHAR(128) NOT NULL DEFAULT 'unspecified';

ALTER TABLE ainer_identity_member_audit
    ALTER COLUMN reason_code DROP DEFAULT,
    DROP CONSTRAINT ck_ainer_identity_member_audit_operation,
    ADD CONSTRAINT ck_ainer_identity_member_audit_operation
        CHECK (operation IN ('ADDED', 'REACTIVATED', 'REMOVED', 'ROLE_CHANGED')),
    ADD CONSTRAINT ck_ainer_identity_member_audit_reason
        CHECK (reason_code ~ '^[A-Za-z0-9._:@/-]{1,128}$');
