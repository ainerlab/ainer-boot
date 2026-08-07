# ADR-0033 Greenfield 原子 Cutover 完整执行规划

## 文档状态

- 状态：S1–S8 全部完成并验证（205/0/0/0，0 skipped）
- 日期：2026-08-06（S8 完成于 2026-08-07）
- 适用：`reset/0033-greenfield` 分支的 S1.x 原子 cutover（C1–C4 合并为一个不可分割切片）
- 依据：[ADR-0033 Greenfield](../decisions/0033-account-workspace-subject-isolation-greenfield-baseline.md) +
  [Cutover 执行计划](0033-greenfield-cutover-plan.md) +
  [Impact 文档](ainer-foundation-greenfield-reset-impact.md)
- 前序地基：`reset@db71351`（C1 地基：ServicePrincipal 领域+持久化+foundation bean 装配+错误类型）

> 本文档把 cutover-plan 的 C1–C4 合并视角落成完整施工蓝图。它补齐 cutover-plan §3 未覆盖的
> 隐藏缺口（password credential store、securityEpoch claim 基线、workspace/ai-runtime 去 tenant 化），
> 给出每个阶段的文件清单、依赖顺序和测试门。本文档不授权立即改代码，但为后续多次会话提供
> 可逐阶段执行、可验证的施工序列。实时完成状态始终以 [`project-status.md`](../project-status.md) 为准。

---

## 0. 现实评估：为什么不能一次性写完

原子清零的真实工作量约 **140 个文件**，分布在 4 个工程区域：

| 区域 | 难度 | 估文件数 | 阻塞原因 |
|---|---|---|---|
| foundation 能力补全（password store / Profile / securityEpoch） | 困难 | ~12 | foundation 只有领域骨架，缺登录/撤销支撑 |
| AuthServer 登录/token 链路切换 | 困难 | ~25 | 6 条链路依赖 foundation 能力 |
| workspace 去 tenant 化 | 极高 | ~30 | TenantId 是领域一等字段，贯穿全部持久化层 |
| ai-runtime 去 tenant 化 | 高 | ~15 | tenantId 是执行上下文必填字段 + 限流 key |
| legacy 删除 + migration squash | 中等 | ~50 删除 | 波及面广但机械 |

**关键结论**：cutover-plan §C4 的"C1–C4 同原子切片"是正确原则，但其前提（foundation 已具备
登录、密码、撤销能力）尚未满足。本规划把原子切片拆成**有序的可验证施工序列**，每个序列完成后
基线必须保持绿，最终在一个原子提交里完成 C4 删除。

---

## 1. 隐藏缺口（cutover-plan §3 未覆盖，本规划补齐）

### 缺口 A：Password Credential Store

`LoginIdentity` 明确不存储密码材料（`LoginIdentity.java:15-17`），`LoginIdentityType` 无 PASSWORD
类型（现有 USERNAME/EMAIL/PHONE/WECHAT/OIDC/PASSKEY）。但密码登录需要：

- `AinerUserDetailsService.loadUserByUsername` 必须取到 passwordHash 做认证
- bootstrap runners 要写入初始密码
- Passkey 链路不依赖密码（它是独立 ceremony）

**设计**：新建 `ainer_identity_credential` 表，存储 credential material（password hash / WebAuthn
public key 引用），以 `(account_id, credential_type)` 为键。credential_type 区分 PASSWORD / WEBAUTHN /
OIDC_SUBJECT。这样 LoginIdentity 保持"标识绑定"语义，credential 表保持"凭据材料"语义，二者正交。
密码哈希使用项目既有的 Delegating PasswordEncoder（`IdentityModuleConfiguration.identityPasswordEncoder`）。

### 缺口 B：securityEpoch Token Claim 基线

`RevocationAwareOAuth2AuthorizationService` 依赖 `IdentityTokenStatusService.isAccessTokenActive(tenantId,
subjectId, issuedAt)`。greenfield 用 `HumanAccount.securityEpoch` 替代，但需要"签发时 epoch"基线。

**设计**：customizer 签发时写入 `sec_epoch` claim（当前 account 的 securityEpoch）。校验时
比对 claim 里的 epoch == 库里当前 epoch，不等即失效（账号被禁用/重置 epoch 后，旧 token 失效）。
这是 claim 契约的扩展，需同步更新 `ReferenceTokenProfileResolver` 的解析逻辑（`sec_epoch` 为
optional claim，仅新 profile 携带；SERVICE_V1 的 epoch 来自 ServicePrincipal）。

