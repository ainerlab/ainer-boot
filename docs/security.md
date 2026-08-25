# Ainer Identity 与 OAuth 2.1 使用基线

> 适用版本：Greenfield S8（canonical Workspace/Identity）· 核对 2026-08-25

## 1. 已落地边界

Ainer 使用两个独立运行时：

- `ainer-server`：OAuth 2.0 Resource Server，验证 Bearer JWT 的签名、issuer、有效期和 audience；
- `ainer-authorization-server`：基于 Spring Security 7.1 Authorization Server 的 OAuth 2.1 / OIDC 签发服务。

业务模块只依赖 typed `AuthenticatedPrincipal`。`token_profile`（`SERVICE_V1` / `USER_NEUTRAL_V1`，
`claim_contract_version=1`）决定主体类型：`USER_NEUTRAL_V1` 的 `sub` 是 HumanAccount ID，
`SERVICE_V1` 的 `sub` 是 ServicePrincipal ID，`sec_epoch` 是可选撤销基线。scope 按 Spring
Security 规则成为 `SCOPE_*` authority。AI API 要求 `SCOPE_ai.invoke`；Workspace 读取和写入
分别要求 `SCOPE_workspace.read`、`SCOPE_workspace.write`，并继续检查数据库资源角色（ACTIVE
membership）。外部传入的 `X-Ainer-Tenant-Id`、`X-Ainer-Subject-Id` 不参与身份解析。

Client Credentials access token 使用 `SERVICE_V1`，人员 access token 使用 `USER_NEUTRAL_V1`。
内部 Directory 与 Passkey/Workspace 恢复端点不仅检查 scope，还强制 `SERVICE_V1`，防止人员
Token 因误授 scope 进入服务控制面。

Identity PostgreSQL 模型包含 HumanAccount、LoginIdentity、Credential 与 ServicePrincipal。
OAuth registered client、authorization 与 consent 使用 Spring Security 官方 JDBC repository
和独立协议表；Ainer 不创建自研 Token 表。

## 2. Resource Server

`ainer-server` 默认开启安全。至少提供：

```bash
export AINER_SECURITY_ISSUER_URI=https://auth.example.com
export AINER_SECURITY_AUDIENCES=ainer-api
```

issuer 可以是 Ainer Authorization Server，也可以是兼容 OIDC 的企业身份源。启用安全但没有可用 `JwtDecoder` 时应用必须启动失败。只有隔离的本机开发/自动化测试才可显式设置：

```bash
export AINER_SECURITY_RESOURCE_SERVER_ENABLED=false
```

关闭 Ainer Resource Server 后，starter 会提供明确的 permit-all 链，避免 Spring Boot 因 Security 位于 classpath 而生成随机密码和 Basic Login。生产发行配置不得关闭；依赖 `AuthenticatedPrincipal` 的 Workspace/AI 能力也不得借此获得匿名回退身份。

AI 请求示例：

```bash
curl -i -X POST http://127.0.0.1:8080/api/ai/chat/completions \
  -H "Authorization: Bearer ${AINER_ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"USER","content":"介绍 Ainer"}]}'
```

Token 缺失、未知 profile/actor 组合或解析失败返回 401；Token 已验证但缺少所需 scope 返回 403。两类响应都使用 Ainer `ApiResponse` 并携带 request ID。

### 2.1 高风险请求在线校验

M4.3 保留 JWT 本地签名、issuer、audience 和时间校验，并在认证成功后对高风险请求追加 RFC 7662 introspection。默认规则是：

- 所有 `/internal/**`；
- Workspace 授权审计读取；
- `/api/workspaces/**` 与 `/api/ai/**` 的 `POST`、`PUT`、`PATCH`、`DELETE`。

普通读取继续只做本地 JWT 校验。在线校验默认关闭，避免本地/CI 依赖 introspection。
**对外生产签发前必须启用**（上线顺序见 [`operations.md`](operations.md) 2.3）。启用示例：

