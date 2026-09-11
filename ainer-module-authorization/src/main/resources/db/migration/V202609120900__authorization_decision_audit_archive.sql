-- 决策审计冷归档（ADR-0037 §12.4 保留策略）。
--
-- 背景：ainer_authorization_decision_audit 是 append-only 热表，每个通过 @AinerAuthorize
-- 的请求（ALLOW/DENY/CHALLENGE）都会写一行，长期运行必然无限增长。本 migration 建立与热表
-- 同构的归档表（多一列 archived_at），并为两条真实访问路径建索引：
--
--   1) 按 workspace 的稳定游标分页（在线历史查询 / SIEM 导出读热+冷并集）：
--      (workspace_id, evaluated_at DESC, decision_id DESC)，与热表既有索引同形；
--   2) 按决策时间的归档扫描（保留期扫描 WHERE evaluated_at < cutoff ORDER BY evaluated_at,
--      decision_id LIMIT n FOR UPDATE SKIP LOCKED）：(evaluated_at, decision_id)。
--
-- 热表原来没有按决策时间排序的索引，第 (3) 条补上；没有它，每个归档周期都会对大表做全表扫描
-- 加排序。代价是每行决策审计多维护一个 btree 索引（写入放大约一条索引条目），这是审计表
-- 可运维性的必要成本。
--
-- 归档语义：先归档后删除，且仅当归档行确实存在才删除热行（AuthorizationDecisionAuditMapper
-- .archiveBefore）。归档表默认不自动删除；最终删除、法律保留与外部不可变副本需要另立策略。

CREATE TABLE ainer_authorization_decision_audit_archive (
    decision_id UUID PRIMARY KEY,
    workspace_id UUID,
    requester_issuer VARCHAR(256) NOT NULL,
    requester_type VARCHAR(16) NOT NULL,
    requester_id VARCHAR(256) NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    resource_type VARCHAR(128),
    resource_id UUID,
    outcome VARCHAR(8) NOT NULL,
    reason_code VARCHAR(128) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    request_id VARCHAR(128),
    trace_id VARCHAR(128),
    evaluated_at TIMESTAMPTZ NOT NULL,
    agent_id UUID,
    acting_grant_id UUID,
    archived_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ainer_authorization_decision_audit_archive_requester_type
        CHECK (requester_type IN ('USER', 'SERVICE')),
    CONSTRAINT ck_ainer_authorization_decision_audit_archive_outcome
        CHECK (outcome IN ('ALLOW', 'DENY', 'CHALLENGE')),
    CONSTRAINT ck_ainer_authorization_decision_audit_archive_values
        CHECK (btrim(requester_issuer) <> '' AND btrim(requester_id) <> '' AND btrim(permission_code) <> ''),
    CONSTRAINT ck_ainer_authorization_decision_audit_archive_time
        CHECK (archived_at >= evaluated_at)
);

COMMENT ON TABLE ainer_authorization_decision_audit_archive IS
    '决策审计冷归档：与热表同构，保留原 decision_id；只由保留任务写入，默认不删除';
COMMENT ON COLUMN ainer_authorization_decision_audit_archive.archived_at IS
    '该行进入归档表的时间（搬运事务的时钟），不早于 evaluated_at';

-- (1) 按 workspace 分页查询热+冷并集：两个 UNION ALL 分支各自走同形索引后再归并排序。
CREATE INDEX idx_ainer_authorization_decision_audit_archive_workspace_time
    ON ainer_authorization_decision_audit_archive (workspace_id, evaluated_at DESC, decision_id DESC)
    WHERE workspace_id IS NOT NULL;

-- (2) 按决策时间的归档访问路径（归档表侧的保留期扫描、最旧/最新归档调查）。
CREATE INDEX idx_ainer_authorization_decision_audit_archive_time
    ON ainer_authorization_decision_audit_archive (evaluated_at, decision_id);

-- (3) 热表补齐按决策时间的归档访问路径（保留期扫描的候选集选择与排序）。
CREATE INDEX idx_ainer_authorization_decision_audit_time
    ON ainer_authorization_decision_audit (evaluated_at, decision_id);
