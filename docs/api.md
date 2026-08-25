# Ainer HTTP API 契约

> 文档类型：接口基线 · 状态：生效 · 最近核对：2026-08-21 · 适用版本：`1.1.x`

本文记录当前 HTTP 契约和兼容规则。Ainer Admin 的机器可读子集位于
`ainer-authorization-server/src/main/openapi/ainer-admin-v1.yaml`，由固定 Maven profile
校验并生成 TypeScript SDK；本文件继续解释跨 API 的语义与边界。

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

受保护 API 使用 Bearer JWT。Resource Server 验证签名、issuer、有效期和 audience，并把
`token_profile`（`SERVICE_V1`/`USER_NEUTRAL_V1`，`claim_contract_version=1`）解析为 typed
`AuthenticatedPrincipal`。`USER_NEUTRAL_V1` 的 `sub` 是 HumanAccount ID，`SERVICE_V1` 的 `sub`
是 ServicePrincipal ID；缺失或未知 profile/actor 组合失败关闭。OAuth scope 投影为 `SCOPE_<scope>`。

请求参数、请求体或外部请求头不能覆盖 principal 与 subject。scope 只表示调用能力，Workspace
仍检查 ACTIVE membership 和角色。

## 3. 公共端点

| Method | Path | 状态 | 说明 |
|---|---|---:|---|
| GET | `/api/platform/info` | 200 | 平台名、运行模式和 Java feature version |
| GET | `/actuator/health` | 200/503 | Spring Boot 健康状态 |
| GET | `/actuator/info` | 200 | 当前公开的基础信息端点 |

不要因这些端点公开而扩大其他 Actuator exposure。

## 4. Workspace API

| Method | Path | 成功状态 | Scope | 资源角色/状态 |
|---|---|---|---:|---|
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

分页从 1 开始，`size` 范围为 1 到 100。跨 Workspace、非成员和不可见资源拒绝应避免泄露资源是否存在。

## 5. 当前 access token 自助撤销

| Method | Path | Scope | Actor | 说明 |
|---|---|---|---|---|
| POST | `/api/me/access-token-revocations` | 无附加 scope | `USER` | 撤销当前请求携带的 access token |

请求不接受 token 参数，也不能指定其他 authorization；成功响应的 `data` 为
`{"revoked":true}`。Token 不存在、已过期或已撤销统一返回 401
`AINER.COMMON.UNAUTHENTICATED`，`SERVICE` actor 返回 403。实现更新 Spring Authorization
Server 官方 JDBC authorization，不建立 Ainer 自定义 Token 表，也不撤销当前 authorization
可能关联的其他 token。该端点也经过同一 active gate。

### Ainer Admin TypeScript SDK

从仓库根目录运行：

```bash
./mvnw -pl ainer-authorization-server -Painer-admin-sdk generate-resources
```

命令先严格校验 `ainer-admin-v1.yaml`，再用固定版本 OpenAPI Generator 的
`typescript-fetch` 生成器输出到
`ainer-authorization-server/target/generated-sources/ainer-admin-typescript/`。生成目录属于
构建产物，不提交到 Ainer Boot；Ainer Studio 按已批准的前端目录与包管理策略消费该输出。
SDK 只覆盖当前 token 自助撤销，OAuth/OIDC 登录和 logout 继续使用标准协议客户端。
OpenAPI 使用相对 `/` 表达同源入口；由于生成器会为该合法相对地址写入 `http://localhost`
兜底值，Ainer Admin 必须在 SDK `Configuration` 中显式传入 `window.location.origin`，
并通过 `accessToken` 回调读取内存中的当前 access token。登录、退出、SDK 装配和反向代理的
完整运行契约见 [`ainer-admin-integration.md`](ainer-admin-integration.md)。

## 6. AI API

AI runtime 默认关闭；启用后所有端点要求 `ai.invoke` scope。参考装配还要求对应
`@AinerAuthorize` Binding（粗门禁，不是资源级合同）。请求可选 `actingAgentId` +
`workspaceId`：出现代行上下文时网关 fail-closed 调用 `ActingGrant.check`；人员直调不带
`actingAgentId` 则跳过。缺 `workspaceId` 的代行请求返回 422 `AINER.AI.INVALID_ACTING_CONTEXT`。

