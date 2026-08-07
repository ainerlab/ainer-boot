DROP INDEX IF EXISTS idx_ainer_ai_invocation_tenant_started;
DROP INDEX IF EXISTS idx_ainer_ai_invocation_budget;
ALTER TABLE ainer_ai_invocation
    DROP COLUMN IF EXISTS tenant_id;

DROP INDEX IF EXISTS idx_ainer_ai_task_tenant_status;
ALTER TABLE ainer_ai_task
    DROP COLUMN IF EXISTS tenant_id;

ALTER TABLE ainer_ai_context_snapshot
    DROP COLUMN IF EXISTS tenant_id;

CREATE INDEX idx_ainer_ai_invocation_subject_started
    ON ainer_ai_invocation (subject_id, started_at DESC, id DESC);

CREATE INDEX idx_ainer_ai_invocation_budget
    ON ainer_ai_invocation (subject_id, started_at)
    INCLUDE (status, estimated_cost, actual_cost)
    WHERE status IN ('STARTED', 'SUCCEEDED', 'FAILED');

CREATE INDEX idx_ainer_ai_task_status
    ON ainer_ai_task (status, created_at DESC);
