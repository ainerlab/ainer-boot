# Ainer Identity 与 OAuth 2.1 使用基线

> 适用版本：M4.7 + Ainer Admin backend integration baseline · 2026-07-26

## 1. 已落地边界

Ainer 使用两个独立运行时：

- `ainer-server`：OAuth 2.0 Resource Server，验证 Bearer JWT 的签名、issuer、有效期和 audience；
- `ainer-authorization-server`：基于 Spring Security 7.1 Authorization Server 的 OAuth 2.1 / OIDC 签发服务。

业务模块只依赖 `AuthenticatedActor`。`sub` 投影为主体，`tenant_id` 投影为当前租户，
`actor_type` 是必需且只允许 `USER|SERVICE`，scope 按 Spring Security 规则成为 `SCOPE_*`
authority。AI API 要求 `SCOPE_ai.invoke`；Workspace 读取和写入分别要求
`SCOPE_workspace.read`、`SCOPE_workspace.write`，并继续检查数据库资源角色。外部传入的
`X-Ainer-Tenant-Id`、`X-Ainer-Subject-Id` 不参与身份解析。

Client Credentials access token 额外携带 `actor_type=SERVICE`，人员 access token 携带 `actor_type=USER`。内部 Directory 与撤销事件端点不仅检查 scope，还强制 `SERVICE`，防止人员 Token 因误授 scope 进入服务控制面。

Identity PostgreSQL 模型包含用户、租户和成员关系。OAuth registered client、authorization 与 consent 使用 Spring Security 官方 JDBC repository 和独立协议表；Ainer 不创建自研 Token 表。

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

关闭 Ainer Resource Server 后，starter 会提供明确的 permit-all 链，避免 Spring Boot 因 Security 位于 classpath 而生成随机密码和 Basic Login。生产发行配置不得关闭；依赖 `AuthenticatedActor` 的 Workspace/AI 能力也不得借此获得匿名回退身份。

AI 请求示例：

```bash
curl -i -X POST http://127.0.0.1:8080/api/ai/chat/completions \
  -H "Authorization: Bearer ${AINER_ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"USER","content":"介绍 Ainer"}]}'
```

Token 缺失或无效返回 401；Token 已验证但缺少合法 `tenant_id` 或 `ai.invoke` scope 返回 403。两类响应都使用 Ainer `ApiResponse` 并携带 request ID。

### 2.1 高风险请求在线校验

M4.3 保留 JWT 本地签名、issuer、audience 和时间校验，并在认证成功后对高风险请求追加 RFC 7662 introspection。默认规则是：

- 所有 `/internal/**`；
- Workspace 授权审计读取；
- `/api/workspaces/**` 与 `/api/ai/**` 的 `POST`、`PUT`、`PATCH`、`DELETE`。

普通读取继续只做本地 JWT 校验。在线校验默认关闭；启用示例：

```bash
export AINER_SECURITY_ONLINE_VALIDATION_ENABLED=true
export AINER_SECURITY_ONLINE_VALIDATION_INTROSPECTION_URI=https://auth.example.com/oauth2/introspect
export AINER_SECURITY_ONLINE_VALIDATION_CLIENT_ID=ainer-resource-introspection
export AINER_SECURITY_ONLINE_VALIDATION_CLIENT_SECRET='use-secret-injection'
```

每次匹配请求都在线查询，不缓存 `active=true`。inactive 统一返回 401，不暴露不存在、过期或撤销原因；连接超时、凭据错误或响应不可解析返回 503 `AINER.SECURITY.ONLINE_VALIDATION_UNAVAILABLE`，不得退回仅凭 JWT 放行。在线结果只决定当前请求是否继续，业务层仍使用原 JWT 投影的 `AuthenticatedActor`。

生产 introspection URI 必须 HTTPS；HTTP 例外只允许显式开启后的 loopback 测试。路径、方法、连接与读取超时可配置，完整键见 [`configuration.md`](configuration.md)，上线顺序与回滚约束见 [`operations.md`](operations.md)。完整决策见 [ADR-0011](decisions/0011-selective-online-token-validation.md)。

