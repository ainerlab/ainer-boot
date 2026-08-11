# ADR-0037：post-Greenfield 通用混合细粒度授权基线

- 状态：Accepted
- 日期：2026-08-11
- 决策者：Ainer 项目维护者
- 取代：[ADR-0030](0030-hybrid-fine-grained-authorization-baseline.md)（2026-08-02 Proposed，部分原型实现）
- 被取代：无

## 背景

ADR-0030 定义了 Ainer 的通用混合细粒度授权基线（RBAC + ReBAC + 受控 ABAC），但决策文本仍以
pre-Greenfield 的 **tenant 模型**为主：

- `credentialTenantId` 作为 Token 上下文；
- Scope `TENANT(tenantId)`；
- tenant-owned 资源与 `ResourceRef.authoritativeTenantId`；
- I0 切片要求「allowlisted consumer client 的无 tenant USER Token」；
- tenant 成员关系作为授权事实来源。

[ADR-0033 Greenfield](0033-account-workspace-subject-isolation-greenfield-baseline.md)（Option B）已被
接受为地基并完成 S1–S8 施工，**完全移除 tenant 概念**：Identity 换为 HumanAccount/ServicePrincipal/
LoginIdentity/Credential foundation，Token 使用 typed `token_profile`（`SERVICE_V1`/`USER_NEUTRAL_V1`），
撤销通过 `security_epoch`/`sec_epoch` 在线比对，Workspace 与 AI Runtime 已去 tenant 化。代码层面，
`ainer-module-authorization` 的实现已随之迁移到 **Workspace 语义**（`Scope.Workspace/Resource/Global`、
`workspace_id` 列、membership-independent USER Token）。

因此 ADR-0030 处于「决策文本与当前地基不一致」状态。本 ADR 正式取代 ADR-0030，以 post-Greenfield
Workspace 语义重述决策，并解决 ADR-0030 未覆盖的 Spring Security adapter 包归属问题。

## 决策驱动因素

- 授权决策文本必须与 Greenfield 后的无 tenant 地基一致，不保留过时的 tenant 语义；
- ADR-0030 的核心 grant-path 真值表与 RBAC+ReBAC+ABAC 组合仍有效，无需重新设计，只需语义重述；
- Spring Security `AuthorizationManager` adapter 需要同时依赖 Spring Security 类型与授权业务契约，
  其包归属必须明确且不违反「starter 不反向依赖业务模块」约束；
- 已完成的 13 项实现差距（见 `project-status.md` §3）需正式验收记录。

## 决策

### 1. 继承 ADR-0030 的不变核心

以下 ADR-0030 决策**完全保留**，本 ADR 不重复全文，仅引用条款号：

- §1 grant-path 真值表（PUBLIC / RELATION_DERIVED / BINDING_REQUIRED / BINDING_OR_RELATION 四条路径）；
- §1 默认拒绝、缺失/未知/冲突/异常全部 DENY；
- §2 认证主体与授权请求者分离（`AuthenticatedPrincipal` vs `Anonymous`）；
- §3 Permission 与 OAuth scope 分离（`ScopePermissionCeiling` 显式映射，禁止名称相同自动推导）；
- §4 Role/Binding/Scope 分层与不变量；
- §5 领域关系与属性（`DomainAuthorizationPolicy`/`PublicAccessPolicy`/`AuthorizationFactsProvider`）；
- §6 Decision/Challenge/Obligation；
- §7 单对象与集合查询授权；
- §8 Spring Security 集成（adapter 是执行器，非策略源）；
- §11 管理、防提权与 Effective Access；
- §12 撤销、缓存与审计（无 ALLOW 缓存，撤销立即生效）。

### 2. post-Greenfield 语义重述

ADR-0030 中所有 tenant 语义替换为 Workspace 语义：

| ADR-0030（pre-Greenfield） | ADR-0037（post-Greenfield） |
|---|---|
| `credentialTenantId`（Token 上下文） | 移除；资源归属用 `workspaceId`/`accountId` |
| Scope `TENANT(tenantId)` | `Scope.Workspace(workspaceId)` |
| `ResourceRef.authoritativeTenantId` | `ResourceRef.workspaceId`（可空，platform-global 资源为空） |
| tenant-owned 资源 | workspace-owned 资源 |
| I0「allowlisted consumer client 无 tenant USER Token」 | membership-independent USER Token（`USER_NEUTRAL_V1`，HumanAccount sub，无 tenant/roles claim） |
| tenant 成员关系作为授权事实 | Workspace membership（`workspace_id + ACTIVE membership`）作为授权事实 |
| `TENANT(tenantId)` Binding | `WORKSPACE(workspaceId)` Binding |
| tenant-bound resolver | typed `AuthenticatedPrincipalResolver`（SecurityContext → JWT → `AuthenticatedPrincipal`） |

