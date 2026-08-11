# ADR-0030：通用混合细粒度授权基线

- 状态：Superseded by [ADR-0037](0037-post-greenfield-authorization-baseline.md)（2026-08-11）
- 日期：2026-08-02（Proposed）
- 决策者：Ainer 项目维护者
- 取代：无
- 局部修订（接受后）：ADR-0005 决策 3、4，仅限 opt-in 通用授权端点的 tenant-optional 认证投影；
  现有 tenant-bound 用例不变
- 被取代：[ADR-0037](0037-post-greenfield-authorization-baseline.md)

> ⚠️ **Greenfield 后状态注释（2026-08-11 复核）**
>
> 本 ADR 文本以 pre-Greenfield 的 tenant 模型为主（`credentialTenantId`、`TENANT(tenantId)`
> scope、tenant-owned 资源、I0 切片的「allowlisted consumer client 无 tenant USER Token」）。
> ADR-0033 Greenfield（Option B）已被接受为地基并完成 S1–S8 施工，**完全移除 tenant 概念**。
> 当前 `ainer-module-authorization` 的实现已随之迁移到 Workspace 语义（`Scope.Workspace/Resource/Global`、
> `workspace_id` 列）。
>
> 因此本 ADR 处于「决策文本与当前地基不一致」状态：
> - **仍有效的核心决策**：grant-path 真值表、RBAC+ReBAC+ABAC 组合语义、默认拒绝、
>   Permission/Role/Binding/Scope 分层、Spring Security 作为适配器而非策略源、审计数据最小化。
> - **已被 Greenfield 推翻的前提**：任何 tenant 绑定、tenant-owned 资源、tenant-bound USER Token、
>   tenant 成员关系作为授权事实的表述。
>
> 截至 2026-08-11，`ainer-module-authorization` 仅有部分原型实现，**未满足本 ADR「验收记录」段所列
> 的任一验收项**。已核实的实现差距见 `docs/project-status.md` §3 差距清单（含 RESOURCE scope CHECK
> 冲突、systemOnly PUBLIC 绕过、change/decision audit 零写入、决策器无生产装配、三应用未依赖授权
> 模块、管理 API 缺防提权矩阵、无真实 JWT 端到端测试、外部 consumer 仅 PermissionCode 编译期 smoke）。
>
> 完整重述本 ADR（以 post-Greenfield Workspace 模型替换 tenant 表述）需新增取代 ADR，不在当前
> 接手批次内完成。本 ADR 状态从 Accepted 回退为 Proposed，以如实反映「核心决策待重述、实现未达验收」。

## 背景

Ainer 已具备 OAuth/OIDC、可信 `AuthenticatedActor`、扁平 OAuth scope、Workspace 资源关系、
Identity tenant 角色、撤销传播、选择性在线 Token 校验和 Resource Server Step-up。当前模型能支持
已有安全切片，但还不能作为 `xq-platform-next` 的通用授权基座：

- `xq-zhiwu` 同时包含匿名公开行业信息、商家/经营点/合作方关系和多岗位协作；
- `xq-shop-next` 的顾客通常没有 Workspace membership，却要访问公开 Offer 与本人订单、收藏、
  咨询和售后；
- 运营后台既需要 tenant 治理，也需要审核、客服、财务等产品职责；
- 未来 App 应复用同一身份与授权原语，不能每新增渠道就增加 actorType、tenant 或一套角色表；
- 只有单对象 RBAC 不能安全表达列表、搜索和工作队列的数据范围；
- AI Agent 的委托、工具能力和上下文授权需要建立在通用资源授权之上，但不应拖入首个闭环。

ADR-0018 在 Identity tenant 成员管理切片中决定“不引入自研角色/权限表”，其含义是
`TenantRole(OWNER/ADMIN/MEMBER)` 继续作为 Identity tenant 治理事实源。它没有禁止为跨产品业务动作
建立通用 Role/Binding。新模型必须保留这一区分，不能迁移或双写 TenantRole/WorkspaceRole。

本 ADR 补充 ADR-0006、0007、0017、0018 与 0024，不改变它们已经交付的领域事实；接受后会局部
修订 ADR-0005 决策 3、4：`ainer-security` 增加 tenant-optional `AuthenticatedPrincipal` 及解析端口，
但现有 `AuthenticatedActor`、tenant-bound resolver 与未 opt-in 用例保持严格兼容，不复制 JWT 解析。
详细类型、表、场景和任务拆解见
[`Ainer 通用授权与 AI 代行详细方案`](../design/authorization-architecture-plan.md)。

