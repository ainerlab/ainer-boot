# Ainer 项目状态

> 文档类型：时间敏感快照 · 状态：持续更新 · 核对时间：2026-08-08 · 工程版本：`0.1.0-SNAPSHOT`

本文只记录当前事实和验证记录，不替代架构规范与 ADR。每个里程碑结束、发布候选形成或主要风险变化时更新核对时间。

## 1. 当前阶段

`reset/0033-greenfield` 分支已按 ADR-0033（Option B：完全移除 Tenant）完成 S1–S8 全部施工序列
并全绿验证（205 tests / 0 failure / 0 error / 0 skipped）：Identity 换为 HumanAccount/
ServicePrincipal/LoginIdentity/Credential foundation，Token 使用 typed `token_profile`
（`SERVICE_V1`/`USER_NEUTRAL_V1`）与 `claim_contract_version=1`，撤销通过
`security_epoch`/`sec_epoch` claim 在线比对；Workspace 与 AI Runtime 已去 tenant 化，改为纯
membership/subject 边界；legacy identity、tenant 上下文、access-event outbox/relay 消费链路与
平台预配通知均已删除，migration 重建为 4 个可空库重放的 standalone baseline
（identity=`V202608070300`、workspace=`V202608070310`、ai-runtime=`V202608070320`、
authorization-server=`V202608070330`）。M6 品牌 `/login`（Studio 视觉合同 1.0.0）已由
Authorization Server 承载并于 2026-07-29 部署 dev (release `e6cb0b44bb9e-20260729053046`)，
真实 remote 联合 E2E 通过，Studio 合同状态 `implemented`。

下文 M4.x 里程碑记录中涉及 tenant、access-event、relay、Directory 与平台预配的历史描述反映
当时代码，已被 Greenfield S8 删除取代，不作为当前部署依据。真实外部通知网关/供应商联调、供应商
回执映射、最终送达验证、生产限速/告警尚未完成，0-skipped 仍需在正式发布候选环境重复执行。
当前工程是可编译、可运行、可用真实 PostgreSQL 验证的 Spring Boot 4.1 多模块基线，但尚未达到
生产或商业发行就绪。

P2 Create & Generate 已收口（2026-08-09）：`ainner-initializer`（Manifest v1 解析/校验 + 确定性
生成内核）与 `ainner-initializer-cli`（preview/init/diff 离线命令）已交付并全量通过
`verify-initializer-consumer.sh` 三通道门禁（两轮生成字节一致 diff、普通/postgres/CRUD 三个
变体真实测试均 0 skipped、无 Ainer 框架源码副本、生成项目独立编译）；ADR-0035 与 ADR-0036 均
Accepted。P2 退出门禁逐项验证：确定性（同 manifest 两轮 diff=0）、生成安全（preview 不落盘、
非空目标拒绝覆盖、生成器无数据库/网络写入、默认不改菜单）、TTFR 实测 100s（门禁 600s）、
TTCRUD 实测 124s（门禁 1800s）、生成物通过 PostgreSQL 与 golden consumer 门禁。
组织/行业模板与策略包是 ADR-0035 决策 7 明示的 v1 非目标，属 Studio/Enterprise 扩展
（设计文档能力矩阵第 94 行），移交 P3+ 扩展清单，不再作为 P2 阻塞项。

## 2. 已完成

- JDK 25、Maven Reactor、独立 BOM 与 Spring Boot 4.1.0 基线；
- 无 Spring 依赖的核心错误和身份参与者契约；
- Web、Persistence、Security Starter 及自动装配测试；
- ADR-0029 T0 第 1 项的 Web Starter 实现范围：`ainer-starter-web` 已从废弃兼容坐标
  `spring-boot-starter-web` 切换为 `spring-boot-starter-webmvc`，并以聚焦的
  `spring-boot-starter-webmvc-test` 替代该模块原先手工拼装的测试依赖；
- Workspace PostgreSQL 垂直切片、tenant 隔离、成员生命周期、单一 OWNER、授权审计写入与分页读取；
- AI Model Gateway 非流式/SSE、模型白名单、限流、预算、Token/费用和脱敏审计；
- Identity tenant/user/membership、安全 Directory、账号禁用、成员撤销和事务 access-event outbox；
- 独立 Authorization Server、JDBC client/authorization/consent、外部 RSA key、Client Credentials 和 JWT tenant/audience claims；
- 人员/服务 JWT `actor_type` 隔离、官方 OAuth2 Client Credentials Token 获取与缓存；
- 默认关闭的跨运行时 Directory HTTP adapter/client、tenant-bound/平台 scope 与失败关闭邀请校验；
- PostgreSQL outbox lease、重试/耗尽、HTTP relay、Workspace receipt 幂等消费者与 `REVOKED` membership；
- 撤销积压、发布失败、重试耗尽、重复消费与实际撤销数指标；
- Identity 耗尽事件查询、短时双人重放申请、tenant/服务身份隔离和操作审计；
- 无 ACTIVE OWNER 的 Workspace 双人恢复流程，恢复后原 REVOKED OWNER 保持撤销；
- Workspace 授权审计热保留/同库归档、热冷统一查询、稳定游标 SIEM 拉取与导出审计；
- 撤销首次成功传播 Timer/SLO bucket，以及 OWNER 缺失、拒绝窗口、热/归档数和归档失败指标；
- Resource Server 高风险路径/方法选择性 RFC 7662 在线校验，无 active 正向缓存，inactive 401、依赖失败 503；
- Authorization Server 专用 introspection client 隔离、RFC 7009 撤销、官方 JDBC authorization 包装与协议级普通 client 拒绝；
- Identity 当前状态与最新 access-event 组成的人员 Token revocation epoch，以及在线校验放行/拒绝/失败/延时指标；
- 两个发行物的 Prometheus registry 与 exporter、tenantless SERVICE + `platform.metrics.read` 授权，以及独立一分钟 metrics client bootstrap；
- 默认关闭的 tenant 服务 Client 控制面：一次性服务端随机 secret、scope 白名单、tenantless
  operator 双重授权、蓝绿新 ID 轮换、显式退役和同事务操作审计；
- 退役感知 registered client/authorization 包装：阻止新 Token、历史 Token introspection
  inactive，同时保留官方 JDBC authorization 历史可读性；
- 独立的一分钟 client-control operator bootstrap，以及配置失败关闭和真实 PostgreSQL 生命周期
  门禁；
- 测试专用 public client 的 Authorization Code + PKCE S256 真实浏览器会话门禁：登录、
  authorization code 单次交换、错误 verifier、缺失/`plain` challenge 和非法 redirect URI 拒绝；
- JDBC authorization 的 Ainer 人员 principal 精确 Jackson 白名单、认证后凭证擦除、协议记录
  password 排除，以及 public client 无 refresh token 基线；
- 默认关闭的 Spring Security Passkey/WebAuthn、严格 RP/Origin/UV 配置、按账号条件 MFA、
  JDBC 协议与 ACTIVE/REVOKED 生命周期、软撤销、并发最后凭证保护和操作审计；
- 人员 Token 的标准 `amr` / `auth_time` 基线，以及 browser chain 精确 factor accumulation，
  不改变 Client Credentials、internal API 和 metrics 安全链；
- Passkey 真实签名 ceremony 端到端门禁：虚拟 authenticator 驱动 registration/authentication
  签名闭环，Passkey 用户走授权码流程后 access token 携带 `amr=pwd,mfa,pop`、`auth_time`、
  稳定 `sub`/`tenant_id`/`roles`；
- 凭证管理端点（`/webauthn/register/**`）真正受条件 MFA 门禁保护，已登记账号在缺因子时
  无法登记或删除凭证；
- 默认关闭的 Passkey 恢复：自助恢复码（首次登记后签发，高熵一次性、bcrypt 哈希、per-subject
  失败锁定），赎回即吊销该账号全部 ACTIVE Passkey 并写安全操作审计；管理员双人恢复复刻
  Workspace owner recovery 的 request/approve 骨架，approve 吊销目标全部 ACTIVE Passkey；
- 默认关闭的登录限速（node-local 固定窗口，按客户端 IP 节流 `/login`、`/login/webauthn`，
  超额 429）与默认 `optional` 的受控首次 Passkey enrollment（`require-invite` 模式下首登需操作员
  预授权，成功后授权置 CONSUMED，replacement 不受影响）；
- 默认关闭的 resource server step-up 授权策略（`RecentStrongAuthenticationFilter`：高风险路径
  要求人员 Token 的 `amr` 含强因子且 `auth_time` 在 `max-auth-age` 内，否则 403
  `AINER.SECURITY.RECENT_STRONG_AUTHENTICATION_REQUIRED`），首次让 Ainer 签发的 `amr`/`auth_time`
  真正参与授权决策；
- 恢复/enrollment 目标使用 ACTIVE default Identity membership 应用门禁与复合外键双重 tenant 绑定；
  登录限流补齐 WebAuthn options、统一 429/no-store/指标，step-up 补齐匿名 401、USER 限定与
  有界 clock skew；
- Identity 权威运行时提供 tenant 成员列表、加入、角色变更和软移除 API，使用 USER scope + 可信
  tenant claim + 实时 ACTIVE OWNER/ADMIN 四重门禁，所有实际写入同事务审计且不允许通用接口修改 OWNER；
- 首个平台 tenant/OWNER 使用默认关闭、严格幂等、不覆盖密码且由 PostgreSQL transaction advisory
  lock 串行化的 Authorization Server bootstrap；业务 Server 不装配 Identity migration；
- Ainer Admin `dev` public client、双用户 fixture、当前 access token 自助撤销、撤销端点
  active gate、`ainer-admin-v1.yaml` 与 TypeScript SDK 生成入口；TenantMembers 契约与
  tenant selector 已随 S8 删除，契约只保留 `POST /api/me/access-token-revocations`；