**SubjectRef** 不变：`(issuerNamespace, subjectId, SubjectType{USER, SERVICE})`。

**Scope** 首版三种（代码已实现）：
- `Global` — workspace_id/resource_type/resource_id 全 NULL，仅受控 SERVICE；
- `Workspace(workspaceId)` — workspace_id NOT NULL，resource_type/resource_id NULL；
- `Resource(workspaceId, resourceType, resourceId)` — 三列全 NOT NULL。

### 3. Spring Security adapter 包归属（解决边界矛盾）

ADR-0030 §9.3 要求 `ainer-starter-security` 不反向依赖业务授权模块。但 `AuthorizationManager` adapter
需要同时依赖 Spring Security 类型与 `AuthorizationService`/`AuthorizationRequest`/`AuthorizationDecision`。
三个候选位置：

#### 方案 A：`ainer-starter-security`（不采用）

让 starter-security 加 `ainer-module-authorization` 依赖。**违反 ADR-0024 验收方式**（Starter 不得依赖
业务模块），且会让所有使用 starter-security 的应用（含 authorization-server）强制带入授权模块。

#### 方案 B：独立 `ainer-starter-authorization` 新制品（不采用，为时过早）

按 ADR-0025 L122-123，便利性抽象需至少两个独立消费者。当前只有 `ainer-server` 一个装配消费者。
ADR-0030 §9.3 与设计计划 §9（L639-640）明确「第二个真实重复装配消费者出现后再评估独立制品」。

#### 方案 C：`ainer-module-authorization` 的 `spring/` 适配边界（采用）

adapter 首版放在 `ainer-module-authorization` 的 `spring/` 子包（`dev.ainer.authorization.spring`）。
ADR-0030 §9.2 措辞是「**公开领域契约**保持 Spring-free」，不是「整个模块 Spring-free」——它明确为
Spring adapter 留了「明确适配边界」。

**边界约束**：
- `domain/`、`policy/`、`catalog/`、`application/` 包**零 Spring Security 依赖**（ArchUnit 验证）；
- `spring/` 子包是唯一的 Spring 适配层，依赖 `spring-security-core` + `spring-security-web`；
- `spring/` 子包的类**不得被 `domain/`/`application/` 反向引用**；
- `ainer-starter-security` **不加** `ainer-module-authorization` 依赖（维持 §9.3）。

**实现代价**：authorization 模块 pom 新增 compile scope 的 `spring-security-core` + `spring-security-web`。
这不破坏领域 Spring-free，因为 `spring/` 是独立子包，ArchUnit 可验证 `domain/` 不依赖它。

**演进路径**：第二个真实重复装配消费者（如独立服务化后的第二个应用）出现后，按 ADR-0025 评估提取
`ainer-starter-authorization` 独立制品，将 `spring/` 子包上移。

### 4. adapter 首版范围

**本轮实现**：
- `AinerRequestAuthorizationManager` implements `AuthorizationManager<RequestAuthorizationContext>`：HTTP 层
  adapter，从 SecurityContext 取 `AuthenticatedPrincipal`，构造 `AuthorizationRequest`，调
  `AuthorizationService.authorize()`，映射到 `AinerAuthorizationResult`。
- `AinerAuthorizationResult` implements Spring `AuthorizationResult`：保留 decisionId/reasonCode/outcome，
  `isGranted()` 仅 ALLOW。
- `@AinerAuthorize(permission=...)`：方法注解，引用稳定 PermissionCode，通过 `HandlerInterceptor` 设
  request attribute。不做 SpEL。

**后续切片（不在本 ADR 实现）**：
- `AinerMethodAuthorizationManager`（方法级 AOP）；
- RFC 9470 `401 insufficient_user_authentication` challenge `AccessDeniedHandler`/`AuthenticationEntryPoint`；
- `DecisionObligationExecutor`（FieldMask/RecheckBefore 消费）；
- `AuthorizationTargetResolver` 产品注册机制（从 path/参数解析 ResourceRef）。

### 5. 决策映射：ALLOW/DENY/CHALLENGE → Spring AuthorizationResult

| AuthorizationDecision | AinerAuthorizationResult | HTTP 语义（首版） |
|---|---|---|
| ALLOW + obligations 空 | grant | 请求继续 |
| ALLOW + obligations 非空 | deny（OBLIGATION_UNHANDLED，§8.6） | 403（首版不执行 obligation） |
| DENY | deny | 403 |
| CHALLENGE | deny | 403（首版不区分 RFC 9470 401） |

