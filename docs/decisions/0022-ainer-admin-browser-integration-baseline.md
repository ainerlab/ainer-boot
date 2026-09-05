# ADR-0022：Ainer Admin 浏览器集成基线

- 状态：Accepted
- 日期：2026-07-26
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无
- 修订：ADR-0055 退役 Ainer Studio 产品线；`templates/ainer-admin` 参考实现已随 Studio
  仓退役（唯一留存物为维护者冷备份），但本文的 PKCE/SDK/同源代理契约继续有效，
  作为消费者自建管理面的规范

## 背景

Ainer Boot 已具备 Authorization Code + PKCE 测试基线和 tenant 成员管理 API，但尚无供真实前端
使用的 browser public client、可测试开发身份、当前 Token 自助撤销、成员 API 在线活性门禁或
生成式 API 契约。Ainer Studio 已确定第一个完整管理应用模板，后端需要为其提供一条边界明确、
不提前扩大权限模型的参考链路。

产品职责确定为：

- Ainer Boot：后端、认证、tenant、权限和 API；
- Ainer Studio：Blocks、Templates、预览和 Registry；
- Ainer Admin：Ainer Studio 交付的第一个完整 Admin Dashboard Template，也是连接 Ainer Boot
  的官方参考管理应用，唯一源码位于 `ainer-studio/templates/ainer-admin`。

## 决策驱动因素

- 浏览器客户端不能持有 client secret，必须强制 Authorization Code + PKCE S256；
- 第一版只证明 default tenant 的最小成员治理链路，不引入 tenant selector 或自定义 RBAC；
- access token 需要在退出时立即失去成员 API 访问能力，不能只清理浏览器内存；
- 前后端第一版采用同源反代，避免同时扩大 CORS、Cookie 和 Origin 信任面；
- 开发夹具必须默认关闭、不含默认密码，并能真实验证“添加已有用户”；
- 前端需要稳定 OpenAPI 和 TypeScript SDK 生成入口，不能依赖手抄 DTO。

## 备选方案

### 使用带 secret 的 browser client

SPA 无法安全保存 client secret，拒绝。

### 第一版直接启用 Refresh Token

会立即引入长期浏览器会话、轮换、重放检测和持久化策略；当前 MVP 只保存内存 access token，
拒绝在第一版启用。

### 允许前端跨域调用并开放全局 CORS

会同时增加 Origin 白名单、预检、Cookie 与代理差异。第一版由部署入口提供同源反代，拒绝全局
CORS。

### 为开发第二用户直接创建无 tenant 账号

现有登录只从 default membership 解析 tenant；无 tenant 用户无法完成正常授权码登录。采用独立
helper tenant 作为第二用户默认落点，但不把该用户预先加入 Ainer Admin 主 tenant。

## 决策

1. browser public client 固定使用 `client_id=ainer-admin-dev`，只支持
   `authorization_code`，认证方式为 `none`，强制 PKCE S256。
2. redirect URI 为同源绝对地址的 `/ainer-admin/auth/callback`，post logout redirect URI 为
   `/ainer-admin/auth/logged-out`。HTTP 只允许 loopback 开发地址；生产形态必须使用 HTTPS。
3. client scopes 固定为 `openid`、`profile`、`tenant.members.read`、
   `tenant.members.write`。不注册 Refresh Token grant，不签发 refresh token。
4. Ainer Admin 只使用 Token 中由 Identity default membership 签发的 `tenant_id`；不实现
   tenant selector，也不接受前端覆盖 tenant claim。
5. tenant 角色继续固定为 `OWNER/ADMIN/MEMBER`。不新增自定义角色、permission matrix、邀请新用户
   或审计查询 UI。
6. access token 只保存在前端内存。登录事务的 `state`、`nonce`、`code_verifier` 可以短时保存在
   `sessionStorage`，回调完成后必须删除；不得进入 URL、日志或持久化业务存储。
7. Ainer Boot 提供 `POST /api/me/access-token-revocations`。端点只接受当前已认证 USER Bearer，
   不接受任意 token 参数，使用官方 `OAuth2AuthorizationService` 把当前 access token 标记为
   invalidated。
8. tenant 成员 API 在本地 JWT 验证后，必须再读取官方 authorization 状态；已撤销或 Identity
   当前状态无效返回 401，读取依赖失败返回 503，不回退到仅验证 JWT。
