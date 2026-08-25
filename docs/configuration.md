# Ainer 配置与秘密管理

> 文档类型：开发与运维规范 · 状态：生效 · 最近核对：2026-08-25 · 适用版本：`1.0.x`

## 1. 原则

- 仓库只保存安全默认值和占位符，不保存密码、Token、API key、私钥或生产域名绑定。
- 环境变量示例用于本地开发；生产秘密应由部署平台的 secret store、只读文件挂载或 KMS 注入。
- 未配置关键安全项时应启动失败，不得生成临时生产密钥或静默关闭鉴权。
- 配置键属于兼容接口。重命名、删除或改变默认值必须更新本文件、Changelog 和发布说明。

Spring Boot 标准配置仍可通过属性、环境变量或启动参数提供。下表记录 Ainer 主动支持的键。

## 2. 公共数据库配置

| 环境变量 | 必填 | 示例 | 说明 |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | 是 | `jdbc:postgresql://127.0.0.1:5432/ainer` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | 是 | `ainer` | 数据库账号 |
| `SPRING_DATASOURCE_PASSWORD` | 是 | secret 注入 | 数据库密码 |

业务应用与 Authorization Server 应使用不同数据库和最小权限账号。

Mapper XML 位置使用 MyBatis-Plus 的 `mybatis-plus.mapper-locations`；旧的
`mybatis.mapper-locations` 不再是 Ainer 支持的配置键。Ainer persistence starter 固定
PostgreSQL 分页 `maxLimit=100`、全局 `IdType.AUTO` 和显式 UUID TypeHandler；这些默认值的
架构边界见
[ADR-0028](decisions/0028-mybatis-plus-infrastructure-baseline.md)。

## 3. `ainer-server`

| 环境变量 | 默认值 | 生产说明 |
|---|---|---|
| `AINER_WORKSPACE_ENABLED` | `true` | 控制 Workspace 模块装配 |
| `AINER_SECURITY_RESOURCE_SERVER_ENABLED` | `true` | 生产不得关闭 |
| `AINER_SECURITY_ISSUER_URI` | 空 | Resource Server 启用时必须指向可信 issuer |
| `AINER_SECURITY_AUDIENCES` | `ainer-api` | access token 必须包含的 audience，可配置列表 |

M4.3 高风险请求在线 Token 校验默认关闭：

| 环境变量 | 默认值 | 生产说明 |
|---|---|---|
| `AINER_SECURITY_ONLINE_VALIDATION_ENABLED` | `false` | 本地/CI 保持关闭；**生产签发前必须按 [`operations.md`](operations.md) 2.3 启用** |
| `AINER_SECURITY_ONLINE_VALIDATION_INTROSPECTION_URI` | 空 | 启用时必填，生产必须为 HTTPS `/oauth2/introspect` |
| `AINER_SECURITY_ONLINE_VALIDATION_CLIENT_ID` | 空 | 无 tenant、无业务 scope 的专用 introspection client |
| `AINER_SECURITY_ONLINE_VALIDATION_CLIENT_SECRET` | 空 | 启用时必填，secret 注入 |
| `AINER_SECURITY_ONLINE_VALIDATION_CONNECT_TIMEOUT` | `2s` | 必须为正数 |
| `AINER_SECURITY_ONLINE_VALIDATION_READ_TIMEOUT` | `2s` | 必须为正数 |
| `AINER_SECURITY_ONLINE_VALIDATION_ALLOW_INSECURE_HTTP` | `false` | 仅 loopback 自动化测试允许 `true` |
| `AINER_SECURITY_ONLINE_VALIDATION_ALWAYS_PATHS` | `/internal/**,/api/workspaces/*/authorization-audits` | 所有 HTTP 方法均在线校验的 Ant 路径 |
| `AINER_SECURITY_ONLINE_VALIDATION_MUTATING_PATHS` | `/api/workspaces/**,/api/ai/**` | 仅匹配下列变更方法时在线校验 |
| `AINER_SECURITY_ONLINE_VALIDATION_MUTATING_METHODS` | `POST,PUT,PATCH,DELETE` | 变更方法列表 |

启用时 client、secret、URI、正超时和至少一条有效保护规则缺一不可，否则启动失败。inactive 返回 401；在线依赖异常返回 503 且不回退到本地 JWT 放行。默认规则是安全基线，收窄前必须按 ADR-0011 做安全评审。

