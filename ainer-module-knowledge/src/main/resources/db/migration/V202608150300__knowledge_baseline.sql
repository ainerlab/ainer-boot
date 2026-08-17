-- Knowledge Foundation baseline (ADR-0044 K1/K2): stable semantic identity, immutable
-- revisions with lineage, typed sources/evidence, append-only lifecycle events.

CREATE TABLE ainer_knowledge_object (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    workspace_id UUID NOT NULL,
    kind VARCHAR(128) NOT NULL,
    title VARCHAR(256) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_knowledge_object_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_knowledge_object_status CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT ck_ainer_knowledge_object_kind CHECK (btrim(kind) <> '')
);

CREATE TABLE ainer_knowledge_revision (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    object_id UUID NOT NULL,
    revision_number BIGINT NOT NULL,
    payload_markdown TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by_issuer VARCHAR(256) NOT NULL,
    created_by_type VARCHAR(16) NOT NULL,
    created_by_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT ck_ainer_knowledge_revision_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_knowledge_revision_status
        CHECK (status IN ('PROPOSED', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_ainer_knowledge_revision_actor CHECK (created_by_type IN ('USER', 'SERVICE')),
    CONSTRAINT ck_ainer_knowledge_revision_payload CHECK (btrim(payload_markdown) <> ''),
    CONSTRAINT ux_ainer_knowledge_revision_number UNIQUE (object_id, revision_number),
    CONSTRAINT fk_ainer_knowledge_revision_object
        FOREIGN KEY (object_id) REFERENCES ainer_knowledge_object (id)
);

CREATE INDEX idx_ainer_knowledge_revision_object
    ON ainer_knowledge_revision (object_id, revision_number DESC);

CREATE TABLE ainer_knowledge_revision_lineage (
    from_revision_id UUID NOT NULL,
    to_revision_id UUID NOT NULL,
    relation VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_knowledge_revision_lineage PRIMARY KEY (from_revision_id, to_revision_id),
    CONSTRAINT ck_ainer_knowledge_revision_lineage_relation CHECK (relation = 'SUPERSEDES'),
    CONSTRAINT fk_ainer_knowledge_revision_lineage_from
        FOREIGN KEY (from_revision_id) REFERENCES ainer_knowledge_revision (id),
    CONSTRAINT fk_ainer_knowledge_revision_lineage_to
        FOREIGN KEY (to_revision_id) REFERENCES ainer_knowledge_revision (id),
    CONSTRAINT ck_ainer_knowledge_revision_lineage_no_self
        CHECK (from_revision_id <> to_revision_id)
);

CREATE TABLE ainer_knowledge_source (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    revision_id UUID NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_ref VARCHAR(512) NOT NULL,
    CONSTRAINT ck_ainer_knowledge_source_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT fk_ainer_knowledge_source_revision
        FOREIGN KEY (revision_id) REFERENCES ainer_knowledge_revision (id)
);

CREATE TABLE ainer_knowledge_evidence (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    revision_id UUID NOT NULL,
    link_type VARCHAR(16) NOT NULL,
    target_ref VARCHAR(512) NOT NULL,
    CONSTRAINT ck_ainer_knowledge_evidence_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_knowledge_evidence_link CHECK (link_type IN ('SUPPORTS', 'CONTRADICTS')),
    CONSTRAINT fk_ainer_knowledge_evidence_revision
        FOREIGN KEY (revision_id) REFERENCES ainer_knowledge_revision (id)
);

-- Append-only：无 UPDATE/DELETE 路径（应用层不提供，物理删除不发生）
CREATE TABLE ainer_knowledge_lifecycle_event (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    object_id UUID NOT NULL,
    revision_id UUID,
    event VARCHAR(16) NOT NULL,
    actor_issuer VARCHAR(256) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_knowledge_lifecycle_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_knowledge_lifecycle_event
        CHECK (event IN ('OBJECT_CREATED', 'PROPOSED', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_ainer_knowledge_lifecycle_actor CHECK (actor_type IN ('USER', 'SERVICE'))
);

CREATE INDEX idx_ainer_knowledge_lifecycle_object
    ON ainer_knowledge_lifecycle_event (object_id, occurred_at DESC);