首版 adapter 的 ALLOW 只在 obligations 为空时 grant——满足 ADR-0030 §8.6「只有 ALLOW obligation 为空
或 adapter 已完整执行时才可单独使用 AuthorizationManager」。

## 非目标

- 不重新设计 grant-path 真值表或 RBAC+ReBAC+ABAC 组合（ADR-0030 §1 仍有效）；
- 不实现方法级 adapter、challenge handler、obligation executor（后续切片）；
- 不提前创建 `ainer-starter-authorization` 独立制品（等第二个消费者）；
- 不验收门禁 8/9/10 的外部不可变制品 + Admin UI + 生产 SLA（属产品化阶段）。

## 后果

### 正面

- 授权决策文本与 Greenfield 后的无 tenant 地基一致；
- adapter 边界矛盾正式解决，`spring/` 子包是明确适配层；
- 领域 Spring-free 约束通过 ArchUnit 可验证；
- 13 项实现差距正式验收记录。

### 负面与风险

- authorization 模块新增 compile 的 `spring-security` 依赖，需 ArchUnit 守护边界（`domain/` 不依赖 `spring/`）；
- adapter 首版不区分 CHALLENGE 的 401，迁移到 RFC 9470 需后续切片；
- ALLOW+obligations 首版被拒绝，限制了 adapter 的适用场景（obligation executor 未实现前只能用于
  obligation 为空的端点）。

## 安全、数据与隐私

- 继承 ADR-0030 的全部安全/数据/隐私约束（默认拒绝、参数化查询、审计数据最小化、撤销立即生效）；
- adapter 不引入新的数据存储或缓存；
- adapter 的 deny 不泄露 decisionId/reasonCode 到 HTTP 响应（首版 403 统一错误码，不暴露内部决策细节）。

## 运维与迁移

1. ADR-0030 标记 `Superseded by ADR-0037`，文本保留为历史记录；
2. 现有代码（已 Workspace 化）无需迁移；
3. adapter 通过 `@Import(AuthorizationModuleConfiguration.class)` 在 `ainer-server` 装配，`spring/` 子包
   的 bean 随 ComponentScan 自动发现（`@ConditionalOnProperty ainer.authorization.enabled`，默认开启）；
4. 端点 opt-in adapter 时，从 ADR-0017 `step-up` 配置精确移除已迁移 action（§8.8 互斥约束）。

## 验收记录

截至 2026-08-11，ADR-0030 差距清单 13 项全部闭合（详见 `project-status.md` §3）：

- P0 缺陷 1-5（RESOURCE scope CHECK、systemOnly PUBLIC、审计零写入、Role.name、时间戳）✅；
- 装配缺陷 6-8（AuthorizationService 装配、deny-all 默认 policy、ainer-server 依赖）✅；
- 验收缺陷 9-13（真实 JWT 端到端、防提权矩阵、外部 Golden Consumer、参数化 SQL、撤权端到端）✅。

验证基线：290 tests / 0 failure / 0 error / 0 skipped（PostgreSQL 18.3 Testcontainers）。

§13.4 创建门禁状态：
- 门禁 8（外部 Golden Consumer 验证）：仓内 SNAPSHOT consumer 已验证；不可变非 SNAPSHOT 制品 + 完整
  产品关系/投影场景仍属产品化阶段；
- 门禁 9（Ainer Admin Role/Binding 管理）：API 层 + 防提权矩阵已交付；Admin UI 集成 + 生产可信管理
  bootstrap 仍属 P3；
- 门禁 10（撤权后受保护写失效）：仓内真实 JWT + HTTP + PostgreSQL 链路已验证；跨实例/缓存/传播 SLA
  仍属生产部署验收。

## 参考

- [ADR-0030（被取代）](0030-hybrid-fine-grained-authorization-baseline.md)
- [ADR-0033 Greenfield](0033-account-workspace-subject-isolation-greenfield-baseline.md)
- [ADR-0024 演进式模块化平台架构](0024-evolutionary-modular-platform-architecture.md)
- [ADR-0025 公共制品与仓库边界](0025-public-artifacts-utilities-and-repository-boundary.md)
- [ADR-0017 Resource Server Step-up](0017-resource-server-step-up-policy.md)
- [详细方案](../design/authorization-architecture-plan.md)
- [Spring Security Authorization Architecture](https://docs.spring.io/spring-security/reference/servlet/authorization/architecture.html)
