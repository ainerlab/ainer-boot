# Ainer 文档总览：从这里开始

> 文档类型：统一入口 · 状态：生效 · 最近核对：2026-08-25 · 适用版本：`1.0.x`（`v1.1.0` 已打 tag，远端 Packages 发布待计费月重置）

本文是 Ainer Boot 文档的唯一权威入口。它帮助开发者、架构师和 AI agent 先建立同一份项目心智
模型，再进入具体规范。它不复制各专题文档的细节，也不替代当前状态、架构规范或 ADR。

## 1. 只有十分钟时

按顺序阅读：

1. 本文：理解项目、文档边界和阅读路线；
2. [`project-status.md`](project-status.md)：确认当前真正完成了什么、验证记录和已知缺口；
3. [`architecture.md`](architecture.md)：理解模块、发行物、依赖和数据所有权；
4. [`conventions.md`](conventions.md)：理解实现必须遵守的工程规则。

准备开始开发时，再阅读 [`development.md`](development.md) 与
[`testing.md`](testing.md)。任务涉及 HTTP、数据库、安全、AI 或发布时，使用第 4 节的专项路线。

## 2. 项目心智模型

Ainer（AI-Native Extensible Runtime）是基于 JDK 25、Spring Boot 4.1 和 PostgreSQL 18 的
AI-native、但不局限于 AI 的通用企业应用脚手架与平台底座。它同时面向三个层次：

- 可独立发布的 Java BOM、framework、starter、test support 与 build tools；
- Project Initializer、通用企业模块、可选 AI runtime 与参考应用；
- 未来社区版、企业版和行业产品的工程与商业交付基线。

`xq-platform-next` 是规划中的首个外部产品消费者，不是 Ainer 源码副本；`python-learning-service`
已登记为第二个外部消费者（Version-based 升级接入，不绑定开发分支）。Ainer Studio 独立负责
管理端模板、Blocks 与视觉交付。完整产品边界与消费者登记见
[`design/ainer-scaffold-design.md`](design/ainer-scaffold-design.md)。

当前优先做正确的模块化单体边界；服务化通过独立发行物、稳定契约、明确数据所有权和可靠事件
演进，不依靠一个配置开关伪装成微服务。

这一方向正式定义为
[演进式模块化平台架构](decisions/0024-evolutionary-modular-platform-architecture.md)：使用 DDD
识别领域边界，使用端口和适配器控制依赖，以模块化单体交付当前系统，并在满足明确条件后按需
演进为独立服务。

Ainer 生产者构建通过 Maven Wrapper 使用 Maven 4.0.0-rc-6 preview，由 Maven 4 内建
Consumer POM 处理 `${revision}`，不再使用 Flatten Maven Plugin；Maven 3.9+ 只作为已发布制品
的下游消费兼容门禁。Initializer 生成项目自带另一份 Wrapper，固定稳定 Maven 3.9.16；生成项目
不得借用 Ainer 生产者仓库的 Maven 4 Wrapper。完整边界见
[ADR-0026](decisions/0026-maven-4-build-and-consumer-pom-baseline.md)。

