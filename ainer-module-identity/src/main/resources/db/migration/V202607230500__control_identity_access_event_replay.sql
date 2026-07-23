CREATE TABLE ainer_identity_access_event_replay_request (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    approved_by VARCHAR(128),
    incident_reference VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    executed_at TIMESTAMPTZ,
    CONSTRAINT fk_ainer_identity_replay_event
        FOREIGN KEY (event_id) REFERENCES ainer_identity_access_event (id),
    CONSTRAINT ck_ainer_identity_replay_requested_by
        CHECK (requested_by ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_identity_replay_approved_by
        CHECK (approved_by IS NULL OR approved_by ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_identity_replay_incident
        CHECK (incident_reference ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_identity_replay_status
        CHECK (status IN ('REQUESTED', 'EXECUTED', 'EXPIRED')),
    CONSTRAINT ck_ainer_identity_replay_time
        CHECK (expires_at > requested_at),
    CONSTRAINT ck_ainer_identity_replay_execution
        CHECK (
            (status IN ('REQUESTED', 'EXPIRED') AND approved_by IS NULL AND executed_at IS NULL)
            OR (
                status = 'EXECUTED'
                AND approved_by IS NOT NULL
                AND approved_by <> requested_by
                AND executed_at IS NOT NULL
            )
        )
);

CREATE UNIQUE INDEX uk_ainer_identity_replay_open_event
    ON ainer_identity_access_event_replay_request (event_id)
    WHERE status = 'REQUESTED';

CREATE INDEX idx_ainer_identity_replay_tenant_time
    ON ainer_identity_access_event_replay_request (tenant_id, requested_at DESC, id DESC);

CREATE TABLE ainer_identity_security_operation_audit (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    target_id UUID NOT NULL,
    operation_type VARCHAR(48) NOT NULL,
    phase VARCHAR(24) NOT NULL,
    actor_service_id VARCHAR(128) NOT NULL,
    incident_reference VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_identity_security_operation_type
        CHECK (operation_type = 'ACCESS_EVENT_REPLAY'),
    CONSTRAINT ck_ainer_identity_security_operation_phase
        CHECK (phase IN ('REQUESTED', 'EXECUTED')),
    CONSTRAINT ck_ainer_identity_security_operation_actor
        CHECK (actor_service_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_identity_security_operation_incident
        CHECK (incident_reference ~ '^[A-Za-z0-9._:@/-]{1,128}$')
);

CREATE UNIQUE INDEX uk_ainer_identity_security_operation_phase
    ON ainer_identity_security_operation_audit (operation_id, phase);

CREATE INDEX idx_ainer_identity_security_operation_tenant_time
    ON ainer_identity_security_operation_audit (tenant_id, occurred_at DESC, id DESC);
