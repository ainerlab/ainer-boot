# ADR-0016：登录限速与受控首次 Passkey Enrollment

- 状态：Accepted
- 日期：2026-07-25
- 决策者：待指定
- 取代：无
- 被取代：无

## 背景

ADR-0014/0015 已建立 Passkey 优先认证、条件 MFA、恢复码与管理员双人恢复。但登录与
enrollment 路径仍有两条未加固的滥用入口：

1. **在线暴力/撞库**：`/login`（密码）与 `/login/webauthn`（Passkey 断言）目前没有任何限速。
   人员账号的密码哈希是离线慢哈希，但在线试错不受限。恢复码赎回虽已有 per-subject 锁定
   （ADR-0015），但 `/login` 与 options 端点完全没有节流。
2. **首个 Passkey 抢先 enrollment**：ADR-0014 明确的风险——“在首个 Passkey 登记前，密码被窃取
   的攻击者仍可能抢先 enrollment”。当前无 ACTIVE Passkey 的账号可用密码 bootstrap 直接登记
   首枚 Passkey，没有任何邀请或审批 gate，尤其对高权限账号（租户 OWNER/operator）风险显著。

当前事实：Ainer 唯一的限速器是 AI 网关的 `TenantRateLimiter`（node-local、固定窗口、per-tenant、
内存）。全仓**没有 Redis 或任何共享状态**（已核实），因此限速只能 node-local。Passkey
enrollment 的首登入口在 `AinerJdbcPasskeyCredentialRepository.register()`，目前只校验 Identity
账号 enabled + nonLocked。

## 决策驱动因素

- 在线暴力与撞库必须被显著减速，但不能引入 Redis 等尚未存在的共享依赖；
- 限速器要可复用、可测试、失败关闭（默认拒绝超额），且明确标注 node-local 边界；
- 高权限账号的首个 Passkey enrollment 必须经过受控审批，普通账号可按配置选择；
- 不改变条件 MFA、恢复与现有授权语义，只在登录/enrollment 路径前置节流与门禁；
- 保持 clean-room：基于 JDK 并发原语与 Spring Security filter，不引入 Bucket4j/Resilience4j。

## 备选方案

### 限速

#### 方案 A：引入 Bucket4j + Redis 共享存储

集群级精确限速，但 ainer-boot 当前无 Redis，且本阶段不新增中间件依赖；拒绝在 foundation 阶段
引入未验证的共享状态。保留为多实例阶段候选。

#### 方案 B：只缩短密码 Token TTL

不解决在线试错，仅缩小窗口，拒绝作为限速替代。

#### 方案 C：node-local 固定窗口限速器（采用）

复刻 `TenantRateLimiter` 的固定窗口 + `ConcurrentHashMap` 思路，做可复用的 per-key 限速器，
按客户端 IP 对登录类端点节流。明确标注 node-local，多实例时每实例独立计数（叠加后总上限更高，
可接受为“减速”目标）。采用。

### 受控首次 enrollment

#### 方案 D：邀请码令牌（client 提交）

安全但 WebAuthn 协议 filter 不携带自定义参数，需要额外端点换取 enrollment session，复杂度高。

#### 方案 E：服务端 enrollment 授权（采用）

管理员/操作员预登记“某 subject 允许登记首枚 Passkey”的授权行；`register()` 在
`require-invite` 模式下校验该授权存在且未消耗。与 WebAuthn 协议流程解耦，服务端单点校验，简单
可审计。采用。

## 决策

### 第一部分：登录限速（node-local）

1. 新增可复用限速器 `AinerRateLimiter`（固定窗口、`ConcurrentHashMap`、`Clock` 注入、per-key），
   提供 `tryAcquire(String key)` 与惰性清理；窗口大小与每窗口上限可配置。明确为 node-local，
   不宣称集群级一致。
2. 新增 `AinerLoginRateLimitFilter`（`OncePerRequestFilter`），锚定在浏览器安全链靠前位置
   （`SecurityContextHolderFilter` 之后、认证 filter 之前），按**客户端 IP** 对配置的路径节流：
   默认覆盖 `/login`（POST）、`/login/webauthn`、`/webauthn/authenticate/options`。
3. 超额返回 **429** `AINER.SECURITY.RATE_LIMITED`，响应带 `Retry-After`（向上取整到窗口剩余秒数），
   不泄露具体上限或计数。IP 取值只用于限速键，不进入业务日志的常规字段。
4. 默认关闭；启用需显式配置 `window`、`max-requests` 与非空路径集合。限速只决定当前请求是否
   继续，不影响后续认证与授权决策。
5. 限速器暴露基础指标：放行、拒绝计数（不含 IP 或用户名）。dashboard/告警仍是后续可观测切片。

### 第二部分：受控首次 Passkey Enrollment

6. 新增 `ainer_passkey_enrollment_grant` 表：`subject_id`（PK，引用 Identity 账号）、`tenant_id`、
   `granted_by`（SERVICE sub）、`incident_reference`、`granted_at`、`consumed_at`（nullable）、
   `status`（`ACTIVE`/`CONSUMED`）。一行表示“该 subject 被授权登记首枚 Passkey”。
7. 新增默认关闭的控制面 `/internal/passkey-enrollment/tenants/{tenantId}/grants`（POST 建立、
   GET 分页、DELETE 撤销），复用 `requireTenantAccess` 与 `actor_type=SERVICE` +
   `passkey.enrollment.manage` scope。建立/撤销写安全操作审计。
