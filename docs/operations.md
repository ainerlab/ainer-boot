# Ainer 运行与故障处理手册

> 文档类型：运行手册 · 状态：基础版 · 最近核对：2026-07-26 · 适用版本：`0.1.x`

本手册覆盖当前两个 Spring Boot 发行物的构建、启动和基础诊断。生产部署平台、监控后端、备份系统和灾难恢复尚未选型，因此未验证的命令不能写成生产 SOP。

## 1. 构建产物

```bash
./mvnw clean verify
```

可执行 JAR：

```text
ainer-server/target/ainer-server-0.1.0-SNAPSHOT.jar
ainer-authorization-server/target/ainer-authorization-server-0.1.0-SNAPSHOT.jar
```

发布应使用固定版本而不是 `SNAPSHOT`，详见 [`releasing.md`](releasing.md)。

## 2. 启动顺序

1. 确认目标数据库可连接且已有可恢复备份；
2. 启动 Authorization Server，等待 migration 与健康检查成功；
3. 验证 issuer 元数据和 JWK；
4. 启动 `ainer-server`；
5. 验证业务健康、JWT audience 和关键授权拒绝路径；
6. AI 启用时再验证 provider 连通性、预算与错误脱敏。

当前没有自动化生产部署管线，上述顺序是验收要求，不代表已经形成可直接执行的生产发布脚本。

### 2.1 M4.1 跨运行时撤销上线顺序

Directory、relay、consumer 全部默认关闭。首次启用严格按以下顺序：

1. 先发布包含 Workspace `REVOKED`/receipt migration 的 `ainer-server`，保持 consumer 关闭；
2. 为 Directory client 与 Identity relay 分别创建 Client Credentials client，使用不同 secret，分别只授予 `identity.directory.read.all` 与 `identity.access-events.publish`；
3. 在 `ainer-server` 启用 access-event consumer，并把 trusted publisher 配成 relay client ID；
4. 在 Authorization Server 启用 Directory API；按需要在 `ainer-server` 启用 Directory client；
5. 验证服务/人员 Token 拒绝、tenant 隔离和重复事件 smoke；
6. 最后启用 Identity relay，观察积压、失败、耗尽和 Workspace 实际撤销指标。

relay 的 Token endpoint 可以是同一 Authorization Server 的外部 HTTPS issuer 地址。回滚时先关闭 relay，保留 outbox 和 receipt；不要删除失败/耗尽记录。consumer schema 向前保留，待投递根因解决后再通过 M4.2 双人审批控制面重放。

### 2.2 M4.2 安全运维上线顺序

重放、OWNER 恢复、归档与 SIEM 导出全部默认关闭。首次上线按以下顺序：

1. 先执行 Identity 和 Workspace 新 migration，发布应用但保持所有 M4.2 开关关闭；
2. 分别创建 replay request、replay approve、OWNER recovery request、OWNER recovery approve 和 SIEM exporter client；
3. 确保 request/approve scope 不在同一 client，凭据由不同责任人保管，同时审查 `.all` 跨 tenant scope 的必要性；
4. 先启用 SIEM 导出，从最早游标回放，按 audit ID 去重并持久化 checkpoint；
5. 在备份恢复的接近真实规模数据库上验证 batch size、WAL、锁等待和查询计划，确认外部副本后再启用热数据归档；
6. 最后按事故响应流程启用重放与 OWNER 恢复控制面，完成服务身份、tenant 和过期拒绝 smoke。

回滚应用时只关闭控制面与后台任务，保留申请、安全操作审计和归档表。不得为回滚而删除历史记录。

### 2.3 M4.3 选择性在线撤销上线顺序

在线校验默认关闭。首次启用严格按以下顺序：

