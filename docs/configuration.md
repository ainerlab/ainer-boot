# Ainer 配置与秘密管理

> 文档类型：开发与运维规范 · 状态：生效 · 最近核对：2026-07-26 · 适用版本：`0.1.x`

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
| `AINER_SECURITY_ONLINE_VALIDATION_ENABLED` | `false` | 启用选择性 RFC 7662 在线校验 |
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
| `AINER_SECURITY_STEP_UP_ENABLED` | `false` | 启用人员近期强认证门禁 |
| `AINER_SECURITY_STEP_UP_MAX_AUTH_AGE` | `15m` | `auth_time` 最大年龄，必须大于 0 且不超过 24 小时 |
| `AINER_SECURITY_STEP_UP_CLOCK_SKEW` | `60s` | 签发方/资源方时钟偏差容忍，范围 0..5 分钟 |
| `AINER_SECURITY_STEP_UP_REQUIRED_AMR` | `mfa` | USER token 必须包含的全部 `amr` |
| `AINER_SECURITY_STEP_UP_ALWAYS_PATHS` | 空 | 所有方法均要求 step-up 的路径列表 |
| `AINER_SECURITY_STEP_UP_MUTATING_PATHS` | `/api/workspaces/*/ownership-transfers` | 默认保护 Workspace 所有权转移 |
| `AINER_SECURITY_STEP_UP_MUTATING_METHODS` | `POST,PATCH,DELETE` | 与 mutating paths 共同匹配 |

未认证请求继续由 Resource Server 返回 401；step-up 只消费 `actor_type=USER`，不把 SERVICE
token 误判为缺 MFA。未来超过允许 skew 的 `auth_time`、过期、缺失或缺强因子均返回特定 403。

跨运行时 Identity 能力默认关闭：

| 环境变量 | 默认值 | 生产说明 |
|---|---|---|
| `AINER_IDENTITY_DIRECTORY_CLIENT_ENABLED` | `false` | 启用 Workspace 邀请前的远程 Directory 校验 |
| `AINER_IDENTITY_DIRECTORY_BASE_URL` | 空 | Authorization Server 根 URL，必须 HTTPS |
| `AINER_IDENTITY_DIRECTORY_TOKEN_URI` | 空 | OAuth 2.0 Token endpoint，必须 HTTPS |
| `AINER_IDENTITY_DIRECTORY_CLIENT_ID` | 空 | Directory 专用 Client Credentials client |
| `AINER_IDENTITY_DIRECTORY_CLIENT_SECRET` | 空 | 24..256 字符，secret 注入 |
| `AINER_IDENTITY_DIRECTORY_SCOPE` | `identity.directory.read.all` | 多租户业务运行时所需平台级只读 scope |
| `AINER_IDENTITY_DIRECTORY_ALLOW_INSECURE_HTTP` | `false` | 仅 loopback 自动化测试允许 `true` |
| `AINER_WORKSPACE_ACCESS_EVENT_CONSUMER_ENABLED` | `false` | 启用受保护的撤销事件消费端点 |
| `AINER_WORKSPACE_ACCESS_EVENT_TRUSTED_PUBLISHER` | 空 | 必填，必须精确等于 relay Token 的 `sub`/client ID |
| `AINER_WORKSPACE_ACCESS_EVENT_MAX_FUTURE_SKEW` | `5m` | 允许事件时间超前的窗口，必须大于 0 且不超过 1 天 |
| `AINER_WORKSPACE_ACCESS_EVENT_PROPAGATION_SLO` | `60s` | 首次成功消费的端到端传播 Timer bucket，必须大于 0 且不超过 1 天 |

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

Directory client 与 access-event consumer 可独立启用。consumer 开启但可信 publisher 缺失时启动失败；Directory client 开启但 URL、client、secret 或 scope 缺失时启动失败。

OWNER 恢复的 request/approve scope 必须授予两个不同 Client；不得把两种 scope 放进同一个 Client。SIEM exporter 使用第三个独立 Client，并只授予 `workspace.audit.export` 或平台级 `.all`。归档启用前先在数据库副本验证 WAL、锁等待和批量大小；默认 `90d` 不是合规承诺，生产保留期必须由数据分类与法规确定。

