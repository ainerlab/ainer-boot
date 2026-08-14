# ADR-0042：组织与员工目录 Greenfield 基线

- 状态：Accepted
- 日期：2026-08-14
- 决策者：Ainer 项目维护者
- 取代：[ADR-0032](0032-organization-workforce-directory-baseline.md)（tenant 模型已随 ADR-0033 Greenfield 删除，本文以 Workspace 语义合规取代）
- 被取代：无

## 背景

ADR-0032 完成了组织与员工目录的领域设计（四类主体关系区分、访问型目录而非 HRIS、有效期与
Assignment 不变量、防组织管理员间接提权），但其全部模型锚定 Tenant，并依赖已被 ADR-0033 S8
删除的 access-event outbox/relay 消费链路与 Identity tenant membership 治理。ADR-0033 之后：

- 身份是 `HumanAccount`/`ServicePrincipal` foundation，subject 无 tenant 归属；
- 资源治理边界是 **Workspace**（`workspace_id` + ACTIVE membership，OWNER/ADMIN/MEMBER）；
- 通用授权（ADR-0037）以 `Scope.Workspace/Resource/Global` 与 Binding 实时解析运行，
  无 ALLOW 缓存——撤销在下一次决策即生效，不依赖事件传播。

本文不重复 ADR-0032 已论证的备选方案与领域取舍（方案 A–E 的结论继续有效）；只重述被
Greenfield 改变的部分。未提及的条款（概念边界、时间语义、防提权原则、隐私边界）按 ADR-0032
原文继续有效，但其中的 tenant 语义一律按本文替换为 Workspace。

## 决策

### 1. 锚点：Workspace 取代 Tenant

1. `OrganizationDirectory` 归属一个 Workspace（`workspace_id`），是员工目录容器；不自动表示
   法律公司、商家或结算主体（产品关系仍归产品）。
2. 目录与全部子实体（OrgUnit、Engagement、Assignment、Position 等）显式保存
   `workspace_id`；内部复合外键 `(workspace_id, directory_id, id)` 阻止跨目录引用。
3. 目录治理复用 Workspace membership + OAuth scope：管理 API 要求可信 principal 持
   `organization.read`/`organization.manage` scope；目录归属 Workspace 的成员治理
   （OWNER/ADMIN 管理 Workspace）是产品装配层策略，不在模块内重新实现。
4. 一个 Workspace 可以有零到多个 OrganizationDirectory；C 端应用不装配本模块。

### 2. Subject 模型

1. `WorkforceEngagement` 的 subject 使用与 ADR-0037 一致的 authority 限定三元组
   `SubjectRef(issuerNamespace, subjectId, USER)`；`SERVICE` 主体不得成为员工（runtime
   SERVICE 不是员工，ADR-0032 §5.9 继续有效）。
2. issuer 必须等于产品配置的可信 Authorization Server issuer；subjectId 满足安全标识符
   模式。模块不查询 Identity 私表；账号存在性校验通过可选的产品装配端口提供，未提供时
   仅校验 issuer 与格式（不承诺账号活跃）。
3. Engagement 与 HumanAccount 是两个生命周期：调岗/离职不改写账号，也不猜测性撤销
   Workspace membership、直接 USER Binding 或产品 owner 关系。

### 3. 撤销语义：决策时实时解析（取代 ADR-0032 §9）

ADR-0032 §9 的 access-event outbox/tenant epoch 前置修复矩阵已随 Greenfield 删除。本模块的
「调岗/离职即时失权」由**拉取式解析**保证，与 ADR-0037 授权引擎同构：

1. 一切成员/任职事实查询（unit members、position assignees、O2 SubjectSet membership）
   在评估时间 `evaluationTime` 实时检查父链：Engagement `status IN (ENABLED)` 且
   `valid_period ∋ evaluationTime`，再检查 Assignment/PositionAssignment 自身状态与有效期。
   无事实缓存、无 ALLOW 缓存。
2. 暂停（SUSPENDED）与终止（REVOKED 或闭合 validUntil）在**下一次决策**即失去派生资格，
   不等待 JWT 自然到期，也不需要事件/outbox 传播。
3. 子关系（Assignment/PositionAssignment）有效期必须包含于父 Engagement 有效期；父失效后
   子关系即使未清理也不产生任何授权事实。
4. 决策审计与变更审计分离、append-only；审计不保存姓名、部门名、岗位标题之外的人事资料。

### 4. 首版模型（O1，本切片交付）

继承 ADR-0032 §3 的实体与不变量，锚点替换为 Workspace：

```text
OrganizationDirectory(id, workspaceId, code, displayName, status, version)
OrgUnit(id, workspaceId, directoryId, code, displayName, kind[ROOT|UNIT], status, version)
OrgUnitParent(id, workspaceId, directoryId, childUnitId, parentUnitId, validPeriod, status)
WorkforceEngagement(id, workspaceId, directoryId, subjectRef, engagementType,
  employeeNumber?, validPeriod, status, version)
UnitAssignment(id, workspaceId, directoryId, engagementId, orgUnitId,
  kind[PRIMARY|SECONDARY|ACTING], validPeriod, status)
Position(id, workspaceId, directoryId, orgUnitId, code, displayName, status, version)
PositionAssignment(id, workspaceId, directoryId, positionId, engagementId,
  unitAssignmentId, kind, validPeriod, status)
```