1. 先发布含 revocation-aware authorization service 和专用 introspection client 限制的 Authorization Server，保持 Resource Server 在线校验关闭；
2. 通过独立 bootstrap 或受审计的 Client 控制面建立专用 client，确认它没有 tenant、没有业务 scope，只有 `token.introspect` 与显式 introspection 标记；
3. 使用 HTTPS 验证专用 client 可查询 active、普通 client 得到 401 `invalid_client`，并验证 RFC 7009 撤销后变为 inactive；
4. 验证人员账号/成员禁用后的旧 Token inactive，以及事件后新签发 Token 的边界；
5. 在 `ainer-server` 配置 URI、专用凭据、2 秒级超时和保护规则，先灰度单实例，再验证低风险不在线查询、高风险 active/inactive/依赖失败三类路径；
6. 观察在线校验放行、inactive、失败和延时指标，完成容量与告警门禁后再扩大实例和流量。

回滚时优先修复 Authorization Server 或网络依赖。关闭 Resource Server 在线校验会恢复 JWT 自然到期窗口，属于安全降级，必须独立批准、记录开始/结束时间并保持 Identity access-event 和 OAuth authorization 元数据不变。不得通过删除撤销事件或重建 client 规避故障。

### 2.4 受保护 Prometheus exporter 上线顺序

应用已经提供 exporter 与授权边界，但不包含 Prometheus、dashboard 或告警部署。首次接入按以下顺序：

1. 发布两个发行物，确认 exposure 只有 `health,info,prometheus`，并验证匿名 `/actuator/prometheus` 返回 401；
2. 在受控初始化窗口启用 metrics bootstrap，创建与业务/introspection client 不同的 client ID 和 secret，随后立即移除 bootstrap 开关与明文 secret；
3. 获取 Token 后验证 USER、tenant-bound SERVICE、缺 scope 均 403，只有无 tenant SERVICE + `platform.metrics.read` 返回 200；
4. 把 secret 写入 Prometheus 节点上的 secret file/store，文件权限只授予抓取进程；不得在仓库、命令历史或抓取配置中写明文；
5. 先接入 Authorization Server 自身指标，再接入 `ainer-server`，建立 scrape 成功率、Token endpoint、JVM、连接池与在线校验 dashboard；
6. 根据压测和故障注入建立告警阈值、路由、值班人与 runbook，再把“指标可抓取”升级为“生产监控已完成”。

Prometheus 抓取配置示例：

```yaml
scrape_configs:
  - job_name: ainer-server
    scheme: https
    metrics_path: /actuator/prometheus
    oauth2:
      client_id: ainer-prometheus
      client_secret_file: /run/secrets/ainer-prometheus-client-secret
      scopes:
        - platform.metrics.read
      token_url: https://auth.example.com/oauth2/token
    static_configs:
      - targets:
          - ainer-server.example.com
```

指标 client 当前只支持“新 ID 蓝绿切换”，完整退役旧 client 仍等待受审计 Client 控制面。轮换时先创建新 client、更新 Prometheus、确认持续抓取，再按变更窗口停用旧 client；在停用能力落地前不能宣称轮换闭环完成。

Authorization Server 多实例接受门禁至少包括：两实例共享 PostgreSQL、相同 active JWK、滚动更新、单节点中断、Token 签发/introspection/metrics 连续性、数据库中断与恢复，以及 `ainer-server` 高风险请求在依赖故障时保持 503 失败关闭。浏览器登录会话若依赖节点本地状态，入口必须显式使用粘性会话或后续设计共享会话；当前尚无多节点证据。

### 2.5 tenant 服务 Client 生命周期

控制面与 operator bootstrap 都默认关闭。空环境首次启用顺序：

1. 在受控窗口配置独立 operator bootstrap，使用新 client ID、secret store 注入的 24..128 字符
   secret，并同时把同一 ID 配入 `AINER_AUTHORIZATION_CLIENT_CONTROL_OPERATOR_CLIENT_IDS`；
2. 启动 Authorization Server，确认 operator 只有 `oauth.clients.manage`、无 tenant、Token TTL
   一分钟；随后立即移除 bootstrap 开关和明文 secret；
3. 保持 `AINER_AUTHORIZATION_CLIENT_CONTROL_ENABLED=true`，只把明确审核过的 tenant 业务 scope
   放入 allowed scopes；平台、operator、introspection、metrics 和 `.all` 不得加入；
