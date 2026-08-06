-- Greenfield Identity foundation credential store (ADR-0033 Greenfield §4, execution plan 缺口 A).
-- Additive during the S1.x destructive slice: password material / WebAuthn public key reference / OIDC
-- subject now has an owner table, while LoginIdentity keeps only identifier-binding semantics. The
-- nullable credential data stays out of LoginIdentity so a binding never leaks secret material.
-- IDs are application-generated PostgreSQL UUIDv7 (no DEFAULT here), matching ADR-0020 and the existing
-- human_account / login_identity baseline.

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
);

-- At most one ACTIVE credential material per (account, type); rotation revokes the prior one before a
-- new ACTIVE material is inserted. REVOKED material may coexist with a fresh ACTIVE one for audit.
CREATE UNIQUE INDEX ux_ainer_identity_credential_active_type
    ON ainer_identity_credential (account_id, type)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_ainer_identity_credential_account
    ON ainer_identity_credential (account_id);

-- Human profile is a 0:1 attribute of a HumanAccount. account_id is the natural primary key: a profile
-- never exists without its account, and an account carries at most one profile row.
CREATE TABLE ainer_identity_human_profile (
    account_id UUID NOT NULL,
    display_name VARCHAR(128),
    avatar_url VARCHAR(1024),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ainer_identity_human_profile PRIMARY KEY (account_id),
    CONSTRAINT ck_ainer_identity_human_profile_display_name
        CHECK (display_name IS NULL OR btrim(display_name) <> ''),
    CONSTRAINT ck_ainer_identity_human_profile_avatar_url
        CHECK (avatar_url IS NULL OR btrim(avatar_url) <> ''),
    CONSTRAINT fk_ainer_identity_human_profile_account
        FOREIGN KEY (account_id) REFERENCES ainer_identity_human_account (id)
);