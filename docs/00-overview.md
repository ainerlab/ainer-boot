# Ainer 文档总览：从这里开始

> 文档类型：统一入口 · 状态：生效 · 最近核对：2026-07-26 · 适用版本：`0.1.x`

本文是 Ainer Boot 文档的唯一权威入口。它帮助开发者、架构师和 AI agent 先建立同一份项目心智
模型，再进入具体规范。它不复制各专题文档的细节，也不替代当前状态、架构规范或 ADR。

## 1. 只有十分钟时

按顺序阅读：

1. 本文：理解项目、文档边界和阅读路线；
2. [`project-status.md`](project-status.md)：确认当前真正完成了什么、验证证据和已知缺口；
3. [`architecture.md`](architecture.md)：理解模块、发行物、依赖和数据所有权；
4. [`conventions.md`](conventions.md)：理解实现必须遵守的工程规则。

准备开始开发时，再阅读 [`development.md`](development.md) 与
[`testing.md`](testing.md)。任务涉及 HTTP、数据库、安全、AI 或发布时，使用第 4 节的专项路线。

## 2. 项目心智模型

Ainer（AI-Native Extensible Runtime）是基于 JDK 25、Spring Boot 4.1 和 PostgreSQL 18 的
AI 原生企业应用平台底座。它同时面向三个层次：

- 可独立发布的 Java framework、starter 与 BOM；
- 承载 Identity、Workspace 和 AI runtime 的模块化应用；
- 未来社区版、企业版和行业产品的工程与商业交付基线。

当前优先做正确的模块化单体边界；服务化通过独立发行物、稳定契约、明确数据所有权和可靠事件
演进，不依靠一个配置开关伪装成微服务。

```text
ainer-dependencies                    统一依赖版本

ainer-framework/
├── ainer-core                        无 Spring 依赖的核心契约
├── ainer-spring                      Spring 共性
├── ainer-security                    可信主体与 authority 契约
├── ainer-starter-web                 HTTP、错误与请求追踪
├── ainer-starter-persistence         MyBatis、Flyway、PostgreSQL、UUID
└── ainer-starter-security            JWT Resource Server 共性

ainer-server                          业务 Resource Server
├── ainer-module-workspace            tenant 资源、成员与授权审计
└── ainer-module-ai-runtime           模型网关、策略、用量与费用审计

ainer-authorization-server            OAuth 2.1/OIDC、Passkey 与 Identity 管理面
└── ainer-module-identity             tenant、user、membership 与安全事件
```

必须先理解的四条边界：

1. `ainer-core` 不依赖 Spring，Starter 不依赖业务模块；
2. 业务模块拥有自己的表、migration、端口和事务；
3. Identity 与业务运行时不共享查询私有表，跨边界使用契约或可靠事件；
4. tenant、subject 和 owner 来自可信身份上下文，不接受客户端自声明。

完整依据见 [`architecture.md`](architecture.md) 和
[ADR-0001](decisions/0001-independent-architecture-baseline.md)。

## 3. 如何判断哪份文档可信

| 文档类型 | 回答的问题 | 权威文档 | 更新方式 |
|---|---|---|---|
| 产品入口 | Ainer 是什么，如何快速运行 | [`../README.md`](../README.md) | 用户可见能力变化时更新 |
| 当前状态 | 现在完成了什么，证据和缺口是什么 | [`project-status.md`](project-status.md) | 每个里程碑、发布候选或风险变化时更新 |
| 长期规范 | 以后应当如何设计和实现 | `architecture.md`、`conventions.md` 等 | 代码与规范同一变更 |
| ADR | 为什么选择这个不可轻易逆转的方案 | [`decisions/README.md`](decisions/README.md) | 接受后不改写结论，以新 ADR 取代 |
| 专题手册 | 某一领域如何开发、验证和运行 | 数据库、安全、AI、运维等文档 | 对应能力变化时更新 |
| 研究/设计 | 候选方案、兼容证据和未来路线 | `design/`、`migration/`、Boot 4 备忘 | 不得写成已交付事实 |
| 变更记录 | 用户可见版本变化 | [`../CHANGELOG.md`](../CHANGELOG.md) | 随功能和发布维护 |

没有一条“代码永远高于文档”或“文档永远高于代码”的简单规则：

- 已接受 ADR 和长期规范代表有意识的约束，代码偏离不能自动推翻它；
- 代码、migration 和测试代表当前可执行事实，文档不能虚构已经交付；
- 二者冲突时必须判断是实现缺陷还是文档失效，并在同一变更中修正和留下依据；
- 动态测试数量、当前缺口和下一里程碑只在 `project-status.md` 维护，其他文档只引用。