4. 用 operator 的短时 Token 调用创建 API，把一次性 `clientSecret` 直接写入调用方 secret
   store；禁止进入 shell history、CI 日志、ingress/APM body、工单或聊天；
5. 用新 client smoke 验证 Token 的 `actor_type=SERVICE`、tenant、audience、scope 和业务拒绝路径。

轮换严格使用蓝绿顺序：

1. 调用 `/{oldClientId}/rotations` 创建新 ID，保存一次性新 secret；
2. 灰度部署新 ID/secret，并验证新旧 client 均能完成 Token 和业务 smoke；
3. 完成所有实例切换，观察至少一个旧 Token TTL 与调用指标；
4. 调用 `/{oldClientId}/retirement`，确认旧凭据换 Token 返回 401、旧 Token introspection 为
   inactive、新 client 不受影响；
5. 保留 registered client、生命周期和审计记录，不执行手工 DELETE。

退役不可逆。误退役时创建全新 client，不手工把状态改回 ACTIVE。离线 JWT 可能活到最多配置的
短 TTL；需要立即阻断的路径必须启用在线校验。当前 API 只管理由它创建的 tenant 服务 client，
不能用于退役 operator、metrics、introspection、`.all` 或既有 bootstrap client。

### 2.6 M4.6 Passkey 灰度启用

Passkey 默认关闭，首次启用不能只切一个开关：

1. 先发布 migration 和代码，保持 `AINER_AUTHORIZATION_PASSKEY_ENABLED=false`，确认旧 PKCE、
   Client Credentials、internal API 与 metrics smoke 不变；
2. 配置最终 HTTPS 登录域名对应的小写 RP ID、精确 Origin、RP name 和 ceremony timeout；
   反向代理必须保留正确外部 Origin，不能用内部容器域名代替；
3. 在隔离环境用目标浏览器和真实/虚拟 authenticator 验证首次登记、Passkey 登录、第二凭证
   replacement、旧凭证撤销、最后凭证拒绝、session 超时与 CSRF；
4. 高权限账号启用前，选择并演练恢复路径：启用恢复码时确保明文只在签发响应出现一次；启用管理员
   恢复时使用不同 SERVICE 主体分别持有 request/approve scope，并验证目标 tenant/subject 绑定；
5. 若使用 `require-invite` enrollment，先为目标 ACTIVE tenant member 建立短时预授权；不得把
   `optional` 误当作生产高权限账号的受控登记策略；
6. 小范围启用，监控登录限流 allow/deny、登记/撤销/恢复审计、数据库错误和恢复工单，再扩大账号范围；
7. 多实例前验证粘性会话或另行设计共享 session；当前 WebAuthn options 存于 HTTP session，
   不能假设任意节点无状态完成同一 ceremony。

普通回滚可关闭功能并保留 `user_*`/`ainer_passkey_*` 表。对已登记账号关闭 Passkey 会把
OAuth authorization 恢复为密码路径，属于安全降级，必须审批、通知并记录时间窗口；不得通过
手工删除 lifecycle/credential 行解除门禁。

### 2.7 首个平台 tenant/OWNER 引导

引导运行在 Identity 权威数据所属的 Authorization Server，默认关闭：

1. 在受控空环境通过 secret store 注入 tenant code/name、username、display name 与 12..128 字符
   password，并只在初始化窗口启用 `AINER_PLATFORM_TENANT_BOOTSTRAP_ENABLED=true`；
2. 启动 Authorization Server；确认日志只记录 tenant code 与 subject ID，不出现密码；
3. 验证租户、用户、ACTIVE 默认 membership 与 OWNER 角色完整存在，再立即移除开关和明文密码；
4. 重启验证严格幂等：完整匹配时不改密码；tenant code 或 username 部分占用、状态漂移时必须启动失败，
   不得手工补记录后继续；
5. 后续 tenant/user 使用 2.8 节专用 SERVICE 控制面，不反复开启首租户 bootstrap。