不变量（数据库 + 服务层双层强制）：

1. 每 Directory 恰好一个 ROOT Unit（部分唯一索引）；ROOT 不可有 parent。
2. UnitParent 同目录、无自指、无环（锁 + 参数化递归 CTE 检查）。
3. 同 Directory 内同一 Subject 的 Engagement 有效期不得重叠（PostgreSQL
   `tstzrange` + `EXCLUDE USING gist ... WITHOUT OVERLAPS`，`btree_gist`，仅对
   `status != 'REVOKED'` 生效）；重新入职创建新 Engagement。
4. `employeeNumber` 在 Directory 内唯一且不复用。
5. 同一 Engagement 同期最多一个 PRIMARY UnitAssignment（部分唯一索引）；
   SECONDARY/ACTING 可并存。
6. Position 的 orgUnit 创建后不可变；`(directoryId, orgUnitId, code)` 唯一。
7. PositionAssignment 引用的 UnitAssignment 必须同 Engagement 且落同一 Unit（复合 FK）。
8. 调岗以同一时刻 T 在单事务关闭旧 Assignment、创建新 Assignment，T 前后无双重资格。
9. 历史记录与审计不物理级联删除。

### 5. 模块与装配

1. `ainer-module-organization` 装配在 `ainer-server`，`ainer.organization.enabled`
   默认开启（`matchIfMissing`），off-state 冒烟以 `false` 排除。
2. 管理 API 是命令式端点（create/transfer/suspend/terminate），不提供绕过不变量的任意表
   CRUD；分页 ≤100；错误码 `AINER.ORGANIZATION.*` 稳定字符串，真实 HTTP 状态码。
3. 查询面：目录分页、Unit 树、Engagement 当前状态、Unit 成员（决策时实时解析语义）。
4. Authorization 核心不依赖本模块；O2 的 SubjectSet 集成（position assignee
   `SubjectSetBinding`、`SubjectSetMembershipResolver`、GrantImpactPolicy 与自提权防护）
   以 ADR-0037 扩展形式另行交付，本切片不实现，也不预建空端口实现。
5. Team/TeamMembership、UnitLeadership、ReportingLine、SCIM/HRIS adapter 维持 ADR-0032
   的按需追加原则，不空建。

### 6. 分阶段路线

| 阶段 | 内容 | 状态 |
|---|---|---|
| O1 | 目录/Unit/Engagement/Assignment/Position 基线 + 管理 API + 审计 + 实时成员解析 | 本切片 |
| O2 | SubjectSetBinding + position assignee resolver + 授权集成 + 防提权矩阵 | 后续 |
| O3 | Team/Leadership/ReportingLine、子树 SubjectSet、SCIM adapter | 真实需求后 |

## 后果

- 正面：模型与 ADR-0037 授权同锚（Workspace），撤销语义复用无缓存实时解析，删除了整个
  outbox 前置矩阵；C 端不装配即零影响。
- 负面：评估路径常驻数据库查询（与授权 Binding 解析同级，O2 需批量 resolver 与代表性查询
  计划验证）；Workspace membership 治理策略由产品装配，模块不自带目录管理员模型（O2 引入
  GrantImpactPolicy 时补齐）。
- 首个消费者仍是 `xq-platform-next`；第二个独立消费者完成兼容验证前，领域 API 视为
  incubating，可能调整。

## 验收

- **O1（2026-08-14）已交付并验证**：`ainer-module-organization`（第 25 个 reactor 模块），
  migration `V202608140400` 从空库重放（含 `btree_gist` 扩展与 EXCLUDE 约束）；服务层
  PostgreSQL 18.3 Testcontainers 测试 8 项（目录原子 ROOT、重复编码 409、任职期重叠 409、
  员工编号不复用、终止后可重新入职、开放 PRIMARY 唯一、有效期包含、调岗单事务 T 前后
  无双重资格、暂停后成员投影立即为空且子分配未清理、岗位同 Unit 同 Engagement 校验、
  UUIDv7 断言）+ 真 JWT HTTP 测试 6 项（401/403/201/409/422、审计行、成员投影终止后立即
  为空）全部通过，0 skipped。
- O2 交付前不得宣称「组织派生授权」或 SubjectSet 能力。

## 参考

- [ADR-0032（被取代）](0032-organization-workforce-directory-baseline.md)
- [ADR-0033 Greenfield](0033-account-workspace-subject-isolation-greenfield-baseline.md)
- [ADR-0037 post-Greenfield 授权](0037-post-greenfield-authorization-baseline.md)
- [ADR-0040 P3 企业基座与 1.0 产品契约](0040-p3-enterprise-base-and-1.0-product-contract.md)
