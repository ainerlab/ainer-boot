# ADR-0017：Resource Server Step-up 授权策略

- 状态：Accepted
- 日期：2026-07-25
- 决策者：待指定
- 取代：无
- 被取代：无

## 背景

ADR-0014/0015/0016 已让 Authorization Server 在 Passkey 用户完成 WebAuthn 后，于 access token
写入标准 `amr`（含 `mfa`、`pop`）与 `auth_time`（Phase A 真实 ceremony 已端到端验证）。ADR-0014
明确这两项是“为后续资源服务器 step-up policy 提供标准输入”。

但 `ainer-starter-security`（Resource Server）目前只校验 JWT 签名、issuer、audience、过期与
（可选的）在线存活状态，**从不读取 `amr` 或 `auth_time`**。因此一个高风险业务操作（修改授权、
导出安全日志、执行恢复、变更付费设置等）无法要求“近期强认证”，任何未过期的合法 Token 都能放行，
即使它的最近一次认证因子只是密码、或是一次很久之前的 Passkey 登录。

当前事实：starter 里有且仅有一个自定义 `AuthorizationManager`
（`TenantlessServiceScopeAuthorizationManager`，用于 metrics 端点），其余授权靠 scope authority
与资源成员关系。在线校验（ADR-0011）用 `OnlineAccessTokenValidationFilter`（filter 模式）实现，
能在失败时通过 `AinerSecurityFailureWriter` 返回**特定错误码**。

## 决策驱动因素

- 让 `amr`/`auth_time` 真正参与授权决策，使高风险操作可要求近期强认证因子；
- 复用 starter 已有的 `AuthorizationManager` 与 failure-writer 机制，不引入新依赖；
- 失败关闭、语义清晰、可配置、可测试；不破坏普通低风险 JWT API 与 Client Credentials；
- step-up 是**本地、自包含、可立即落地**的切片；账号通知（见“范围外”）需要 email 基础设施、
  联系字段与投递通道，与部署耦合，属另一切片。

## 备选方案

### 方案 A：缩短 access token TTL

缩小窗口，但不能区分“近期强认证”与“任何合法 Token”，且放大签发负载。保留为纵深防御，不作为
step-up 替代。

### 方案 B：业务 Controller 手工校验 amr

容易遗漏新端点，把 Token claim 泄漏进业务层，拒绝。

### 方案 C：用 AuthorizationManager（`.access(manager)`）

最 Spring-idiomatic，但 `.access()` 的拒绝只能产生通用 `AINER.COMMON.FORBIDDEN`，无法返回
“需要近期强认证”这类可区分错误码。

### 方案 D：filter 模式 + failure-writer（采用）

镜像 `OnlineAccessTokenValidationFilter`：按配置路径/方法选择性触发，从
`JwtAuthenticationToken` 读取 `amr`/`auth_time`，不满足时用 `AinerSecurityFailureWriter` 写出
特定错误码 `AINER.SECURITY.RECENT_STRONG_AUTHENTICATION_REQUIRED`（403）。采用。

## 决策

1. 新增 `RecentStrongAuthenticationFilter`（`OncePerRequestFilter`，位于 `ainer-starter-security`），
   锚定在 `BearerTokenAuthenticationFilter` 之后（与在线校验 filter 同位置），仅在配置的受保护
   请求上触发（`shouldNotFilter` 反向匹配，复用 `OnlineAccessTokenValidationFilter` 的路径/方法
   matcher 构造方式）。
2. 对每个匹配请求，从 `SecurityContextHolder` 的 `JwtAuthenticationToken` 读取：
   - `amr`（`getClaimAsStringList("amr")`）必须包含配置的 `required-amr`（默认含 `mfa`）；
   - `auth_time`（`getClaimAsInstant("auth_time")`）距 now 不超过 `max-auth-age`（默认 15 分钟，
     可配至 1 天）。
3. 满足则放行；任一不满足（缺 amr、缺 auth_time、auth_time 过期、非 JwtAuthenticationToken）
   失败关闭，写 `AINER.SECURITY.RECENT_STRONG_AUTHENTICATION_REQUIRED`（403），不泄露具体缺哪个。
   非 Bearer（未认证）请求不由此 filter 处理（已由前置认证入口返回 401）。
4. 只作用于 `actor_type=USER` 的人员 Token；`SERVICE`（Client Credentials）无 `amr`/`auth_time`，
   被 step-up 匹配的路径本就不应是机器调用（若配置如此，视为配置错误，启动校验告警）。