### 2.8 M4.8A 平台 Identity 预配、激活、取消与通知回执

平台预配/激活、operator bootstrap 都默认关闭。当前切片的安全上线顺序：

1. 发布 migration 和应用，保持 `AINER_IDENTITY_PLATFORM_CONTROL_ENABLED=false`；确认现有
   bootstrap、登录、Token、tenant 成员管理和 Flyway smoke 不变；
2. 在受控窗口启用 platform identity operator bootstrap，使用独立 client ID 和 secret store
   注入的 24..128 字符 secret；同一 ID 配入
   `AINER_IDENTITY_PLATFORM_CONTROL_OPERATOR_CLIENT_IDS`；
3. 启动后核对 operator 无 tenant、只有四个 `platform.tenants/users.read/write` scope、
   Client Credentials 和一分钟 Token；然后移除 bootstrap 开关与明文 secret；
4. 由 secret manager 注入 active key version 与至少一把 32-byte AES key；先在测试环境验证旧 key
   仍可解密存量 outbox，再切换写入版本，禁止在日志、命令历史或仓库保存 key；
5. 在另一个受控窗口启用 provisioning notification relay client bootstrap，建立无 tenant、只有
   `identity.provisioning-notifications.publish` 且 Token TTL 一分钟的独立 client；不得复用
   platform operator，建立后移除 bootstrap 开关和明文 secret；
6. 配置通知网关完整 HTTPS POST URI、Token URI、relay client/secret、lease/retry/max-attempts 与
   batch size，再显式启用 notification relay。网关必须按 notification UUIDv7
   `Idempotency-Key` 去重，并在返回 2xx 前持久化完整 envelope；
7. 在第三个受控窗口建立无 tenant、只有
   `identity.provisioning-notifications.receipts.write` 的回执 gateway client，把精确 ID 写入
   receipt 白名单后再启用 endpoint；不得复用 outbound relay、platform operator 或其他平台凭据；
8. 启用平台控制面，先用测试 tenant 验证首次申请、相同键重放、不同 payload 冲突、缺 scope、
   tenant-bound SERVICE、USER 和白名单外 client 拒绝；
9. 核对 request/grant/outbox/audit 各一条、Identity 核心 tenant/user/membership 仍为零变化；
   grant 只有摘要，outbox 只有密文，平台响应和日志不含联系地址、密码、激活 secret 或密文；
10. 使用受控网关验证 Token 失败、401/403、其他 4xx 与 5xx 后延迟重试；2xx 后检查 outbox 为
   `PUBLISHED`、`published_at` 已写入、payload 已标记 `destroyed` 且相同 notification ID 重放
   仍由网关返回成功。`PUBLISHED` 只代表网关持久接收，不代表最终触达；
11. 让网关分别回传受控 `DELIVERED` 和 `FAILED` 终态，验证匿名、错误 scope、tenant-bound
    SERVICE、白名单外 client 被拒；相同事实重放 `created=false`，矛盾终态 409，回执抢先于
    `PUBLISHED` 时也为 409 并由网关稍后以同一 event ID 重试。数据库与日志不得出现正文、联系
    地址、供应商原始 body 或自由文本错误；
12. 新用户消费 secret 后检查 grant/request、
   ACTIVE tenant/user/默认 OWNER 与 audit 同事务完成，并验证回放拒绝；已有 ACTIVE 用户必须以
   本人 USER Token 接受，新增 membership 不覆盖原默认 tenant；
13. 用测试申请验证显式 `/cancellations`：首次调用把 request/grant/outbox 收口为
    `CANCELLED`、payload 标记 `destroyed`、审计保存取消 change reference；重复调用不新增审计。
    再以独立 tenant/user read scope 验证分页、最大 `size=100` 和响应不含密码/OAuth/通知数据；
