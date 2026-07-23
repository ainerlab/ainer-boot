ALTER TABLE ainer_workspace_member
    DROP CONSTRAINT ck_ainer_workspace_member_status,
    DROP CONSTRAINT ck_ainer_workspace_member_activation,
    DROP CONSTRAINT ck_ainer_workspace_member_owner_active;

ALTER TABLE ainer_workspace_member
    ADD CONSTRAINT ck_ainer_workspace_member_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
    ADD CONSTRAINT ck_ainer_workspace_member_activation
        CHECK (
            (status = 'PENDING' AND activated_at IS NULL)
            OR (status = 'ACTIVE' AND activated_at IS NOT NULL)
            OR status = 'REVOKED'
        ),
    ADD CONSTRAINT ck_ainer_workspace_member_owner_not_pending
        CHECK (role <> 'OWNER' OR status IN ('ACTIVE', 'REVOKED'));

CREATE TABLE ainer_workspace_identity_event_receipt (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(48) NOT NULL,
    tenant_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    payload_version SMALLINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    affected_memberships INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_ainer_workspace_identity_event_type
        CHECK (event_type IN ('IDENTITY_USER_DISABLED', 'IDENTITY_MEMBERSHIP_REVOKED')),
    CONSTRAINT ck_ainer_workspace_identity_event_payload_version
        CHECK (payload_version = 1),
    CONSTRAINT ck_ainer_workspace_identity_event_affected
        CHECK (affected_memberships >= 0)
);

CREATE INDEX idx_ainer_workspace_identity_event_subject
    ON ainer_workspace_identity_event_receipt (tenant_id, subject_id, occurred_at DESC);