```bash
export AINER_SECURITY_ONLINE_VALIDATION_ENABLED=true
export AINER_SECURITY_ONLINE_VALIDATION_INTROSPECTION_URI=https://auth.example.com/oauth2/introspect
export AINER_SECURITY_ONLINE_VALIDATION_CLIENT_ID=ainer-resource-introspection
export AINER_SECURITY_ONLINE_VALIDATION_CLIENT_SECRET='use-secret-injection'
```

每次匹配请求都在线查询，不缓存 `active=true`。inactive 统一返回 401，不暴露不存在、过期或撤销原因；连接超时、凭据错误或响应不可解析返回 503 `AINER.SECURITY.ONLINE_VALIDATION_UNAVAILABLE`，不得退回仅凭 JWT 放行。在线结果只决定当前请求是否继续，业务层仍使用原 JWT 解析出的 `AuthenticatedPrincipal`。

生产 introspection URI 必须 HTTPS；HTTP 例外只允许显式开启后的 loopback 测试。路径、方法、连接与读取超时可配置，完整键见 [`configuration.md`](configuration.md)，上线顺序与回滚约束见 [`operations.md`](operations.md)。完整决策见 [ADR-0011](decisions/0011-selective-online-token-validation.md)。

## 3. Workspace 资源授权

Workspace 使用“能力 scope + 资源关系”双层授权：

| 操作 | 必需 scope | 资源角色 |
|---|---|---|
| 创建 Workspace | `workspace.write` | 创建者自动成为 OWNER |
| 查询详情、分页 | `workspace.read` | OWNER / ADMIN / MEMBER |
| 重命名、邀请、角色变更、移除 | `workspace.write` | OWNER / ADMIN |
| 接受本人邀请 | `workspace.read` | `sub` 等于受邀主体 |
| 转移所有权 | `workspace.write` | 当前 OWNER，目标必须是 ACTIVE 成员 |

HTTP 创建请求只有 `name`，创建者由 `USER_NEUTRAL_V1` 的 `sub`（HumanAccount）产生。通用成员接口
只能邀请 ADMIN 或 MEMBER，邀请初始为 `PENDING`，不产生任何资源访问权。受邀主体必须使用 `sub`
与邀请目标一致的已验证 token 接受，才会变为 `ACTIVE`。这复用可信 Identity Provider 的主体证明，
同时避免 Workspace 读取 Identity 私有表。SERVICE_V1 principal 不能进入 Human membership。

角色变更只能在 ADMIN/MEMBER 之间进行；移除非 OWNER 成员会立即撤销后续访问。所有权只能由当前
OWNER 通过专用事务转移：事务先锁定 Workspace，把旧 OWNER 降为 ADMIN，再提升一个 ACTIVE 成员；
部分唯一索引保证最多一个 ACTIVE OWNER。跨 Workspace 或非 ACTIVE 成员访问返回 404，已经是
ACTIVE 成员但角色不足返回 403。

创建、改名、邀请、接受、角色变化、移除、所有权转移的允许决策，以及资源授权拒绝，都会记录 actor、target、Workspace、action、decision、稳定 reason code 和时间。审计使用独立事务且不外键关联 Workspace；受保护写操作不能在审计失败时继续。普通成功读取不逐条审计，避免在当前阶段制造无界访问日志。

审计查询使用 `workspace.audit.read` scope，并继续要求查询者是目标 Workspace 的 ACTIVE OWNER/ADMIN。查询 SQL 绑定 workspace，按时间和 UUID 稳定倒序分页；成功或拒绝读取审计的决策也进入审计。M4.2 的后台任务可将超过热保留期的记录在同一业务库内原子迁移到归档表，普通查询仍统一读取热表与归档表。默认关闭的 SIEM 拉取端点使用 `(occurredAt, id)` 游标，并为每批导出追加安全操作审计。

每个 Workspace SQL 都显式绑定 `workspace_id`；成员分页同时绑定 subject 和 `ACTIVE` 状态。完整设计见 [ADR-0006](decisions/0006-workspace-tenant-authorization-baseline.md) 与 [ADR-0007](decisions/0007-workspace-membership-lifecycle-and-audit.md)，去 tenant 化见 Greenfield S6。PostgreSQL RLS 仍是未来纵深防御选项，当前不能把尚未验证的连接池租户会话当作安全边界。