`ainer.runtime.mode` 当前在 YAML 固定为 `monolith`。它只选择本地或远程 adapter，不能把同一发行物自动变成微服务拓扑。

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
| `AINER_AI_REQUESTS_PER_MINUTE` | `60` | 当前进程内租户限流基线 |
| `AINER_AI_TENANT_DAILY_BUDGET` | `10.00` | 必须大于 0 |
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
| `AINER_IDENTITY_ENABLED` | `true` | 控制 Identity module、tenant 成员 API 与 Identity migration 装配 |
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
| `AINER_AUTHORIZATION_BOOTSTRAP_MACHINE_TENANT_ID` | 空 | bootstrap client 的可信 tenant |
| `AINER_AUTHORIZATION_BOOTSTRAP_MACHINE_SCOPES` | `ai.invoke` | 最小必要 scopes |
| `AINER_AUTHORIZATION_BOOTSTRAP_INTROSPECTION_ENABLED` | `false` | 只在建立专用在线校验 client 的初始化窗口启用 |
| `AINER_AUTHORIZATION_BOOTSTRAP_INTROSPECTION_CLIENT_ID` | 空 | bootstrap 开启时必填，不得与业务 client 复用 |
| `AINER_AUTHORIZATION_BOOTSTRAP_INTROSPECTION_CLIENT_SECRET` | 空 | 24..128 字符，secret 注入 |
| `AINER_AUTHORIZATION_BOOTSTRAP_METRICS_ENABLED` | `false` | 只在建立专用 Prometheus client 的初始化窗口启用 |
| `AINER_AUTHORIZATION_BOOTSTRAP_METRICS_CLIENT_ID` | 空 | bootstrap 开启时必填，必须与 introspection/业务 client 分离 |
| `AINER_AUTHORIZATION_BOOTSTRAP_METRICS_CLIENT_SECRET` | 空 | 24..128 字符，secret 注入 |
| `AINER_AUTHORIZATION_BOOTSTRAP_CLIENT_CONTROL_OPERATOR_ENABLED` | `false` | 只在建立无 tenant 控制面 operator 的初始化窗口启用 |
| `AINER_AUTHORIZATION_BOOTSTRAP_CLIENT_CONTROL_OPERATOR_CLIENT_ID` | 空 | bootstrap 开启时必填，必须加入下方 operator 白名单 |
| `AINER_AUTHORIZATION_BOOTSTRAP_CLIENT_CONTROL_OPERATOR_CLIENT_SECRET` | 空 | 24..128 字符，secret 注入 |
| `AINER_AUTHORIZATION_BOOTSTRAP_PLATFORM_IDENTITY_OPERATOR_ENABLED` | `false` | 只在建立无 tenant 平台 Identity operator 的初始化窗口启用 |
| `AINER_AUTHORIZATION_BOOTSTRAP_PLATFORM_IDENTITY_OPERATOR_CLIENT_ID` | 空 | bootstrap 开启时必填，必须加入平台 Identity operator 白名单 |
| `AINER_AUTHORIZATION_BOOTSTRAP_PLATFORM_IDENTITY_OPERATOR_CLIENT_SECRET` | 空 | 24..128 字符，secret 注入 |
| `AINER_AUTHORIZATION_BOOTSTRAP_PROVISIONING_NOTIFICATION_RELAY_ENABLED` | `false` | 只在建立无 tenant 预配通知 relay client 的初始化窗口启用 |
| `AINER_AUTHORIZATION_BOOTSTRAP_PROVISIONING_NOTIFICATION_RELAY_CLIENT_ID` | 空 | bootstrap 开启时必填，必须与平台 operator 分离 |
| `AINER_AUTHORIZATION_BOOTSTRAP_PROVISIONING_NOTIFICATION_RELAY_CLIENT_SECRET` | 空 | 24..128 字符，secret 注入 |
| `AINER_AUTHORIZATION_BOOTSTRAP_PROVISIONING_NOTIFICATION_RECEIPT_ENABLED` | `false` | 只在建立无 tenant 回执 gateway client 的初始化窗口启用 |
| `AINER_AUTHORIZATION_BOOTSTRAP_PROVISIONING_NOTIFICATION_RECEIPT_CLIENT_ID` | 空 | bootstrap 开启时必填，必须与 outbound relay 分离 |
| `AINER_AUTHORIZATION_BOOTSTRAP_PROVISIONING_NOTIFICATION_RECEIPT_CLIENT_SECRET` | 空 | 24..128 字符，secret 注入 |
| `AINER_AUTHORIZATION_CLIENT_CONTROL_ENABLED` | `false` | 启用受审计 tenant 服务 client 内部控制面 |
| `AINER_AUTHORIZATION_CLIENT_CONTROL_OPERATOR_CLIENT_IDS` | 空 | 启用时必填；无 tenant operator client ID 精确白名单，可逗号分隔 |
| `AINER_AUTHORIZATION_CLIENT_CONTROL_ALLOWED_SCOPES` | `ai.invoke` | tenant 服务 client 可授 scope 白名单；禁止平台、控制面和 `.all` scope |
| `AINER_AUTHORIZATION_CLIENT_CONTROL_ACCESS_TOKEN_TTL` | `5m` | managed client access token TTL，范围 30 秒至 15 分钟 |
| `AINER_AUTHORIZATION_CLIENT_CONTROL_CLIENT_SECRET_TTL` | `90d` | managed client secret 有效期，范围 1 至 365 天 |
| `AINER_AUTHORIZATION_CLIENT_CONTROL_SECRET_BYTES` | `32` | 服务端随机 secret 字节数，范围 32..64 |
| `AINER_IDENTITY_PLATFORM_CONTROL_ENABLED` | `false` | 启用平台 Identity 预配/查询与用户激活端点 |
| `AINER_IDENTITY_PLATFORM_CONTROL_OPERATOR_CLIENT_IDS` | 空 | 启用时必填；tenantless operator client ID 精确白名单，可逗号分隔 |
| `AINER_IDENTITY_PLATFORM_CONTROL_REQUEST_TTL` | `7d` | `REQUESTED` 预留有效期，范围 15 分钟至 30 天 |
| `AINER_IDENTITY_PLATFORM_CONTROL_ACTIVATION_TTL` | `24h` | 新用户 grant 有效期，不能超过 request TTL |
| `AINER_IDENTITY_PLATFORM_CONTROL_ACTIVATION_MAX_ATTEMPTS` | `5` | 错误 secret 上限，范围 1..20 |
| `AINER_IDENTITY_NOTIFICATION_ACTIVE_KEY_VERSION` | 空 | 启用时必填；AES-GCM 当前写 key 版本 |
| `AINER_IDENTITY_NOTIFICATION_PROTECTION_KEYS` | 空 | 启用时必填；逗号分隔的版本化 32-byte base64url key ring |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_RELAY_ENABLED` | `false` | 启用 outbox 到通知网关的定时 relay |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_GATEWAY_URI` | 空 | 通知网关完整 POST URI，生产必须 HTTPS |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_TOKEN_URI` | 空 | relay Client Credentials Token endpoint，生产必须 HTTPS |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_CLIENT_ID` | 空 | 独立无 tenant relay client |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_CLIENT_SECRET` | 空 | 24..256 字符，secret 注入 |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_SCOPE` | `identity.provisioning-notifications.publish` | 唯一通知发布 scope |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_ALLOW_INSECURE_HTTP` | `false` | 仅本地合约测试可显式开启 |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_FIXED_DELAY` | `5s` | relay 调度间隔，必须为正 |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_LEASE_DURATION` | `30s` | 领取租约，必须为正 |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_RETRY_DELAY` | `30s` | 失败重试延迟，必须为正 |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_MAX_ATTEMPTS` | `10` | 自动投递上限，至少 1 |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_BATCH_SIZE` | `50` | 每批 `1..500` |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_RECEIPTS_ENABLED` | `false` | 装配 provider-neutral 终态回执 API |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_RECEIPT_GATEWAY_CLIENT_IDS` | 空 | 启用时必填；tenantless gateway client ID 精确白名单 |

