# Ainer HTTP API 契约

> 文档类型：接口基线 · 状态：生效 · 最近核对：2026-07-26 · 适用版本：`0.1.x`

本文记录当前手写 HTTP 契约和兼容规则。它是开发者索引，不替代未来由代码生成并在 CI 校验的 OpenAPI 文档。

## 1. 通用响应

除 SSE 和 Spring Authorization Server 标准协议端点外，JSON API 使用：

```json
{
  "code": "AINER.COMMON.OK",
  "message": "OK",
  "data": {},
  "requestId": "request-or-generated-id",
  "timestamp": "2026-07-23T00:00:00Z"
}
```

HTTP status 是传输层权威语义。`code` 是稳定业务错误标识，客户端不得只检查 HTTP 200。请求可携带 `X-Request-Id`；服务总会返回 `X-Request-Id` 并在 JSON 中回显 `requestId`。

通用错误：400 `AINER.COMMON.INVALID_REQUEST`、401 `AINER.COMMON.UNAUTHENTICATED`、403 `AINER.COMMON.FORBIDDEN`、404 `AINER.COMMON.NOT_FOUND`、409 `AINER.COMMON.CONFLICT`、422 `AINER.COMMON.BUSINESS_RULE_VIOLATION`、500 `AINER.COMMON.INTERNAL_ERROR`。模块可以提供更具体的稳定错误码。

## 2. 身份上下文

受保护 API 使用 Bearer JWT。Resource Server 验证签名、issuer、有效期和 audience，并从 `sub`、
`tenant_id` 与必需的 `actor_type` 构建 `AuthenticatedActor`。`actor_type` 只允许 `USER` 或
`SERVICE`；缺失或未知值失败关闭。OAuth scope 投影为 `SCOPE_<scope>`。

请求参数、请求体或外部请求头不能覆盖 tenant 与 subject。scope 只表示调用能力，Workspace 仍检查同 tenant 的 ACTIVE membership 和角色。

## 3. 公共端点

| Method | Path | 状态 | 说明 |
|---|---|---:|---|
| GET | `/api/platform/info` | 200 | 平台名、运行模式和 Java feature version |
| GET | `/actuator/health` | 200/503 | Spring Boot 健康状态 |
| GET | `/actuator/info` | 200 | 当前公开的基础信息端点 |

不要因这些端点公开而扩大其他 Actuator exposure。

## 4. Workspace API

| Method | Path | 成功状态 | Scope | 资源角色/状态 |
|---|---|---:|---|---|
| POST | `/api/workspaces` | 201 | `workspace.write` | 创建者成为 ACTIVE OWNER |
| GET | `/api/workspaces/{id}` | 200 | `workspace.read` | ACTIVE member |
| GET | `/api/workspaces?page=1&size=20` | 200 | `workspace.read` | 只返回当前主体可访问资源 |
| PATCH | `/api/workspaces/{id}` | 200 | `workspace.write` | OWNER 或 ADMIN |
| POST | `/api/workspaces/{id}/members` | 201 | `workspace.write` | OWNER 或 ADMIN；只可邀请 ADMIN/MEMBER，结果 PENDING |
| POST | `/api/workspaces/{id}/membership-acceptances` | 200 | `workspace.read` | 受邀主体接受自己的 PENDING invitation |
| POST | `/api/workspaces/{id}/member-role-changes` | 200 | `workspace.write` | OWNER 或 ADMIN；不得操作 OWNER |
| POST | `/api/workspaces/{id}/member-removals` | 200 | `workspace.write` | OWNER 或 ADMIN；不得移除 OWNER |
| POST | `/api/workspaces/{id}/ownership-transfers` | 200 | `workspace.write` | 仅当前 OWNER；目标必须为 ACTIVE member |
| GET | `/api/workspaces/{id}/authorization-audits?page=1&size=20` | 200 | `workspace.audit.read` | OWNER 或 ADMIN |

分页从 1 开始，`size` 范围为 1 到 100。跨 tenant、非成员和不可见资源拒绝应避免泄露资源是否存在。

## 5. Identity 租户成员 API

以下接口位于 Identity 数据所属的 `ainer-authorization-server`，仅接受 `actor_type=USER`。
路径 `tenantId` 必须等于 JWT
`tenant_id`，调用者还必须是数据库中的 ACTIVE `OWNER`/`ADMIN`；scope 与资源角色缺一不可。