### 3.1 通用授权管理防提权

`authorization.manage` 只是 OAuth 能力上限，不能单独证明“谁能授权谁”。通用授权模块要求宿主以
唯一的版本化 `GrantAdministrationPolicy` bean 精确登记可信 SERVICE `issuer + sub`，并分别计算
可授予 Permission、Scope 与目标主体集合；这些集合不得从管理者 Effective Access 自动推导。
未登记策略时，所有 `/api/authorization/**` 管理访问默认拒绝。

应用服务事务边界会再次执行同一 guard，因此绕过 HTTP Controller 直接调用 Role/Binding 写服务也
不能跳过校验。通用管理路径硬性拒绝 system-only Permission、GLOBAL Binding、自授予/自改，以及
策略目录外的权限、范围和目标；初始产品 owner/operator 等业务使用权必须由验证真实业务关系的
onboarding/ownership 流程建立并独立审计。Greenfield 已移除 Tenant，所以 ADR-0030 原 tenant OWNER
bootstrap 文本不能直接复活；当前 post-Greenfield 基线以 ADR-0037 为准。

### 3.2 `@AinerAuthorize` 端点粗粒度门禁

宿主装配 `AuthorizationModuleConfiguration`、Servlet Web 与 `AuthenticatedPrincipalResolver` 后，会
注册 `AinerRequestAuthorizationManager` 和 MVC `AinerAuthorizeInterceptor`。解析器可以来自
security starter 自动装配；适配器在 bean 创建时解析，避免用户 `@Configuration` 阶段
`@ConditionalOnBean` 误判导致注解空操作。Spring Security filter
chain 先完成 JWT 认证；MVC 解析出 handler 后，拦截器读取方法上的
`@AinerAuthorize(permission=...)`，在 controller 执行前调用 manager。DENY、CHALLENGE、未知策略和
未执行 obligation 均失败关闭为统一 403，不把 decisionId 或内部 reason code 暴露给客户端；缺少
Bearer Token 仍由 Resource Server 在更早阶段返回 401。

首版注解只适用于低/中风险、`resourceType=request`、无 obligation 的 HTTP 门禁。参考装配会把
`/api/workspaces/{id}` 路径变量写入 `ResourceRef.workspaceId`，使 WORKSPACE Binding 必须对上该
工作区；创建/列表等无路径 id 的端点仍回退「持有该权限」粗闸门。它不把请求体解析为资源，
也不执行 FieldMask/RecheckBefore；高价值写、资源 ownership 和数据库查询范围必须继续在应用
服务中显式调用授权端口。Handler 注解只能在 MVC 阶段取得，禁止把 request attribute 假设成能被
更早执行的 `AuthorizationFilter` 读取。

参考装配（`ainer-server`）已给 Workspace 读写/审计、文件读写、配置读写（含 secret 写入）、
`ai.invoke` 与 `ai.agents.manage` 接线 `@AinerAuthorize`，并注册 Workspace 路径
`AuthorizationTargetResolver`。**不是** 1.x 资源级授权合同。网关仅在请求带 `actingAgentId`
时调用 `ActingGrant.check`（缺 `workspaceId` 返回 422，拒绝不泄露 reason）。其余 P3
Controller、把 permission 改成类型化 resourceType、方法级 AOP 与 obligation executor 仍留给
后续。

`PUBLIC_PROJECTION` 不会自行把路径加入 Resource Server 的 `public-paths`。即使宿主同时开放路径并
注册 `PublicAccessPolicy`，当前 public ALLOW 仍携带 projection obligation，而 0.1 adapter 尚未执行
该 obligation，因此会失败关闭为 403；在 `DecisionObligationExecutor` 交付前不得宣称支持匿名投影。

## 4. Authorization Server

