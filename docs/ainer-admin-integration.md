# Ainer Admin 与 Ainer Boot 集成手册

> 文档类型：前后端集成与运行契约 · 状态：MVP 基线 · 最近核对：2026-07-26 · 适用版本：`0.1.x`

本文定义 Ainer Admin 第一版如何通过同源入口接入 Ainer Boot。它是前端实现、反向代理配置、
开发联调和端到端验收的共同契约，不替代
[`ainer-admin-v1.yaml`](../ainer-authorization-server/src/main/openapi/ainer-admin-v1.yaml)
中的机器可读 API 定义。

产品边界固定为：

- **Ainer Boot**：后端、认证、tenant、权限与 API；
- **Ainer Studio**：Blocks、Templates、预览与 Registry；
- **Ainer Admin**：Ainer Studio 交付的第一个完整 Admin Dashboard Template，也是连接
  Ainer Boot 的官方参考管理应用；唯一前端源码位于
  `ainer-studio/templates/ainer-admin`。

设计依据见
[`ADR-0022`](decisions/0022-ainer-admin-browser-integration-baseline.md)。

## 1. MVP 固定契约

| 项目 | 固定值或规则 |
|---|---|
| 产品名 | Ainer Admin |
| 部署基址 | `/ainer-admin/` |
| browser client ID | `ainer-admin-dev` |
| 登录回调 | `/ainer-admin/auth/callback` |
| 退出回调 | `/ainer-admin/auth/logged-out` |
| OAuth grant | Authorization Code |
| PKCE | 必须为 S256 |
| Scopes | `openid profile tenant.members.read tenant.members.write` |
| tenant | 只使用 access token 的 default `tenant_id` |
| tenant 角色 | 固定 `OWNER / ADMIN / MEMBER` |
| Token 保存 | access token 与 ID token 只保存在页面内存 |
| Refresh Token | 不注册、不签发、不保存 |
| 网络拓扑 | 同源反向代理，不启用全局 CORS |

第一版明确不实现 tenant selector、自定义 RBAC、role-permission matrix、新用户邀请、审计查询
页面和 Refresh Token。成员新增只接受 Identity 中已经存在的用户；通用成员 API 不能创建、
授予、修改或移除 OWNER。

## 2. 同源入口与反向代理

浏览器只能看到一个公开 HTTPS origin，例如 `https://admin.example.test`。Ainer Admin 静态文件、
OAuth/OIDC 协议端点与成员 API 都通过该 origin 访问：

```text
browser
  |
  +-- /ainer-admin/** --------------------------> Ainer Admin static/SPA
  |
  +-- /.well-known/**, /oauth2/**, /login ------> ainer-authorization-server
  +-- /ainer-login/tokens.css ------------------> ainer-authorization-server
  +-- /ainer-login/login.css -------------------> ainer-authorization-server
  +-- /connect/logout, /api/me/** --------------> ainer-authorization-server
  +-- /api/tenants/*/members** -----------------> ainer-authorization-server
```

代理规则至少满足：

1. `/ainer-admin/**` 由前端静态服务处理，SPA fallback 只能限制在这个前缀内；
2. `/.well-known/**`、`/oauth2/**`、`/connect/logout`、`/login`、`/login/**`、
   `/error`、精确 `/ainer-login/tokens.css` 与精确 `/ainer-login/login.css` 转发到
   Authorization Server；`/default-ui.css` 只为回滚到 M6 前 release 保留；
3. `/api/me/access-token-revocations` 和 `/api/tenants/*/members**` 转发到
   Authorization Server，不能被 SPA fallback 改写为 `index.html`；
4. 启用 Passkey 时再把 `/webauthn/**` 与 `/login/webauthn` 转发到同一个
   Authorization Server；
5. 保留外部 `Host`、scheme 和客户端转发信息，Authorization Server 的公开 issuer 必须与浏览器
   实际使用的 HTTPS origin 一致；不得把容器名或内部 HTTP 地址写入重定向；
6. 原样转发 `Set-Cookie`、`Cookie`、表单 body、状态码和 `Location`，不能把 session cookie
   限制到仅 `/oauth2`；