## 4. 按任务选择阅读路线

### 4.1 新开发者或首次接手

1. [`project-status.md`](project-status.md)
2. [`development.md`](development.md)
3. [`architecture.md`](architecture.md)
4. [`conventions.md`](conventions.md)
5. [`testing.md`](testing.md)
6. 与任务相关的专题文档和 ADR

### 4.2 架构、模块边界或商业基线

1. [`design/paradigm-redesign.md`](design/paradigm-redesign.md)
2. [`architecture.md`](architecture.md)
3. [`design/ainer-scaffold-design.md`](design/ainer-scaffold-design.md)
4. [`decisions/README.md`](decisions/README.md)
5. 先新增或取代 ADR，再实现重大决策

### 4.3 HTTP API

1. [`api.md`](api.md)
2. [`conventions.md`](conventions.md)
3. [`security.md`](security.md)
4. [`testing.md`](testing.md)

### 4.4 数据库与持久化

1. [`database-design-standard.md`](database-design-standard.md)
2. [`database.md`](database.md)
3. [`testing.md`](testing.md)
4. 所属模块的现有 migration、Mapper 和 PostgreSQL 集成测试

### 4.5 身份、安全与 tenant 授权

1. [`security.md`](security.md)
2. [`architecture.md`](architecture.md) 的安全与数据边界
3. [`decisions/README.md`](decisions/README.md) 中相关安全 ADR；平台 Identity 供应与通知回执
   重点阅读 [ADR-0019](decisions/0019-identity-provisioning-tenant-context-and-ownership-governance.md)
   和 [ADR-0021](decisions/0021-provisioning-notification-delivery-receipts.md)
4. [`configuration.md`](configuration.md)
5. [`testing.md`](testing.md)

### 4.6 AI、模型网关、RAG 或 Agent

1. [`ai-gateway.md`](ai-gateway.md)
2. [ADR-0003](decisions/0003-ai-model-gateway-baseline.md)
3. 设计多阶段任务或持久产物时读
   [`design/ai-runtime-data-model.md`](design/ai-runtime-data-model.md)
4. 设计 RAG、文档、embedding 或检索时读
   [`design/knowledge-data-model.md`](design/knowledge-data-model.md)
5. [`security.md`](security.md) 的数据与身份约束
6. [`database-design-standard.md`](database-design-standard.md) 的 AI 数据规则
7. [`testing.md`](testing.md)

两份 `design/` 文档均为 Proposed，只定义候选语义和实现门槛，不代表 Run、Artifact 或 Knowledge
物理表已经交付。

### 4.7 运行、故障处理与发布

1. [`operations.md`](operations.md)
2. [`configuration.md`](configuration.md)
3. [`releasing.md`](releasing.md)
4. [`project-status.md`](project-status.md) 的当前运行缺口

### 4.8 旧系统迁移或 Boot 4 兼容

1. [`boot4-migration-notes.md`](boot4-migration-notes.md)
2. [`migration/ainer-migration-plan.md`](migration/ainer-migration-plan.md)
3. [`design/paradigm-redesign.md`](design/paradigm-redesign.md)

研究材料只提供证据和路线，最终实现仍受当前架构、长期规范与已接受 ADR 约束。

## 5. 完整文档地图

### 核心认知

| 文档 | 作用 |
|---|---|
| [`project-status.md`](project-status.md) | 当前阶段、完成项、验证证据、缺口和下一步 |
| [`architecture.md`](architecture.md) | 模块、依赖、发行物、运行模式和数据所有权 |
| [`design/paradigm-redesign.md`](design/paradigm-redesign.md) | 为什么不沿用旧脚手架范式 |
| [`design/ainer-scaffold-design.md`](design/ainer-scaffold-design.md) | 脚手架产品与长期架构设计 |
| [`decisions/README.md`](decisions/README.md) | ADR 状态、索引和模板 |

### 工程规范

| 文档 | 作用 |
|---|---|
| [`development.md`](development.md) | 环境、构建、运行和开发流程 |
| [`conventions.md`](conventions.md) | 命名、分层、错误、Starter、安全和编码约定 |
| [`dependencies.md`](dependencies.md) | 依赖版本、用途、许可证和升级纪律 |
| [`testing.md`](testing.md) | 测试层次、PostgreSQL 门禁与完成标准 |
| [`api.md`](api.md) | HTTP、响应、错误、分页和接口契约 |
| [`configuration.md`](configuration.md) | 配置键、安全默认值和秘密注入 |

