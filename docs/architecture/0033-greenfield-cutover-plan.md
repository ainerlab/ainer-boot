# ADR-0033 Greenfield Cutover 执行计划

## 文档状态

- 状态：执行计划（待施工，非已实施）
- 日期：2026-08-05
- 适用：`reset/0033-greenfield` 分支的 S1.2 破坏性 cutover
- 依据：[ADR-0033 Greenfield](../decisions/0033-account-workspace-subject-isolation-greenfield-baseline.md) +
  [Impact 文档](ainer-foundation-greenfield-reset-impact.md) §3/§4/§10 + 已落地的替换脊柱
- 脊柱基线：`reset@900d7cb`（S1.0 / S1.1 / S1.2 领域+服务+持久化+resolver），全部加法、已验证、未接 runtime、未删旧

> 本计划把 Impact 文档里抽象的 Stage 1–8 落成有序、带测试门、可勾选的施工清单，供一个**全新、专注的 session**
> 照单执行 cutover。当前 session 只完成了加法脊柱（下 §1）；**§2 的破坏性 cutover 尚未动**。

---

## 1. 脊柱基线（已就绪，加法、已验证）

| 层 | 内容 | commit | 验证 |
|---|---|---|---|
| `security.principal` | authority-qualified sealed `PrincipalSubjectRef`（Human/Service）、`IdentityAuthorityRef` | `ac353f7` | 8 tests |
| `security.token` | `TokenProfile`（3 profile，fail-closed）、`AuthenticatedPrincipal`（profile↔kind 不变量）、`VerifiedJwtClaims`、`TokenProfileResolver` 端口 | `e0b4a4c` | 6 tests |
| `identity.foundation` 领域 | `HumanAccount`（securityEpoch）、`LoginIdentity`（1:N）、状态枚举 | `351aa00` | 6 tests |
| `identity.foundation` 服务 | `IdentityFoundationService`（register/link/find，无自动 merge） | `81dd951` | 6 tests（in-memory） |
| identity 持久化 schema | `ainer_identity_human_account` / `ainer_identity_login_identity`（active 绑定部分唯一索引） | `bda4823` | Flyway 重放（含 13/16 schema 断言） |
| identity 持久化接线 | `HumanAccountMapper`/`LoginIdentityMapper`（@Mapper）+ `Mybatis*Repository`（UUIDv7，affected-rows 检查） | `fafa5d6` | 3 PG 集成测试 |
| `security.token` resolver | `ReferenceTokenProfileResolver`（fail-closed profile 解析） | `900d7cb` | 7 tests |

累计：identity 74 tests / ainer-security 26 tests，均 0 fail / 0 skip。与 legacy runtime 共存，未触碰、未删除 legacy。

---

## 2. Cutover 执行计划（破坏性，有序、带测试门、原子合入）

**核心不变量**：任一可部署状态必须保持「legacy 或新模型二选一权威」；**C4 的删除必须与 C1–C3 的接线同原子切片完成**，绝不出现 Impact 风险登记 P0 的「删了 tenant predicate、还没接替代 Authorization」中间态。C1–C3 是共存接线（legacy 仍服务旧 consumer），C4 是原子删除（仅在新权威就绪后）。

### C1 — AuthServer 签发新 profile（共存）

- token customizer：对新 audience 签发 `USER_NEUTRAL_V1` / `USER_WORKSPACE_V1` / `SERVICE_V1`，`sub` 来自 `HumanAccount`/`ServicePrincipal`、状态来自 foundation、`token_profile` + `claim_contract_version` + `actor_type` 入 claim。
- legacy `LEGACY_TENANT` profile 对旧 audience 继续签发（drain 期）。
- **测试门**：新 profile token 由 `ReferenceTokenProfileResolver` 正确解析；legacy token 不受影响；新 profile 无 `tenant_id` claim。

### C2 — Resource Server / security starter 解析新 profile（共存）

- security starter 接 `TokenProfileResolver`：verified JWT → `AuthenticatedPrincipal`（新 profile audience）。
- legacy profile audience 继续走 `AuthenticatedActor`。
- **测试门**：新 profile 请求解析为 typed principal；缺/未知/矛盾 profile fail-closed（401）；legacy 路径零回归。

### C3 — Identity 服务切换到 foundation（共存）

- 注册 / 登录走 `IdentityFoundationService` + foundation repos（`HumanAccount`/`LoginIdentity`），不再写 `IdentityUser`/`TenantMembership`。
- Passkey 凭证绑定 `HumanAccount`（去掉 default tenant membership 依赖）。
- **测试门**：Account-with-zero-Workspace 完成认证；Personal Workspace 幂等 provisioning；legacy tenant API 仍服务旧 consumer。

### C4 — 原子删除 legacy + squash migration（破坏性核心，必须与 C1–C3 同切片）

