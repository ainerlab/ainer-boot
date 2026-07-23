# ADR-0012：生产指标访问与 Authorization Server 可用性边界

- 状态：Proposed
- 日期：2026-07-23
- 决策者：Ainer 核心维护者
- 取代：无
- 被取代：无

## 背景

ADR-0011 让 Authorization Server 成为高风险 API 在线撤销的依赖，并要求至少观测在线放行、inactive、依赖失败和调用延时。代码已经记录这些 Micrometer 指标，但两个发行物只公开 `health` 与 `info`，没有生产可抓取的指标端点。直接匿名公开 Actuator 或复用业务 client 会扩大信息泄露和凭据权限，因此必须先确定指标访问身份、失败语义与运行边界。

当前 Authorization Server 的 OAuth registered client 与 authorization 使用共享 PostgreSQL JDBC repository，具备多实例共享协议状态的基础；但浏览器登录会话、容量、故障切换、数据库连接与真实多节点行为仍未验证。Ainer 不能把“可以启动多个进程”写成已经完成高可用。

## 决策驱动因素

- 指标端点默认拒绝匿名访问，并与业务 tenant、人员身份和业务 scope 隔离；
- Prometheus 能使用标准 OAuth 2.0 Client Credentials 自动获取短时 Token；
- 关闭业务 Resource Server 的本机模式不能意外公开指标；
- 指标抓取失败必须可与业务健康、Token 签发和在线校验故障分别定位；
- 不引入静态 Bearer Token、自研监控鉴权或新的共享会话存储；
- 只声明已经有代码和验证证据支持的可用性能力。

## 备选方案

### 匿名公开 `/actuator/prometheus`

配置最少，但会暴露运行时、路由、JVM、数据库池和安全指标，且业务安全显式关闭时更容易误上线。拒绝。

### 只依赖内网或入口 IP 白名单

网络边界可作为纵深防御，但不能证明调用主体，也难以安全覆盖跨集群 Prometheus。保留为部署层补充，不作为应用层授权替代。

### 为 Prometheus 配置长期静态 Bearer Token

实现简单，但缺少标准 client 生命周期、短 TTL 和细粒度 scope，泄漏后的处置窗口不可控。拒绝。

### 复用 introspection 或普通业务 client

会把 Token 状态查询、tenant 业务能力和运行指标访问耦合到同一凭据，破坏最小权限与独立轮换。拒绝。

## 决策

1. 两个发行物增加 Prometheus registry，并只额外暴露 `/actuator/prometheus`；`health` 与 `info` 的现有可见性保持不变，其他 Actuator endpoint 不扩大 exposure。
2. `/actuator/prometheus` 只接受已验证 JWT，且必须同时满足：`actor_type=SERVICE`、不存在 `tenant_id` claim、拥有 `platform.metrics.read` scope。人员 Token、tenant-bound 服务 Token、普通业务 client 和 introspection client全部拒绝。
3. `ainer-starter-security` 提供可复用的 tenantless service-scope authorization manager。`ainer-server` 在现有 Resource Server filter chain 中应用它；不得通过新增应用级默认链绕过 starter 的主链。
4. 即使显式设置 `ainer.security.resource-server.enabled=false`，starter 的 opt-out filter chain 也必须拒绝 `/actuator/prometheus`。本机匿名业务调试不等于匿名监控授权。
5. Authorization Server 使用独立的无状态 metrics filter chain 验证自己签发的 JWT；协议端点、内部 API、metrics 与浏览器登录分别保持明确顺序和匹配范围。
6. Authorization Server 提供默认关闭、一次性的 metrics client bootstrap。它只创建无 tenant、仅 `platform.metrics.read`、只支持 `client_credentials`、access token TTL 为 1 分钟的 registered client；不得增加 introspection 标记或业务 scope。
7. Prometheus 使用标准 OAuth 2.0 Client Credentials 配置和 secret file 获取短时 Token，不保存固定 Bearer Token。生产还应叠加 TLS、受控网络入口和 secret manager。
8. metrics client 与 introspection client 使用不同 client ID/secret。现阶段可通过“创建新 client ID、切换抓取、验证成功、再停用旧 client”的蓝绿方式准备轮换；由于受审计 Client 管理控制面尚未完成，本 ADR 不宣称旧 client 已能安全在线退役。
9. Authorization Server 多实例共享 PostgreSQL protocol/Identity 状态是 HA 前提，不是完整 HA 证据。生产接受前仍必须验证至少两实例、滚动更新、节点中断、数据库故障/恢复、JWK 一致性、Token 签发/introspection/metrics 和高风险 API 失败关闭。