高风险人员操作的 step-up 默认关闭：

| 环境变量 | 默认值 | 生产说明 |
|---|---|---|
| `AINER_SECURITY_STEP_UP_ENABLED` | `false` | 本地/CI 保持关闭；**生产签发前必须启用**（默认保护所有权转移） |
| `AINER_SECURITY_STEP_UP_MAX_AUTH_AGE` | `15m` | `auth_time` 最大年龄，必须大于 0 且不超过 24 小时 |
| `AINER_SECURITY_STEP_UP_CLOCK_SKEW` | `60s` | 签发方/资源方时钟偏差容忍，范围 0..5 分钟 |
| `AINER_SECURITY_STEP_UP_REQUIRED_AMR` | `mfa` | USER token 必须包含的全部 `amr` |
| `AINER_SECURITY_STEP_UP_ALWAYS_PATHS` | 空 | 所有方法均要求 step-up 的路径列表 |
| `AINER_SECURITY_STEP_UP_MUTATING_PATHS` | `/api/workspaces/*/ownership-transfers` | 默认保护 Workspace 所有权转移 |
| `AINER_SECURITY_STEP_UP_MUTATING_METHODS` | `POST,PATCH,DELETE` | 与 mutating paths 共同匹配 |

未认证请求继续由 Resource Server 返回 401；step-up 只消费 `actor_type=USER`，不把 SERVICE
token 误判为缺 MFA。未来超过允许 skew 的 `auth_time`、过期、缺失或缺强因子均返回特定 403。

M4.2 安全运维与授权审计控制面默认关闭：

| 环境变量 | 默认值 | 生产说明 |
|---|---|---|
| `AINER_WORKSPACE_OWNER_RECOVERY_ENABLED` | `false` | 暴露 REVOKED OWNER 双人审批恢复端点 |
| `AINER_WORKSPACE_OWNER_RECOVERY_APPROVAL_TTL` | `15m` | 申请有效期，必须大于 0 且不超过 1 天 |
| `AINER_WORKSPACE_AUDIT_RETENTION_ENABLED` | `false` | 启用授权审计热/冷归档调度与运行指标刷新 |
| `AINER_WORKSPACE_AUDIT_HOT_RETENTION` | `90d` | 热表保留窗口；归档表当前不自动删除 |
| `AINER_WORKSPACE_AUDIT_RETENTION_FIXED_DELAY` | `5m` | 归档和指标刷新周期 |
| `AINER_WORKSPACE_AUDIT_DENIED_WINDOW` | `5m` | `denied.window` 指标的统计窗口 |
| `AINER_WORKSPACE_AUDIT_ARCHIVE_BATCH_SIZE` | `500` | 每周期最多归档 `1..5000` 条 |
| `AINER_WORKSPACE_AUDIT_EXPORT_ENABLED` | `false` | 暴露受保护的 SIEM 稳定游标拉取端点 |
| `AINER_WORKSPACE_AUDIT_TRUSTED_EXPORTER` | 空 | 启用导出时必填，精确匹配 exporter Token `sub` |

人员 Token 撤销在线判定：授权查询通过 `sec_epoch`/账号状态比对（`RevocationAwareOAuth2AuthorizationService`）而非订阅撤销事件。

OWNER 恢复的 request/approve scope 必须授予两个不同 Client；不得把两种 scope 放进同一个 Client。SIEM exporter 使用第三个独立 Client，并只授予 `workspace.audit.export` 或平台级 `.all`。归档启用前先在数据库副本验证 WAL、锁等待和批量大小；默认 `90d` 不是合规承诺，生产保留期必须由数据分类与法规确定。

`ainer.runtime.mode` 当前在 YAML 固定为 `monolith`。它只选择本地或远程 adapter，不能把同一发行物自动变成微服务拓扑。

文件存储模块（ADR-0040）默认装配：