## 3. Workspace 资源授权

Workspace 使用“能力 scope + 资源关系”双层授权：

| 操作 | 必需 scope | 资源角色 |
|---|---|---|
| 创建 Workspace | `workspace.write` | 创建者自动成为 OWNER |
| 查询详情、分页 | `workspace.read` | OWNER / ADMIN / MEMBER |
| 重命名、邀请、角色变更、移除 | `workspace.write` | OWNER / ADMIN |
| 接受本人邀请 | `workspace.read` | 同 tenant 且 `sub` 等于受邀主体 |
| 转移所有权 | `workspace.write` | 当前 OWNER，目标必须是 ACTIVE 成员 |

HTTP 创建请求只有 `name`，tenant 和 owner 由 JWT 的 `tenant_id` / `sub` 产生。通用成员接口只能邀请 ADMIN 或 MEMBER，邀请初始为 `PENDING`，不产生任何资源访问权。受邀主体必须使用同 tenant 且 `sub` 与邀请目标一致的已验证 token 接受，才会变为 `ACTIVE`。这复用可信 Identity Provider 的主体证明，同时避免 Workspace 读取 Identity 私有表。

角色变更只能在 ADMIN/MEMBER 之间进行；移除非 OWNER 成员会立即撤销后续访问。所有权只能由当前 OWNER 通过专用事务转移：事务先锁定 Workspace，把旧 OWNER 降为 ADMIN，再提升一个 ACTIVE 成员；部分唯一索引保证最多一个 ACTIVE OWNER。跨租户或非 ACTIVE 成员访问返回 404，已经是 ACTIVE 成员但角色不足返回 403。

创建、改名、邀请、接受、角色变化、移除、所有权转移的允许决策，以及资源授权拒绝，都会记录 actor、target、tenant、Workspace、action、decision、稳定 reason code 和时间。审计使用独立事务且不外键关联 Workspace；受保护写操作不能在审计失败时继续。普通成功读取不逐条审计，避免在当前阶段制造无界访问日志。

审计查询使用 `workspace.audit.read` scope，并继续要求查询者是目标 Workspace 的 ACTIVE OWNER/ADMIN。查询 SQL 同时绑定 tenant 与 workspace，按时间和 UUID 稳定倒序分页；成功或拒绝读取审计的决策也进入审计。M4.2 的后台任务可将超过热保留期的记录在同一业务库内原子迁移到归档表，普通查询仍统一读取热表与归档表。默认关闭的 SIEM 拉取端点使用 `(occurredAt, id)` 游标，并为每批导出追加安全操作审计。

每个 Workspace SQL 都显式绑定 tenant；成员分页同时绑定 subject 和 `ACTIVE` 状态。完整设计见 [ADR-0006](decisions/0006-workspace-tenant-authorization-baseline.md) 与 [ADR-0007](decisions/0007-workspace-membership-lifecycle-and-audit.md)。PostgreSQL RLS 仍是未来纵深防御选项，当前不能把尚未验证的连接池租户会话当作安全边界。

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

mvn -pl ainer-authorization-server -am spring-boot:run
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
export AINER_AUTHORIZATION_BOOTSTRAP_MACHINE_TENANT_ID=tenant:local
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

两个发行物的 `/actuator/prometheus` 都是服务控制面，不是匿名健康端点。Token 必须满足 `actor_type=SERVICE`、不存在 `tenant_id`，并且只有所需的 `platform.metrics.read` scope；人员、tenant-bound 业务服务、introspection client 和普通业务 client 都不能读取指标。

受控初始化窗口可以建立独立 metrics client：

```bash
export AINER_AUTHORIZATION_BOOTSTRAP_METRICS_ENABLED=true
export AINER_AUTHORIZATION_BOOTSTRAP_METRICS_CLIENT_ID=ainer-prometheus
export AINER_AUTHORIZATION_BOOTSTRAP_METRICS_CLIENT_SECRET='at-least-24-characters-secret'
```