## 决策驱动因素

- 同时支持匿名公开访问、tenantless 顾客、tenant 管理人员、业务 operator 与服务身份；
- Permission、Role、资源关系、状态与渠道边界各自只有一个明确所有者；
- OAuth scope 保持 Token 能力上限，不膨胀为动态资源 ACL；
- 单对象授权和集合查询授权都能在读取数据前执行；
- Binding 可撤销，仍有效 JWT 不能恢复已撤销的数据库授权；
- Spring Security 7.1 能作为 HTTP/方法执行适配器，但业务策略保持 Spring-free；
- Ainer 不泄漏小趣产品模型，真实外部消费者无需修改 Ainer 常量或源码；
- 首版能在模块化单体内完成，不新增授权服务、关系数据库或策略 DSL 的运维成本。

## 备选方案

### 方案 A：继续使用 OAuth scope + 固定领域角色

改动最少，但每个新产品会继续创建自己的硬编码角色判断，无法统一管理 Binding、Effective Access、
撤销、查询数据范围和未来 Agent 子集授权。它可继续作为兼容输入，不足以成为脚手架目标。

### 方案 B：只实现通用 RBAC

Role/Permission 易于管理，却无法可靠表达“顾客只能看本人订单”“商家 operator 只能发布所属
listing”“员工只处理分配队列”以及公开资源状态。若把这些规则都塞进 Role，会产生角色爆炸和
陈旧授权。因此不单独采用。

### 方案 C：立即引入 OpenFGA、Cedar、OPA 或远程 PDP

这些系统分别提供成熟的关系模型、类型化策略或策略即代码能力，但当前 Ainer 尚无跨语言消费者、
独立扩缩容需求或远程授权 SLO。现在引入会增加部署、策略发布、数据投影、双写、一致性、调试与
故障域，同时仍不能替代产品状态机。保留为未来适配目标，不作为首版依赖。

### 方案 D：本地类型化混合授权（采用）

以 RBAC + ReBAC + 受控 ABAC 组合决策，在模块化单体内使用 Java 和 PostgreSQL；产品通过稳定端口
提供资源关系与查询约束。它覆盖当前真实场景，也保留未来远程 PDP 的演进边界。采用。

## 决策

### 1. 正式模型

Ainer 通用授权采用“受控 grant path + 约束交集”语义：

```text
authenticated path:
  verified issuer / audience / OAuth scope ceiling
  ∩ (live Role/Binding grant OR explicit relation-derived grant)

public path:
  explicit PublicAccessPolicy

selected path ∩ domain-owned relationship/state facts
  ∩ typed resource and request constraints
  → ALLOW | DENY | CHALLENGE + obligations
```

`OR` 只发生在产品 policy 明确选择的完整路径之间。四条路径的真值表是：

| 路径 | 必须满足 | 不要求 |
|---|---|---|
| PUBLIC | 显式 PublicAccessPolicy + 公开状态/渠道 + 可执行 public projection；发送响应前必须执行 | Token、OAuth scope、Binding |
| RELATION_DERIVED | 有效 principal + issuer/audience + OAuth scope ceiling + 完整 owner/participant 关系与状态 | Binding |
| BINDING_REQUIRED | 有效 principal + OAuth scope ceiling + live scope-matched Binding + policy 声明的关系/状态 | relation grant 作为替代 |
| BINDING_OR_RELATION | 公共认证约束 + 一个完整 Binding 分支或一个完整 relation 分支 + 状态/风险 | 两个残缺分支拼接 |

credential tenant 存在时是不可跨越的 tenant ceiling；为空不会自动产生 tenant 权限。relation-derived
只产生当前关系/资源的最小 grant；tenantless credential 要使用 tenant-scoped Binding 时，产品 policy
还必须显式允许并通过 business acting identity 或其他类型化服务端关系解析可信 scope，否则拒绝。

- RBAC 只负责便于管理的职责集合；
- ReBAC 负责 owner/operator/member/participant/assigned-to 等对象关系；
- ABAC 只使用类型化、可信的资源状态、渠道、时间和认证强度；
- 顾客 owner/participant 等产品策略可产生精确资源上的 relation-derived grant，不为每个顾客或
  订单创建 SubjectBinding；匿名只允许走显式 PublicAccessPolicy，并只能得到公开投影；