| 环境变量 | 默认值 | 生产说明 |
|---|---|---|
| `AINER_FILE_ENABLED` | `true` | 控制文件模块装配 |
| `AINER_FILE_MAX_SIZE_BYTES` | `52428800` | 上传大小上限（50MB）；超限返回 413 |
| `AINER_FILE_ALLOWED_CONTENT_TYPES` | 常见安全白名单 | png/jpeg/gif/webp/pdf/txt/json/zip/docx/xlsx/mp4；不在白名单返回 415 |
| `AINER_STORAGE_LOCAL_ENABLED` | `true` | 本地文件适配器（`FileStoragePort` 默认实现） |
| `AINER_STORAGE_LOCAL_BASE_DIRECTORY` | `./data/ainer-storage` | 本地存储根目录；产品可用 S3/OSS bean 覆盖整个端口 |

P3 管理面 scope（ADR-0040，在应用服务内对已验证 principal 强制）：

| Scope | 模块 | 说明 |
|---|---|---|
| `dictionary.read` / `dictionary.manage` | dictionary | 查询 / 类型与项全生命周期 |
| `config.read` / `config.manage` | config | 列表与历史 / 设置值与 secret |
| `notification.read` / `notification.manage` / `notification.submit` | notification | 模板与记录查询 / 模板生命周期 / 直接提交 |
| `file.read` / `file.write` | file | 读取下载 / 上传删除 |

Webhook 真实投递默认关闭（开发继续走日志 sender）。启用后 recipient 必须是白名单 host
上的绝对 URL，生产必须 HTTPS；HTTP 仅允许显式开启后的 loopback。不跟随重定向。

| 环境变量 | 默认值 | 生产说明 |
|---|---|---|
| `AINER_NOTIFICATION_WEBHOOK_ENABLED` | `false` | 用 HTTP POST 替换 WEBHOOK 渠道的日志兜底 |
| `AINER_NOTIFICATION_WEBHOOK_ALLOWED_HOSTS` | 空 | 启用时必填；recipient host 必须完全匹配 |
| `AINER_NOTIFICATION_WEBHOOK_CONNECT_TIMEOUT` | `2s` | 必须为正数 |
| `AINER_NOTIFICATION_WEBHOOK_READ_TIMEOUT` | `2s` | 必须为正数 |
| `AINER_NOTIFICATION_WEBHOOK_ALLOW_INSECURE_HTTP` | `false` | 仅 loopback 自动化测试允许 `true` |

SMTP 邮件真实投递默认关闭。启用后必须提供 `from`，并装配 `JavaMailSender`
（通常配置 `spring.mail.host`），否则启动失败。recipient / subject 拒绝控制字符。

| 环境变量 | 默认值 | 生产说明 |
|---|---|---|
| `AINER_NOTIFICATION_EMAIL_ENABLED` | `false` | 用 SMTP 替换 EMAIL 渠道的日志兜底 |
| `AINER_NOTIFICATION_EMAIL_FROM` | 空 | 启用时必填；合法 From 地址 |

## 4. AI runtime

AI 默认关闭。启用时以下设置共同构成安全门禁：

| 环境变量 | 默认值 | 约束 |
|---|---|---|
| `AINER_AI_ENABLED` | `false` | 显式启用 |
| `AINER_AI_PROVIDER_NAME` | `openai-compatible` | 小写稳定标识 |
| `AINER_AI_BASE_URL` | 空 | 必须包含 host，生产必须 HTTPS |
| `AINER_AI_API_KEY` | 空 | secret 注入，禁止记录 |
| `AINER_AI_DEFAULT_MODEL` | 空 | 最长 128 字符 |
| `AINER_AI_ALLOWED_MODELS` | 空 | 白名单必须包含默认模型 |
| `AINER_AI_CONNECT_TIMEOUT` | `5s` | 必须为正数 |
| `AINER_AI_REQUEST_TIMEOUT` | `60s` | 必须为正数 |
| `AINER_AI_ALLOW_INSECURE_HTTP` | `false` | 仅本地合约测试可设为 `true` |
| `AINER_AI_REQUESTS_PER_MINUTE` | `60` | 当前进程内 subject 限流基线 |
| `AINER_AI_SUBJECT_DAILY_BUDGET` | `10.00` | 必须大于 0 |
| `AINER_AI_MAX_PROMPT_CHARACTERS` | `100000` | `1000..10000000` |
| `AINER_AI_CURRENCY` | `USD` | 三位大写代码 |
| `AINER_AI_INPUT_PER_MILLION_TOKENS` | `0` | 不得为负 |
| `AINER_AI_OUTPUT_PER_MILLION_TOKENS` | `0` | 不得为负 |

