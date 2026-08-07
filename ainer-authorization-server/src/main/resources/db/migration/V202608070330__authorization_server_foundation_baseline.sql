-- OAuth protocol, browser client and account-bound Passkey baseline.

CREATE TABLE oauth2_registered_client (
    id VARCHAR(100) PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    client_id_issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_secret VARCHAR(200),
    client_secret_expires_at TIMESTAMPTZ,
    client_name VARCHAR(200) NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types VARCHAR(1000) NOT NULL,
    redirect_uris VARCHAR(1000),
    post_logout_redirect_uris VARCHAR(1000),
    scopes VARCHAR(1000) NOT NULL,
    client_settings VARCHAR(2000) NOT NULL,
    token_settings VARCHAR(2000) NOT NULL
);

CREATE UNIQUE INDEX ux_oauth2_registered_client_client_id
    ON oauth2_registered_client (client_id);

CREATE TABLE oauth2_authorization (
    id VARCHAR(100) PRIMARY KEY,
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes VARCHAR(1000),
    attributes TEXT,
    state VARCHAR(500),
    authorization_code_value TEXT,
    authorization_code_issued_at TIMESTAMPTZ,
    authorization_code_expires_at TIMESTAMPTZ,
    authorization_code_metadata TEXT,
    access_token_value TEXT,
    access_token_issued_at TIMESTAMPTZ,
    access_token_expires_at TIMESTAMPTZ,
    access_token_metadata TEXT,
    access_token_type VARCHAR(100),
    access_token_scopes VARCHAR(1000),
    oidc_id_token_value TEXT,
    oidc_id_token_issued_at TIMESTAMPTZ,
    oidc_id_token_expires_at TIMESTAMPTZ,
    oidc_id_token_metadata TEXT,
    refresh_token_value TEXT,
    refresh_token_issued_at TIMESTAMPTZ,
    refresh_token_expires_at TIMESTAMPTZ,
    refresh_token_metadata TEXT,
    user_code_value TEXT,
    user_code_issued_at TIMESTAMPTZ,
    user_code_expires_at TIMESTAMPTZ,
    user_code_metadata TEXT,
    device_code_value TEXT,
    device_code_issued_at TIMESTAMPTZ,
    device_code_expires_at TIMESTAMPTZ,
    device_code_metadata TEXT
);

CREATE INDEX idx_oauth2_authorization_client_principal
    ON oauth2_authorization (registered_client_id, principal_name);

CREATE INDEX idx_oauth2_authorization_state
    ON oauth2_authorization (state)
    WHERE state IS NOT NULL;

CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorities VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

CREATE TABLE ainer_oauth_browser_client (
    registered_client_id VARCHAR(100) PRIMARY KEY REFERENCES oauth2_registered_client (id),
    client_id VARCHAR(100) NOT NULL UNIQUE,
    client_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    replaces_client_id VARCHAR(100),
    created_by_service_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    retired_by_service_id VARCHAR(128),
    retired_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_ainer_oauth_browser_client_status
        CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT ck_ainer_oauth_browser_client_retirement
        CHECK ((status = 'ACTIVE' AND retired_by_service_id IS NULL AND retired_at IS NULL)
            OR (status = 'RETIRED' AND retired_by_service_id IS NOT NULL AND retired_at IS NOT NULL)),
    CONSTRAINT fk_ainer_oauth_browser_client_replaces
        FOREIGN KEY (replaces_client_id) REFERENCES ainer_oauth_browser_client (client_id)
);

CREATE INDEX idx_ainer_oauth_browser_client_status
    ON ainer_oauth_browser_client (status, created_at DESC);

CREATE TABLE ainer_oauth_browser_client_audit (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    operation VARCHAR(32) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    related_client_id VARCHAR(100),
    actor_service_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    change_reference VARCHAR(200) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_oauth_browser_client_audit_operation
        CHECK (operation IN ('CREATED', 'ROTATED', 'RETIRED'))
);

CREATE INDEX idx_ainer_oauth_browser_client_audit_client_time
    ON ainer_oauth_browser_client_audit (client_id, occurred_at DESC);

CREATE INDEX idx_ainer_oauth_browser_client_audit_actor_time
    ON ainer_oauth_browser_client_audit (actor_service_id, occurred_at DESC);

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
    CONSTRAINT ck_user_credentials_signature_count CHECK (signature_count >= 0)
);

CREATE INDEX idx_user_credentials_user ON user_credentials (user_entity_user_id);