Ainer Admin 开发 browser client 只在 `dev` profile 中装配，并且默认关闭：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `AINER_ADMIN_BROWSER_CLIENT_ENABLED` | `false` | 显式初始化固定 public client `ainer-admin-dev` |
| `AINER_ADMIN_BROWSER_CLIENT_REDIRECT_URI` | 空 | 必须以 `/ainer-admin/auth/callback` 结尾 |
| `AINER_ADMIN_BROWSER_CLIENT_POST_LOGOUT_REDIRECT_URI` | 空 | 必须以 `/ainer-admin/auth/logged-out` 结尾且与登录回调同源 |

两个 URI 生产形态必须为 HTTPS；HTTP 只允许 `localhost`、`127.0.0.1` 或 `::1`。client 固定使用
Authorization Code、PKCE S256 和 `openid profile tenant.members.read tenant.members.write`，
不注册 client secret 或 Refresh Token。相同 `client_id` 已存在但策略不完全匹配时启动失败，
不会覆盖或静默接受漂移。该入口是开发初始化，不是生产 browser client 控制面。

Ainer Admin 开发身份 fixture 同样只在 `dev` profile 中装配并默认关闭：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `AINER_ADMIN_DEV_BOOTSTRAP_ENABLED` | `false` | 显式初始化 Admin 开发身份 |
| `AINER_ADMIN_DEV_OWNER_USERNAME` | 空 | 主 tenant 的 default OWNER 用户名 |
| `AINER_ADMIN_DEV_OWNER_PASSWORD` | 空 | 12..128 字符，只能通过 secret 注入 |
| `AINER_ADMIN_DEV_OWNER_DISPLAY_NAME` | 空 | OWNER 展示名 |
| `AINER_ADMIN_DEV_MEMBER_USERNAME` | 空 | 用于“添加已有用户”的第二用户名 |
| `AINER_ADMIN_DEV_MEMBER_PASSWORD` | 空 | 12..128 字符，只能通过 secret 注入 |
| `AINER_ADMIN_DEV_MEMBER_DISPLAY_NAME` | 空 | 第二用户展示名 |

