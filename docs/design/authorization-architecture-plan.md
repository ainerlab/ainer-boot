# Ainer 通用授权与 AI 代行详细方案

> 文档类型：详细设计 · 状态：Proposed · 日期：2026-08-02
>
> 关联决策：[ADR-0030](../decisions/0030-hybrid-fine-grained-authorization-baseline.md)、
> [ADR-0031](../decisions/0031-agent-delegation-and-ai-context-authorization.md)、
> [ADR-0032](../decisions/0032-organization-workforce-directory-baseline.md)
>
> 当前实现状态仍以 [`project-status.md`](../project-status.md) 为准；本文中的目标类型、表和接口均未实现。

## 1. 结论

Ainer 应建设一套可被小趣知物、小趣藏物、运营后台以及未来 App 共同消费的
**通用混合细粒度授权内核**，但不建设一个包办所有业务语义的权限平台。

最终授权由受控 grant path 与约束交集共同计算。`OR` 只发生在产品策略明确选择的完整授权路径之间，
不能把两条不完整路径的局部条件拼成一次放行：

```text
authenticated path: 已验证凭据的 issuer / audience / OAuth scope 上限
  ∩（Role/Binding grant OR 显式 relation-derived grant）

public path: 显式 PublicAccessPolicy

选中的完整路径继续 ∩ 产品领域拥有的资源关系、资源状态与受控请求上下文
  ∩（AI 第二阶段）ActingGrant 与 Capability 约束
  → ALLOW | DENY | CHALLENGE + obligations
```

Role/Binding 不是所有访问的强制前提。顾客读取本人订单可由产品明确声明的 owner relation 授权，
匿名公开读取只能由显式 PublicAccessPolicy 授权；需要后台职责或可管理业务授权时才走 Role/Binding。
grant path 可以选择其一，选定路径内的 scope、relation、状态、风险与 obligation 仍必须全部满足。

这是一套组合模型：

- RBAC 解决“如何让管理员管理一组职责”；
- ReBAC 解决“这个人和这个对象是什么关系”；
- ABAC 解决“当前资源状态、渠道、时间和认证强度是否满足条件”；
- Delegation/Capability 解决“Agent 能否在限定范围内替某个人执行某种能力”；
- Spring Security 7.1 负责认证、HTTP/方法执行点、MFA 因子和标准协议适配，不成为产品业务规则的事实源。

首版不引入 OpenFGA、Cedar、OPA 或独立授权微服务。Ainer 借鉴它们的资源—动作—主体—上下文
建模、关系授权、默认拒绝和策略版本思想，先以模块化单体内的 Java + PostgreSQL 实现完成真实
消费者验证；出现独立扩缩容、安全隔离或跨语言消费者后，再按 ADR-0024 的条件评估远程 PDP。

本方案同时固定此前的五个关键选择：

1. 通用 Scope 只保留 GLOBAL/TENANT/精确 RESOURCE；merchant、location、product/object 等由
   resource type + 产品关系表达，不扩成 Ainer 枚举；
2. Agent 不新增长期认证凭据类型，远程 runtime 先认证 SERVICE，再叠加 Agent definition + ActingGrant；
3. Capability 与业务 Permission 正交：使用类型化 catalog/constraint，但不建立第二套 Role 层级，也
   不把工具/模型约束压成 Permission 字符串；
4. v1 只支持一层 principal→agent，不支持 agent→agent 或转委托；
5. 新建单一 `ainer-module-authorization`，不并入 Identity，也不预拆成多个空模块或微服务。

## 2. 为什么它可以支撑两个小程序和未来应用

两个小程序不是两个 tenant，也不能共用一套“用户属于组织角色，所以能看所有数据”的规则：

- **小趣知物（`xq-zhiwu`）** 是公开行业信息与协作网络，既有匿名公开读取，也有商家、经营点、
  合作方、采购、品控、拍摄、录货、审核和发布等对象关系；
- **小趣藏物（`xq-shop-next`）** 是消费者发现、决策与交易产品，顾客通常不是 Workspace 或
  tenant 成员，公开商品、本人收藏、咨询、订单和售后具有不同所有权与状态规则；
- **运营后台** 既有 tenant 治理，也有审核、客服、财务、内容运营等业务职责；Ainer Identity 的
  `OWNER/ADMIN/MEMBER` 不能自动获得这些产品权限；
- **未来 App** 只需注册新的 `platform_app`、OAuth client/audience、产品 Permission 和领域关系，
  不需要增加新的认证 actor 类型或把 App 伪装成 tenant。

Ainer 提供稳定原语和扩展端口，`xq-platform-next` 提供产品 Permission、资源、关系、状态机和
查询翻译，因此既能复用，又不会把 `Merchant`、`IndustryListing`、`ConsumerOffer`、采购或品控
等小趣专属概念写进脚手架。

## 3. 当前基座与需要补齐的部分

### 3.1 已经存在

- `AuthenticatedActor(subjectId, tenantId, actorType, authorities)`，actorType 当前只有
  `USER|SERVICE`；
- `AinerSecurityScopes` 与 Spring Security 的 `SCOPE_*` authority；
- OAuth 2.1/OIDC、Authorization Code + PKCE、Client Credentials、JWT Resource Server、
  RFC 7662/7009、Passkey 与 `amr/auth_time`；
- Workspace 的“scope + ACTIVE 资源成员关系”授权与授权审计；
- Identity 的 tenant `OWNER/ADMIN/MEMBER` 治理与撤销传播；
- `RecentStrongAuthenticationFilter` 以及 Authorization Server 中已经使用的
  `FactorGrantedAuthority` / `AuthorizationManagerFactories.multiFactor()`；
- AI Model Gateway 的预算、限流、敏感模式、调用状态和费用审计；
- `GovernedAiExecutionContext`、`ContextSnapshot` 等后续 AI 治理契约的早期形态。

### 3.2 不能直接沿用为通用授权的部分

1. `AuthenticatedActor` 强制 tenant，无法自然表达匿名请求、无 tenant membership 的顾客以及
   跨商户公开读取；不能简单把 `tenantId` 改为任意可空字符串后让所有现有调用点承担歧义。
2. OAuth scope 是令牌能力上限，不是动态资源权限事实；JWT `authorities` 也不能塞入每一个
   merchant、order 或 listing 的实时授权关系。
3. `TenantRole` 与 `WorkspaceRole` 虽然都包含 `OWNER/ADMIN/MEMBER`，但属于不同领域，不能合并成
   一张“平台角色真相表”。
4. 只有单对象 `authorize` 不够；公开信息流、商品搜索、工作队列和订单列表必须在查询前得到
   结构化授权约束，不能先查全量再逐条过滤。
5. 当前路径型 Step-up 适合已有端点兼容，但长期应由 action/risk 驱动，并能区分认证升级、交易确认
   和人工审批。
6. AI 调用审计同时承担预算与状态结算，不能和通用授权决策审计合并成一张表。
7. 当前人员 Authorization Code 流程和 `AuthenticatedActorResolver` 都要求 ACTIVE tenant membership；
   仅把新类型中的 tenant 改成可空并不能让真实顾客取得、刷新和撤销 tenantless USER Token。

## 4. 边界与术语

| 概念 | 含义 | 不表示什么 |
|---|---|---|
| Credential principal | 已由 OAuth/OIDC、mTLS 等验证的 USER 或 SERVICE | 不自动拥有业务资源权限 |
| Anonymous requester | 没有认证凭据的请求 | 不是伪造的 `PUBLIC` 登录用户 |
| Tenant context | 当前组织与数据隔离上下文 | 不是 App、角色或 OAuth scope |
| Platform app | 小程序、后台、伙伴应用等受验证渠道 | 不是 tenant，也不直接授予权限 |
| Permission | 稳定的业务动作契约，如某类资源的 read/publish | 不是 URL、菜单或任意数据库字符串 |
| Role | 一组便于管理的 Permission | 不携带资源所有权或业务状态机 |
| SubjectBinding | 把主体、Role 与精确 Scope 绑定的可撤销授权 | 不是永久写进 JWT 的权限快照 |
| ScopeRef | `GLOBAL`、`TENANT` 或精确 `RESOURCE` 授权范围 | 不等于 OAuth scope 字符串 |
| Relation | owner/operator/member/participant/assigned-to 等产品关系 | 不由 Ainer 万能关系表统一拥有 |
| Attribute | 资源状态、认证强度、时间、渠道等受控事实 | 不是客户端提交的任意属性 Map |
| Acting identity | 人在产品中的商家、员工任职或其他业务身份选择 | Header 只是选择器，不是凭据 |
| Agent | AI runtime 中被版本化管理的执行参与者 | 不是 modelId，也不必成为登录凭据类型 |
| ActingGrant | principal 对 Agent 的短时、可撤销、不可传递委托 | 不是全权代理或无限 agent 链 |

