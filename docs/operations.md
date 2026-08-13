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

### 2.1 人员撤销在线生效

账号禁用、密码轮换和成员撤销通过递增 `securityEpoch` 使旧 Token 失效，不依赖跨运行时事件
relay。Authorization Server 在查找人员 authorization 时用 JWT `sec_epoch` claim 与 Identity
当前 epoch 比对，不等即 inactive。首次启用按以下顺序：

1. 先发布 Authorization Server 与应用，确认新 baseline 从空库重放成功；
2. 用真实浏览器会话签发含 `sec_epoch` 的 `USER_NEUTRAL_V1` Token；
3. 变更账号密码或禁用账号，验证旧 Token 在线校验返回 inactive、新签发 Token 正常；
4. 需要更强实时性的高风险路径再按 2.3 节启用在线 introspection。

回滚时保留已签发的 OAuth authorization 与 Identity 元数据不变。epoch 方案是 Greenfield 的
原子切换结果，不保留 access-event outbox、relay 或消费端。

### 2.2 M4.2 安全运维上线顺序

OWNER 恢复、归档与 SIEM 导出全部默认关闭。首次上线按以下顺序：

1. 先发布应用但保持所有 M4.2 开关关闭；
2. 分别创建 OWNER recovery request、OWNER recovery approve 和 SIEM exporter client；
3. 确保 request/approve scope 不在同一 client，凭据由不同责任人保管，同时审查 `.all` 跨
   scope 的必要性；
4. 先启用 SIEM 导出，从最早游标回放，按 audit ID 去重并持久化 checkpoint；
5. 在备份恢复的接近真实规模数据库上验证 batch size、WAL、锁等待和查询计划，确认外部副本后再启用热数据归档；
6. 最后按事故响应流程启用 OWNER 恢复控制面，完成服务身份与过期拒绝 smoke。

回滚应用时只关闭控制面与后台任务，保留申请、安全操作审计和归档表。不得为回滚而删除历史记录。

### 2.3 M4.3 选择性在线撤销上线顺序

在线校验默认关闭。首次启用严格按以下顺序：

1. 先发布含 revocation-aware authorization service 和专用 introspection client 限制的 Authorization Server，保持 Resource Server 在线校验关闭；
2. 通过独立 bootstrap 或受审计的 Client 控制面建立专用 client，确认它没有 tenant、没有业务 scope，只有 `token.introspect` 与显式 introspection 标记；
3. 使用 HTTPS 验证专用 client 可查询 active、普通 client 得到 401 `invalid_client`，并验证 RFC 7009 撤销后变为 inactive；
4. 验证账号密码轮换/禁用后的旧 Token inactive，以及 burst 后新签发 Token 正常；
5. 在 `ainer-server` 配置 URI、专用凭据、2 秒级超时和保护规则，先灰度单实例，再验证低风险不在线查询、高风险 active/inactive/依赖失败三类路径；
6. 观察在线校验放行、inactive、失败和延时指标，完成容量与告警门禁后再扩大实例和流量。

回滚时优先修复 Authorization Server 或网络依赖。关闭 Resource Server 在线校验会恢复 JWT 自然到期窗口，属于安全降级，必须独立批准、记录开始/结束时间并保持 Identity `securityEpoch` 和 OAuth authorization 元数据不变。不得通过直接修改 epoch 或重建 client 规避故障。

### 2.4 受保护 Prometheus exporter 上线顺序

应用已经提供 exporter 与授权边界，但不包含 Prometheus、dashboard 或告警部署。首次接入按以下顺序：

1. 发布两个发行物，确认 exposure 只有 `health,info,prometheus`，并验证匿名 `/actuator/prometheus` 返回 401；
2. 在受控初始化窗口启用 metrics bootstrap，创建与业务/introspection client 不同的 client ID 和 secret，随后立即移除 bootstrap 开关与明文 secret；
3. 获取 Token 后验证 USER、缺 scope 均 403，只有专用 metrics SERVICE Token 返回 200；
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

Authorization Server 多实例接受门禁至少包括：两实例共享 PostgreSQL、相同 active JWK、滚动更新、单节点中断、Token 签发/introspection/metrics 连续性、数据库中断与恢复，以及 `ainer-server` 高风险请求在依赖故障时保持 503 失败关闭。浏览器登录会话若依赖节点本地状态，入口必须显式使用粘性会话或后续设计共享会话；当前尚未完成多节点验证。

### 2.5 M4.6 Passkey 灰度启用

Passkey 默认关闭，首次启用不能只切一个开关：

1. 先发布 migration 和代码，保持 `AINER_AUTHORIZATION_PASSKEY_ENABLED=false`，确认旧 PKCE、
   Client Credentials、internal API 与 metrics smoke 不变；
2. 配置最终 HTTPS 登录域名对应的小写 RP ID、精确 Origin、RP name 和 ceremony timeout；
   反向代理必须保留正确外部 Origin，不能用内部容器域名代替；
3. 在隔离环境用目标浏览器和真实/虚拟 authenticator 验证首次登记、Passkey 登录、第二凭证
   replacement、旧凭证撤销、最后凭证拒绝、session 超时与 CSRF；
