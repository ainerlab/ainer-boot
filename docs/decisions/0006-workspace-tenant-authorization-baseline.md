# ADR-0006：Workspace 租户归属与资源授权基线

- 状态：Accepted
- 日期：2026-07-22

## 背景

M1 的 Workspace 是用于验证领域、事务、MyBatis、Flyway 与 PostgreSQL 的技术样本。它允许请求体指定 owner，Workspace 表没有 `tenant_id`，查询也没有租户或成员条件。该模型不能作为企业多租户产品的安全边界。

M3 已建立由 Bearer JWT 验证得到的 `AuthenticatedActor`，其中 `sub` 表示主体、`tenant_id` 表示当前租户、scope 映射为 `SCOPE_*` authority。Workspace 必须只使用这份可信身份，不能继续接受客户端自报 owner 或 tenant。

## 决策

1. `ainer_workspace` 与 `ainer_workspace_member` 都持久化非空 `tenant_id`。成员表使用 `(tenant_id, workspace_id)` 复合外键再次约束租户归属。
2. Workspace 创建者和租户只从 `AuthenticatedActor` 获取。创建 HTTP 请求不再包含 `ownerSubjectId`，创建者自动成为 OWNER。
3. 授权分两层：
   - `SCOPE_workspace.read` / `SCOPE_workspace.write` 判断调用者能否使用某类 Workspace 能力；
   - Workspace 成员关系判断调用者能否访问具体资源，OWNER/ADMIN 可管理，MEMBER 只读。
4. 应用用例显式接收 `AuthenticatedActor` 并执行授权，不能只依赖 Controller 或 URL 规则。这样内部调用、消息入口和未来远程适配器也不能绕过资源检查。
5. 所有 Workspace 读取、更新和分页 SQL 都包含绑定参数形式的 `tenant_id`；分页还必须包含当前 `subject_id` 的成员关系。
6. 跨租户和非成员访问统一返回 Workspace `NOT_FOUND`，避免通过资源标识探测存在性；已是成员但角色不足时返回 403 `ACCESS_DENIED`。
7. 通用成员接口不能授予 OWNER。所有权转移必须由未来的专用用例处理，并同时保证唯一 OWNER、审计与并发一致性。
8. 已存在但无法可信归属的 M1 样本数据迁移到 `legacy-unassigned/<workspace-id>` 隔离租户，不能自动猜测真实租户。上线前由运维显式映射或清理。

## 为什么不在本阶段直接启用 PostgreSQL RLS

PostgreSQL Row-Level Security 可以作为纵深防御，但共享连接池下必须保证每个事务可靠设置并清除租户上下文，数据库角色还不能拥有绕过策略的权限。任何遗漏都会导致错误拒绝或跨请求上下文污染。

本阶段先建立可审查的显式 tenant SQL、复合约束和应用授权测试。只有在连接池事务钩子、migration/运维角色、后台任务、备份与测试策略全部明确后，才通过独立 ADR 引入 RLS；引入后也不能删除应用层授权。

## 验收行为

- 客户端不能指定 Workspace owner 或 tenant。
- 缺少 read/write scope 默认拒绝。
- 同一主体切换到其他 tenant 后不能读取原 tenant Workspace。
- 同 tenant 非成员不能按 ID 或分页发现 Workspace。
- MEMBER 可读但不能重命名或管理成员；OWNER/ADMIN 可管理。
- 所有 Workspace Mapper 查询和更新都绑定 tenant 参数。
- PostgreSQL migration 验证 tenant 列、约束、索引与复合外键。

## 后果与未覆盖范围

正面：

- Workspace 从数据库样本升级为可信租户资源，可作为后续 AI Agent、知识库和商业数据权限的参考切片。
- Scope 与资源角色职责分离，既不把所有资源 ID 塞入 JWT，也不把“已登录”误当成授权。
- 应用层和数据层同时携带租户条件，越权缺陷更容易在代码审查和测试中发现。

成员邀请、角色变更、移除、单一 OWNER、所有权转移与授权决策审计已由 [ADR-0007](0007-workspace-membership-lifecycle-and-audit.md) 落地。仍需后续建设：

- Identity Directory/用户选择契约、账号禁用与租户成员撤销事件；
- 授权审计查询、保留期、归档、SIEM 导出、告警控制面和批量权限管理；
- PostgreSQL RLS 或独立数据库/Schema 等更强隔离发行方案；
- 对其他业务资源复用同一原则，但不复制 Workspace 的具体表结构。

## 参考

- [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)
- [Spring Security Authorization](https://docs.spring.io/spring-security/reference/7.0/servlet/authorization/index.html)
- [PostgreSQL Row Security Policies](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)