## 5. 通用领域模型

### 5.1 认证主体与授权请求者分离

首版不破坏现有 `AuthenticatedActor`。新增不强制 tenant 的认证投影，并通过兼容适配器让现有
Workspace/Identity 继续使用 tenant-bound actor：

```java
public record AuthenticatedPrincipal(
        String issuer,
        String subjectId,
        PrincipalType type,      // USER | SERVICE
        @Nullable UUID credentialTenantId,
        Set<String> authorities,
        Set<String> audiences,
        @Nullable String clientId) {}

public sealed interface Requester {
    record Authenticated(AuthenticatedPrincipal principal) implements Requester {}
    record Anonymous() implements Requester {}
}
```

约束：

- `PUBLIC` 不进入认证 actorType；公开访问由 `Anonymous` + 产品公开策略表达；
- `GROUP` 首版不是请求 actor，只在未来需要批量成员授权时再设计；
- `AGENT` 不直接塞进 `AuthenticatedActor`。远程 Agent 调用仍先认证 SERVICE 凭据，再由受验证的
  Agent definition 与 ActingGrant 形成委托请求者；
- SubjectRef 必须包含 issuer/namespace，防止不同发行者出现相同 `sub` 时碰撞；
- `credentialTenantId` 只是当前 Token/会话选择的可空 tenant 上下文，不是 Subject 的固有属性；
- 若 `credentialTenantId` 存在，它是不可跨越的 tenant 上限；若不存在，绝不凭“tenantless”自动
  产生任何 tenant 权限，必须由完整 Binding 或 relation-derived 路径解析出目标资源与关系；
- tenant 只可来自已验证 Token 中存在的 ceiling、资源 resolver 和有效业务关系的受控组合，不能从
  普通 Header、路径或请求体直接采信。

`AuthenticatedPrincipal` 与解析端口属于 `ainer-security`，`ainer-starter-security` 仍是把已验证
Spring `Authentication`/JWT 投影为 Ainer 主体的唯一位置；Authorization 模块只消费该端口，不再次
读取 JWT。现有 `AuthenticatedActorResolver` 继续严格要求 tenant，供 Workspace/Identity 等未迁移
用例使用。接受本方案后，这会在 opt-in 端点范围内局部修订 ADR-0005 的“只有
`AuthenticatedActor` 且 `tenant_id` 必填”约束，而不是建立第二个认证事实源。

仅有可空投影还不等于支持真实顾客。创建 `xq-platform-next` 前必须交付一个 membership-independent
的 USER 签发档位：

- Identity 以 ACTIVE user 为主体事实，不要求该 user 先拥有 tenant membership；人员认证模型不再把
  `AinerUserDetails.tenantId` 作为所有 client 的必填字段；
- 只有显式注册并允许 consumer profile 的 OAuth client/audience 才能申请 tenantless USER Token；
  Token 包含稳定 `sub`、`actor_type=USER`、受限 audience/scope，不包含 `tenant_id` 或 tenant `roles`；
- consumer client 不得申请 Workspace、tenant 管理或平台控制面 scope；tenant-bound client 与旧
  `AuthenticatedActorResolver` 行为保持不变；
- 登录、授权码交换、刷新（若该 client 启用）、撤销与账号禁用都复用真实 Authorization Server 状态，
  不能只用测试 Decoder 构造一个无 tenant JWT；
- 外部 OIDC/微信登录由 provider adapter 把受验证外部身份绑定到稳定 Ainer subject。小程序 code、
  openId、Header 或前端 customerId 都不能直接成为 Resource Server 的可信主体。

默认实现以 Ainer Authorization Server 的真实 Authorization Code + PKCE consumer client 完成合同
测试；这不强制微信小程序使用浏览器跳转。`xq-platform-next` 的微信 adapter 应在服务端交换一次性
code，按 `platformAppId + provider + openId` 绑定稳定 subject，并通过受控 Authorization Server
扩展 grant/登录 ceremony 签发同类 Token；过期、重放、AppId 混用和客户端伪造必须拒绝，具体协议
另立产品 ADR。部署若改用兼容外部 OIDC issuer，也必须通过同一签发、解析、账号失效与 scope 隔离
合同。

### 5.2 Permission

建议公开契约：

```java
public record PermissionDefinition(
        String code,
        String action,
        String resourceType,
        RiskTier riskTier,
        AuditLevel auditLevel,
        boolean systemOnly,
        boolean agentDelegable) {}
```

规则：

- Permission code 是稳定应用契约；实现模块通过 `PermissionContributor` 注册；
- 重复 code、同 code 不同定义、未知 resource type 在启动或决策时失败关闭；
- `AinerSecurityScopes` 保持 OAuth scope 契约，通过显式 `ScopePermissionCeilingMapper` 映射
  Permission，禁止按字符串相同自动推导；
- Ainer 内置权限只描述通用平台能力；`industry.*`、`consumer.*`、`order.*` 等由
  `xq-platform-next` 注册；
- 代码注册定义是“应用确实会执行什么动作”的权威，数据库 Permission 表是管理面可查询的受控
  catalog 投影，不能让管理员创造应用从未实现的任意字符串；
- `agentDelegable` 只表示该 Permission 能否进入 AI ActingGrant；“谁能把 Permission 加入 tenant Role”
  由独立 `GrantAdministrationPolicy` 决定，不能把使用权、Role 分配权和 Agent 委托权混成一个布尔值；
- Permission 的废弃采用状态与兼容窗口，不原地改变既有 code 的含义。

Catalog 物化采用受控同步：应用启动先汇总全部 contributor、校验冲突并计算 definition digest，
再把已知定义以事务方式写入/核对 catalog。多实例启动使用 PostgreSQL transaction advisory lock 与
幂等 upsert 串行化同一 catalog 的同步；实例在同步成功前不进入 ready。数据库存在同 code 不同定义时
启动失败；模块暂时未装配不能自动物理删除历史 Permission，只能通过明确弃用状态和 change audit
收口。RolePermission 引用已弃用 Permission 时不产生新的 ALLOW，并由管理面提示迁移。

### 5.3 Role 与 Binding

```text
Role(id, tenantId?, code, name, status, systemRole, version)
RolePermission(roleId, permissionCode)
SubjectBinding(
  id, subjectRef, roleId, scopeRef,
  validFrom, validUntil, status, version, revokedAt, revokedBy)
```

首版约束：

- Role 只是 Permission 集合，不做继承、组合和嵌套；
- 首个直接 Binding 闭环只面向 USER/SERVICE，不做 Group nesting；ADR-0032 后续以独立
  SubjectSetBinding 支持代码注册的一跳岗位/Team 集合，但 SubjectSet 不成为请求 actor；
- Scope 只有 `GLOBAL`、`TENANT(tenantId)`、
  `RESOURCE(tenantId, resourceType, resourceId)`；
- `GLOBAL` 只允许受控平台 SERVICE 或明确的系统管理流程；普通 tenant 管理员不能授予；
- tenant Role 只能产生同 tenant 的 TENANT/RESOURCE Binding；tenant 为空的 system Role 首版只允许
  tenantless SERVICE 的 GLOBAL Binding，禁止跨两类归属混用；
- 不实现显式 deny binding；默认拒绝与 grant 交集已经足够完成首切片；
- 不在 Binding 内保存任意 JSON 条件、SpEL、Rego 或 SQL；条件由类型化的领域事实提供；
- 可选 `GrantSourceRef(sourceType, sourceId, sourceVersion)` 只由受信 onboarding/ownership/workforce
  流程写入，用于 provenance 与按来源撤销；普通客户端不能自报，Authorization 不为它建立跨模块 FK；
- Identity `TenantRole` 与 Workspace `WorkspaceRole` 不迁移、不双写。兼容 contributor 可把它们
  作为当前关系事实提供给通用决策，但原领域仍是事实源。

部门、岗位和 Team 不进入 JWT 或 `actorType`。组织集成不把通用 Scope 改成树，也不物化每名员工
一条直接 Binding；它以独立 `SubjectSetBinding`、批量 `SubjectSetMembershipResolver` 与
provenance 审计作为紧随直接 Binding 的切片。完整边界见
[`Ainer 组织与员工目录详细方案`](organization-workforce-architecture-plan.md)。

### 5.4 ResourceRef 与 ScopeRef