14. 观察 requested/idempotent/cancelled、receipt delivered/failed Counter、HTTP 4xx/5xx、
    数据库唯一冲突，以及 provisioning notification 的 pending/failed/exhausted/cancelled/
    oldest-ready 指标。仓库只有 provider-neutral 回执接收端，不包含真实外部通知网关或邮件/
    短信/站内信供应商；完成真实供应商联调和告警前不得接入商业开户，或把 `REQUESTED`/
    `PUBLISHED`/本地合成 `DELIVERED` 当作真实客户可达证据。

回滚先关闭通知回执 endpoint，再关闭通知 relay，最后关闭平台控制面和新申请；保留
request/grant/outbox/receipt/audit 及已经激活的核心事实，不回删 tenant/user/membership。开放预留会在 GET、消费或后续冲突检查时惰性转为
`EXPIRED`；secret 错误耗尽会收口为 `LOCKED/CANCELLED`。未激活申请使用显式 cancellation API，
不要手工删除、解密或改状态；该 API 不能回滚已经激活的核心身份，也不能召回已经被通知网关持久
接收的消息。当前仍没有后台过期清扫 SLA，紧急处置应记录 request ID、operator ID、change
reference、key version 与时间线。

### 2.8 Ainer Admin 同源入口

Ainer Admin 固定部署在 `/ainer-admin/`，OAuth/OIDC、登录、当前 Token 撤销和 tenant 成员 API
通过同一公开 HTTPS origin 反代到 Authorization Server。上线前必须验证外部 issuer、Host/scheme、
精确 callback/logout URI、session cookie、`Location`、no-store 与 SPA fallback 边界；不得用全局
CORS 掩盖代理路径错误。

登录、Token 交换和 `/connect/logout` 必须复用同一浏览器 cookie session，否则 ID token 的
`sid` 无法与登录 session 完成可靠注销。完整路由表、退出失败语义、开发 fixture 和 smoke
见 [`ainer-admin-integration.md`](ainer-admin-integration.md)。过渡 dev 环境的可执行发布、独立
PostgreSQL、systemd、TLS、Nginx、回滚和公网联合验收以
[`development-environment-deployment.md`](development-environment-deployment.md) 为准。它不
替代尚未完成的 production ingress、browser client 控制面和高可用验收。