### 数据与领域专题

| 文档 | 作用 |
|---|---|
| [`database-design-standard.md`](database-design-standard.md) | PostgreSQL 18 表、字段、类型、约束与索引设计规范 |
| [`database.md`](database.md) | 数据库归属、当前表、Flyway 和 Migration 运行手册 |
| [`security.md`](security.md) | OAuth、Identity、tenant、Passkey 和安全边界 |
| [`ai-gateway.md`](ai-gateway.md) | 模型网关、SSE、策略、费用和安全基线 |
| [`design/ai-runtime-data-model.md`](design/ai-runtime-data-model.md) | Run、Invocation、Artifact 与业务结果的候选边界 |
| [`design/knowledge-data-model.md`](design/knowledge-data-model.md) | Knowledge revision、chunk、索引代际与检索授权提案 |

### 运行、发布与迁移

| 文档 | 作用 |
|---|---|
| [`operations.md`](operations.md) | 启停、健康检查、诊断、备份和故障处理 |
| [`releasing.md`](releasing.md) | 版本、制品、兼容、发布和回滚门禁 |
| [`boot4-migration-notes.md`](boot4-migration-notes.md) | Spring Boot 4/JDK 25 兼容验证 |
| [`migration/ainer-migration-plan.md`](migration/ainer-migration-plan.md) | 旧项目到 Ainer 的迁移路线 |

仓库级协作规则见 [`../AGENTS.md`](../AGENTS.md)，贡献流程见
[`../CONTRIBUTING.md`](../CONTRIBUTING.md)。

## 6. 当前阶段

本文不复制当前里程碑，以免形成第二份很快过期的状态。当前阶段、已交付能力、最新测试数字、
已知缺口和下一里程碑始终以 [`project-status.md`](project-status.md) 为唯一维护位置。

## 7. 文档维护与防止继续失序

新增文档前必须先回答：

1. 现有文档是否已经拥有这个主题；
2. 内容是长期规范、当前状态、ADR、专题手册还是研究记录；
3. 谁负责随代码变化更新；
4. 它从本文哪个入口可达，是否会与现有文档重复；
5. 若取代旧文档，旧入口和历史语境如何处理。

规则：

- `00-overview.md` 是唯一编号文档，`00-` 只表示“从这里开始”；
- 其他文档使用稳定语义命名，不建立需要频繁重排的 `01-`、`02-` 顺序；
- 新内容优先进入已有权威文档；只有职责稳定且无法合理归入现有文档时才新建；
- 一个事实只设一个权威维护位置，其他地方使用链接和简短摘要；
- `docs/README.md` 只是目录门面，不承载第二套导航或项目事实；
- 新文档必须接入本文，禁止孤岛文档；
- 已接受 ADR 不改写结论；方案变化新增 ADR，并标记取代关系；
- 计划使用“拟议”“未实现”，已完成能力必须指向代码、migration 或测试证据；
- 重大删除保留迁移、弃用或取代说明，不能通过删除文档抹去历史。

## 8. 代码变化应更新哪里

| 变化 | 必须检查或更新 |
|---|---|
| 用户可见能力、启动方式 | 根 `README.md` |
| 当前完成项、测试证据、缺口、下一步 | `project-status.md` |
| HTTP 路径、字段、状态码、scope、错误 | `api.md` |
| 模块边界、事务、安全、兼容或商业承诺 | `architecture.md` + 新 ADR |
| 配置键、默认值、secret | `configuration.md` |
| 表、字段、类型、约束、索引 | `database-design-standard.md` + `database.md` |
| 测试命令、门禁、跳过行为 | `testing.md` |
| 启停、诊断、备份、恢复 | `operations.md` |
| 版本、制品、发布或回滚 | `releasing.md` + `CHANGELOG.md` |
| 新依赖、版本或许可证 | `dependencies.md` |
| 文档新增、改名、取代 | 本文与所有入链 |

## 9. 文档完成定义

文档变更至少满足：

- 链接可达，命令、模块名、配置键和版本与当前仓库一致；
- 没有真实域名、密码、Token、私钥、API key、客户数据、prompt 或供应商正文；
- 没有把计划能力写成已经实现；
- 当前状态与长期规范没有混在同一权威位置反复维护；
- 新文档已经接入本文，不重复已有文档职责；
- `git diff --check` 通过。

影响代码的变更仍必须执行 [`testing.md`](testing.md) 规定的测试，不能用文档审查代替运行验证。