```java
public record ResourceRef(
        String resourceType,
        String resourceId,
        @Nullable UUID authoritativeTenantId) {}

public sealed interface ScopeRef {
    record Global() implements ScopeRef {}
    record Tenant(UUID tenantId) implements ScopeRef {}
    record Resource(ResourceRef resource) implements ScopeRef {}
}
```

`authoritativeTenantId` 必须由资源所属领域的 resolver 从数据库或可信聚合取得；只有明确的
platform-global resource 可以为空。Controller 可以
传递资源 ID 作为查找键，但不能把请求中的 tenant/owner 当成已确认事实。

merchant、business-location、listing、offer、order 等不是 Scope 枚举成员，而是产品注册的
resource type。只有真实用例需要“可在该资源上分配 Role”时，才允许成为 `RESOURCE` binding
范围；普通父子关系由产品关系 resolver 处理。

### 5.5 领域关系、产品策略与可信事实

Ainer 定义三个窄端口，不建立万能 `authorization_relationship` 表：

```java
public interface AuthorizationFactsProvider {
    boolean supports(String resourceType);
    AuthorizationFacts resolve(FactsRequest request);
}

public interface DomainAuthorizationPolicy {
    boolean supports(String permissionCode, String resourceType);
    GrantPath grantPath(String permissionCode, String resourceType);
    PolicyContribution evaluate(PolicyRequest request);
}

public interface PublicAccessPolicy {
    boolean supports(String permissionCode, String resourceType);
    PublicPolicyContribution evaluate(PublicPolicyRequest request);
}
```

`AuthorizationFactsProvider` 返回本次判断需要的最小事实，例如：

- `USER U controls ACTING_IDENTITY A`；
- `A operator-of MERCHANT M`；
- `LISTING L belongs-to M`；
- `CUSTOMER C owner-of ORDER O`；
- `EMPLOYEE E assigned-to REVIEW_TEAM R`；
- 资源是否 published、sellable、active、editable；
- 当前版本、工作流阶段和风险相关事实。

`DomainAuthorizationPolicy` 明确这些事实如何影响 Permission，并声明该动作需要哪一种 grant path：

- `BINDING_REQUIRED`：后台审核、商家发布等必须先具备 Role/Binding grant，再检查产品关系；
- `RELATION_DERIVED`：顾客 `owner-of order`、咨询 `participant-of inquiry` 等可直接产生该资源上的
  最小 grant，不为每个订单或顾客创建 SubjectBinding；
- `BINDING_OR_RELATION`：产品确有两种合法管理路径时显式声明，不能由 evaluator 猜测。

`grantPath(permissionCode, resourceType)` 是注册时可校验的稳定声明，不根据客户端输入或单次事实动态
切换；`evaluate` 只在选定路径内判断关系、状态与约束。重复或冲突声明启动失败。

`PublicAccessPolicy` 是匿名和公开投影的唯一 grant path。它必须检查产品公开状态并返回 FieldMask
等 obligation；不存在显式 public policy 时，Anonymous 默认拒绝。已登录用户也可以走 public path，
但只能得到与匿名相同的公开字段；个性化字段必须重新走 authenticated path。
请求携带无效、过期或签名错误的 Bearer Token 时仍返回 401，不能为了命中 public path 静默降级为
Anonymous。

约束：

- provider 只接受类型化请求，不接受 Controller 构造的任意 Map；
- provider 通过所属模块的应用端口访问数据，不跨模块查询私表或注入对方 Mapper；
- 缺 provider/policy、provider 异常、事实不完整或资源 tenant 不一致时默认拒绝；
- Ainer 只编排事实与策略，不保存所有产品关系的第二份真相；
- 至少两个独立消费者证明通用 tuple store 的语义与运维价值后，才评估关系集中存储。

### 5.6 受控上下文

```java
public record BusinessActingIdentityRef(
        String identityType,
        String identityId,
        @Nullable UUID authoritativeTenantId) {}

public record AuthorizationContext(
        String requestId,
        String traceId,
        String platformAppCode,
        @Nullable BusinessActingIdentityRef businessActingIdentity,
        Set<String> audiences,
        @Nullable AuthenticationAssurance assurance,
        Instant evaluatedAt) {}
```

`platformAppCode` 必须由服务端根据 registered client/authorized party、audience 与受控 route/app
registration 解析；普通 Header/请求参数最多是匹配候选，不能单独成为渠道事实或切换到另一 App。

`X-Acting-Identity-Id`、请求字段或内部消息字段都只是 selector。产品拥有的
`BusinessActingIdentityResolver` 必须以已认证 principal、platform app 与 selector 实时解析当前可选
业务身份，返回上述类型化引用；不存在、失效、跨主体或渠道不匹配时默认拒绝。产品 policy 继续检查
该已选身份与目标 merchant/location/resource 的 operator/assignment 关系，不能把“能够选择身份”
等同于“能执行任何动作”。原始 selector 不进入 AuthorizationRequest、facts provider 或审计；类型化
引用必须进入 decision audit。临近副作用的重新授权也必须重新解析，防止关系在请求期间失效。

首版允许的上下文仅包含已验证渠道、已解析业务身份、audience、认证方式/时间、当前时间和追踪信息。
Anonymous 的 assurance 必须为空，Authenticated 的 assurance 必须存在；构造器/工厂负责维持该
不变量。IP、设备、地理、金额或数据分级只有真实规则出现后，以显式类型增加。禁止提供通用
`Map<String,Object>` 或让管理员上传表达式。

### 5.7 AuthorizationRequest 与 Decision

```java
public record AuthorizationRequest(
        Requester requester,
        AccessMode accessMode,       // PUBLIC_PROJECTION | AUTHENTICATED
        String permissionCode,
        ResourceRef resource,
        AuthorizationContext context) {}

public record AuthorizationDecision(
        UUID decisionId,
        DecisionOutcome outcome,       // ALLOW | DENY | CHALLENGE
        String reasonCode,
        String policyVersion,
        @Nullable ChallengeRequirement challenge,
        List<DecisionObligation> obligations,
        Instant evaluatedAt,
        @Nullable Instant validUntil) {}
```

Anonymous 只允许 `PUBLIC_PROJECTION`；Authenticated 只有在端点/用例显式声明
`PUBLIC_PROJECTION` 时才走 public path，认证路径拒绝后不能自动回退到公开路径。个性化字段必须是
独立的 AUTHENTICATED 查询/投影，不能在 public DTO 上按“登录了就多返回几列”的隐式分支实现。
AccessMode 由服务端端点/应用用例合同固定，不接受客户端通过 Header、query 或 body 任意选择。

语义：

- `ALLOW`：当前检查点允许继续，但调用方必须执行全部 obligation；
- `DENY`：不得继续，reasonCode 为稳定、低基数安全码；
- `CHALLENGE`：当前动作不得执行，完成要求后必须重新 authorize；
- `AuthenticationChallenge` 表示认证升级，可映射 RFC 9470；
- `TransactionConfirmationChallenge` 绑定 action/resource/version digest，不等于 MFA；
- `ApprovalChallenge` 创建或引用审批流程，不伪装成 OAuth 认证失败；
- 首切片只接通 `AuthenticationChallenge`，另外两类先保留契约边界，不实现通用工作流。

建议的通用 obligation：

- `FieldMask`：返回字段投影约束；
- `DataClassificationCeiling`：允许进入响应、检索或 AI 上下文的数据级别；
- `Watermark`：后台敏感查看的水印要求；
- `AuthorizedUntil`：本次读取或任务检查点的最晚有效时间；
- `RecheckBefore`：要求在副作用、工具调用或外部出网前重新决策。

首版只实现真实切片需要的类型，不以 JSON 创建任意 obligation。

未知 obligation、当前执行点不支持的 obligation 或 obligation 执行失败一律失败关闭。公开读取优先
让 `AuthorizedQueryPlan` 返回类型化 public projection/DTO，避免先装载内部字段再依赖序列化时删除；
若使用通用 FieldMask，响应映射器必须返回可验证的消费回执，缺回执不得发送响应。

`policyVersion` 是用于关联和重放分析的稳定不透明值，至少能关联应用发行版本、Permission catalog
digest、命中的 Binding version 和产品 policy version。它不替代 decisionId，也不被描述成数字签名；
产品 policy 变更必须显式递增版本并进入发布/兼容验证。

## 6. 决策管线

单资源授权固定按以下顺序执行：

