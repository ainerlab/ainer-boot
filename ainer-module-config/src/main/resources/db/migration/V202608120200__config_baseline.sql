-- Ainer Config baseline (ADR-0038 P3 commercial-grade enterprise base).
-- Dynamic configuration with type-safe values, version history and encrypted secret support.

-- 1. Config entry — current value of a configuration key, scoped by namespace.
CREATE TABLE ainer_config_entry (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    namespace VARCHAR(128) NOT NULL,
    config_key VARCHAR(256) NOT NULL,
    config_value TEXT,
    value_type VARCHAR(16) NOT NULL DEFAULT 'STRING',
    is_secret BOOLEAN NOT NULL DEFAULT FALSE,
    encrypted_value TEXT,
    description VARCHAR(512),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_config_value_type
        CHECK (value_type IN ('STRING', 'INTEGER', 'LONG', 'DECIMAL', 'BOOLEAN', 'JSON')),
    CONSTRAINT ck_ainer_config_values
        CHECK (btrim(namespace) <> '' AND btrim(config_key) <> ''),
    -- Non-secret must have config_value; secret must have encrypted_value
    CONSTRAINT ck_ainer_config_secret_consistency
        CHECK ((NOT is_secret AND config_value IS NOT NULL) OR (is_secret AND encrypted_value IS NOT NULL))
);

-- Unique active key within namespace (one current value per key).
CREATE UNIQUE INDEX ux_ainer_config_entry_key
    ON ainer_config_entry (namespace, config_key);

CREATE INDEX idx_ainer_config_entry_namespace
    ON ainer_config_entry (namespace);

-- 2. Config history — append-only version history of every change.
CREATE TABLE ainer_config_history (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    entry_id UUID NOT NULL,
    namespace VARCHAR(128) NOT NULL,
    config_key VARCHAR(256) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    old_version BIGINT,
    new_version BIGINT,
    changed_by_issuer VARCHAR(256),
    changed_by_type VARCHAR(16),
    changed_by_id VARCHAR(256),
    changed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ainer_config_history_entry
        FOREIGN KEY (entry_id) REFERENCES ainer_config_entry (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_ainer_config_history_changed_by_type
        CHECK (changed_by_type IS NULL OR changed_by_type IN ('USER', 'SERVICE'))
);

CREATE INDEX idx_ainer_config_history_entry_time
    ON ainer_config_history (entry_id, changed_at DESC, id DESC);

CREATE INDEX idx_ainer_config_history_namespace
    ON ainer_config_history (namespace, changed_at DESC);
