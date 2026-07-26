# Ainer AI Runtime 数据模型提案

> 文档类型：设计提案 · 状态：Proposed · 最近核对：2026-07-26 · 不直接授权创建 migration

## 1. 目的

本文回答一个有限问题：在现有 Model Gateway 已经拥有 `AiInvocation` 的前提下，Ainer 何时需要
`Run` 和 `Artifact`，它们与业务系统如何分工。

本文不设计 Agent 框架、工作流编辑器、Tool Registry、Memory 或通用评测平台。设计原则受
[`database-design-standard.md`](../database-design-standard.md) 约束；实现前仍需以首个真实
纵向切片接受 ADR 和 migration 评审。

## 2. 证据与结论

### 2.1 Ainer 当前已经证明的模型

当前 `ainer-module-ai-runtime` 只有 Model Gateway：

- `AiInvocation` 表示一次真实 provider 调用或出网前拒绝；
- invocation 记录 provider、模型、Token、费用、策略、状态和耗时；
- prompt 与模型输出正文默认不落库；
- PostgreSQL 调用事实是预算与审计依据。

这已经证明 `Invocation` 必要，但尚未证明每次调用都需要工作流、步骤、产物和反馈表。

### 2.2 xq 现状提供的反向证据

xq 当前同时存在知识库、工作流 JSON、聊天内容、商品识别、客户画像和销售事实。它说明真实产品
确实需要多阶段 AI 任务和可引用结果，也暴露了必须避免的边界问题：

- “供 AI 使用”不等于数据应归 AI 模块所有；
- 工作流图 JSON 不能代替稳定的执行事实；
- 客户、销售、商品和估价结果不能因为由 AI 参与生成就成为 AI 平台事实；
- 一次 provider 调用不足以表示完整业务任务。

因此需要 `Run` 和可选 `Artifact`，但不需要立即建立通用工作流引擎。

## 3. 核心语义

```text
Run         一次需要独立跟踪的 AI 任务
Invocation  一次 provider 调用或调用前策略拒绝
Artifact    Run 产生的、需要长期引用的不可变平台产物
Business
Result      业务模块确认后形成的领域事实，不属于 AI Runtime
```

关系：

```text
Run 1 ── 0..n Invocation
Invocation ── belongs to 0..1 Run
Run 1 ── 0..n Artifact
Invocation ── 0..n Artifact（可选来源关系）
Business Result ── references ── Run / Artifact
```

`Invocation` 可以独立存在。普通单次模型调用不为追求“模型整齐”强制创建 Run；只有命中第 4 节
条件的任务才建立 Run。

## 4. 何时创建 Run

满足下列任一条件时应创建 Run：

1. 一次业务任务包含多次 invocation、检索、工具或非模型处理；
2. 任务异步执行，调用者需要查询、取消或重试；
3. 任务会产生需要持久引用的 Artifact；
4. 业务需要以任务维度追踪状态、成本、失败原因或幂等；
5. 同一任务需要跨进程、跨节点恢复。

仅有下列情况时不创建 Run：

- 同步、单次、无持久产物的 Model Gateway 调用；
- 只为了给日志增加一层父 ID；
- 尚无消费者的“未来 Agent”占位；
- 单纯统计费用；费用仍从 Invocation 事实计算。

## 5. Run 候选模型

首个实现只应包含已被纵向切片需要的字段：

| 字段语义 | 规则 |
|---|---|
| `id` | UUIDv7 |
| `tenant_id` | Ainer tenant UUID，来自可信身份上下文 |
| `purpose_code` | 稳定任务用途，如 `JADE_ANALYSIS`，不是任意展示名称 |
| `actor_type` / `actor_id` | 发起用户或服务身份；`actor_id` 保留 opaque subject 语义 |
| `request_id` | 链路关联，不承担幂等 |
| `idempotency_key` | 调用方在明确作用域内提供 |
| `request_fingerprint` | 防止同一幂等键提交不同请求；不保存输入正文 |
| `status` | 最小集合：`ACCEPTED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED` |
| `failure_code` | 稳定平台错误码，不保存供应商原始正文 |
| `created_at` / `started_at` / `completed_at` | `timestamptz`，满足时间先后约束 |
| `version` | 条件更新与并发控制 |

幂等唯一边界必须由首个调用契约确定，不能仅凭本提案猜测。通常至少包含
`tenant_id + actor + purpose_code + idempotency_key`，并使用 `request_fingerprint` 检测键复用。

Run 不保存：

- 通用 `input_payload` / `output_payload`；
- 业务对象的完整快照；
- provider prompt / response；
- 工作流 graph JSON；
- 可从 Invocation 重算的费用汇总；
- `subject_type + subject_id` 万能多态业务关联。

业务调用方可以保存 `run_id`；Ainer 不反向外键到 xq 等外部业务表。确需关联展示时使用受限、
非授权依据的调用方 reference 契约，并由调用方负责解释。

## 6. Invocation 如何接入 Run

首个 orchestrated slice 出现时，现有 `ainer_ai_invocation` 最多增加：

