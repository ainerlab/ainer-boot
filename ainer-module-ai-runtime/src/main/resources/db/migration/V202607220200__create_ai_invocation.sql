CREATE TABLE ainer_ai_invocation (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
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
    CONSTRAINT ck_ainer_ai_invocation_status
        CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED', 'REJECTED')),
    CONSTRAINT ck_ainer_ai_invocation_policy
        CHECK (policy_decision IN (
            'ALLOWED',
            'REJECTED_MODEL',
            'REJECTED_PROMPT_SIZE',
            'REJECTED_SENSITIVE_DATA',
            'REJECTED_RATE_LIMIT',
            'REJECTED_BUDGET'
        )),
    CONSTRAINT ck_ainer_ai_invocation_tokens
        CHECK (
            (input_tokens IS NULL OR input_tokens >= 0)
            AND (output_tokens IS NULL OR output_tokens >= 0)
        ),
    CONSTRAINT ck_ainer_ai_invocation_cost
        CHECK (
            estimated_cost >= 0
            AND (actual_cost IS NULL OR actual_cost >= 0)
        ),
    CONSTRAINT ck_ainer_ai_invocation_latency
        CHECK (latency_ms IS NULL OR latency_ms >= 0),
    CONSTRAINT ck_ainer_ai_invocation_time
        CHECK (completed_at IS NULL OR completed_at >= started_at),
    CONSTRAINT ck_ainer_ai_invocation_completion
        CHECK (
            (status = 'STARTED' AND completed_at IS NULL)
            OR (status <> 'STARTED' AND completed_at IS NOT NULL)
        )
);

CREATE INDEX idx_ainer_ai_invocation_tenant_started
    ON ainer_ai_invocation (tenant_id, started_at DESC, id DESC);

CREATE INDEX idx_ainer_ai_invocation_budget
    ON ainer_ai_invocation (tenant_id, started_at)
    INCLUDE (status, estimated_cost, actual_cost)
    WHERE status IN ('STARTED', 'SUCCEEDED', 'FAILED');

CREATE INDEX idx_ainer_ai_invocation_request
    ON ainer_ai_invocation (request_id);