| Method | Path | Scope | 资源角色/状态 | 说明 |
|---|---|---|---|---|
| GET | `/api/tenants/{tenantId}/members?page=1&size=20` | `tenant.members.read` | ACTIVE OWNER/ADMIN | 只返回 ACTIVE tenant/user/membership 的安全投影 |
| POST | `/api/tenants/{tenantId}/members` | `tenant.members.write` | ACTIVE OWNER/ADMIN | 把已存在用户加入 tenant；`username`/`subjectId` 二选一，角色只允许 ADMIN/MEMBER |
| PATCH | `/api/tenants/{tenantId}/members/{subjectId}` | `tenant.members.write` | ACTIVE OWNER/ADMIN | 在 ADMIN/MEMBER 间调整；通用接口不能修改 OWNER |
| DELETE | `/api/tenants/{tenantId}/members/{subjectId}?reasonCode=...` | `tenant.members.write` | ACTIVE OWNER/ADMIN | 软移除非 OWNER 成员 |

POST 请求还包含 `role` 与安全格式 `reasonCode`；PATCH 包含 `role`、`reasonCode`。已 DISABLED
的非 OWNER membership 再次添加会显式重新激活，ACTIVE 重复关系、LOCKED/OWNER 状态冲突返回
409。每次成功写操作与 `reasonCode`、request ID 一起进入同事务成员安全审计。分页从 1 开始，
`size=1..100`。

以上每个请求在 JWT 验证之后还会查询 Authorization Server 官方 authorization，确认当前
access token 仍为 active；未知、过期、显式撤销或 Identity 当前状态失效统一返回 401，
查询依赖失败返回 503 `AINER.SECURITY.ONLINE_VALIDATION_UNAVAILABLE`，不会退回仅凭 JWT 放行。

## 6. 当前 access token 自助撤销

| Method | Path | Scope | Actor | 说明 |
|---|---|---|---|---|
| POST | `/api/me/access-token-revocations` | 无附加 scope | `USER` | 撤销当前请求携带的 access token |

请求不接受 token 参数，也不能指定其他 authorization；成功响应的 `data` 为
`{"revoked":true}`。Token 不存在、已过期或已撤销统一返回 401
`AINER.COMMON.UNAUTHENTICATED`，`SERVICE` actor 返回 403。实现更新 Spring Authorization
Server 官方 JDBC authorization，不建立 Ainer 自定义 Token 表，也不撤销当前 authorization
可能关联的其他 token。该端点也经过同一 active gate。

## 7. AI API

AI runtime 默认关闭；启用后所有端点要求 `ai.invoke` scope。

| Method | Path | 响应 | 说明 |
|---|---|---|---|
| POST | `/api/ai/chat/completions` | JSON | 非流式 completion |
| POST | `/api/ai/chat/completions/stream` | `text/event-stream` | `delta`、`usage`、`done` 或 `error` 事件 |
| GET | `/api/ai/invocations/{id}` | JSON | 只读取当前 tenant 的调用审计 |

请求、SSE payload、错误和审计字段见 [`ai-gateway.md`](ai-gateway.md)。Ainer 的内部契约不是对 OpenAI API 的完整字段兼容承诺。

## 8. Authorization Server 与内部 Identity API

`ainer-authorization-server` 暴露 Spring Authorization Server 的标准 OAuth 2.1/OIDC 端点，具体启用能力和密钥要求见 [`security.md`](security.md)。协议端点遵循标准响应，不套 Ainer JSON envelope。

当前明确验证以下标准协议端点：

| Method | Path | Client 要求 | 语义 |
|---|---|---|---|
| GET | `/oauth2/authorize` | 精确注册 redirect URI 的 public client；`requireProofKey=true` | Authorization Code + PKCE；只接受 S256，缺失/`plain` challenge 失败 |
| POST | `/oauth2/token` | public client 传 `client_id`、authorization code、redirect URI 和正确 `code_verifier` | 授权码只能成功交换一次；测试基线返回 access/id token，不返回 refresh token |
| POST | `/oauth2/introspect` | HTTP Basic；显式受信、只有 `token.introspect`、无 tenant 的专用 client | RFC 7662；返回 `active`，普通业务 client 返回 401 `invalid_client` |
| POST | `/oauth2/revoke` | Spring Authorization Server 注册 client 认证 | RFC 7009；撤销官方 JDBC authorization 中的目标 Token |