7. 协议端点、API 以及 `/ainer-admin/auth/**` 使用 `Cache-Control: no-store`；代理、APM 和
   access log 不记录 authorization code、`code_verifier`、Bearer Token、ID token 或密码；
8. 第一版不增加全局 CORS。出现跨域请求说明入口或 SDK `basePath` 配置发生漂移，应修复路由，
   而不是放宽 Origin。
9. 禁止增加 `/ainer-login/**`、通用静态目录或 `/api/**` catch-all；登录资源与成员 API 都必须
   维持可审计的精确代理边界。

Authorization Server 把 OIDC ID token 的 `sid` 与登录 HTTP session 绑定。浏览器必须在同一个
cookie session 中完成登录、授权、Token 交换和 `/connect/logout`；不能用一个脱离浏览器 cookie
jar 的后端脚本交换 code，再期待原浏览器会话完成 RP-Initiated Logout。

## 3. 登录与回调

公开 `GET /login` 由 Ainer Boot 服务端渲染，不属于 Ainer Admin React 路由。页面固定提交
`POST /login` 的 `username`、`password` 与服务端生成的 CSRF 参数；Admin 不读取凭据。
普通认证失败统一回到 `/login?error`，不区分未知账号与错误密码；明确的认证基础设施异常映射为
一次性 `503` 页面；声明接受 HTML 的登录提交被限速时原位返回 `429` 与 `Retry-After`。这些
HTML、错误和重定向均 `no-store`，且不改变后续 SavedRequest 恢复的 OAuth 授权请求。

品牌登录页面源自 Ainer Studio commit
`a73f40b77b33f4591fd5eadcc7c3fd4bb8f430b8` 的视觉合同 `1.0.0`：

- `contract.json` SHA-256：
  `e8e50c266957c7fe14af4b4e30508dd6fe52f43c12029261d8a44e5d51ce2786`；
- `tokens.css` SHA-256：
  `2a8eeed8d598ebc647163662a7de8f7bb0d0ce2e3a171e2392e638ba75c095d8`。

Boot 固定复制这两份资源，不运行 Studio React/Ant Design 预览。合同 1.0.0 不显示 Passkey
动作；Boot 仍保留 WebAuthn 协议端点、条件 MFA 过滤器与 factor query 上下文，但需要可见 Passkey
交互的环境必须等待 Studio 新合同，不能由 Boot 自行设计入口。

前端开始登录时生成高熵 `state`、`nonce` 和 `code_verifier`，只把它们短时放入
`sessionStorage`。`code_challenge` 使用 SHA-256 与 base64url-no-padding 计算。授权请求固定包含：

```text
GET /oauth2/authorize
  ?response_type=code
  &client_id=ainer-admin-dev
  &redirect_uri={origin}/ainer-admin/auth/callback
  &scope=openid%20profile%20tenant.members.read%20tenant.members.write
  &state={random}
  &nonce={random}
  &code_challenge={S256(code_verifier)}
  &code_challenge_method=S256
```

回调页必须先验证 query 中的 `state`，再以
`application/x-www-form-urlencoded`、`credentials: "same-origin"` 调用：

```text
POST /oauth2/token

grant_type=authorization_code
client_id=ainer-admin-dev
code={authorization_code}
redirect_uri={origin}/ainer-admin/auth/callback
code_verifier={original_code_verifier}
```

交换成功后立即删除 `state`、`nonce` 与 `code_verifier`。校验 ID token 的 issuer、audience、
nonce、签名与时间边界；access token 和 ID token 只留在内存，不进入 `localStorage`、
`sessionStorage`、IndexedDB、URL 或日志。响应中出现 Refresh Token 应视为服务端策略漂移并拒绝
继续。

Ainer Admin 从已验证 access token 取得 `tenant_id` 作为唯一 default tenant，并把它写入成员
API 路径。前端解析 claim 只用于路由和展示，不构成服务端信任边界；服务端仍会校验路径 tenant、
USER actor、scope、实时成员角色与 access token active 状态。

页面刷新会丢失内存 Token，第一版应重新开始 Authorization Code 流程。已有 Authorization Server
session 时可以无须再次输入密码，但不能因此引入前端持久化 Token。