```text
0. 入口 adapter 先把可选 selector 交给产品 resolver，得到 BusinessActingIdentityRef 后才构造请求
1. 规范化 requester、accessMode、permission、resource 与已验证 context
2. 按 accessMode 进入且只进入一条分支：
   PUBLIC_PROJECTION
     2.1 解析 PublicAccessPolicy 及其公开 facts；不存在则 DENY
     2.2 计算 public row constraint 与 projection/FieldMask
     2.3 跳过 OAuth scope、DomainAuthorizationPolicy、Role/Binding 与 authenticated relation grant
   AUTHENTICATED
     2.4 校验 issuer / audience / principal 类型并计算 OAuth scope ceiling
     2.5 解析 DomainAuthorizationPolicy 声明的 grant path
     2.6 仅当路径含 Binding 分支时查询 Role / RolePermission / SubjectBinding 并匹配 scope
     2.7 调用 AuthorizationFactsProvider，执行完整 relation/resource state policy
     2.8 对 BINDING_OR_RELATION 分别判断完整分支，禁止混拼残缺条件
3. 执行所选分支的受控上下文、riskTier 与认证强度规则
4. 生成 ALLOW / DENY / CHALLENGE、obligations、reasonCode、policyVersion、decisionId
5. 按 auditLevel 写入包含业务 acting identity 的决策审计并返回
```

任何一步未知、冲突或异常都不能转成 ALLOW。产品 policy 必须显式声明合法 grant path；路径确定后，
该路径在下表列出的全部约束取交集，不能把另一条路径不需要的条件强加进来，也不能因为其中一层
通过就提前放行。

### 6.1 Grant path 真值表

| 路径 | 必须同时满足 | 明确不要求 | tenant 规则 |
|---|---|---|---|
| `PUBLIC` | 显式 PublicAccessPolicy、资源公开状态、渠道约束、可执行 public projection/FieldMask；发送响应前必须执行 | Token、OAuth scope、Role/Binding | 不从请求者推导 tenant；资源归属由 public policy/resolver 确认 |
| `RELATION_DERIVED` | 已认证 principal、issuer/audience、OAuth scope ceiling、完整 owner/participant 等关系、资源状态与上下文 | SubjectBinding | `credentialTenantId` 可空；存在时仍是上限，不存在时关系只能产生当前资源的最小 grant |
| `BINDING_REQUIRED` | 已认证 principal、OAuth scope ceiling、当前有效且 scope 匹配的 Binding，以及 policy 声明的关系/状态 | relation-derived grant 作为替代 | credential tenant 存在时必须与 tenant-owned binding/resource 一致；为空时只有 policy 明确允许 tenantless credential，并通过 business acting identity 或其他类型化服务端关系解析可信 scope 才可继续 |
| `BINDING_OR_RELATION` | 公共认证约束，加上一个**完整** Binding 分支或一个**完整** relation 分支，再与状态/风险相交 | 两个残缺分支拼接 | 采用实际命中分支的 tenant 规则 |

有效登录用户可以显式走 `PUBLIC`，但结果必须与 Anonymous 的 public projection 相同；无效、过期或
签名错误的 Bearer Token 仍在认证层返回 401，不能降级到 public path。`PUBLIC` 不要求 OAuth scope，
其余三条 authenticated path 均要求显式 scope-to-permission ceiling 映射。

### 6.2 Scope 匹配

- `GLOBAL` 匹配所有资源，但必须由 system-only 管理边界签发；
- `TENANT(T)` 只匹配 `resource.authoritativeTenantId == T`；
- `RESOURCE(T,type,id)` 只做精确匹配；
- 首版不做任意父子递归、路径通配或 scope 树；
- 产品需要“经营点授权可作用于其 listing”时，由关系 provider 解析，不由通用 Scope 猜测。

### 6.3 Risk 与 Challenge

Permission 定义 `LOW|MEDIUM|HIGH` 是默认风险等级，产品规则可以在受控范围内提高，不能降低
system-only 或安全操作的最低等级。典型规则：

| 风险 | 示例 | 首版行为 |
|---|---|---|
| LOW | 公开读取、本人普通列表读取 | 满足授权即 ALLOW |
| MEDIUM | 编辑草稿、更新偏好 | 可要求近期已认证会话 |
| HIGH | 发布、授权变更、退款批准、安全日志导出 | 认证强度不足时 CHALLENGE |

`CHALLENGE` 完成后必须使用新的认证结果重新运行完整决策，不能把 challenge 回执直接当业务授权。

## 7. 列表、搜索与工作队列授权

仅有 `authorize(subject, action, object)` 会导致三类错误：先查全量再过滤、逐行 N+1 授权、或为了
性能绕过资源权限。Ainer 必须提供查询前授权扩展，但不输出 SQL 字符串。

建议契约：

```java
public interface QueryAuthorizationPlanner<I, Q> {
    AuthorizedQueryPlan<Q> plan(QueryAuthorizationRequest<I> request);
}

public record QueryAuthorizationRequest<I>(
        Requester requester,
        AccessMode accessMode,
        String permissionCode,
        String resourceType,
        String queryPurpose,
        I requestedQuery,
        AuthorizationContext context) {
}

public sealed interface AuthorizedQueryPlan<Q> {
    record Allowed<Q>(
            Q constraint,
            List<DecisionObligation> obligations,
            String policyVersion) implements AuthorizedQueryPlan<Q> {}

    record Denied<Q>(
            String reasonCode,
            String policyVersion) implements AuthorizedQueryPlan<Q> {}
}
```

`I` 是产品已经完成输入校验的查询意图，`Q` 是授权器生成、由 Repository/search adapter 强制应用的
约束。Query 请求复用单对象请求的 Requester、AccessMode、permission 与类型化 context，并以 resource
type/query purpose 代替具体 resourceId；PUBLIC_PROJECTION 与 AUTHENTICATED 同样只能进入一条管线，
不能自动回退、合并 row 集合或把 authenticated 字段并入 public projection。确需组合公开与个性化
数据时，应用用例必须发起两次显式授权并使用类型化合并策略；任何一条 DENY 都不能被静默忽略。

其中 `Q` 是产品定义的类型化查询约束，例如：

```text
IndustryListingReadConstraint(
  publicOnly,
  allowedMerchantIds,
  allowedLocationIds,
  allowedStates,
  fieldProjection)
```

产品 Repository/search adapter 将其翻译为参数化 PostgreSQL 或搜索引擎过滤条件。Ainer 负责
scope ceiling、Binding 与组合流程，不知道产品表名、列名或搜索 DSL。

Query plan 应优先表达可下推的 join/exists/predicate，不把一个主体可访问的全部资源 ID 先加载成
巨大集合。只有规模明确受限时才允许 bounded ID set，并必须在契约中声明上限。

验收要求：

- 未授权 row 在数据库/搜索查询阶段就被排除；
- 不能把未授权结果先加载到 JVM 再过滤；
- 不能把 subject、tenant、resource ID 或排序字段拼接成 SQL；
- 批量检查必须显式限制规模并记录查询次数；
- 代表数据量执行 PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)`，不预设未经测量的延迟数字。

## 8. Spring Boot 4.1 / Spring Security 7.1 落地

### 8.1 分层

```text
HTTP SecurityFilterChain
  └─ 登录状态、issuer、audience、粗粒度 OAuth scope

Spring AuthorizationManager adapter
  └─ 把 request/method invocation 转成 Ainer AuthorizationRequest

Application use case / transaction
  └─ AuthorizationService.require(...)，执行资源级最终检查

AI context / retrieval / tool / side effect
  └─ 每个安全边界重新检查或消费 obligations