Passkey 显式启用后还装配以下 Spring Security WebAuthn 端点；它们同样不套 Ainer envelope：

| Method | Path | 身份要求 | 语义 |
|---|---|---|---|
| POST | `/webauthn/register/options` | 已认证人员；已有 ACTIVE Passkey 时还需 WebAuthn 因子 | 生成短时 registration options；强制 UV 与 resident credential |
| POST | `/webauthn/register` | 同上 + CSRF | 验证 attestation 并登记 credential |
| DELETE | `/webauthn/register/{credentialId}` | credential owner + WebAuthn 因子 + CSRF | 软撤销非最后一个 ACTIVE credential |
| POST | `/webauthn/authenticate/options` | 可匿名或已有 session | 生成短时 authentication options；强制 UV |
| POST | `/login/webauthn` | 对应 session options + CSRF | 验证 assertion 并建立/追加 WebAuthn 认证因子 |

credential ID 是可关联安全元数据，不应出现在普通日志。启用自助恢复后，
`POST /passkey/recovery-codes` 由已登记且完成 WebAuthn 条件门禁的人员签发 8 枚明文只返回一次的
恢复码；`POST /passkey/recovery-codes/redeem` 由密码会话中的本人提交一枚恢复码，成功后吊销该
subject 全部 ACTIVE Passkey。完整边界见 ADR-0014/0015。

Authorization Code + PKCE 当前由自动化测试专用 registered client 证明，生产没有默认 browser client，
也没有 browser/OIDC client 创建 API。不得把测试 client、测试 issuer 或测试 RSA key 带入发行环境。

人员 Token 的 introspection 还检查 Identity tenant/user/membership 当前状态和最新 revocation epoch。inactive 原因不向调用方细分。Resource Server 对匹配高风险规则的 inactive 返回 Ainer 401；introspection 依赖失败返回 503 `AINER.SECURITY.ONLINE_VALIDATION_UNAVAILABLE`。保护规则和配置见 [`configuration.md`](configuration.md)。

以下是默认关闭的系统间接口，不属于公网或租户客户端 API：