8. `AinerJdbcPasskeyCredentialRepository.register()` 在 `require-invite` 模式下：若该 subject 当前
   无 ACTIVE Passkey，必须存在一行 `ACTIVE` 授权；登记成功同事务把该授权置 `CONSUMED`。
   已有 ACTIVE Passkey 的账号登记 replacement 不受影响（不是首登）。
9. 模式开关 `ainer.security.authorization-server.passkey.enrollment.mode`
   ∈ `{optional, require-invite}`，默认 `optional`（向后兼容当前行为）。生产对高权限账号建议
   `require-invite`。
10. enrollment 授权是**服务端单点**校验，不要求 client 在 WebAuthn 请求里携带任何额外参数；
    普通账号的 bootstrap（`optional` 模式）行为不变。

## 后果

### 正面

- `/login` 等登录端点的在线试错被显著减速，恢复码的 per-subject 锁定之外补齐了 IP 维度节流；
- 高权限账号的首枚 Passkey enrollment 必须经过操作员授权，消除“密码被盗即抢先 enrollment”；
- 限速器与 enrollment 授权都是 node-local/同库，不引入新中间件，可在本阶段完整测试。

### 负面与风险

- node-local 限速在多实例下每实例独立计数，总上限约为 `单实例上限 × 实例数`；它是“减速”而非
  “硬上限”，多实例阶段需替换为共享存储（届时新立 ADR）；
- IP 维度对 NAT/代理后端可能误伤共享出口的真实用户，需结合 `X-Forwarded-For` 受信代理配置
  （本切片只取 `request.getRemoteAddr()`，受信代理解析留待部署配置）；
- enrollment 授权是 admin 控制面，本身需要 IAM 职责分离（request/approve scope 授予不同 Client），
  本切片只提供 `manage` 单 scope 的建立/撤销，未做授权建立的双人审批（可后续叠加）；
- `require-invite` 模式下，未被授权的账号完全无法登记首枚 Passkey，运维必须先建授权再引导用户。

## 安全、数据与隐私

限速键只取客户端 IP，不与用户名或 subject 关联存储；计数与拒绝指标不含 IP、用户名或 Token。
429 响应统一错误码，不区分“不存在账号”与“密码错误”。enrollment 授权表只保存稳定 subject、
tenant、granted_by、incident_reference、时间与状态，不含密码、私钥或恢复码。`incident_reference`
同样受 `^[A-Za-z0-9._:@/-]{1,128}$` 约束。

## 运维与迁移

发布顺序：

1. 发布限速器、enrollment 授权表与控制面代码，保持 `passkey.rate-limit.enabled=false`、
   `passkey.enrollment.mode=optional`；
2. 在受控初始化窗口建立 `passkey.enrollment.manage` operator client（复用既有 operator bootstrap
   范式），加入白名单与 `allowed-scopes`；
3. 决定登录限速参数与是否启用；对高权限账号把 `enrollment.mode` 设为 `require-invite` 并预先建立
   授权行；
4. 启用后监控：限速放行/拒绝、429 计数、enrollment 授权建立/消耗/撤销、`require-invite` 下被
   拒的首登尝试。

回滚优先关闭两个开关（`rate-limit.enabled=false`、`enrollment.mode=optional`），保留新表与审计，
不删除已形成的授权或审计记录。已执行 migration 不得修改。

## 验收证据

2026-07-25 已完成（全 Reactor `mvn test` 通过，PostgreSQL Testcontainers 实际执行，0 跳过）：

- 限速器单元测试覆盖：窗口内放行、超额拒绝、不同 key 独立计数、跨窗口复位、`Retry-After` 向上取整、
  非法配置拒绝；node-local 边界已在类与 ADR 中明确标注；
- 受控首次 enrollment 在真实 PostgreSQL 上验证：`require-invite` 模式下无授权的首枚 Passkey 登记
  被拒（`ENROLLMENT_GRANT_REQUIRED`），操作员建立授权后首登成功且授权同事务置 `CONSUMED`，
  已有 ACTIVE Passkey 的 replacement 登记不受影响；enrollment 授权建立/撤销写安全操作审计；
- 真实 HTTP 限速测试在自定义 context path 下验证 POST 首次放行、超额 429、标准 Ainer 错误
  envelope、`Retry-After`/`no-store`、GET 不受影响和 allow/deny Micrometer counter；
- enrollment grant 与恢复控制面共用 tenant-subject guard，跨 tenant 或非 ACTIVE 目标不会生成授权；
- Flyway 从空库执行十份 Authorization Server migration，含 enrollment 授权表与安全操作审计
  CHECK 扩展（`ENROLLMENT_GRANT`/`GRANTED`/`REVOKED`）。

尚未完成：

- enrollment 控制器层 SERVICE/scope 拒绝路径的 HTTP 级自动化测试；
- 受信代理 `X-Forwarded-For` 解析、多节点共享存储限速、授权建立的双人审批。

## 参考

- [ADR-0014：Passkey 优先的人员认证与条件 MFA 基线](0014-passkey-first-human-authentication.md)
- [ADR-0015：Passkey 恢复（恢复码 + 管理员双人恢复）](0015-passkey-recovery.md)
- [OWASP：Authentication Cheat Sheet — Rate Limiting](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [NIST SP 800-63B-4：Rate Limiting (Throttling)](https://pages.nist.gov/800-63-4/sp800-63b.html)
