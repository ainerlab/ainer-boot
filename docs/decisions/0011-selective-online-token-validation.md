# ADR-0011：高风险 API 选择性在线 Token 校验

- 状态：Accepted
- 日期：2026-07-23
- 决策者：Ainer 核心维护者
- 取代：无
- 被取代：无

## 背景

M4.1/M4.2 已能把 Identity 禁用事实可靠投递到 Workspace，收紧本地资源关系，但 `ainer-server` 仍只离线验证自包含 JWT。已签发 Token 在过期前仍可调用尚未消费事件的 API；对于产生成本、修改授权、导出安全日志或执行恢复的高风险请求，这个窗口不可接受。

Spring Authorization Server 默认提供 RFC 7662 Token Introspection 与 RFC 7009 Token Revocation 端点。Spring Security Resource Server 也明确支持将 JWT 发送到 introspection endpoint，由授权服务器对当前 `active` 状态作最终判定。Ainer 需要在撤销延时、授权服务器可用性、高风险 API 性能和外部 OIDC 兼容之间做出明确取舍。

## 决策驱动因素

- 高风险请求在 Identity 撤销事务提交后不再依赖 JWT 过期时间；
- 无效 Token 与授权服务器不可用必须有不同、可运营的错误语义；
- 在线判定不得依赖单机内存或每节点手工 deny-list；
- introspection 凭据必须是专用服务 client，普通业务 client 不得查询任意 Token 状态；
- 不改变业务模块只依赖 `AuthenticatedActor` 的边界；
- 外部兼容 OIDC 发行物可以通过标准 introspection 配置接入。

## 备选方案

### 所有 API 全量 introspection

撤销语义最简单，但每个普通读请求都引入网络往返和 Authorization Server 故障域，并不符合当前容量证据。本阶段拒绝。

### 只缩短 JWT TTL

能缩小窗口，但不能在账号禁用事务后立即阻止高风险操作。保留为纵深防御，不作为在线撤销替代。

### Resource Server 共享 Redis deny-list

查询快，但需要新的共享状态、投递和过期一致性，且会把发行物绑定到 Redis。当前 Identity 数据库已拥有事务撤销事实与索引，不重复建设第二条真相链。

### 在每个业务 Controller 中手工调用身份服务

容易遗漏新端点，会把 Token 与远程安全协议泄漏到业务层，拒绝。

## 决策

1. `ainer-starter-security` 保留 JWT 签名、issuer、audience 和时间窗口的本地验证；在 Bearer JWT 成功认证后，只对匹配高风险规则的请求再执行 RFC 7662 在线存活检查。业务层继续只看到原 `Jwt` 与 `AuthenticatedActor`。
2. 默认高风险规则包括：所有 `/internal/**`；Workspace 授权审计读取；以及 `/api/workspaces/**`、`/api/ai/**` 上的 `POST/PUT/PATCH/DELETE`。发行物可收紧或扩展路径，但启用时规则不得为空。
3. 在线检查默认关闭。启用时必须配置 HTTPS introspection URI、专用 client ID/secret、连接/读取超时和非空保护规则；仅 loopback 自动化测试允许 HTTP。
4. 高风险请求不对 `active=true` 做正向缓存。这是“在线”语义的必要代价；后续若引入缓存，必须把最大撤销窗口写入 SLO 并新立 ADR。
5. JWT 无效或 introspection 返回 inactive 时清除 SecurityContext 并返回 401 `AINER.COMMON.UNAUTHENTICATED`，不泄漏 Token 不存在、过期或已撤销的具体原因。
6. introspection 超时、连接失败、凭据错误或响应不可解析时失败关闭，返回 503 `AINER.SECURITY.ONLINE_VALIDATION_UNAVAILABLE`。不回退到“JWT 已签名所以继续放行”。
7. Ainer Authorization Server 使用官方 JDBC `OAuth2AuthorizationService`。RFC 7009 对单 Token 的撤销继续使用官方端点与授权元数据，不创建自研 Token 表。
8. 对 `actor_type=USER` 的 Ainer Token，Authorization Server 在 introspection/刷新查找时还检查 Identity tenant/user/membership 当前状态，以及 `(tenant_id, subject_id)` 最新 access-event 时间。Token `issuedAt <= latestRevokedAt` 时视为失效；这使现有与 Identity 状态同事务提交的 access event 成为 revocation epoch。
9. Authorization Server introspection 只允许 registered client 显式设置 `ainer.introspection-allowed=true`。普通业务 client 即使有 client secret 也不得查询任意 Token；Resource Server 必须使用无业务 scope 的专用 introspection client。
10. 指标至少区分在线放行、inactive 拒绝、依赖失败和调用延时。指标和错误不得包含 Token、client secret 或 introspection 原始响应。