### 缺口 C：WorkspaceRef / Access Ceiling

cutover-plan §3 第 3 项已指出：`AuthenticatedPrincipal` 无 workspace access ceiling，`USER_WORKSPACE_V1`
需要 `WorkspaceRef`。但 workspace 去 tenant 化（见 §2 S6）后，workspace 的访问控制从 tenant partition
变成纯 membership，ceiling 的语义需要重新定义。

**设计**：`USER_NEUTRAL_V1`（无 ceiling，纯身份 token）先落地；`USER_WORKSPACE_V1` 在 workspace
去 tenant 化完成后再定义 ceiling 机制（可能是一次性列举 workspace ID 集合，或运行时查 membership）。
C1–S5 阶段只实现 USER_NEUTRAL_V1 + SERVICE_V1。

---

## 2. 施工序列（有序、可验证、最终原子合入）

每个序列（S）完成后必须 `./mvnw clean verify` 跑绿。S2–S7 是加法/共存，S8 是破坏性删除（原子点）。
当前进度：C1 地基（ServicePrincipal）已完成（`db71351`）；S2 foundation 能力补全已完成
（password credential store + HumanProfile + securityEpoch 无关的 profile 读写，全 reactor 388/0/0/0）；
S3 customizer 新 profile 签发已完成（SERVICE_V1 + USER_NEUTRAL_V1 轨道，fail-closed，
全 reactor 401/0/0/0）；S4 登录链路与 Passkey foundation 接线已完成，
全 reactor 407/0/0/0）；S5 Resource Server typed profile resolver 已完成，
全 reactor 411/0/0/0）；S6 canonical Workspace 去 tenant 已完成，
全 reactor 387/0/0/0）；S7 AI Runtime 去 tenant 已完成，
全 reactor 387/0/0/0。

### S2 — Foundation 能力补全（password credential store + Profile + securityEpoch 查询）

**目标**：让 foundation 具备支撑登录认证的全部能力，不接 runtime。

**新建文件**：
- `foundation/Credential.java`（record：credentialId/accountId/CredentialType/credentialData/status/createdAt/rotatedAt）
- `foundation/CredentialType.java`（enum：PASSWORD / WEBAUTHN_PUBLIC_KEY / OIDC_SUBJECT）
- `foundation/CredentialStatus.java`（enum：ACTIVE / REVOKED）
- `foundation/CredentialRow.java` + `CredentialMapper.java` + `CredentialMapper.xml`
- `foundation/CredentialRepository.java` + `MybatisCredentialRepository.java`
- `foundation/HumanProfile.java`（record：accountId/displayName/avatarUrl，0:1）
- `foundation/HumanProfileRow.java` + `HumanProfileMapper.java` + `HumanProfileMapper.xml`
- `foundation/HumanProfileRepository.java` + `MybatisHumanProfileRepository.java`
- `IdentityFoundationService` 扩展：`registerWithPassword`、`findCredentialForLogin`、`updateProfile`、`rotatePassword`
- `db/migration/V202608290300__create_credential_and_profile.sql`
- `IdentityErrorCode` 补码：`CREDENTIAL_NOT_FOUND` / `CREDENTIAL_REVOKED` / `INVALID_CREDENTIAL` / `PROFILE_NOT_FOUND`
- `IdentityModuleConfiguration` 补 Credential/HumanProfile bean 装配

**schema 要点**：
- `ainer_identity_credential`：id(UUIDv7 PK) / account_id(FK) / type / credential_data(TEXT, password hash) /
  status / created_at / rotated_at；部分唯一索引 `(account_id, type) WHERE status='ACTIVE'`
- `ainer_identity_human_profile`：account_id(PK, FK) / display_name / avatar_url / updated_at

**测试门**：
- `IdentityFoundationServiceTest` 扩展：注册+密码、查凭据、Profile 读写、密码轮换
- `IdentityFoundationPersistenceTest` 扩展：credential/profile 表 PG 验证（含唯一索引冲突）
- migration 计数断言同步（identity 14→15, authserver 22→23）

### S3 — Customizer 新 profile 签发（SERVICE_V1 + USER_NEUTRAL_V1）

**目标**：customizer 能签发新 profile token（含 sec_epoch claim），legacy 轨道共存保留。