它只支持 Client Credentials，access token TTL 为 1 分钟，不携带 tenant，也没有 introspection 标记。创建完成后立即移除开关和明文 secret。Prometheus 应使用 OAuth2 配置从 secret file 获取 client secret，不能保存长期静态 Bearer Token。完整边界与尚未完成的 HA/轮换证据见 [ADR-0012](decisions/0012-production-observability-and-auth-availability.md)。

### 5.4 受审计 tenant 服务 Client 控制面

日常 tenant-bound 机器 client 可以使用默认关闭的内部控制面，不再通过环境变量提交调用方选择的
secret。启用前必须先在受控初始化窗口建立专用、无 tenant 的 operator client；它只持有
`oauth.clients.manage`，并把该 client ID 加入精确白名单：

```bash
export AINER_AUTHORIZATION_BOOTSTRAP_CLIENT_CONTROL_OPERATOR_ENABLED=true
export AINER_AUTHORIZATION_BOOTSTRAP_CLIENT_CONTROL_OPERATOR_CLIENT_ID=ainer-client-operator
export AINER_AUTHORIZATION_BOOTSTRAP_CLIENT_CONTROL_OPERATOR_CLIENT_SECRET='at-least-24-characters-secret'

export AINER_AUTHORIZATION_CLIENT_CONTROL_ENABLED=true
export AINER_AUTHORIZATION_CLIENT_CONTROL_OPERATOR_CLIENT_IDS=ainer-client-operator
export AINER_AUTHORIZATION_CLIENT_CONTROL_ALLOWED_SCOPES=ai.invoke,identity.directory.read
```

首次启动成功后立即移除三个 operator bootstrap 环境变量和明文 secret；保留控制面开关与
operator ID 白名单。operator bootstrap 重复运行不会覆盖已有 secret。

控制面同时要求已验证的 `actor_type=SERVICE`、无 `tenant_id`、`oauth.clients.manage` scope 和精确
operator ID；人员 Token、tenant-bound 服务或仅有 scope 但不在白名单的服务均被拒绝。operator
client 不得兼任业务、introspection、metrics 或运维审批 client。

创建只接受 tenant UUID、client ID/name、白名单内 scopes 和受限 `changeReference`。Authorization
Server 生成至少 32 字节随机 secret，数据库只保存哈希，明文仅在创建响应返回一次；后续 GET、
审计、日志和错误不得返回。调用方必须在收到响应后立即写入自己的 secret store，响应丢失时创建新
client，不能设计“找回 secret”。

轮换使用新 client ID：创建 replacement、部署新 secret、验证新旧 client 并行工作，再显式退役旧
client。退役会立即阻止旧 client 获取新 Token，并让它的历史 Token 对 RFC 7662 introspection
显示 inactive；只做离线 JWT 验证的低风险 API 仍可能在默认 5 分钟短 TTL 内接受既有 Token。
生命周期和 `CREATED`/`ROTATED`/`RETIRED` 审计同事务写入，不物理删除历史 registered client。

该控制面刻意不管理 browser/OIDC client、public client、redirect URI、metrics、introspection、
operator、`.all` 跨 tenant scope 或既有 bootstrap client。后几类仍需要独立初始化和未来专门
控制面，不能把本切片描述成“所有 OAuth Client 已纳管”。完整决策见
[ADR-0013](decisions/0013-audited-oauth-service-client-lifecycle.md)。

## 6. 浏览器与人员身份

协议装配支持 Authorization Code、PKCE、Refresh Token、Client Credentials 与 OIDC；实际可用授权类型仍由 registered client 白名单决定。禁止配置 password grant。

M4.5 已用测试专用 public client 完成真实 HTTP 浏览器会话与 PostgreSQL 端到端门禁：