```

授权核心保持 Spring-free。Spring 侧建议提供：

- `AinerRequestAuthorizationManager`：实现
  `AuthorizationManager<RequestAuthorizationContext>`；
- `AinerMethodAuthorizationManager`：实现 `AuthorizationManager<MethodInvocation>`；
- `AinerSpringAuthorizationResult`：保留 decisionId、reason 和 challenge 的适配结果；
- `AuthorizationTargetResolver`：从类型化命令参数解析 ResourceRef；
- `DecisionObligationExecutor`：在明确执行点消费受支持的 obligation 并产生回执；
- 可选 `@AinerAuthorize(permission = "...")`，注解只引用稳定 permission，不保存 SpEL 策略；
- `AinerChallengeAccessDeniedHandler` / `AuthenticationEntryPoint`：区分普通 403 与认证升级 401。

Spring `AuthorizationManager` 是执行适配器，不压扁 Ainer richer decision，也不是数据库策略源。
标准 Spring interceptor 在 ALLOW 后只消费 granted/denied 结果，不会替 Ainer 执行 FieldMask、
RecheckBefore 等 obligation。因此 Request/Method adapter 只可用于“ALLOW obligation 为空或已在该适配器
内完整执行”的门禁；否则返回 `OBLIGATION_UNHANDLED` 并拒绝。需要字段投影或后续检查点的用例必须
走显式 `AuthorizationService` + query plan/obligation executor。写操作仍在应用服务内显式 `require`，
不能只依赖 AOP，以免 self-invocation、异步入口或事务时序绕过。

### 8.2 Step-up 现代化

当前 `RecentStrongAuthenticationFilter` 保持兼容，不在文档变更中直接改变行为。目标迁移为：

1. action/risk 决策返回 `AuthenticationChallenge(acrValues,maxAge)`；
2. Spring adapter 把它映射为拒绝结果；
3. Resource Server 返回 RFC 9470 的 `401` 与
   `WWW-Authenticate: Bearer error="insufficient_user_authentication"`；
4. 客户端回到 Authorization Server 完成所需认证；
5. 新 Token 携带准确 `acr`、`amr`、`auth_time`；
6. 原请求以幂等键重试并重新授权。

Spring Security 7.1 已提供 `FactorGrantedAuthority` 与
`AuthorizationManagerFactories.multiFactor()`，Ainer 已经在 Passkey 安全装配中使用。后续应把
当前笼统的 `mfa,pop` 逐步精确化为受控的 WebAuthn factor/ACR 语义。一个总 `auth_time` 不能证明
多个因子各自的新鲜度；若产品真的需要，必须增加明确 ceremony 或受控因子时间投影。
Resource Server 只有在 issuer 提供可验证的 factor 与时间语义后，才把它们通过受控
`JwtAuthenticationConverter` 投影为带 `issuedAt` 的 `FactorGrantedAuthority`；不能把任意
客户端 claim 或普通 scope 伪装成认证因子。在此之前保留现有 claim filter 的兼容门禁。

兼容期按 action/path 建立互斥迁移清单：同一 action/path 不能同时启用旧
`RecentStrongAuthenticationFilter` 和新 Challenge adapter，否则旧 filter 会先返回 403，使 RFC 9470
响应不可达。迁移一个 action 时，先完成客户端与签发端合同测试，再从旧路径配置中精确移除该 action，
随后启用新 adapter，并在 ADR-0017 中记录被取代的具体范围；未迁移 action 继续使用旧行为。

### 8.3 不把以下能力混为一体

- Spring `GrantedAuthority`：认证结果中的粗粒度视图；
- OAuth scope：Token 能力上限；
- Ainer Permission：业务动作契约；
- SubjectBinding：实时可撤销的授权；
- Relation/Attribute：产品领域当前事实；
- 菜单/路由：服务端 Effective Access 的 UI 投影，不能反向成为授权源。

## 9. 模块与依赖边界

首版新增一个 Maven 模块：

```text
ainer-module-authorization/
└── src/main/java/dev/ainer/module/authorization/
    ├── permission/          catalog 与注册
    ├── role/                Role / RolePermission
    ├── binding/             SubjectBinding / ScopeRef / 撤销
    ├── administration/      assignable policy / Role template / 防提权
    ├── decision/            request / evaluator / audit
    ├── query/               查询授权扩展
    ├── spring/              Spring Security adapter
    └── infrastructure/      MyBatis / PostgreSQL