9. 退出顺序为先调用自助撤销，再调用 OIDC RP-Initiated Logout
   `/connect/logout`，携带 `id_token_hint`、已注册的 `post_logout_redirect_uri` 和防重放
   `state`。浏览器无论撤销请求结果如何都必须清理内存 token；依赖故障应向用户显示退出未完全
   确认。
10. `dev` profile 提供默认关闭的 Ainer Admin fixture：主 tenant `ainer-admin-dev` 包含一个
    default OWNER；第二用户以 `ainer-admin-member-home` 为 default OWNER，但初始不是主 tenant
    成员。用户名、显示名和密码均由环境注入，不提供仓库默认密码。
11. 成员 JSON API 与自助撤销进入 `ainer-admin-v1.yaml`。TypeScript SDK 由固定生成入口产生，
    OAuth/OIDC 登录仍交给前端标准协议客户端，不生成自制 OAuth SDK。
12. 部署基址固定为 `/ainer-admin/`。第一版不启用全局 CORS；同源入口反代 OAuth/OIDC、登录和
    成员 API 到 Ainer Boot。

## 后果

### 正面

- Ainer Admin 获得一条可由真实浏览器和 PostgreSQL 证明的最小管理链路；
- 退出同时清理浏览器状态和服务端 authorization 活性；
- 开发夹具可重复初始化且不会把测试密码写入仓库；
- Studio 与 Boot 通过明确 URI、scope、OpenAPI 和代理契约协作。

### 负面与风险

- public SPA 仍暴露于浏览器运行环境风险；高价值生产场景后续应重新评估 BFF；
- access token 只在内存中，页面刷新后需要重新走授权流程；
- Authorization Server 数据库成为成员 API 的在线依赖；
- dev browser client 和 fixture 不是生产 client 控制面，生产注册、轮换和删除仍需独立设计。

## 安全、数据与隐私

client 无 secret，PKCE 只接受 S256。回调和退出 URI 精确匹配，不能使用通配符。fixture 只在
`dev` profile、显式开关和完整凭据同时存在时运行；密码只经 Identity password hasher 持久化，
不得记录明文。自助撤销不接受请求体 token，避免形成任意 token oracle。

## 运维与迁移

本决策不新增业务表或自研 Token 表。部署先建立同源路由，再以 `dev` profile 和显式环境变量执行
一次性 client/fixture 初始化；初始化完成后关闭 fixture 开关。生产不得直接复用
`ainer-admin-dev` fixture 或开发账号。

回滚可关闭 browser client/fixture 开关并下线 Ainer Admin 路由；已存在的 OAuth authorization、
Identity tenant/user/membership 和成员审计不得物理删除。

## 验收证据

必须以同一个 `ainer-admin-dev` browser client 在真实 PostgreSQL Testcontainers 中覆盖：

1. 表单登录与 PKCE S256 code exchange；
2. access/id token 含预期 scope、default tenant 和 OWNER 角色，且没有 refresh token；
3. 成员 GET、添加已有用户、MEMBER/ADMIN 双向调整和软移除；
4. 当前 access token 自助撤销后，旧 token 再访问成员 API 返回 401；
5. OIDC logout 只重定向到已注册的 `/ainer-admin/auth/logged-out`；
6. 关键 Testcontainers 测试实际执行且 `skipped=0`。

## 参考

- [RFC 7636：Proof Key for Code Exchange](https://www.rfc-editor.org/rfc/rfc7636)
- [RFC 7009：OAuth 2.0 Token Revocation](https://www.rfc-editor.org/rfc/rfc7009)
- [OpenID Connect RP-Initiated Logout 1.0](https://openid.net/specs/openid-connect-rpinitiated-1_0.html)
- [ADR-0005：Identity 与 OAuth 2.1 安全基线](0005-identity-and-oauth2-security-baseline.md)
- [ADR-0011：高风险 API 选择性在线 Token 校验](0011-selective-online-token-validation.md)
- [ADR-0018：管理授权模型与租户成员管理](0018-management-authorization-and-tenant-member-management.md)
- [ADR-0019：Identity 供应、租户上下文与所有权治理](0019-identity-provisioning-tenant-context-and-ownership-governance.md)
