-- Greenfield ServicePrincipal foundation (ADR-0033 Greenfield §2.6).
-- Additive during the S1.1 destructive slice: the stable non-human principal and its OAuth client binding
-- coexist with the legacy service-client tables until the cutover squashes the legacy history.
--
-- A ServicePrincipal is the audit-stable identity of a non-human caller; an OAuth client_id is a rotatable
-- credential bound to it. Client rotation must not change the audit identity. IDs are application-generated
-- PostgreSQL UUIDv7 (no DEFAULT here), matching ADR-0020.

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
);

-- At most one ACTIVE binding per client_id at a time; retired bindings may coexist with a fresh ACTIVE one
-- (credential rotation retires the prior binding and inserts a new one).
CREATE UNIQUE INDEX uq_ainer_identity_oauth_client_binding_active_client
    ON ainer_identity_oauth_client_binding (client_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_ainer_identity_oauth_client_binding_principal
    ON ainer_identity_oauth_client_binding (principal_id);

CREATE INDEX idx_ainer_identity_oauth_client_binding_client
    ON ainer_identity_oauth_client_binding (client_id);