- 删 legacy identity 代码：`IdentityTenant`/`IdentityUser`/`TenantMembership`/`TenantRole`、`TenantProvisioning*`、ownership/transfer/recovery、`/api/tenants/**`、`/api/me/tenants`、`/select-tenant`、tenant directory/event 路由。
- 删 inner Workspace 的 `tenant_id` 列 + JWT `tenant_id` claim + tenant OAuth client setting + tenant rate/budget key。
- squash migration：删 legacy identity（12）+ workspace（8）migration，重建可从空库重放的 baseline（foundation 表 + standalone Workspace）；旧库不可原地升级（Greenfield 前提）。
- **测试门（必须全过）**：空库重放新 baseline；跨 Workspace DENY；运行代码/API/schema/JWT 语义 inventory 无 `tenant`；Account-with-zero-Workspace；Personal Workspace 幂等；撤销 fail-closed；`IdentityModuleIntegrationTest` 计数随 squash 重校。

### C5 — 原子合入 dev

- C1–C4 在 `reset/0033-greenfield` 作为一个原子单元合 `dev`（或先合 `codex/scaffold-modern-baseline`）。
- 合入前 dev 与 reset 已对齐（reset 已含 dev 的 Instant 修复 / offstate / gitleaks / P1 publish）。
- 合入后 dev 不存在 legacy tenant runtime；CI 双 job（quality + secret-scan）green。

---

## 3. 开放设计决策（cutover 必须收口）

脊柱建设暴露、cutover 必须明确处理的悬而未决项：

1. **错误类型**：`IdentityFoundationService` 现用 `IllegalStateException`（骨架）；生产需 `BusinessException` + `IdentityErrorCode`（新增 foundation 专用错误码，或复用并区分）。
2. **Profile / ServicePrincipal 领域类型未建**：当前只有 `HumanAccount`/`LoginIdentity`；Greenfield 完整模型还需 `Profile`（0:1）与 `ServicePrincipal`（含 credential binding）。
3. **`AuthenticatedPrincipal` 无 workspace access ceiling**：`USER_WORKSPACE_V1` 需要 `WorkspaceRef` + ceiling；cutover 前需建 `WorkspaceRef` 并补该字段。
4. **foundation 包位置**：现 `dev.ainer.module.identity.foundation`（与 `account` 平级）；cutover 后是否提升为 `account` 主体（删 legacy 后）需定。
5. **migration squash 版本戳**：新 baseline 的 `VYYYYMMDDHHMM` 起点与 legacy 删除的边界需明确（避免 Flyway 校验失败）。
6. **id-source 张力**：`IdentityFoundationService` 取 `Supplier<UUID>`；生产装配 `accountRepository::nextUuidV7`（DB），测试用 sequential supplier——cutover 时确认 bean 装配。
7. **resolver 边界**：`ReferenceTokenProfileResolver` 是参考实现；AuthServer 真实 resolver 复用其逻辑但读真实 JWT 解析结果——确认适配边界（避免两份解析逻辑）。
8. **`selectByTypeAndIdentifier` 仅查 ACTIVE**：revoke 后重绑语义在 in-memory 与 DB 两实现已一致；cutover 时确认 AuthServer 登录路径用同一契约。
9. **`IdentityModuleIntegrationTest` 计数**：随 squash 删/建表会再变；C4 需重校 13/16 这类断言。

---

## 4. 脊柱评审指引（删旧前过一遍）

cutover 不可逆，删 legacy 前应先评审 §1 脊柱。每层要点：

- **S1.0 principal**：Human/Service sealed 非等价；`IdentityAuthorityRef` 限定 issuer/realm；裸 UUID 跨 issuer 不等。
- **S1.1 token profile**：`TokenProfile.fromClaim` fail-closed；`AuthenticatedPrincipal` 的 profile↔principal-kind 不变量（USER_*→Human、SERVICE→Service）。
- **S1.2 领域**：`HumanAccount` 不级联、securityEpoch 单调；`LoginIdentity` 1:N、不自动 merge、跨 authority 不合并。
- **S1.2 服务**：重复标识硬冲突；link 要求 account ACTIVE；跨 authority 同标识不合并。
- **S1.2 schema**：active 绑定**部分唯一索引**（允许 revoke 共存）；FK account_id；CHECK 约束（status/type/time）。
- **S1.2 持久化**：`nextUuidV7()` version=7；insert affected-rows==1 检查；`findByTypeAndIdentifier` 仅 ACTIVE；row↔domain 映射正确。
- **resolver**：未知 profile / 错版本 / actor_type 不匹配 全 fail-closed；profile↔kind 由 `AuthenticatedPrincipal` 收口。
- **整体**：确认**无任何代码触碰 legacy runtime**（纯加法共存）；无安全回归（`@NullMarked` 一致、不可变集合）。

---

## 参考

- [ADR-0033 Greenfield](../decisions/0033-account-workspace-subject-isolation-greenfield-baseline.md)
- [Impact 文档（删除/重建/Stage 0–8）](ainer-foundation-greenfield-reset-impact.md)
- [project-status.md](../project-status.md)（Greenfield 进度）
