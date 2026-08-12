-- Ainer Dictionary baseline (ADR-0038 P3 commercial-grade enterprise base).
-- Tree-structured dictionary types, multilingual items with caching support.
-- The database is rebuilt from empty; no prior schema is upgraded.

-- 1. Dictionary type — tree-structured classification (e.g. "gender", "order_status", "industry").
--    Supports unlimited nesting via parent_id self-reference.
CREATE TABLE ainer_dictionary_type (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    code VARCHAR(128) NOT NULL,
    parent_id UUID,
    name VARCHAR(256) NOT NULL,
    name_en VARCHAR(256),
    description VARCHAR(512),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sort_index INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ainer_dictionary_type_parent
        FOREIGN KEY (parent_id) REFERENCES ainer_dictionary_type (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_ainer_dictionary_type_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_ainer_dictionary_type_values
        CHECK (btrim(code) <> '' AND btrim(name) <> '')
);

-- Unique active code within the same parent (null parent = root level).
CREATE UNIQUE INDEX ux_ainer_dictionary_type_code_active
    ON ainer_dictionary_type (parent_id, code)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_ainer_dictionary_type_parent
    ON ainer_dictionary_type (parent_id);

-- 2. Dictionary item — individual entries within a type (e.g. code="MALE", label="男"/"Male").
CREATE TABLE ainer_dictionary_item (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    type_id UUID NOT NULL,
    code VARCHAR(128) NOT NULL,
    label VARCHAR(256) NOT NULL,
    label_en VARCHAR(256),
    value VARCHAR(512),
    sort_index INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    css_class VARCHAR(128),
    remark VARCHAR(512),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ainer_dictionary_item_type
        FOREIGN KEY (type_id) REFERENCES ainer_dictionary_type (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_ainer_dictionary_item_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_ainer_dictionary_item_values
        CHECK (btrim(code) <> '' AND btrim(label) <> '')
);

-- Unique active code within a type.
CREATE UNIQUE INDEX ux_ainer_dictionary_item_code_active
    ON ainer_dictionary_item (type_id, code)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_ainer_dictionary_item_type
    ON ainer_dictionary_item (type_id, sort_index, code);
