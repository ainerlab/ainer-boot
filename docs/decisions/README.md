# Ainer 架构决策记录

> 文档类型：决策索引 · 状态：生效 · 最近核对：2026-08-27

ADR 记录难以逆转、跨模块或影响长期兼容性的决定。它不是实现日志，也不替代 API 或运行手册。

## 当前决策

| ADR | 状态 | 主题 |
|---|---|---|
| [0001](0001-independent-architecture-baseline.md) | Accepted | 自主架构基线与竞品隔离 |
| [0002](0002-workspace-persistence-baseline.md) | Accepted | Workspace 持久化历史基线（工具选择部分由 0028 取代） |
| [0003](0003-ai-model-gateway-baseline.md) | Accepted | AI Model Gateway 基线 |
| [0004](0004-ainer-brand-and-naming-baseline.md) | Accepted | Ainer 品牌与技术命名 |
| [0005](0005-identity-and-oauth2-security-baseline.md) | Accepted | Identity 与 OAuth 2.1 安全基线 |
| [0006](0006-workspace-tenant-authorization-baseline.md) | Accepted | Workspace tenant 与资源授权 |
| [0007](0007-workspace-membership-lifecycle-and-audit.md) | Accepted | 成员生命周期、所有权与授权审计 |
| [0008](0008-identity-directory-and-access-revocation.md) | Accepted | Directory 与访问撤销传播边界 |
| [0009](0009-cross-runtime-access-revocation-delivery.md) | Accepted | 跨运行时 Directory 与访问撤销投递 |
| [0010](0010-security-operations-and-audit-lifecycle.md) | Accepted | 安全运维双人审批与授权审计生命周期 |
| [0011](0011-selective-online-token-validation.md) | Accepted | 高风险 API 选择性在线 Token 校验 |
| [0012](0012-production-observability-and-auth-availability.md) | Proposed | 生产指标访问与 Authorization Server 可用性边界 |
| [0013](0013-audited-oauth-service-client-lifecycle.md) | Proposed | 受审计 OAuth tenant 服务客户端生命周期 |
| [0014](0014-passkey-first-human-authentication.md) | Proposed | Passkey 优先的人员认证与条件 MFA 基线 |
| [0015](0015-passkey-recovery.md) | Accepted | Passkey 恢复（恢复码 + 管理员双人恢复） |
| [0016](0016-login-rate-limit-and-controlled-enrollment.md) | Accepted | 登录限速与受控首次 Passkey Enrollment |
| [0017](0017-resource-server-step-up-policy.md) | Accepted | Resource Server Step-up 授权策略 |
| [0018](0018-management-authorization-and-tenant-member-management.md) | Accepted | 管理授权模型与租户成员管理 |
| [0019](0019-identity-provisioning-tenant-context-and-ownership-governance.md) | Accepted | Identity 供应、租户上下文与所有权治理 |
| [0020](0020-postgresql-native-greenfield-baseline.md) | Accepted | PostgreSQL Native-First Greenfield 数据基线 |
| [0021](0021-provisioning-notification-delivery-receipts.md) | Proposed | 供应通知最终投递回执边界 |
| [0022](0022-ainer-admin-browser-integration-baseline.md) | Accepted | Ainer Admin 浏览器集成基线 |
| [0023](0023-governed-ai-task-execution-and-identity-weekly-report.md) | Proposed | 受治理 AI 任务执行模型与 Identity 周报验收场景 |
| [0024](0024-evolutionary-modular-platform-architecture.md) | Accepted | 演进式模块化平台架构 |
| [0025](0025-public-artifacts-utilities-and-repository-boundary.md) | Accepted | 公共制品、工具类与仓库边界 |
| [0026](0026-maven-4-build-and-consumer-pom-baseline.md) | Accepted | Maven 4 构建与 Consumer POM 基线 |
| [0027](0027-keep-jdk-25-production-baseline.md) | Accepted | 保留 JDK 25 生产基线并跟踪 JDK 27 |
| [0028](0028-mybatis-plus-infrastructure-baseline.md) | Accepted | MyBatis-Plus 基础设施增强基线 |
| [0029](0029-jdk25-boot4-modern-baseline.md) | Proposed | JDK 25 / Spring Boot 4 现代化基线 |
| [0030](0030-hybrid-fine-grained-authorization-baseline.md) | Superseded by [0037](0037-post-greenfield-authorization-baseline.md) | 通用混合细粒度授权基线（pre-Greenfield tenant 模型，已被 ADR-0037 取代） |
| [0031](0031-agent-delegation-and-ai-context-authorization.md) | Superseded by [0043](0043-agent-delegation-greenfield-baseline.md) | Agent 代行、Capability 与 AI 上下文授权基线（已被 ADR-0043 Greenfield 重述取代） |
| [0032](0032-organization-workforce-directory-baseline.md) | Superseded by [0042](0042-organization-directory-greenfield-baseline.md) | 组织、员工任职与 SubjectSet 授权基线（pre-Greenfield tenant 模型，已被 ADR-0042 取代） |
| [0033 Greenfield](0033-account-workspace-subject-isolation-greenfield-baseline.md) | Accepted | Account、Workspace、Subject 与 Isolation Greenfield 基线（Option B：完全移除 Tenant；目标基线，按 Impact Stage 0–8 执行） |
| [0034](0034-knowledge-foundation-and-ai-context-model.md) | Proposed | Knowledge Foundation 与 AI Context Model 基线 |
| [0035](0035-project-initializer-and-manifest-v1-baseline.md) | Accepted | Project Initializer 与 Manifest v1 基线（确定性生成、安全 preview/diff、golden consumer 门禁） |
| [0036](0036-initializer-crud-generation.md) | Accepted | Initial CRUD 生成：manifest `entities` 段、单表 CRUD 文件清单、PostgreSQL 门禁与 TTCRUD 计时 |
| [0037](0037-post-greenfield-authorization-baseline.md) | Accepted | post-Greenfield 通用混合细粒度授权基线（取代 ADR-0030，Workspace 语义 + adapter 归属决策） |
| [0038](0038-p4-scope-refinement-and-enterprise-base.md) | Superseded by [0040](0040-p3-enterprise-base-and-1.0-product-contract.md) | P4 范围精简与企业基建前置（结论被违反维护规则改写，由 ADR-0040 合规取代） |
| [0039](0039-cache-and-distributed-coordination-baseline.md) | Accepted | 缓存与分布式协调基础设施基线（引入 Valkey/Redis 可选缓存 + 分布式锁 + Spring Cache 抽象） |
| [0040](0040-p3-enterprise-base-and-1.0-product-contract.md) | Accepted | P3 企业基座与 1.0 产品契约（取代 ADR-0038，Stable/Incubating/非目标 + G0–G4 路线） |
| [0041](0041-private-rc-supply-chain-and-immutable-release-baseline.md) | Accepted | 私有 RC 供应链、远端完整读回、项目签名 provenance 与不可变 GitHub Release 基线 |
| [0042](0042-organization-directory-greenfield-baseline.md) | Accepted | 组织与员工目录 Greenfield 基线（取代 ADR-0032：Workspace 锚点 + 决策时实时解析撤销语义 + O1/O2 切片） |
| [0043](0043-agent-delegation-greenfield-baseline.md) | Accepted | Agent 代行 Greenfield 基线（取代 ADR-0031：ActingGrant + 委托检查点实时解析 + A1 最小切片） |
| [0044](0044-knowledge-foundation-implementation-baseline.md) | Accepted | Knowledge Foundation 实现基线（落实 ADR-0034：Greenfield 澄清 + K1 身份/版本 + K2 信任/生命周期两切片） |
| [0045](0045-versioning-lts-and-patch-baseline.md) | Accepted | 版本策略、LTS 与补丁支持基线（相邻 minor 升级 + 一级回滚窗口 + patch 规则 + 兼容检查落地形态；v0.2.0 双消费者矩阵为首批证据） |
| [0046](0046-1.0-lts-terms.md) | Accepted | 1.0 LTS 条款定稿（1.0.x 为首个 LTS 线；补丁规则沿用 ADR-0045；窗口与 EOL 评估条款） |
| [0047](0047-task-scheduling-baseline.md) | Accepted | 任务调度模块基线（P4：延迟/周期执行 + SKIP LOCKED 队列 + 指数退避 + 管理面；泛化通知模块已验证模式） |
| [0048](0048-packages-storage-governance-and-rc-artifacts-retirement.md) | Accepted | Packages 存储治理与 rc 链 Maven 制品退役（修订 ADR-0041 保留条款在存储预算约束下的适用；删除 rc.1/rc.2/rc.3 制品，保留 tag/Release 证据/git 历史） |
| [0049](0049-maven4-reactor-bom-import-warning.md) | Accepted | Maven 4 同 reactor BOM import 告警：暂不消除，等待 Maven 4 GA（修订 ADR-0026 开放项；不改 parentless BOM 消费合同） |
| [0050](0050-delayed-self-elevation-alert.md) | Accepted | 岗位集合绑定延迟自提权采用 Alert：入岗命中自建绑定写审计/指标，不自动撤销、不阻断入岗 |
| [0051](0051-mit-license-and-public-repository.md) | Accepted | Ainer Boot 源码采用 MIT，GitHub 仓库公开（修订 ADR-0041 私有分发假设；不授予商标权） |
| [0052](0052-initializer-v2-secure-vertical-slice.md) | Accepted | Initializer v2 安全纵向切片：`simple-service + workspace`、显式分层、Workspace SQL、授权审计与第四消费者门禁 |

