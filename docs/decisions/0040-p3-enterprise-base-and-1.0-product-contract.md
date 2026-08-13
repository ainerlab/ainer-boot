# ADR-0040：P3 企业基座与 1.0 产品契约

- 状态：Accepted
- 日期：2026-08-13
- 决策者：Ainer 项目维护者
- 取代：[ADR-0038](0038-p4-scope-refinement-and-enterprise-base.md)（违反维护规则改写结论，本 ADR 合规取代）
- 被取代：无

## 背景

ADR-0038 初版将企业基建模块（通知/任务/缓存）设为「端口+文档，产品自实现」，后在同一 ADR 中被直接
改写为「商业级完整实现」——这违反了 ADR 维护规则。本 ADR 正式取代 ADR-0038，以合规方式确立：

1. 企业基建模块的商业级规格与实现承诺；
2. 1.0 Product Contract（Stable / Incubating / 非目标）；
3. G0–G4 发布路线。

## 决策

### 1. 企业基建模块：商业级完整实现 + SPI 可替换

每个模块提供**完整默认实现**（含管理 API、缓存、审计、权限），通过 SPI 端口允许产品替换底层 adapter。
不做行业模板；模块是通用基座，不含产品语义。

| 模块 | 规格 | SPI | 默认实现 |
|---|---|---|---|
| 文件存储 | 上传/下载/删除、元数据、大小/类型限制、路径遍历防护 | `FileStoragePort` | `LocalFileStorageAdapter` |
| 字典 | 树形分类、多语言 label、启用/禁用、排序、Spring Cache | — | PostgreSQL + MyBatis + Caffeine |
| 配置 | 类型安全、热更新、版本历史、AES-GCM 加密 secret、变更审计 | `ConfigEncryptionPort` | PostgreSQL + `AesGcmEncryptor` |
| 通知 | 多渠道、模板渲染、PG SKIP LOCKED 队列、virtual thread 并发、指数退避重试 | `ChannelSender` | `LoggingChannelSender`（开发用） |
| 缓存 | Spring Cache 抽象、分布式锁、可替换后端 | `DistributedLockPort` | Caffeine（本地）/ Redis（分布式） |

**通知/任务/缓存/幂等等不改为「端口+文档，产品自实现」**——产品用脚手架就是为了不做这些重复劳动。

### 2. 1.0 Product Contract

**Stable（1.0 必须达到商业级）**：
- Framework：core / spring / security / web / persistence / security / cache / test-support
- Identity：HumanAccount / ServicePrincipal / Credential / Passkey
- Workspace：membership 资源治理 + OWNER 转移 + 审计热/归档
- Authorization：ADR-0037 混合授权 + adapter + 审计 + 防提权
- AI Runtime：模型网关 + SSE + 预算 + 费用审计
- Dictionary / Config / Notification / FileStorage（P3 基座）
- Initializer：manifest v1 确定性生成 + CRUD 模板
- Docker Compose 开发环境
- HTTP 统一响应 + 真实状态码 + 稳定错误码
- 真实签名 JWT 端到端安全链
- Spring Cache 抽象（Caffeine 默认 / Redis 可选）
- AES-GCM secret 加密
- UUIDv7 持久化身份（零 `UUID.randomUUID()` 在持久化路径）

**Incubating（1.0 可用但不承诺 API 稳定）**：
- Agent 代行（ADR-0031）
- Knowledge Foundation（ADR-0034）
- 组织目录（ADR-0032，基于 Greenfield 模型重述）
- 任务调度（P4）

**非目标（1.0 明确不做）**：
- Kafka / RocketMQ / RabbitMQ（ADR-0024 触发条件未满足）
- 菜单/前端 route 权限引擎
- 商业连接器 / 合规留存
- 前端管理面（Ainer Studio 是独立产品）
- 多数据源 / 读写分离 / 分库分表
- Spring Cloud / 服务注册 / 配置中心

### 3. 兼容承诺

**Stable 层**在 1.x 内保持：
- HTTP API 路径和响应结构兼容（新增字段不破坏）
- 数据库 migration 只向前追加（不修改已发布 migration）
- 错误码 `AINER.*` 稳定字符串不变
- SPI 端口接口签名兼容（新增方法用 default）

**Incubating 层**不承诺兼容——minor 版本可能 breaking。

### 4. G0–G4 发布路线

| 阶段 | 使命 | 退出条件 |
|---|---|---|
| **G0** | 冻结 1.0 Product Contract | 本 ADR Accepted + scaffold-design 同步 |
| **G1** | P3 真值与硬化 | UUIDv7/加密/锁/日志/PII/migration 修复完成；文件元数据补齐；管理 API 补齐 |
| **G2** | 0.1 发布列车 | 0.1.0-rc.1 → 远端独立消费 → 0.1.0；签名/SBOM/provenance/migration replay/回滚验证 |
| **G3** | 产品核心闭环 | 最小 Agent/Tool/Context/Evaluation 治理；组织目录（Greenfield 模型）；Knowledge 两个语义切片 |
| **G4** | 1.0 候选 | 至少两个独立参考消费者；连续升级与回滚；HTTP/Java/schema/config 兼容检查；LTS/补丁策略 |

**消费者验证**：G2 阶段建立两个中立 reference consumer 作为兼容实验室。G3 阶段如需将 Knowledge 稳定
纳入 1.0，必须允许 xq/mdpress 的最小验证切片。

## 安全与数据

- 所有 secret 配置值经 AES-GCM 认证加密后存储（`AesGcmEncryptor`，12B 随机 IV）
- 分布式锁释放使用 Lua 原子 GET+DEL（防 race）
- 通知日志不记录 recipient/title/body 原文（PII 脱敏）
- 持久化 ID 统一 UUIDv7（时间有序，零 `UUID.randomUUID()`）

## 参考

- [ADR-0038（被取代）](0038-p4-scope-refinement-and-enterprise-base.md)
- [ADR-0039 缓存基础设施](0039-cache-and-distributed-coordination-baseline.md)
- [ADR-0037 post-Greenfield 授权](0037-post-greenfield-authorization-baseline.md)
- [scaffold-design §13](../design/ainer-scaffold-design.md)
- [database-design-standard §14](../database-design-standard.md)