**改动文件**：
- `AinerAuthorizationServerConfiguration.ainerJwtTokenCustomizer`：
  - 注入 `ServicePrincipalFoundationService` + `HumanAccountRepository`
  - SERVICE_V1 轨道：client setting `ainer.token-profile=SERVICE_V1` → foundation sub（ServicePrincipal）+
    `token_profile` + `claim_contract_version=1` + `actor_type=SERVICE` + `sec_epoch`（principal 的）；
    principal 不存在/非 ACTIVE 则 fail-closed
  - USER_NEUTRAL_V1 轨道：AinerUserDetails（S4 改造后）→ HumanAccount sub + profile claims +
    `sec_epoch`（account 的）+ 保留 amr/auth_time
  - legacy 轨道保留（无 setting 的 client 走旧 claims）
- `AinerUserDetails` 重设计：去 tenantId，subjectId → HumanAccount.accountId，新增 securityEpoch 字段
- 新增常量 `TOKEN_PROFILE_SETTING = "ainer.token-profile"` + `SEC_EPOCH_CLAIM = "sec_epoch"`

**测试门**：
- SERVICE_V1 token：sub=ServicePrincipal ID（≠client_id）、token_profile/contract_version/actor_type 正确、无 tenant_id
- USER_NEUTRAL_V1 token：sub=HumanAccount ID、sec_epoch 正确、无 tenant_id/roles
- legacy 轨道零回归（现有 117 测试全过）
- SERVICE_V1 principal 缺失时 fail-closed

### S4 — 登录链路切 foundation（AinerUserDetailsService + bootstrap + Passkey）

**目标**：登录认证从 foundation 取数据，不再依赖 legacy IdentityUser/TenantMembership。

**改动文件**（约 12 文件）：
- `AinerUserDetailsService`：改调 `IdentityFoundationService.findCredentialForLogin(username)` →
  返回 (HumanAccount, Credential passwordHash)；构造去 tenantId 的 AinerUserDetails
- `AinerJdbcPasskeyCredentialRepository`：subject_id → account_id，credential 绑定 HumanAccount；
  register 时改调 foundation 查 account（非 IdentityApplicationService）
- `AinerPasskeyConfiguration` / `AinerPasskeyTenantSubjectGuard`：去 tenant 依赖或重命名
- `PlatformTenantBootstrapRunner`：改调 `IdentityFoundationService.registerWithPassword(authority, USERNAME, ...)`
- `AinerAdminDevFixtureRunner`：同上，去 tenantCode/tenantName
- `PlatformTenantBootstrapProperties` / `AinerAdminDevBootstrapProperties`：字段调整（去 tenantCode/Name，留 username/password/displayName）
- Passkey recovery/enrollment/admin-recovery 链路：subject_id → account_id 迁移（schema + mapper + service）
- `ainer_passkey_*` 表：subject_id 列语义迁移到 account_id（ALTER + 数据迁移或 squash）

**测试门**：
- 真实登录 flow（密码 + PKCE）走 foundation，token 携带 USER_NEUTRAL_V1 profile
- Account-with-zero-Workspace 完成认证（无 tenant 也能登录）
- Passkey ceremony 端到端（registration/authentication）
- bootstrap runner 创建 foundation 账号
- dev fixture runner 创建多用户

### S5 — Security starter 接 TokenProfileResolver（C2）

**目标**：Resource Server 解析新 profile token 为 `AuthenticatedPrincipal`，与 legacy 共存。

**改动文件**（约 5 文件）：
- 新建 `JwtToVerifiedJwtClaims` adapter（Spring `Jwt` → `VerifiedJwtClaims`，映射 issuer/subject/audience/expiresAt/claims）
- 新建 `AuthenticatedPrincipalResolver`（读 SecurityContext 的 Jwt → TokenProfileResolver.resolve → AuthenticatedPrincipal）
- `AinerResourceServerAutoConfiguration`：注册新 resolver bean（与 legacy `AuthenticatedActorResolver` 共存）
- `ReferenceTokenProfileResolver`：支持 optional `sec_epoch` claim 解析
- 新 profile fail-closed 测试（缺/未知/矛盾 profile → 401）

**测试门**：
- 新 profile 请求解析为 typed AuthenticatedPrincipal（Human/Service 正确分流）
- 缺/未知/矛盾 profile fail-closed（401）
- legacy AuthenticatedActor 路径零回归

### S6 — Workspace 去 tenant 化（C4 的最大前置工程）

**目标**：Workspace 领域/持久化/API 移除 TenantId，改为纯 membership 访问控制。

> 这是整个 cutover 工作量最大的部分，约 30 文件。建议作为独立子工程，可与 S3–S5 并行（不同模块）。