```text
ainer-dependencies                    统一依赖版本

ainer-framework/
├── ainer-core                        无 Spring 依赖的核心契约
├── ainer-spring                      Spring 共性 + 文件存储 adapter
├── ainer-security                    可信主体与 authority 契约
├── ainer-starter-web                 HTTP、错误与请求追踪
├── ainer-starter-persistence         MyBatis-Plus/MyBatis、Flyway、PostgreSQL、UUID
├── ainer-starter-security            JWT Resource Server 共性
├── ainer-starter-cache               Spring Cache + Caffeine/Redis + 分布式锁（ADR-0039）
├── ainer-starter-observability       Observation + requestId/trace MDC；OTLP 默认关（ADR-0029 T1#6）
└── ainer-test-support                集成测试基座（RestTestClient、Testcontainers、PostgreSQL）

ainer-server                          业务 Resource Server
├── ainer-module-workspace            membership 资源、成员与授权审计
├── ainer-module-ai-runtime           模型网关、策略、用量与费用审计
├── ainer-module-authorization        混合细粒度授权 + adapter + 审计（ADR-0037）
├── ainer-module-dictionary           树形字典 + 多语言 + Spring Cache（ADR-0040）
├── ainer-module-config               动态配置 + 类型安全 + 热更新 + 版本（ADR-0040）
├── ainer-module-notification         通知端口 + SKIP LOCKED 队列 + 可选 HTTP webhook（ADR-0040）
├── ainer-module-file                 文件元数据 + 大小/类型限制 + 管理 API（ADR-0040）
├── ainer-module-organization         组织目录（Incubating，ADR-0042）
├── ainer-module-knowledge            Knowledge Foundation（Incubating，ADR-0044）
└── ainer-module-task                 任务调度（Incubating，ADR-0047）

ainer-authorization-server            OAuth 2.1/OIDC、Passkey 与 Identity 管理面
└── ainer-module-identity             HumanAccount、ServicePrincipal、登录身份与 Credential

ainer-offstate-app                    P1 最小可消费应用（无外部服务冒烟）

ainer-initializer                     P2 离线确定性生成内核（Manifest v1，ADR-0035）
ainer-initializer-cli                 P2 离线 CLI：preview / init / diff
```

必须先理解的四条边界：

1. `ainer-core` 不依赖 Spring，Starter 不依赖业务模块；
2. 业务模块拥有自己的表、migration、端口和事务；
3. Identity 与业务运行时不共享查询私有表，跨边界使用显式服务契约；
4. subject 和 owner 来自可信身份上下文，不接受客户端自声明。

完整依据见 [`architecture.md`](architecture.md) 和
[ADR-0001](decisions/0001-independent-architecture-baseline.md)、
[ADR-0024](decisions/0024-evolutionary-modular-platform-architecture.md)。

## 3. 如何判断哪份文档可信

| 文档类型 | 回答的问题 | 权威文档 | 更新方式 |
|---|---|---|---|
| 产品入口 | Ainer 是什么，如何快速运行 | [`../README.md`](../README.md) | 用户可见能力变化时更新 |
| 商业文档 | 为什么值得采购；分层/交付/销售物料 | [`commercial/`](commercial/README.md) | 能力发布或商业决策变化时更新 |
| 交接文档 | 接手项目所需的全部入口：定位、状态、架构速查、常见坑、待办 | [`handoff.md`](handoff.md) | 里程碑或重大变化时更新 |
| 1.0 产品说明 | 1.0 合同快照：能力域、合同、质量模型、版本支持与快速开始 | [`ainer-boot-1.0-product.md`](ainer-boot-1.0-product.md) | 1.0 快照不滚动；能力变化走 CHANGELOG |
| 当前状态 | 现在完成了什么、验证结果和缺口是什么 | [`project-status.md`](project-status.md) | 每个里程碑、发布候选或风险变化时更新 |
| 长期规范 | 以后应当如何设计和实现 | `architecture.md`、`conventions.md` 等 | 代码与规范同一变更 |
| ADR | 为什么选择这个不可轻易逆转的方案 | [`decisions/README.md`](decisions/README.md) | 接受后不改写结论，以新 ADR 取代 |
| 专题手册 | 某一领域如何开发、验证和运行 | 数据库、安全、AI、运维等文档 | 对应能力变化时更新 |
| 研究/设计 | 候选方案、兼容验证和未来路线 | `design/`、`migration/`、Boot 4 备忘、`reviews/` 审计快照 | 不得写成已交付事实 |
| 变更记录 | 用户可见版本变化 | [`../CHANGELOG.md`](../CHANGELOG.md) | 随功能和发布维护 |

没有一条“代码永远高于文档”或“文档永远高于代码”的简单规则：

- 已接受 ADR 和长期规范代表有意识的约束，代码偏离不能自动推翻它；
- 代码、migration 和测试代表当前可执行事实，文档不能虚构已经交付；
- 二者冲突时必须判断是实现缺陷还是文档失效，并在同一变更中修正和留下依据；
- 动态测试数量、当前缺口和下一里程碑只在 `project-status.md` 维护，其他文档只引用。