fixture 严格创建两个不同的 Identity 信任根：OWNER 属于 `ainer-admin-dev`，第二用户以
`ainer-admin-member-home` 为 default OWNER，因此两人均可正常登录，但第二用户初始不是 Admin
主 tenant 成员。初始化复用 Identity 的严格幂等 bootstrap；任何部分占用或状态漂移都会失败关闭，
不会覆盖密码。完成后关闭开关并移除环境中的明文密码。该能力不得用于生产开户。
前端回调、同源路由和联调顺序见
[`ainer-admin-integration.md`](ainer-admin-integration.md)。

首个平台 tenant/OWNER 引导也运行在 Authorization Server，默认关闭：

| 环境变量 | 默认值 | 生产说明 |
|---|---|---|
| `AINER_PLATFORM_TENANT_BOOTSTRAP_ENABLED` | `false` | 只在空环境受控初始化窗口启用 |
| `AINER_PLATFORM_TENANT_BOOTSTRAP_TENANT_CODE` | 空 | 3..64 位小写字母/数字/连字符稳定代码 |
| `AINER_PLATFORM_TENANT_BOOTSTRAP_TENANT_NAME` | 空 | 2..80 字符 |
| `AINER_PLATFORM_TENANT_BOOTSTRAP_USERNAME` | 空 | Identity 全局唯一用户名 |
| `AINER_PLATFORM_TENANT_BOOTSTRAP_PASSWORD` | 空 | secret 注入，12..128 字符 |
| `AINER_PLATFORM_TENANT_BOOTSTRAP_DISPLAY_NAME` | 空 | 1..80 字符 |

引导仅在租户、用户与 ACTIVE 默认 OWNER 关系完整匹配时幂等跳过；任何部分占用或状态漂移都
启动失败，不覆盖已有密码。创建成功后立即从运行环境删除开关与明文密码。