## 4. 成员 API 与 TypeScript SDK

MVP 使用以下 JSON API：

| Method | Path | 用途 |
|---|---|---|
| GET | `/api/tenants/{tenantId}/members?page=1&size=20` | 成员列表 |
| POST | `/api/tenants/{tenantId}/members` | 添加已有用户为 ADMIN/MEMBER |
| PATCH | `/api/tenants/{tenantId}/members/{subjectId}` | ADMIN/MEMBER 双向调整 |
| DELETE | `/api/tenants/{tenantId}/members/{subjectId}?reasonCode=...` | 软移除 |
| POST | `/api/me/access-token-revocations` | 撤销当前 access token |

字段、响应 envelope、稳定错误码与约束以
[`ainer-admin-v1.yaml`](../ainer-authorization-server/src/main/openapi/ainer-admin-v1.yaml)
和 [`api.md`](api.md) 为准。前端不得手抄另一份 DTO。

从 Ainer Boot 根目录生成 TypeScript SDK：

```bash
./mvnw -pl ainer-authorization-server -Painer-admin-sdk generate-resources
```

输出位于：

```text
ainer-authorization-server/target/generated-sources/ainer-admin-typescript/
```

生成目录是可丢弃构建产物，不提交到 Ainer Boot。Ainer Studio 应按自己的包管理流程消费或发布。
OpenAPI 使用相对 `/` 表达同源入口，但 OpenAPI Generator 会把这个合法相对 server 写成
`http://localhost` 兜底值，因此运行时必须覆盖 `basePath`：

```ts
import {
  Configuration,
  CurrentSessionApi,
  TenantMembersApi,
} from '@ainer/admin-sdk'

const configuration = new Configuration({
  basePath: window.location.origin,
  accessToken: async () => `Bearer ${accessTokenStore.require()}`,
  credentials: 'same-origin',
})

export const tenantMembersApi = new TenantMembersApi(configuration)
export const currentSessionApi = new CurrentSessionApi(configuration)
```

不要把内部 Authorization Server 地址配置为浏览器 `basePath`，也不要因配置错误而启用 CORS。
SDK 只生成 JSON API client；OAuth/OIDC 登录与 logout 继续按标准协议实现。

## 5. revoke 与 logout

退出必须同时处理 access token 活性、Authorization Server session 和浏览器内存：

1. 在内存清理前暂存当前 ID token，并生成新的 logout `state`；只把该 state 短时写入
   `sessionStorage`；
2. 使用当前 Bearer 调用 `POST /api/me/access-token-revocations`；
3. 200 表示当前 access token 已失效；401 可按“已经失效”继续；503 或网络失败表示服务端撤销
   未确认，但不能把用户困在页面内；
4. 无论撤销结果如何，都在同一浏览器 session 导航到：

```text
/connect/logout
  ?id_token_hint={id_token}
  &post_logout_redirect_uri={origin}/ainer-admin/auth/logged-out
  &state={logout_state}
```

5. 发起导航后清理 access token；退出回调再次验证 state，并无条件清理全部 Token、临时登录状态
   和敏感页面数据；
6. 撤销依赖失败或 OIDC logout 未完成时，界面明确显示“本地已退出，但服务端退出未完全确认”，
   上报不含 Token 的诊断信息。

自助撤销只失效 access token，不失效同一 authorization 的 ID token；这是先 revoke、再用
`id_token_hint` 完成标准 logout 的原因。撤销成功后不得再用旧 access token 重试成员 API。

## 6. 安全的开发初始化

client 与身份 fixture 都只在 `dev` profile、显式开关下运行。下面只展示占位值：

