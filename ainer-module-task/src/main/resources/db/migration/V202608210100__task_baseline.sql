-- Task scheduling module baseline (ADR-0047): delayed/periodic execution with
-- SKIP LOCKED queue claiming, exponential backoff retry, and append-only audit.

CREATE TABLE ainer_task_definition (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    task_type VARCHAR(128) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    handler_ref VARCHAR(256) NOT NULL,
    max_attempts INT NOT NULL DEFAULT 3,
    timeout_seconds INT NOT NULL DEFAULT 300,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_task_definition_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_task_definition_status CHECK (status IN ('ACTIVE', 'PAUSED')),
    CONSTRAINT ck_ainer_task_definition_type CHECK (btrim(task_type) <> ''),
    CONSTRAINT ck_ainer_task_definition_attempts CHECK (max_attempts BETWEEN 1 AND 20),
    CONSTRAINT ck_ainer_task_definition_timeout CHECK (timeout_seconds BETWEEN 1 AND 86400),
    CONSTRAINT ux_ainer_task_definition_type UNIQUE (task_type)
);

CREATE TABLE ainer_task_job (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    task_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL,
    next_run_at TIMESTAMPTZ NOT NULL,
    interval_seconds BIGINT,
    locked_by VARCHAR(128),
    locked_at TIMESTAMPTZ,
    last_error VARCHAR(512),
    created_by_issuer VARCHAR(256) NOT NULL,
    created_by_type VARCHAR(16) NOT NULL,
    created_by_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_ainer_task_job_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_task_job_status CHECK (status IN
        ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'EXHAUSTED', 'CANCELLED')),
    CONSTRAINT ck_ainer_task_job_actor CHECK (created_by_type IN ('USER', 'SERVICE')),
    CONSTRAINT ck_ainer_task_job_attempts CHECK (max_attempts BETWEEN 1 AND 20),
    CONSTRAINT fk_ainer_task_job_definition
        FOREIGN KEY (task_type) REFERENCES ainer_task_definition (task_type)
);

-- SKIP LOCKED 领取索引：只取到期 PENDING
CREATE INDEX idx_ainer_task_job_ready
    ON ainer_task_job (next_run_at) WHERE status = 'PENDING';

CREATE INDEX idx_ainer_task_job_status_type
    ON ainer_task_job (status, task_type, created_at DESC);

CREATE INDEX idx_ainer_task_job_locked
    ON ainer_task_job (locked_at) WHERE status = 'RUNNING';

CREATE TABLE ainer_task_audit (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    job_id UUID NOT NULL,
    event VARCHAR(32) NOT NULL,
    attempt INT,
    actor_issuer VARCHAR(256),
    actor_type VARCHAR(16),
    actor_id VARCHAR(128),
    detail VARCHAR(512),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_task_audit_id_v7 CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_task_audit_event CHECK (event IN
        ('SUBMITTED', 'CLAIMED', 'SUCCEEDED', 'FAILED', 'RETRY_SCHEDULED',
         'EXHAUSTED', 'CANCELLED', 'PAUSED', 'RESUMED')),
    CONSTRAINT ck_ainer_task_audit_actor CHECK (actor_type IS NULL OR actor_type IN ('USER', 'SERVICE')),
    CONSTRAINT fk_ainer_task_audit_job
        FOREIGN KEY (job_id) REFERENCES ainer_task_job (id)
);

CREATE INDEX idx_ainer_task_audit_job
    ON ainer_task_audit (job_id, occurred_at DESC);
