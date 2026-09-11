# Ainer 运行与故障处理手册

> 文档类型：运行手册 · 状态：基础版 · 最近核对：2026-08-28 · 适用版本：`1.0.x–1.4.x`

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

脚手架默认关闭在线校验与 step-up，方便本地和 CI 不依赖 introspection。**对外生产签发前必须先
按 2.3 启用高风险路径在线校验，并启用 step-up**（默认保护 Workspace 所有权转移）。人员撤销
在 Authorization Server 侧已由 `securityEpoch` 即时生效；Resource Server 高风险写路径若保持
默认关闭，仍会接受未过期 JWT，直到自然到期。关闭这两项属于安全降级，须独立批准并记录窗口。

### 2.1 人员撤销在线生效

账号禁用、密码轮换、凭据撤销与服务主体禁用都在同一条带期望态的条件 UPDATE 内递增
`securityEpoch`（状态与 epoch 一起前进，非法迁移与并发竞争失败关闭），不依赖跨运行时事件
relay。Authorization Server 在查找人员 authorization 时用 JWT `sec_epoch` claim 与 Identity
当前 epoch 比对，不等即 inactive。

**边界必须一起说明**：这条链路只影响走在线校验（RFC 7662 introspection，即 2.3 节开关）的请求。
关闭在线校验的资源服务器在 Token 过期前仍会接受旧 epoch 的自包含 JWT——那是 TTL 窗口，不是
"已全局强实时撤销"。首次启用按以下顺序：

1. 先发布 Authorization Server 与应用，确认新 baseline 从空库重放成功；
2. 用真实浏览器会话签发含 `sec_epoch` 的 `USER_NEUTRAL_V1` Token；
3. 变更账号密码或禁用账号，验证旧 Token 在线校验返回 inactive、新签发 Token 正常；
4. 需要更强实时性的高风险路径再按 2.3 节启用在线 introspection；
5. 需要运营界面化操作时按 2.4 节启用身份生命周期控制面，并登记唯一受信 SERVICE `sub`。

回滚时保留已签发的 OAuth authorization 与 Identity 元数据不变。epoch 方案是 Greenfield 的
原子切换结果，不保留 access-event outbox、relay 或消费端。

### 2.4 身份生命周期控制面启用顺序（默认关闭）

1. 用一次性 machine client 引导建立只持有 `identity.accounts.manage`（需要管理服务主体时再加
   `identity.service-principals.manage`）的 SERVICE client，随后删除引导开关与明文 secret；
2. 从 `ainer_identity_oauth_client_binding` 读出该 client 绑定的 ServicePrincipal UUID，
   写入 `AINER_AUTHORIZATION_IDENTITY_CONTROL_TRUSTED_SERVICE_ID`；
3. 打开 `AINER_AUTHORIZATION_IDENTITY_CONTROL_ENABLED=true`；白名单缺失或非法时启动失败，
   不要用空值启动；
4. 先用一条合法变更验证 200 + `security_epoch` 递增 + `ainer_identity_principal_lifecycle_audit`
   出现对应记录，再做真实禁用；
5. 禁用或轮换运营用 ServicePrincipal 后，其旧 Token 即使未过期也会被控制面 403 拒绝；
   轮换运营凭据必须同时更新白名单。

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

在线校验默认关闭（本地/CI）。**生产签发是上线必选项**，不是可选增强。首次启用严格按以下顺序：

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

### 2.7 签名密钥轮换（JWKS 信任锚）

轮换把「发布哪把 key」和「用哪把 key 签发」分开：`/oauth2/jwks` 发布密钥目录里的**全部**公钥，
Token 只用 `AINER_AUTHORIZATION_SIGNING_KEY_ACTIVE_ID` 指定的那一把签发。旧 key 只要还在目录里
就继续能验签，一旦移出目录就立刻失败关闭（未知 `kid`）。语义与边界见
[`security.md`](security.md) §4.1。

