# ADR-0056：账号与服务主体生命周期写路径、security_epoch 递增语义与撤销边界

- 状态：Accepted
- 日期：2026-09-11
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无
- 修订：细化 ADR-0033 Greenfield §3/§4 的账号生命周期与 revocation epoch 语义；与
  ADR-0008/0011 的"撤销即时性只成立于在线校验"结论保持一致，不放宽 ADR-0011 的默认关闭边界

## 背景

Greenfield 基线给 `HumanAccount` 与 `ServicePrincipal` 都定义了单调递增的 `security_epoch`，
`AinerJwtTokenCustomizer` 在签发时把当前 epoch 写入 `sec_epoch` claim，
`RevocationAwareOAuth2AuthorizationService` 在按 token 查找 authorization 时比较 claim 与当前
epoch。但实现只有 insert/select：`security_epoch` 永远是 0，不存在任何状态变更写路径
（`disableAccount`/`lockAccount`/`closeAccount`/密码轮换递增 epoch 都不存在）。后果是 epoch 比对
恒真，"账号禁用/密码轮换后旧 Token 失效"这个被 `docs/security.md`、`docs/architecture.md`、
`docs/operations.md` 写成已解决的能力在代码里并不存在。

同时暴露第二个事实：该能力的即时性只可能来自 RFC 7662 在线校验（`OnlineAccessTokenValidationFilter`，
默认关闭）。本地 JWT 校验不做数据库查询，自包含 JWT 在自身 TTL 内仍然可用。把"事件前签发的
Token 全部失效"写成无条件事实，是同一缺陷的另一半。

第三，集成测试还暴露：`oauth2_authorization` 的 claim 元数据以 `Map<String,Object>` 往返，
`sec_epoch` 作为装箱 `Long` 会写成类型 id，而 `PolymorphicTypeValidator` 未放行 `java.lang.Long`，
导致 `findByToken` 反序列化失败 → introspection 503。也就是说即使补上写路径，在线撤销判定也
走不到。

## 决策驱动因素

- 文档承诺必须与实现一致；要么实现，要么改文档，不能两者相反；
- 安全相关变更必须失败关闭：非法状态迁移、并发竞争、缺失期望态一律拒绝；
- 不得引入进程内异步事件、outbox 或跨运行时 relay（Greenfield 已删除这些链路）；
- Identity 模块没有 Web 依赖，HTTP 面只能落在 `ainer-authorization-server`；
- 不新增匿名可达端点，不绕过既有授权机制；
- 撤销的即时性必须按"是否走在线校验"分别表述并可验证。

## 备选方案

1. **只改文档，承认能力不存在。** 最小改动，但账号禁用/密码轮换是企业身份的基本运营能力，
   缺它会迫使运营直接改库（更危险），且 `AccountStatus` 的 `DISABLED`/`CLOSED` 将永远是死枚举。
   放弃。
2. **用应用层先 SELECT 再 UPDATE（读-改-写）。** 实现简单，但两个并发迁移会互相覆盖，
   epoch 也可能被覆盖回退，无法给出可证明的并发语义。放弃。
3. **用 PostgreSQL 行锁（`SELECT ... FOR UPDATE`）+ 应用层判断。** 语义正确，但需要额外往返与
   显式锁管理，且锁范围依赖调用方事务边界。作为对照方案记录，不采用。
4. **带期望态的条件 UPDATE（compare-and-set），状态与 epoch 在同一条语句内前进。** 采用。
5. **撤销即"删行/软删账号"。** 违反 ADR-0033 的非级联不变量与审计要求。放弃。

## 决策

### 写路径与状态机

- 人员账号：`IdentityFoundationService#changeAccountStatus`（`DISABLED` / `LOCKED` / `CLOSED` /
  恢复 `ACTIVE`）、`rotatePassword`、`revokeCredential`；
- 服务主体：`ServicePrincipalFoundationService#changePrincipalStatus`（`ACTIVE <-> DISABLED`）；
- 迁移表由 `AccountStatus#canTransitionTo` / `ServicePrincipalStatus#canTransitionTo` 定义：
  `CLOSED` 是终态，离开它的迁移一律拒绝；**同状态不是变更**，重复迁移返回 409 而不是静默
  no-op（避免空操作再次递增 epoch 并伪造审计）；`DISABLED -> ACTIVE` 与 `LOCKED -> ACTIVE`
  允许，前者是安全禁用经处置后的运营复原方向，后者是限流态的自然解除。

### 递增语义

每一次安全相关写入都递增 `security_epoch`，包括**恢复/解锁**：早期方案曾考虑"恢复不递增"，
但那会让禁用/锁定前签发的 Token 在恢复后重新可用，与"早于当前 epoch 的凭证全部失效"直接矛盾，
也无法在审计中证明撤销发生过。同时保留"单调递增"这一唯一不变量，便于推理与验证。

### 并发语义

