# ADR-0005：Identity 与 OAuth 2.1 安全基线

- 状态：Accepted
- 日期：2026-07-22

## 背景

M2 的 AI Gateway 使用请求头显式传入租户和主体，这只能表达调用上下文，不能证明调用者身份。Ainer 要成为可收费、可部署到企业环境的产品，必须在继续扩展 AI、工作流和商业模块前建立可审计、可替换且遵循标准协议的身份边界。

Spring Boot 4.1 管理 Spring Security 7.1，并提供 OAuth 2.0 Resource Server 与 Authorization Server 的官方装配。Ainer 不需要发明 Token 协议，也不应把认证、租户选择和业务权限混为一个全局工具类。

## 决策

1. `ainer-server` 是 OAuth 2.0 Resource Server，只接受标准 Bearer JWT。签发者、JWK 和 audience 通过外部配置声明；启用安全后缺少解码器配置必须启动失败，不允许静默放行。
2. Token 签发使用独立的 `ainer-authorization-server` 应用装配。协议采用 OAuth 2.1 / OpenID Connect，支持 Authorization Code + PKCE、Refresh Token 和 Client Credentials；不支持 Resource Owner Password Credentials。
3. `ainer-security` 只定义稳定的 `AuthenticatedActor` 与解析端口；`ainer-starter-security` 负责从 Spring Security 的认证结果投影主体、租户与权限。业务模块不得直接读取 JWT，也不得信任外部身份请求头。
4. `sub` 是稳定主体标识，`tenant_id` 是当前租户标识。缺少、超长或格式非法的可信声明一律拒绝。多租户用户的租户切换后续通过显式授权流程完成，不能由客户端随意覆盖请求头。
5. Scope 映射遵循 Spring Security 的 `SCOPE_` authority 语义；角色使用 `ROLE_` 语义。业务权限通过方法或端点授权表达，不能只依赖“已经登录”。
6. Identity 数据与 OAuth 协议数据分离：用户、租户和成员关系属于 `ainer-module-identity`；registered client、authorization、consent 和 signing key 属于 Authorization Server 装配。
7. 用户密码只保存 Spring Security delegating password encoder 生成的哈希。项目不提供硬编码管理员、默认密码、`{noop}` 密码或默认 client secret。
8. Authorization Server 的 registered client、authorization 和 consent 使用 JDBC 持久化；内存仓库只允许出现在自动化测试或明确的本地样例中。
9. 签名私钥、client secret 和上游身份源凭证只能从外部 secret 注入，不写入仓库、不进入响应和日志。生产环境不生成临时签名密钥。
10. 安全失败沿用 Ainer 的 HTTP 状态和 `ApiResponse`：未认证为 401，已认证但租户声明无效或权限不足为 403，并始终携带 request ID。

## 模块边界

```text
ainer-module-ai-runtime ──> ainer-security <── ainer-starter-security
                                                │
Bearer JWT ─────────────────────────────────────┘

ainer-authorization-server ──> ainer-module-identity
            │
            └── Spring Security Authorization Server + JDBC protocol stores
```

业务模块依赖的是认证后的参与者，而不是 Spring Security、JWT claim 或授权服务器数据库。这样既允许默认发行物使用 Ainer Authorization Server，也允许企业客户接入 Keycloak、Microsoft Entra ID、Auth0 或其他兼容 OIDC 的身份提供方。

## 首个交付切片

M3 foundation 已按两段落地：

1. 先交付 `ainer-security`、Resource Server starter、JWT 契约与 AI Gateway 的可信身份改造；
2. 再交付 Identity PostgreSQL 模型、独立 Authorization Server、JDBC 协议存储与端到端签发测试。

两段现已完成：AI API 不再接受自报租户/主体，Identity、独立 Authorization Server、JDBC
协议存储、Client Credentials 与 Authorization Code + PKCE 浏览器自动化契约均已进入代码。
PKCE 证据使用测试专用 public client；生产 browser/OIDC client 控制面、人员身份控制面、MFA
和密钥轮换仍属于后续 hardening，不能将测试 issuer、client 或 key 当作生产签发方案。

## 后果

正面：

- Ainer 的业务能力不绑定自研 Token，也不绑定单一身份产品。
- 租户和主体从密码学验证后的凭证产生，AI 审计、预算和权限数据获得可信归属。
- 认证失败、权限失败和业务失败保持一致的可观测响应契约。

代价：

- 本地完整登录链路需要 PostgreSQL、签名密钥和显式 client 配置。
- 多租户切换、会话撤销、密钥轮换和企业身份联邦需要单独设计，不能靠一个超长 JWT 一次解决。
- JWT 撤销不是实时会话删除；高风险操作后续需要短生命周期 access token、refresh token 策略或 introspection 等补充控制。