- client 使用 `ClientAuthenticationMethod.NONE`、Authorization Code、精确 redirect URI 和
  `requireProofKey=true`；
- 只接受 S256，缺失 challenge、`plain`、错误 verifier 和未注册 redirect URI 均被拒绝；
- 表单登录包含 cookie/CSRF，会签发带稳定 `sub`、`tenant_id`、`roles` 的 access token 与 OIDC
  ID token；authorization code 只能交换一次；
- public client 不注册 Refresh Token grant，响应不得出现 refresh token。需要长期浏览器会话时应
  先明确 BFF、会话与轮换策略，不能给 SPA 注入 client secret。

官方 JDBC authorization 的 JsonMapper 使用隔离配置，只在 Spring Security 自带白名单之外精确
允许 `AinerUserDetails`，没有放宽全局包名或 `Object` 子类型。人员主体实现
`CredentialsContainer`，认证成功后擦除 password hash；持久化 mixin 忽略 password 属性，集成
测试直接检查授权记录不含密码或 password 字段。

这些证据不创建生产默认 browser client，也不提供注册/轮换 API、品牌登录页、同意页、租户选择、
会话治理或完整 MFA。测试 issuer、测试 client 和测试 RSA key 不能用于发行环境。

Ainer Admin 另提供只在 `dev` profile、显式开关下初始化的固定 public client
`ainer-admin-dev`。它注册 `/ainer-admin/auth/callback` 与
`/ainer-admin/auth/logged-out` 两个同源精确 URI，只允许 Authorization Code + PKCE S256，
scopes 固定为 `openid profile tenant.members.read tenant.members.write`，不注册 Refresh Token。
它用于官方参考管理应用联调，不是生产 browser client 控制面；完整边界见
[ADR-0022](decisions/0022-ainer-admin-browser-integration-baseline.md)。

同一 `dev` profile 还可以显式启用 Admin fixture。它通过 Identity 的严格幂等 bootstrap 创建
`ainer-admin-dev` 的 default OWNER，以及以独立 `ainer-admin-member-home` 为 default tenant 的
第二用户。第二用户初始不属于 Admin 主 tenant，因此可以验证“添加已有用户”；fixture 不提供默认
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
  稳定 `sub`/`tenant_id`/`roles`（customizer 按 username 解析 WebAuthn principal）。

真实签名 ceremony 已用 webauthn4j 虚拟 authenticator 在自动化测试中端到端跑通（attestation
+ assertion 闭环、`amr=pwd,mfa,pop` 与凭证管理门禁均在 HTTP 层验证）。恢复码、管理员双人恢复、
`require-invite` 首次 enrollment、登录 POST 限速和 Resource Server step-up 也已落地并默认关闭。
恢复与 enrollment 的目标 `(tenant,subject)` 必须对应 ACTIVE tenant/user/default membership；数据库
复合外键再防止孤立或跨 tenant 安全记录。登录限流使用标准 Ainer 429 envelope、`Retry-After`、
只匹配配置的 POST 路径，且明确是 node-local。step-up 只处理 USER token，校验必需 `amr`、
`auth_time` 最大年龄、未来时间和可配时钟偏差；匿名仍返回 401。

当前仍未覆盖主流真实设备/浏览器兼容矩阵、恢复通知和多节点 session/共享限流证据。TOTP 只保留为
后续受限恢复 fallback 的候选，不能作为抗钓鱼主因子。完整决策和威胁模型见 ADR-0014 至 ADR-0017。

人员账号由 `ainer-module-identity` 保存 delegating password hash、状态和唯一默认租户。Authorization Server 的 `UserDetailsService` 从该端口加载账号，签发时把稳定 UUID 写入 `sub`，把默认租户写入 `tenant_id`，并把成员角色写入 `roles`。