- PUBLIC 不要求 OAuth scope；其他 authenticated path 才要求显式 scope-to-permission ceiling；
- 缺失、未知、冲突、provider 异常和策略异常全部默认拒绝；
- Agent ActingGrant 与 Capability 由 ADR-0031 在该模型之上增加。

### 2. 认证主体与授权请求者分离

1. 现有 `AuthenticatedActor` 在首版保持兼容，继续服务 tenant-bound Workspace/Identity 用例。
2. 新增不强制 tenant 的 `AuthenticatedPrincipal`，至少包含 issuer、subjectId、`USER|SERVICE`、
   可空 credentialTenantId、authorities、audiences 与 clientId。credentialTenantId 只是当前
   Token/会话上下文，不是 Subject 固有属性。
   该类型与解析端口位于 `ainer-security`；`ainer-starter-security` 仍是从已验证 Spring
   `Authentication`/JWT 投影主体的唯一位置，Authorization 模块不得再次解析 Token。
3. 授权请求者是 `Authenticated(principal)` 或 `Anonymous`。匿名不是一个已认证 `PUBLIC` actor。
   公共端点收到无效、过期或签名错误的 Bearer Token 时仍返回 401，不能静默降级为 Anonymous。
4. `PUBLIC` 不进入 `AuthenticatedActor.actorType`，`GROUP` 首版不成为请求主体。
5. tenant 是独立请求/资源上下文。credentialTenantId 存在时必须限制 tenant-owned Binding/resource；
   为空时不能凭空推导 tenant。资源 tenant 必须由领域 resolver 取得，不能直接相信 Header、路径或
   请求体。
6. SubjectRef 包含 issuer/namespace，防止不同发行者的相同 `sub` 碰撞。
7. 仅增加可空字段不能宣称支持真实顾客。创建 `xq-platform-next` 前必须实现 membership-independent
   USER 签发档位：ACTIVE Identity user 不要求 tenant membership；只有 allowlisted consumer
   client/audience 可取得不含 `tenant_id/roles`、仅含受限 scope 的 USER Token；tenant 管理、Workspace
   与平台控制面 scope 不可申请。
8. tenantless USER 必须通过真实 Ainer Authorization Code + PKCE 或合同等价的受信外部 OIDC 完成
   签发、Resource Server 解析、授权撤销、账号禁用和 scope 隔离端到端测试，不能用测试 Decoder
   自签 claim 代替。外部 OIDC/微信身份由 provider adapter 绑定稳定 subject，前端 code/openId/
   customerId 不成为可信主体。微信小程序可采用服务端一次性 code exchange + 受控 Authorization
   Server 扩展 grant/登录 ceremony，具体协议另立产品 ADR，不被本 ADR 强制为浏览器重定向流程。
9. 产品业务 Acting Identity 是类型化 `BusinessActingIdentityRef`。Header/请求字段只作为 selector，
   产品 resolver 以 principal + platform app 实时解析；产品 policy 再检查它与目标资源的关系。类型化
   引用进入 decision audit，原始 selector 不进入授权核心或审计。

### 3. Permission 与 OAuth scope

1. Permission 使用稳定 code，并注册 action、resourceType、riskTier、auditLevel、systemOnly 与
   agentDelegable 等受控元数据。
2. Permission definition 由实现模块的代码 contributor 注册；重复 code 或同 code 不同定义启动
   失败。数据库 Permission catalog 是管理投影，不允许管理员创建应用未实现的任意字符串。
   启动时先校验 contributor 并核对 catalog digest；数据库存在冲突定义时失败关闭，缺席模块不能
   自动删除历史 Permission，只能通过明确弃用与 change audit 收口。
   多实例以 PostgreSQL transaction advisory lock + 幂等 upsert 串行化 catalog 同步，成功前实例
   不进入 ready。
3. `AinerSecurityScopes` 保持 OAuth scope 契约。新增显式 `ScopePermissionCeilingMapper`，禁止
   因名称相同自动把 scope 当 Permission。
4. OAuth scope 只给出 authenticated path 的 Token 上限；之后按第 1 节真值表选择 Binding 或
   relation-derived 完整路径。PUBLIC 不要求 scope，RELATION_DERIVED 不要求数据库 Binding。
5. 小趣的 `industry.*`、`consumer.*`、`merchant.*`、`order.*` 等 Permission 由
   `xq-platform-next` 注册，不进入 Ainer 常量。