```

实际包仍按 feature 聚合，每个 feature 内再按需要分 application/domain/infrastructure；上图不是要求
创建六层目录。

边界：

- authorization domain/public contract 不依赖 Spring、Web、MyBatis 或产品模块；
- `ainer-starter-security` 不反向依赖业务 authorization 模块；
- Spring adapter 首版放在 authorization 模块的适配边界，出现第二个重复装配消费者后再评估独立
  starter/API 制品；
- Authorization 拥有 Role、RolePermission、Binding、变更审计与通用决策审计；
- Identity 拥有用户、tenant membership 与认证生命周期；
- Workspace 与产品模块拥有资源、关系和状态；
- AI Runtime 拥有 Agent definition、Tool/Capability catalog 与调用审计；
- 默认仍装配在 `ainer-server` 模块化单体中，不创建授权微服务。

## 10. PostgreSQL 数据模型

所有名称为 Proposed；实现前按数据库规范输出逐表设计说明并核对 63-byte 标识上限。

### 10.1 通用授权首切片

| 表 | 类型 | 主要字段 | 关键约束 |
|---|---|---|---|
| `ainer_authorization_permission` | catalog 投影 | UUIDv7 id、code、action、resource_type、risk_tier、audit_level、system_only、agent_delegable、source_module、status、definition_version | code 唯一；定义冲突启动失败；不允许任意管理端创建；Role 分配权另由 GrantAdministrationPolicy 决定 |
| `ainer_authorization_role` | 可变聚合 | UUIDv7 id、tenant_id?、code、name、system_role、status、version、created/updated | tenant/system 归属明确；乐观版本；系统角色受保护 |
| `ainer_authorization_role_permission` | 关联 | role_id、permission_id、created_at | 复合主键；FK RESTRICT；首版无 JSON 条件 |
| `ainer_authorization_subject_binding` | 生命周期聚合 | UUIDv7 id、issuer、subject_type、subject_id、role_id、scope 字段、valid_from/until、status、version、revoked_*、可选 grant_source_type/id/version | scope 组合 CHECK；过期/撤销不参与决策；GLOBAL 受限；grant source 只能由受信应用流程写入 |
| `ainer_authorization_change_audit` | append-only | UUIDv7 id、tenant、actor、target_type/id、action、before/after version、request/trace、occurred_at | 不更新、不软删；不保存 Token/正文 |
| `ainer_authorization_decision_audit` | append-only | UUIDv7 decision_id、tenant?、requester_issuer/type/id、acting_identity_type/id?、permission、resource_type/id、outcome、reason、challenge、policy_version、request/trace、evaluated_at | 按 auditLevel 写入；高风险失败关闭；只存类型/稳定 ID，不存 selector 原文 |

### 10.2 Organization SubjectSet 后续切片

ADR-0032 的 O2 不改写首切片表；它在直接 Binding 闭环之后增加：

| 表 | 类型 | 主要字段 | 关键约束 |
|---|---|---|---|
| `ainer_authorization_subject_set_binding` | 生命周期聚合 | UUIDv7 id、tenant_id、directory_id、set_type/object_id/relation、role_id、scope 字段、valid_from/until、status、version、revoked_* | owner resolver 产生权威 tenant/directory；Role/Scope/resource 同 tenant；不支持嵌套或任意表达式 |

Organization 事实仍留在 owner 模块；Authorization 不复制成员明细或建立跨模块 FK。岗位/Team 变更
使用 GrantImpactPolicy，decision audit 记录命中的 SubjectSetRef 与事实版本。完整设计见
[`Ainer 组织与员工目录详细方案`](organization-workforce-architecture-plan.md)。

### 10.3 重要数据规则

- Ainer 持久化 ID 使用 PostgreSQL 18 `uuidv7()`；
- `tenant_id` 使用 UUID；外部 opaque subject 保留 issuer + string subject 的显式语义；
- 核心授权查询字段使用普通列，不把 scope、permission、status、subject 或 resource 塞入 JSONB；
- `scope_kind` CHECK：
  - GLOBAL：tenant/resource 全空；
  - TENANT：tenant 非空、resource 为空；
  - RESOURCE：tenant、resource_type、resource_id 全部非空；
- 所有 tenant-owned 管理查询显式绑定 tenant；
- MyBatis-Plus 只用于 catalog/Role/Binding 的简单管理 CRUD；
- 决策解析、有效期、scope 交集、审计游标等使用显式参数 SQL；
- 不启用 tenant interceptor 代替显式条件；
- 审计保留、归档和外部不可变副本沿用 ADR-0010 的原则，但首版不宣称 WORM。

### 10.3 查询形状与索引方向

至少为以下真实查询设计索引并在代表数据量复核：

- subject 当前有效 binding：
  `(issuer, subject_type, subject_id, status, tenant_id, scope_kind)`；
- role 的 permission：`(role_id, permission_id)`；
- tenant 下 Role/Binding 管理分页：`(tenant_id, created_at DESC, id DESC)`；
- 决策审计按 tenant + 时间稳定游标：`(tenant_id, evaluated_at DESC, decision_id DESC)`；
- 按 subject 或 resource 调查的受限查询；
- 过期 binding 清理只改变状态，不物理删除授权历史。

不在详细设计阶段机械创建全部组合索引；最终索引必须映射实际 SQL 与查询计划。

## 11. 管理 API 与防提权

建议最小管理面：

```text
GET    /api/authorization/permissions
GET    /api/authorization/roles
POST   /api/authorization/roles
PATCH  /api/authorization/roles/{roleId}
PUT    /api/authorization/roles/{roleId}/permissions
GET    /api/authorization/bindings
POST   /api/authorization/bindings
POST   /api/authorization/bindings/{bindingId}/revocations
POST   /api/authorization/subject-set-bindings                     # ADR-0032 O2
POST   /api/authorization/subject-set-bindings/{bindingId}/revocations
GET    /api/authorization/effective-access
GET    /api/authorization/decision-audits
```

不使用 DELETE 表示 Binding 撤销；撤销是保留 actor、时间、原因码和版本的状态变化。

管理授权必须解决“谁能授权谁”：

- “能执行 Permission”与“能把 Permission 分配给别人”是两种权限。新增代码注册、版本化的
  `GrantAdministrationPolicy` 与可选 `RoleTemplateContributor`，计算 tenant 可分配 Permission/Scope/
  target subject 集合；不从 grantor 自己的 Effective Access 自动推出分配权；
- 首个 tenant 管理入口仅由现有 ACTIVE Tenant OWNER + 专用 OAuth scope 启动。Tenant ADMIN 首版
  默认不能创建 Role、改 RolePermission 或创建 Binding，避免在零 Binding 初始状态下暗中把 ADMIN
  解释成“拥有全部产品权限”；后续开放必须另有显式管理 Role 与防提权测试；
- 产品/平台 contributor 提供受控的 tenant-assignable catalog/Role template；系统同步只登记可选集合，
  不自动给 OWNER 任何产品使用权，也不自动创建业务 Binding；
- Tenant OWNER 只能在本 tenant、assignable catalog、允许的 Scope 和 ACTIVE 合格目标主体内管理；
  不能处理 GLOBAL、system-only、未登记或不在 GrantAdministrationPolicy assignable 集合内的
  Permission；通用管理 API 不允许创建/扩大自己的 Role/Binding；
- 初始商家 owner/operator 等产品使用权由产品 onboarding/ownership 流程在校验真实业务关系后写入，
  并独立审计，不能通过通用 Role 页面自我声明；
- `agentDelegable` 仅由 ActingGrant 签发检查使用，不参与 Role 分配判断；
- Role/Binding 变更与 change audit 同事务；审计失败则变更回滚；
- Effective Access 是服务器计算的投影，前端隐藏菜单不能替代 API 门禁。

Ainer Admin 通过 OpenAPI/SDK 完成 role → permission → binding → effective access → revoke 最小链路。

## 12. 审计、撤销与缓存

### 12.1 审计分层

| 类型 | 所有者 | 内容 |
|---|---|---|
| Authorization change audit | Authorization | Role/Permission/Binding 管理变化 |
| Authorization decision audit | Authorization | 哪个 principal 选择哪个已解析业务身份，对什么资源执行什么动作得到何种结果 |
| Workspace/产品业务审计 | 所属业务域 | 业务状态变化、所有权转移、发布、退款等 |
| AI invocation audit | AI Runtime | provider、模型、Token、费用、耗时、调用状态与 AI 策略 |

它们通过 `decisionId`、`invocationId`、`grantId`、`requestId`、`traceId` 关联，不物理合并。

审计强度：

- Role/Binding 变更：全部记录，同事务失败关闭；
- HIGH 风险 ALLOW/DENY/CHALLENGE：记录，关键副作用在审计失败时不执行；
- 关键 DENY 与全部 CHALLENGE：记录；
- 普通公开列表读取：指标或采样，不逐 row 写 OLTP 审计；
- 决策审计不保存 Bearer Token、prompt、资源正文、价格正文或 PII。

### 12.2 撤销语义

首版不缓存 ALLOW 或 Binding：

- Binding 撤销事务提交后，下一次授权决策必须读取当前状态并拒绝；
- 仍有效 JWT 不能恢复已经撤销的数据库授权；
- 已开始的普通短请求可能在撤销提交前已经完成检查，文档不能承诺撤回已经执行的副作用；
- HIGH 风险写在副作用前尽量靠近执行点重新检查；若出现必须消除并发窗口的操作，再通过版本条件、
  锁或专用状态机处理；
- 将来增加缓存前必须另行定义 epoch/invalidation、最大失效窗口、故障语义和压测结果。

## 13. AI Agent、Capability 与上下文授权

AI 专项在通用授权之上实施，不阻塞首个 Role/Binding 闭环。

### 13.1 分离四个概念

```text
AgentDefinition    稳定 agentId、版本、用途、状态与 runtime 配置引用
Model              可替换的模型目录项，不是身份
Authenticated caller  调用 AI runtime 的 USER/SERVICE 凭据主体
ActingGrant         principal → agent 的短时委托
```

Agent 不固定拥有某个 principal，modelId 也不能充当 agentId。远程 Agent 首版使用 SERVICE 凭据
认证运行时，再由 agentId/version/grantRef 形成授权上下文。

### 13.2 ActingGrant

```text
ActingGrant(
  id, principalSubjectRef, agentId, agentVersion,
  validFrom, validUntil, status, nonDelegable=true,
  permission subset, scope subset, capability constraints,
  context policy reference, version, revokedAt)
```

规则：

- v1 只允许一层 `principal → agent`，不允许 agent→agent；
- grant 权限、scope、capability 必须是 principal 当前有效权限的子集，且每个 Permission 都显式
  `agentDelegable=true`；
- grant 过期、撤销、principal 失效、agent 版本停用任一成立即拒绝；
- Agent 不能仅凭自身 SERVICE scope扩大 principal 权限；
- 长任务在上下文检索、每次工具调用、外部副作用和结果发布前重新检查；
- 撤销不能收回已发送给外部模型的数据，必须通过最小上下文、provider 数据政策和任务取消降低暴露。

### 13.3 Capability

Capability 是 Tool/Function/Model/Autonomy 的类型化 catalog 与约束，不建立第二套 Role 层级：

它不直接内嵌进 Permission definition：业务动作需要长期稳定，而工具、模型和自主等级由 AI Runtime
独立版本化且变化更快。授权决策对 Permission 与 Capability 取交集，ActingGrant 同时携带两者的最小
子集，因此既不会漏掉执行方式约束，也不会因更换模型/工具重写业务 Role。

- AI Runtime/Tool Registry 拥有 capability 定义、输入 schema、超时、幂等和副作用等级；
- Authorization 只消费稳定 capability ID 与 grant constraint；
- Permission 表达“能否做某业务动作”，Capability 表达“Agent 可使用哪种执行能力”；
- 典型约束包括允许工具、模型族、自主等级、最大调用次数、是否允许外部写、副作用审批要求；
- 不把 capability 条件压成逗号字符串或任意 JSON 表达式。

### 13.4 Context authorization

`ContextScope` 不成为第二套授权系统，而是 AuthorizationDecision 的数据边界 obligation：

```text
授权决策
  → AuthorizedDataBoundary
  → RAG / 业务查询在检索前应用 tenant、resource、relation、classification 过滤
  → ContextSnapshot 只记录实际采用的受控来源引用与策略版本
  → SensitiveDataPolicy 再执行出网内容检查
