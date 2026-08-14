-- Dictionary change audit (ADR-0040 management hardening). Append-only; same-transaction inserts.
-- Business database: ainer (owned by ainer-server).

CREATE TABLE ainer_dictionary_audit (
    id UUID NOT NULL DEFAULT uuidv7(),
    operation VARCHAR(32) NOT NULL,
    target_kind VARCHAR(8) NOT NULL,
    target_id UUID NOT NULL,
    actor_issuer VARCHAR(256) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128),
    detail VARCHAR(512),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_dictionary_audit PRIMARY KEY (id),
    CONSTRAINT ck_ainer_dictionary_audit_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_dictionary_audit_operation CHECK (operation IN (
        'TYPE_CREATED', 'TYPE_UPDATED', 'TYPE_STATUS_CHANGED',
        'ITEM_CREATED', 'ITEM_UPDATED', 'ITEM_STATUS_CHANGED')),
    CONSTRAINT ck_ainer_dictionary_audit_target_kind CHECK (target_kind IN ('TYPE', 'ITEM')),
    CONSTRAINT ck_ainer_dictionary_audit_actor_type CHECK (actor_type IN ('USER', 'SERVICE'))
);

CREATE INDEX idx_ainer_dictionary_audit_target
    ON ainer_dictionary_audit (target_kind, target_id, occurred_at DESC);