| Method | Path | 响应 | 说明 |
|---|---|---|---|
| POST | `/api/ai/chat/completions` | JSON | 非流式 completion |
| POST | `/api/ai/chat/completions/stream` | `text/event-stream` | `delta`、`usage`、`done` 或 `error` 事件 |
| GET | `/api/ai/invocations/{id}` | JSON | 只读取当前 subject 的调用审计 |

请求、SSE payload、错误和审计字段见 [`ai-gateway.md`](ai-gateway.md)。Ainer 的内部契约不是对 OpenAI API 的完整字段兼容承诺。

## 7. Authorization Server 与内部 Identity API

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
account 全部 ACTIVE Passkey。完整边界见 ADR-0014/0015。

Authorization Code + PKCE 当前由自动化测试专用 registered client 证明，生产 browser client
通过默认关闭的 browser client 控制面创建。不得把测试 client、测试 issuer 或测试 RSA key 带入
发行环境。

人员 Token 的 introspection 还检查 Identity 当前状态和最新 revocation epoch（`sec_epoch` 不符即失效）。inactive 原因不向调用方细分。Resource Server 对匹配高风险规则的 inactive 返回 Ainer 401；introspection 依赖失败返回 503 `AINER.SECURITY.ONLINE_VALIDATION_UNAVAILABLE`。保护规则和配置见 [`configuration.md`](configuration.md)。

以下是默认关闭的系统间接口，不属于公网或租户客户端 API。所有系统间接口都要求类型化
SERVICE Token：`token_profile=SERVICE_V1`、`claim_contract_version=1`、`actor_type=SERVICE`，
再叠加表中列出的 scope：

| 发行物 | Method | Path | 必需服务权限 | 说明 |
|---|---|---|---|---|
| Authorization Server | POST | `/internal/passkey-recovery/accounts/{accountId}/recovery-requests` | `actor_type=SERVICE` + `passkey.recovery.request.all` | 为指定 ACTIVE account 建立双人恢复申请 |
| Authorization Server | POST | `/internal/passkey-recovery/accounts/{accountId}/recovery-requests/{requestId}/approvals` | `actor_type=SERVICE` + `passkey.recovery.approve.all` | 不同服务批准并吊销目标全部 ACTIVE Passkey |
| Authorization Server | GET/POST | `/internal/passkey-enrollment/accounts/{accountId}/grants` | `actor_type=SERVICE` + `passkey.enrollment.manage.all` | 查询或授予目标 account 的首枚 Passkey enrollment |
| Authorization Server | POST | `/internal/oauth-browser-clients` | `actor_type=SERVICE` + `oauth.browser-clients.manage` + operator ID 白名单 | 创建生产 browser Authorization Code + PKCE client |
| Authorization Server | GET | `/internal/oauth-browser-clients` | 同上 | 分页查询安全生命周期投影 |
| Authorization Server | GET | `/internal/oauth-browser-clients/{clientId}` | 同上 | 查询单个 client 生命周期投影 |
| Authorization Server | POST | `/internal/oauth-browser-clients/{clientId}/rotations` | 同上 | 以新 client ID 创建并行 replacement |
| Authorization Server | POST | `/internal/oauth-browser-clients/{clientId}/retirement` | 同上 | 显式退役，阻止新 Token 并让在线 Token 查询 inactive |
| `ainer-server` | POST | `/internal/workspace-owner-recovery/workspaces/{workspaceId}/requests` | `actor_type=SERVICE` + `workspace.owner-recovery.request.all` | 为无 ACTIVE OWNER 的 Workspace 申请恢复 |
| `ainer-server` | POST | `/internal/workspace-owner-recovery/workspaces/{workspaceId}/requests/{requestId}/approvals` | `actor_type=SERVICE` + `workspace.owner-recovery.approve.all` | 不同服务批准并提升现有 ACTIVE 成员 |
| `ainer-server` | GET | `/internal/workspace-authorization-audits/workspaces/{workspaceId}/exports` | `actor_type=SERVICE` + `workspace.audit.export.all` + 可信 exporter `sub` | SIEM 按 Workspace 稳定游标拉取热/冷审计并集 |

browser client 控制面默认关闭，只管理生产的 public Authorization Code + PKCE client：
创建、查询、轮换（以新 ID 创建 replacement）与显式退役（阻止新 Token、历史在线 Token 查询
inactive）。public client 无事前 secret，只返回生命周期投影。退役不可逆，不提供 DELETE 或
重新激活。

