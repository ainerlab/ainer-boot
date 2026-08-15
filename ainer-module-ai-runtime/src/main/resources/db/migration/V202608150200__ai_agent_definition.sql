-- Agent definition registry (ADR-0043 A1): agents are authorization participants, not
-- credentials. Owned by AI Runtime; the authorization module reads status through the
-- AgentDefinitionStatusResolver port. workspace_id is nullable (platform-level agent).

CREATE TABLE ainer_ai_agent_definition (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    code VARCHAR(64) NOT NULL,
    agent_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    purpose VARCHAR(256) NOT NULL,
    runtime_ref VARCHAR(256),
    workspace_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    retired_at TIMESTAMPTZ,
    CONSTRAINT ck_ainer_ai_agent_definition_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_ai_agent_definition_status CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT ck_ainer_ai_agent_definition_code CHECK (btrim(code) <> ''),
    CONSTRAINT ux_ainer_ai_agent_definition_code_version UNIQUE (code, agent_version)
);

CREATE INDEX idx_ainer_ai_agent_definition_status
    ON ainer_ai_agent_definition (status, code);
