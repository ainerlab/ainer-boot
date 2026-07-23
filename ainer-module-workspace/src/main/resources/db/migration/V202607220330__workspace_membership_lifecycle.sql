ALTER TABLE ainer_workspace_member
    RENAME COLUMN joined_at TO created_at;

ALTER TABLE ainer_workspace_member
    ADD COLUMN status VARCHAR(16),
    ADD COLUMN invited_by VARCHAR(128),
    ADD COLUMN activated_at TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE ainer_workspace_member
SET status = 'ACTIVE',
    invited_by = subject_id,
    activated_at = created_at,
    updated_at = created_at
WHERE status IS NULL;

ALTER TABLE ainer_workspace_member
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN invited_by SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ADD CONSTRAINT ck_ainer_workspace_member_status
        CHECK (status IN ('PENDING', 'ACTIVE')),
    ADD CONSTRAINT ck_ainer_workspace_member_invited_by
        CHECK (invited_by ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    ADD CONSTRAINT ck_ainer_workspace_member_activation
        CHECK (
            (status = 'PENDING' AND activated_at IS NULL)
            OR (status = 'ACTIVE' AND activated_at IS NOT NULL)
        ),
    ADD CONSTRAINT ck_ainer_workspace_member_owner_active
        CHECK (role <> 'OWNER' OR status = 'ACTIVE'),
    ADD CONSTRAINT ck_ainer_workspace_member_time
        CHECK (
            updated_at >= created_at
            AND (activated_at IS NULL OR activated_at >= created_at)
        );

DROP INDEX idx_ainer_workspace_member_tenant_subject;

CREATE INDEX idx_ainer_workspace_member_tenant_subject_status
    ON ainer_workspace_member (tenant_id, subject_id, status, workspace_id);

CREATE UNIQUE INDEX uq_ainer_workspace_member_active_owner
    ON ainer_workspace_member (tenant_id, workspace_id)
    WHERE role = 'OWNER' AND status = 'ACTIVE';