完整调用与审计说明见 [`ai-gateway.md`](ai-gateway.md)。

## 5. Authorization Server

| 环境变量 | 默认值 | 生产说明 |
|---|---|---|
| `AINER_AUTHORIZATION_SERVER_PORT` | `9000` | 服务端口 |
| `AINER_AUTHORIZATION_SERVER_ISSUER` | 空 | 必填，必须是显式 HTTPS URL |
| `AINER_AUTHORIZATION_SERVER_AUDIENCE` | `ainer-api` | 签发 access token 的 audience |
| `AINER_AUTHORIZATION_SIGNING_KEY_ID` | 空 | 必填，轮换时使用新 ID |
| `AINER_AUTHORIZATION_PRIVATE_KEY_LOCATION` | 空 | 必填，只读 PEM 资源位置 |
| `AINER_AUTHORIZATION_PUBLIC_KEY_LOCATION` | 空 | 必填，PEM 资源位置 |
| `AINER_IDENTITY_ENABLED` | `true` | 控制 Identity module 与 Identity migration 装配 |
| `AINER_AUTHORIZATION_PASSKEY_ENABLED` | `false` | 启用 Spring Security WebAuthn/Passkey 与条件人员门禁 |
| `AINER_AUTHORIZATION_PASSKEY_RP_ID` | 空 | 启用时必填，小写 DNS 名；测试可用 `localhost` |
| `AINER_AUTHORIZATION_PASSKEY_RP_NAME` | `Ainer` | 浏览器展示的 relying party 名称，1..100 字符 |
| `AINER_AUTHORIZATION_PASSKEY_ALLOWED_ORIGINS` | 空 | 启用时必填，精确 Origin 列表，host 必须在 RP ID 范围 |
| `AINER_AUTHORIZATION_PASSKEY_ALLOW_INSECURE_HTTP` | `false` | 仅允许 `localhost` 自动化测试显式设为 `true` |
| `AINER_AUTHORIZATION_PASSKEY_CEREMONY_TIMEOUT` | `5m` | WebAuthn ceremony timeout，必须大于 0 且不超过 10 分钟 |
| `AINER_AUTHORIZATION_PASSKEY_RECOVERY_ENABLED` | `false` | 启用管理员双人 Passkey 恢复控制面 |
| `AINER_AUTHORIZATION_PASSKEY_SELF_RECOVERY_ENABLED` | `false` | 启用人员恢复码签发/赎回；应与 Passkey 一起启用 |
| `AINER_AUTHORIZATION_PASSKEY_RECOVERY_APPROVAL_TTL` | `15m` | 管理员恢复申请有效期 |
| `AINER_AUTHORIZATION_PASSKEY_ENROLLMENT_MODE` | `optional` | `optional` 或 `require-invite`；后者要求首枚 Passkey 预授权 |
| `AINER_AUTHORIZATION_LOGIN_RATE_LIMIT_ENABLED` | `false` | 启用 node-local 登录端点固定窗口限流 |
| `AINER_AUTHORIZATION_LOGIN_RATE_LIMIT_WINDOW` | `1m` | 固定窗口，必须为正 |
| `AINER_AUTHORIZATION_LOGIN_RATE_LIMIT_MAX_REQUESTS` | `20` | 每 IP/窗口最大请求数，必须大于 0 |
| `AINER_AUTHORIZATION_LOGIN_RATE_LIMIT_PATHS` | `/login,/login/webauthn,/webauthn/authenticate/options` | 只匹配 POST，路径必须为绝对路径 |
| `AINER_AUTHORIZATION_BOOTSTRAP_MACHINE_ENABLED` | `false` | 只在受控初始化窗口启用 |
| `AINER_AUTHORIZATION_BOOTSTRAP_MACHINE_CLIENT_ID` | 空 | bootstrap 开启时必填 |
| `AINER_AUTHORIZATION_BOOTSTRAP_MACHINE_CLIENT_SECRET` | 空 | 至少 24 字符，secret 注入 |
| `AINER_AUTHORIZATION_BOOTSTRAP_MACHINE_SCOPES` | `ai.invoke` | 最小必要 scopes |
| `AINER_AUTHORIZATION_BOOTSTRAP_INTROSPECTION_ENABLED` | `false` | 只在建立专用在线校验 client 的初始化窗口启用 |
| `AINER_AUTHORIZATION_BOOTSTRAP_INTROSPECTION_CLIENT_ID` | 空 | bootstrap 开启时必填，不得与业务 client 复用 |
| `AINER_AUTHORIZATION_BOOTSTRAP_INTROSPECTION_CLIENT_SECRET` | 空 | 24..128 字符，secret 注入 |
| `AINER_AUTHORIZATION_BOOTSTRAP_METRICS_ENABLED` | `false` | 只在建立专用 Prometheus client 的初始化窗口启用 |
| `AINER_AUTHORIZATION_BOOTSTRAP_METRICS_CLIENT_ID` | 空 | bootstrap 开启时必填，必须与 introspection/业务 client 分离 |
| `AINER_AUTHORIZATION_BOOTSTRAP_METRICS_CLIENT_SECRET` | 空 | 24..128 字符，secret 注入 |
| `AINER_AUTHORIZATION_BOOTSTRAP_BROWSER_CLIENT_CONTROL_OPERATOR_ENABLED` | `false` | 只在建立 browser client 控制面 operator 的初始化窗口启用 |
| `AINER_AUTHORIZATION_BOOTSTRAP_BROWSER_CLIENT_CONTROL_OPERATOR_CLIENT_ID` | 空 | bootstrap 开启时必填，必须加入下方 operator 白名单 |
| `AINER_AUTHORIZATION_BOOTSTRAP_BROWSER_CLIENT_CONTROL_OPERATOR_CLIENT_SECRET` | 空 | 24..128 字符，secret 注入 |
| `AINER_AUTHORIZATION_BROWSER_CLIENT_CONTROL_ENABLED` | `false` | 启用受审计 browser client 控制面 |
| `AINER_AUTHORIZATION_BROWSER_CLIENT_CONTROL_OPERATOR_CLIENT_IDS` | 空 | 启用时必填；operator client ID 精确白名单，可逗号分隔 |