- nullable `run_id`；
- nullable `operation_code`，表示 Run 内稳定的逻辑操作；
- nullable `attempt_number`，仅在真实重试语义出现时增加。

`run_id` 保持可空，从而保留已被证明有价值的独立 Gateway 调用。Ainer 不创建通用
`ainer_ai_step` 表；只有步骤本身具备独立状态、等待、审批、恢复或非 invocation 产物时，才重新
评估步骤实体。

Run 状态不通过“最后一条 invocation”猜测。Run application service 按任务协议显式推进状态；
Invocation 继续独立记录 provider 事实和费用。

## 7. Artifact 候选模型

只有输出需要跨请求、跨服务或长期引用时才创建 Artifact。候选字段：

| 字段语义 | 规则 |
|---|---|
| `id` | UUIDv7 |
| `tenant_id` / `run_id` | 与 Run 同 tenant，并由复合约束证明 |
| `producing_invocation_id` | 可空；非模型处理也可产生产物 |
| `artifact_type` | 稳定类型，例如分析草稿、结构化候选、报告附件 |
| `media_type` | 标准 MIME type |
| `storage_reference` | 受权限控制的对象存储引用，不保存公开永久 URL |
| `content_hash` / `byte_length` | 完整性与大小 |
| `schema_version` | 结构化产物的契约版本 |
| `classification` | 数据分级与访问策略输入 |
| `created_at` / `expires_at` | 保留和到期语义 |

Artifact 在对象内容成功持久化后才发布为可见事实，发布后不可原地改写；新内容产生新 Artifact。
删除、法律保留和对象存储失败必须由显式生命周期处理。

Artifact 只是平台产物，不自动成为业务事实。业务模块必须完成 schema、权限和领域规则校验，
再写入自己的报告、估价、商品属性或决策表。

## 8. 当前明确不建的表

### 8.1 通用 Step

`operation_code` 足以覆盖首个多调用任务。没有独立等待、审批和恢复语义前，不建步骤表。

### 8.2 通用 Feedback

当前没有被证明通用的反馈语义。聊天满意度、估价复核、知识命中纠错和模型评测具有不同 actor、
量表、保留与权限，不能先塞进万能 `ainer_ai_feedback`。

首个真实反馈由其业务 owner 建模；当至少两个消费者证明稳定公共语义后，再评估平台事件或评测
模型。

### 8.3 Workflow Definition

Run 是执行事实，不是工作流定义。没有可版本化、可发布、可回滚的执行器之前，不建立 graph JSON
或通用节点表。

### 8.4 Run 聚合成本表

成本以 Invocation 为不可变账本，通过查询或经过证明的投影汇总。没有性能证据前不复制金额。

## 9. xq-platform 参考接入

以“小趣玉眸”一次翡翠分析为例，边界应当是：

```text
xq 业务模块
  1. 拥有商品、图片引用、人工观察、估价案例和最终业务报告
  2. 固化经校验的业务输入快照
  3. 以 purpose + idempotency key 请求 Ainer Run

Ainer AI Runtime
  4. 拥有 Run、Invocation、检索证据和 Artifact
  5. 执行模型调用与平台策略，不直接写 xq 业务表

xq 业务模块
  6. 读取产物并校验 schema、权限、模型置信与人工复核条件
  7. 写入自己的分析/估价报告，保存 run_id / artifact_id 作为 lineage
```

这意味着：

- `xq_product_*`、客户、订单、成交价和最终估价仍由 xq 业务域拥有；
- Ainer 不建立 `ainer_ai_jade_*`；
- xq 不直写 `ainer_ai_run` / `ainer_ai_artifact`；
- 两边没有跨数据库外键，使用 UUID、契约和可靠事件关联；
- AI 结果未经业务验证不得自动覆盖人工标签、商品事实或最终价格。

## 10. 首个实现切片

建议首个切片只验证：

1. 一个真实的多阶段 xq AI 用例；
2. `Run` 状态、幂等、失败恢复和 tenant 隔离；
3. 现有 Invocation 可选关联 Run；
4. 一个结构化 Artifact 的对象引用、hash、schema version 与业务消费；
5. xq 业务结果与 Ainer 平台事实分离。

不在同一切片实现 Agent、Tool Registry、工作流设计器、Memory、通用 Feedback 和完整 Knowledge
平台。若首个用例只需要单次 Gateway 调用，则继续使用 Invocation，不为了路线图提前落表。

## 11. 实现前门禁

- 接受一份说明幂等作用域、状态机、失败恢复和数据分级的 ADR；
- 明确业务 owner 与 Ainer owner，各自唯一写入路径；
- 明确 Artifact 的 schema、对象存储 ACL、保留和删除；
- PostgreSQL 18 约束覆盖 UUIDv7、tenant 同属、状态和时间关系；
- 跨 tenant、幂等键复用、重复回调、对象上传失败和业务校验失败均有测试；
- 更新 [`ai-gateway.md`](../ai-gateway.md)、API 契约、项目状态和数据字典。
