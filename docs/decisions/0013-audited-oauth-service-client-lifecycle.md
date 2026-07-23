# ADR-0013：受审计 OAuth 服务客户端生命周期

- 状态：Proposed
- 日期：2026-07-23
- 决策者：Ainer 核心维护者
- 取代：无
- 被取代：无

## 背景

Ainer 已通过 Spring Security Authorization Server 官方 JDBC repository 支持 Client
Credentials，并提供业务、introspection 与 metrics 三类默认关闭的初始化 bootstrap。bootstrap
只适合空环境首次建立信任：它接收外部 secret、只创建不更新，也没有在线退役和操作审计。随着
Ainer 作为长期维护和商业交付脚手架发展，日常租户服务客户端不能继续依赖改环境变量和重启完成
创建与轮换。

直接开放 `oauth2_registered_client`、复用 Dynamic Client Registration，或允许任意 grant、
redirect URI 和 scope，会把协议存储细节变成不受约束的高权限管理 API。浏览器/OIDC client、
平台级 metrics/introspection client 与 tenant-bound 机器 client 的风险也不同，不应在第一个
写控制面中混合。

## 决策驱动因素

- 明文 client secret 不能进入数据库、审计、日志、错误或后续查询；
- 新环境需要可审计、可轮换、可退役的日常 tenant 服务客户端；
- 轮换必须允许新旧凭据并行验证，避免原地覆盖造成无回滚窗口；
- 退役既要阻止新 Token，也不能破坏 Spring 官方 authorization 历史记录的读取；
- 控制面本身必须默认关闭，并防止 tenant 服务或人员 Token 自我扩权；
- 平台级、跨 tenant 和浏览器 client 在有独立威胁模型前保持关闭。

## 备选方案

### 直接提供 `oauth2_registered_client` CRUD

实现最短，但把 grant、认证方法、redirect URI、client settings 和 JSON 序列化细节暴露给调用方，
也很难稳定约束危险组合。拒绝。

### 原地更新同一 client secret

数据结构简单，但切换瞬间旧实例会失败，无法先验证新凭据再退役旧凭据，也会让事故回滚依赖再次
写 secret。拒绝。

### 删除已退役的 registered client

可以阻止认证，但会破坏 authorization/consent 历史关联和协议调查证据。拒绝。

### 立即开放浏览器、平台与租户 client 的统一控制台

长期可能需要，但 redirect URI、PKCE、public/confidential client、同意页、跨 tenant scope 和
平台职责分离需要不同策略。当前证据不足，延后。

## 决策

1. 新增默认关闭的 `/internal/oauth-service-clients` 控制面，只管理 tenant-bound、
   `client_credentials` + `client_secret_basic` 服务客户端。tenant ID 必须是 UUID，不接受
   Authorization Code、Refresh Token、redirect URI 或 public client。
2. 调用者必须同时满足：已验证 JWT、`actor_type=SERVICE`、无 `tenant_id`、
   `oauth.clients.manage` scope，以及配置中的精确 operator client ID 白名单。scope 是必要条件，
   不是 operator 身份的替代。
3. 可授 scope 使用启动配置白名单；`oauth.clients.manage`、`token.introspect`、
   `platform.metrics.read` 和所有 `.all` scope 永久排除在本控制面之外。空 operator 或空/危险
   scope 配置使应用启动失败。
4. secret 由 Authorization Server 使用 `SecureRandom` 生成 32..64 字节随机值，再以
   Base64 URL 无填充编码。数据库只保存 `PasswordEncoder` 哈希；明文只在创建或轮换成功响应中
   返回一次，普通读取永不返回。
5. access token TTL 限制在 30 秒至 15 分钟，默认 5 分钟；client secret 有效期限制在 1 至
   365 天，默认 90 天。过期由官方 client authentication 处理。
6. 轮换创建新的 client ID，并记录 `replaces_client_id`。旧 client 保持 ACTIVE，直到调用方完成
   部署切换和验证后显式退役；不提供原地 secret 覆盖。
7. Ainer 使用独立 `ainer_oauth_service_client` 表保存 ACTIVE/RETIRED 生命周期，不修改 Spring
   官方 schema。`RegisteredClientRepository.findByClientId` 对已退役 client 返回不存在，阻止
   新认证；`findById` 仍允许官方 JDBC authorization 重建历史记录。