Ainer Admin 开发 browser client 只在 `dev` profile 中装配，并且默认关闭：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `AINER_ADMIN_BROWSER_CLIENT_ENABLED` | `false` | 显式初始化固定 public client `ainer-admin-dev` |
| `AINER_ADMIN_BROWSER_CLIENT_REDIRECT_URI` | 空 | 必须以 `/ainer-admin/auth/callback` 结尾 |
| `AINER_ADMIN_BROWSER_CLIENT_POST_LOGOUT_REDIRECT_URI` | 空 | 必须以 `/ainer-admin/auth/logged-out` 结尾且与登录回调同源 |

两个 URI 生产形态必须为 HTTPS；HTTP 只允许 `localhost`、`127.0.0.1` 或 `::1`。client 固定使用
Authorization Code、PKCE S256 和 `openid profile workspace.read workspace.write`，不注册
client secret 或 Refresh Token。相同 `client_id` 已存在但策略不完全匹配时启动失败，
不会覆盖或静默接受漂移。该入口是开发初始化，不是生产 browser client 控制面。

Ainer Admin 开发身份 fixture 同样只在 `dev` profile 中装配并默认关闭：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `AINER_ADMIN_DEV_BOOTSTRAP_ENABLED` | `false` | 显式初始化 Admin 开发身份 |
| `AINER_ADMIN_DEV_OWNER_USERNAME` | 空 | 平台 account bootstraps 之后的主账号用户名 |
| `AINER_ADMIN_DEV_OWNER_PASSWORD` | 空 | 12..128 字符，只能通过 secret 注入 |
| `AINER_ADMIN_DEV_OWNER_DISPLAY_NAME` | 空 | 账号展示名 |
| `AINER_ADMIN_DEV_MEMBER_USERNAME` | 空 | 用于"添加已有用户"的第二用户名 |
| `AINER_ADMIN_DEV_MEMBER_PASSWORD` | 空 | 12..128 字符，只能通过 secret 注入 |
| `AINER_ADMIN_DEV_MEMBER_DISPLAY_NAME` | 空 | 第二用户展示名 |