```bash
export SPRING_PROFILES_ACTIVE=dev
export AINER_ADMIN_BROWSER_CLIENT_ENABLED=true
export AINER_ADMIN_BROWSER_CLIENT_REDIRECT_URI=http://127.0.0.1:5173/ainer-admin/auth/callback
export AINER_ADMIN_BROWSER_CLIENT_POST_LOGOUT_REDIRECT_URI=http://127.0.0.1:5173/ainer-admin/auth/logged-out

export AINER_ADMIN_DEV_BOOTSTRAP_ENABLED=true
export AINER_ADMIN_DEV_OWNER_USERNAME=owner@example.test
export AINER_ADMIN_DEV_OWNER_PASSWORD='<secret-store-value>'
export AINER_ADMIN_DEV_OWNER_DISPLAY_NAME='Admin Owner'
export AINER_ADMIN_DEV_MEMBER_USERNAME=member@example.test
export AINER_ADMIN_DEV_MEMBER_PASSWORD='<different-secret-store-value>'
export AINER_ADMIN_DEV_MEMBER_DISPLAY_NAME='Existing User'
```

OWNER 的 default tenant code 为 `ainer-admin-dev`。第二用户属于独立
`ainer-admin-member-home` default tenant，初始不属于 Admin 主 tenant，因此可真实验证“添加已有
用户”。两个账号都能登录，但添加成员时必须使用 OWNER Token 的 `tenant_id`。

fixture 严格幂等：完整状态匹配时不覆盖密码，部分占用或状态漂移时启动失败。首次初始化成功后应
关闭 fixture 开关并从进程环境移除明文密码。该 fixture、client ID 和账号不得用于生产开户。
全部配置键与失败条件见 [`configuration.md`](configuration.md)。

## 7. 验收与测试记录

关键端到端门禁：

```bash
./mvnw -pl ainer-authorization-server -am \
  -Dtest=AinerAdminBrowserIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Docker-compatible runtime 不能被 Maven 自动发现时，显式指定本机 socket：

```bash
DOCKER_HOST=unix:///absolute/path/to/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./mvnw -pl ainer-authorization-server -am \
  -Dtest=AinerAdminBrowserIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

该测试使用同一个 `ainer-admin-dev` browser session 和真实 PostgreSQL 18.3，覆盖：

- 表单登录、Authorization Code + PKCE S256、code exchange；
- default tenant、OWNER、四个固定 scope 和无 Refresh Token；
- 成员列表、添加已有用户、MEMBER → ADMIN → MEMBER、软移除与成员审计；
- 当前 access token 撤销、旧 Token 被 active gate 返回 401；
- ID token 保持可用于 `/connect/logout`，精确回到
  `/ainer-admin/auth/logged-out`，并清除登录 session。

形成发布候选前还必须运行 `./mvnw clean verify`，并确认 Testcontainers 用例实际启动、`skipped=0`。本地
缺少 Docker 而产生的自动跳过不能作为 Ainer Admin 后端基线的验收结果。

## 8. 当前风险与后续边界

- `ainer-admin-dev` 是开发 client，不是生产 browser client 注册、轮换和退役控制面；
- SPA 内存仍可能受运行时 XSS 影响；高价值生产场景需要重新评估 BFF、CSP 与会话模型；
- 成员 API 对官方 authorization repository 执行逐请求 active 检查，Authorization Server
  数据库故障会返回 503 并失败关闭；
- 当前无 tenant selector；用户只能管理 Token default tenant；
- 当前没有成员审计查询 UI，但服务端写操作审计仍会持续落库；
- 同源代理配置尚需在目标 ingress/proxy 产品上进行真实 HTTPS、Cookie、重定向和缓存验收。

## 9. 公开域名与品牌迁移

Ainer Admin 与 Boot 在每个环境继续使用一个规范 HTTPS origin。过渡开发环境建议使用
`https://ainer-dev.xiaoqu99.com`，但该主机名在完成 DNS、证书、代理和真实验收前只能描述为计划，
不能写成已部署事实。

未来取得独立 Ainer 品牌域名后，production 推荐使用 `app.<brand-domain>` 同时承载 Admin、
Authorization Server 协议端点和 API；品牌官网使用 apex，Studio 可在不共享 Admin 登录会话时使用
`studio.<brand-domain>`。域名切换不通过 CORS 或 OAuth 端点的笼统重定向解决，完整拓扑、`.com` /
`.cn` 边界和迁移步骤见
[`public-origin-and-domain-strategy.md`](public-origin-and-domain-strategy.md)。