## 后果

### 正面

- 现有安全与业务指标获得标准 Prometheus 导出路径；
- 指标访问与 tenant、人员、业务 client、introspection 凭据实现最小权限隔离；
- Resource Server 安全关闭模式不会把指标端点一起匿名打开；
- Prometheus 使用短时标准 Token，为后续受审计 client 轮换保留兼容路径。

### 负面与风险

- Prometheus 抓取依赖 Authorization Server Token endpoint，授权服务故障会同时影响新 Token 获取；抓取端应在有效期内复用 Token，但不得把缓存写成长效静态凭据；
- Authorization Server 指标认证依赖自身 JwtDecoder 与签名 key 一致性，错误轮换可能导致监控与业务同时失效；
- 当前 bootstrap 只创建、不更新也不停用 client，完整生命周期仍依赖后续控制面；
- 尚无生产 dashboard、告警路由、容量或多节点故障证据。

## 安全、数据与隐私

metrics Token 不携带 tenant 或业务 scope，secret 只能从 secret store 或只读 secret file 注入。日志、错误、指标和文档不得包含 secret 或完整 Token。Prometheus endpoint 可能暴露类名、连接池、路径和资源使用情况，应用层授权不能替代 TLS、网络隔离和最小化标签；指标标签不得加入 tenant、subject、client secret、Token、prompt 或客户正文。

## 运维与迁移

先发布代码和新 metrics client bootstrap，保持 Prometheus 未接入；在受控窗口创建独立 client 后立即移除 bootstrap secret。随后验证无 Token 401、错误主体/tenant/scope 403、专用 client 200，再配置 Prometheus OAuth2 抓取。上线 dashboard 与告警前不得把 exporter 存在写成生产可观测性完成。

回滚时先停止抓取并从 exposure 移除 `prometheus`；保留 client 记录以便审计，待 Client 控制面具备停用能力后退役。不得通过公开端点或复用业务凭据恢复监控。

## 验收证据

2026-07-23 已完成：

- tenantless SERVICE + `platform.metrics.read` 的授权单元与真实 HTTP 200；
- 缺失 Token 401，USER、tenant-bound SERVICE、缺 scope 均 403；
- Resource Server 显式关闭时指标仍拒绝；
- metrics bootstrap 的固定 scope、无 tenant、短 TTL、幂等和弱 secret 失败关闭；
- 两个发行物均包含 Prometheus registry，且 exposure 未扩大到其他 endpoint；
- macOS Colima/Testcontainers 环境中的完整 Reactor 门禁实际执行全部 PostgreSQL 集成组；
- Authorization Server 真实 PostgreSQL 协议测试实际验证专用/tenant-bound metrics Token 对 exporter 的 401/403/200，并覆盖 Client Credentials、OIDC discovery、专用 introspection、RFC 7009 与 Identity revocation epoch。

最新动态测试数量与环境版本记录在 [`project-status.md`](../project-status.md)。未完成项仍包括：独立发布候选环境门禁、真实 Prometheus OAuth2 抓取、多节点容量/故障注入、dashboard/告警路由和旧 client 退役证据。完成前本 ADR 保持 Proposed。

## 参考

- [Spring Boot：Metrics 与 Prometheus](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Spring Boot：Actuator Endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [Spring Security：Authorize HTTP Requests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
- [Prometheus：Configuration / OAuth2](https://prometheus.io/docs/prometheus/latest/configuration/configuration/)
- [Prometheus：Security Model](https://prometheus.io/docs/operating/security/)
- [ADR-0011：高风险 API 选择性在线 Token 校验](0011-selective-online-token-validation.md)