Authorization Server 使用独立 PostgreSQL 数据库或独立 schema 所属权。启动前配置 datasource、HTTPS issuer 与 RSA 签名密钥：

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ainer_auth
export SPRING_DATASOURCE_USERNAME=ainer_auth
export SPRING_DATASOURCE_PASSWORD='use-secret-injection'
export AINER_AUTHORIZATION_SERVER_ISSUER=https://auth.example.com
export AINER_AUTHORIZATION_SERVER_AUDIENCE=ainer-api
export AINER_AUTHORIZATION_SIGNING_KEY_ID=ainer-signing-2026-01
export AINER_AUTHORIZATION_PRIVATE_KEY_LOCATION=file:/run/secrets/ainer-private.pem
export AINER_AUTHORIZATION_PUBLIC_KEY_LOCATION=file:/run/secrets/ainer-public.pem

./mvnw -pl ainer-authorization-server -am spring-boot:run
```

私钥必须是 PKCS#8 PEM，公钥必须是 X.509 SubjectPublicKeyInfo PEM。开发环境可生成：

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out ainer-private.pem
openssl pkey -in ainer-private.pem -pubout -out ainer-public.pem
```

密钥文件不得提交。生产环境应由 secret manager 挂载只读文件，并建立双 key 发布、旧 Token 验证窗口和定期轮换流程；当前最小装配加载一对 RSA key，不等同于完整 KMS 生命周期。

Flyway 会创建 Identity 表和 Spring Security JDBC 协议表。应用没有默认管理员、默认用户、默认 client 或默认 secret，空库启动后不会凭空获得可用凭证。

## 5. 显式 Client 引导

### 5.1 业务机器 Client

为了让全新环境完成第一条 Client Credentials 链路，提供一次性、默认关闭的机器 client 引导：

```bash
export AINER_AUTHORIZATION_BOOTSTRAP_MACHINE_ENABLED=true
export AINER_AUTHORIZATION_BOOTSTRAP_MACHINE_CLIENT_ID=ainer-local-agent
export AINER_AUTHORIZATION_BOOTSTRAP_MACHINE_CLIENT_SECRET='at-least-24-characters-secret'
export AINER_AUTHORIZATION_BOOTSTRAP_MACHINE_SCOPES=ai.invoke,workspace.read,workspace.write
```

首次启动会用 delegating password encoder 保存 secret 哈希，并创建仅支持 `client_credentials` 的 client；同一 `client_id` 再次启动不会覆盖或轮换 secret。成功后立即从运行环境移除 bootstrap 开关和明文 secret。正式控制台落地后，应通过有审计的 client 管理用例替代一次性引导。

获取 Token：

```bash
curl -u 'ainer-local-agent:at-least-24-characters-secret' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials&scope=ai.invoke' \
  https://auth.example.com/oauth2/token
```

### 5.2 在线校验专用 Client

Resource Server 必须使用独立 introspection client。它不绑定 tenant、不携带业务 scope，只拥有 `token.introspect`，并在 registered client settings 中显式标记 `ainer.introspection-allowed=true`。普通机器 client 即使拥有有效 secret 也会从 `/oauth2/introspect` 得到协议级 401 `invalid_client`。

受控初始化窗口可以启用独立 bootstrap：

```bash
export AINER_AUTHORIZATION_BOOTSTRAP_INTROSPECTION_ENABLED=true
export AINER_AUTHORIZATION_BOOTSTRAP_INTROSPECTION_CLIENT_ID=ainer-resource-introspection
export AINER_AUTHORIZATION_BOOTSTRAP_INTROSPECTION_CLIENT_SECRET='at-least-24-characters-secret'
```

初始化完成后立即移除开关和明文 secret。不得用普通 machine bootstrap 建立该 client，也不得给它增加 tenant 或业务 scope。RFC 7009 `/oauth2/revoke` 继续使用 Spring Authorization Server 官方授权元数据；M4.3 没有创建自研 Token 表。

### 5.3 指标抓取专用 Client

两个发行物的 `/actuator/prometheus` 都是服务控制面，不是匿名健康端点。Token 必须满足
`SERVICE_V1`、`actor_type=SERVICE`，并且只有所需的 `platform.metrics.read` scope；人员、
业务服务、introspection client 和普通业务 client 都不能读取指标。

受控初始化窗口可以建立独立 metrics client：