6. 菜单、前端 route 与 servlet path 不是 Permission 的事实源；它们只能消费 Effective Access。
7. Permission 的 `agentDelegable` 只控制是否可进入 ADR-0031 ActingGrant；Role 分配权由独立
   `GrantAdministrationPolicy`/assignable catalog 决定，不能从“我能使用它”自动推出“我能授予它”。

### 4. Role、Binding 与 Scope

1. Role 是 Permission 集合；SubjectBinding 把 USER/SERVICE、Role 和精确 Scope 关联，并带状态、
   有效期、版本和撤销信息。
2. 首版 Scope 只有：
   - `GLOBAL`；
   - `TENANT(tenantId)`；
   - `RESOURCE(tenantId, resourceType, resourceId)`。
3. 普通 USER 不能获得 GLOBAL；GLOBAL/system-only 只能由受控平台服务或专门安全流程管理。
   tenant Role 只能产生同 tenant 的 TENANT/RESOURCE Binding；system Role 首版只允许 tenantless
   SERVICE 的 GLOBAL Binding。
4. 首版不做 Role hierarchy/composition、Group nesting、显式 deny Binding、递归 Scope 或任意条件 JSON。
   [ADR-0032](0032-organization-workforce-directory-baseline.md) 提议在直接 Binding 闭环之后增加独立、
   代码注册且只解析一跳的 SubjectSetBinding；SubjectSet 不成为认证 actor，也不改变本 ADR 的首版
   直接 USER/SERVICE Binding 门禁。
5. Identity TenantRole 与 WorkspaceRole 不迁移、不双写。兼容 contributor 可以把当前成员关系作为
   实时事实，但所属领域仍是权威。
6. `ResourceRef.authoritativeTenantId` 只对明确的 platform-global resource 可空；tenant-owned 资源由
   产品 resolver 返回非空权威 tenant。首版 RESOURCE Binding 仍必须带 tenant，不允许客户端借可空值
   绕过 Scope 检查。
7. SubjectBinding 可保存由受信 onboarding/ownership/workforce 流程生成的 `GrantSourceRef`，用于
   展示 provenance 和按来源撤销；它不复制来源领域数据、不建立跨模块 FK，也不能由普通管理请求
   自报。没有 source 的手工 Binding 必须在 offboarding/effective access 中显式显示为 residual grant。

### 5. 领域关系与属性

1. Ainer 定义类型化 `AuthorizationFactsProvider`、`DomainAuthorizationPolicy` 和
   `PublicAccessPolicy` registry。产品模块返回最小 owner/operator/participant/assignment/parent/state
   事实，并以注册时可校验、不得随请求动态切换的声明明确动作采用 `BINDING_REQUIRED`、
   `RELATION_DERIVED` 或受控组合 grant path。
2. public policy 是匿名访问的唯一 grant path；不存在显式 policy 时默认拒绝。已登录用户走 public
   path 时也只能得到公开 FieldMask。
3. provider/policy 只通过所属模块公开端口读取，不跨模块查询私表或 Mapper。
4. Ainer 首版不建立万能 relationship tuple 表；至少两个独立消费者证明语义稳定后再评估。
5. 上下文仅包含受验证的 platformApp/audience、认证 assurance/时间、当前时间、requestId/traceId
   以及可选的已解析 BusinessActingIdentityRef 等明确字段；不提供任意 `Map<String,Object>`、SpEL、
   Rego、SQL 或管理员上传规则。
6. `platform_app` 是渠道上下文，不是 tenant、Role 或 Scope，也不自动授予 Permission。
7. AuthorizationRequest 显式携带 `AccessMode(PUBLIC_PROJECTION|AUTHENTICATED)`：Anonymous 只能
   使用前者；已认证请求只有在端点/用例明确选择时才能走 public path，authenticated DENY 不自动
   回退到公开路径。AccessMode 由服务端端点/用例合同固定，不接受客户端 Header/query/body 任意选择。
8. evaluator 按 AccessMode 进入且只进入一条管线：PUBLIC_PROJECTION 只调用 PublicAccessPolicy 及
   其公开 facts/projection，然后直接进入公共 risk/audit 收口，明确跳过 OAuth scope、
   DomainAuthorizationPolicy、Role/Binding 与 authenticated relation grant；AUTHENTICATED 才执行后者。

### 6. Decision、Challenge 与 Obligation

