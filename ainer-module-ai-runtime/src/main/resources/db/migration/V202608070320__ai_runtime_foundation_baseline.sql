-- AI runtime baseline. Subject is the usage and authorization attribution key.

CREATE TABLE ainer_ai_invocation (
    id UUID PRIMARY KEY,
    subject_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    requested_model VARCHAR(128) NOT NULL,
    resolved_model VARCHAR(128) NOT NULL,
    streaming BOOLEAN NOT NULL,
    status VARCHAR(16) NOT NULL,
    policy_decision VARCHAR(40) NOT NULL,
    prompt_fingerprint CHAR(64) NOT NULL,
    input_tokens INTEGER,
    output_tokens INTEGER,
    usage_estimated BOOLEAN NOT NULL DEFAULT false,
    estimated_cost NUMERIC(20, 8) NOT NULL,
    actual_cost NUMERIC(20, 8),
    currency CHAR(3) NOT NULL,
    latency_ms BIGINT,
    provider_request_id VARCHAR(160),
    error_code VARCHAR(96),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    task_run_id UUID,
    CONSTRAINT ck_ainer_ai_invocation_status
        CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED', 'REJECTED')),
    CONSTRAINT ck_ainer_ai_invocation_policy
        CHECK (policy_decision IN (
            'ALLOWED', 'REJECTED_MODEL', 'REJECTED_PROMPT_SIZE',
            'REJECTED_SENSITIVE_DATA', 'REJECTED_RATE_LIMIT', 'REJECTED_BUDGET'
        )),
    CONSTRAINT ck_ainer_ai_invocation_tokens
        CHECK ((input_tokens IS NULL OR input_tokens >= 0)
            AND (output_tokens IS NULL OR output_tokens >= 0)),
    CONSTRAINT ck_ainer_ai_invocation_cost
        CHECK (estimated_cost >= 0 AND (actual_cost IS NULL OR actual_cost >= 0)),
    CONSTRAINT ck_ainer_ai_invocation_latency
        CHECK (latency_ms IS NULL OR latency_ms >= 0),
    CONSTRAINT ck_ainer_ai_invocation_time
        CHECK (completed_at IS NULL OR completed_at >= started_at),
    CONSTRAINT ck_ainer_ai_invocation_completion
        CHECK ((status = 'STARTED' AND completed_at IS NULL)
            OR (status <> 'STARTED' AND completed_at IS NOT NULL))
);

CREATE INDEX idx_ainer_ai_invocation_subject_started
    ON ainer_ai_invocation (subject_id, started_at DESC, id DESC);

CREATE INDEX idx_ainer_ai_invocation_budget
    ON ainer_ai_invocation (subject_id, started_at)
    INCLUDE (status, estimated_cost, actual_cost)
    WHERE status IN ('STARTED', 'SUCCEEDED', 'FAILED');

CREATE INDEX idx_ainer_ai_invocation_request
    ON ainer_ai_invocation (request_id);

CREATE TABLE ainer_ai_task (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    workspace_id UUID,
    task_type VARCHAR(64) NOT NULL,
    target_identity_id UUID,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    trigger VARCHAR(16) NOT NULL DEFAULT 'manual',
    triggered_by VARCHAR(128) NOT NULL,
    policy_version VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_ai_task_status
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_ainer_ai_task_trigger
        CHECK (trigger IN ('manual', 'scheduled', 'event'))
);

CREATE INDEX idx_ainer_ai_task_status
    ON ainer_ai_task (status, created_at DESC, id DESC);

CREATE INDEX idx_ainer_ai_task_identity
    ON ainer_ai_task (target_identity_id, created_at DESC)
    WHERE target_identity_id IS NOT NULL;

CREATE TABLE ainer_ai_context_snapshot (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    identity_id UUID,
    identity_version_id UUID,
    evidence_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    memory_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    as_of TIMESTAMPTZ NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_ainer_ai_context_snapshot_identity
    ON ainer_ai_context_snapshot (identity_id, as_of DESC)
    WHERE identity_id IS NOT NULL;

CREATE TABLE ainer_ai_task_run (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    task_id UUID NOT NULL,
    context_snapshot_id UUID NOT NULL,
    governed_context JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_ainer_ai_task_run_task
        FOREIGN KEY (task_id) REFERENCES ainer_ai_task (id) ON DELETE CASCADE,
    CONSTRAINT fk_ainer_ai_task_run_snapshot
        FOREIGN KEY (context_snapshot_id) REFERENCES ainer_ai_context_snapshot (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_ainer_ai_task_run_status
        CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_ainer_ai_task_run_completion
        CHECK ((status = 'RUNNING' AND completed_at IS NULL)
            OR (status <> 'RUNNING' AND completed_at IS NOT NULL))
);

CREATE INDEX idx_ainer_ai_task_run_task
    ON ainer_ai_task_run (task_id, started_at DESC);

CREATE INDEX idx_ainer_ai_invocation_task_run
    ON ainer_ai_invocation (task_run_id)
    WHERE task_run_id IS NOT NULL;

CREATE TABLE ainer_ai_result (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    run_id UUID NOT NULL,
    invocation_id UUID,
    content TEXT NOT NULL,
    fact_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    inferences JSONB NOT NULL DEFAULT '[]'::jsonb,
    result_schema_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ainer_ai_result_run
        FOREIGN KEY (run_id) REFERENCES ainer_ai_task_run (id) ON DELETE CASCADE
);

CREATE INDEX idx_ainer_ai_result_run
    ON ainer_ai_result (run_id, created_at DESC);

CREATE TABLE ainer_ai_feedback (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    result_id UUID NOT NULL,
    decision VARCHAR(16) NOT NULL,
    edited_content TEXT,
    feedback_reason TEXT,
    memory_proposal JSONB NOT NULL DEFAULT '[]'::jsonb,
    reviewer_id VARCHAR(128) NOT NULL,
    reviewed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ainer_ai_feedback_result
        FOREIGN KEY (result_id) REFERENCES ainer_ai_result (id) ON DELETE CASCADE,
    CONSTRAINT ck_ainer_ai_feedback_decision
        CHECK (decision IN ('ACCEPT', 'EDIT', 'REJECT'))
);

CREATE INDEX idx_ainer_ai_feedback_result
    ON ainer_ai_feedback (result_id, reviewed_at DESC);