OWNER 恢复请求体：

```json
{
  "workspaceId": "80000000-0000-0000-0000-000000000001",
  "newOwnerSubjectId": "subject:active-admin",
  "incidentReference": "INC-2026-0043"
}
```

批准时再次锁定 Workspace；必须没有 ACTIVE OWNER、至少有一个 REVOKED OWNER，且目标是同
Workspace 的 ACTIVE 非 OWNER 成员。旧 REVOKED OWNER 不被重新激活。request/approve Client
必须不同，不能把两个 scope 授给同一个运维 Client。

SIEM 导出参数 `afterOccurredAt` 与 `afterId` 必须同时提供或同时省略，`limit=1..1000`。结果按 `occurredAt,id` 升序，返回 `nextOccurredAt`、`nextId` 和 `hasMore`。消费者持久化这对游标并按 audit ID 去重；每次导出批次本身也写入安全操作审计。

## 8. 通用授权 API（ADR-0037；ADR-0030 已被取代）

### `@AinerAuthorize` 端点门禁

产品 controller 可以在方法上声明 `@AinerAuthorize(permission="...")`。该注解是 controller 执行前
的门禁，不新增 HTTP endpoint，也不替代应用服务中的资源级授权。未认证请求返回 401；DENY 返回统一
403；高风险权限缺少近期强认证时返回 401 并携带 RFC 9470 挑战头
`WWW-Authenticate: Bearer error="insufficient_user_authentication"`。

目标解析与投影语义：

- 未注册 `AuthorizationTargetResolver` 时，目标固定为 `resourceType=request` 的合成资源，
  permission 也必须以该类型注册。产品注册解析器 bean 后（第一个非空结果胜出），门禁按解析出的
  类型化 `ResourceRef` 决策，类型不匹配一律 403。参考装配注册 Workspace 路径解析器：
  `/api/workspaces/{id}` 写入 `workspaceId`（类型仍是 `request`），对该工作区的 Binding
  才能通过；无路径 id 时仍是粗闸门。
- ALLOW 携带的 `PublicProjection` 是响应投影数据而非待执行义务，不会阻断放行。完整决策通过请求
  属性 `ainer.authorization.decision` 暴露，controller 必须自行消费投影描述符完成字段裁剪——
  门禁本身不做字段投影。
- `PUBLIC_PROJECTION` 也不会绕过 Resource Server：宿主必须另行配置 `public-paths` 并注册
  `PublicAccessPolicy`，否则匿名请求失败关闭为 403。
- 方法级 AOP 与 obligation executor 尚未进入支持面。

### 管理 API

所有 `/api/authorization/**` 端点要求 SERVICE principal + `authorization.manage` scope，并且该精确
`issuer + sub` 必须由宿主代码注册的版本化 `GrantAdministrationPolicy` 判定为可信管理主体。
仅持有 scope 不产生授权管理权；未注册策略时全部管理端点失败关闭为 403。响应使用统一
`ApiResponse` 信封。

通用管理面只处理策略声明的 assignable Permission、Scope 和目标主体，不从管理者自己的
Effective Access 推导分配权。Role 不能包含 `systemOnly` 或策略未列入的权限；通用 Binding API
不能创建 GLOBAL scope，也不能创建、撤销自己的 Binding；管理者任一 ACTIVE Binding 引用的 Role 不能由其
本人扩大或替换权限。产品 onboarding/bootstrap 如需创建初始业务授权，必须走独立关系校验与审计路径。

### Permission 目录（只读）

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/authorization/permissions` | 返回所有已注册权限定义（code、action、resourceType、riskTier、auditLevel、systemOnly、agentDelegable） |

### Role 管理

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/authorization/roles` | 创建角色（code、name、permissions）。未注册或不可授予/system-only permission 返回 422。重复 code 返回 409 |
| `GET` | `/api/authorization/roles/{roleId}` | 查询角色（含 permissions） |
| `PUT` | `/api/authorization/roles/{roleId}/permissions` | 原子替换角色权限（乐观版本检查）。自己的 ACTIVE Binding 引用该 Role 时返回 403；版本冲突返回 409 |

