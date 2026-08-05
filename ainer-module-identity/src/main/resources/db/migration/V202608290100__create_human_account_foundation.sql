-- Greenfield Identity foundation (ADR-0033 Greenfield §3-§4).
-- Additive during the S1.2 destructive slice: new HumanAccount / LoginIdentity baseline tables coexist
-- with the legacy IdentityUser / IdentityTenant schema until the cutover squashes the legacy history.
-- IDs are application-generated PostgreSQL UUIDv7 (no DEFAULT here), matching ADR-0020.

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
    CONSTRAINT ck_ainer_identity_login_identity_authority
        CHECK (btrim(provider_authority) <> '' AND btrim(normalized_identifier) <> ''),
    CONSTRAINT fk_ainer_identity_login_identity_account
        FOREIGN KEY (account_id) REFERENCES ainer_identity_human_account (id)
);

-- At most one ACTIVE binding per (type, providerAuthority, normalizedIdentifier); revoked bindings may
-- coexist with a fresh ACTIVE one (re-link produces a new binding after verification).
CREATE UNIQUE INDEX uq_ainer_identity_login_identity_active_binding
    ON ainer_identity_login_identity (type, provider_authority, normalized_identifier)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_ainer_identity_login_identity_account
    ON ainer_identity_login_identity (account_id);
