# ADR-0023：受治理 AI 任务执行模型与 Identity 周报验收场景

- 状态：Proposed
- 日期：2026-07-29
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无

## 背景

ADR-0019 完成了 Identity 治理（M4.8 A/B/C），ADR-0003 建立了 AI Model Gateway 基线。
但两条线目前是平行的：AI Gateway 只知道 tenantId/subjectId，不知道调用者代表哪个经营
身份、使用什么数据范围、基于哪些事实做判断。

战略评审（2026-07-29）指出：ainer-boot 不应追求独立商业定位或通用 AI 治理平台，
而应作为 Ainer 产品族的共享内核，通过"受治理的经营智能闭环"建立实质差异化。

本文定义从业务场景反推的 AI 任务执行对象模型，并以 **Identity 周报**作为第一个完整
验收闭环。

## 决策驱动因素

- AI 调用必须绑定到已验证的经营身份和租户上下文，不能只靠 tenantId；
- 预算、用量、审计必须可对账，流式调用的最终 token 未知时需要预占-结算模型；
- AI 输出必须区分事实引用和模型推断，人工反馈（接受/编辑/拒绝）必须可记录；
- 对象模型从真实业务场景反推，不先造通用抽象再找用例；
- 不与 Spring AI、Dify、Langfuse 的通用 RAG/Agent 能力重复。

## 验收场景：Identity 周报

### 场景描述

系统为某个 Workspace 下的一个 Identity（如某品牌的小红书账号）自动生成周报，
内容包括：上周发布概览、表现指标、业务反馈摘要、基于已批准 Memory 的经营建议。

### 验收必须能回答的 10 个问题

1. 谁触发了这次任务？（actor）
2. 代表哪个 Tenant 和 Workspace？
3. 使用的是哪个 Identity 和 Identity Version？
4. 引用了哪些事实、数据快照和 Memory？（Context Snapshot）
5. 使用了哪个模型、Provider 和策略版本？
6. 花了多少 Token、Credits 和真实成本？
7. 哪些内容是事实引用，哪些是模型推断？
8. 人工是否接受、编辑或拒绝？
9. 后续发布与业务反馈是否支持这次建议？
10. 整条链路能否重放和审计？

## 对象模型

从上述场景反推，不引入通用 Run/Artifact/Knowledge 抽象。

```text
AiTask                 — 业务上要完成什么（如 "identity-weekly-report"）
AiTaskRun              — 一次任务执行（绑定 GovernedAiExecutionContext）
ContextSnapshot        — 本次使用了哪些来源与版本（Identity、Publication、Metrics、Memory）
AiInvocation           — Run 中的一次模型调用（已有，扩展 context 引用）
AiResult               — 结构化输出（区分 fact_refs 和 inferences）
AiFeedback             — 人工反馈（ACCEPT / EDIT / REJECT + 修正内容）
UsageEvent             — 用量与成本结算事件（预占 → 实际 → 调整）
```

### AiTask

```text
id: UUIDv7
tenant_id: UUID
workspace_id: UUID
task_type: VARCHAR      — 如 'identity-weekly-report'
target_identity_id: UUID — 操作目标身份
status: VARCHAR          — PENDING / RUNNING / COMPLETED / FAILED
trigger: VARCHAR         — 'manual' / 'scheduled' / 'event'
triggered_by: VARCHAR    — actor_id 或 'scheduler'
policy_version: VARCHAR  — 使用的策略版本
created_at, updated_at: TIMESTAMPTZ
```

### AiTaskRun

```text
id: UUIDv7
task_id: UUID → AiTask
governed_context: JSONB  — GovernedAiExecutionContext 快照（不可变）
context_snapshot_id: UUID → ContextSnapshot
status: VARCHAR          — RUNNING / COMPLETED / FAILED / CANCELLED
started_at, completed_at: TIMESTAMPTZ
```

### ContextSnapshot