### Binding 生命周期

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/authorization/bindings` | 创建绑定（issuer、subjectType、subjectId、roleId、scopeKind、可选 workspaceId/resourceType/resourceId/validUntil）。GLOBAL/不可授予 scope 返回 422，越界目标或自授予返回 403 |
| `GET` | `/api/authorization/bindings/{bindingId}` | 查询绑定 |
| `POST` | `/api/authorization/bindings/{bindingId}/revocations` | 逻辑撤销绑定（reason）。撤销后 liveBindings 立即不返回——无 ALLOW 缓存 |

### Effective Access

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/authorization/effective-access?issuer=&subjectType=&subjectId=` | 查询某 subject 当前所有有效绑定（ACTIVE + 有效期内） |

Scope kind 与底层 CHECK 约束：`GLOBAL`（workspace_id/resource_type/resource_id 全 NULL）、
`WORKSPACE`（workspace_id 非空）、`RESOURCE`（workspace_id + resource_type + resource_id 全非空）。
通用管理 API 不创建 GLOBAL Binding；受控平台路径若建立 GLOBAL Binding，决策器仍只允许 SERVICE
subject 使用。

## 9. 文件 API（ADR-0040）

文件模块默认装配；所有端点要求认证，scope 在应用服务内强制（`file.read` 读取/下载，`file.write` 上传/删除）。
参考装配另有 `@AinerAuthorize` 粗门禁，需对应 Binding；模块切片未装配拦截器时注解不生效。
仍不是对象级（按文件 ID）授权合同。

| Method | Path | Scope | 响应 | 说明 |
|---|---|---|---|---|
| POST | `/api/files` | `file.write` | 201 JSON | multipart 上传（`namespace` + `file`）；返回元数据（含 SHA-256） |
| GET | `/api/files` | `file.read` | JSON | 分页（`page`≥1、`size`≤100，可选 `namespace` 过滤） |
| GET | `/api/files/{id}/content` | `file.read` | 文件流 | `Content-Disposition` 携带原文件名 |
| DELETE | `/api/files/{id}` | `file.write` | JSON | 删除字节与元数据；审计行保留 |

限制：超出 `ainer.file.max-size-bytes` 返回 413（`AINER.FILE.FILE_TOO_LARGE`）；content-type 不在
`ainer.file.allowed-content-types` 白名单返回 415（`AINER.FILE.CONTENT_TYPE_NOT_ALLOWED`）。上传/删除
写入同事务 `ainer_file_audit`。存储后端为 `FileStoragePort` SPI（默认本地适配器，产品可用 S3/OSS 覆盖）。

## 10. 企业基座管理 API（ADR-0040）

三模块管理面默认随模块装配；scope 在应用服务内对已验证 principal 强制。

### Dictionary（`dictionary.read` / `dictionary.manage`）

参考装配另有 `@AinerAuthorize` 粗门禁，需对应 Binding；模块切片未装配拦截器时注解不生效。
仍不是按类型/项 ID 的对象级授权合同。

| Method | Path | Scope | 说明 |
|---|---|---|---|
| POST/GET | `/api/dictionaries/types` | manage / read | 创建类型（409 重复编码）；`?status=&page=&size=` 分页 |
| GET/PUT | `/api/dictionaries/types/{id}` | read / manage | 读取；乐观锁部分更新（stale 409） |
| POST | `/api/dictionaries/types/{id}/status-changes` | manage | 启用/禁用（动作名词端点） |
| POST/GET | `/api/dictionaries/types/{typeId}/items` | manage / read | 创建项；分页（含禁用状态） |
| PUT | `/api/dictionaries/items/{id}`、POST `.../status-changes` | manage | 项更新与状态变更 |

写入同事务记录 `ainer_dictionary_audit`（operation/target/actor/requestId）。错误码 `AINER.DICTIONARY.*`。

### Config（`config.read` / `config.manage`）

参考装配另有 `@AinerAuthorize` 粗门禁，需对应 Binding；模块切片未装配拦截器时注解不生效。
仍不是按 namespace/key 的对象级授权合同。

| Method | Path | Scope | 说明 |
|---|---|---|---|
| POST | `/api/configs/entries` | manage | 设置明文值；对 secret 键返回 409 |
| POST | `/api/configs/secrets` | manage | 设置 secret（AES-GCM；明文不回显） |
| GET | `/api/configs/entries?namespace=` | read | 列表；secret 项 value 置 null |
| GET | `/api/configs/history?namespace=&key=` | read | 版本历史（secret 记录为 `[encrypted]`） |