- 同一 `ainer-admin-dev` browser session 的 PKCE → revoke → OIDC logout
  真实 PostgreSQL 端到端门禁；成员列表/添加/改角色/软移除验证已随 S8 移除；
- Ainer 品牌服务端登录页：Studio 合同与 Tokens 固定哈希、四种服务端状态、服务端 CSRF、
  SavedRequest、统一凭据错误、HTML 429/`Retry-After`、明确认证基础设施异常 503、精确 CSS
  代理，以及不改变 WebAuthn/MFA filter 的兼容基线；
- 默认关闭的平台 Identity 预配申请控制面：tenantless SERVICE、tenant/user 成对 read/write
  scope、精确 operator 白名单、独立一分钟 operator bootstrap、operator 级幂等、规范化摘要、
  tenant code/新 username 并发预留、惰性过期、同事务平台审计和安全状态查询；
- 平台预配与首租户 bootstrap 共享 tenant code/username advisory lock；申请只预生成
  PostgreSQL UUIDv7 tenant/subject，不提前写核心 tenant/user/membership；
- 新用户短时限次 activation grant（256-bit secret、数据库只存 SHA-256 摘要）、带 key version
  的 AES-256-GCM notification outbox、失败重试与 key rotation 读取；平台投影/审计不返回 secret、
  联系地址、密文或请求摘要；
- 新用户消费 grant 时本人设置首个长期密码；已有 ACTIVE 用户必须用本人 USER Token 与
  `identity.provisioning.accept` 接受。成功事务原子创建 ACTIVE tenant、user（若需要）与 OWNER；
  失败计数锁定、过期、回放和核心写入失败均不产生孤儿 ACTIVE tenant；
- 平台 tenant/user 核心事实安全分页分别受对应 read scope 保护，单页最多 100，不返回密码、
  OAuth、membership、通知或 activation 数据；未激活申请通过显式 cancellation 子资源幂等关闭，
  request、预期 grant、未发布 payload 销毁与阶段审计同事务；
- 默认关闭的预配通知终态回执 API：独立 tenantless gateway client、专用
  `identity.provisioning-notifications.receipts.write` scope、精确白名单、只允许已
  `PUBLISHED` notification、gateway event/notification 双重幂等、UUIDv7 回执和最小安全字段；
- `ainer-dev.xiaoqu99.com` 手工触发的可复现 dev 发布与真实公网环境：独立 PostgreSQL 18.3、
  loopback Authorization Server systemd、版本化 JAR/Studio/Admin、原子切换/校验回滚、
  Let's Encrypt 和精确同源 Nginx 配置；真实 Chromium 已完成 PKCE、成员治理、revoke、
  OIDC logout 和退出后重新登录门禁；
- ADR-0001 至 ADR-0011、ADR-0015 至 ADR-0020、ADR-0022、ADR-0024 至 ADR-0028 与 ADR-0033 Greenfield
  已接受（0033 Greenfield 为目标基线，Option B：完全移除 Tenant；按
  [Impact](architecture/ainer-foundation-greenfield-reset-impact.md) Stage 0–8 执行，接受不授权
  立即改代码）；ADR-0012 至 ADR-0014、ADR-0021、ADR-0023、ADR-0029 至 ADR-0032 与 ADR-0034 处于 Proposed；
  ADR-0033 v1/v2 标记 Historical；架构、HTTP API、安全、数据、测试、运行与发布基础文档已建立。
- Greenfield S1.2 加法脊柱在 `reset/0033-greenfield` 分支成型且已验证（principal/token-profile/Identity 领域+
  服务+PostgreSQL 持久化+resolver 参考实现，共 identity 74 + security 26 tests / 0 fail），与 legacy 共存、
  未接 runtime；破坏性 cutover 待执行，有序施工清单见 [`0033-greenfield-cutover-plan.md`](architecture/0033-greenfield-cutover-plan.md)。
- Greenfield S2 foundation 能力补全（执行规划 缺口 A）已在 `reset/0033-greenfield` 分支完成：新建
  `ainer_identity_credential`（PASSWORD/WEBAUTHN_PUBLIC_KEY/OIDC_SUBJECT，ACTIVE/REVOKED，部分唯一索引
  `(account_id, type) WHERE status='ACTIVE'`）与 `ainer_identity_human_profile`（0:1 account）两张表；
  `IdentityFoundationService` 扩展 `registerHumanAccountWithPassword` / `findPasswordCredentialForLogin` /
  `rotatePassword` / `updateProfile`，密码经 Delegating PasswordEncoder 编码后入库、rotatedAt 标记轮换、
  未知账号/缺 ACTIVE 凭据 fail-closed；identity 错误码补 CREDENTIAL_NOT_FOUND/CREDENTIAL_REVOKED/
  INVALID_CREDENTIAL/PROFILE_NOT_FOUND。全 reactor 388 tests / 0 failure / 0 error / 0 skipped（Colima）。
   施工序列与决策表更新见 [`0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md)。
- Greenfield S6 canonical Workspace 去 tenant 已在 `reset/0033-greenfield` 分支完成：当前
  `Workspace`/`WorkspaceMember`/审计/recovery 使用 `workspace_id + ACTIVE membership` 边界，
  业务 API 使用 S5 typed `AuthenticatedPrincipal` 并拒绝 Service principal 进入 Human membership；
  MyBatis 查询与 owner 唯一约束已去 tenant，新增 `V202608070100` 从空库重放后删除 Workspace
  schema 的 tenant 列。Identity Directory 改为 ACTIVE HumanAccount 查询，subject-only access event
  只记录 receipt、不跨所有 Workspace 全局撤销 membership；owner recovery、审计导出改为 Workspace
  scope。旧 tenant-first Workspace 测试已重写为 canonical membership/跨 Workspace DENY 门禁。
  全 reactor 387 tests / 0 failure / 0 error / 0 skipped（Colima）。
  施工序列与决策表更新见 [`0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md)。
- Greenfield S7 AI Runtime 去 tenant 已在 `reset/0033-greenfield` 分支完成：
  `GovernedAiExecutionContext`、Invocation/Task/ContextSnapshot、AI audit/budget repository 与 API
  改用 typed `AuthenticatedPrincipal` 的 subject/actor context；node-local limiter 更名为
  `SubjectRateLimiter`，PostgreSQL daily budget/advisory lock 按 subject 绑定，模型调用、Task run、
  SSE、审计读取与 cross-subject 404 回归保持通过。新增 AI migration 移除 invocation/task/snapshot 的
  tenant 列，配置改为 `subject-daily-budget`。全 reactor 387 tests / 0 failure / 0 error / 0 skipped
  （Colima）。
  施工序列与决策表更新见 [`0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md)。
- Greenfield S8 原子删除 legacy + migration squash 已在 `reset/0033-greenfield` 分支完成（不可逆点）：
  删除 legacy identity `account/` 领域/应用/基础设施约 50 文件、AuthServer legacy controllers 约 20
  文件、`tenantcontext/` 与 tenant selection、`RevocationAwareOAuth2AuthorizationService` 的 legacy
  tenant/user 在线状态依赖、legacy `AuthenticatedActor` 体系（被 typed `AuthenticatedPrincipal` 取代）、
  `AinerResourceServerProperties.tenantClaim`/`AuthenticatedService` 的 tenantId，以及 legacy workspace
  identity access-event 消费链路（subject-only 语义已由 canonical Workspace 覆盖）。`foundation/` 包
  提升为 identity 主体包（`IdentityErrorCode` 收口至 foundation）。migration 重建为 4 个可空库重放的
  standalone baseline：identity=`V202608070300`、workspace=`V202608070310`、ai-runtime=`V202608070320`、
  authorization-server=`V202608070330`，旧库不可原地升级（ADR-0033 前提）。全仓 `tenant_id`/`tenantId`
  grep 仅剩测试内"列不存在"负向断言与参数命名。全 reactor 205 tests / 0 failure / 0 error / 0 skipped
  （Colima），0 skipped。
  施工序列与决策表更新见 [`0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md)。
  `AinerUserDetails` 加性重设计（新增 nullable `accountId` + `securityEpoch`，legacy 字段保留），
  customizer 抽为可测的 `AinerJwtTokenCustomizer`：client setting `ainer.token-profile` 选择轨道，
  SERVICE_V1（ServicePrincipal sub + token_profile/claim_contract_version=1/actor_type=SERVICE +
  sec_epoch，无 tenant_id）与 USER_NEUTRAL_V1（HumanAccount sub + profile claims + sec_epoch，
  保留 amr/auth_time，无 tenant_id/roles）均 fail-closed（缺 principal/account、非 ACTIVE、未知
  profile → OAuth2 400 access_denied，不回退 legacy）；无 setting 的 client 走原 legacy claims 零回归。
  常量 `TOKEN_PROFILE_SETTING`/`SEC_EPOCH_CLAIM` 收口于配置类；JSON mixin 同步 accountId/securityEpoch
   往返。全 reactor 401 tests / 0 failure / 0 error / 0 skipped（Colima）。
   施工序列与决策表更新见 [`0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md)。
- Greenfield S4 登录链路与 Passkey foundation 接线已在 `reset/0033-greenfield` 分支完成：
  `AinerUserDetailsService` foundation-first 读取 `HumanAccount + PASSWORD credential`，旧 tenant
  账号保留 fallback/legacy context enrichment；foundation-only 账号可在无 Workspace 下完成密码+PKCE，
  token 使用 `USER_NEUTRAL_V1`。平台 bootstrap 改为创建 foundation account/profile，dev fixture 在共存期
  同时保留 legacy tenant 投影；Passkey credential、recovery code、enrollment grant、双人 recovery
  增加 `account_id` 绑定与 account 控制面 API，legacy `(tenant_id, subject_id)` API 保留。新增两条
  PostgreSQL migration，Authorization Server 从 23 增至 25 migrations；真实密码、WebAuthn ceremony、
  account recovery/enrollment/admin recovery 均通过。全 reactor 407 tests / 0 failure / 0 error / 0 skipped
  （Colima）。
  施工序列与决策表更新见 [`0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md)。
