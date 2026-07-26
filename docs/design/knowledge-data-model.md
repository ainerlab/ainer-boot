# Ainer Knowledge 数据模型提案

> 文档类型：设计提案 · 状态：Proposed · 最近核对：2026-07-26 · 不直接授权创建 migration

## 1. 目的

本文定义 Ainer Knowledge 的最小职责、候选概念和首个纵向切片，目标是让 RAG 数据可追溯、
可重建、可授权，而不是提前制造一个万能知识库产品。

本文不决定 `pgvector` 的索引类型、外部向量数据库、文档解析器或管理 UI。存储选型必须在真实
数据集上另立 ADR；数据库通用规则见
[`database-design-standard.md`](../database-design-standard.md)。

## 2. 从现状得到的证据

xq 当前知识模型已经证明 `knowledge -> document -> segment` 是真实需求，同时也说明仅有这三层
仍不够：

- 文档正文会变化，但缺少不可变 revision 时无法解释某次回答使用了哪个版本；
- embedding 模型名和 vector ID 不能证明维度、模型版本、切分器和索引代际；
- segment 被原地覆盖后，历史引用和评测不可重现；
- 角色上的 knowledge ID 列表不能表达资源级授权、发布版本和检索策略；
- 客户画像、销售事实等来源数据放进 AI 命名空间会模糊事实所有权。

因此 Ainer 应补足 revision、派生版本和授权证据，但不能照搬现有表。

## 3. 责任边界

### 3.1 来源 bounded context 拥有

- 商品、订单、客户、成交、翡翠观察等业务事实；
- 谁可以读取这些事实的权威授权策略；
- 数据修订、撤销和删除的业务原因；
- 是否允许被摄取、用于模型或进入某个知识用途。

### 3.2 Knowledge bounded context 拥有

- 被批准来源的摄取状态和来源引用；
- 文档的不可变 revision；
- 切分、embedding 和索引代际等可重建派生数据；
- 检索时实际使用的 revision、chunk、得分与授权决策证据；
- 派生数据的重建、切换、过期和清理。

Knowledge 数据是**可重建投影**，不是业务事实的新权威副本。共享数据库不授予 Knowledge 直接
读取其他模块私有表的权利；摄取使用公开导出契约、对象事件或经批准的只读来源适配器。

## 4. 最小概念模型

```text
Document
  └── Revision (immutable source evidence)
        └── Chunk (immutable derivation)
              └── Embedding (per index generation)

Index Generation
  └── selects parser/splitter/embedding configuration and active cutover

Retrieval
  └── optional evidence of query + selected chunks
```

这里的每个概念都需要独立的不变量，但不表示首个 migration 必须一次创建全部表。

## 5. Document 与 Revision

### 5.1 Document

Document 是稳定逻辑身份，候选语义：

- UUIDv7 `id` 与 UUID `tenant_id`；
- 稳定 `document_code` 或来源身份；
- `source_type` 和受限 `source_reference`；
- 当前发布 revision 的引用；
- 生命周期状态；
- 创建和更新时间。

`source_reference` 只用于追溯，不用于绕过来源系统授权。不得把任意业务对象序列化进 Document。

是否需要 `Corpus` / `Knowledge Base` 聚合由首个产品用例决定。若首个用例只有部署时固定的一个
tenant 知识集合，可以先用受控 `corpus_code` 契约；没有管理、发布和授权生命周期前，不预建
万能知识库容器。

### 5.2 Revision

Revision 表示一次不可变来源快照，至少需要：

- UUIDv7 `id`、`document_id`、`tenant_id`；
- 单调 `revision_number` 或来源 `source_version`；
- `content_hash`、内容大小、MIME type 和受控对象引用；
- `schema_version`、解析状态、数据分级；
- 来源捕获时间与创建时间；
- 失败时稳定错误码，不保存解析器原始敏感错误。

同一 Document 的 revision 不原地覆盖。新内容产生新 revision；撤销发布不删除历史 lineage，
但必须阻止其继续进入新检索结果并按保留策略清理正文。

## 6. Chunk

Chunk 是 Revision 经指定切分配置产生的不可变派生项，候选语义：

- `revision_id`、`tenant_id` 和稳定序号；
- `content_hash`、token 数、字符或页码范围；
- splitter 名称与版本；
- 受大小限制的 chunk 正文或受控对象引用；
- 数据分级和来源定位信息。

同一 revision 使用不同 splitter/version 时产生新的派生集合，不覆盖旧 chunk。页码、章节和
来源定位必须结构化，不能全部塞进开放 metadata。

chunk 正文是否进入 PostgreSQL 取决于大小、加密、检索延迟、备份和删除要求。短文本可以在审批
后落库；大正文和附件默认在对象存储。两种情况下都必须有 hash 和统一 ACL。

## 7. Index Generation 与 Embedding

Index Generation 表示一套可发布、可回滚的检索代际，至少固定：

- embedding provider/model/version/dimensions；
- parser 与 splitter version；
- 距离度量和索引配置；
- 数据选择范围与创建时间；
- `BUILDING`、`ACTIVE`、`FAILED`、`RETIRED` 等最小状态；
- 激活、回滚和清理规则。