Identity Directory 只返回 ACTIVE tenant、ACTIVE user、ACTIVE membership 的 tenant、subject、username、display name 和 role，不返回密码哈希、账号锁定细节或 OAuth 协议数据。默认关闭的 HTTP adapter 要求服务 JWT：`identity.directory.read` 只能查询 Token `tenant_id`，`identity.directory.read.all` 才能选择路径中的任意 tenant。人员 Token 即使误含 scope 也会被拒绝。`ainer-server` 可选 Directory client 使用 OAuth 2.0 Client Credentials，在启用时于创建 Workspace 邀请前验证目标是 ACTIVE Directory member；404 拒绝邀请，身份或传输故障按 503 关闭失败。

租户成员 API 只接受 USER token，并同时要求 `tenant.members.read|write` capability、路径 tenant
等于可信 claim，以及数据库中的 ACTIVE OWNER/ADMIN 调用者关系。写操作不能授予或修改 OWNER；
新增、重新激活、角色变更和移除都与 actor/target/tenant/reason/request ID 审计同事务提交。
该 API 位于 Identity 权威数据库所在的 Authorization Server；业务 Resource Server 不复制
Identity 表。Ainer Admin API 在本地 JWT 验证后逐请求查询官方 authorization repository；
未知、过期、撤销、client 退役或 Identity 当前状态失效统一 401，查询依赖失败统一 503
`AINER.SECURITY.ONLINE_VALIDATION_UNAVAILABLE`，不会降级为只检查 JWT。

`POST /api/me/access-token-revocations` 为 USER bearer 提供当前 access token 的窄自助撤销。
端点不接收任意 token 参数，不要求 public browser client 伪造 client authentication，也不扩大
RFC 7009 的 client 授权边界。撤销直接失效 Spring Authorization Server 官方 JDBC
authorization 中的当前 access token；不存在、过期或已撤销统一按 401 处理。

账号禁用会阻止后续人员 token 签发，非 OWNER tenant membership 可以被撤销。每次实际状态变化与 `ainer_identity_access_event` outbox 在同一事务提交；事件只保存 tenant、subject、类型、版本和时间。relay 通过短事务使用 `FOR UPDATE SKIP LOCKED` 领取并提交 lease，随后在事务外通过 HTTPS + Client Credentials 投递；成功或失败确认使用 event ID 与 lease owner 条件更新。

Workspace 事件端点要求 `actor_type=SERVICE`、`identity.access-events.publish` 和精确可信 publisher `sub`。消费事务先插入 event receipt，再将同 tenant/subject、创建时间不晚于事件时间的 PENDING/ACTIVE membership 置为 `REVOKED`。重复 event ID 幂等成功，旧事件不影响后来创建的 membership，跨 tenant 不受影响。安全禁用可以让 OWNER 变为 REVOKED 并暂时留下无 ACTIVE OWNER 的 Workspace；这优先于继续放行已禁用账号，恢复/所有权处置必须使用后续专用流程。

当前仍未提供公网注册、找回密码、恢复通知、租户切换和图形化 client 控制台；Passkey
协议/条件门禁、恢复、受控 enrollment、step-up、租户成员管理与 tenant-bound Client
Credentials 内部生命周期 API 已落地。除通用测试 client 外，`dev` profile 已提供固定的
`ainer-admin-dev` public client；它不是生产 browser client 控制面。
Directory 与事件 adapter 均默认关闭且不共享数据库；完整投递决策见
[ADR-0009](decisions/0009-cross-runtime-access-revocation-delivery.md)。

M4.3 的 Authorization Server 在查找人员 authorization 时同时检查 tenant、user、membership 当前状态和同 tenant/subject 最新 access-event 时间。Token `issuedAt` 不晚于最新事件时会被在线视为 inactive；事件发生后签发且当前身份仍 ACTIVE 的 Token 才可继续。该 revocation epoch 利用现有 Identity 事务事实和索引，不新增 Token 表。选择性在线校验只覆盖配置的高风险请求，普通低风险 JWT API 仍存在自然到期窗口，因此不能宣称所有 API 都已强实时全局撤销。

## 7. 安全运维控制面