**改动范围**：
- 领域：`Workspace` / `WorkspaceMember` 去 tenantId 字段；`TenantId` 值对象删除
- 持久化：所有 Row（WorkspaceRow/WorkspaceMemberRow/WorkspaceAuthorizationAuditRow/...）去 tenant_id；
  Mapper XML 去所有 `AND tenant_id = #{tenantId}` 谓词；Repository 接口去 TenantId 参数
- 应用：`WorkspaceApplicationService` 去 `actor.tenantId()`；访问控制纯 membership
  （`memberRepository.findByWorkspaceAndSubject(workspaceId, subjectId)`）
- API：`WorkspaceResponse` / `WorkspaceMemberResponse` 去 tenant 字段
- Identity directory 检查：去 tenant 维度（`isActiveMember(workspaceId, subjectId)`）
- migration：workspace 表去 tenant_id 列（S8 squash 时统一处理，S6 先在应用层兼容）

**测试门**：
- 跨 Workspace DENY（无 tenant partition 仍隔离）
- workspace CRUD 全功能
- 审计/owner recovery 无 tenant 维度
- workspace 测试全绿

### S7 — AI Runtime 去 tenant 化

**目标**：AI Gateway / Task / Invocation 去 tenantId，限流 key 改用 subject 或 authority。

**改动范围**（约 15 文件）：
- `GovernedAiExecutionContext`：去 tenantId 必填字段，`isService()` 改用 PrincipalSubjectRef 类型判断
- `AiGatewayController` / `AiTaskRunService`：去 actor.tenantId()，改 AuthenticatedPrincipal
- 领域：`AiTask` / `AiInvocation` / `ContextSnapshot` 去 tenantId
- 持久化：Row / Mapper / Repository 去 tenant_id
- `TenantRateLimiter` → `SubjectRateLimiter`（按 subjectId 限流）或 `AuthorityRateLimiter`
- `application.yaml`：`tenant-daily-budget` → `subject-daily-budget`

**测试门**：AI invoke / Task run 全功能，限流按新 key

### S8 — 原子删除 legacy + squash migration（C4 核心，破坏性，不可逆）

**目标**：删除全部 legacy identity 代码 + migration，重建可从空库重放的 baseline。

**状态**：✅ 已完成（2026-08-07）。基线重建为 4 个 standalone baseline（identity=
V202608070300、workspace=V202608070310、ai-runtime=V202608070320、
authorization-server=V202608070330），空库重放验证通过。

**前提**：S2–S7 全部完成且全绿。这是不可逆点，必须是单个原子提交。

**删除清单**：
- identity `account/` 全部 legacy 领域/应用/基础设施（IdentityUser/IdentityTenant/TenantMembership/
  TenantRole/IdentityAccount/IdentityDirectoryEntry/IdentityApplicationService/IdentityRepository/
  MybatisIdentityRepository/IdentityTokenStatusService/...约 50 文件）
- AuthServer legacy controllers（`identity/` 下 TenantMember/Ownership/Provisioning/Directory/
  PlatformIdentity*/NotificationReceipt*/...约 20 文件）
- `tenantcontext/` 全部（AinerTenantSelectionFilter/AinerTenantSelectionController/MyTenantContextController）
- `RevocationAwareOAuth2AuthorizationService`（改用 securityEpoch 直接比对）
- legacy `AuthenticatedActor` / `AuthenticatedActorResolver` / `SecurityContextAuthenticatedActorResolver`
  （被 AuthenticatedPrincipal 取代）
- `AinerResourceServerProperties.tenantClaim` / `AuthenticatedService` 的 tenantId
- foundation 包提升为 identity 主体（`foundation/` → `account/` 或扁平化）

**migration squash**：
- ✅ 删 legacy identity（12 个）+ workspace tenant 相关 + authserver tenant 相关 migration
- ✅ 重建可从空库重放的 baseline（foundation 表 + standalone Workspace + service principal + credential）
- ✅ 旧库不可原地升级（Greenfield 前提，ADR-0033）

**测试门（必须全过）**：
- ✅ 空库重放新 baseline
- ✅ 运行代码/API/schema/JWT 语义 inventory 无 `tenant`（全仓 grep `tenant_id` / `tenantId` 仅剩测试内
  负向断言"列不存在"与参数命名）
- ✅ Account-with-zero-Workspace 完成认证
- ✅ Personal Workspace 幂等 provisioning
- ✅ 撤销 fail-closed（securityEpoch 不等 → token 失效）
- ✅ 全量 `./mvnw clean verify` 205/0/0/0（0 skipped）

---

