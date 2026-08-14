-- Organization directory module baseline (ADR-0042, Workspace-anchored Greenfield model).
-- Business database: ainer (owned by ainer-server).
-- 复合外键 (workspace_id, directory_id, id) 阻止跨 Workspace/目录引用；Engagement 用
-- btree_gist + tstzrange EXCLUDE 强制同目录同 Subject 有效期不重叠（REVOKED 解除占用）。

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE ainer_org_directory (
    id UUID NOT NULL DEFAULT uuidv7(),
    workspace_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_org_directory PRIMARY KEY (id),
    CONSTRAINT ck_ainer_org_directory_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_org_directory_status CHECK (status IN ('ENABLED', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT ck_ainer_org_directory_code CHECK (btrim(code) <> ''),
    CONSTRAINT ux_ainer_org_directory_workspace_code UNIQUE (workspace_id, code),
    CONSTRAINT ux_ainer_org_directory_workspace_id UNIQUE (workspace_id, id)
);

COMMENT ON TABLE ainer_org_directory IS '组织目录容器（ADR-0042）：归属 Workspace，不表示法律公司';

CREATE TABLE ainer_org_unit (
    id UUID NOT NULL DEFAULT uuidv7(),
    workspace_id UUID NOT NULL,
    directory_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    kind VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_org_unit PRIMARY KEY (id),
    CONSTRAINT ck_ainer_org_unit_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_org_unit_kind CHECK (kind IN ('ROOT', 'UNIT')),
    CONSTRAINT ck_ainer_org_unit_status CHECK (status IN ('ENABLED', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT ck_ainer_org_unit_code CHECK (btrim(code) <> ''),
    CONSTRAINT ux_ainer_org_unit_directory_code UNIQUE (directory_id, code),
    CONSTRAINT ux_ainer_org_unit_identity UNIQUE (workspace_id, directory_id, id),
    CONSTRAINT fk_ainer_org_unit_directory
        FOREIGN KEY (workspace_id, directory_id)
        REFERENCES ainer_org_directory (workspace_id, id)
);

-- 每 Directory 恰好一个 ROOT（部分唯一）；ROOT 不允许父关系
CREATE UNIQUE INDEX ux_ainer_org_unit_root
    ON ainer_org_unit (directory_id) WHERE kind = 'ROOT';

CREATE TABLE ainer_org_unit_parent (
    id UUID NOT NULL DEFAULT uuidv7(),
    workspace_id UUID NOT NULL,
    directory_id UUID NOT NULL,
    child_unit_id UUID NOT NULL,
    parent_unit_id UUID NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_org_unit_parent PRIMARY KEY (id),
    CONSTRAINT ck_ainer_org_unit_parent_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_org_unit_parent_status CHECK (status IN ('ENABLED', 'REVOKED')),
    CONSTRAINT ck_ainer_org_unit_parent_no_self CHECK (child_unit_id <> parent_unit_id),
    CONSTRAINT ck_ainer_org_unit_parent_period
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT fk_ainer_org_unit_parent_child
        FOREIGN KEY (workspace_id, directory_id, child_unit_id)
        REFERENCES ainer_org_unit (workspace_id, directory_id, id),
    CONSTRAINT fk_ainer_org_unit_parent_parent
        FOREIGN KEY (workspace_id, directory_id, parent_unit_id)
        REFERENCES ainer_org_unit (workspace_id, directory_id, id)
);

-- 同一子 Unit 同时最多一条未闭合父关系
CREATE UNIQUE INDEX ux_ainer_org_unit_parent_open
    ON ainer_org_unit_parent (child_unit_id)
    WHERE status = 'ENABLED' AND valid_until IS NULL;

CREATE INDEX idx_ainer_org_unit_parent_child
    ON ainer_org_unit_parent (child_unit_id, status, valid_from);

CREATE TABLE ainer_org_engagement (
    id UUID NOT NULL DEFAULT uuidv7(),
    workspace_id UUID NOT NULL,
    directory_id UUID NOT NULL,
    subject_issuer VARCHAR(256) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    subject_type VARCHAR(16) NOT NULL,
    engagement_type VARCHAR(24) NOT NULL,
    employee_number VARCHAR(64),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_org_engagement PRIMARY KEY (id),
    CONSTRAINT ck_ainer_org_engagement_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_org_engagement_subject_type CHECK (subject_type = 'USER'),
    CONSTRAINT ck_ainer_org_engagement_type
        CHECK (engagement_type IN ('EMPLOYEE', 'CONTRACTOR')),
    CONSTRAINT ck_ainer_org_engagement_status CHECK (status IN ('ENABLED', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT ck_ainer_org_engagement_period
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT ck_ainer_org_engagement_subject CHECK (btrim(subject_id) <> ''),
    CONSTRAINT ux_ainer_org_engagement_employee_number UNIQUE (directory_id, employee_number),
    CONSTRAINT ux_ainer_org_engagement_identity
        UNIQUE (workspace_id, directory_id, id),
    CONSTRAINT fk_ainer_org_engagement_directory
        FOREIGN KEY (workspace_id, directory_id)
        REFERENCES ainer_org_directory (workspace_id, id),
    -- 同目录同 Subject 的非 REVOKED Engagement 有效期不得重叠（ADR-0042 §4.3）
    CONSTRAINT ex_ainer_org_engagement_subject_period EXCLUDE USING gist (
        directory_id WITH =,
        (subject_issuer || ':' || subject_id) WITH =,
        tstzrange(valid_from, valid_until) WITH &&
    ) WHERE (status <> 'REVOKED')
);

CREATE INDEX idx_ainer_org_engagement_subject
    ON ainer_org_engagement (directory_id, subject_issuer, subject_id, status);

CREATE TABLE ainer_org_unit_assignment (
    id UUID NOT NULL DEFAULT uuidv7(),
    workspace_id UUID NOT NULL,
    directory_id UUID NOT NULL,
    engagement_id UUID NOT NULL,
    org_unit_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_org_unit_assignment PRIMARY KEY (id),
    CONSTRAINT ck_ainer_org_unit_assignment_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_org_unit_assignment_kind
        CHECK (kind IN ('PRIMARY', 'SECONDARY', 'ACTING')),
    CONSTRAINT ck_ainer_org_unit_assignment_status
        CHECK (status IN ('ENABLED', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT ck_ainer_org_unit_assignment_period
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT ux_ainer_org_unit_assignment_identity
        UNIQUE (workspace_id, directory_id, id),
    CONSTRAINT ux_ainer_org_unit_assignment_anchor
        UNIQUE (workspace_id, directory_id, id, engagement_id, org_unit_id),
    CONSTRAINT fk_ainer_org_unit_assignment_engagement
        FOREIGN KEY (workspace_id, directory_id, engagement_id)
        REFERENCES ainer_org_engagement (workspace_id, directory_id, id),
    CONSTRAINT fk_ainer_org_unit_assignment_unit
        FOREIGN KEY (workspace_id, directory_id, org_unit_id)
        REFERENCES ainer_org_unit (workspace_id, directory_id, id)
);

-- 同一 Engagement 同时最多一个未闭合 PRIMARY（ADR-0042 §4.5）
CREATE UNIQUE INDEX ux_ainer_org_unit_assignment_open_primary
    ON ainer_org_unit_assignment (engagement_id)
    WHERE kind = 'PRIMARY' AND status = 'ENABLED' AND valid_until IS NULL;

CREATE INDEX idx_ainer_org_unit_assignment_engagement
    ON ainer_org_unit_assignment (engagement_id, status, valid_from);

CREATE TABLE ainer_org_position (
    id UUID NOT NULL DEFAULT uuidv7(),
    workspace_id UUID NOT NULL,
    directory_id UUID NOT NULL,
    org_unit_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_org_position PRIMARY KEY (id),
    CONSTRAINT ck_ainer_org_position_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_org_position_status CHECK (status IN ('ENABLED', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT ck_ainer_org_position_code CHECK (btrim(code) <> ''),
    CONSTRAINT ux_ainer_org_position_unit_code UNIQUE (directory_id, org_unit_id, code),
    CONSTRAINT ux_ainer_org_position_identity UNIQUE (workspace_id, directory_id, id),
    CONSTRAINT fk_ainer_org_position_unit
        FOREIGN KEY (workspace_id, directory_id, org_unit_id)
        REFERENCES ainer_org_unit (workspace_id, directory_id, id)
);

CREATE TABLE ainer_org_position_assignment (
    id UUID NOT NULL DEFAULT uuidv7(),
    workspace_id UUID NOT NULL,
    directory_id UUID NOT NULL,
    position_id UUID NOT NULL,
    engagement_id UUID NOT NULL,
    unit_assignment_id UUID NOT NULL,
    org_unit_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_org_position_assignment PRIMARY KEY (id),
    CONSTRAINT ck_ainer_org_position_assignment_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_org_position_assignment_kind
        CHECK (kind IN ('PRIMARY', 'SECONDARY', 'ACTING')),
    CONSTRAINT ck_ainer_org_position_assignment_status
        CHECK (status IN ('ENABLED', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT ck_ainer_org_position_assignment_period
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT fk_ainer_org_position_assignment_position
        FOREIGN KEY (workspace_id, directory_id, position_id)
        REFERENCES ainer_org_position (workspace_id, directory_id, id),
    CONSTRAINT fk_ainer_org_position_assignment_engagement
        FOREIGN KEY (workspace_id, directory_id, engagement_id)
        REFERENCES ainer_org_engagement (workspace_id, directory_id, id),
    CONSTRAINT fk_ainer_org_position_assignment_unit_assignment
        FOREIGN KEY (workspace_id, directory_id, unit_assignment_id, engagement_id, org_unit_id)
        REFERENCES ainer_org_unit_assignment
            (workspace_id, directory_id, id, engagement_id, org_unit_id)
);

CREATE TABLE ainer_org_change_audit (
    id UUID NOT NULL DEFAULT uuidv7(),
    entity_type VARCHAR(32) NOT NULL,
    entity_id UUID NOT NULL,
    operation VARCHAR(32) NOT NULL,
    actor_issuer VARCHAR(256) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_org_change_audit PRIMARY KEY (id),
    CONSTRAINT ck_ainer_org_change_audit_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_org_change_audit_actor_type CHECK (actor_type IN ('USER', 'SERVICE')),
    CONSTRAINT ck_ainer_org_change_audit_entity
        CHECK (entity_type IN ('DIRECTORY', 'UNIT', 'ENGAGEMENT', 'UNIT_ASSIGNMENT',
            'POSITION', 'POSITION_ASSIGNMENT'))
);

CREATE INDEX idx_ainer_org_change_audit_entity
    ON ainer_org_change_audit (entity_type, entity_id, occurred_at DESC);