## 4. 按任务选择阅读路线

### 4.1 新开发者或首次接手

1. [`handoff.md`](handoff.md)——交接文档（先读这个）
2. [`project-status.md`](project-status.md)
3. [`development.md`](development.md)
3. [`architecture.md`](architecture.md)
4. [`conventions.md`](conventions.md)
5. [`testing.md`](testing.md)
6. 与任务相关的专题文档和 ADR

### 4.2 架构、脚手架产品路线或商业基线

1. [`design/paradigm-redesign.md`](design/paradigm-redesign.md)
2. [`architecture.md`](architecture.md)
3. [`design/ainer-scaffold-design.md`](design/ainer-scaffold-design.md)
4. [`architecture/ainer-boot-ai-application-foundation-audit.md`](architecture/ainer-boot-ai-application-foundation-audit.md)
5. [`architecture/ainer-foundation-v1-roadmap.md`](architecture/ainer-foundation-v1-roadmap.md)
6. [`decisions/README.md`](decisions/README.md)
7. 先新增或取代 ADR，再实现重大决策

Foundation Roadmap 仍是 Proposed；其 mdpress-first 是有条件的路线建议，不会自动取代当前
`xq-platform-next` first-consumer 决策。消费者顺序改变必须先有独立 ADR。

### 4.3 HTTP API

1. [`api.md`](api.md)
2. [`conventions.md`](conventions.md)
3. [`security.md`](security.md)
4. [`testing.md`](testing.md)

### 4.4 数据库与持久化

1. [`database-design-standard.md`](database-design-standard.md)
2. [ADR-0028](decisions/0028-mybatis-plus-infrastructure-baseline.md)
3. [`database.md`](database.md)
4. [`testing.md`](testing.md)
5. 所属模块的现有 migration、Mapper 和 PostgreSQL 集成测试

### 4.5 身份、安全与授权

