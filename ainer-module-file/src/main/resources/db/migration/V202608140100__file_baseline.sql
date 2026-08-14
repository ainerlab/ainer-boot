-- File storage module baseline (ADR-0040): metadata for stored objects plus append-only change audit.
-- Business database: ainer (owned by ainer-server).

CREATE TABLE ainer_file_object (
    id UUID NOT NULL DEFAULT uuidv7(),
    storage_key VARCHAR(512) NOT NULL,
    namespace VARCHAR(128) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128),
    content_length BIGINT NOT NULL,
    checksum_sha256 CHAR(64),
    workspace_id UUID,
    uploaded_by_issuer VARCHAR(256) NOT NULL,
    uploaded_by_type VARCHAR(16) NOT NULL,
    uploaded_by_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_file_object PRIMARY KEY (id),
    CONSTRAINT ck_ainer_file_object_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_file_object_actor_type CHECK (uploaded_by_type IN ('USER', 'SERVICE')),
    CONSTRAINT ck_ainer_file_object_length CHECK (content_length >= 0),
    CONSTRAINT ck_ainer_file_object_filename CHECK (btrim(filename) <> ''),
    CONSTRAINT ux_ainer_file_object_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_ainer_file_object_namespace_created
    ON ainer_file_object (namespace, created_at DESC, id DESC);

CREATE TABLE ainer_file_audit (
    id UUID NOT NULL DEFAULT uuidv7(),
    file_id UUID,
    operation VARCHAR(32) NOT NULL,
    namespace VARCHAR(128) NOT NULL,
    actor_issuer VARCHAR(256) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_file_audit PRIMARY KEY (id),
    CONSTRAINT ck_ainer_file_audit_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_file_audit_operation CHECK (operation IN ('UPLOADED', 'DELETED')),
    CONSTRAINT ck_ainer_file_audit_actor_type CHECK (actor_type IN ('USER', 'SERVICE')),
    CONSTRAINT fk_ainer_file_audit_file
        FOREIGN KEY (file_id) REFERENCES ainer_file_object (id) ON DELETE SET NULL
);

CREATE INDEX idx_ainer_file_audit_file ON ainer_file_audit (file_id, occurred_at DESC);
CREATE INDEX idx_ainer_file_audit_namespace ON ainer_file_audit (namespace, occurred_at DESC, id DESC);