CREATE TABLE ainer_passkey_credential (
    credential_id VARCHAR(1000) PRIMARY KEY,
    user_entity_user_id VARCHAR(1000) NOT NULL,
    account_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ainer_passkey_account
        FOREIGN KEY (account_id) REFERENCES ainer_identity_human_account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_ainer_passkey_protocol_credential
        FOREIGN KEY (credential_id, user_entity_user_id)
        REFERENCES user_credentials (credential_id, user_entity_user_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_ainer_passkey_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_ainer_passkey_revocation_time
        CHECK ((status = 'ACTIVE' AND revoked_at IS NULL)
            OR (status = 'REVOKED' AND revoked_at IS NOT NULL)),
    CONSTRAINT ck_ainer_passkey_version CHECK (version >= 0)
);

CREATE INDEX idx_ainer_passkey_account_status
    ON ainer_passkey_credential (account_id, status);

CREATE INDEX idx_ainer_passkey_user_status
    ON ainer_passkey_credential (user_entity_user_id, status);

CREATE TABLE ainer_passkey_credential_audit (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    credential_id VARCHAR(1000) NOT NULL,
    user_entity_user_id VARCHAR(1000) NOT NULL,
    account_id UUID NOT NULL REFERENCES ainer_identity_human_account (id) ON DELETE RESTRICT,
    operation VARCHAR(16) NOT NULL,
    request_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_passkey_audit_operation CHECK (operation IN ('REGISTERED', 'REVOKED'))
);

CREATE INDEX idx_ainer_passkey_audit_account_time
    ON ainer_passkey_credential_audit (account_id, occurred_at, id);

CREATE INDEX idx_ainer_passkey_audit_credential_time
    ON ainer_passkey_credential_audit (credential_id, occurred_at, id);

CREATE TABLE ainer_passkey_recovery_code (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id UUID NOT NULL REFERENCES ainer_identity_human_account (id) ON DELETE RESTRICT,
    code_hash VARCHAR(100) NOT NULL,
    status VARCHAR(12) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    CONSTRAINT ck_ainer_passkey_recovery_code_status
        CHECK (status IN ('ACTIVE', 'USED', 'SUPERSEDED')),
    CONSTRAINT ck_ainer_passkey_recovery_code_used_time
        CHECK ((status = 'ACTIVE' AND used_at IS NULL)
            OR (status IN ('USED', 'SUPERSEDED') AND used_at IS NOT NULL))
);

CREATE INDEX idx_ainer_passkey_recovery_code_account_status
    ON ainer_passkey_recovery_code (account_id, status);

CREATE TABLE ainer_passkey_recovery_lockout (
    account_id UUID PRIMARY KEY REFERENCES ainer_identity_human_account (id) ON DELETE RESTRICT,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_passkey_recovery_lockout_attempts CHECK (failed_attempts >= 0)
);

CREATE TABLE ainer_passkey_recovery_request (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id UUID NOT NULL REFERENCES ainer_identity_human_account (id) ON DELETE RESTRICT,
    requested_by VARCHAR(128) NOT NULL,
    approved_by VARCHAR(128),
    incident_reference VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    executed_at TIMESTAMPTZ,
    CONSTRAINT ck_ainer_passkey_recovery_request_status
        CHECK (status IN ('REQUESTED', 'EXECUTED', 'EXPIRED')),
    CONSTRAINT ck_ainer_passkey_recovery_request_reference
        CHECK (incident_reference ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_passkey_recovery_request_actor
        CHECK (requested_by ~ '^[A-Za-z0-9._:@/-]{1,128}$'
            AND (approved_by IS NULL OR approved_by ~ '^[A-Za-z0-9._:@/-]{1,128}$')),
    CONSTRAINT ck_ainer_passkey_recovery_request_expiry CHECK (expires_at > requested_at),
    CONSTRAINT ck_ainer_passkey_recovery_request_execution
        CHECK ((status IN ('REQUESTED', 'EXPIRED') AND approved_by IS NULL AND executed_at IS NULL)
            OR (status = 'EXECUTED' AND approved_by IS NOT NULL
                AND approved_by <> requested_by AND executed_at IS NOT NULL))
);

CREATE UNIQUE INDEX ux_ainer_passkey_recovery_request_open_account
    ON ainer_passkey_recovery_request (account_id)
    WHERE status = 'REQUESTED';

CREATE INDEX idx_ainer_passkey_recovery_request_time
    ON ainer_passkey_recovery_request (requested_at DESC, id DESC);

CREATE TABLE ainer_passkey_security_operation_audit (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    operation_id UUID NOT NULL,
    account_id UUID NOT NULL REFERENCES ainer_identity_human_account (id) ON DELETE RESTRICT,
    operation_type VARCHAR(32) NOT NULL,
    phase VARCHAR(16) NOT NULL,
    actor_type VARCHAR(8) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    incident_reference VARCHAR(128),
    request_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_passkey_security_audit_type
        CHECK (operation_type IN ('RECOVERY_CODE_ISSUED', 'SELF_RECOVERY', 'ADMIN_RECOVERY',
                                  'ENROLLMENT_GRANT')),
    CONSTRAINT ck_ainer_passkey_security_audit_phase
        CHECK (phase IN ('ISSUED', 'REDEEMED', 'REQUESTED', 'EXECUTED', 'GRANTED', 'REVOKED')),
    CONSTRAINT ck_ainer_passkey_security_audit_actor
        CHECK (actor_type IN ('USER', 'SERVICE')
            AND actor_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_passkey_security_audit_reference
        CHECK (incident_reference IS NULL OR incident_reference ~ '^[A-Za-z0-9._:@/-]{1,128}$')
);

CREATE UNIQUE INDEX ux_ainer_passkey_security_audit_phase
    ON ainer_passkey_security_operation_audit (operation_id, phase);

CREATE INDEX idx_ainer_passkey_security_audit_account_time
    ON ainer_passkey_security_operation_audit (account_id, occurred_at, id);

CREATE TABLE ainer_passkey_enrollment_grant (
    account_id UUID PRIMARY KEY REFERENCES ainer_identity_human_account (id) ON DELETE RESTRICT,
    granted_by VARCHAR(128) NOT NULL,
    incident_reference VARCHAR(128) NOT NULL,
    status VARCHAR(12) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT ck_ainer_passkey_enrollment_status
        CHECK (status IN ('ACTIVE', 'CONSUMED', 'REVOKED')),
    CONSTRAINT ck_ainer_passkey_enrollment_time
        CHECK ((status IN ('ACTIVE', 'REVOKED') AND consumed_at IS NULL)
            OR (status = 'CONSUMED' AND consumed_at IS NOT NULL)),
    CONSTRAINT ck_ainer_passkey_enrollment_actor
        CHECK (granted_by ~ '^[A-Za-z0-9._:@/-]{1,128}$'
            AND incident_reference ~ '^[A-Za-z0-9._:@/-]{1,128}$')
);

CREATE INDEX idx_ainer_passkey_enrollment_account_status
    ON ainer_passkey_enrollment_grant (account_id, status);