```bash
export AINER_AUTHORIZATION_BOOTSTRAP_METRICS_ENABLED=true
export AINER_AUTHORIZATION_BOOTSTRAP_METRICS_CLIENT_ID=ainer-prometheus
export AINER_AUTHORIZATION_BOOTSTRAP_METRICS_CLIENT_SECRET='at-least-24-characters-secret'
```

它只支持 Client Credentials，access token TTL 为 1 分钟，也没有 introspection 标记。创建完成后立即移除开关和明文 secret。Prometheus 应使用 OAuth2 配置从 secret file 获取 client secret，不能保存长期静态 Bearer Token。完整边界与尚未完成的 HA/轮换验证见 [ADR-0012](decisions/0012-production-observability-and-auth-availability.md)。

### 5.4 受审计 browser client 控制面

生产 browser client 可以使用默认关闭的内部控制面创建，不再通过环境变量提交调用方选择的
client 策略。启用前必须先在受控初始化窗口建立专用、无 tenant 的 operator client；它只持有
`oauth.browser-clients.manage`，并把该 client ID 加入精确白名单：

```bash
export AINER_AUTHORIZATION_BOOTSTRAP_BROWSER_CLIENT_CONTROL_OPERATOR_ENABLED=true
export AINER_AUTHORIZATION_BOOTSTRAP_BROWSER_CLIENT_CONTROL_OPERATOR_CLIENT_ID=ainer-browser-client-operator
export AINER_AUTHORIZATION_BOOTSTRAP_BROWSER_CLIENT_CONTROL_OPERATOR_CLIENT_SECRET='at-least-24-characters-secret'

export AINER_AUTHORIZATION_BROWSER_CLIENT_CONTROL_ENABLED=true
export AINER_AUTHORIZATION_BROWSER_CLIENT_CONTROL_OPERATOR_CLIENT_IDS=ainer-browser-client-operator
export AINER_AUTHORIZATION_BROWSER_CLIENT_CONTROL_ALLOWED_SCOPES=openid,profile,workspace.read,workspace.write
```

首次启动成功后立即移除 operator bootstrap 环境变量和明文 secret；保留控制面开关与
operator ID 白名单。operator bootstrap 重复运行不会覆盖已有 secret。

控制面同时要求已验证的 `SERVICE_V1`/`actor_type=SERVICE`、`oauth.browser-clients.manage` scope
和精确 operator ID；人员 Token、tenant-bound 服务或仅有 scope 但不在白名单的服务均被拒绝。
operator client 不得兼任业务、introspection、metrics 或运维审批 client。

创建只接受 public Authorization Code + PKCE client：client ID/name、精确 redirect URI（同源
pair）、白名单内 scopes 和受限 `changeReference`；不注册 client secret、不启用 Refresh Token。
生产 redirect URI 必须 HTTPS，HTTP 只允许 loopback。数据库保存生命周期投影（无 secret），
`CREATED`/`ROTATED`/`RETIRED` 审计同事务写入，不物理删除历史 registered client。

轮换使用新 client ID：创建 replacement、更新前端配置并验证，再显式退役旧 client。退役会立即
阻止旧 client 获取新 Token，并让它的历史 Token 对 RFC 7662 introspection 显示 inactive；
只做离线 JWT 验证的低风险 API 仍可能在默认 5 分钟短 TTL 内接受既有 Token。

该控制面刻意不管理业务机器 client、metrics、introspection、operator、`.all` 跨租户 scope 或
既有 bootstrap client。后几类仍需要独立初始化和未来专门控制面，不能把本切片描述成"所有 OAuth
Client 已纳管"。完整决策见 [ADR-0013](decisions/0013-audited-oauth-service-client-lifecycle.md)。

## 6. 浏览器与人员身份

协议装配支持 Authorization Code、PKCE、Refresh Token、Client Credentials 与 OIDC；实际可用授权类型仍由 registered client 白名单决定。禁止配置 password grant。

M4.5 已用测试专用 public client 完成真实 HTTP 浏览器会话与 PostgreSQL 端到端门禁：