Embedding 绑定 `chunk_id + generation_id`，不能只保存模型展示名或一个外部 vector ID。换模型、
维度、切分器或关键规范化规则时建立新 generation，完成离线构建与质量验证后原子切换；不得
静默覆盖活动向量。

是否使用 PostgreSQL `pgvector`，必须用真实语料验证：

- tenant/ACL 过滤后的召回率和 p95/p99 延迟；
- 索引构建、增量写入、VACUUM、备份与恢复；
- 维度、总向量量、单 tenant 倾斜和重建窗口；
- HNSW/IVFFlat 或其他方案的准确率、内存和运维代价。

首个实现只有一个向量后端时直接实现该后端，不预建“支持所有向量数据库”的抽象层。第二个真实
后端出现后，再从差异中抽取端口。

## 8. 授权模型

检索授权至少包含 tenant 隔离。首个切片建议只支持：

> tenant 管理员明确发布、tenant 内统一可读的精选知识。

这样可以先证明 revision、重建、检索和引用，不虚假宣称已经具备资源级 ACL。

当真实用例需要部门、角色、客户或单资源权限时：

1. 来源 bounded context 仍是授权权威；
2. Knowledge 保存来源资源引用、授权投影版本和策略范围；
3. 查询必须在向量检索前完成可执行过滤，不能先召回敏感 chunk 再在应用层丢弃；
4. 授权投影通过可靠事件更新，撤销延迟必须有 SLO；
5. 高风险场景在返回内容前可以向来源授权端口重新确认；
6. 检索证据记录使用的策略/投影版本。

没有上述能力时，客户、成交、内部销售经验等敏感资料不得进入宣称“资源级隔离”的知识集合。

## 9. Retrieval 证据

不是每次检索都必须进入业务 OLTP 表。只有产品需要引用、问题追查、在线评测或合规重放时才
持久化 Retrieval。

需要持久化时，最小语义包括：

- tenant、可选 `run_id`、请求与时间；
- query fingerprint，原始 query 默认不落库；
- corpus code、generation ID、filter/policy version；
- 候选/命中数量、耗时和稳定错误码；
- 每个命中的 document revision、chunk、rank、score 和是否最终采用。

多个 hit 使用从属表或专用观测存储，不保存逗号列表或无版本 JSON。高容量 trace 应进入适合的
observability/analytics 存储；业务 OLTP 只保留受控审计与引用事实。

## 10. 与 AI Runtime 的关系

- Run 表示完整 AI 任务；
- Retrieval 是 Run 中可选的知识操作，不等于 provider Invocation；
- 选中的 chunk 内容可以在内存中组成模型上下文，但不复制进 Invocation 审计；
- Artifact 可以引用实际采用的 revision/chunk，支持报告引用和后续复核；
- Knowledge 不拥有 Agent、模型费用或最终业务结果。

具体 Run / Artifact 语义见
[`ai-runtime-data-model.md`](ai-runtime-data-model.md)。

## 11. xq-platform 参考接入

推荐的首个来源是**精选翡翠知识文档**，而不是客户画像、销售明细或全量生产商品表：

```text
xq 内容 owner
  -> 审核并发布可用于 AI 的文档 revision / 对象引用
  -> 可靠事件或显式摄取命令

Ainer Knowledge
  -> 校验 tenant、来源和内容 hash
  -> revision -> chunk -> generation embedding
  -> 发布通过质量门禁的 active generation

Ainer AI Runtime
  -> tenant 过滤检索
  -> 记录必要引用证据
  -> 生成 Artifact

xq 业务 owner
  -> 校验产物
  -> 写最终分析或估价事实
```

后续接入商品、历史成交和销售经验时，xq 仍拥有原始事实。Ainer 只接收按用途批准、最小化、
脱敏并可撤销的知识投影；不得长连接 xq 生产数据库复制任意表。

## 12. 当前明确不做

- 不复制 xq 的 `ai_knowledge` / document / segment 表；
- 不以 `knowledge_ids` / `tool_ids` 数组或逗号列表表达授权；
- 不把所有文档 metadata、ACL 和来源关系放进 JSONB；
- 不在同一行静默覆盖正文、chunk 或 embedding；
- 不把客户、销售、商品和成交事实改名为 `ainer_knowledge_*`；
- 不默认保存 prompt、query、命中全文或供应商原始响应；
- 不默认创建 GIN、HNSW、IVFFlat 或分区；
- 不在没有真实召回数据时承诺某个 vector backend。

## 13. 首个实现切片

开始编码前必须明确：

1. 一个真实 xq 精选知识用例和 owner；
2. 允许的文档类型、最大大小、数据分级和删除规则；
3. 首版 tenant-wide 授权边界；
4. parser、splitter、embedding 的明确版本；
5. 离线 gold set、召回质量和延迟门槛；
6. generation 激活、失败回滚和全量重建流程；
7. Artifact/业务结果如何保存引用。

首版只实现满足该用例的物理表和端口。资源级 ACL、外部向量库、自动爬取、通用管理 UI、知识图谱
和在线 Feedback 在有独立证据后逐项增加。
