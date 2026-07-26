# ADR-0018：管理授权模型与租户成员管理

- 状态：Accepted
- 日期：2026-07-25（2026-07-26 接受并完成首个实现切片）
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无

## 背景

Ainer Boot 的认证与 Passkey 硬化（ADR-0014~0017）已落地，但平台尚无可用的管理后端：没有用户/租户/
成员管理 HTTP API，没有"管理员"授权概念，生产环境也没有创建首个租户与管理员的入口（`provisionTenantOwner`
仅被测试调用）。现有 `/internal/**` 控制面全部是 SERVICE-only（机器凭据），与"浏览器里的人管理用户"
的目标不匹配。

本 ADR 决定管理层的授权模型与第一切片（租户内成员管理），为后续用户管理、browser/OIDC 控制面与
菜单/权限打地基。

## 决策驱动因素

- 让浏览器里的人（租户 OWNER/ADMIN）能管理本租户成员，匹配最终控制台目标；
- 不破坏既有 SERVICE-only `/internal` 控制面的安全边界，不在 USER token 上扩大平台级权限；
- 复用 Ainer 已验证的"可信 JWT 投影 tenant/sub + 资源成员关系"授权模式（Workspace 即如此）；
- 不引入自研角色/权限表，`TenantRole`（OWNER/ADMIN/MEMBER）枚举继续作为租户内角色权威；
- 生产首个租户/管理员必须有可审计、可关闭、一次性的引导入口。

## 备选方案

### 方案 A：所有管理走 SERVICE `/internal` 控制面（沿用现状）

与现有控制面一致，但人无法在浏览器管理，与控制台目标矛盾，拒绝作为主方案。

### 方案 B：USER token 承担平台超管（建租户、跨租户）

需要新设计平台超管角色与跨租户信任边界，安全面扩大且当前无此需求。本切片不做。

### 方案 C：混合——租户内 USER + 平台级 SERVICE（采用）

租户内成员管理（列表/邀请/改角色/移除）由 USER token + 租户资源角色授权；平台级（建租户、跨租户
运维）继续走既有 SERVICE `/internal` 控制面。SaaS 主流，安全边界清晰。采用。

## 决策

### 1. 管理授权模型（混合）

1. 租户内管理 API 位于 Identity 数据所属的 `ainer-authorization-server`，路径为
   `/api/tenants/{tenantId}/members`，由独立 Bearer security chain 面向 `actor_type=USER`
   的 token 暴露。`ainer-server` 不装配 Identity module，避免业务库与身份库形成两个真相源。
2. 授权采用“USER actor + capability scope + 可信 JWT 投影 + 实时资源成员关系”四重门禁：
   调用者的 `tenant_id`/`sub` 只从已验证 JWT 取得；读取要求 `tenant.members.read`，写入要求
   `tenant.members.write`；调用者还必须是目标 tenant 的 `ACTIVE` 成员且角色为 `OWNER` 或
   `ADMIN`。`SERVICE`、`MEMBER`、非成员、跨租户与非 ACTIVE 调用者一律拒绝。scope 不能替代
   数据库资源角色，资源角色也不能替代 scope。
3. 平台级管理（创建租户、跨租户用户/审计运维）不属于本切片，继续用既有 SERVICE `/internal` 控制面
   （后续另立切片）；USER token 不获得任何平台级能力。
4. 租户内 `OWNER` 转移仍只能通过锁定事务的专用流程（复用 Workspace ownership transfer 的不变量思路），
   通用成员接口只能授予/调整 `ADMIN`/`MEMBER`。

### 2. 租户成员管理 API

5. 端点（USER token，tenant-bound）：
   - `GET /api/tenants/{tenantId}/members`（分页，要求 `ACTIVE` ADMIN/OWNER）；
   - `POST /api/tenants/{tenantId}/members`（把一个已存在且可用的 Identity 用户加入本租户，
     角色限 `ADMIN`/`MEMBER`；`username` 与 `subjectId` 必须二选一）；
   - `PATCH /api/tenants/{tenantId}/members/{subjectId}`（在 ADMIN/MEMBER 间改角色）；
   - `DELETE /api/tenants/{tenantId}/members/{subjectId}`（移除非 OWNER 成员；OWNER 移除走专用转移流程，
     本切片对 OWNER 移除失败关闭）。
6. 目标用户解析与成员写入都走 Identity 应用服务，不绕过领域校验。本切片只操作 Identity 本库，
   不调用外部 Directory。已 `DISABLED` 的非 OWNER 关系可被明确重新激活；`LOCKED`、ACTIVE 重复关系
   与 OWNER 状态冲突均失败关闭。