前置条件：密钥目录形态已启用（`AINER_AUTHORIZATION_SIGNING_KEY_DIRECTORY` +
`AINER_AUTHORIZATION_SIGNING_KEY_ACTIVE_ID`，见 [`configuration.md`](configuration.md) §5）；
目录是只读挂载且未提交进仓库；已知当前 access token TTL（默认 5 分钟，见 client 的 token
settings）。

| 步骤 | 命令 / 动作 | 可观测信号 | 回滚点 |
|---|---|---|---|
| 0. 基线 | `curl -fsS $ISSUER/oauth2/jwks \| jq -r '.keys[].kid'` | 目录里现有 `kid` 列表；启动日志 `signing key ring: active=…, published=[…]` | — |
| 1. 生成新 key | `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out <new-kid>.private.pem && openssl pkey -in <new-kid>.private.pem -pubout -out <new-kid>.public.pem` | 私钥 `0600`、公钥 `0644`，文件名 `<kid>.private.pem` / `<kid>.public.pem` | 删除新文件 |
| 2. 同时发布新旧 | 只把 `<new-kid>.public.pem` 放进目录，`active-key-id` **不变**，重启 AS | 启动日志 `published=[old, new]` 且进入 `rotation window`；`/oauth2/jwks` 出现新 `kid`；新 Token 的 `kid` 仍是旧 key | 移除新公钥文件并重启 |
| 3. 切换签发 | 把 `<new-kid>.private.pem` 放进目录，`active-key-id=<new-kid>`，重启 AS | 启动日志 `active=<new-kid>`；用 `jwt decode`/`kid` 观察新签发 Token 的 header 已是新 key；旧 Token 继续 200 | 把 `active-key-id` 改回旧 key 重启（旧私钥仍在目录时无需重新生成） |
| 4. 等一个 Token TTL | 等待 ≥ access token TTL（默认 5 分钟，含时钟偏差留裕量） | 旧 key 签发的 Token 自然过期；`kid=old` 的请求量降到 0（按 `kid` 统计 401/200 或网关访问日志） | 同步骤 3 |
| 5. 移除旧 key | 从目录删除 `<old-kid>.public.pem`（私钥另存到离线归档），重启 AS | 启动日志 `published=[<new-kid>]`；`/oauth2/jwks` 不再含旧 `kid`；旧 Token 请求 401 | 把旧公钥文件放回目录并重启（在 Token 过期前才有效） |

轮换前后的检查清单：

- 任何一步都不要同时改目录内容与 `active-key-id` 之外的配置；每次只重启一次 AS；
- 步骤 2/3 之后必须确认 `ainer-server` 侧仍能验签：用真实请求打一个受保护端点，401/403 都不等于
  「验签失败」，要看 `kid` 与错误码；
- 资源服务器按 Nimbus `JWKSourceBuilder` 默认缓存 JWKS（`DEFAULT_CACHE_TIME_TO_LIVE` 300s = 5 分钟），
  因此步骤 5 之后已经缓存旧 JWKS 的实例最多还会接受旧 key 签名的 Token 5 分钟。**不要靠缓存过期
  兜底**：步骤 4 已经让旧 Token 自然过期，这才是真正的失效点；
- 若怀疑私钥泄露，跳过步骤 4：直接执行步骤 5 并接受在途 Token 立即失效（所有 `kid=old` 的 Token
  立刻 401），同时按事件流程撤销相关账号凭据；
- 单文件形态（`AINER_AUTHORIZATION_SIGNING_KEY_ID`）没有过渡期，换 key 会让在途 Token 全部失效，
  轮换前必须先迁到目录形态。

