ALTER TABLE ainer_workspace
    ADD COLUMN tenant_id VARCHAR(128);

-- Existing M1 sample rows cannot be assigned to a real tenant safely. Keep them
-- quarantined under an unreachable-by-default identifier until an operator maps
-- or removes them explicitly.
UPDATE ainer_workspace
SET tenant_id = 'legacy-unassigned/' || id::text
WHERE tenant_id IS NULL;

ALTER TABLE ainer_workspace
    ALTER COLUMN tenant_id SET NOT NULL,
    ADD CONSTRAINT ck_ainer_workspace_tenant
        CHECK (tenant_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    ADD CONSTRAINT uq_ainer_workspace_tenant_id
        UNIQUE (tenant_id, id);

ALTER TABLE ainer_workspace_member
    ADD COLUMN tenant_id VARCHAR(128);

UPDATE ainer_workspace_member member
SET tenant_id = workspace.tenant_id
FROM ainer_workspace workspace
WHERE member.workspace_id = workspace.id
  AND member.tenant_id IS NULL;

ALTER TABLE ainer_workspace_member
    ALTER COLUMN tenant_id SET NOT NULL,
    ADD CONSTRAINT ck_ainer_workspace_member_tenant
        CHECK (tenant_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    ADD CONSTRAINT fk_ainer_workspace_member_tenant_workspace
        FOREIGN KEY (tenant_id, workspace_id)
        REFERENCES ainer_workspace (tenant_id, id)
        ON DELETE CASCADE;

DROP INDEX idx_ainer_workspace_created;

DROP INDEX idx_ainer_workspace_member_subject;

CREATE INDEX idx_ainer_workspace_tenant_created
    ON ainer_workspace (tenant_id, created_at DESC, id DESC);

CREATE INDEX idx_ainer_workspace_member_tenant_subject
    ON ainer_workspace_member (tenant_id, subject_id, workspace_id);