Bootstrap 是幂等初始化手段，不是长期管理 API。初始化完成后应关闭。新 tenant-bound 业务服务
client 应使用受审计控制面；平台级 metrics/introspection、operator、`.all` 和既有 bootstrap
client 尚不在该 API 的管理范围。

introspection bootstrap 固定创建无 tenant、只有 `token.introspect` scope、`ainer.introspection-allowed=true` 的专用 client；其 access token TTL 为 1 分钟。端点还会再次拒绝带 tenant、额外业务 scope 或缺少显式标记的 client。普通 machine bootstrap 不具备 introspection 权限。

metrics bootstrap 固定创建无 tenant、只有 `platform.metrics.read` scope、仅支持 Client Credentials
的专用 client；client-control operator bootstrap 固定创建无 tenant、只有
`oauth.clients.manage` 的专用 client。两者 access token TTL 都是 1 分钟且没有 introspection
标记。metrics、introspection、operator 必须使用不同 ID/secret。所有 bootstrap 都只创建不存在
的 client，不会覆盖、轮换或停用已有记录。

platform identity operator bootstrap 固定创建无 tenant、只有
`platform.tenants.read|write` 与 `platform.users.read|write` 四个 scope、仅支持 Client
Credentials、access token TTL 一分钟且没有 introspection 标记。若相同 client ID 已存在但
scope、grant、认证方式、tenant、Token 格式或 TTL 不完全符合策略，启动失败而不是覆盖。它必须与
metrics、introspection、client-control、Passkey recovery 和事件重放凭据分离。

provisioning notification relay bootstrap 固定创建无 tenant、只有
`identity.provisioning-notifications.publish`、仅支持 Client Credentials、Token TTL 一分钟且
没有 introspection 标记。既有同 ID client 的 scope、grant、认证方式、tenant、Token 格式或 TTL
不完全匹配时同样拒绝启动。

provisioning notification receipt bootstrap 固定创建无 tenant、只有
`identity.provisioning-notifications.receipts.write`、仅支持 Client Credentials、Token TTL
一分钟且没有 introspection 标记。它必须与 relay、platform operator、metrics、introspection
使用不同 ID/secret；同 ID client 的策略不完全匹配时启动失败，不覆盖既有 secret。

平台 Identity 控制面与激活配置：