1. 通用 `AuthorizationDecision` 保留 `decisionId`、`ALLOW|DENY|CHALLENGE`、稳定 reasonCode、
   policyVersion、challenge、obligations、evaluatedAt 与可选 validUntil。
2. `CHALLENGE` 表示当前动作不得执行，完成要求后必须重新运行完整授权。
3. Challenge 明确区分：
   - Authentication/Step-up；
   - Transaction confirmation；
   - Human approval。
4. 首切片只实现 AuthenticationChallenge；交易确认和人工审批只保留类型边界，不能误映射成相同
   OAuth 错误。
5. Obligation 用于 FieldMask、DataClassificationCeiling、Watermark、AuthorizedUntil、
   RecheckBefore 等调用方必须执行的约束；只实现真实用例需要的类型，不创建任意 JSON DSL。
6. policyVersion 至少关联应用发行版本、Permission catalog digest、命中 Binding version 与产品
   policy version；它用于审计和重新判断，不被描述成签名或授权凭据。
7. 未知、当前执行点不支持或执行失败的 obligation 默认拒绝。公开读取优先使用 query plan 的类型化
   public projection/DTO；使用 FieldMask 时必须有受控 executor 与消费回执，不能依赖调用方记忆。

### 7. 单对象与集合查询授权

1. 应用服务通过 `AuthorizationService.authorize/require` 执行单资源决策；写操作在副作用前尽量
   靠近事务执行点再次检查。
2. 提供类型化 `QueryAuthorizationPlanner<I,Q>` / `QueryAuthorizationRequest<I>` /
   `AuthorizedQueryPlan<Q>` 扩展。Query request 必须显式携带 Requester、AccessMode、permission、
   resourceType、queryPurpose、已校验产品查询意图与 context；plan 使用 sealed `Allowed<Q>` /
   `Denied<Q>`，DENY 不携带可空 constraint。
3. Ainer 不返回 SQL、表名、列名或搜索引擎 DSL。
4. 禁止先查全量再过滤、逐 row N+1 authorize、字符串拼接 `IN SQL` 或让前端传数据范围。
5. 未授权 row 必须在数据库或搜索查询阶段排除。
6. 查询与单资源复用同一 AccessMode 不变量。PUBLIC 与 AUTHENTICATED 不自动回退、union row 或合并
   字段投影；显式组合用例必须分别授权并使用类型化合并策略，不能忽略任一 DENY。

### 8. Spring Security 7.1 集成

1. HTTP 层只处理公开/认证、issuer、audience、粗粒度 OAuth scope 和少量固定控制面限制。
2. 新增 Spring adapter：
   - `AuthorizationManager<RequestAuthorizationContext>`；
   - `AuthorizationManager<MethodInvocation>`；
   - 用自定义 Spring `AuthorizationResult` 保留 DENY/CHALLENGE 的 decisionId/reason/challenge；
   - 类型化 `AuthorizationTargetResolver`。
3. 可提供 `@AinerAuthorize(permission=...)`，但注解只引用稳定 Permission，不保存任意 SpEL 策略。
4. 高风险业务写仍在应用服务显式调用 AuthorizationService，不能仅依赖 AOP。
5. Spring `GrantedAuthority` 与 `AuthorizationManager` 是认证视图和执行适配器，不成为动态授权
   数据库或产品策略源。
6. 标准 Spring interceptor 在 ALLOW 后不会替 Ainer 执行 FieldMask/RecheckBefore。只有 ALLOW
   obligation 为空或 adapter 已完整执行时才可单独使用 AuthorizationManager；其他用例必须显式调用
   AuthorizationService + query plan/DecisionObligationExecutor，未消费时拒绝。
7. 当前 ADR-0017 的路径型 filter 与 403 行为在兼容期保持不变。迁移到 action/risk 驱动并使用
   RFC 9470 `401 insufficient_user_authentication` 时，需要和客户端/Authorization Server 联合实现、
   验证并明确记录对 ADR-0017 的取代范围。
8. 同一 action/path 不得同时启用旧 filter 与新 Challenge adapter；迁移清单先从旧路径配置精确移除
   已验证 action，再启用新 adapter。否则旧 403 会先返回，使 RFC 9470 401 不可达。

### 9. 模块与所有权