## 3. 健康检查

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:9000/actuator/health
```

当前公开 `health` 和 `info`。健康为 `UP` 只证明应用存活与已注册健康组件状态，不能替代登录、发 Token、Workspace 授权和 AI 调用 smoke test。

`/actuator/prometheus` 虽在 exposure 中，但不是公开端点。匿名请求应返回 401，错误主体/tenant/scope 应返回 403；只有专用 metrics Token 可以读取。不要把 `curl` 携带的真实 Token 写入文档、工单或 shell history。

## 4. 启动失败诊断

### Flyway 失败

- 停止继续部署，不修改已执行 migration；
- 记录失败版本、SQLSTATE 和目标 schema 历史；
- 在数据库副本复现并用新 migration 修复；
- 不直接删除 Flyway history 或手工标记成功。

### Authorization Server 失败

- 检查 issuer 是否为 HTTPS；
- 检查 RSA key ID、PEM 路径、文件权限和公私钥是否匹配；
- 检查身份库与 OAuth 表 migration；
- 平台 Identity 控制面启用时，检查 operator 白名单非空、ID 格式和 15 分钟至 30 天 request TTL；
  operator bootstrap 还必须与既有同 ID client 的 scope/grant/tenant/Token 策略完全一致；
- Passkey 开启时检查 RP ID、Origin、HTTPS、timeout 与代理外部域名；不要临时扩大 Origin；
- 不把私钥内容粘贴到日志或工单。

### Resource Server 返回 401/403

- 401：检查 Token 签名、issuer、audience、有效期、`sub` 和 `tenant_id`；匹配高风险规则时还要检查 introspection client 与 Identity 当前状态/epoch；
- 403：检查 scope，再检查 Workspace ACTIVE membership 与角色；
- 用 `X-Request-Id` 关联请求，不记录完整 Bearer Token。

### 高风险在线校验返回 503

- 确认 Authorization Server 健康、TLS、DNS、连接/读取超时和 `/oauth2/introspect` 可达；
- 确认使用独立 introspection client，secret 未过期，client 只有 `token.introspect` 且无 tenant；
- 对照 `ainer.security.online.validation.failed` 与 `.duration`，区分持续依赖故障和容量/延时问题；
- 不把完整 Token、client secret 或 introspection 原始响应写入日志和事件记录；
- 不自动回退到离线 JWT 放行。确需临时关闭时按安全降级流程批准并持续追踪恢复。

### AI 调用失败

- 检查模块是否启用、HTTPS base URL、模型白名单和预算；
- 区分策略拒绝、连接超时、provider 失败和客户端断开；
- 只记录稳定错误码和调用 ID，不记录 API key、prompt 或供应商原始正文。

### 耗尽事件恢复

1. 先确认耗尽是下游故障、鉴权、网络还是不可重试契约问题；未解决根因时不得反复重放；
2. 使用 read scope 按 tenant 查询真正耗尽且无有效 lease 的事件，记录安全 `incidentReference`；
3. request client 创建申请，独立 approve client 在有效期内复核 event/tenant/事故后执行；
4. 观察原 event ID 的 relay 发布、Workspace receipt 和传播时延，不创建新事件规避幂等。

### REVOKED OWNER 恢复

1. 先确认原 OWNER 的 Identity 状态和撤销事实，不得通过恢复流程重新激活原主体；
2. 确认 Workspace 无 ACTIVE OWNER，并选择同 tenant/Workspace 的 ACTIVE 非 OWNER 成员；
3. request client 创建申请，由另一 approve client 复核事故和目标成员后执行；
4. 验证新成员是唯一 ACTIVE OWNER、原 OWNER 仍是 REVOKED，并查看请求/执行审计。

## 5. 优雅停机

两个发行物启用 graceful shutdown，当前 shutdown phase 超时为 20 秒。终止前应停止接收新流量，等待短请求和事务完成。SSE、长时间模型请求和 outbox relay 上线后必须重新验证超时，而不是假设 20 秒长期适用。

## 6. 数据保护

生产上线前必须另行完成并演练：

- PostgreSQL 自动备份、保留期和加密；
- point-in-time recovery 或等价恢复策略；
- 身份库与业务库一致的恢复点选择；
- RSA 私钥备份、访问审计和轮换；
- outbox 积压与重复消费恢复；
- Workspace 授权审计的最终保留/删除策略、法律保留和外部不可变副本。

这些能力当前属于缺口，不能仅凭应用测试宣称已具备灾难恢复能力。

## 7. 最小事件记录模板

发生故障时记录：时间线、版本与配置摘要、影响 tenant/功能范围、request/event/invocation ID、HTTP/稳定错误码、数据库 migration 状态、采取的动作、恢复证据和后续预防项。记录中必须移除密码、Token、私钥、API key、prompt 和客户敏感正文。

## 8. 当前可观测性与告警基线

已有 request ID、Actuator health/info、受保护 Prometheus exporter、AI invocation 审计、Workspace 授权审计以及以下 Micrometer 指标：

| 指标 | 类型 | 含义 |
|---|---|---|
| `ainer.identity.access.events.published` | Counter | relay 成功确认总数 |
| `ainer.identity.access.events.failed` | Counter | 投递失败并安排重试总数 |
| `ainer.identity.access.events.relay.cycle.failed` | Counter | relay 周期自身失败总数 |
| `ainer.identity.access.events.pending` | Gauge | 未达上限的 PENDING 数 |
| `ainer.identity.access.events.failed.current` | Gauge | 未达上限、仍可重试的 FAILED 数 |
| `ainer.identity.access.events.exhausted` | Gauge | 已达最大尝试且不再自动领取的数 |
| `ainer.identity.access.events.oldest.ready.age.seconds` | Gauge | 最老可领取事件积压年龄 |
| `ainer.workspace.identity.access.events.received` | Counter | consumer 成功处理总数，含重复 |
| `ainer.workspace.identity.access.events.duplicates` | Counter | receipt 命中的重复总数 |
| `ainer.workspace.identity.access.memberships.revoked` | Counter | 实际进入 REVOKED 的 membership 总数 |
| `ainer.workspace.identity.access.events.propagation` | Timer | 事件发生至首次成功消费的端到端时延，含可配置 SLO bucket |
| `ainer.identity.access.events.replay.requested` / `.executed` | Counter | 重放申请/成功执行数 |
| `ainer.workspace.owner.recovery.requested` / `.executed` | Counter | OWNER 恢复申请/成功执行数 |
| `ainer.workspace.authorization.audit.archived` | Counter | 从热表完成归档的数量 |
| `ainer.workspace.authorization.audit.archive.failed` | Counter | 归档周期失败数 |
| `ainer.workspace.authorization.audit.hot` | Gauge | 当前热表记录数 |
| `ainer.workspace.authorization.audit.archive.current` | Gauge | 当前归档表记录数 |
| `ainer.workspace.authorization.audit.denied.window` | Gauge | 配置时间窗口内的 DENIED 数 |
| `ainer.workspace.authorization.audit.oldest.hot.age.seconds` | Gauge | 最旧热审计的年龄 |
| `ainer.workspace.ownerless` | Gauge | 无 ACTIVE OWNER 的 Workspace 数 |
| `ainer.workspace.authorization.audit.exported` | Counter | SIEM 导出批次成功返回的记录数 |
| `ainer.security.online.validation.allowed` | Counter | 高风险请求在线判定 active 并继续的数量 |
| `ainer.security.online.validation.inactive` | Counter | 在线判定 inactive 并返回 401 的数量 |
| `ainer.security.online.validation.failed` | Counter | introspection 依赖失败并返回 503 的数量 |
| `ainer.security.online.validation.duration` | Timer | 每次高风险 introspection 调用耗时，不包含后续业务处理 |
| `ainer.identity.tenant.provisioning.requested` | Counter | 新建平台 Identity 预配申请总数 |
| `ainer.identity.tenant.provisioning.idempotent` | Counter | 命中同 operator/幂等键/摘要并返回原申请的总数 |
| `ainer.identity.tenant.provisioning.cancelled` | Counter | 显式取消首次完成 `REQUESTED -> CANCELLED` 的总数；幂等重放不增加 |
| `ainer.identity.tenant.provisioning.notification.delivered` | Counter | 首次登记 `DELIVERED` 回执的总数；幂等重放不增加 |
| `ainer.identity.tenant.provisioning.notification.failed` | Counter | 首次登记 `FAILED` 回执的总数；幂等重放不增加 |

初始建议目标是：滚动 30 天内 99% 的首次成功 Workspace 撤销消费在可配置的 60 秒内完成。Timer 只包含已成功样本，因此必须同时使用 `exhausted == 0`、最老可领取年龄不超过 60 秒和 relay cycle 无持续失败作为完整性守门。这是尚未用真实流量验证的初始 SLO，不是正式错误预算。

初始告警条件至少包括：`exhausted > 0` 立即告警、最老可领取年龄持续超过 60 秒、`ownerless > 0` 立即告警、archive failure 增长，以及 DENIED 窗口值明显超过环境基线。DENIED 阈值必须根据正常流量建基线，不能在未观测环境中伪造通用数字。

在线校验初始告警至少包括 `.failed` 持续增长、`.inactive` 异常突增和 `.duration` 接近读取超时；阈值必须由压测和真实流量建立。当前代码已经安全暴露 Prometheus 文本 exporter，但尚未部署生产 Prometheus、统一 dashboard、告警路由、trace 和结构化日志 schema。exporter、指标、归档代码和 SIEM 拉取 API 存在，不等于生产监控或外部不可变审计链路已经完成。

平台预配初始观察至少包括：requested 突增、idempotent 比例异常、409/422 增长、开放
`REQUESTED` 数量与最老 `expires_at`。后两项当前没有现成 Gauge，需先用受限只读查询或后续专用
指标补齐，不能把 Counter 存在误报为完整告警闭环。