| 环境变量 | 默认值 | 生产说明 |
|---|---|---|
| `AINER_IDENTITY_PLATFORM_CONTROL_ENABLED` | `false` | 同时装配平台申请与用户激活端点 |
| `AINER_IDENTITY_PLATFORM_CONTROL_OPERATOR_CLIENT_IDS` | 空 | tenantless 平台 operator 精确 ID 白名单 |
| `AINER_IDENTITY_PLATFORM_CONTROL_REQUEST_TTL` | `7d` | 已有用户待接受请求 TTL，范围 `15m..30d` |
| `AINER_IDENTITY_PLATFORM_CONTROL_ACTIVATION_TTL` | `24h` | 新用户 grant TTL，范围 `5m..request TTL` |
| `AINER_IDENTITY_PLATFORM_CONTROL_ACTIVATION_MAX_ATTEMPTS` | `5` | secret 失败上限，范围 `1..20` |
| `AINER_IDENTITY_NOTIFICATION_ACTIVE_KEY_VERSION` | 空 | 当前 AES-GCM 写入密钥版本 |
| `AINER_IDENTITY_NOTIFICATION_PROTECTION_KEYS` | 空 | 逗号分隔的 `version:base64url` key ring；每把解码后必须恰好 32 bytes |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_RELAY_ENABLED` | `false` | 装配 HTTPS gateway publisher 与 scheduler |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_GATEWAY_URI` | 空 | 网关完整 POST URI；不允许 user-info、query 或 fragment |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_TOKEN_URI` | 空 | OAuth2 Token endpoint |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_CLIENT_ID` | 空 | relay 专用 client ID |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_CLIENT_SECRET` | 空 | relay secret，使用 secret manager 注入 |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_SCOPE` | `identity.provisioning-notifications.publish` | 不得增加平台或业务 scope |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_ALLOW_INSECURE_HTTP` | `false` | 只供 loopback 自动化测试 |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_FIXED_DELAY` | `5s` | scheduler 周期 |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_LEASE_DURATION` | `30s` | 多实例领取租约 |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_RETRY_DELAY` | `30s` | provider/鉴权/网络失败后的再次可领取延迟 |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_MAX_ATTEMPTS` | `10` | exhausted 判断与自动领取上限 |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_BATCH_SIZE` | `50` | 每周期上限 `1..500` |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_RECEIPTS_ENABLED` | `false` | 装配终态回执 Controller 与精确安全 matcher |
| `AINER_IDENTITY_PROVISIONING_NOTIFICATION_RECEIPT_GATEWAY_CLIENT_IDS` | 空 | 启用时必填；可逗号分隔的 gateway client ID 白名单 |

控制面启用时，operator 白名单、激活策略、活动密钥版本或 key ring 缺失/非法都会拒绝启动。
POST 同时需要两个 write scope，GET 同时需要两个 read scope；写 scope 不隐式包含读 scope。
key ring 用于保护联系目标与激活明文，必须由 secret manager 注入，不能写入仓库或普通配置中心；
轮换时先加入新 key 并切换 active version，旧 key 至少保留到对应 outbox 全部被网关接收或安全
取消。relay 启用时 URI、client、secret、scope 或重试边界缺失/非法会拒绝启动，生产 URI 默认
强制 HTTPS。网关返回 2xx 后 outbox 标记 `PUBLISHED` 并销毁可解密 payload；因此网关必须先持久化
并按 `Idempotency-Key` 去重。receipt 开关启用但 gateway 白名单为空或 ID 非法时同样拒绝启动；
回调 client 只能使用专用 scope，不能复用 relay credential。当前代码已开放预配申请/查询、新用户
grant 消费、已有用户本人接受、平台取消、tenant/user 列表、通知网关 transport 与 provider-neutral
终态回执接收；真实外部邮件/短信/站内信供应商不在仓库中。
需要接受已有用户请求的 browser/OIDC registered client 必须显式允许
`identity.provisioning.accept`；平台 operator 和 relay client 都不得获得该人员 scope。

Client 控制面配置在启动时失败关闭：operator 白名单或 allowed scope 为空会拒绝启动；输入含
`oauth.clients.manage`、`token.introspect`、`platform.metrics.read` 或任意 `.all` scope 也会
拒绝启动。operator Token 还必须是无 tenant 的 SERVICE 并含 `oauth.clients.manage`。secret 由
服务端生成且只在创建/轮换响应返回一次，不能把响应 body 记录到 ingress、APM 或工单。完整使用
和限制见 [`security.md`](security.md) 与
[ADR-0013](decisions/0013-audited-oauth-service-client-lifecycle.md)。

Passkey 开关默认关闭。启用时 RP ID、RP name 和 Origin 缺失或越界会拒绝启动；Origin 不允许
path、query、fragment 或 user-info。生产必须使用 HTTPS，`allow-insecure-http` 不能为普通
HTTP 域名开后门。RP ID/Origin 是浏览器密码学信任边界，域名变更需要迁移和安全评审，不能当作
普通 UI 配置临时切换。完整启用、降级和恢复限制见
[ADR-0014](decisions/0014-passkey-first-human-authentication.md)。登录限流为单节点安全基线；多实例
部署前必须改用共享权威限流或在可信入口统一节流。恢复与 enrollment 控制面要求独立最小权限
SERVICE client，并再次校验目标 subject 属于路径 tenant 的 ACTIVE default Identity membership。

Identity 内部 API 与 relay 配置：

| 环境变量 | 默认值 | 生产说明 |
|---|---|---|
| `AINER_IDENTITY_DIRECTORY_API_ENABLED` | `false` | 暴露受服务 JWT 保护的内部 Directory adapter |
| `AINER_IDENTITY_ACCESS_EVENT_RELAY_ENABLED` | `false` | 启用 outbox 定时领取和投递 |
| `AINER_IDENTITY_ACCESS_EVENT_WORKSPACE_BASE_URL` | 空 | `ainer-server` 根 URL，必须 HTTPS |
| `AINER_IDENTITY_ACCESS_EVENT_TOKEN_URI` | 空 | OAuth 2.0 Token endpoint，必须 HTTPS |
| `AINER_IDENTITY_ACCESS_EVENT_CLIENT_ID` | 空 | relay 专用 Client Credentials client；其 ID 同时是可信 publisher subject |
| `AINER_IDENTITY_ACCESS_EVENT_CLIENT_SECRET` | 空 | 24..256 字符，secret 注入 |
| `AINER_IDENTITY_ACCESS_EVENT_SCOPE` | `identity.access-events.publish` | 事件发布最小 scope |
| `AINER_IDENTITY_ACCESS_EVENT_ALLOW_INSECURE_HTTP` | `false` | 仅 loopback 自动化测试允许 `true` |
| `AINER_IDENTITY_ACCESS_EVENT_FIXED_DELAY` | `5s` | relay 周期间隔，必须为正 |
| `AINER_IDENTITY_ACCESS_EVENT_LEASE_DURATION` | `30s` | 单次领取租约，必须为正 |
| `AINER_IDENTITY_ACCESS_EVENT_RETRY_DELAY` | `30s` | 失败后再次可领取的延迟，必须为正 |
| `AINER_IDENTITY_ACCESS_EVENT_MAX_ATTEMPTS` | `10` | 自动领取上限，至少 1 |
| `AINER_IDENTITY_ACCESS_EVENT_BATCH_SIZE` | `50` | 每批 `1..500` |
| `AINER_IDENTITY_ACCESS_EVENT_RECOVERY_ENABLED` | `false` | 暴露耗尽事件查询与双人审批重放端点 |
| `AINER_IDENTITY_ACCESS_EVENT_RECOVERY_APPROVAL_TTL` | `15m` | 重放申请有效期，必须大于 0 且不超过 1 天 |

Directory client 与 relay 应使用两个不同 client/secret，并分别只授予
`identity.directory.read.all` 与 `identity.access-events.publish`。跨 tenant Directory 的 `.all`
client 不属于当前 tenant 服务控制面，仍需独立受控初始化；tenant-bound relay 可以在把
`identity.access-events.publish` 纳入 allowed scopes 后通过控制面创建。不得复用日常业务 client
或把 secret 写入 YAML。

重放控制面与 relay 共用 `AINER_IDENTITY_ACCESS_EVENT_MAX_ATTEMPTS`，避免“自动重试尚未耗尽但人工控制面已视为耗尽”的配置分裂。查询、申请和批准分别使用 `identity.access-events.replay.read|request|approve`，平台操作使用对应 `.all`；request 与 approve 必须属于不同 Client，不能与 relay Client 复用。

## 6. Actuator 与运行时

两个发行物只公开 `health`、`info` 和 `prometheus` Actuator endpoint，并启用健康探针。`health`/`info` 保持现有公开可见性；`/actuator/prometheus` 强制要求无 tenant 的 `actor_type=SERVICE` JWT 与 `platform.metrics.read` scope。人员 Token、tenant-bound 服务 Token、缺 scope 或无 Token 均不能读取指标。显式关闭业务 Resource Server 时，指标端点仍拒绝匿名访问。

Prometheus registry 已随两个可执行发行物引入，但仓库没有部署 Prometheus、dashboard 或告警路由。生产还必须使用 TLS、受控网络入口和 secret file/store；不得把固定 Bearer Token 或 client secret 写入 YAML。

优雅停机已启用，shutdown phase 超时为 20 秒。修改超时必须结合请求、SSE 和数据库事务实测。

## 7. 新增配置检查表

- 属性归属明确，并使用 `@ConfigurationProperties`；
- 有安全默认值、边界验证和错误配置测试；
- secret 不出现在默认 YAML、日志、错误或测试快照；
- 环境变量命名使用 `AINER_*`；
- 本文件和对应专题文档已更新；
- 删除旧键时给出弃用周期和迁移说明。
