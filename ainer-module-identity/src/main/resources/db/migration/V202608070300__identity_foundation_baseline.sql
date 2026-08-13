-- Ainer Foundation Identity baseline. The database is rebuilt from empty; no prior schema is upgraded.

CREATE TABLE ainer_identity_human_account (
    id UUID PRIMARY KEY,
    issuer VARCHAR(256) NOT NULL,
    realm VARCHAR(128),
    status VARCHAR(16) NOT NULL,
    security_epoch BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_identity_human_account_status
        CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED', 'CLOSED')),
    CONSTRAINT ck_ainer_identity_human_account_epoch
        CHECK (security_epoch >= 0),
    CONSTRAINT ck_ainer_identity_human_account_issuer
        CHECK (btrim(issuer) <> '')
);

CREATE INDEX idx_ainer_identity_human_account_authority
    ON ainer_identity_human_account (issuer, realm);

CREATE TABLE ainer_identity_login_identity (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    type VARCHAR(16) NOT NULL,
    provider_authority VARCHAR(256) NOT NULL,
    normalized_identifier VARCHAR(256) NOT NULL,
    status VARCHAR(16) NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    CONSTRAINT ck_ainer_identity_login_identity_type
        CHECK (type IN ('USERNAME', 'EMAIL', 'PHONE', 'WECHAT', 'OIDC', 'PASSKEY')),
    CONSTRAINT ck_ainer_identity_login_identity_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_ainer_identity_login_identity_time
        CHECK (linked_at >= verified_at),
    CONSTRAINT ck_ainer_identity_login_identity_values
        CHECK (btrim(provider_authority) <> '' AND btrim(normalized_identifier) <> ''),
    CONSTRAINT fk_ainer_identity_login_identity_account
        FOREIGN KEY (account_id) REFERENCES ainer_identity_human_account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE UNIQUE INDEX ux_ainer_identity_login_identity_active_binding
    ON ainer_identity_login_identity (type, provider_authority, normalized_identifier)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_ainer_identity_login_identity_account
    ON ainer_identity_login_identity (account_id);

CREATE TABLE ainer_identity_credential (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    type VARCHAR(32) NOT NULL,
    credential_data TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    rotated_at TIMESTAMPTZ,
    CONSTRAINT ck_ainer_identity_credential_type
        CHECK (type IN ('PASSWORD', 'WEBAUTHN_PUBLIC_KEY', 'OIDC_SUBJECT')),
    CONSTRAINT ck_ainer_identity_credential_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_ainer_identity_credential_data
        CHECK (btrim(credential_data) <> ''),
    CONSTRAINT ck_ainer_identity_credential_rotated
        CHECK (status <> 'ACTIVE' OR rotated_at IS NULL),
    CONSTRAINT fk_ainer_identity_credential_account
        FOREIGN KEY (account_id) REFERENCES ainer_identity_human_account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE UNIQUE INDEX ux_ainer_identity_credential_active_type
    ON ainer_identity_credential (account_id, type)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_ainer_identity_credential_account
    ON ainer_identity_credential (account_id);

CREATE TABLE ainer_identity_human_profile (
    account_id UUID PRIMARY KEY,
    display_name VARCHAR(128),
    avatar_url VARCHAR(1024),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_identity_human_profile_display_name
        CHECK (display_name IS NULL OR btrim(display_name) <> ''),
    CONSTRAINT ck_ainer_identity_human_profile_avatar_url
        CHECK (avatar_url IS NULL OR btrim(avatar_url) <> ''),
    CONSTRAINT fk_ainer_identity_human_profile_account
        FOREIGN KEY (account_id) REFERENCES ainer_identity_human_account (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE ainer_identity_service_principal (
    id UUID PRIMARY KEY,
    issuer VARCHAR(256) NOT NULL,
    realm VARCHAR(128),
    status VARCHAR(16) NOT NULL,
    security_epoch BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_identity_service_principal_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_ainer_identity_service_principal_epoch
        CHECK (security_epoch >= 0),
    CONSTRAINT ck_ainer_identity_service_principal_issuer
        CHECK (btrim(issuer) <> '')
);

CREATE INDEX idx_ainer_identity_service_principal_authority
    ON ainer_identity_service_principal (issuer, realm);

CREATE TABLE ainer_identity_oauth_client_binding (
    id UUID PRIMARY KEY,
    principal_id UUID NOT NULL,
    client_id VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    bound_at TIMESTAMPTZ NOT NULL,
    unbound_at TIMESTAMPTZ,
    CONSTRAINT ck_ainer_identity_oauth_client_binding_status
        CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT ck_ainer_identity_oauth_client_binding_active_unbound
        CHECK (status <> 'ACTIVE' OR unbound_at IS NULL),
    CONSTRAINT ck_ainer_identity_oauth_client_binding_client_id
        CHECK (btrim(client_id) <> ''),
    CONSTRAINT fk_ainer_identity_oauth_client_binding_principal
        FOREIGN KEY (principal_id) REFERENCES ainer_identity_service_principal (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE UNIQUE INDEX ux_ainer_identity_oauth_client_binding_active_client
    ON ainer_identity_oauth_client_binding (client_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_ainer_identity_oauth_client_binding_principal
    ON ainer_identity_oauth_client_binding (principal_id);

CREATE INDEX idx_ainer_identity_oauth_client_binding_client
    ON ainer_identity_oauth_client_binding (client_id);
