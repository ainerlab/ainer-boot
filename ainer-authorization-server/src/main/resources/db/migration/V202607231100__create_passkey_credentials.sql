CREATE TABLE user_entities (
    id VARCHAR(1000) PRIMARY KEY,
    name VARCHAR(320) NOT NULL UNIQUE,
    display_name VARCHAR(320) NOT NULL
);

CREATE TABLE user_credentials (
    credential_id VARCHAR(1000) PRIMARY KEY,
    user_entity_user_id VARCHAR(1000) NOT NULL REFERENCES user_entities(id) ON DELETE RESTRICT,
    public_key BYTEA NOT NULL,
    signature_count BIGINT NOT NULL,
    uv_initialized BOOLEAN NOT NULL,
    backup_eligible BOOLEAN NOT NULL,
    authenticator_transports VARCHAR(1000) NOT NULL,
    public_key_credential_type VARCHAR(100) NOT NULL,
    backup_state BOOLEAN NOT NULL,
    attestation_object BYTEA NOT NULL,
    attestation_client_data_json BYTEA NOT NULL,
    created TIMESTAMPTZ NOT NULL,
    last_used TIMESTAMPTZ NOT NULL,
    label VARCHAR(1000) NOT NULL,
    CONSTRAINT uq_user_credentials_id_user UNIQUE (credential_id, user_entity_user_id),
    CONSTRAINT ck_user_credentials_signature_count
        CHECK (signature_count >= 0)
);

CREATE INDEX idx_user_credentials_user
    ON user_credentials(user_entity_user_id);

CREATE TABLE ainer_passkey_credential (
    credential_id VARCHAR(1000) PRIMARY KEY,
    user_entity_user_id VARCHAR(1000) NOT NULL,
    subject_id UUID NOT NULL REFERENCES ainer_identity_user(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ainer_passkey_protocol_credential
        FOREIGN KEY (credential_id, user_entity_user_id)
        REFERENCES user_credentials(credential_id, user_entity_user_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_ainer_passkey_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_ainer_passkey_revocation_time
        CHECK (
            (status = 'ACTIVE' AND revoked_at IS NULL)
            OR (status = 'REVOKED' AND revoked_at IS NOT NULL)
        ),
    CONSTRAINT ck_ainer_passkey_version
        CHECK (version >= 0)
);

CREATE INDEX idx_ainer_passkey_subject_status
    ON ainer_passkey_credential(subject_id, status);

CREATE INDEX idx_ainer_passkey_user_status
    ON ainer_passkey_credential(user_entity_user_id, status);

CREATE TABLE ainer_passkey_credential_audit (
    id UUID PRIMARY KEY,
    credential_id VARCHAR(1000) NOT NULL,
    user_entity_user_id VARCHAR(1000) NOT NULL,
    subject_id UUID NOT NULL,
    operation VARCHAR(16) NOT NULL,
    request_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_passkey_audit_operation
        CHECK (operation IN ('REGISTERED', 'REVOKED'))
);

CREATE INDEX idx_ainer_passkey_audit_subject_time
    ON ainer_passkey_credential_audit(subject_id, occurred_at, id);

CREATE INDEX idx_ainer_passkey_audit_credential_time
    ON ainer_passkey_credential_audit(credential_id, occurred_at, id);