1. 新建单一 `ainer-module-authorization`，不同时创建 api/core/starter/server 四个空 Maven 模块。
2. 模块公开领域契约保持 Spring/MyBatis-free；Spring adapter 和 PostgreSQL adapter 位于明确适配边界。
3. `ainer-starter-security` 不反向依赖业务授权模块。第二个真实重复装配消费者出现后，再评估提取
   独立 starter/API 制品。
4. Authorization 拥有 Permission catalog 投影、Role、RolePermission、SubjectBinding、授权变更
   审计和通用决策审计。
5. Identity 拥有 subject/tenant membership；Workspace/产品拥有资源关系和状态；AI Runtime
   拥有 Agent/Tool/Capability catalog 与调用审计。
6. 默认装配在模块化单体，不建立授权微服务。

### 10. PostgreSQL 与 MyBatis

首切片拟新增：

- `ainer_authorization_permission`；
- `ainer_authorization_role`；
- `ainer_authorization_role_permission`；
- `ainer_authorization_subject_binding`；
- `ainer_authorization_change_audit`；
- `ainer_authorization_decision_audit`。

规则：

- ID 使用 PostgreSQL 18 UUIDv7，tenant 使用 UUID；
- Permission catalog 分开保存 `system_only` 与 `agent_delegable`；Role assignability 由版本化
  GrantAdministrationPolicy 计算，不复用同一字段；
- Scope、Permission、subject、resource、status、时间等核心查询字段使用普通列，不放 JSONB；
- Scope 列组合使用 CHECK，tenant-owned 关系使用显式 tenant 条件和适当复合约束；
- MyBatis-Plus 只用于简单 catalog/Role/Binding CRUD；决策解析、有效期、scope 和审计游标使用显式 SQL；
- 不使用 tenant interceptor 或 RLS 代替应用授权与显式 tenant 条件；
- 最终索引逐项映射真实查询与代表数据量查询计划，不按字段机械全建索引。

### 11. 管理、防提权与 Effective Access

1. 提供 Permission 查询、Role/RolePermission 管理、Binding 创建/撤销、Effective Access 与受限
   decision audit API，并生成 OpenAPI/SDK。
2. Binding 撤销使用显式 revocation 子资源，不物理 DELETE。
3. 初始 tenant 管理入口只复用现有 ACTIVE Tenant OWNER + 专用 OAuth scope。Tenant ADMIN 首版默认
   不能创建/修改 Role 或 Binding；后续开放需显式管理 Role 与独立防提权测试。
4. 代码注册、版本化的 `GrantAdministrationPolicy` 与可选 `RoleTemplateContributor` 提供 tenant
   assignable Permission/Scope/target 集合。该集合不从管理员自身 Effective Access 推导，也不意味着
   OWNER 自动获得任何产品使用权。
5. OWNER 只能给本 tenant 的 ACTIVE 合格目标配置 assignable catalog 内的非 GLOBAL/non-system
   权限，通用管理 API 禁止修改自己的 Role/Binding。初始商家 owner/operator 权限由校验真实关系的
   产品 onboarding/ownership 流程建立并独立审计。
6. `agentDelegable` 与 Role assignability 分离；前者只由 ActingGrant 使用。
7. Role/Binding 变更与 append-only change audit 同事务，审计失败则回滚。
8. Ainer Admin 展示服务器计算的 Effective Access；隐藏菜单不能绕过服务端。

### 12. 撤销、缓存与审计

1. 首版不缓存 ALLOW 或 live Binding。撤销事务提交后，下一次授权决策必须读取当前状态并拒绝，
   即使 JWT 尚未过期。
2. 首版不承诺撤回已完成检查或已经执行的副作用；强并发撤销需求由专用锁、版本条件或状态机解决。
3. 未来增加缓存前必须另立一致性/撤销 ADR，定义 epoch、invalidation、最大窗口、故障语义与压测。
4. Role/Binding 变更、HIGH 风险决策、关键 DENY 和全部 CHALLENGE 按规则写审计；公开列表读取不逐
   row 写 OLTP 审计。
5. Authorization change/decision audit、Workspace/产品审计与 AI invocation audit 分开保存，通过
   decisionId/requestId/traceId 等关联。
6. 审计不得保存 Bearer Token、prompt、资源正文、价格正文、供应商正文或 PII。
7. decision audit 记录 principal 与可选的已解析 BusinessActingIdentityRef 稳定类型/ID，不记录原始
   Header/selector。

## 非目标