告警建议：`kid=old` 的请求在步骤 4 结束后仍持续出现（说明有客户端缓存/自签 Token）、
JWKS 端点返回的 `kid` 集合与预期不符、启动日志中的 `active=` 与预期不符、AS 因密钥环校验失败
而重启（启动期 `IllegalStateException`，见 §4「Authorization Server 失败」）。

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
- 密钥环形态的启动失败都会带明确原因，按消息处理：`ambiguous`（同时配了单文件与目录形态）、
  `active key … has no published public key`（`active-key-id` 拼错或该 key 未发布）、
  `has no private key`（激活 key 缺 `<kid>.private.pem`）、`without matching`（有私钥无公钥）、
  `Unrecognized file`（目录里文件名不符合 `<kid>.public.pem` / `<kid>.private.pem`）、
  `same RSA key pair`（公私钥不是同一对）、`bits`（模数低于 2048）、`does not exist`（目录不存在）；
- 检查身份库与 OAuth 表 migration；
- Passkey 开启时检查 RP ID、Origin、HTTPS、timeout 与代理外部域名；不要临时扩大 Origin；
- 不把私钥内容粘贴到日志或工单。

### Resource Server 返回 401/403

- 401：检查 Token 签名、issuer、audience、有效期、`sub`；匹配高风险规则时还要检查 introspection client 与 Identity 当前 epoch/状态；
- 401 且**全部**请求都失败时，先看启动日志的 `Ainer resource server trust anchor: jwkSetUri=…`：
  配置了 `jwk-set-uri` 却没有 `issuer-uri` 属于被框架拒绝的错误配置（见 §4 下方说明），
  而 `jwk-set-uri` 指向的 AS 换过 key 时，检查 `/oauth2/jwks` 的 `kid` 集合与 Token header 的
  `kid` 是否一致（轮换 runbook 见 §2.7）；
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

### 通知记录停在 PENDING（投递引擎不运行）

提交返回 201、审计有 `TEMPLATE_*` 行，但 `ainer_notification_record.status` 长期是 `PENDING`
且日志无任何异常——这是「调度器没注册」的典型形态（2026-09-11 修复前的默认状态）：