1. [`security.md`](security.md)
2. [`architecture.md`](architecture.md) 的安全与数据边界
3. Account、Workspace 与 Isolation 语义以
   [ADR-0033 Greenfield](decisions/0033-account-workspace-subject-isolation-greenfield-baseline.md)
   （Accepted 为目标基线，Option B：完全移除 Tenant；按 [Impact](architecture/ainer-foundation-greenfield-reset-impact.md)
   Stage 0–8 执行）为准；[v2](decisions/0033-account-workspace-isolation-model-baseline-v2.md)、
   [v1](decisions/0033-account-workspace-isolation-model-baseline.md) 与
   [对抗性审查](architecture/adr-0033-adversarial-review.md) 为决策历史。
   Greenfield 替换脊柱的早期实施计划与验收见
   [`architecture/identity-foundation-v1-implementation-plan.md`](architecture/identity-foundation-v1-implementation-plan.md)；
   原子清零（C1–C4 合并）的完整施工序列、隐藏缺口与依赖图见
   [`architecture/0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md)，
   S1–S8 已全部完成并验证（注意：此处的 S1–S8 指 Greenfield ADR-0033 的账号/主体隔离脊柱施工切片，
   与 ADR-0030 通用授权的内部切片 S0–S3 是**两套不同的 S 编号**；ADR-0030 已被
   [ADR-0037](decisions/0037-post-greenfield-authorization-baseline.md) 取代，13 项差距全闭合）
4. [`decisions/README.md`](decisions/README.md) 中相关安全 ADR
5. 集成官方参考管理应用时阅读
   [`ainer-admin-integration.md`](ainer-admin-integration.md) 与
   [ADR-0022](decisions/0022-ainer-admin-browser-integration-baseline.md)
6. [`configuration.md`](configuration.md)
7. [`testing.md`](testing.md)

### 4.6 AI、模型网关、RAG 或 Agent

1. [`ai-gateway.md`](ai-gateway.md)
2. [ADR-0003](decisions/0003-ai-model-gateway-baseline.md)
3. 设计 Knowledge、Content、Grounding 或 Context Assembly 时先读
   [ADR-0034](decisions/0034-knowledge-foundation-and-ai-context-model.md)（Proposed）
4. 设计多阶段任务或持久产物时读
   [`design/ai-runtime-data-model.md`](design/ai-runtime-data-model.md)
5. 设计 RAG、文档、embedding 或检索时读
   [`design/knowledge-data-model.md`](design/knowledge-data-model.md)
6. [`security.md`](security.md) 的数据与身份约束
7. [`database-design-standard.md`](database-design-standard.md) 的 AI 数据规则
8. [`testing.md`](testing.md)

ADR-0034 与两份 `design/` 文档均为 Proposed；前者拟冻结长期语义边界，后两者提供候选实现模型，
均不代表 Run、Artifact 或 Knowledge 物理能力已经交付。

### 4.7 运行、故障处理与发布

1. [`operations.md`](operations.md)
2. [`development-environment-deployment.md`](development-environment-deployment.md)
3. [`configuration.md`](configuration.md)
4. [`public-origin-and-domain-strategy.md`](public-origin-and-domain-strategy.md)
5. [`releasing.md`](releasing.md)
6. [`project-status.md`](project-status.md) 的当前运行缺口

### 4.8 旧系统迁移或 Boot 4 兼容

1. [`boot4-migration-notes.md`](boot4-migration-notes.md)
2. [`migration/ainer-migration-plan.md`](migration/ainer-migration-plan.md)
3. [`design/paradigm-redesign.md`](design/paradigm-redesign.md)

研究材料只提供事实依据和候选路线，最终实现仍受当前架构、长期规范与已接受 ADR 约束。

### 4.9 构建、Consumer POM 或下游消费

1. [ADR-0026](decisions/0026-maven-4-build-and-consumer-pom-baseline.md)
2. [`development.md`](development.md)
3. [`testing.md`](testing.md)
4. [`releasing.md`](releasing.md)
5. [`dependencies.md`](dependencies.md)

先区分 Ainer 的 Maven 4 生产者构建与 Maven 3.9+/Maven 4 外部 consumer 门禁。POM 4.1 和
`packaging=bom` 尚未进入当前实施范围，不能因 XML 精简而绕过安装后 POM 与真实下游验证。

### 4.10 Java 25 / Maven 4 / Spring Boot 4.1 能力利用

1. [`reviews/java25-maven4-springboot41-capability-audit.md`](reviews/java25-maven4-springboot41-capability-audit.md)
2. [ADR-0026](decisions/0026-maven-4-build-and-consumer-pom-baseline.md)
3. [ADR-0029](decisions/0029-jdk25-boot4-modern-baseline.md)（Proposed）
4. [`boot4-migration-notes.md`](boot4-migration-notes.md)
5. [`project-status.md`](project-status.md) 中与 ADR-0029 / Maven 4 相关的缺口

该审计是只读快照，回答“基线是否真正被利用、何处仍有遗留或重复基础设施”，不代替 ADR，也不
把计划能力写成已交付。实施任何 finding 前先对照 ADR 与 `project-status.md`。

## 5. 完整文档地图

### 核心认知

| 文档 | 作用 |
|---|---|
| [`project-status.md`](project-status.md) | 当前阶段、完成项、验证记录、缺口和下一步 |
| [`reviews/2026-08-19-commercial-grade-code-review.md`](reviews/2026-08-19-commercial-grade-code-review.md) | 商业级代码评审审计快照（三路语义评审：安全/正确性/契约一致性；已修复项与遗留 follow-up） |
| [`ainer-boot-1.0-product.md`](ainer-boot-1.0-product.md) | 1.0 产品说明（合同快照：能力域与工具链、Stable/Incubating、质量与信任模型、LTS、参考消费者、快速开始） |
| [`handoff.md`](handoff.md) | 项目交接文档（定位、当前状态、架构速查、必读 ADR、常见坑、发布操作、当前待办、AI 代理协作须知） |
| [`architecture.md`](architecture.md) | 模块、依赖、发行物、运行模式和数据所有权 |
| [`design/paradigm-redesign.md`](design/paradigm-redesign.md) | 为什么不沿用旧脚手架范式 |
| [`design/ainer-scaffold-design.md`](design/ainer-scaffold-design.md) | Ainer Boot 产品定位、竞品能力矩阵、P0–P5 路线与长期架构设计 |
| [`architecture/ainer-boot-ai-application-foundation-audit.md`](architecture/ainer-boot-ai-application-foundation-audit.md) | 面向 xq-platform 与 mdpress 的 AI Application Foundation 架构审计快照 |
| [`architecture/ainer-foundation-v1-roadmap.md`](architecture/ainer-foundation-v1-roadmap.md) | Foundation v1 的能力盘点、FV1-P0～P3 施工顺序、产品验证和明确不做（Proposed） |
| [`decisions/0033-account-workspace-subject-isolation-greenfield-baseline.md`](decisions/0033-account-workspace-subject-isolation-greenfield-baseline.md) | ADR-0033 Greenfield 基线（Accepted 为目标，Option B：完全移除 Tenant；按 Impact Stage 0–8 执行） |
| [`architecture/ainer-foundation-greenfield-reset-impact.md`](architecture/ainer-foundation-greenfield-reset-impact.md) | Greenfield reset 的删除/重建范围、迁移 baseline、JWT/API/event reset 与 Stage 0–8 执行顺序 |
| [`architecture/0033-greenfield-cutover-plan.md`](architecture/0033-greenfield-cutover-plan.md) | Greenfield cutover 早期执行计划（Historical：已被原子执行规划取代，S1–S8 已完成） |
| [`architecture/0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md) | C1–C4 原子清零完整执行规划：隐藏缺口（password store/securityEpoch/workspace 去 tenant）、S2–S8 施工序列与依赖图（S1–S8 全部完成） |
| [`decisions/0033-account-workspace-isolation-model-baseline.md`](decisions/0033-account-workspace-isolation-model-baseline.md) | ADR-0033 v1 历史草案（Historical，未生效） |
| [`architecture/adr-0033-adversarial-review.md`](architecture/adr-0033-adversarial-review.md) | ADR-0033 v1 的对抗性审查与 Major Revision 依据 |
| [`decisions/0033-account-workspace-isolation-model-baseline-v2.md`](decisions/0033-account-workspace-isolation-model-baseline-v2.md) | ADR-0033 v2 迁移路线草案（Historical，不采用，保留为迁移备选语境） |
| [`decisions/0034-knowledge-foundation-and-ai-context-model.md`](decisions/0034-knowledge-foundation-and-ai-context-model.md) | Knowledge Foundation、Content/Asset 边界与 AI Context Assembly 基线（Proposed） |
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
| [`ainer-admin-integration.md`](ainer-admin-integration.md) | Ainer Admin 的 PKCE、成员 API、SDK、退出与同源代理契约 |