- 不替换 OAuth/OIDC、Spring Authorization Server、JWT 校验或现有 tenant-bound
  `AuthenticatedActor` 用例；但必须新增受控 tenantless USER client/profile 与通用认证投影；
- 不迁移 TenantRole/WorkspaceRole；
- 不把 PUBLIC/GROUP/AGENT 一次性加入当前认证 actor；
- 不实现 Agent credential、ActingGrant、Capability、ContextScope 或 agent→agent；
- 不实现通用关系图数据库、Scope 树、Role hierarchy、Group nesting 或显式 deny policy；
- 不自研 Policy-as-Code DSL，不接入外部 PDP；
- 不把菜单、URL、RLS、tenant interceptor 或 JWT authorities 当业务授权源；
- 不为所有读取保存完整 decision row；
- 不拆授权微服务、不加 ALLOW cache、不承诺全球一致性；
- 不把小趣产品资源与状态机写进 Ainer。

## 实施切片

本 ADR 使用 I0 + S0–S3 表示内部切片，不替代产品路线 P0–P5；进度只维护在
`project-status.md`。I0 是真实顾客身份合同前置，不属于 Authorization 模块内部实现。

### I0：tenantless USER 签发与解析

- `ainer-security` 新 principal/解析端口与旧 actor 严格兼容；
- Identity membership-independent ACTIVE user 登录投影；
- allowlisted consumer client 的受限 audience/scope、无 tenant/role USER Token；
- 真实 Authorization Code + PKCE 或受信外部 OIDC 的签发、解析、撤销、账号禁用与 tenant API 拒绝
  端到端测试。

### S0：契约与纯决策器

- 不可变领域类型、Permission/resource/provider registry、scope ceiling mapper；
- 内存 fixture、默认拒绝、冲突注册和异常语义测试；
- Spring-free、`@NullMarked` 公共契约。

### S1：持久化最小闭环

- 六张首切片表、Role/Binding 应用服务、change/decision audit；
- 精确 GLOBAL/TENANT/RESOURCE scope；
- PostgreSQL 18 Testcontainers 正反矩阵；
- 无 ALLOW cache，撤销后下一次保护写拒绝。

### S2：Spring 与管理面

- HTTP/方法 `AuthorizationManager` adapter 和应用服务显式门禁；
- Role/Binding/Effective Access OpenAPI/SDK；
- Ainer Admin 最小管理链路；
- OWNER-only bootstrap、GrantAdministrationPolicy/assignable catalog、防提权、obligation executor 与
  AuthenticationChallenge 适配原型。

### S3：关系、查询与 Golden Consumer

- facts provider 与 query planner；
- 独立外部消费者定义 `merchant.listing.publish`、tenantless customer relation 与 Consumer Offer
  独立发布等非 Ainer 模型；
- 公开 row/字段投影、operator 发布、顾客 owner/participant、跨 tenant、撤销、参数 SQL 和查询次数
  门禁；
- 关闭脚手架创建门禁 8、9、10。

完成 I0 与 S0–S3 后即可创建 `xq-platform-next`；不等待 Agent、RAR、外部 PDP 或全部企业模块。

## 后果

### 正面

- 同一模型覆盖匿名、顾客、tenant 人员、业务 operator、运营人员和服务身份；
- 管理职责、对象关系、资源状态、OAuth scope 与渠道上下文不再混为一个字符串权限；
- 产品可定义资源和关系而不修改 Ainer，满足脚手架独立消费目标；
- 单对象和集合查询使用同一授权边界，降低列表泄漏与 N+1 检查风险；
- Spring Boot 4/Security 7 的 AuthorizationManager、方法安全和 MFA 能力得到实际利用；
- 不提前承担远程 PDP、策略 DSL 和关系集中存储的运维成本。

### 负面与风险

- 混合模型比静态 RBAC 有更多明确边界，需要维护 Permission catalog、provider、query planner 和
  管理防提权测试；
- 领域关系仍由多个模块拥有，错误 provider 可能导致拒绝或泄漏，必须用契约与负向测试约束；
- 无缓存会增加受保护写的数据库读取，需用真实查询形状和索引验证，而不能提前猜测性能；
- Existing Workspace/Identity 与新模型会有兼容期，需要避免双写和两套真相；
- Step-up 从路径/403 迁移到 action/risk/RFC 9470 涉及客户端与签发端，不能只改 Resource Server。

## 安全、数据与隐私