7. 所有成员变更在同一事务写入 `ainer_identity_member_audit`，记录操作者 sub、目标 subject、
   tenant、`ADDED|REACTIVATED|ROLE_CHANGED|REMOVED`、目标角色、原因码、request ID 与时间。
   审计失败时业务写一并回滚。
8. Identity 保持纯领域/应用/持久化模块，不依赖 Web。HTTP DTO 与
   `TenantMemberController` 位于 Identity 所属的可执行适配器 `ainer-authorization-server`，
   防止传输模型向应用层倒灌。

### 3. 首个租户/管理员生产引导

9. 新增默认关闭的 `PlatformTenantBootstrapRunner`。启用时按配置（tenant code/name、username、
   12..128 字符 password、display name）一次性创建首个平台租户及其 OWNER。只有 tenant code、
   username、ACTIVE tenant/user/default membership 与 OWNER 角色完整匹配时才幂等返回；租户或用户名
   被部分占用、关系禁用/漂移时返回稳定冲突，不覆盖密码或“假装成功”。PostgreSQL 事务级 advisory
   lock 串行化多实例并发初始化。成功后立即移除开关与明文密码。
10. 引导只创建首个租户；后续租户由平台级 SERVICE 控制面（后续切片）创建，不在本 ADR。

## 后果

### 正面

- 浏览器里的租户管理员首次能管理本租户成员，闭环"登录 → 管成员"；
- 授权复用既有资源成员关系模式，不引入自研角色表，不扩大 USER token 平台权限；
- 生产首个租户/管理员有了可关闭、一次性的引导入口，消除 `provisionTenantOwner` 仅测试可用的空白。

### 负面与风险

- Authorization Server 同时承担 OAuth 协议面与 Identity 人员管理面，需要用有序 security chain
  明确隔离标准协议、内部 SERVICE API、USER 管理 API、metrics 与浏览器默认链；
- 成员管理 API 目前以"单租户 per USER token"为前提（登录只解析默认租户），多租户会话/切换仍是后续；
- 平台级建租户/跨租户管理仍未提供，本切片后仍需 SERVICE 控制面或后续切片补齐；
- 邀请成员要求目标用户已存在于 Identity（无自助注册）；未注册用户的邀请流程是后续切片。

## 安全、数据与隐私

调用者 tenant/sub 只来自已验证 JWT；目标 tenant 路径变量必须等于调用者 tenant（单租户前提下），且
调用者必须是该 tenant 的 ACTIVE ADMIN/OWNER，由数据库成员关系查询确认（不接受请求体自报身份）。
OWNER 移除/降级在通用成员接口失败关闭。审计不含密码、Token 或 prompt；只保存稳定 subject、操作、
原因码与时间。

## 运维与迁移

发布顺序：

1. 发布 Authorization Server 的成员管理 API、Identity 模块 migration 与 bootstrap runner，保持
   `ainer.platform.tenant-bootstrap.enabled=false`；
2. 在受控初始化窗口配置首个租户/管理员参数并启用引导；成功后移除开关与明文密码；
3. 为浏览器管理员准备生产 browser/OIDC client（后续切片）或先用测试 public client 验证端到端；
4. 启用后监控成员变更审计与拒绝率。

回滚关闭成员管理（按 tenant 拒绝或下线路由）与 bootstrap 开关；已形成的租户/成员/审计记录不删除。

## 验收证据

2026-07-26 已完成首个实现切片：

- Identity 真实 PostgreSQL 集成测试覆盖列表、按 username/subjectId 加入、角色变更、移除、重新激活，
  并核对每次变更的 operation、reason code 与 request ID 审计；
- 授权负向路径覆盖缺 scope、`SERVICE` actor、跨 tenant、`MEMBER` 资源角色与 OWNER 修改失败关闭；
- Authorization Server 随机端口 HTTP + 实际 RSA 编解码 + 真实 PostgreSQL 测试覆盖 401、403、
  加入、列表、改角色、移除、统一 envelope 与审计落库，并证明操作的是 Identity 权威数据库；
- bootstrap 应用用例覆盖首次创建、重复执行不覆盖密码，以及 tenant code/username 部分占用冲突；
  密码最小长度与 Identity 领域规则统一为 12；
- 两份增量 migration 建立成员安全审计并补充原因码/`REACTIVATED` 约束；既有 migration 未改写。

## 参考

- [ADR-0005：Identity 与 OAuth 2.1 安全基线](0005-identity-and-oauth2-security-baseline.md)
- [ADR-0006：Workspace tenant 与资源授权](0006-workspace-tenant-authorization-baseline.md)
- [ADR-0007：Workspace 成员生命周期、所有权与授权审计](0007-workspace-membership-lifecycle-and-audit.md)
- [ADR-0013：受审计 OAuth tenant 服务客户端生命周期](0013-audited-oauth-service-client-lifecycle.md)
