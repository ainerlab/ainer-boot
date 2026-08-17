# ADR-0044：Knowledge Foundation 实现基线（K1/K2 两切片）

- 状态：Accepted
- 日期：2026-08-15
- 决策者：Ainer 项目维护者
- 取代：无（落实 [ADR-0034](0034-knowledge-foundation-and-ai-context-model.md)；该 ADR 保持
  Proposed 目标合同地位，本文按 Greenfield 语义澄清并授权首批实现切片）

## 背景

ADR-0034 冻结了 Knowledge Foundation 的方向（受治理语义层、最小语义内核、OKF 关系、
Context Assembly 分层），并要求「另立实现 ADR」。本文即实现 ADR，按 ADR-0040 G3 的
「Knowledge 两个语义切片」交付首批代码。Greenfield 澄清（ADR-0034 的 3 处 tenant 表述）：

1. Knowledge home 锚定 **Workspace**（`workspace_id`），不存在 tenant 级 custody；
2. 检索/发现授权使用 ADR-0037 `Scope.Workspace`，无 tenant 边界；
3. 「跨 tenant 共享」读作跨 Workspace sharing（ADR-0034 §6 原文语义不变）。

## 决策：K1 + K2 两切片

### K1：Identity 与 Revision（身份与版本）

1. `KnowledgeObject`：稳定语义身份（`kind` 为 namespaced 受控字符串 + `title`），锚定
   Workspace；本身无正文。
2. `KnowledgeRevision`：**不可变**语义负载（Markdown representation + 基础 metadata），
   单调递增 `revisionNumber`；更新 = 新 Revision + `RevisionLineage(SUPERSEDES)`。
3. 精确解析：`GET object（asOf）` 返回**当时已发布**的精确 Revision pin；消费方引用必须
   pin Revision ID（不变式 #4）。
4. `resolveLatest` 只在授权检索中使用（Phase 2+），K1 仅按精确 ID/object+asOf 解析。

### K2：Trust 与 Lifecycle（信任与生命周期）

1. `SourceRef[]`：Revision 的来源引用（namespaced source type + 稳定引用串），非身份。
2. `EvidenceLink[]`：`SUPPORTS|CONTRADICTS` 类型化证据链接，append-only，不自动等于真实
   （不变式 #5）。
3. Lifecycle **append-only 事件**：`PROPOSED → PUBLISHED`（+ `RETIRED`）。
   - 任何 principal（USER/SERVICE）可创建 PROPOSED Revision——AI 输出只能到此为止；
   - **PUBLISH 是人工门禁**：只有 USER principal 可执行（不变式 #9：AI/Agent/import
     不能自行 PUBLISH 或验证自己的 Evidence）；SERVICE 调用 publications 端点一律 403；
   - 事件表不可 UPDATE/DELETE（无物理删除路径）。
4. Scope：`knowledge.read`（读取/解析）与 `knowledge.manage`（创建/提议/发布），应用服务内
   强制；错误码 `AINER.KNOWLEDGE.*`；同事务写入 `ainer_knowledge_lifecycle_event` 审计。

### 非目标（本切片明确不做）

- 不建向量/关键词/图索引（Phase 2）、不建 Context Assembly（Phase 3）、不建 OKF import/export
  （Phase 4）、不做 PlatformCatalog/跨 Workspace sharing、不保存 prompt/AI 输出正文为
  Knowledge、不做验证/attestation 记录与结构化 Representation[]（后续按需）。

## 验收

- **K1/K2（2026-08-15）已交付并验证**：`ainer-module-knowledge`（第 26 个 reactor 模块），
  migration `V202608150300` 从空库重放。真 JWT HTTP 5 项全绿（0 skipped）：SERVICE 可提案
  （PROPOSED，createdByType=SERVICE）而**发布 403 `AINER.KNOWLEDGE.PUBLISH_REQUIRES_HUMAN`**
  （不变式 #9）；人工发布 200、重复发布 409；未发布 Revision 对 asOf 解析 404 不可见；
  supersede 产生新 Revision + SUPERSEDES lineage；asOf 精确 pin（v1 发布后 +1µs → v1，
  -1µs → 404）；生命周期事件 append-only 计数断言；kind 非 namespaced 422。
  负载列无更新路径（仅 status/published_at 投影经 markPublished 单向转移）。

## 参考

- [ADR-0034](0034-knowledge-foundation-and-ai-context-model.md)（目标合同）
- [ADR-0037](0037-post-greenfield-authorization-baseline.md)（Workspace 授权）
- [ADR-0040](0040-p3-enterprise-base-and-1.0-product-contract.md)（G3 门禁）