8. authorization service 在 Token 查找时额外检查 client 生命周期。已退役 client 的历史 Token
   对 introspection 返回 inactive，Refresh/授权 Token 查找也不再可用。已经发出的自包含 JWT 在
   只做离线验签的低风险 Resource Server 上仍可能存活到短 TTL 到期；退役不能被描述成全网瞬时
   删除 JWT。
9. 创建、轮换和退役与操作审计在同一事务提交。审计包含 operation、client ID、关联 client ID、
   tenant、operator service ID、request ID、受限 `changeReference` 和时间，不包含 secret 或
   Token。
10. 退役使用状态与 version 条件更新，不提供重新激活和物理删除。恢复采用创建新 client 的方式，
    保留原审计事实。

## 后果

### 正面

- 日常 tenant 服务 client 不再需要把调用方选择的明文 secret 放入启动配置；
- 蓝绿轮换允许先部署和验证新凭据，再退役旧凭据；
- 退役对新 Token 与在线 introspection 立即生效，同时保留官方协议历史；
- 控制面权限、可授 scope 和生命周期拥有明确的失败关闭边界。

### 负面与风险

- 调用方必须安全接收一次性 secret 并立即写入自己的 secret store；响应中断后只能重新创建新
  client，不能找回旧明文；
- 每次 registered client 认证与 Token 在线查询增加一次生命周期表读取；
- 离线 Resource Server 上的既有 JWT 仍有自然到期窗口；
- 当前没有列表分页、审计导出、双人审批、平台 client 纳管或浏览器 client 管理 UI。

## 安全、数据与隐私

控制面不得记录请求/响应 body。入口网关、APM 和审计代理也必须对 `clientSecret` 字段禁采集；
应用内 `IssuedClient.toString()` 固定脱敏不能替代外围日志策略。operator 凭据只用于控制面，不得
兼任业务、metrics、introspection 或恢复审批 client。

生命周期与审计表归 Authorization Server 身份库所有。审计当前仍是同库记录，不是 WORM、签名
证据或法律意义的不可抵赖；外部不可变副本和保留策略另行设计。

## 运维与迁移

已有 bootstrap/manual client 没有生命周期行，继续按 ACTIVE 兼容读取，但不能通过本控制面查询、
轮换或退役。新 tenant 服务 client 应逐步迁移为新 ID：控制面创建、写入调用方 secret store、
灰度切换、验证 Token 与业务调用、退役旧 managed client。

平台级 metrics/introspection、`.all` 跨 tenant client 和控制面 operator 仍通过各自独立受控
bootstrap 或未来专门流程建立；operator bootstrap 只授予 `oauth.clients.manage`、无 tenant、
一分钟 access token。本 ADR 不宣称这些 bootstrap 凭据已可由当前 API 退役。

## 验收证据

2026-07-23 已完成：

- 空 PostgreSQL 18.3 执行官方 OAuth schema 与 Ainer 生命周期/审计 migration；
- 创建后数据库只有 password hash，明文只在首次响应出现；
- 未列入白名单的 scope 返回稳定 422，tenant-bound operator 返回 403；
- operator bootstrap 只创建 tenantless `oauth.clients.manage`、一分钟 Token，弱 secret 失败关闭；
- 新 ID 轮换期间新旧 client 都可签发，显式退役后旧 client 获取 Token 返回 401；
- 退役 client 的既有 Token introspection 为 inactive；
- Spring JDBC authorization 仍能读取退役 client 的历史授权，不产生 500；
- 创建、轮换、退役审计不含 secret 字段。

最新完整测试数量记录在 [`project-status.md`](../project-status.md)。未完成项包括 operator 职责分离
的生产证据、外围响应日志脱敏验证、审计外部副本、平台 client 生命周期、浏览器/OIDC client
控制面和 Authorization Code + PKCE 端到端。本 ADR 在评审这些边界前保持 Proposed。

## 参考

- [ADR-0005：Identity 与 OAuth 2.1 安全基线](0005-identity-and-oauth2-security-baseline.md)
- [ADR-0011：高风险 API 选择性在线 Token 校验](0011-selective-online-token-validation.md)
- [ADR-0012：生产指标访问与 Authorization Server 可用性边界](0012-production-observability-and-auth-availability.md)
- [Spring Security Authorization Server：Core Model / RegisteredClient](https://docs.spring.io/spring-security/reference/7.1/servlet/oauth2/authorization-server/core-model-components.html)