```text
id: UUIDv7
tenant_id: UUID
identity_id: UUID
identity_version_id: UUID
evidence_refs: JSONB     — [{type, id, version, summary}]
  type ∈ {publication, metric, feedback, memory, brand_rule}
memory_refs: JSONB       — [{memory_id, version, scope, confidence}]
as_of: TIMESTAMPTZ       — 快照时间点
schema_version: INT
```

### AiResult

```text
id: UUIDv7
run_id: UUID → AiTaskRun
invocation_id: UUID → AiInvocation
content: TEXT            — 结构化输出（Markdown 或 JSON）
fact_refs: JSONB         — [{snapshot_evidence_ref, quote}]
inferences: JSONB        — [{label, text, confidence}]
result_schema_version: INT
created_at: TIMESTAMPTZ
```

### AiFeedback

```text
id: UUIDv7
result_id: UUID → AiResult
decision: VARCHAR        — ACCEPT / EDIT / REJECT
edited_content: TEXT     — 仅 EDIT 时填写
feedback_reason: TEXT
memory_proposal: JSONB   — [{proposed_memory, scope, evidence_refs}]
reviewer_id: VARCHAR     — actor_id
reviewed_at: TIMESTAMPTZ
```

### UsageEvent（扩展现有 AiInvocation 审计）

不新建表。扩展现有 `ainer_ai_invocation` 的 `policy_decision` 和 `cost` 字段语义，
增加 `task_run_id` 外键（nullable，向后兼容无 Task 的直接调用）。

## 不包含

- 通用 Agent 工作流引擎；
- 通用文档上传 → 切块 → Embedding → 向量检索 RAG 管线；
- 通用 Prompt 模板管理 IDE；
- `Artifact` 抽象（当确实生成可持久化文件时再引入）；
- `Knowledge` 万能对象（Memory 和 Evidence 保持分离）。

## 实现顺序

1. 定义 `GovernedAiExecutionContext` 契约（已完成）；
2. 创建 `AiTask` / `AiTaskRun` / `ContextSnapshot` migration + 领域对象；
3. 扩展 `AiInvocation` 增加 `task_run_id`；
4. 实现 `AiTaskRunService`（创建 Task → 构建 Snapshot → 调用 Gateway → 保存 Result）；
5. 实现 `AiFeedbackService`（接受/编辑/拒绝 + Memory Proposal）；
6. 实现 Identity 周报 `ContextSnapshotBuilder`（从 Identity + Publication + Metrics + Memory 构建快照）；
7. 端到端测试覆盖 10 个验收问题。

## Build 与 Buy 边界

| Ainer 自己拥有 | 优先复用或可插拔 |
|---|---|
| AiTask / AiTaskRun / ContextSnapshot | 模型 Provider SDK（已有 OpenAI-compatible） |
| AiResult / AiFeedback | 通用模型路由、重试、熔断（Spring AI） |
| GovernedAiExecutionContext | PII 和内容安全引擎（外部服务） |
| Memory Proposal | 通用 Embedding 与向量数据库 |
| ContextSnapshotBuilder（领域特化） | 通用 Trace 存储与可视化 |

## 后果

### 正面

- AI 调用从此绑定经营身份和事实来源，不只是"谁调了模型花了多少钱"；
- 人工反馈进入闭环，Memory 从提议到批准有完整记录；
- ContextSnapshot 不可变，支持审计重放；
- 从业务场景反推，不造通用抽象。

### 代价

- 需要新增 4 张表和对应领域对象；
- AiTaskRunService 是新的复杂事务边界；
- ContextSnapshotBuilder 需要对接 Identity、Publication、Metrics 等领域（部分尚未实现）。

## 参考

- [ADR-0003：AI Model Gateway 基线](0003-ai-model-gateway-baseline.md)
- [ADR-0019：Identity 供应、租户上下文与所有权治理](0019-identity-provisioning-tenant-context-and-ownership-governance.md)
- [ADR-0020：PostgreSQL Native-First 基线](0020-postgresql-native-greenfield-baseline.md)
- 战略评审：ainer-boot 定位收敛（2026-07-29）
