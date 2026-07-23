# ADR-0007：Workspace 成员生命周期、所有权与授权审计

- 状态：Accepted
- 日期：2026-07-22

## 背景

ADR-0006 已建立可信 tenant、scope 与 Workspace 资源角色的双层授权，但第一版成员接口仍会立即激活任意 `subjectId`。如果 Workspace 为了验证受邀主体而直接读取 Identity 表，会让两个业务模块共享数据所有权；如果完全不验证，又可能把输错或伪造的主体直接变成有效成员。

同时，Workspace 还缺少角色变更、移除、唯一 OWNER、原子所有权转移和可持久化的授权决策记录。

## 决策

1. 新成员先进入 `PENDING`，不能读取或管理 Workspace。
2. 只有当前已验证 JWT 的 `tenant_id` 与 Workspace tenant 一致，且 `sub` 等于受邀 `subjectId` 时，才能通过“本人接受”用例变为 `ACTIVE`。这利用 Identity Provider 已验证的主体和租户，不要求 Workspace 直查 Identity 表。
   受信 issuer 的接入契约必须保证 `tenant_id` 表示该主体当前被允许使用的租户上下文；只验证 JWT 签名但不约束 claim 语义不满足此决策。
3. Workspace 只把 `ACTIVE` 成员视为资源关系。PENDING 邀请既不是登录身份，也不是授权。
4. 通用角色变更只能在 ADMIN 与 MEMBER 之间进行；OWNER 不能通过通用接口授予、降级或移除。
5. 每个 Workspace 必须最多存在一个 ACTIVE OWNER，由 PostgreSQL 部分唯一索引保证。
6. 所有权转移是专用事务：先锁定 Workspace 行，确认调用者是当前 OWNER、目标是 ACTIVE 成员，再把旧 OWNER 降为 ADMIN、目标提升为 OWNER。任何一步失败都整体回滚。
7. OWNER/ADMIN 可以移除非 OWNER 成员。移除 PENDING 邀请等价于撤销邀请；移除 ACTIVE 成员立即撤销后续资源访问。
8. Workspace 持久化授权审计。授权失败以及创建、改名、邀请、接受、角色变更、移除、所有权转移等授权成功决策都记录 tenant、Workspace、actor、target、action、decision、稳定 reason code 和时间。
9. 授权审计使用独立事务。对受保护写操作，审计不可用时操作失败并回滚；不能为了业务可用性静默丢弃安全审计。
10. 审计表不外键关联 Workspace，确保不存在资源的拒绝、资源删除后的历史和跨租户探测都能保留。

## API 语义

- `POST /api/workspaces/{id}/members`：创建 PENDING 邀请。
- `POST /api/workspaces/{id}/membership-acceptances`：当前主体接受自己的邀请。
- `POST /api/workspaces/{id}/member-role-changes`：修改非 OWNER 成员角色。
- `POST /api/workspaces/{id}/member-removals`：移除非 OWNER 成员或撤销邀请。
- `POST /api/workspaces/{id}/ownership-transfers`：当前 OWNER 转移所有权。

这些路径表示业务操作产生的资源，不使用 `/updateRole`、`/deleteMember` 一类 RPC 动词路径。

## 后果与限制

正面：

- Workspace 不需要共享 Identity 数据库，也能证明接受邀请者就是可信 token 中的目标主体。
- 管理员无法直接把一个未经目标主体确认的邀请变成有效访问权。
- 所有权不再只是 Java 约定，同时拥有数据库唯一性和串行化事务保证。
- 高价值权限变化具备独立于普通访问日志的审计记录。

限制：

- Identity 已提供 ACTIVE tenant member 的安全 Directory 应用契约；跨运行时适配器和受服务身份保护的公共查询尚未实现。
- JWT 表示 token 签发时的 Identity tenant 上下文；禁用用户、租户切换和实时撤销仍依赖短 token 生命周期或后续 introspection/event 机制。
- 审计分页查询已实现；归档、保留期、SIEM 导出和告警控制面尚未实现。
- “最多一个 OWNER”由数据库保证；“始终至少一个 OWNER”由创建与转移事务保证，数据库无法用普通行约束独立表达。

## 参考

- [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)
- [Spring Security Authorization](https://docs.spring.io/spring-security/reference/7.0/servlet/authorization/index.html)
