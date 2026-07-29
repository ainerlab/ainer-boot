-- ADR-0023: 受治理 AI 任务执行模型。
-- AiTask / AiTaskRun / ContextSnapshot / AiResult / AiFeedback。
-- 扩展 ainer_ai_invocation 增加 task_run_id 关联。

CREATE TABLE ainer_ai_task (
    id UUID NOT NULL DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    workspace_id UUID,
    task_type VARCHAR(64) NOT NULL,
    target_identity_id UUID,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    trigger VARCHAR(16) NOT NULL DEFAULT 'manual',
    triggered_by VARCHAR(128) NOT NULL,
    policy_version VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_ainer_ai_task_status
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_ainer_ai_task_trigger
        CHECK (trigger IN ('manual', 'scheduled', 'event'))
);

CREATE INDEX idx_ainer_ai_task_tenant_status
    ON ainer_ai_task (tenant_id, status, created_at DESC);

CREATE INDEX idx_ainer_ai_task_identity
    ON ainer_ai_task (target_identity_id, created_at DESC)
    WHERE target_identity_id IS NOT NULL;

CREATE TABLE ainer_ai_context_snapshot (
    id UUID NOT NULL DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    identity_id UUID,
    identity_version_id UUID,
    evidence_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    memory_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    as_of TIMESTAMPTZ NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_ainer_ai_context_snapshot_identity
    ON ainer_ai_context_snapshot (identity_id, as_of DESC)
    WHERE identity_id IS NOT NULL;

CREATE TABLE ainer_ai_task_run (
    id UUID NOT NULL DEFAULT uuidv7(),
    task_id UUID NOT NULL,
    context_snapshot_id UUID NOT NULL,
    governed_context JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (id),
    CONSTRAINT fk_ainer_ai_task_run_task
        FOREIGN KEY (task_id) REFERENCES ainer_ai_task (id) ON DELETE CASCADE,
    CONSTRAINT fk_ainer_ai_task_run_snapshot
        FOREIGN KEY (context_snapshot_id) REFERENCES ainer_ai_context_snapshot (id),
    CONSTRAINT ck_ainer_ai_task_run_status
        CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_ainer_ai_task_run_completion
        CHECK (
            (status = 'RUNNING' AND completed_at IS NULL)
            OR (status <> 'RUNNING' AND completed_at IS NOT NULL)
        )
);

CREATE INDEX idx_ainer_ai_task_run_task
    ON ainer_ai_task_run (task_id, started_at DESC);

CREATE TABLE ainer_ai_result (
    id UUID NOT NULL DEFAULT uuidv7(),
    run_id UUID NOT NULL,
    invocation_id UUID,
    content TEXT NOT NULL,
    fact_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    inferences JSONB NOT NULL DEFAULT '[]'::jsonb,
    result_schema_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ainer_ai_result_run
        FOREIGN KEY (run_id) REFERENCES ainer_ai_task_run (id) ON DELETE CASCADE
);

CREATE INDEX idx_ainer_ai_result_run
    ON ainer_ai_result (run_id, created_at DESC);

CREATE TABLE ainer_ai_feedback (
    id UUID NOT NULL DEFAULT uuidv7(),
    result_id UUID NOT NULL,
    decision VARCHAR(16) NOT NULL,
    edited_content TEXT,
    feedback_reason TEXT,
    memory_proposal JSONB NOT NULL DEFAULT '[]'::jsonb,
    reviewer_id VARCHAR(128) NOT NULL,
    reviewed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ainer_ai_feedback_result
        FOREIGN KEY (result_id) REFERENCES ainer_ai_result (id) ON DELETE CASCADE,
    CONSTRAINT ck_ainer_ai_feedback_decision
        CHECK (decision IN ('ACCEPT', 'EDIT', 'REJECT'))
);

CREATE INDEX idx_ainer_ai_feedback_result
    ON ainer_ai_feedback (result_id, reviewed_at DESC);

-- 扩展已有 invocation 表关联 TaskRun
ALTER TABLE ainer_ai_invocation
    ADD COLUMN task_run_id UUID;

CREATE INDEX idx_ainer_ai_invocation_task_run
    ON ainer_ai_invocation (task_run_id)
    WHERE task_run_id IS NOT NULL;