5. 新增嵌套配置 `ainer.security.resource-server.step-up`：`enabled`（默认关闭）、`max-auth-age`、
   `required-amr`、`always-protected-paths`、`mutating-protected-paths`、`mutating-methods`，
   镜像 `online-validation` 的属性结构与校验（启用时规则不得为空、max-auth-age 合法）。
6. 新增错误码 `RECENT_STRONG_AUTHENTICATION_REQUIRED`（403）于 `AinerSecurityErrorCode`，经
   `ErrorCodeContributor` 自动注册。
7. 指标：放行、拒绝计数（不含 Token、sub 或 amr 正文）。

## 范围外：账号通知

ADR-0014 要求“因子替换必须通知账号所有者”。但 ainer-boot 当前**无 Identity 联系字段（无 email/
phone）、无邮件/短信/推送依赖、无通用通知 outbox**（已核实）。通知没有可达联系字段即等同于
既有安全审计，无独立价值；而引入 email 字段 + outbox + 投递适配器 + 多处触发点是与部署耦合的
大切片。因此账号通知**整体推迟到独立切片（Phase E）**，本 ADR 只交付 step-up。Phase B/C 已在
`project-status.md` 把“可达通知”记为已知缺口，本决策不改变该缺口状态。

## 后果

### 正面

- 高风险 API 现在可以要求近期 Passkey 认证，`amr`/`auth_time` 不再只是被签发而不被消费；
- 复用既有 filter + failure-writer 模式，零新依赖，默认关闭、可灰度；
- step-up 拒绝有可区分错误码，前端可据此引导重新认证。

### 负面与风险

- step-up 路径上的请求增加一次内存 claim 判定（无可观测成本）；
- `max-auth-age` 过紧会频繁触发重新认证、影响可用性；过松等于失效；需按操作风险分级配置；
- step-up 只保护配置的路径，新高风险端点必须同步加入规则，否则不被保护（与在线校验同样的
  运维纪律）；
- step-up 依赖 Token 携带真实 `amr`/`auth_time`；外部 OIDC 发行物若不提供这些 claim，受保护
  路径会对人员 Token 持续失败（部署需确保发行物合规）。

## 安全、数据与隐私

filter 只读取已验证 JWT 的 `amr`/`auth_time` 与请求路径/方法，不写库、不调用外部服务、不把
Token 或 amr 写入日志/指标/错误正文。拒绝响应统一错误码，不区分“缺 amr”与“auth_time 过期”。

## 运维与迁移

发布顺序：

1. 发布 filter、配置与错误码，保持 `step-up.enabled=false`；
2. 在受控环境用真实 Passkey Token（`amr` 含 `mfa,pop`）验证放行、用密码 Token（仅 `pwd`）验证
   拒绝、用旧 `auth_time` 验证过期；
3. 选定高风险路径（如 Workspace 授权审计导出、成员角色变更、Passkey 管理控制面、付费变更）配置
   规则，小范围启用并监控拒绝率与误伤；
4. 按操作风险分级收紧 `max-auth-age`。

回滚关闭 `step-up.enabled` 即可，不删除任何数据或 Token。

## 验收证据

2026-07-25 已完成（全 Reactor `mvn test` 通过，0 跳过）：

- `RecentStrongAuthenticationFilter` 单元测试覆盖：`amr` 含必需因子且 `auth_time` 新鲜 → 放行；
  密码 Token 缺 `mfa` → 403 `AINER.SECURITY.RECENT_STRONG_AUTHENTICATION_REQUIRED`（响应体含错误码）；
  `auth_time` 超过 `max-auth-age` → 拒绝；缺 `amr`/`auth_time` → 拒绝；非 JwtAuthenticationToken → 拒绝；
  非受保护路径不被节流；`StepUp.validate()` 拒绝空规则、空 `required-amr` 与超 24 小时的 `max-auth-age`。
- 默认关闭，错误码经 `ErrorCodeContributor` 自动注册；filter 与在线校验 filter 同锚定在
  `BearerTokenAuthenticationFilter` 之后。

尚未完成：

- step-up filter 的端到端 HTTP 集成测试（stub JwtDecoder + 受保护业务路径 200/403）；
- `max-auth-age` 按操作风险分级的生产配置与拒绝率监控；
- 账号通知（Phase E）。

## 参考

- [ADR-0011：高风险 API 选择性在线 Token 校验](0011-selective-online-token-validation.md)
- [ADR-0014：Passkey 优先的人员认证与条件 MFA 基线](0014-passkey-first-human-authentication.md)
- [RFC 8176：Authentication Method Reference Values](https://www.rfc-editor.org/rfc/rfc8176)
- [OIDC Core：auth_time Claim](https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest)
