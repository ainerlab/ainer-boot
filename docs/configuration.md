# Ainer 配置与秘密管理

> 文档类型：开发与运维规范 · 状态：生效 · 最近核对：2026-07-23 · 适用版本：`0.1.x`

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

Bootstrap 是幂等初始化手段，不是长期管理 API。初始化完成后应关闭，并通过后续 Client 控制面完成生命周期管理。

introspection bootstrap 固定创建无 tenant、只有 `token.introspect` scope、`ainer.introspection-allowed=true` 的专用 client；其 access token TTL 为 1 分钟。端点还会再次拒绝带 tenant、额外业务 scope 或缺少显式标记的 client。普通 machine bootstrap 不具备 introspection 权限。

metrics bootstrap 固定创建无 tenant、只有 `platform.metrics.read` scope、仅支持 Client Credentials 的专用 client；其 access token TTL 为 1 分钟，且没有 introspection 标记。它与 introspection client 必须使用不同 ID/secret。两个 bootstrap 都只创建不存在的 client，不会覆盖、轮换或停用已有记录。

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

Directory client 与 relay 应使用两个不同 client/secret，并分别只授予 `identity.directory.read.all` 与 `identity.access-events.publish`。当前 Client 管理控制面尚未完成，可以在两个受控初始化窗口使用不同 bootstrap client ID 建立它们；不得复用日常业务 client 或把 secret 写入 YAML。

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
