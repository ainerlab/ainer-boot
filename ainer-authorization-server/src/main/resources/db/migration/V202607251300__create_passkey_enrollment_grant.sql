CREATE TABLE ainer_passkey_enrollment_grant (
    subject_id UUID PRIMARY KEY REFERENCES ainer_identity_user(id) ON DELETE RESTRICT,
    tenant_id UUID NOT NULL,
    granted_by VARCHAR(128) NOT NULL,
    incident_reference VARCHAR(128) NOT NULL,
    status VARCHAR(12) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT ck_ainer_passkey_enrollment_status
        CHECK (status IN ('ACTIVE', 'CONSUMED', 'REVOKED')),
    CONSTRAINT ck_ainer_passkey_enrollment_time
        CHECK (
            (status IN ('ACTIVE', 'REVOKED') AND consumed_at IS NULL)
            OR (status = 'CONSUMED' AND consumed_at IS NOT NULL)
        ),
    CONSTRAINT ck_ainer_passkey_enrollment_actor
        CHECK (
            granted_by ~ '^[A-Za-z0-9._:@/-]{1,128}$'
            AND incident_reference ~ '^[A-Za-z0-9._:@/-]{1,128}$'
        )
);

CREATE INDEX idx_ainer_passkey_enrollment_tenant_status
    ON ainer_passkey_enrollment_grant(tenant_id, status);