`UPDATE ... SET status = ?, security_epoch = security_epoch + 1 WHERE id = ? AND status = ?`
（凭据类变更用 `SET security_epoch = security_epoch + 1 WHERE id = ? AND status = ?`）。
行级写锁 + 期望态比较：并发迁移只有一个能影响 1 行，另一个得到 0 行并由服务层失败关闭为 409；
不存在"后写覆盖先写"。epoch 与状态在同一条语句内前进，因此不存在"状态已改但 epoch 未加"的窗口。
密码轮换/凭据撤销与 epoch 递增同事务：第二步失败整体回滚。

### HTTP 面

落在 Authorization Server 发行物的 `/internal/identity/**`（默认关闭）：
`identity.accounts.manage` 覆盖账号状态迁移、密码轮换与凭据撤销；
`identity.service-principals.manage` 覆盖服务主体状态迁移。除最小 scope 外要求精确登记的可信
SERVICE `sub`（ServicePrincipal UUID），并额外要求该 principal 当前 ACTIVE 且 Token 的
`sec_epoch` 等于其当前 epoch。应用服务在事务边界内重复同一 guard。变更与
`ainer_identity_principal_lifecycle_audit` 审计同事务；密码材料不出现在响应与审计中。

### 撤销边界（必须与实现一起表述）

- 走在线校验（RFC 7662）的请求：epoch 递增后旧 `sec_epoch` Token 判定 inactive → 401，
  刷新与撤销查找同样失败；
- 只做本地 JWT 校验的请求：自包含 JWT 在 TTL 内仍然可用；
- 因此文档统一改为带条件表述，并新增边界测试固定"关闭在线校验时同一旧 Token 仍通过资源服务器"。

### JSON 多态白名单

`AinerOAuth2AuthorizationJsonMapperFactory` 的 `PolymorphicTypeValidator` 放行 `String`、
`Long`、`Integer`、`Boolean`、`Double` 这些无行为副作用的 JDK 标量（不放行整个 `java.lang` 包），
使 `sec_epoch` 等 claim 能完成 JDBC 往返。

### 不包含

不引入账号自助注册/找回、密码复杂度策略、账号合并、批量运营 API、双人审批（本控制面是单步可逆
操作，需要双人的场景继续走 ADR-0010 的恢复流程）、以及任何进程内撤销事件。

## 后果

### 正面

- 文档承诺的 epoch 撤销现在有实现、有审计、有真实端到端证据；
- 状态机与并发语义可证明（条件 UPDATE + CHECK 约束 + 并发测试）；
- 撤销边界在文档与测试中同时被固定，不会再被写成"无条件全局实时"。

### 负面与风险

- 恢复/解锁也会使既有会话失效，运营需要知道这是一次"重新认证"事件；
- 控制面是单步操作，只有 scope + 可信 `sub` 一道人以外的门禁，误用会造成可用性事件（可逆）；
- 每次在线校验多一次数据库查询（ADR-0011 已接受该成本）。

## 安全、数据与隐私

- 调用方身份来自已验证 Token 的 `sub` 与 `sec_epoch`，不接受请求体或请求头声明的身份；
- 审计记录 actor `sub`、requestId、changeReference，不记录密码或凭据材料；
- 表在 `ainer_auth` 库内，沿用 Identity 的 schema 所有权与 migration 不可变性；
- 不新增匿名可达端点；`/internal/**` 过滤链继续 `denyAll()` 兜底。

## 运维与迁移

- 新 migration `V202609111800__identity_principal_lifecycle_audit.sql` 只新增一张审计表，
  不改动已发布 migration，可在空库重放；
- 控制面默认关闭；启用顺序、白名单登记与轮换要求见 `docs/operations.md` §2.4；
- 回滚只关闭开关，历史审计与 epoch 保持不变。

## 验收记录

- `IdentitySecurityEpochWritePathTest`（真实 PostgreSQL，12 tests）：状态机、并发、epoch 单调、
  密码轮换与凭据撤销的同事务语义；
- `IdentitySecurityEpochRevocationIntegrationTest`（真实 PostgreSQL + 真实 PKCE Token + 两个真实
  Resource Server 探针，4 tests）：禁用/密码轮换/凭据撤销后旧 Token 在线校验 401、关闭在线校验
  时仍 200、新 Token 携带新 epoch 且可用、非受信调用方被拒；
- `IdentityControlPlaneHttpTest`（6 tests）：scope/主体/epoch 拒绝路径、409/404/400、同事务审计、
  密码不回显；
- `AinerOAuth2AuthorizationJsonMapperFactoryTest`：标量 claim 的多态往返回归（修复前必失败）。
- 实测数字与全量门禁结果见 `docs/project-status.md`。

## 参考

- [ADR-0033](0033-account-workspace-subject-isolation-greenfield-baseline.md)（HumanAccount 生命周期根）
- [ADR-0008](0008-identity-directory-and-access-revocation.md) / [ADR-0011](0011-selective-online-token-validation.md)
  （撤销传播与选择性在线校验）
- [ADR-0010](0010-security-operations-and-audit-lifecycle.md)（安全操作审计与双人审批边界）
- [`../security.md`](../security.md) / [`../operations.md`](../operations.md) / [`../database.md`](../database.md)