## 3. 依赖图与执行顺序

```
S2 (foundation 能力补全)
 └─ S3 (customizer 新 profile)           ── 依赖 S2 的 password/epoch
     └─ S4 (登录链路切 foundation)        ── 依赖 S2 + S3
         └─ S5 (security starter C2)      ── 依赖 S3 的 claim 契约
 S6 (workspace 去 tenant)                ── 独立，可与 S3-S5 并行（不同模块）
 S7 (ai-runtime 去 tenant)               ── 独立，可与 S3-S5 并行（不同模块）
     全部完成后 ──► S8 (原子删除 + squash) ── 不可逆点
```

**建议执行顺序**：S2 → S3 → S4 → S5 → S6 → S7 → S8
S6/S7 改的是 workspace/ai-runtime 模块，与 S3–S5（identity/security 模块）无编译依赖，可并行。

---

## 4. 关键设计决策（本规划落定，收口 cutover-plan §3）

| # | 决策 | 本规划落定 | 状态 |
|---|---|---|---|
| 1 | 错误类型 | BusinessException(IdentityErrorCode) | ✅ C1 地基完成 |
| 2 | ServicePrincipal 领域 | principal 表 + 独立 binding 表 | ✅ C1 地基完成 |
| 3 | WorkspaceRef/ceiling | USER_NEUTRAL_V1 先落地；USER_WORKSPACE_V1 待 S6 后定义 | ✅ USER_NEUTRAL_V1 S3 完成；USER_WORKSPACE_V1 📋 S6 |
| 4 | foundation 包位置 | S8 后提升为 identity 主体 | ✅ S8 完成（foundation/ 为 identity 主体包） |
| 5 | migration squash | S8 重建 baseline，旧库不可原地升级 | ✅ S8 完成（4 个 standalone baseline） |
| 6 | id-source | Configuration 显式 @Bean 绑 repo::nextUuidV7 | ✅ C1 地基完成 |
| 7 | resolver 边界 | customizer 直接构造 claim；starter 用 ReferenceTokenProfileResolver 解析 | ✅ S3/S5 完成 |
| 8 | selectByTypeAndIdentifier | ACTIVE only | ✅ C1 地基完成 |
| 9 | 测试计数 | S8 重校 | ✅ S8 完成（205/0/0/0，0 skipped） |
| **A** | **password credential store**（新缺口） | 新建 ainer_identity_credential 表，S2 落地 | ✅ S2 完成 |
| **B** | **securityEpoch claim 基线**（新缺口） | customizer 写 sec_epoch claim，S3 落地 | ✅ S3 完成 |
| **C** | **workspace 去 tenant**（新缺口） | S6 整体重写持久化层，纯 membership 访问控制 | ✅ S6 完成 |

---

## 5. 风险与不变量

- **不可逆点只有 S8**：S2–S7 都是加法/共存，任何一步失败可独立回退
- **S8 前必须全绿**：S8 删除后若发现 S2–S7 的缺陷，回退成本极高
- **fail-closed 贯穿**：新 profile 路径任何缺失（principal/credential/epoch）必须抛错，不回退 legacy
- **零回归**：S3–S5 的共存期间，legacy 路径行为必须完全不变（现有测试是回归门禁）
- **不跨阶段提交半成品**：每个 S 完成且全绿才提交；S8 必须是单个原子提交
- **workspace/ai-runtime 是最大风险**：去 tenant 化触及领域核心，必须有充分的集成测试覆盖

---

## 6. 会话执行建议

每个 S 是一个独立的会话工作单元：

- **每次会话一个 S**：完成、验证全绿、提交后再进下一个
- **S6/S7 可并行**：如果有多人/多会话，workspace 和 ai-runtime 去 tenant 可同时进行
- **S8 前做一次全量 review**：确认 S2–S7 全绿且无遗漏，再执行不可逆删除
- **进度同步**：每个 S 完成后更新 `project-status.md` 和本文档的决策表状态

---

## 参考

- [ADR-0033 Greenfield](../decisions/0033-account-workspace-subject-isolation-greenfield-baseline.md)
- [Cutover 执行计划（C1–C5 原始版）](0033-greenfield-cutover-plan.md)
- [Impact 文档（删除/重建/Stage 0–8）](ainer-foundation-greenfield-reset-impact.md)
- [Foundation v1 路线图](ainer-foundation-v1-roadmap.md)
- [Identity Foundation v1 实施计划](identity-foundation-v1-implementation-plan.md)
- [project-status.md](../project-status.md)（Greenfield 进度）