### 数据与领域专题

| 文档 | 作用 |
|---|---|
| [`database-design-standard.md`](database-design-standard.md) | PostgreSQL 18 表、字段、类型、约束与索引设计规范 |
| [`database.md`](database.md) | 数据库归属、当前表、Flyway 和 Migration 运行手册 |
| [`security.md`](security.md) | OAuth、Identity、撤销 epoch、Passkey 和安全边界 |
| [`ai-gateway.md`](ai-gateway.md) | 模型网关、SSE、策略、费用和安全基线 |
| [`design/authorization-architecture-plan.md`](design/authorization-architecture-plan.md) | 通用混合授权、集合查询、Spring Security 适配与 Agent 代行详细方案（Proposed） |
| [`design/organization-workforce-architecture-plan.md`](design/organization-workforce-architecture-plan.md) | 部门、员工任职、岗位、团队及 SubjectSet 授权集成详细方案（Proposed） |
| [`design/ai-runtime-data-model.md`](design/ai-runtime-data-model.md) | Run、Invocation、Artifact 与业务结果的候选边界 |
| [`design/knowledge-data-model.md`](design/knowledge-data-model.md) | Knowledge revision、chunk、索引代际与检索授权提案 |

### 商业文档

| 文档 | 作用 |
|---|---|
| [`commercial/README.md`](commercial/README.md) | 对外商业文档套件索引：阅读顺序、买家画像、待定商业决策清单 |
| [`commercial/product-whitepaper.md`](commercial/product-whitepaper.md) | 产品白皮书（对外）：定位、问题论证、能力域价值、信任证据链、交付形态 |
| [`commercial/edition-tiers.md`](commercial/edition-tiers.md) | 版本分层框架草案（Community/Pro/Enterprise 边界，基于能力矩阵推导） |
| [`commercial/customer-delivery-guide.md`](commercial/customer-delivery-guide.md) | 客户交付指南：接入路径、验收基线、升级回滚、责任分界 |
| [`commercial/sales-one-pager.md`](commercial/sales-one-pager.md) | 销售物料一页纸 |
| [`commercial/gap-analysis-and-next-steps.md`](commercial/gap-analysis-and-next-steps.md) | 可售性差距分析与发展路线建议（非权威，供排期讨论） |