fixture 严格创建两个不同的 Identity 信任根：OWNER 属于 `ainer-admin-dev`，第二用户以
`ainer-admin-member-home` 为默认 Workspace 的 OWNER，因此两人均可正常登录，但第二用户初始
不是 Admin 主账号的成员。初始化复用 Identity 的严格幂等 bootstrap；任何部分占用或状态漂移都会
失败关闭，不会覆盖密码。完成后关闭开关并移除环境中的明文密码。该能力不得用于生产开户。
前端回调、同源路由和联调顺序见
[`ainer-admin-integration.md`](ainer-admin-integration.md)。

首个平台身份引导也运行在 Authorization Server，默认关闭：

| 环境变量 | 默认值 | 生产说明 |
|---|---|---|
| `AINER_PLATFORM_ACCOUNT_BOOTSTRAP_ENABLED` | `false` | 只在空环境受控初始化窗口启用 |
| `AINER_PLATFORM_ACCOUNT_BOOTSTRAP_USERNAME` | 空 | Identity 全局唯一用户名 |
| `AINER_PLATFORM_ACCOUNT_BOOTSTRAP_PASSWORD` | 空 | secret 注入，12..128 字符 |
| `AINER_PLATFORM_ACCOUNT_BOOTSTRAP_DISPLAY_NAME` | 空 | 1..80 字符 |

引导仅在 HumanAccount 完整匹配时幂等跳过；任何部分占用或状态漂移都启动失败，不覆盖已有密码。
创建成功后立即从运行环境删除开关与明文密码。

Bootstrap 是幂等初始化手段，不是长期管理 API。初始化完成后应关闭。

introspection bootstrap 固定创建无 tenant、只有 `token.introspect` scope、`ainer.introspection-allowed=true` 的专用 client；其 access token TTL 为 1 分钟。端点还会再次拒绝带 tenant、额外业务 scope 或缺少显式标记的 client。普通 machine bootstrap 不具备 introspection 权限。

metrics bootstrap 固定创建无 tenant、只有 `platform.metrics.read` scope、仅支持 Client Credentials
的专用 client；browser client-control operator bootstrap 固定创建无 tenant、只有
`oauth.browser-clients.manage` 的专用 client。两者 access token TTL 都是 1 分钟且没有 introspection
标记。metrics、introspection、operator 必须使用不同 ID/secret。所有 bootstrap 都只创建不存在
的 client，不会覆盖、轮换或停用已有记录。

browser client 控制面配置在启动时失败关闭：operator 白名单为空会拒绝启动；输入含
`oauth.clients.manage`、`oauth.browser-clients.manage`、`token.introspect`、`platform.metrics.read`
或任意 `.all` scope 也会拒绝启动。operator Token 还必须是无 tenant 的 SERVICE 并含
`oauth.browser-clients.manage`。管理的是生产 public Authorization Code + PKCE client，无事前
secret；只返回生命周期投影，不返回任何 secret。完整使用和限制见 [`security.md`](security.md) 与
[ADR-0013](decisions/0013-audited-oauth-service-client-lifecycle.md)。

Passkey 开关默认关闭。启用时 RP ID、RP name 和 Origin 缺失或越界会拒绝启动；Origin 不允许
path、query、fragment 或 user-info。生产必须使用 HTTPS，`allow-insecure-http` 不能为普通
HTTP 域名开后门。RP ID/Origin 是浏览器密码学信任边界，域名变更需要迁移和安全评审，不能当作
普通 UI 配置临时切换。完整启用、降级和恢复限制见
[ADR-0014](decisions/0014-passkey-first-human-authentication.md)。登录限流为单节点安全基线；多实例
部署前必须改用共享权威限流或在可信入口统一节流。恢复与 enrollment 控制面要求独立最小权限
SERVICE client，并按 `accountId` 校验目标 ACTIVE account。

## 6. Actuator 与运行时

两个发行物只公开 `health`、`info` 和 `prometheus` Actuator endpoint，并启用健康探针。`health`/`info` 保持现有公开可见性；`/actuator/prometheus` 强制要求 `token_profile=SERVICE_V1`/`actor_type=SERVICE`、`claim_contract_version=1` 且只有 `platform.metrics.read` scope 的 SERVICE JWT。人员 Token、业务服务 Token、缺 scope 或无 Token 均不能读取指标。显式关闭业务 Resource Server 时，指标端点仍拒绝匿名访问。

Prometheus registry 已随两个可执行发行物引入，但仓库没有部署 Prometheus、dashboard 或告警路由。生产还必须使用 TLS、受控网络入口和 secret file/store；不得把固定 Bearer Token 或 client secret 写入 YAML。