## 历史草案与审查记录

| Record | Standing | Document status | Note |
|---|---|---|---|
| [ADR-0033 v1](0033-account-workspace-isolation-model-baseline.md) | Historical draft; never effective | Historical | 保留 Account-first / God Workspace 初稿；2026-08-04 由 Greenfield 收口 |
| [ADR-0033 v2](0033-account-workspace-isolation-model-baseline-v2.md) | Historical draft; never effective | Historical | 迁移兼容路线草案（LegacyTenantRef/facet mapping）；2026-08-04 不采用，保留为迁移备选语境 |
| [ADR-0033 Adversarial Review](../architecture/adr-0033-adversarial-review.md) | Review record | N/A | 结论：`C. Major revision required`，导致 v2，最终导向 Greenfield |

ADR-0033 Greenfield（Option B）于 2026-08-04 被 Accepted 为 Foundation 目标基线；v1/v2 均为 Historical，
不采用。Greenfield reset 按 [Impact 文档](../architecture/ainer-foundation-greenfield-reset-impact.md) Stage 0–8
执行，完成前既有 Accepted tenant、JWT、内层 Workspace 与 OWNER 规则仍是当前运行权威；接受不授权立即
修改代码，每个 Stage 独立验收。

## 何时需要 ADR

- 新增或改变模块边界、数据库所有权或部署拓扑；
- 选择身份协议、消息中间件、AI provider 抽象或重要第三方依赖；
- 改变事务、tenant 隔离、加密、审计或数据保留策略；
- 引入破坏性 API、配置、事件或 schema 变更；
- 做出会影响商业授权、clean-room 或长期成本的选择。

普通 bug 修复、局部重构和不改变边界的实现细节不需要 ADR。

## 生命周期

状态使用 `Proposed`、`Accepted`、`Rejected`、`Deprecated` 或 `Superseded by ADR-NNNN`。新 ADR 从 Proposed 开始，完成审查后才能 Accepted。

已接受 ADR 保留当时背景和结论。允许修正链接、错字和不改变含义的事实错误；改变结论时新增 ADR，并在旧 ADR 标记被取代，不能改写历史。

## 编号与模板

从全仓下一个四位序号开始，文件名使用：

```text
NNNN-short-kebab-title.md
```

复制 [`0000-template.md`](0000-template.md)，填写背景、决策、备选、后果、安全/数据/运维影响、
验收记录和迁移方式。不得只写最终方案而省略取舍依据。
