CREATE TABLE ainer_identity_tenant (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_identity_tenant_code
        CHECK (code ~ '^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$'),
    CONSTRAINT ck_ainer_identity_tenant_name
        CHECK (char_length(btrim(name)) BETWEEN 2 AND 80),
    CONSTRAINT ck_ainer_identity_tenant_status
        CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),
    CONSTRAINT ck_ainer_identity_tenant_time
        CHECK (updated_at >= created_at)
);

CREATE TABLE ainer_identity_user (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_identity_user_username
        CHECK (username ~ '^[a-z0-9][a-z0-9._@-]{2,99}$'),
    CONSTRAINT ck_ainer_identity_user_password_hash
        CHECK (char_length(password_hash) BETWEEN 20 AND 255),
    CONSTRAINT ck_ainer_identity_user_display_name
        CHECK (char_length(btrim(display_name)) BETWEEN 1 AND 80),
    CONSTRAINT ck_ainer_identity_user_status
        CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),
    CONSTRAINT ck_ainer_identity_user_time
        CHECK (updated_at >= created_at)
);

CREATE TABLE ainer_identity_membership (
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(16) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, user_id),
    CONSTRAINT fk_ainer_identity_membership_tenant
        FOREIGN KEY (tenant_id) REFERENCES ainer_identity_tenant (id) ON DELETE CASCADE,
    CONSTRAINT fk_ainer_identity_membership_user
        FOREIGN KEY (user_id) REFERENCES ainer_identity_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_ainer_identity_membership_role
        CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    CONSTRAINT ck_ainer_identity_membership_status
        CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED'))
);

CREATE UNIQUE INDEX ux_ainer_identity_membership_default
    ON ainer_identity_membership (user_id)
    WHERE is_default = true;

CREATE INDEX idx_ainer_identity_membership_tenant
    ON ainer_identity_membership (tenant_id, status, user_id);