Identity 耗尽事件重放与 Workspace OWNER 恢复是默认关闭的高风险能力。两者都采用短时两阶段流程：一个 SERVICE JWT 主体持有 request scope 建立申请，另一个 `sub` 不同的 SERVICE JWT 主体持有 approve scope 审批并在同一事务内执行。默认有效期为 15 分钟，tenant-bound scope 只能操作 Token 中的 tenant，只有显式 `.all` scope 允许跨 tenant。

必须把 request 与 approve scope 授予不同的 Client Credentials client，并由不同责任人保管凭据。代码中的 `sub` 不同检查只是技术底线，不会自动建立组织上的职责分离。`incidentReference` 只接受受限安全标识，不得填写 Token、密码、客户正文或自由文本故障细节。

Identity 只重置仍属于 `PENDING`/`FAILED`、无有效 lease 且已达最大尝试数的原事件。原 event ID、tenant、subject、类型、版本和发生时间保持不变，仅清理 lease/错误并恢复为可领取状态；因此下游 receipt 幂等语义仍有效。

OWNER 恢复只在 Workspace 无 ACTIVE OWNER、至少有一个 REVOKED OWNER，且目标是同 tenant/Workspace 的 ACTIVE 非 OWNER 成员时可执行。审批事务会锁定 Workspace 并重新检查条件；旧的 REVOKED OWNER 保持 REVOKED，不会因恢复流程重新获得访问权。

申请和成功执行阶段写入模块所属数据库的安全操作审计。当前的同库归档不是 WORM、数字签名或法律意义的不可抵赖；生产发行仍需要独立权限域、对象锁或等价不可变副本。完整决策见 [ADR-0010](decisions/0010-security-operations-and-audit-lifecycle.md)。

## 8. 验证

```bash
mvn -pl ainer-framework/ainer-starter-security -am test
mvn -pl ainer-authorization-server -am test
mvn -pl ainer-module-identity -am test
mvn -pl ainer-module-workspace -am test
mvn test
```

Resource Server 的 401/403、可信 claim、伪造身份头以及 Workspace 应用授权测试不依赖 Docker。Identity、JDBC 协议表、Client Credentials 签发与 Workspace tenant SQL 测试使用 PostgreSQL Testcontainers；没有 Docker 时会明确跳过，不会改用 H2。

M4.3 还要求验证低风险不调用 introspection、高风险无正向缓存、inactive 401、在线依赖失败 503、
专用 client 与普通 client 隔离、RFC 7009 撤销，以及 Identity epoch 的等于/前后边界。指标边界
还要验证无 Token 401，USER/tenant-bound/missing-scope 403，专用 tenantless SERVICE 200，以及
业务 Resource Server 关闭时仍不匿名公开。tenant 服务 client 控制面另需验证一次性 secret、scope
白名单、operator/tenant 隔离、蓝绿轮换、退役后新 Token 401、历史 Token introspection inactive
和无 secret 审计。PKCE 门禁必须使用真实 HTTP 会话和 PostgreSQL，覆盖 S256 正反路径、登录 CSRF、
授权码重放、redirect URI、人员 claims、无 refresh token 以及协议记录不落凭证。Passkey 基线还
必须覆盖配置失败关闭、UV-required options、无凭证 bootstrap、已登记账号条件拦截、生命周期/
审计同事务、软撤销、replacement、最后凭证保护、恢复/enrollment tenant-subject 绑定、登录
429 和 step-up 的 200/401/403。租户成员管理还要以真实 PostgreSQL + HTTP 覆盖 USER/SERVICE、
scope、跨 tenant、实时资源角色与审计。真实 PostgreSQL 和协议 smoke 证据维护在
[`project-status.md`](project-status.md)。

Ainer Admin 还必须以同一个 browser cookie session 覆盖 PKCE、成员操作、当前 access token
撤销和 OIDC logout；完整同源集成与验收命令见
[`ainer-admin-integration.md`](ainer-admin-integration.md)。