- Greenfield S5 Resource Server typed profile resolver 已在 `reset/0033-greenfield` 分支完成：新增
  `AuthenticatedPrincipalResolver` core port、Spring `Jwt` 到 `VerifiedJwtClaims` adapter，以及 starter
  的 SecurityContext resolver；`USER_NEUTRAL_V1`/`SERVICE_V1` 分别解析为 Human/Service subject，
  `sec_epoch` 解析为 optional typed epoch，SERVICE 无 `amr` 时使用 `client_credentials` assurance。
  缺失、未知、版本不支持或 actor/profile 矛盾统一 fail-closed 为 401；legacy
  `AuthenticatedActorResolver` 保持独立零回归。全 reactor 411 tests / 0 failure / 0 error / 0 skipped
  （Colima）。
  施工序列与决策表更新见 [`0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md)。
- M4.8B 租户上下文选择代码基线：`GET /api/me/tenants` 返回当前 USER 的 ACTIVE membership
  安全投影（tenant ID/code/name/role/is_default），LOCKED/DISABLED tenant/user/membership
  不返回；`AinerTenantSelectionFilter` 在 Authorization Code + PKCE 流程的 authorization
  endpoint 拦截多 ACTIVE membership 人员并重定向到服务端渲染的 `/select-tenant` 选择页，
  选择结果绑定当前 AS 会话（session attribute）与 authorization request（principal 更新后
  持久化进 `OAuth2Authorization`）；token customizer 在签发人员 access token 前实时重查
  `findActiveMembership(tenantId, subjectId)` 校验关系仍然 ACTIVE 并取得当前角色，principal
  或客户端提交的 tenant 只作为候选；SERVICE token 被该端点 403 拒绝。
- M4.8C OWNER 专用转移代码基线：双自然人确认状态机（REQUESTED → EXECUTED / CANCELLED /
  EXPIRED），`OwnershipTransferService` 在单一事务中锁定双方 membership、再次校验 ACTIVE 角色、
  先降原 OWNER 为 ADMIN 再升目标 ADMIN 为 OWNER、写入 `OWNERSHIP_TRANSFERRED` 操作审计并为
  双方写入 `IDENTITY_MEMBERSHIP_ROLE_CHANGED` access event 使旧角色 Token 进入撤销链路；
  数据库部分唯一索引保证每 tenant 最多一个 ACTIVE OWNER 和一个未完成转移；
  `OwnershipTransferController` 暴露 initiate/get/accept/cancel 四个端点，要求
  `tenant.ownership.transfer` scope、可信 tenant claim 与实时角色门禁。
- M4.8C OWNER 丢失恢复代码基线：双 tenantless SERVICE request/approve（不同 service subject），
  只能提升现有 ACTIVE ADMIN 为 OWNER 并降原 OWNER 为 ADMIN，不恢复被禁用主体；独立表/端点
  （`/internal/identity/ownership-recovery/**`）与 scope（`identity.ownership-recovery.request|approve`），
  与正常转移不共用授权规则；security operation audit 记录 REQUESTED + EXECUTED 两阶段。
- ownership-transfer step-up 门禁：默认关闭，启用后要求人员 Token 的 `amr` 含强因子且
  `auth_time` 在 `maxAuthAge` 内才能执行所有权转移。

## 3. 最近验证记录

2026-08-10 T1-7 `ainer-test-support` 落地：RestTestClient + `@ServiceConnection` + PostgreSQL 测试基座
- 新增 `ainer-framework/ainer-test-support` 模块（ADR-0029 T1 第 7 项）：`RestTestClient`/`RestResponse`
  基于 Boot 4.1 `TestRestTemplate` 提供 JSON 便捷与 JsonPath 断言；`AinerPostgresContainer` 固定
  `postgres:18.3-alpine` 镜像，配合 Boot `@ServiceConnection`（`JdbcContainerConnectionDetailsFactory`
  via spring-boot-testcontainers + spring-boot-jdbc）自动装配 DataSource，取代 `@DynamicPropertySource`
  样板。模块自身 5 个测试全绿（RANDOM_PORT 集成 + 真实 PG 容器 + 单元）。
- Initializer v1 模板已接入：生成项目 pom 增加 `ainer-test-support` test 依赖；SMOKE/CRUD 测试模板
  改用 RestTestClient 与 `@ServiceConnection` 基座。ProjectGeneratorTest 断言同步（33 tests 全绿）。
- 全量 `./mvnw clean verify`（JDK 25 + Colima）BUILD SUCCESS：248 tests / 0 failure / 0 error /
  0 skipped。`verify-maven-consumers.sh`（19 个 consumer POM、9 个 library 制品 sources/javadoc、
  Maven 3.9/4 双 golden consumer）与 `verify-initializer-consumer.sh`（普通/postgres/CRUD 三通道
  真实 Testcontainers 0 skipped）均通过。
- 关键依赖事实：Boot 4.1 中 `spring-boot-resttestclient` 不传递 `spring-boot-restclient` 与
  `spring-boot-http-client`，test-support 需显式声明；`spring-boot-jdbc` 的
  `JdbcContainerConnectionDetailsFactory` 经 `META-INF/spring.factories` 注册
  `ConnectionDetailsFactory`，`@ServiceConnection` + PG 容器即可生成 JDBC ConnectionDetails。

2026-08-10 P0-5 虚拟线程双模式压测矩阵闭环：等待型场景通过并落地默认开关
- 脚本升级为双场景：JDBC 分页（`/api/metricRows`）与等待型并发（注入
  `/api/wait` 端点模拟外部 IO 阻塞，`Thread.sleep` 阻塞式等待）。
- 等待型实测（80ms × 400 并发，8000 请求两轮复跑一致）：platform
  p50≈167ms/p95≈174ms、2239 req/s；virtual p50≈87ms/p95≈112ms、3954 req/s——
  虚拟线程延迟约减半（-48%）、吞吐 +77%；两轮 p50 差 1ms 内稳定。
- JDBC 分页对比（4000 请求/40 并发）：platform p95=17、virtual p95=16，
  RPS 4378 vs 4406——双模式同级，虚拟线程无性能回归。
- 依据 ADR-0029 决策 5 条件成立，Initializer v1 模板新增
  `spring.threads.virtual.enabled=true` 默认开启；新增
  `generatedProjectsEnableVirtualThreadsByDefault` 断言（33 tests 全绿）；
  真实消费者 `xq-platform-next` 重新生成后在默认虚拟线程下
  clean verify 4 tests 0 skipped 全绿。
- 业务失败两场景均为 0；ab Length 计数保持为连接复用观测伪影
  （此前已单独验证 30 次连续响应长度一致）。

2026-08-10 P0-5 虚拟线程双模式压测矩阵基线（脚本 `scripts/measure-virtual-threads.sh`）
- 在临时目录生成 PostgreSQL CRUD 消费者（`metricRows` 实体，manifest v1），以固定
  Hikari 池 16、Tomcat 线程上限 200 分别启动平台线程（默认）与
  `spring.threads.virtual.enabled=true` 双模式，ApacheBench 压制 `/api/metricRows`
  分页接口（真实 JDBC + Flyway migration + 50 行种子数据）。
- 实测（本机 macOS + Colima, JDK 25）：8000 请求/80 并发下 platform
  p50=11/p90=20/p95=25/p99=68ms、5636 req/s；virtual p50=10/p90=23/p95=30/p99=69ms、
  5890 req/s；两轮复跑（2000/40）趋势一致（virtual p95 19–30ms 与 platform
  20–32ms 同级）。业务失败均为 0（ab 无 Non-2xx 行），Failed 计数为 ab 对
  keep-alive 复用的 Length 观测伪影（已单独验证 30 次连续响应长度一致）。
- JFR 录制 `settings=profile`（platform/virtual 各约 2.3–2.9MB）确认
  jdk.ThreadStart 事件存在（virtual 45 条，含 container-0 容器线程）。
- 结论：等待型 MVC+JDBC 负载下双模式性能同级，虚拟线程无性能回归；按 ADR-0029
  决策 5 先保持默认平台线程，"新 MVC 项目默认 v-thread" 的开关需要更重负载
  （高等待/长阻塞场景）进一步压测后决定。矩阵已接入 CI 独立
  `virtual-thread-matrix` job（Ubuntu apache2-utils 提供 ab），不阻塞主质量门禁。
- 过程中暴露 JDK 25 + PG JDBC 环境事实：`jdbc:postgresql://127.0.0.1`（字面
  IP）经 `InetSocketAddress.createUnresolved` 连接失败（UnknownHostException），
  使用 `localhost` 正常——脚本已固化为 localhost，写入验证记录便于排查。

2026-08-09 首个外部消费者 `xq-platform-next` 出生（P3 前置验证）
- 用 Initializer CLI（manifest v1）在外部独立仓库 `~/01-code/xq/xq-platform-next` 生成
  `platformApp` 实体 CRUD 全栈：独立 `mvn verify`（Maven 3.9，JDK 25，真实 PostgreSQL 18.3
  Testcontainers）4 tests / 0 failure / 0 skipped，BUILD SUCCESS。
- 暴露并修复生成器缺陷：CRUD 测试示例值 `字段名-created/updated` 可能超过 `string(N)` 上限
  （channelType string(16) 越界导致 500）；`sampleValue`/`sampleJsonValue` 新增
  `paddedSample()` 按 `EntityField.size()` 截断；新增单测
  `crudIntegrationTestSamplesRespectStringSize`（string(8) 边界），32 tests 全绿。
- 独立构建直接暴露旧 SNAPSHOT 污染：`~/.m2` 中 8 月 5 日的 starter-persistence POM 缺
  mybatis-plus 版本（消费者传入失败）；重装最新 reactor 后解析正常——提示非 SNAPSHOT
  发布前必须先跑 consumer 通道刷新隔离仓库。

2026-08-09 P2 收口与 P1 可重复验证基线（`reset/0033-greenfield`，worktree 全量）
- P2 Create & Generate 四项退出门禁闭环（详见 §1）；`verify-initializer-consumer.sh`
  三通道（普通/postgres/CRUD 变体）与 `measure-ttcrud.sh`（124s/门禁 1800s）本地重跑通过：
  CRUD 全链路 create→get→update→list→delete→404 走真实 postgres:18.3-alpine，0 skipped。
- `verify-maven-consumers.sh` 本地重跑通过：reactor clean install 到隔离 repo 后，
  8 个 library 制品 sources/javadoc 伴随件齐全、18 个 consumer POM 无裸 `${revision}`、
  3 个制品 `spring-configuration-metadata.json` 存在、`maven-artifact-plugin:compare`
  可重复性通过、BOM 下 Maven 3.9.16 与 Maven 4.0.0-rc-6 双 golden consumer 均构建成功。
- `./mvnw clean verify` 全量（JDK 25 + Colima Docker）BUILD SUCCESS：241 tests /
  0 failure / 0 error / 0 skipped，`check-surefire-results.sh` 通过；offstate 最小应用
  `OffStateApplicationTest` 0 skipped。
- 剩余 P1 发布动作依赖 GitHub Packages PAT + 签名密钥（release/GPG）等仓库资产决策，
  形成真实 non-SNAPSHOT 发布记录后关闭 P1；P0 剩余分支保护（private + 免费版限制）与
  仓库可见性决策待定。

2026-08-04(续) CI 首次跑绿 + 基线合入 dev + 许可证决策。
- CI run 30904716377（`ubuntu-24.04` 原生 Docker）`completed=success`：全量 reactor verify +
  `check-surefire-results.sh` 0-skipped + Maven 3.9/4 consumer + CycloneDX SBOM。修复 2 个 Instant flaky
  （纳秒 vs PG `timestamptz` 微秒，commit `397b021`）后首次跑绿——关闭「CI 首次成功」P0 门。
- PR #2（`codex/scaffold-modern-baseline` → `dev`）已 merge（`5480457`）；scaffold 现代化基线
  （ADR-0029 P0 / 授权 S0 / Maven 4 RC6 / ADR-0033 Greenfield）集成进 `dev`。
- 许可证决策：**暂不开源**（私有/专有）。LICENSE P0 项按「不需要 OSS 许可」处理；未来若开源或对外发布，再定商业/开源许可。
- 分支保护：private 仓库 + GitHub 免费版**无法启用分支保护**（HTTP 403，需 GitHub Pro 或转 public）。CI 仍跑在
  `pull_request` / `push(dev,main)`，靠「绿了再合」软约束；硬性 gate 待仓库可见性/计费决策。

2026-08-04 关闭 P0 的 Maven 4 Wrapper 阻断。RC6 现已正式同步到 Maven Central
（`https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/4.0.0-rc-6/`，HTTP 200）。此前
`.mvn/wrapper/maven-wrapper.properties` 的 `distributionSha256Sum` 取自 Apache 临时候选目录那份发行包，
与 Central 正式发布版字节不同，导致 `./mvnw` 报 "Failed to validate Maven distribution SHA-256"。已用
Central 正式发布版的 SHA-256 更新 wrapper（`e7a17cac…`，并经官方 `.sha512` 兄弟文件 `8167e73d…` 交叉
校验证明为 Apache 真品）。更新后从干净缓存首次跑通：`./mvnw --version` 显示 Maven 4.0.0-rc-6；
`./mvnw clean verify`（JDK 25、Docker/Colima 在线）15 模块 BUILD SUCCESS，326 tests / 0 failure / 0 error /
起初一次跑得 **105 skipped**（根因是 wrapper SHA 过期，已修）。0-skipped 门禁同日关闭：用 `testing.md` §4
既有的 Colima 配方（`DOCKER_HOST` 指向 Colima socket + `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`）
跑全量 `./mvnw clean verify`，达成 **326 tests / 0 failure / 0 error / 0 skipped**，`scripts/check-surefire-results.sh`
通过。先前 105-skip 的真因是 Testcontainers 的 Ryuk 容器无法 bind-mount 裸 Colima socket 路径（virtiofs
`operation not supported`），导致 `DockerAvailableDetector` 误判无 Docker、`disabledWithoutDocker` 跳过全部 105 个
集成测试；单独设 `DOCKER_HOST` 不够，必须配合 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 让 Ryuk 把 socket 挂到
`/var/run/docker.sock`（Colima 映射）。同日 `scripts/verify-maven-consumers.sh` 通过：15 个 consumer POM 无裸
`${revision}`、3 个制品含配置元数据、`artifact:compare` 可重复性、Maven 3.9+ 与 Maven 4 golden consumer 均能
经 BOM 构建。至此 ADR-0026 §验收方式 全部本地满足。后续（见下条）：CI 已首次跑绿、PR #2 合入 dev、
许可证决策为暂不开源；P0 仅剩分支保护（受 private + GitHub 免费版限制）与秘密扫描。

2026-07-31 静态核对当前工作树：`ainer-starter-web` 已使用
`spring-boot-starter-webmvc` 与 `spring-boot-starter-webmvc-test`，ADR-0029 T0 第 1 项的
Web Starter 实现范围已经落地。当前工作树的最终 Maven 4 验证仍未完成：Wrapper 配置的
Maven 4.0.0-rc-6 持久下载地址仍返回 404，因此无法从干净缓存完成 `./mvnw --version` 与
`./mvnw clean verify`。该阻断不回退已经实现的 POM 修改，但在官方发行包可用、完整 Reactor 与
consumer 门禁重跑通过前，当前工作树不能形成发布候选。

2026-07-30 已用 JDK 25、Maven 4.0.0-rc-6 与
`postgres:18.3-alpine` Testcontainers 完成 MyBatis-Plus Boot 4 persistence starter 原型。
原型验证 `BaseMapper` insert/select、PostgreSQL `DEFAULT uuidv7()` 生成键回填与 UUID version
7、自定义 XML 共存、显式 tenant 条件、分页 total/记录，以及自动配置中的全局
`IdType.AUTO`、UUID TypeHandler 和 `maxLimit=100`。随后在同一 JDK 25 / Maven 4.0.0-rc-6 /
Colima 环境执行完整 `clean verify`：14 个 Reactor project、303 项测试、0 failure、0 error、
0 skipped，既有复杂 XML 与真实 PostgreSQL 路径全部回归。隔离发布门禁也已通过：Maven 4
producer/Consumer POM 与可重复制品检查成功，Maven 3.9.16 和 Maven 4 外部 golden consumer
均能导入 BOM、消费 persistence starter 并编译 `BaseMapper<?>` 引用。该结果接受 ADR-0028
的受限基础设施增强，不代表整个项目已达到发布候选状态。

2026-07-30 隔离评估过把基线提升到官方 OpenJDK 27 EA Build 32。Maven 4 可以在该 JDK 上完成
`validate` 并开始以 `--release 27` 编译，但 ArchUnit 1.4.2 无法读取 class major 71，架构测试
因此不能导入被测类；Spring Boot 4.1 的官方兼容范围同时仍止于 Java 26。项目没有关闭架构规则，
也没有引入 ArchUnit 快照或私有补丁，而是按 ADR-0027 保持 JDK 25 LTS、`--release 25` 和
Enforcer `[25,26)`。JDK 27 只保留为 GA 与依赖生态就绪后的升级候选。

2026-07-30 使用 JDK 25 与已校验的 Apache Maven 4.0.0-rc-6 发行包预置 Wrapper 缓存，在当前
工作区完成 `./mvnw clean verify`：14 个 Reactor 模块成功；Surefire 共发现 300 个测试，
0 failure、0 error，其中 104 个 Testcontainers 测试因当前机器没有 Docker 而跳过。
`scripts/verify-maven-consumers.sh` 的隔离 `install`、两次制品比较和独立 Maven 4/Maven 3.9+
consumer 均成功；两类 consumer 都能导入 `ainer-dependencies` BOM 并消费 Starter。标准
Consumer POM 中的 `${revision}` 均有当前安装版本属性可解析；这里不包括 Maven 4 额外保存的
`*-build.pom`。关闭 Flatten Maven Plugin 后，标准 Consumer POM 与原方案逐字节一致；额外启用
`maven.consumer.pom.flatten=true` 只会显著展开 POM，因此当前固定为 `false`。使用 Maven 3.9.16
执行生产者 `install` 会在 parentless BOM 的 `validate` 阶段失败，并在写入任何 `dev.ainer`
制品前停止。这些结果证明迁移实现可行，但不等于发布候选的 0-skipped 门禁，也不证明正式 CI
已建立。

上述验证使用的发行包来自 Apache 临时候选目录并已完成官方摘要校验。仓库已生成 Maven Wrapper
3.3.4，并配置
Maven Central 持久地址与发行包 SHA-256；但 2026-07-30 当前执行 `./mvnw --version` 仍因
Central 尚未同步该发行包而下载失败。ADR-0026 禁止回退到可能被删除的候选目录；只有官方端点
可用且 `./mvnw --version`、`./mvnw clean verify` 实际通过后，Maven 4 构建切换才能标记为完整
实施。

2026-07-28/29 的 M4.8B + M4.8C 候选在 macOS Colima、Testcontainers 2.0.5 与
`postgres:18.3-alpine` 环境完成完整 `mvn clean test`：14 个 Reactor 模块成功，全部测试
实际执行通过，0 failure、0 error、0 skipped。真实 PostgreSQL 从空库执行全部 migration，
覆盖：单租户用户 `GET /api/me/tenants`、SERVICE 403、多租户选择非默认 tenant 后 token
`tenant_id`/`roles` 来自实时 membership、OWNER 转移角色原子交换 + 审计 + 双方 access event、
非 OWNER 发起被拒、非 ADMIN 目标被拒、每 tenant 最多一个未完成转移、发起者取消后可再次发起、
并发接受只能成功一次（4 线程）、过期转移不可接受但可取消、HTTP 端到端 ownership-transfer、
OWNER 丢失恢复双 SERVICE request/approve + 同一 SERVICE 拒绝 + 非 ADMIN 目标拒绝。

2026-07-27 在 `https://ainer-dev.xiaoqu99.com` 完成首次真实公网联合验收。Authorization Server
release 为 `3f9420a4425f11e78feace776fe0b15853a0b884`，Ainer Studio/Admin release 为
`d13fe026cd5422f85f03c443e09f825c05e114a1`；systemd 与独立 PostgreSQL 18.3 均在线，空库
实际执行 16 份 migration，公开 discovery issuer 与规范 origin 一致。无网络拦截的 Chromium
实际完成表单登录、Authorization Code + PKCE、成员读取/添加、MEMBER → ADMIN → MEMBER、软移除、
当前 access token 撤销、RP-Initiated Logout 和退出后重新访问要求登录。公网延迟暴露的 Studio
退出导航/路由守卫竞态已由 `d13fe02` 修复并复验通过；fixture 运行开关已关闭，密码不在 Java
EnvironmentFile。

2026-07-27 的 M6 品牌登录候选在 macOS Colima、Testcontainers 2.0.5 与
`postgres:18.3-alpine` 环境完成 `mvn clean test`：14 个 Reactor 模块成功，71 个测试套件、
281 个测试全部实际执行通过，0 failure、0 error、0 skipped。真实 Chromium 直接访问候选
Authorization Server 的 `/login` 与 `/login?error`，并以同一服务端模板/CSS 检查合同规定的
429/503 视觉状态；normal、credential-error、rate-limited、service-unavailable 四种状态均完成
1440×900 和 390×844 截图复核，桌面/移动共 8 组 axe-core 4.12.1 扫描均为 0 violation。
服务端测试同时覆盖 CSRF、SavedRequest/PKCE、通用凭据错误、一次性 503、HTML 429、
`Retry-After`、no-store 与既有 Passkey/WebAuthn ceremony。Studio 合同和 Tokens 的 SHA-256
分别保持 `e8e50c266957c7fe14af4b4e30508dd6fe52f43c12029261d8a44e5d51ce2786` 与
`2a8eeed8d598ebc647163662a7de8f7bb0d0ce2e3a171e2392e638ba75c095d8`。该候选尚未推送或部署，
不能替代现有 dev 公网验收。

2026-07-27 部署工具通过 `bash -n`、隔离路径/精确代理静态门禁和 `git diff --check`。Java
`mvn test` 的 14 模块构建成功，但执行机当时没有 Docker，Authorization Server 的 30 个
Testcontainers 测试按既有 `disabledWithoutDocker` 策略跳过；该次运行不替代下述 0-skipped
基线，也不能作为公网 dev 部署验证结果。首次上线仍必须用服务器真实 PostgreSQL migration 和远程
Chromium 联合验收关闭门禁。

2026-07-26 在 macOS Colima、Testcontainers 2.0.5 与 `postgres:18.3-alpine` 环境执行完整
`mvn test`：14 个 Reactor 模块成功，67 个测试套件、271 个测试全部实际执行通过，
0 failure、0 error、0 skipped。

本轮 Ainer Admin 验证使用固定 `ainer-admin-dev` public client、同一 HTTP cookie session 与
`postgres:18.3-alpine` 从空库执行 Authorization Server 16 份 migration。端到端覆盖
Authorization Code + PKCE S256、无 Refresh Token、default tenant/OWNER claims、成员 GET、添加
已有用户、MEMBER → ADMIN → MEMBER、软移除与 `[ADDED, ROLE_CHANGED, ROLE_CHANGED, REMOVED]`
审计；自助撤销后旧 access token 被 active gate 返回 401，ID token 仍可完成
`/connect/logout` 并精确返回 `/ainer-admin/auth/logged-out`。全量回归还把旧成员 API 集成测试
从“只签名 JWT”改为持久化真实 active authorization，避免测试绕过新的在线活性边界。
本次已把 `dev@a22e121` 的 M4.8A 合入 Ainer Admin 分支。Java、POM、`application.yaml`、Admin
OpenAPI 与 M4.8A migration 自动合并且没有 migration 版本碰撞；7 个冲突全部位于 README、
Changelog、ADR 索引和状态文档，并已保留双方内容。安全链顺序保持为协议端点、内部控制面、
M4.8A 激活、Admin 成员/revoke active gate、指标和默认登录；M4.8A tenantless SERVICE Token
夹具与 Admin active authorization 夹具彼此隔离。融合回归还修正了 M4.8A 新用户激活测试把
`IdentityAccount.roles()` 误写为领域角色 `OWNER` 的断言，使其与既有 Spring authority
`ROLE_OWNER` 契约一致；对外 Token `roles` claim 仍为 `OWNER`。
`docs/README.md` 继续只是目录门面，`docs/00-overview.md` 是唯一权威入口并已纳入
`ainer-admin-integration.md`。严格 OpenAPI 校验和 TypeScript SDK 生成成功；
`ainer-admin-v1.yaml` SHA-256 保持
`1269a0e325f645ab9371a7783635e0a7cdfe1bfad4cd11b56bc6ade5f2468056`。

本轮 M4.7 新增 Identity 管理面验证：Identity 模块从空库执行 6 份 migration，真实 PostgreSQL
覆盖成员列表、按 username/subjectId 加入、角色变更、软移除、DISABLED 重激活、OWNER 保护与每次
写入的 operation/reason/request ID 审计。Authorization Server 从空库执行 13 份 migration，
随机端口 HTTP 使用实际 RSA Bearer JWT 覆盖匿名 401、缺 scope/SERVICE/MEMBER/跨 tenant 403，以及
加入、列表、改角色、移除和 3 条审计落库；同时证明成员 API 只使用 Identity 权威数据库。
bootstrap 用例证明首次创建、重复执行不覆盖密码、部分 tenant/username 占用失败关闭。

此前 M4.8A 预配申请验证：Identity 模块从空库执行 7 份 migration，真实 PostgreSQL 覆盖规范化
请求、相同摘要幂等重放、同幂等键下 tenant name/change reference 变化冲突、tenant code 双线程并发
预留只成功一次、过期释放、ACTIVE 用户复用、LOCKED 用户拒绝、与 bootstrap 共享冲突门禁、核心
tenant/user/membership 零污染和 request/phase audit。Authorization Server 从空库执行 14 份
migration，随机端口 HTTP 使用实际 RSA Bearer JWT 覆盖匿名 401、缺 header 400、缺成对 scope、
tenant-bound SERVICE、USER、白名单外 operator 的 403，以及 POST/GET、安全投影、no-store、
幂等冲突和审计落库。配置与 bootstrap 单元测试覆盖空 operator、TTL 边界、弱 secret 和策略不匹配
既有 client 的启动失败。

本轮激活核心增量验证：本机 PostgreSQL 18.4 随机 schema 经 Flyway 实际执行 Identity 全部 8 份
migration，跑通申请、AES-GCM 通知解密、错误 secret 次数持久化、成功原子激活和回放拒绝，结束后
schema 已清理；同一批 migration 还在单事务临时 schema 中完整执行并回滚。新增不依赖 Docker 的
4 个测试覆盖 AES-GCM round-trip、tamper、未知 key、旧 key rotation 读取与 provider 失败延迟重试；
配置测试覆盖 activation TTL/次数/key ring 失败关闭。完整 Identity PostgreSQL 用例又加入锁定、
过期、已有用户 subject 绑定、默认 tenant 保留、核心写入失败回滚和密文不含 secret 断言。

本轮通知 transport 增量已实现独立 tenantless relay client bootstrap、OAuth2 Client Credentials、
HTTPS gateway publisher、稳定 `Idempotency-Key`、调度领取、失败分类和 pending/failed/exhausted/
cancelled/oldest-ready 指标。HTTP 合约测试证明 Bearer、版本化 envelope、新用户激活材料和已有用户
无 secret 投影；配置测试证明普通 HTTP、带 query 的 URI 和非法重试边界失败关闭。网关 2xx 后或
请求取消时，Identity 会销毁 `protected_payload` 的可解密内容；`PUBLISHED` 只证明网关持久接收，
不代表邮件、短信或站内信最终送达。

本轮平台控制增量增加成对 write scope 的显式 cancellation，以及各自 read scope 的 tenant/user
安全分页。非 Docker 单元测试覆盖 tenantless SERVICE、operator 白名单、scope 拆分、分页边界和
取消指标；PostgreSQL 18.4 隔离 schema 重放全部 8 份 Identity migration，并实测 request/grant/
outbox 同时 `CANCELLED`、payload 销毁和取消审计。Testcontainers 用例还覆盖重复取消不重复审计、
新用户 grant 缺失时整笔回滚、已有用户无 grant、稳定排序/total 和 HTTP 响应不含凭据数据。

本轮终态回执增量增加独立 gateway client bootstrap、tenantless SERVICE + 专用 scope + 精确白名单
安全链、`DELIVERED|FAILED` 最小模型、单 notification 终态与 gateway event 幂等、抢先回执状态
冲突以及首次终态 Counter。10 个不依赖 Docker 的新增测试覆盖参数/未来时间、回放/冲突、Controller
安全投影、配置失败关闭和 bootstrap 策略。本机 PostgreSQL 18.4 隔离 schema 从空库重放全部 9 份
Identity migration，验证回执 UUIDv7、合法终态写入、重复 notification 唯一冲突和 `FAILED`
空失败码拒绝；该 smoke 实际发现并修正了 SQL 三值逻辑下 NULL 逃逸 check 的问题，schema 已清理。
随机端口 OAuth2/Bearer 与真实事务 Testcontainers 用例已写入并通过 test compilation，但当前无
Docker，尚未实际执行，不能计入 0-skipped 发布验证。

当前机器未运行 Docker。本轮干净执行 `mvn clean test` 时 14 个 Reactor 模块全部成功，
Surefire 共发现 61 个测试套件、256 个测试；其中 172 个实际执行并通过，0 failure、0 error，
84 个 Testcontainers 测试因 `disabledWithoutDocker=true` 跳过。因此上述本机 PostgreSQL smoke
是真实增量验证，但不是新的完整 0-skipped 发布快照。上方 221-test 结果仍是最近一次完整不跳过
基线，合并/发布前必须在 Colima/Testcontainers 可用环境重跑全量并更新数字。

本轮安全收口还验证：Passkey 恢复/enrollment 对目标 ACTIVE default membership 的跨 tenant guard 与
数据库复合外键；登录限流在 context path 下对 WebAuthn options 返回统一 429、
`Retry-After`/no-store 并记录 allow/deny；step-up 真实 HTTP 覆盖匿名 401、USER 成功、SERVICE/
缺因子/旧时间/越界未来时间 403。全量测试还暴露并修正了 AI 测试 JWT 缺必填 `actor_type` 的旧夹具。

累计 Phase D resource server step-up 验证：`RecentStrongAuthenticationFilter` 单元测试覆盖
`amr` 含必需因子且 `auth_time` 新鲜放行、密码 Token 缺 `mfa` 返回 403（错误体含特定错误码）、
`auth_time` 过期/缺失拒绝、缺 `amr` 拒绝、非 JWT 认证拒绝、非受保护路径不节流，以及 `StepUp`
配置校验拒绝空规则/空 `required-amr`/超 24 小时 `max-auth-age`。这是 resource server 第一次消费
Authorization Server 在 Phase A 签发的 `amr`/`auth_time`。filter 默认关闭，与在线校验 filter 同锚点。

累计 Phase C 限速与受控 enrollment 验证：限速器单元测试覆盖窗口内放行、超额拒绝、不同 key
独立计数、跨窗口复位与 `Retry-After` 取整；受控首次 enrollment 在真实 PostgreSQL 上验证
`require-invite` 模式——无授权的首枚 Passkey 登记被拒（`ENROLLMENT_GRANT_REQUIRED`），操作员建立
授权后首登成功且授权同事务置 `CONSUMED`，已有 ACTIVE Passkey 的 replacement 不受影响。限速明确为
node-local（全仓无 Redis），多实例需共享存储留待后续。限速 filter 的端到端 HTTP 429 已完成；
enrollment 服务控制面与真实登记拒绝路径已由 PostgreSQL 集成测试覆盖。

累计 Phase B Passkey 恢复验证：恢复码自助流程在真实 HTTP 会话与 PostgreSQL 上跑通——
真实 Passkey 登记后签发 8 枚高熵一次性恢复码（明文仅返回一次，库内只存 bcrypt 哈希），
密码登录本人用一枚恢复码赎回后，该账号全部 ACTIVE Passkey 被吊销并写 `SELF_RECOVERY`
安全操作审计，用户可重新 bootstrap。管理员双人恢复在 service 层用真实事务验证：申请者建立
`REQUESTED`，同服务批准被拒（`RECOVERY_APPROVER_MUST_DIFFER`），不同服务批准成功、吊销目标
全部 Passkey，`(operation_id, phase)` 偏唯一审计为 `[REQUESTED, EXECUTED]`，重复批准被拒。
恢复码失败尝试按 subject 累计并锁定；最后凭证保护在恢复上下文中被安全越过（不破坏普通自助
删除的最后凭证保护）。通知（含联系字段与可达通道）仍为已知缺口，未在本切片交付。

累计 Phase A Passkey 真实签名 ceremony 端到端验证：用 webauthn4j 虚拟 authenticator
驱动 `/webauthn/register`（真实 attestation）与 `/login/webauthn`（真实 assertion）闭环，
真实走通 Spring Security 7.1 的 `Webauthn4JRelyingPartyOperations` 签名校验代码路径；
Passkey 用户完成授权码流程后，access token 携带 `amr=pwd,mfa,pop`、`auth_time`、稳定
`sub`（subjectId UUID）、`tenant_id` 与 `roles`。这组测试同时揭露并修复了三个此前被合成
CredentialRecord 测试掩盖的真实缺陷：OAuth2 授权记录无法反序列化 `WebAuthnAuthentication`
主体（注册 `WebauthnJacksonModule`）、Passkey 用户 token 因 customizer 不认 WebAuthn
principal 而缺 Ainer claims（customizer 改为按 username 解析 `AinerUserDetails`），以及
凭证管理端点 `/webauthn/register/**` 因协议 filter 在授权 filter 之前短路而未被条件 MFA
门禁保护（新增 `AinerPasskeyCredentialManagementGateFilter`，锚定 `CsrfFilter` 之后、协议
filter 之前）。HTTP 层门禁测试覆盖：已登记账号在缺因子时 `/webauthn/register/options` 与
`DELETE /webauthn/register/{id}` 均 302 到 `/login`，凭证不被新增或删除；首次 bootstrap
（未登记账号）不受影响。本轮尚未覆盖真实设备/浏览器兼容矩阵。

累计 OAuth Client 生命周期验证：创建
managed client 后只保存 password hash，一次性 secret 可正常换取 tenant-bound JWT；未授权 scope
返回稳定 422，tenant-bound operator 返回 403。蓝绿轮换期间新旧 ID 并行可用，显式退役后旧
secret 换 Token 返回 401、历史 Token introspection inactive，而 Spring 官方 JDBC authorization
仍可重建历史记录。配置测试覆盖空 operator、平台/`.all` scope、过长 access token 和弱随机
secret 的启动失败；operator bootstrap 只创建无 tenant、`oauth.clients.manage`、一分钟 Token。
创建、轮换、退役审计表没有 secret 字段。

本轮新增 PKCE 验证：测试专用 public client 通过真实表单登录、cookie/CSRF 和 S256 challenge
取得 authorization code，并用正确 verifier 交换出包含人员 `sub`、`tenant_id`、`roles` 的
access token 和 OIDC ID token；响应不含 refresh token。authorization code 重放、错误 verifier、
缺失/`plain` challenge 均失败，未注册 redirect URI 不发生外部跳转。真实 JDBC 往返暴露并修复
了 Jackson 3 拒绝 Ainer 人员 principal 的问题；修复只增加精确类型白名单，并证明授权记录不含
密码或 password 字段。

本轮新增 Passkey 验证：Authorization Server 从空库执行七份 migration，创建 Spring 官方
WebAuthn 协议表和 Ainer 生命周期/审计表。配置门禁拒绝 IP 型 RP ID、越界 Origin、普通 HTTP、
重复 Origin、路径和过长 timeout；真实 HTTP registration options 强制 resident credential 与
`userVerification=required`。无凭证账号仍可用密码完成 PKCE，Token 含 `amr=pwd` 和
`auth_time`；登记合成 credential 后，仅密码不能取得 authorization code。JDBC 门禁验证登记、
计数器/last-used 更新、replacement、软撤销、审计和协议记录保留；并发撤销两个 ACTIVE
credential 时只允许一个成功，最后一个保持 ACTIVE。后续虚拟 authenticator 签名 ceremony 已完成，
但主流真实设备兼容矩阵仍未完成，因此不能把自动化验证表述为生产兼容性认证。

本轮新增指标安全验证：Resource Server 真实 HTTP 测试覆盖无 Token 401、USER/tenant-bound SERVICE/缺 scope 403、tenantless SERVICE 200，并使用自定义 management base path 证明路径配置不会绕过授权；路径 matcher 还覆盖 context path、尾斜杠和编码路径。业务 Resource Server 显式关闭时，真实 Prometheus endpoint 仍拒绝匿名并且不返回 JVM/process 指标。metrics bootstrap 测试证明只创建 Client Credentials、无 tenant、只有 `platform.metrics.read`、一分钟 Token、无 introspection 标记，重复运行不覆盖且弱 secret 失败关闭。Authorization Server 的真实 PostgreSQL 协议测试已实际验证专用/tenant-bound metrics Token 与 exporter 的 401/403/200，并同时覆盖 Client Credentials、OIDC discovery、专用 introspection、RFC 7009 与 Identity revocation epoch。

同日使用本机真实 PostgreSQL 18.4 从空库执行 Identity 四份、Workspace 八份全量 migration。除 M4.1 的 outbox 领取/撤销验证外，本轮实际执行了耗尽原事件双人重放事务、REVOKED OWNER 提升新 OWNER 事务、安全操作审计约束，以及授权审计归档 CTE、热冷统一查询和导出审计。原事件内容保持不变，旧 OWNER 保持 REVOKED，归档后热表 0/归档表 3；两个一次性数据库均已删除。loopback HTTP 测试实际验证 Client Credentials Token 获取/缓存和 Bearer 事件发布。

M4.3 另使用本机 PostgreSQL 18.4 从空库执行 Authorization Server 五份 migration 并实际启动发行物。协议 smoke 证明普通 client introspection 返回 401、专用 client 对新 Token 返回 `active=true`、RFC 7009 revocation 返回 200 且随后 `active=false`。真实 JDBC 往返同时暴露并修复了 Boot 4/Jackson 3 对 JDK 私有不可变 claim 集合的反序列化拒绝。对 5,000 条合成 access event 的 revocation epoch 查询使用 `idx_ainer_identity_access_event_subject` Index Only Scan，实测执行约 0.036 ms；旧/等于 epoch 的 Token inactive，事件后的 Token active，membership 禁用后当前身份 inactive。一次性数据库和 RSA 测试密钥目录均已删除。

该结果证明当前源码基线可构建，且全部自动化 PostgreSQL 集成组已在本地 Docker-compatible runtime 中实际执行；它仍不是独立发布候选环境的验证结果，也不证明生产容量、备份恢复或高可用。

## 4. 已知缺口

### 访问控制

- 通用混合细粒度授权 S0 已落地（ADR-0030）：`ainer-module-authorization` 拥有不可变领域契约
  （Permission/Role/SubjectBinding/Scope/AuthorizationDecision）、PermissionRegistry（冲突检测）
  和 AuthorizationService 纯决策器（grant-path 真值表 + resourceType/systemOnly/GLOBAL/scope 安全
  检查 + HIGH-risk Challenge + PublicAccessPolicy 投影），16 项测试通过。但 S1（PostgreSQL 持久化）、
  S2（Spring adapter + 管理 API）、S3（关系/查询/Golden Consumer 验证）、Agent 代行（ADR-0031）
  与组织（ADR-0032）尚未实现；现有人员登录与
  `AuthenticatedActorResolver` 仍要求 ACTIVE tenant membership，tenantless USER consumer client/
  签发/解析/撤销合同也尚未实现，不能仅凭 Proposed 可空 principal 宣称已支持真实顾客；
- 组织与员工目录当前只有 ADR-0032 和详细方案；`ainer-module-organization`、OrgUnit、
  WorkforceEngagement、Position/Assignment、SubjectSetBinding、管理 API 和 XQ 岗位纵向切片均未
  实现。普通 tenant 成员角色变更/移除还没有完整写入 access-event outbox，Identity 已定义的
  role-changed 事件与 Workspace consumer 合同也不兼容；在修复并验证 Token 失效、Workspace 撤销、
  workforce-derived grant 撤销三条独立语义前，不能承诺调岗/离职即时失权；当前 subject-scoped
  access event 也不能表达 tenant-wide disable，需补 tenant epoch/event 或等价在线门禁；
- 选择性在线校验只覆盖配置的高风险 API；普通低风险自包含 JWT 仍有自然到期窗口；
- Authorization Server 已成为高风险 API 在线依赖；受保护 exporter 与独立 metrics/introspection 凭据创建基线已有代码，但尚未完成生产高可用、容量、旧凭据退役、真实 Prometheus、dashboard 与告警；
- 重放与 OWNER 恢复已做服务 `sub` 分离，但生产 IAM 仍需证明凭据由不同人员/职责保管；
- 授权审计归档仍位于同一数据库，没有 WORM、数字签名、法律保留和最终删除策略；
- SIEM 只有默认关闭的拉取 API，没有部署外部消费者、不可变副本或告警路由；
- Directory/relay/consumer 与 M4.2 控制面默认关闭，尚未在真实多节点环境完成容量、故障注入和滚动 30 天撤销 SLO 验证。

### Identity 与 OAuth

- tenant-bound Client Credentials 已有生命周期控制面，但 browser/OIDC、平台级
  metrics/introspection/operator、`.all` 与既有 bootstrap client 尚未纳管，也没有列表分页、审计
  导出、双人审批或 UI；
- Authorization Code + PKCE 与 Passkey 条件门禁、虚拟 authenticator 签名 ceremony、恢复、
  受控 enrollment 和 Resource Server step-up 已有自动化验证，但生产 browser/OIDC client 控制面、
  恢复通知、真实设备矩阵、共享限流、多节点会话和签名密钥轮换未完成；品牌登录合同 1.0.0
  明确不提供可见 Passkey 动作，因此需要人员 Passkey 登录的部署仍需等待 Studio 新合同；
- 平台级 tenant/user 控制面已有默认关闭的预配申请/查询、一次性激活核心、加密 notification
  outbox、OAuth2/HTTPS 通知网关 relay、已有用户本人接受、安全分页、显式取消与 provider-neutral
  终态回执接收；真实外部通知网关/供应商、供应商回执映射和最终送达验证、禁用/恢复、tenant
  ownership transfer 和成员管理 UI 尚未完成；
- tenant 成员 API 已随 S8 删除；Ainer Admin 只保留当前会话自助撤销端点，其撤销端点已强制
  逐请求 online active gate，数据库故障返回 503 并失败关闭；不能把业务 Server 的默认
  step-up 规则误认为覆盖该端点；
- Ainer Admin 同源代理已有契约但尚未在选定生产 ingress 上完成 HTTPS、Cookie、重定向和缓存
  验收；`ainer-admin-dev` 与开发身份不能替代生产 browser client 生命周期和正式开户。

### AI 平台

- 限流仍是单进程基线，未形成集群级一致性；
- provider 凭据托管、指标、trace、输出策略、评测、RAG 与 Agent runtime 尚未完成；
- 通用 Run / Artifact 与 Knowledge 仍只有 Proposed 数据模型；ADR-0023 已提前落下受治理
  Task/Run/Result/Feedback migration 与应用代码，但 ADR 仍为 Proposed，且事务、tenant/actor
  授权、UUIDv7、invocation linkage、Context 实际输入和正文数据治理尚未通过验收，因此不得
  描述为已完成平台能力；
- 供应商兼容面仅覆盖当前 OpenAI-compatible 最小协议。

### 工程与运营

- PostgreSQL Native-First 目标已由 ADR-0020 和数据库规范 1.2 确立，但当前持久化实现仍大量使用
  UUIDv4，Workspace/AI `tenant_id` 仍为 `varchar(128)`；M4.8A 新增 request/grant/outbox/audit
  /receipt 及预留 tenant/subject 已率先使用 PostgreSQL `uuidv7()`，但统一 `RETURNING`、全域
  tenant UUID 化和 1.0 clean baseline 尚未实施；
- MyBatis-Plus Boot 4 starter 的真实 PostgreSQL 原型、全量 Reactor、既有复杂 XML 与
  Maven 3/Maven 4 外部 consumer 回归已经通过；后续风险转为版本升级回归、规则误用和正式 CI
  尚未固化这些门禁；
- Maven 4.0.0-rc-6 仍是 preview；RC6 已于 2026-08-04 确认正式同步到 Maven Central，wrapper SHA 已
  修正为 Central 正式发布版校验值，`./mvnw --version` 与 `./mvnw clean verify` 已从干净缓存跑通
  （15 模块 BUILD SUCCESS）。用 `testing.md` §4 Colima 配方跑全量 verify 已达成 `0 skipped`
  （326/0/0/0，见 §3 2026-08-04 记录）；`scripts/verify-maven-consumers.sh` 也已通过（consumer POM、
  配置元数据、可重复性、M3.9+/M4 consumer）。ADR-0026 §验收方式 已本地满足；CI 已首次跑绿（见 §3
  2026-08-04(续)）、PR #2 合入 dev、许可证决策为暂不开源（私有/专有）；P0 仅剩分支保护（private + GitHub
  免费版无法启用，待可见性/计费决策）与秘密扫描（pre-public 前最有价值）；
- 已增加只读权限的候选 GitHub Actions 质量门禁，编排 JDK 25、Maven 4、Docker、
  PostgreSQL/Testcontainers `skipped=0`、Maven 3/4 consumer 与短期 CycloneDX SBOM；RC6 已上 Central、
  wrapper 已修、本地 `./mvnw clean verify` 已达成 `0 skipped`、consumer 门禁已通过（2026-08-04），工作流已
  首次跑绿（run 30904716377，ubuntu 原生 Docker）；但 private + GitHub 免费版无法启用分支保护/必需检查，
  目前靠「绿了再合」软约束，硬性 gate 待仓库可见性/计费决策，故尚未称为正式 CI；
   制品发布能力已部分闭环：`release` profile 生成 sources/javadoc 伴随制品，consumer 门禁断言其存在，
   根 POM `-Prelease` 构建本地验证（26 模块），GPG `.asc` 签名与 `actions/attest-build-provenance`
   provenance（SLSA v1）已接入 `release.yml`（secret 注入式，fail-closed）；尚未打真实非 SNAPSHOT
   tag 验证 GitHub Packages 端到端发布，正式签名密钥未生成/配置，`gpg.skip` 只用于本地与
   consumer 验证，签名轮换与首次签名发布记录仍待闭环；
- 没有具名模块维护者矩阵、`CODEOWNERS` 和正式审查责任分配；
- 没有生产备份恢复、容量测试、正式错误预算/告警路由和灾难恢复演练；
- 没有稳定版兼容政策；许可证决策为暂不开源（私有/专有），未来若开源或对外发布再定商业/开源许可；付费产品交付系统未建立；
- Testcontainers 仍使用 `disabledWithoutDocker`；本机 Colima 已能完整执行，候选 CI 已用
  `scripts/check-surefire-results.sh` 明确拒绝任何 skipped 测试，但该门禁仍需首次成功并纳入
  分支保护。

### 脚手架产品化评估

2026-07-30 的本地复审形成以下工程判断。百分比只用于确定投入顺序，不是发布承诺或质量度量：

- Ainer 作为安全、Identity、Workspace 与 AI 原生平台内核，成熟度约为 `65%–70%`；
- Ainer 作为可由外部项目直接消费和生成新项目的通用脚手架，成熟度约为 `35%–45%`；
- 当前不应复制 Ainer 源码创建产品仓库，应先完成制品发布、Project Initializer 和独立消费者门禁；
- `xq-platform-next` 应在 Scaffold Ready 与生成器门禁通过后作为首个外部消费者创建，不等待所有
  企业能力完成。

按
[`Ainer Boot 产品定位、竞品能力矩阵与路线图`](design/ainer-scaffold-design.md)
定义的全局产品化阶段，当前尚未退出 **P0 Baseline Integrity**：候选 CI 已多次跑绿但分支保护
 仍受 private 仓库/免费版限制待决；Wrapper 官方持久端点、许可证（ADR-0004 私有不开源）和正式
 发布门禁（signing key 首次真实生成与配置）仍未闭环。P1 发布能力（release profile、
 sources/javadoc、GPG、provenance）与 **P2 Create & Generate 已收口**（ADR-0035/ADR-0036
 Accepted：manifest v1、preview/diff、三通道 consumer 门禁、TTFR 100s 与 TTCRUD 124s 量化
 门禁均闭环）已有验证结果，但 P3 首个外部消费者尚未交付。该设计文档只维护长期阶段和退出
条件，本页继续独占当前阶段、完成记录与缺口。

ADR-0029「JDK 25 / Boot 4 现代化基线」P0 进展（均经 `mvn 3.9.16 + -Denforcer.skip=true` 验证；正式
`./mvnw clean verify`、零跳过门禁与 Testcontainers 集成仍待 Maven 4 RC6 官方发行包恢复后执行）：

- P0-2 出站 HTTP：消除全部 4 处 `RestClient.create()/builder()` 反模式，统一注入 Boot 管理的
  `RestClient.Builder`；AI SSE 保留 JDK `HttpClient` 并显式注释例外。`@HttpExchange` + Service Client Group
  延后——现有 relay 各有独立 client-credentials token provider，套用 group/configurer 比当前显式注入更重。
- P0-3 配置即契约：公开制品统一生成 `spring-configuration-metadata.json` 并纳入消费者门禁；为原本无校验的
  配置类补齐 `@Validated` 声明式约束；**全部 22 个 `@ConfigurationProperties` 已改为构造器绑定不可变**
  （保留 getter 名零破坏调用点，构造器内处理默认值；含密钥/密码的用不可变**类**而非 record，故无
  `toString()` 泄露；`Pricing.validate()` 的字段改写已移入构造器）。
- P0-4 空安全基线：`@NullMarked` 已覆盖 ainer-core、ainer-spring、ainer-security、ainer-starter-web
  （8 包）并标注真实 `@Nullable`；@NullMarked 模块已声明 jspecify 直接依赖。**NullAway 强制未接入**：
  error-prone 2.50 + NullAway 0.12.7 在 Maven 3.9.16/compiler 3.14.0 + JDK 25 下无法工作：forked 编译拿不到
  `--add-exports` 致 error-prone 崩溃；in-process 下即便经 `MAVEN_OPTS` 确保 `--add-exports` 生效，
  `-Xplugin:ErrorProne` 插件仍不挂载、javac 拒绝 `-Xep` 标志——属 compiler 3.14.0/JDK 25 插件加载不兼容，
  非配置可调。多次尝试（模块局部 forked、根 forked、根 in-process + `.mvn/jvm.config`、`MAVEN_OPTS`）均失败，
  此前因 Maven 4 RC6 wrapper 阻断无法在正式工具链验证（该阻断已于 2026-08-04 修复，见 §3），故已还原配置
  保持构建绿色；NullAway 作为「CI 接入」项可在现已可用的 Maven 4 工具链上重新评估。
- P0-5 虚拟线程：`aiStreamExecutor` 已标记 `@Bean(defaultCandidate=false)`；**双模式压测矩阵
  已闭环**（`scripts/measure-virtual-threads.sh`，见 §3 2026-08-10 记录）：JDBC 分页场景双模式
  同级无回归，等待型场景（80ms×400 并发）虚拟线程 p50 减半、吞吐 +77%；**已按 ADR-0029 决策 5
  落地 Initializer 模板默认 `spring.threads.virtual.enabled=true`**，xq-platform-next 在默认
  虚拟线程下 4 tests 全绿。

## 5. 下一里程碑

产品化主线调整为先关闭 P0，再依次进入 P1 Scaffold Ready（P2 Create & Generate 已收口）和
P3 首个外部消费者。近期可交付顺序是：

1. RC6 已上 Central、wrapper 已修正、本地 `./mvnw clean verify` 已达成 `0 skipped`、consumer 门禁已通过、
   CI 已首次跑绿（2026-08-04 run 30904716377）、scaffold 基线已合入 `dev`（PR #2）、许可证已决策（暂不开源，
   私有/专有）。P0 仅剩：分支保护（private + GitHub 免费版受限，需仓库可见性/计费决策）与秘密扫描
   （pre-public 前最有价值）；
2. 让 Wrapper 官方持久端点、非 SNAPSHOT 制品、最小 off-state 应用与 Maven 3.9+/4 外部消费者
   形成可重复发布验证记录，关闭 P1；
3. **P2 Create & Generate 已收口（2026-08-09）**：manifest v1、preview/diff、确定性生成与
   golden consumer 门禁交付（ADR-0035 Accepted）；CRUD v1 生成交付（ADR-0036 Accepted，
   `entities` manifest + 6 类 CRUD 文件）；`verify-initializer-consumer.sh` 三通道、
   TTFR 实测 100s/门禁 600s 与 TTCRUD 实测 124s/门禁 1800s 均接入 CI 并闭环。
   组织/行业模板按 ADR-0035 决策 7 属 Studio/Enterprise 扩展，移交 P3+，不阻塞 P2 收口；
4. **首个外部消费者 `xq-platform-next` 已生成（2026-08-09）**：独立仓库
   `~/01-code/xq/xq-platform-next`，Initializer 生成 `platformApp` CRUD 全栈，独立
   `mvn verify` 4 tests 0 skipped（真实 PostgreSQL 18.3）；并修复生成器 string(N)
   示例值越界缺陷（见 §3 验证记录）；
5. `python-learning-service` 已登记为第二个外部消费者（§13.5）：领域模型与 API 契约
   可先行开发，后台适配层隔离；等 P1 版本化 BOM/Starter 发布后按“版本化制品升级”接入，
   不绑定开发分支、不复制源码。

Identity、安全与运维纵深继续修复明确生产风险和 P0/P1/P3 阻塞项，但不再作为无限推迟
Initializer 与外部消费者的前置功能清单。

M4.8 已形成并接受
[ADR-0019](decisions/0019-identity-provisioning-tenant-context-and-ownership-governance.md)，
按依赖顺序拆为三个切片。M4.8A 已完成 tenantless SERVICE 预配、激活核心与真实 HTTPS gateway
transport、provider-neutral 终态回执接收，以及平台显式取消与 tenant/user 安全分页。M4.8B 已完成
`GET /api/me/tenants`、Authorization Code + PKCE 多租户选择流程与 token customizer 实时重查的
代码基线与真实 PostgreSQL + 真实 HTTP 集成验证；M4.8C 已完成双自然人确认 OWNER 转移状态机、
原子角色交换与双方 access event 撤销链路的代码基线与 PostgreSQL 集成验证；M4.8C OWNER 丢失
恢复（decision 30）已完成双 tenantless SERVICE request/approve 代码基线与 PostgreSQL 集成
验证；下一步把 M4.8B/C 候选部署到 dev 公网联合验收，补齐多浏览器会话并行、真实 HTTP
ownership-recovery 端到端、选择后撤销失效、真实多 tenant UI 与生产 browser/OIDC client 控制面验证。

M4.8 与商业 entitlement 保持正交：Identity `OWNER/ADMIN/MEMBER` 只表达授权角色，Community /
Pro / Enterprise、license、订阅和配额留给后续独立 entitlement 边界。生产并行工作仍包括真实
Prometheus 抓取、dashboard/告警、Authorization Server 多实例容量/故障验证、browser/OIDC client
生产控制面、真实设备/浏览器兼容矩阵、metrics/introspection/operator 旧凭据退役、Resource Server
灰度与安全降级审批，以及把 M4.2 操作审计接入生产级 IAM 职责分离和外部不可变存储。

完成条件包括：平台供应使用独立 tenantless SERVICE operator、最小 scope、幂等键、一次性激活
凭据与真实 PostgreSQL/HTTP 验证；tenant selection 支持并行会话且不能由请求参数伪造 claim；
OWNER 转移具备双方强认证、单一未完成请求、数据库唯一 ACTIVE OWNER、同事务审计和双方旧 Token
撤销验证；Passkey 通知、真实设备与多节点 session 完成验证；发布候选环境的 Testcontainers
测试不跳过；
高风险 online validation 在目标容量与故障注入下满足确定的延时/可用性目标；request/approve
与平台凭据在运营上真正分离并可轮换；外部审计副本可验证、可恢复；初始撤销 SLO 经多节点验证
修正后形成正式错误预算。随后继续补齐浏览器/平台 Client 控制面、签名密钥轮换与独立商业
entitlement 内核。

## 6. 更新规则

- 只写已经有代码、migration、测试或明确验证记录的完成项；
- 测试数量和版本变动后更新本页，不散落到长期规范；
- 缺口关闭时同时更新对应 ADR、专题文档和 Changelog；
- 发布版本形成后保留历史 Changelog，本页只保留最新状态。