| 发行物 | Method | Path | 必需服务权限 | 说明 |
|---|---|---|---|---|
| Authorization Server | GET | `/internal/identity/directory/tenants/{tenantId}/members/{subjectId}` | `actor_type=SERVICE` + `identity.directory.read` 或 `.read.all` | 精确查询 ACTIVE 安全投影 |
| Authorization Server | GET | `/internal/identity/directory/tenants/{tenantId}/members?query=&limit=20` | 同上 | 搜索 ACTIVE 成员，`limit=1..50` |
| `ainer-server` | POST | `/internal/identity/access-events` | `actor_type=SERVICE` + `identity.access-events.publish` + 可信 publisher `sub` | 幂等收敛 Workspace membership |
| Authorization Server | GET | `/internal/identity/access-event-recovery/tenants/{tenantId}/exhausted?page=1&size=20` | `actor_type=SERVICE` + `identity.access-events.replay.read` 或 `.read.all` | 查询无有效 lease 的真正耗尽事件 |
| Authorization Server | POST | `/internal/identity/access-event-recovery/tenants/{tenantId}/replay-requests` | `actor_type=SERVICE` + `identity.access-events.replay.request` 或 `.request.all` | 创建 15 分钟默认有效的重放申请 |
| Authorization Server | POST | `/internal/identity/access-event-recovery/tenants/{tenantId}/replay-requests/{requestId}/approvals` | `actor_type=SERVICE` + `identity.access-events.replay.approve` 或 `.approve.all` | 由不同服务批准并重置原事件 |
| Authorization Server | POST | `/internal/passkey-recovery/tenants/{tenantId}/recovery-requests` | `actor_type=SERVICE` + `passkey.recovery.request` 或 `.request.all` | 为属于该 ACTIVE tenant 的 ACTIVE subject 建立双人恢复申请 |
| Authorization Server | POST | `/internal/passkey-recovery/tenants/{tenantId}/recovery-requests/{requestId}/approvals` | `actor_type=SERVICE` + `passkey.recovery.approve` 或 `.approve.all` | 不同服务批准并吊销目标全部 ACTIVE Passkey |
| Authorization Server | GET/POST | `/internal/passkey-enrollment/tenants/{tenantId}/grants` | `actor_type=SERVICE` + `passkey.enrollment.manage` 或 `.manage.all` | 查询或授予目标 ACTIVE tenant subject 的首枚 Passkey enrollment |
| Authorization Server | DELETE | `/internal/passkey-enrollment/tenants/{tenantId}/grants/{subjectId}` | 同上 | 撤销未消费的 enrollment grant |
| Authorization Server | POST | `/internal/oauth-service-clients` | tenantless `actor_type=SERVICE` + `oauth.clients.manage` + operator ID 白名单 | 创建 tenant-bound Client Credentials client，secret 只返回一次 |
| Authorization Server | GET | `/internal/oauth-service-clients/{clientId}` | 同上 | 查询安全生命周期投影，不返回 secret/hash |
| Authorization Server | POST | `/internal/oauth-service-clients/{clientId}/rotations` | 同上 | 以新 client ID 创建并行 replacement |
| Authorization Server | POST | `/internal/oauth-service-clients/{clientId}/retirement` | 同上 | 显式退役，阻止新 Token 并让在线 Token 查询 inactive |
| `ainer-server` | POST | `/internal/workspace-owner-recovery/tenants/{tenantId}/requests` | `actor_type=SERVICE` + `workspace.owner-recovery.request` 或 `.request.all` | 为无 ACTIVE OWNER 的 Workspace 申请恢复 |
| `ainer-server` | POST | `/internal/workspace-owner-recovery/tenants/{tenantId}/requests/{requestId}/approvals` | `actor_type=SERVICE` + `workspace.owner-recovery.approve` 或 `.approve.all` | 不同服务批准并提升现有 ACTIVE 成员 |
| `ainer-server` | GET | `/internal/workspace-authorization-audits/tenants/{tenantId}/exports` | `actor_type=SERVICE` + `workspace.audit.export` 或 `.export.all` + 可信 exporter `sub` | SIEM 按稳定游标拉取热/冷审计并集 |

`identity.directory.read` 必须由 Token `tenant_id` 绑定路径 tenant；只有 `identity.directory.read.all` 可以跨 tenant 选择。Directory 响应只含 `tenantId`、`subjectId`、`username`、`displayName`、`role`。非 ACTIVE 精确查询返回 404 `AINER.IDENTITY.DIRECTORY_MEMBER_NOT_FOUND`，不得暴露密码哈希、锁定细节或 OAuth 数据。

OAuth 服务客户端控制面默认关闭。它只接收 tenant UUID、`client_credentials` 服务 client 和配置
白名单内 scope，不接受 redirect URI、Authorization Code、public client、平台 scope 或 `.all`
scope。创建请求示例：

```json
{
  "clientId": "orders-agent-v1",
  "clientName": "Orders Agent",
  "tenantId": "50000000-0000-0000-0000-000000000001",
  "scopes": ["ai.invoke"],
  "changeReference": "CHG-2026-1001"
}
```

成功响应 `data` 结构如下；`clientSecret` 只在本次创建或轮换响应出现：

```json
{
  "client": {
    "clientId": "orders-agent-v1",
    "clientName": "Orders Agent",
    "tenantId": "50000000-0000-0000-0000-000000000001",
    "scopes": ["ai.invoke"],
    "status": "ACTIVE",
    "replacesClientId": null,
    "clientIdIssuedAt": "2026-07-23T00:00:00Z",
    "clientSecretExpiresAt": "2026-10-21T00:00:00Z",
    "createdAt": "2026-07-23T00:00:00Z",
    "retiredAt": null
  },
  "clientSecret": "returned-once-and-never-queryable"
}
```

轮换请求必须指定不同且未使用的新 ID：

```json
{
  "replacementClientId": "orders-agent-v2",
  "replacementClientName": "Orders Agent v2",
  "changeReference": "CHG-2026-1002"
}
```

轮换不自动退役旧 ID；调用方先把新 secret 写入 secret store、灰度部署并验证，再向
`/{oldClientId}/retirement` 发送 `{"changeReference":"CHG-2026-1003"}`。退役不可逆，不提供
DELETE 或重新激活。

