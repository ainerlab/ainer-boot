CREATE TABLE ainer_workspace (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_workspace_name
        CHECK (char_length(btrim(name)) BETWEEN 2 AND 80),
    CONSTRAINT ck_ainer_workspace_version
        CHECK (version >= 0),
    CONSTRAINT ck_ainer_workspace_time
        CHECK (updated_at >= created_at)
);

CREATE TABLE ainer_workspace_member (
    workspace_id UUID NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    role VARCHAR(24) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (workspace_id, subject_id),
    CONSTRAINT fk_ainer_workspace_member_workspace
        FOREIGN KEY (workspace_id) REFERENCES ainer_workspace (id) ON DELETE CASCADE,
    CONSTRAINT ck_ainer_workspace_member_subject
        CHECK (char_length(btrim(subject_id)) BETWEEN 1 AND 128),
    CONSTRAINT ck_ainer_workspace_member_role
        CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))
);

CREATE INDEX idx_ainer_workspace_created
    ON ainer_workspace (created_at DESC, id DESC);

CREATE INDEX idx_ainer_workspace_member_subject
    ON ainer_workspace_member (subject_id, workspace_id);