审计即 `ainer_config_history`（同事务，含 actor）。错误码 `AINER.CONFIG.*`。

### Notification（`notification.read` / `manage` / `submit`）

参考装配另有 `@AinerAuthorize` 粗门禁，需对应 Binding；模块切片未装配拦截器时注解不生效。
仍不是按模板/记录 ID 的对象级授权合同。

| Method | Path | Scope | 说明 |
|---|---|---|---|
| POST/GET | `/api/notifications/templates` | manage / read | 创建模板（409 重复）；`?status=` 分页 |
| PUT | `/api/notifications/templates/{id}`、POST `.../status-changes` | manage | 乐观锁更新与状态变更 |
| POST | `/api/notifications/messages` | submit | 直接提交通知入队 |
| GET | `/api/notifications/records?status=` | read | 投递记录分页；**不含**渲染 title/body（PII） |

模板变更同事务记录 `ainer_notification_audit`。错误码 `AINER.NOTIFICATION.*`。

## 11. 任务调度 API（ADR-0047）

任务模块默认装配；scope 在应用服务内强制。参考装配另有 `@AinerAuthorize` 粗门禁，需对应
Binding；模块切片未装配拦截器时注解不生效。仍不是按作业 ID 的对象级授权合同。
`taskType` 全小写、以字母开头，可含数字/点/连字符。
payload 必须是合法 JSON 对象且不超过 64 KB（否则 422 `AINER.TASK.INVALID_PAYLOAD`）。

### 定义管理（`task.read` / `task.manage`）

| Method | Path | Scope | 说明 |
|---|---|---|---|
| POST | `/api/tasks/definitions` | `task.manage` | 注册任务类型（201；409 `DUPLICATE_TASK_TYPE`） |
| GET | `/api/tasks/definitions?page=&size=` | `task.read` | 分页（`size`≤100，越界 422） |
| POST | `/api/tasks/definitions/{taskType}/status-changes` | `task.manage` | `{"status":"ACTIVE"\|"PAUSED"}`；其他取值 422 `INVALID_STATUS` |

### 作业（`task.submit` 提交；读取/管理见 scope 列）

| Method | Path | Scope | 说明 |
|---|---|---|---|
| POST | `/api/tasks/jobs` | `task.submit` | 提交作业：`delaySeconds` 延迟或 `intervalSeconds` 周期（201） |
| GET | `/api/tasks/jobs/{id}` | `task.read` | 单个作业；不存在 404 |
| GET | `/api/tasks/jobs?status=&taskType=&page=&size=` | `task.read` | 分页过滤 |
| POST | `/api/tasks/jobs/{id}/cancellations` | `task.manage` | 取消 PENDING 作业；RUNNING/终态 409 |
| POST | `/api/tasks/jobs/{id}/retries` | `task.manage` | 重试 FAILED/EXHAUSTED 作业；其余状态 409 |

生命周期由执行引擎驱动：领取（RUNNING）→ 成功 SUCCEEDED 或退避 FAILED → 耗尽 EXHAUSTED；
周期任务成功后回到 PENDING 并推进 `next_run_at`。超时按定义 `timeout_seconds` 由看门狗判 FAILED，
不杀死执行线程——迟到的结果被丢弃，因此处理器必须幂等（at-least-once）。周期任务重试耗尽后
进入终态 EXHAUSTED，需经 retries 端点人工恢复。SUBMITTED/CLAIMED/SUCCEEDED/RETRY_SCHEDULED/
EXHAUSTED/CANCELLED/REGISTERED 等事件写入同事务或引擎侧 append-only 审计 `ainer_task_audit`；
payload 正文不入库审计。错误码族 `AINER.TASK.*`。

## 12. 兼容与变更

- 新增可选响应字段通常向后兼容，客户端必须容忍未知字段；
- 删除、改名、改变字段类型、收紧枚举或改变 status/error code 都需要发布说明；
- 安全修复可以立即收紧非法或越权行为，但必须记录影响；
- API 版本策略和 OpenAPI 自动校验尚未建立，稳定版前必须另立 ADR；
- Controller、测试、本文和 Changelog 必须在同一变更保持一致。