- client 使用 `ClientAuthenticationMethod.NONE`、Authorization Code、精确 redirect URI 和
  `requireProofKey=true`；
- 只接受 S256，缺失 challenge、`plain`、错误 verifier 和未注册 redirect URI 均被拒绝；
- 表单登录包含 cookie/CSRF，会签发带稳定 `sub`（HumanAccount UUID）、`token_profile=USER_NEUTRAL_V1`、`claim_contract_version=1` 与 `roles` 的 access token 与 OIDC
  ID token；authorization code 只能交换一次；
- public client 不注册 Refresh Token grant，响应不得出现 refresh token。需要长期浏览器会话时应
  先明确 BFF、会话与轮换策略，不能给 SPA 注入 client secret。

官方 JDBC authorization 的 JsonMapper 使用隔离配置，只在 Spring Security 自带白名单之外精确
允许 `AinerUserDetails`，没有放宽全局包名或 `Object` 子类型。人员主体实现
`CredentialsContainer`，认证成功后擦除 password hash；持久化 mixin 忽略 password 属性，集成
测试直接检查授权记录不含密码或 password 字段。

这些自动化验证不创建生产默认 browser client，也不提供注册/轮换 API、同意页、租户选择、会话治理或
完整 MFA。测试 issuer、测试 client 和测试 RSA key 不能用于发行环境。

M6 使用 Ainer Studio `a73f40b` 的视觉合同 1.0.0 提供纯服务端品牌登录页。它保持
`GET/POST /login`、服务端 CSRF、Spring Security SavedRequest 与 MFA filter 语义；用户名不存在
和密码错误统一显示同一消息。HTML 登录限速保留 429、`Retry-After` 与
`AINER.COMMON.RATE_LIMITED` 的对应关系，JSON/WebAuthn 端点仍使用原错误合同；只有明确的
`AuthenticationServiceException` 才产生一次性 503 视图，普通失败不能伪装为服务不可用。
页面不回填用户名或密码，所有 HTML、失败重定向和错误响应都使用 `no-store`。

视觉合同 1.0.0 明确禁止显示 Passkey 动作，因此本次实现只保证协议与条件 MFA 兼容，不声称提供
人员可操作的 Passkey 登录 UI。需要启用人员 Passkey 的部署必须先取得 Studio 新版视觉/交互合同；
不能在 Boot 中私自添加按钮或脚本。

Ainer Admin 另提供只在 `dev` profile、显式开关下初始化的固定 public client
`ainer-admin-dev`。它注册 `/ainer-admin/auth/callback` 与
`/ainer-admin/auth/logged-out` 两个同源精确 URI，只允许 Authorization Code + PKCE S256，
scopes 固定为 `openid profile workspace.read workspace.write`，不注册 Refresh Token。
它用于官方参考管理应用联调，不是生产 browser client 控制面；完整边界见
[ADR-0022](decisions/0022-ainer-admin-browser-integration-baseline.md)。

同一 `dev` profile 还可以显式启用 Admin fixture。它通过 Identity 的严格幂等 bootstrap 创建
`ainer-admin-dev` 的 default OWNER，以及一个独立 HumanAccount。第二个账户不带任何 Workspace
access，可用于验证独立登录、撤销互不影响以及非成员访问被拒绝；fixture 不提供默认
用户名或密码，配置缺失、用户名相同、部分占用或状态漂移均启动失败。

M4.6 增加默认关闭的 Passkey/WebAuthn 协议基础。启用时：

- RP ID 必须是小写 DNS 名或 `localhost`，Origin host 必须等于 RP ID 或位于其子域；生产只接受
  HTTPS，显式 HTTP 例外只允许 `localhost` 自动化测试；
- registration 与 authentication options 都强制 `userVerification=required`，使用 resident
  credential；私钥和本地生物识别模板不进入 Ainer；
- 无 ACTIVE Passkey 的账号可用密码完成首次 bootstrap；一旦存在 ACTIVE Passkey，
  `/oauth2/authorize` 和 `/webauthn/register/**` 凭证管理必须完成 WebAuthn 因子。由于 Spring
  Security WebAuthn 协议 filter 在授权 filter 之前短路，凭证管理端点由专用
  `AinerPasskeyCredentialManagementGateFilter` 在协议 filter 之前显式运行条件 MFA
  `AuthorizationManager`，缺因子时重定向到登录入口；