稳定模块错误码包括：

| HTTP | code | 语义 |
|---:|---|---|
| 422 | `AINER.AUTHORIZATION.OAUTH_CLIENT_INVALID_REQUEST` | client ID/name、tenant、scope 数量或变更引用不合法 |
| 422 | `AINER.AUTHORIZATION.OAUTH_CLIENT_SCOPE_NOT_ALLOWED` | 请求 scope 不在启动白名单 |
| 404 | `AINER.AUTHORIZATION.OAUTH_CLIENT_NOT_FOUND` | 不是该控制面创建的 managed client |
| 409 | `AINER.AUTHORIZATION.OAUTH_CLIENT_ALREADY_EXISTS` | 新 client ID 已存在 |
| 409 | `AINER.AUTHORIZATION.OAUTH_CLIENT_NOT_ACTIVE` | 源 client 已退役 |
| 409 | `AINER.AUTHORIZATION.OAUTH_CLIENT_STATE_CONFLICT` | 并发状态变化或协议记录不一致 |

401/403 继续使用通用安全错误。完整密钥与离线 JWT 限制见 [`security.md`](security.md) 和
[ADR-0013](decisions/0013-audited-oauth-service-client-lifecycle.md)。

事件请求为版本化 JSON：

```json
{
  "eventId": "70000000-0000-0000-0000-000000000001",
  "eventType": "IDENTITY_USER_DISABLED",
  "tenantId": "50000000-0000-0000-0000-000000000001",
  "subjectId": "60000000-0000-0000-0000-000000000001",
  "payloadVersion": 1,
  "occurredAt": "2026-07-23T00:00:00Z"
}
```

`eventType` 当前只有 `IDENTITY_USER_DISABLED`、`IDENTITY_MEMBERSHIP_REVOKED`。成功响应的 `data` 含 `eventId`、`duplicate`、`affectedMemberships`；重复 event ID 返回 200 且不重复修改。超过允许未来时钟偏差的事件返回 400。该端点不提供恢复语义，也不允许普通人员 Token 调用。

账号禁用仍是 Identity 应用用例，尚未开放远程管理 HTTP API；tenant membership 的租户内管理
已经通过第 5 节 USER API 开放，平台级跨租户管理仍未开放。

重放申请请求体：

```json
{
  "eventId": "70000000-0000-0000-0000-000000000001",
  "incidentReference": "INC-2026-0042"
}
```

只有 `attempt_count >= AINER_IDENTITY_ACCESS_EVENT_MAX_ATTEMPTS`、状态为 PENDING/FAILED 且无有效 lease 的事件可申请。批准者 `sub` 必须与申请者不同；批准成功保留原 event ID、payload 与 occurredAt，只把投递状态重置为 PENDING。重复批准、过期、同人批准和跨 tenant 操作返回稳定 4xx，不创建新事件。

OWNER 恢复请求体：

```json
{
  "workspaceId": "80000000-0000-0000-0000-000000000001",
  "newOwnerSubjectId": "subject:active-admin",
  "incidentReference": "INC-2026-0043"
}
```

批准时再次锁定 Workspace；必须没有 ACTIVE OWNER、至少有一个 REVOKED OWNER，且目标是同 Workspace 的 ACTIVE 非 OWNER 成员。旧 REVOKED OWNER 不被重新激活。request/approve Client 必须不同，不能把两个 scope 授给同一个运维 Client。

SIEM 导出参数 `afterOccurredAt` 与 `afterId` 必须同时提供或同时省略，`limit=1..1000`。结果按 `occurredAt,id` 升序，返回 `nextOccurredAt`、`nextId` 和 `hasMore`。消费者持久化这对游标并按 audit ID 去重；每次导出批次本身也写入安全操作审计。

## 8. 兼容与变更

- 新增可选响应字段通常向后兼容，客户端必须容忍未知字段；
- 删除、改名、改变字段类型、收紧枚举或改变 status/error code 都需要发布说明；
- 安全修复可以立即收紧非法或越权行为，但必须记录影响；
- API 版本策略和 OpenAPI 自动校验尚未建立，稳定版前必须另立 ADR；
- Controller、测试、本文和 Changelog 必须在同一变更保持一致。