4. 高权限账号启用前，选择并演练恢复路径：启用恢复码时确保明文只在签发响应出现一次；启用管理员
   恢复时使用不同 SERVICE 主体分别持有 request/approve scope，并验证目标 account 绑定；
5. 若使用 `require-invite` enrollment，先为目标 ACTIVE 用户建立短时预授权；不得把
   `optional` 误当作生产高权限账号的受控登记策略；
6. 小范围启用，监控登录限流 allow/deny、登记/撤销/恢复审计、数据库错误和恢复工单，再扩大账号范围；
7. 多实例前验证粘性会话或另行设计共享 session；当前 WebAuthn options 存于 HTTP session，
   不能假设任意节点无状态完成同一 ceremony。

普通回滚可关闭功能并保留 `user_*`/`ainer_passkey_*` 表。对已登记账号关闭 Passkey 会把
OAuth authorization 恢复为密码路径，属于安全降级，必须审批、通知并记录时间窗口；不得通过
手工删除 lifecycle/credential 行解除门禁。

### 2.6 Ainer Admin 同源入口

Ainer Admin 固定部署在 `/ainer-admin/`，OAuth/OIDC、登录和当前 Token 撤销
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

`/actuator/prometheus` 虽在 exposure 中，但不是公开端点。匿名请求应返回 401，错误主体/scope 应返回 403；只有专用 metrics Token 可以读取。不要把 `curl` 携带的真实 Token 写入文档、工单或 shell history。

## 4. 启动失败诊断

### Flyway 失败

- 停止继续部署，不修改已执行 migration；
- 记录失败版本、SQLSTATE 和目标 schema 历史；
- 在数据库副本复现并用新 migration 修复；
- 不直接删除 Flyway history 或手工标记成功。

### Authorization Server 失败

- 检查 issuer 是否为 HTTPS；
- 检查服务端签发密钥 key ID、PEM 路径、文件权限和公私钥是否匹配；
- 检查身份库与 OAuth 表 migration；
- Passkey 开启时检查 RP ID、Origin、HTTPS、timeout 与代理外部域名；不要临时扩大 Origin；
- 不把私钥内容粘贴到日志或工单。

### Resource Server 返回 401/403

- 401：检查 Token 签名、issuer、audience、有效期、`sub`；匹配高风险规则时还要检查 introspection client 与 Identity 当前 epoch/状态；
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

### REVOKED OWNER 恢复

1. 先确认原 OWNER 的 Identity 状态和撤销事实，不得通过恢复流程重新激活原主体；
2. 确认 Workspace 无 ACTIVE OWNER，并选择同 Workspace 的 ACTIVE 非 OWNER 成员；
3. request client 创建申请，由另一 approve client 复核事故和目标成员后执行；
4. 验证新成员是唯一 ACTIVE OWNER、原 OWNER 仍是 REVOKED，并查看请求/执行审计。

## 5. 优雅停机

两个发行物启用 graceful shutdown，当前 shutdown phase 超时为 20 秒。终止前应停止接收新流量，等待短请求和事务完成。SSE 和长时间模型请求上线后必须重新验证超时，而不是假设 20 秒长期适用。

## 6. 数据保护

生产上线前必须另行完成并演练：

- PostgreSQL 自动备份、保留期和加密；
- point-in-time recovery 或等价恢复策略；
- 身份库与业务库一致的恢复点选择；
- RSA 私钥备份、访问审计和轮换；
- Workspace 授权审计的最终保留/删除策略、法律保留和外部不可变副本。

这些能力当前属于缺口，不能仅凭应用测试宣称已具备灾难恢复能力。

## 7. 最小事件记录模板

发生故障时记录：时间线、版本与配置摘要、影响功能范围、request/invocation ID、HTTP/稳定错误码、数据库 migration 状态、采取的动作、恢复确认记录和后续预防项。记录中必须移除密码、Token、私钥、API key、prompt 和客户敏感正文。

## 8. 当前可观测性与告警基线

已有 request ID、Actuator health/info、受保护 Prometheus exporter、AI invocation 审计、Workspace 授权审计以及以下 Micrometer 指标：

| 指标 | 类型 | 含义 |
|---|---|---|
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
| `ainer.passkey.recovery.requested` / `.executed` | Counter | Passkey 管理员双人恢复申请/成功执行数 |

初始告警条件至少包括：`ownerless > 0` 立即告警、archive failure 增长，以及 DENIED 窗口值明显超过环境基线。DENIED 阈值必须根据正常流量建基线，不能在未观测环境中伪造通用数字。

在线校验初始告警至少包括 `.failed` 持续增长、`.inactive` 异常突增和 `.duration` 接近读取超时；阈值必须由压测和真实流量建立。当前代码已经安全暴露 Prometheus 文本 exporter，但尚未部署生产 Prometheus、统一 dashboard、告警路由、trace 和结构化日志 schema。exporter、指标、归档代码和 SIEM 拉取 API 存在，不等于生产监控或外部不可变审计链路已经完成。