- `/webauthn/register/options`、`/webauthn/register`、
  `/webauthn/authenticate/options` 与 `/login/webauthn` 使用 Spring Security 标准协议响应，
  不套 Ainer JSON envelope；
- 协议凭证保存在 Spring 官方 JDBC 表，Ainer 生命周期表只补充稳定 subject、ACTIVE/REVOKED、
  时间和审计。删除是软撤销；最后一个 ACTIVE Passkey 不允许自助删除，轮换必须先登记
  replacement；
- 密码人员 Token 写标准 `amr=pwd`；完成 UV-required WebAuthn 后实际写 IANA 已登记的
  `mfa,pop`，并以最新因子时间写 `auth_time`。Passkey 用户走授权码流程后，Token 正确携带
  稳定 `sub`/`token_profile`/`roles`（customizer 按 subject 解析 WebAuthn principal）。

真实签名 ceremony 已用 webauthn4j 虚拟 authenticator 在自动化测试中端到端跑通（attestation
+ assertion 闭环、`amr=pwd,mfa,pop` 与凭证管理门禁均在 HTTP 层验证）。恢复码、管理员双人恢复、
`require-invite` 首次 enrollment、登录 POST 限速和 Resource Server step-up 也已落地并默认关闭。
step-up 与在线校验一样：**生产签发前必须启用**，默认保护 Workspace 所有权转移。
恢复与 enrollment 的目标必须是对应 ACTIVE 的 HumanAccount；数据库
复合唯一约束再防止孤立或跨 account 密钥记录。登录限流对 JSON/API 使用标准 Ainer 429 envelope，
对明确接受 HTML 的 `POST /login` 使用同一品牌页面，并统一保留 `Retry-After`、`no-store`；
它只匹配配置的 POST 路径，且明确是 node-local。step-up 只处理 USER token，校验必需 `amr`、
`auth_time` 最大年龄、未来时间和可配时钟偏差；匿名仍返回 401。

当前仍未覆盖主流真实设备/浏览器兼容矩阵、恢复通知和多节点 session/共享限流验证。TOTP 只保留为
后续受限恢复 fallback 的候选，不能作为抗钓鱼主因子。完整决策和威胁模型见 ADR-0014 至 ADR-0017。

人员账号由 `ainer-module-identity` 保存 delegating password hash 与状态；Authorization Server
的 `UserDetailsService` 从该端口加载账号，签发时把稳定 HumanAccount UUID 写入 `sub`，把已授角色
写入 `roles`。没有默认租户 claim；Workspace 授权只依赖 `sub` 与成员关系。

`POST /api/me/access-token-revocations` 为 USER bearer 提供当前 access token 的窄自助撤销。
端点不接收任意 token 参数，不要求 public browser client 伪造 client authentication，也不扩大
RFC 7009 的 client 授权边界。撤销直接失效 Spring Authorization Server 官方 JDBC
authorization 中的当前 access token；不存在、过期或已撤销统一按 401 处理。

账号禁用会阻止后续人员 token 签发：`IdentityFoundationService` 的账号禁用与非 OWNER
membership revoke 与审计同事务提交。签发 token 时 customizer 把当前 `securityEpoch` 写入
`sec_epoch` claim；`RevocationAwareOAuth2AuthorizationService` 在查找人员 authorization 时
用 `sec_epoch` claim 与 Identity 当前 epoch 比对，不等即视为 inactive。账号禁用/密码轮换
递增 epoch 后，事件前签发的 Token 全部失效，无需订阅撤销事件或维持 access-event outbox。

当前仍未提供公网注册、找回密码、恢复通知、预配激活、租户切换和图形化 client 控制台；Passkey
协议/条件门禁、恢复、受控 enrollment、step-up 与 browser client 控制面已落地。除通用测试 client 外，`dev` profile 已提供固定的
`ainer-admin-dev` public client；它不是生产 browser client 控制面。