### 运行、发布与迁移

| 文档 | 作用 |
|---|---|
| [`operations.md`](operations.md) | 启停、健康检查、诊断、备份和故障处理 |
| [`development-environment-deployment.md`](development-environment-deployment.md) | `ainer-dev.xiaoqu99.com` 拓扑、发布、回滚、验收与跨 session 交接 |
| [`public-origin-and-domain-strategy.md`](public-origin-and-domain-strategy.md) | 临时开发 origin、未来独立品牌域名布局与安全迁移步骤 |
| [`releasing.md`](releasing.md) | 版本、制品、兼容、发布和回滚门禁 |
| [`boot4-migration-notes.md`](boot4-migration-notes.md) | Spring Boot 4/JDK 25 兼容验证 |
| [`migration/ainer-migration-plan.md`](migration/ainer-migration-plan.md) | 旧项目到 Ainer 的迁移路线 |
| [`reviews/java25-maven4-springboot41-capability-audit.md`](reviews/java25-maven4-springboot41-capability-audit.md) | Java 25 / Maven 4 / Spring Boot 4.1 能力利用审计快照（只读，含 NO CHANGE） |

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
- 计划使用“拟议”“未实现”，已完成能力必须指向代码、migration 或测试结果；
- 重大删除保留迁移、弃用或取代说明，不能通过删除文档抹去历史。

## 8. 代码变化应更新哪里

| 变化 | 必须检查或更新 |
|---|---|
| 用户可见能力、启动方式 | 根 `README.md` |
| 当前完成项、测试结果、缺口、下一步 | `project-status.md` |
| HTTP 路径、字段、状态码、scope、错误 | `api.md` |
| 模块边界、事务、安全、兼容或商业承诺 | `architecture.md` + 新 ADR |
| 配置键、默认值、secret | `configuration.md` |
| 表、字段、类型、约束、索引 | `database-design-standard.md` + `database.md` |
| 测试命令、门禁、跳过行为 | `testing.md` |
| 启停、诊断、备份、恢复 | `operations.md` |
| 开发环境容器化、本地启动 | `docker-compose.yml` + `.env.example`（见 [`development.md`](development.md) §3） |
| 版本、制品、发布或回滚 | `releasing.md` + `CHANGELOG.md` |
| 新依赖、版本或许可证 | `dependencies.md` |
| 文档新增、改名、取代 | 本文与所有入链 |
| 基线能力利用审计快照 | `reviews/`（只读结论）；实施进度仍只写 `project-status.md` |

## 9. 文档完成定义

文档变更至少满足：

- 链接可达，命令、模块名、配置键和版本与当前仓库一致；
- 没有真实域名、密码、Token、私钥、API key、客户数据、prompt 或供应商正文；
- 没有把计划能力写成已经实现；
- 当前状态与长期规范没有混在同一权威位置反复维护；
- 新文档已经接入本文，不重复已有文档职责；
- `git diff --check` 通过。

影响代码的变更仍必须执行 [`testing.md`](testing.md) 规定的测试，不能用文档审查代替运行验证。
