CREATE TABLE {{table.name}} (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid NOT NULL,
{{entity.sqlColumns}}
    version bigint NOT NULL DEFAULT 0,
    created_by_subject_id varchar(200) NOT NULL,
    updated_by_subject_id varchar(200) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT {{table.versionConstraint}} CHECK (version >= 0){{entity.uniqueConstraints}}
);

CREATE INDEX {{table.pageIndex}}
    ON {{table.name}} (workspace_id, updated_at DESC, id DESC);

CREATE TABLE {{audit.table.name}} (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid NOT NULL,
    resource_id uuid NULL,
    actor_subject_id varchar(200) NOT NULL,
    action varchar(32) NOT NULL,
    decision varchar(16) NOT NULL,
    reason_code varchar(160) NOT NULL,
    request_id varchar(160) NULL,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT {{audit.decisionConstraint}}
        CHECK (decision IN ('ALLOW', 'DENY'))
);

CREATE INDEX {{audit.workspaceTimeIndex}}
    ON {{audit.table.name}} (workspace_id, occurred_at DESC, id DESC);

COMMENT ON COLUMN {{table.name}}.workspace_id IS 'Authoritative Workspace isolation key';
{{entity.sqlComments}}
COMMENT ON TABLE {{audit.table.name}} IS 'Fail-closed resource authorization decisions; no token or request body';