1. 确认全局调度生效：`ainer.scheduling.enabled` 未设为 `false`，且运行时 classpath 上有
   `ainer-spring` 的
   `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
   中的 `AinerSchedulingAutoConfiguration`（`@EnableScheduling` 不再依赖任何业务开关）；
2. 确认模块装配：`ainer.notification.enabled`（默认 `true`）与 `ainer.notification.poll-interval-ms`；
3. 静态兜底：`scripts/check-runtime-wiring.sh` 把「`@Scheduled` 存在 ⇒ 生效的 `@EnableScheduling`」
   做成门禁，本地与 CI 都会拦；
4. 若记录停在 `SENDING`：查看 `lease_owner` / `lease_expires_at`。租约未过期表示仍在投递；
   租约过期后会被下一轮重新领取（实例崩溃、发送线程卡死的自愈路径）；
5. `Send failed ...` / `Send timed out ...` 是引擎的 warn 日志：发送失败按指数退避重试，
   达到 `max_retries` 后进入终态 `FAILED`；超过 `ainer.notification.delivery.send-timeout`
   的发送按失败处理，`error_message` 为 `Delivery timed out after <n>ms`；
6. 投递是 at-least-once：租约过期后的重新领取可能造成重复投递，接收方/发送方必须幂等，
   不要把它当作 exactly-once 通道。

### REVOKED OWNER 恢复

1. 先确认原 OWNER 的 Identity 状态和撤销事实，不得通过恢复流程重新激活原主体；
2. 确认 Workspace 无 ACTIVE OWNER，并选择同 Workspace 的 ACTIVE 非 OWNER 成员；
3. request client 创建申请，由另一 approve client 复核事故和目标成员后执行；
4. 验证新成员是唯一 ACTIVE OWNER、原 OWNER 仍是 REVOKED，并查看请求/执行审计。

## 5. 优雅停机

两个发行物启用 graceful shutdown，当前 shutdown phase 超时为 20 秒。终止前应停止接收新流量，等待短请求和事务完成。SSE 和长时间模型请求上线后必须重新验证超时，而不是假设 20 秒长期适用。

## 6. 数据保护

生产上线前必须另行完成并演练：

- 按 2.3 启用 Resource Server 高风险路径在线校验（introspection client、HTTPS URI、超时与告警）；
- 启用 step-up（至少覆盖默认的所有权转移路径；`REQUIRED_AMR` 与 IdP 实际因子一致）；
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

## 9. 缓存与 Redis 运维（ADR-0039）

### 9.1 缓存是最终一致，TTL 是陈旧值的最终上限

`ainer.cache.type=redis` 时缓存位于 Redis，本质是**最终一致**：写入只失效 `@CacheEvict`
覆盖的键，多实例一致性依赖所有实例使用同一套缓存键与同一个 Redis，而不是广播失效。
Redis 客户端在断连/重连窗口内的行为会进一步拉长陈旧窗口（见 9.2）。

**陈旧值的最终上限就是缓存 TTL**：

| 键 | 默认 | 含义 |
|---|---|---|
| `AINER_CACHE_REDIS_TIME_TO_LIVE` | `PT30M` | Redis 缓存条目 TTL，陈旧值的最终上限 |
| `AINER_CACHE_LOCAL_TIME_TO_LIVE` | `PT30M` | Caffeine 本地缓存写入后过期时间，同上 |

因此**强一致读路径不得依赖缓存**：需要读到最新值的路径必须直读数据库（或在写入后直读校验），
不能经由 `@Cacheable` 方法；ADR-0030 的授权决策本就不缓存，这条约束与之一致。

### 9.2 断连窗口内 evict 与读可能乱序（外部客户端行为，不是本模块缺陷）

**现象与触发条件**：Lettuce 默认 `autoReconnect=true` 且断连行为为
`DisconnectedBehavior.DEFAULT`（自动重连开启时"接受并缓冲命令"）。在 Redis 断连/重连窗口内发出的
命令会被缓冲、重连后重放，于是「写入 → `@CacheEvict`（DEL）→ 随后的读」这一串可能**乱序落地**
（较早的 PUT 落在 DEL 之后），缓存里会**静默保留旧值**，直到 TTL 到期或被下一次 evict 覆盖。
日志侧通常伴随 `io.lettuce.core.protocol.ConnectionWatchdog: Cannot reconnect ...`、
`Connection reset` 等重连痕迹。

**这是 Redis 客户端的默认行为，不是 `ainer-starter-cache` 的实现缺陷**：本模块只使用 Spring Cache 与
`StringRedisTemplate` 的同步 API，命令的落地顺序由客户端与网络决定，不由本模块控制。
本机（macOS Colima）实测复现过该乱序；把测试客户端改成 fail-fast（`REJECT_COMMANDS` +
`autoReconnect(false)`）后现象消失，同一批断言连续多轮全绿。生产默认仍是带缓冲重放的 Lettuce。

**可选的 fail-fast 手段（产品的可用性取舍，不是本模块默认值）**：需要"宁可报错也不缓冲"的产品，
可在应用侧注册一个 `LettuceClientConfigurationBuilderCustomizer`：

```java
@Bean
LettuceClientConfigurationBuilderCustomizer failFastLettuce() {
    return builder -> builder.clientOptions(ClientOptions.builder()
            .autoReconnect(false)
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .build());
}
```

代价必须一并接受：Redis 不可用时缓存读写会**直接抛异常**（`@CacheEvict` 失败不再静默，而是向调用方
冒泡），即用可用性换取"不静默陈旧"。保持默认则接受"断连窗口内可能读到旧值"。两种选择都要显式做出，
不要假设默认行为与 fail-fast 等价。

### 9.3 运维检查清单

- 关键键使用**更短 TTL**，让陈旧窗口有明确上界；
- 监控 Redis 重连日志（`ConnectionWatchdog`）与缓存命中率；重连频繁时按 9.2 评估是否 fail-fast；
- 排查"缓存值与数据库不一致"时，先看 TTL 与 evict 链路（网络/重连），再怀疑数据库或事务；
- 实际生效的缓存后端与锁实现以启动日志 / `AinerCacheCapabilities` bean 为准，不要只看配置声明。