- 默认拒绝，资源 tenant/owner 只取自可信 resolver；
- 管理 API 实施 OWNER-only bootstrap、GrantAdministrationPolicy/assignable catalog、system-only、
  tenant 边界与禁止自改，防止权限管理员自提权；
- 所有 tenant-owned SQL 显式绑定 tenant，参数化查询，拒绝客户端拼接数据范围；
- decision reason 使用低基数安全码，不向匿名/非成员泄露资源存在性或策略内部细节；
- 审计采用数据最小化，不保存凭据、业务正文或 AI 正文；
- 公开访问不伪造认证身份，仍由产品公开状态和字段 mask 决定可见内容；
- 授权通过不替代业务事务、数据库约束、库存、金额、发布或售后状态机。

## 运维与迁移

1. 先发布 schema/module，现有端点不自动切换；
2. 每个用例显式 opt-in，可做 shadow 比较，但 shadow 结果不能放行；
3. Role/Binding/Audit migration 向前追加，不要求生产 down migration；
4. 回滚下线或回退具体用例，不允许在 Authorization 失败时退回“只看 scope 放行”；
5. 已形成的 Role、Binding 与审计保留可读，不随二进制回滚删除；
6. 未来远程 PDP 必须另立 ADR，定义数据投影、策略版本、一致性 SLO、双读 shadow、容量和回退。

## 验收记录

截至 2026-08-02，本 ADR 仅完成设计审查，未新增代码、migration、模块或运行配置，状态保持
Proposed。接受前至少完成：

- I0 与 S0–S3 全部切片；
- PostgreSQL 18 空库与升级验证、授权查询计划和跨 tenant 负向矩阵；
- 真实 issuer 为无 membership USER 签发 tenantless Token，并通过新 resolver、撤销/禁用、consumer
  scope 隔离和旧 tenant API 拒绝端到端测试；
- 真实签名 JWT 的 issuer/audience/scope 以及逐 grant path 真值表测试；
- 撤销 Binding 后原 JWT 的下一次保护写拒绝；
- 管理变更审计失败回滚、高风险决策审计失败关闭；
- Ainer Admin OWNER-only bootstrap、assignable catalog、ADMIN 默认拒绝、自提权负例与最小管理闭环；
- 外部 Golden Consumer 只通过已发布制品定义产品 Permission/resource/provider/query plan，并覆盖
  tenantless Customer owner/participant、Consumer Offer/Industry Listing 发布双向独立负例；
- 真实 HTTP/序列化验证 Anonymous 与 logged-in public path 的 row/字段投影相同；缺 public policy、
  未知或未消费 obligation 失败关闭；
- PUBLIC_PROJECTION 不触发 authenticated policy/Binding，AUTHENTICATED DENY 不回退 public，查询不
  自动 union 两条路径的 row/字段；
- Maven 3.9+/Maven 4、OpenAPI/SDK 与既有模块零回归门禁。

## 参考

- [详细方案](../design/authorization-architecture-plan.md)
- [Spring Security Authorization Architecture](https://docs.spring.io/spring-security/reference/servlet/authorization/architecture.html)
- [Spring Security Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
- [Spring Security Multi-Factor Authentication](https://docs.spring.io/spring-security/reference/servlet/authentication/mfa.html)
- [RFC 9470：OAuth 2.0 Step Up Authentication Challenge Protocol](https://www.rfc-editor.org/rfc/rfc9470.html)
- [Google Zanzibar paper](https://research.google/pubs/zanzibar-googles-consistent-global-authorization-system/)
- [OpenFGA Modeling](https://openfga.dev/docs/modeling/getting-started)
- [Cedar Authorization](https://docs.cedarpolicy.com/auth/authorization.html)
- [OPA Policy Language](https://www.openpolicyagent.org/docs/policy-language)
- [ADR-0005：Identity 与 OAuth 2.1 安全基线](0005-identity-and-oauth2-security-baseline.md)
- [ADR-0006：Workspace tenant 与资源授权](0006-workspace-tenant-authorization-baseline.md)
- [ADR-0017：Resource Server Step-up](0017-resource-server-step-up-policy.md)
- [ADR-0032：组织与员工目录基线](0032-organization-workforce-directory-baseline.md)
- [ADR-0018：管理授权模型与租户成员管理](0018-management-authorization-and-tenant-member-management.md)
- [ADR-0024：演进式模块化平台架构](0024-evolutionary-modular-platform-architecture.md)