```

授权决定“什么数据有资格进入上下文”，`SensitiveDataPolicy` 决定“即便有资格，当前内容是否可以
发送给该 provider”。两者不能互相替代。

### 13.5 Token Exchange

只有 Agent 出现真实远程/跨服务边界时，才采用 RFC 8693 Token Exchange 投影 ActingGrant：

```text
sub          被代表的 principal
act          {"sub":"已认证 runtime SERVICE subject"}
agent_id     私有 claim：Agent definition ID
agent_version
tenant_id?   已解析且受限的 tenant；tenantless 场景省略
aud          单一目标服务
scope        requested OAuth scope ∩ subject-token ceiling ∩ client allowlist ∩ grant projection ceiling
grant_id     私有 claim：可在线校验的委托引用
policy_version
jti / short TTL
```

RFC 8693 的 `act` 是 JSON object；在本模型中它表示真正持有凭据的 runtime SERVICE，Agent 由独立
`agent_id/version` 私有 claim 表示。OAuth client authentication 始终按 token endpoint 配置执行；
可选 `actor_token` 只在必须另行证明 actor 时使用，不能把两者当成同一个机制。业务 Permission 与
Capability 继续由 ActingGrant/PDP 判断，不折叠进 RFC `scope`。

不签发 Refresh Token，不允许传递委托；Token Exchange 不替代 ActingGrant 数据库和撤销检查。
RFC 9396 并不只支持第三方或一次性交易；Ainer 当前仅在出现需要结构化授权详情的真实第三方交易
场景时评估 RAR，不把它作为内部 PDP 请求格式。

## 14. 产品场景映射

| 场景 | Ainer 通用部分 | `xq-platform-next` 产品部分 | 期望结果 |
|---|---|---|---|
| 小趣知物匿名读取公开行业信息 | Anonymous、公开策略扩展、query plan、FieldMask | listing 的 PUBLISHED/PUBLIC、商家 ACTIVE、公开字段 | 满足公开条件 ALLOW，否则 DENY |
| 用户选择商家身份编辑 listing | principal、Permission、Binding、resource scope | controls acting identity、operator-of merchant、listing belongs-to merchant、可编辑状态 | 全部关系成立才 ALLOW |
| 发布 listing | risk、CHALLENGE、decision audit | 商家认证状态、版本、发布工作流、publisher capability | 认证不足 CHALLENGE，完成后重新决策 |
| 采购/品控/拍摄/录货协作 | Role/Binding + relation provider | location/partner assignment、产品 capability、员工任职事实、批次阶段与配置 | scope、capability、任职/关系和状态同时满足才 ALLOW |
| Agent 生成 listing 草稿 | ActingGrant、Capability、Context boundary | 允许来源、具体 agent/tool、禁止发布与价格写 | 只写草稿，发布仍回到人员高风险流程 |
| 小趣藏物匿名搜索 Offer | Anonymous、query plan、FieldMask | offer PUBLISHED、sellable、渠道与公开价格/媒资 | 只返回对客公开投影 |
| 商家或商品运营发布 Consumer Offer | 已解析业务身份、`consumer.offer.publish`、Binding/关系、CHALLENGE | offer 所属 Object/merchant、商业状态、渠道、价格与发布状态机 | 只发布 Consumer Offer；Industry Listing 的权限/状态不能推导该权限，反向也不成立 |
| 顾客查看收藏/订单/咨询 | tenantless USER、owner/participant relation | customer 绑定、owner-of order、participant-of inquiry | 不要求 Workspace membership |
| 下单或售后 | Permission、关系、风险 challenge | 价格/库存/地区、订单所有权、售后状态机 | 授权通过后仍由交易域保证业务原子性 |
| 运营审核/客服 | tenant Binding、relation/query planner、Watermark/Mask | assigned team/region/case、审核与客服状态 | tenant ADMIN 不自动获得业务操作 |
| 财务审批/物流 SERVICE | USER/SERVICE、scope ceiling、审批 challenge | requester≠approver、金额阈值、carrier-of shipment、状态转换 | 财务走审批；物流只能更新被分配运单 |

`platform_app`/client/audience 只作为受验证渠道约束。新增伙伴 App、物流 App 或其他消费者时，
增加注册、Permission、产品 relation/provider 和接口，不修改通用 actorType。

## 15. 实施顺序

以下 `I0 + S0–S4` 只是本方案的实现切片，不替代 Ainer 全局 P0–P5 产品化阶段；动态进度只写
`project-status.md`。I0 是两个小程序真实登录合同的前置，不属于 Authorization 模块内部实现。

### I0：tenantless USER 身份与签发合同

交付：

- `ainer-security` 中 tenant-optional `AuthenticatedPrincipal` 与唯一解析端口；旧
  `AuthenticatedActorResolver` 继续严格 tenant-bound；
- Identity 的 membership-independent ACTIVE user 查询/登录投影；
- 受控 consumer OAuth client profile，只签发受限 audience/scope 且不带 `tenant_id/roles` 的 USER
  access token；
- 真实 Authorization Code + PKCE（或合同等价的受信外部 OIDC）签发、Resource Server 验证、撤销、
  账号禁用和 scope 隔离测试；
- tenantless customer → 产品 Customer 绑定由 `xq-platform-next` relation provider 负责，不把
  customerId 放进 Token。

退出条件：无 tenant membership 的 ACTIVE user 能通过真实 issuer 取得 Token 并被新 resolver 解析；
该 Token 被旧 Workspace/Tenant API 拒绝，账号禁用/授权撤销生效，伪造 tenant/customer Header 无效。

### S0：契约与纯决策器

交付：

- Requester、AccessMode、SubjectRef、PermissionDefinition、ResourceRef、ScopeRef、BusinessActingIdentityRef、
  AuthorizationRequest、Decision、Challenge、基础 obligation；
- Permission registry、OAuth scope ceiling mapper、resource/facts/policy registry；
- BusinessActingIdentityResolver 与 GrantAdministrationPolicy 契约；
- 无数据库的 evaluator fixture 与默认拒绝语义；
- Spring-free 核心与空安全契约；
- 单元测试覆盖冲突注册、未知 permission/resource/provider、scope 交集和异常失败关闭。

退出条件：契约可由独立测试模块定义一个非 Ainer 业务 Permission 并完成判断，Ainer 无产品常量。

### S1：PostgreSQL Role/Binding 最小闭环

交付：

- 新模块、六张首切片表与 Flyway migration；
- Role、RolePermission、SubjectBinding 管理应用服务；
- 精确 GLOBAL/TENANT/RESOURCE scope；
- change audit 与按风险写 decision audit；
- 无 ALLOW cache 的实时决策；
- MyBatis-Plus 简单管理 CRUD + 显式授权 SQL；
- PostgreSQL Testcontainers 正反矩阵。

退出条件：对 `BINDING_REQUIRED` 动作，只有 scope 或只有 Binding 都不能放行；
`RELATION_DERIVED` 与 public path 必须由显式产品 policy 注册；撤销提交后原 JWT 的下一次受保护写被拒绝。

### S2：Spring Security 与管理面

交付：

- Request/Method `AuthorizationManager` adapter；
- 应用服务显式 `AuthorizationService.require`；
- 最小管理 OpenAPI、SDK 与 Effective Access；
- Ainer Admin 的 Role/Binding 管理页面所需 API；
- OWNER-only bootstrap、assignable catalog/Role template 与防提权矩阵；
- DecisionObligationExecutor 和未知/未消费 obligation 失败关闭；
- action/risk 驱动的 AuthenticationChallenge 适配原型，保留现有 Step-up 兼容行为。

退出条件：隐藏菜单或绕过 Controller 不能绕过应用服务授权；管理者不能给自己或他人扩大授权上限。

### S3：关系、列表查询与外部 Golden Consumer

交付：

- `AuthorizationFactsProvider` 与类型化 `QueryAuthorizationPlanner`；
- 独立临时消费者通过已安装/发布 Ainer BOM 与制品定义 XQ-like 的
  `merchant.listing.publish`、tenantless customer relation 与 Consumer Offer 独立发布语义；
- 单资源 publish、顾客 owner/participant 路径与公开/商家列表 query plan；
- Maven 3.9+/Maven 4 consumer、PostgreSQL 18、跨 tenant、撤销、查询次数与参数化 SQL 门禁；
- Ainer Admin 完成 role→permission→binding→effective access→revoke；
- 对应脚手架创建门禁 8、9、10 关闭。

退出条件：I0 与 S0–S3 的真实合同均通过，可以创建 `xq-platform-next`；不等待 Agent、RAR、外部
PDP 或所有企业模块完成。

### S4：Agent 代行与 AI 上下文授权

交付：

- AgentDefinition/version、ActingGrant 与子集校验；
- grant permission/scope/capability 结构化子表；
- Context authorization obligation 与 RAG/Tool 检查点；
- grant 撤销与长任务协作取消；
- decision/invocation/context 关联；
- 仅在真实跨服务消费者出现后增加 Token Exchange adapter。

退出条件：Agent 不能扩大 principal 权限，grant 撤销后新的检索/工具/副作用检查失败，AI 审计与
通用授权审计可关联但各自保持所有权。

## 16. 验收矩阵

### 16.1 契约与兼容

- 现有 JWT claim、OAuth scope、TenantRole、WorkspaceRole 和未 opt-in 模块行为不变；
- 真实 Authorization Server/受信外部 issuer 为无 membership USER 签发 tenantless Token，新 resolver
  成功解析而旧 tenant-bound API 明确拒绝；账号禁用、授权撤销与 client scope 隔离真实生效；
- 重复/冲突 Permission definition 启动失败；未知输入默认 DENY；
- authorization 模块关闭时最小应用可启动，但声明依赖动态授权的用例不能静默 permit；
- `AuthenticatedActor` 兼容接口继续服务现有 tenant-bound 用例，新 principal 模型不迫使一次性重写。

### 16.2 安全

- 真实签名 JWT 覆盖 anonymous、USER、SERVICE、错误 issuer/audience、缺 scope、伪造身份 Header；
- 两个 tenant、跨 tenant resource、错误 role、过期/撤销 binding、错误 resource type/id；
- 对 `BINDING_REQUIRED` 动作，“只有 OAuth scope”“只有数据库 grant”“只有产品 relation”均拒绝；
- relation-derived/public 动作必须命中显式注册的产品 policy，不存在 policy 时拒绝；
- tenantless USER → Customer 映射后，本人 wishlist/inquiry/order 的 owner/participant 路径 ALLOW；
  其他 Customer、伪造 customer/tenant、缺关系或 consumer client 缺 scope 均 DENY；
- 资源 tenant/owner 从 provider 取得，不信任 path/body/header；原始 acting identity selector 只有经
  resolver 变成类型化引用后才能参与决策，并进入 audit；
- Anonymous 与有效已登录用户走 public path 时得到相同 public projection；缺 public policy、未知或
  未消费 FieldMask/obligation 均失败关闭，无效 Bearer Token 返回 401；
- PUBLIC_PROJECTION 测试证明不会查询/依赖 Binding 或 authenticated policy；AUTHENTICATED DENY 不
  回退 public，列表也不自动 union 两条路径的 row/字段；
- CHALLENGE 不执行动作，完成后重新运行完整授权；
- 管理 API 覆盖 OWNER-only bootstrap、ADMIN 默认拒绝、assignable catalog、跨 tenant、GLOBAL/
  system-only、agentDelegable 与自提权负例。

### 16.3 数据与查询

- PostgreSQL 18 空库 migrate、存量升级路径和旧二进制回退兼容评估；不依赖生产 down migration；
- migration、FK、CHECK、唯一性、append-only audit、UUIDv7 与 tenant 复合条件真实执行；
- 列表授权在 SQL/search 之前形成 constraint，无先查后滤和 N+1；
- public/商家查询同时约束 row 与类型化字段投影，内部成本、私人联系方式、供应方和内部状态不进入
  public DTO；
- 所有授权 SQL 参数绑定，复杂解析不使用字符串拼接；
- 代表数据量查询计划、查询次数和最坏分页位置形成验证记录。

### 16.4 撤销、审计与可观测性

- Binding 撤销提交后，原未过期 JWT 的下一次保护写 DENY；
- change audit 失败导致管理变更回滚；高风险 decision audit 失败导致动作不执行；
- 审计包含 principal 与已解析 business acting identity 稳定 ID，只含 reason、policyVersion 与关联 ID，
  不含原始 selector、Token、prompt、资源正文和 PII；
- 指标包含 decision outcome、稳定 reason、延迟、provider failure、audit failure 与 challenge；
- 指标标签禁止 tenant、subject、resource ID 等高基数字段。

### 16.5 模块与外部消费者

- ArchUnit/Maven 阻止 authorization domain 依赖 Spring/MyBatis、跨模块私表/Mapper 和 Starter
  反向依赖产品模块；
- Golden Consumer 位于独立临时目录，只通过制品仓库消费，不复制 Ainer 源码；
- 自定义 Permission、resource、facts provider 和 query planner 无需修改 Ainer；
- Consumer Offer 与 Industry Listing 的 publish Permission/状态双向不推导：任一已发布或有权限都
  不能令另一者自动发布；
- Maven 3.9+ 与 Maven 4、PostgreSQL 18、OpenAPI/SDK 均通过项目既有门禁。

## 17. 明确推迟或不采用

首版不做：

- 外部 OpenFGA/Cedar/OPA 强依赖或授权微服务；
- 万能关系表、关系图数据库和递归 scope tree；
- Role hierarchy、Role composition、任意 Group nesting 和显式 deny policy；代码注册的一跳
  Organization SubjectSet 只按 ADR-0032 的后续切片实现；
- 管理员上传 SpEL/Rego/SQL/JSON 条件；
- 每个资源类型一个 Maven 模块或一个专用远程接口；
- 把全部读请求或列表中的每一行写决策审计；
- ALLOW cache、全局分布式一致性或未经定义的撤销窗口；
- 用 PostgreSQL RLS、tenant interceptor、菜单或前端路由替代应用授权；
- 把 PUBLIC、GROUP、AGENT 全部塞进当前 `AuthenticatedActor.actorType`；
- 无限制 agent→agent 代行；
- 把模型 ID 当 Agent 身份；
- 把 AI invocation、业务审计与 authorization decision 合并一张表；
- 用 RAR 替代内部 Permission/Binding/Grant；
- 在没有远程 Agent 消费者时提前实现 Token Exchange。

## 18. 回滚与演进

1. 先发布 schema、module 和管理面，现有端点不自动切换；
2. 以单个用例 opt-in，可进行 shadow decision 对比，但 shadow ALLOW 不能放行；
3. 每个切片独立启用并保留旧业务授权作为明确回退边界；
4. 回滚不得变成“Authorization 不可用就只看 OAuth scope 放行”；应关闭受影响端点或回退该用例；
5. Role、Binding 与审计数据不随应用回滚物理删除；Flyway 继续采用向前追加修复；
6. 未来迁移远程 PDP 前，必须另立 ADR，说明数据投影、模型版本、一致性 SLO、双读 shadow、
   故障语义、容量和回退。

## 19. 创建 `xq-platform-next` 的判断点

本方案不要求 Ainer 先完成所有高级权限与 AI 能力。满足以下条件即可创建首个产品消费者：

1. I0 与 S0–S3 已完成；授权模块进入已发布/可独立消费制品；
2. 外部 Golden Consumer 完成 `merchant.listing.publish`、跨 tenant 拒绝、binding 撤销，以及真实
   tenantless USER → Customer → wishlist/inquiry/order 的 relation-derived ALLOW/DENY 矩阵；
3. Ainer Admin 能管理最小 Role/Binding 与 Effective Access；
4. 结构化 query authorization 能证明公开列表与商家列表既不泄漏未授权 row，也不泄漏内部字段；
   Anonymous 与已登录 public path 得到相同 public projection，未消费 obligation 时失败关闭；
5. Consumer Offer 与 Industry Listing 的发布 Permission/状态双向独立负例通过；
6. 当前脚手架 Gate 8–10 以及 P1/P2 的制品、Initializer、PostgreSQL、OpenAPI 门禁同时满足。

随后立即生成 `xq-platform-next`，由真实产品模块实现 Permission contributor、facts provider 和
query planner。S4 Agent 能力可以紧随首个业务切片推进，但不应无限推迟产品仓库创建。

## 20. 参考

- [Spring Security 7.1 Authorization Architecture](https://docs.spring.io/spring-security/reference/servlet/authorization/architecture.html)
- [Spring Security Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
- [Spring Security Multi-Factor Authentication](https://docs.spring.io/spring-security/reference/servlet/authentication/mfa.html)
- [Spring Security OAuth 2.1 Authorization Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/)
- [RFC 9470：OAuth 2.0 Step Up Authentication Challenge Protocol](https://www.rfc-editor.org/rfc/rfc9470.html)
- [RFC 8693：OAuth 2.0 Token Exchange](https://www.rfc-editor.org/rfc/rfc8693.html)
- [RFC 9396：OAuth 2.0 Rich Authorization Requests](https://www.rfc-editor.org/rfc/rfc9396.html)
- [Google Zanzibar paper](https://research.google/pubs/zanzibar-googles-consistent-global-authorization-system/)
- [OpenFGA Modeling](https://openfga.dev/docs/modeling/getting-started)
- [OpenFGA Authorization for Agents](https://openfga.dev/docs/modeling/agents)
- [Cedar Authorization](https://docs.cedarpolicy.com/auth/authorization.html)
- [OPA Policy Language](https://www.openpolicyagent.org/docs/policy-language)
- [ADR-0005：Identity 与 OAuth 2.1 安全基线](../decisions/0005-identity-and-oauth2-security-baseline.md)
- [ADR-0006：Workspace 租户归属与资源授权基线](../decisions/0006-workspace-tenant-authorization-baseline.md)
- [ADR-0017：Resource Server Step-up 授权策略](../decisions/0017-resource-server-step-up-policy.md)
- [ADR-0018：管理授权模型与租户成员管理](../decisions/0018-management-authorization-and-tenant-member-management.md)
- [ADR-0024：演进式模块化平台架构](../decisions/0024-evolutionary-modular-platform-architecture.md)