选择性在线校验只覆盖配置的高风险请求，普通低风险 JWT API 仍存在自然到期窗口，因此
不能宣称所有 API 都已强实时全局撤销。

## 7. 安全运维控制面

Identity 耗尽事件重放与 Workspace OWNER 恢复是默认关闭的高风险能力。两者都采用短时两阶段流程：一个 SERVICE JWT 主体持有 request scope 建立申请，另一个 `sub` 不同的 SERVICE JWT 主体持有 approve scope 审批并在同一事务内执行。默认有效期为 15 分钟；申请与审批端点额外要求精确的 trusted publisher/service `sub` 与 scope，不允许普通人员 Token 进入。

必须把 request 与 approve scope 授予不同的 Client Credentials client，并由不同责任人保管凭据。代码中的 `sub` 不同检查只是技术底线，不会自动建立组织上的职责分离。`incidentReference` 只接受受限安全标识，不得填写 Token、密码、客户正文或自由文本故障细节。

Identity 只重置仍属于 `PENDING`/`FAILED`、无有效 lease 且已达最大尝试数的原事件。原 event ID、subject、类型、版本和发生时间保持不变，仅清理 lease/错误并恢复为可领取状态；因此下游 receipt 幂等语义仍有效。

OWNER 恢复只在 Workspace 无 ACTIVE OWNER、至少有一个 REVOKED OWNER，且目标是该 Workspace 的 ACTIVE 非 OWNER 成员时可执行。审批事务会锁定 Workspace 并重新检查条件；旧的 REVOKED OWNER 保持 REVOKED，不会因恢复流程重新获得访问权。

申请和成功执行阶段写入模块所属数据库的安全操作审计。当前的同库归档不是 WORM、数字签名或法律意义的不可抵赖；生产发行仍需要独立权限域、对象锁或等价不可变副本。完整决策见 [ADR-0010](decisions/0010-security-operations-and-audit-lifecycle.md)。

## 8. 验证

```bash
./mvnw -pl ainer-framework/ainer-starter-security -am test
./mvnw -pl ainer-authorization-server -am test
./mvnw -pl ainer-module-identity -am test
./mvnw -pl ainer-module-workspace -am test
./mvnw clean verify
```

Resource Server 的 401/403、可信 claim、伪造身份头以及 Workspace 应用授权测试不依赖 Docker。Identity、JDBC 协议表、Client Credentials 签发与 Workspace 资源 SQL 测试使用 PostgreSQL Testcontainers；没有 Docker 时会明确跳过，不会改用 H2。

M4.3 还要求验证在线校验、专用 client 与普通 client 隔离、RFC 7009 撤销，以及 Identity submission 时间等于/前后边界。指标与 SERVICE 控制面还需验证无 Token 401、USER/missing-scope 403、专用 SERVICE 200，以及业务 Resource Server 关闭时仍不匿名公开。browser client 控制面还需验证一次性、白名单、operator/tenant 隔离、蓝绿轮换、退役后新 Token 401、历史 Token introspection inactive 和无 secret 审计。PKCE 门禁必须使用真实 HTTP 会话和 PostgreSQL，覆盖 S256 正反路径、登录 CSRF、
授权码重放、redirect URI、人员 claims、无 refresh token 以及协议记录不落凭证。Passkey 基线还
必须覆盖配置失败关闭、UV-required options、无凭证 bootstrap、已登记账号条件拦截、生命周期/
审计同事务、软撤销、replacement、最后凭证保护、恢复/enrollment subject 绑定、登录
429 和 step-up 的 200/401/403。账号禁用的同事务事件、在线 epoch、与 Workspace 事件终点都要以真实 PostgreSQL
覆盖。真实 PostgreSQL 和协议 smoke 结果维护在
[`project-status.md`](project-status.md)。

Ainer Admin 还必须以同一个 browser cookie session 覆盖 PKCE、Workspace 操作、当前 access token
撤销和 OIDC logout；完整同源集成与验收命令见
[`ainer-admin-integration.md`](ainer-admin-integration.md)。