## 后果

### 正面

- Identity 禁用/成员撤销事务提交后，Ainer 高风险 API 不再等待 JWT 自然过期；
- 继续使用标准 RFC 7662/7009 与 Spring Security 组件，不影响普通 JWT 发行和业务身份投影；
- 外部 OIDC 发行物只要提供受保护 introspection endpoint 就能接入同一 Resource Server 边界；
- 不新增单机 deny-list 或自研 Token 表。

### 代价与风险

- 每个高风险请求增加一次 Authorization Server 网络与数据库查询；
- Authorization Server 成为高风险 API 的在线依赖，必须建立容量、超时、高可用和告警证据；
- 当前 revocation epoch 查询依赖 Authorization Server 与 Identity 数据在同一发行物/数据库边界；未来拆分时需要独立、可靠的撤销状态投影。
- 普通低风险 JWT API 仍有自然过期窗口，本决策不宣称“所有 API 强实时撤销”。

## 安全、数据与隐私

Resource Server 只把原 Bearer Token 发送到配置的 HTTPS introspection endpoint。Token 不写入日志、指标、审计或错误正文。inactive 统一返回 401，防止状态枚举。introspection client secret 只能从 secret store 注入。

Identity 查询只使用 tenant、subject、当前状态和撤销时间，不读取密码哈希、用户名、Token 正文或客户数据。

## 运维与迁移

先发布 Authorization Server 的 revocation-aware authorization service 与专用 introspection client 限制，再在 Resource Server 保持开关关闭的情况下验证 active/inactive/不可用三类路径。只在 introspection client、TLS、超时、指标和告警完成配置后启用。

回滚时先关闭 Resource Server 在线检查，不删除 OAuth authorization 元数据或 Identity access event。这个回退会恢复 JWT 自然过期窗口，必须被视为安全降级并有独立批准。

## 验收证据

2026-07-23 已完成：

- Resource Server 自动化测试覆盖低风险不调用、高风险 active、无正向缓存、inactive 401、依赖失败 503、无 Bearer 401 和四类 Micrometer 指标；
- 配置测试覆盖默认关闭、HTTPS、loopback HTTP 例外、secret/超时/规则约束；
- Authorization Server 自动化测试覆盖专用 client、普通 client、带 tenant 或业务 scope 的伪专用 client、RFC 7009 与人员 Identity 状态包装；
- Identity 测试覆盖缺失/非 ACTIVE 身份，以及 `issuedAt` 在 epoch 之前、相等和之后的边界；
- 本机 PostgreSQL 18.4 从空库执行五份 Authorization Server migration 并启动真实发行物，协议 smoke 得到普通 introspection 401、专用 `active=true`、revocation 200、撤销后 `active=false`；
- 5,000 条合成 access event 下，epoch 查询使用现有 `idx_ainer_identity_access_event_subject` Index Only Scan，实测约 0.036 ms；未新增 Token 表或 migration；
- 当轮完整 `mvn clean test` 的 14 个 Reactor 模块成功；本机无 Docker 时数据库组按项目规则明确
  跳过。当前不跳过的总测试数量只在 [`project-status.md`](../project-status.md) 维护。

以上证据支持接受本决策与工程基线。生产发行仍必须在 Docker 可用的发布候选环境执行未跳过的 PostgreSQL 集成测试，并补齐高可用、容量、告警和凭据轮换证据；Accepted 不等于生产运营已完成。

## 参考

- [RFC 7662：OAuth 2.0 Token Introspection](https://www.rfc-editor.org/rfc/rfc7662)
- [RFC 7009：OAuth 2.0 Token Revocation](https://www.rfc-editor.org/rfc/rfc7009)
- [Spring Security：Opaque Token / JWT Introspection](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/opaque-token.html)
- [Spring Authorization Server：Configuration Model](https://docs.spring.io/spring-authorization-server/reference/configuration-model.html)
- [ADR-0008：Identity Directory 与访问撤销传播边界](0008-identity-directory-and-access-revocation.md)
- [ADR-0009：跨运行时 Directory 与访问撤销投递](0009-cross-runtime-access-revocation-delivery.md)