优雅停机已启用，shutdown phase 超时为 20 秒。修改超时必须结合请求、SSE 和数据库事务实测。

## 7. 通用授权模块（ADR-0037；ADR-0030 已被取代）

`ainer.authorization.enabled`（默认 `true`）控制 `ainer-module-authorization` 的模块装配。
关闭后授权管理 API 与 BindingResolver 不注册，决策器仅在消费方显式装配 `AuthorizationService`
时可用。

启用 Servlet Web 且宿主存在 `AuthenticatedPrincipalResolver` 时，同一配置还注册
`AinerRequestAuthorizationManager`、`AinerAuthorizeInterceptor` 和 MVC interceptor wiring；不增加
新的 YAML 开关。`@AinerAuthorize` 采用显式 opt-in，未注解 endpoint 继续服从宿主原有
`SecurityFilterChain` 与应用服务授权。注解的 `PUBLIC_PROJECTION` 不会隐式修改
`ainer.security.resource-server.public-paths`；0.1 尚未交付 projection obligation executor，故即使
宿主开放路径也会失败关闭。

管理 API 端点（`/api/authorization/**`）要求 SERVICE principal + `authorization.manage` scope，
并要求宿主提供唯一、代码注册且带版本的 `GrantAdministrationPolicy` bean，精确声明可信
`issuer + sub`、assignable Permission/Scope/target。未提供时模块使用内建 deny-all 行为；Human、
缺 scope、任意持 scope 的未登记 SERVICE 均返回 403。端点与防提权错误语义见 [`api.md`](api.md) §8。

参考装配（`ainer-server`）通过 `ainer.authorization.trusted-managers` 提供白名单的 YAML 形式：
逗号分隔条目，支持两种写法——`<issuer>|<subjectId>` 复合键（精确声明，推荐），或裸
`<subjectId>`（1.1.0 兼容写法，自动绑定本部署 resource server 的 issuer，未配置 issuer 时
该条目失效）。issuer 与主体成对生效，防止单一 issuer 部署演进为多 issuer 后同名 sub 被
误信。缺省为空 = 拒绝一切管理操作。白名单只声明"谁可以管理"，可分配目录本身仍由代码注册的
策略决定；被拒绝的管理尝试会持久化为对 `authorization.manage` 的 DENY 决策审计，审计写入
失败时异常传播、请求失败关闭，不会在缺少审计的情况下继续处理。产品部署应以自己的策略 bean
取代该参考实现。

RSA 签名密钥、撤销 epoch 和在线 introspection 配置属于 Authorization Server（§5），
不在通用授权模块配置范围内。

## 8. 任务调度模块（ADR-0047）

`ainer.task.enabled`（默认 `true`）控制 `ainer-module-task` 的模块装配（定义/作业管理 API 与
持久化）。执行引擎另有独立开关：

| 键 | 默认 | 说明 |
|---|---|---|
| `ainer.task.engine.enabled` | `true` | 执行引擎开关；关闭后仅保留管理面，不轮询领取 |
| `ainer.task.engine.poll-interval-ms` | `5000` | 轮询间隔，下限 100 |
| `ainer.task.engine.batch-size` | `10` | 单次领取上限，1–100 |
| `ainer.task.engine.retry-base-ms` | `10000` | 指数退避基数（`base × 2^(n-1)`），下限 1000 |
| `ainer.task.engine.retry-max-ms` | `3600000` | 退避上限，不小于基数 |
| `ainer.task.engine.zombie-cutoff-multiplier` | `3` | 僵尸 RUNNING 判定倍数（定义 `timeout_seconds × 倍数`），下限 2 |

非法或缺失的键自动钳制到上述默认值。引擎按 `TaskHandler` 端口的 `taskType` 派发；超时语义、
at-least-once 与幂等要求见 ADR-0047 §3。

## 9. 新增配置检查表

- 属性归属明确，并使用 `@ConfigurationProperties`；
- 有安全默认值、边界验证和错误配置测试；
- secret 不出现在默认 YAML、日志、错误或测试快照；
- 环境变量命名使用 `AINER_*`；
- 本文件和对应专题文档已更新；
- 删除旧键时给出弃用周期和迁移说明。
