# ADR-0032：组织与员工目录基线

- 状态：Proposed
- 日期：2026-08-02
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无

## 背景

Ainer 的 P3 目标包含组织/成员、角色、数据范围和首个外部消费者。`xq-platform-next` 又需要同时
支撑小趣知物、小趣藏物、公司运营后台和未来 App，因此必须区分四类主体关系：

- C 端顾客与本人订单、收藏、咨询等产品关系；
- tenant OWNER/ADMIN/MEMBER 的 Identity 治理关系；
- 公司员工、合同人员、部门、岗位、团队和汇报关系；
- Merchant、Business Location、Seller、Operator、采购/品控等 XQ 产品关系。

常见 Java 脚手架把 `user.dept_id`、固定 Role 和 `data_scope=DEPT_AND_CHILD` 组合为权限系统。这种
模型能快速生成后台 CRUD，却无法安全表达一人多任职、跨部门 Team、未来生效、调岗、离职、C/B
双重身份、外部合作方和实时撤销。反过来，立即建设完整 HRIS 又会把薪酬、考勤、招聘、绩效和大量
个人信息引入脚手架，超出 Ainer 的平台边界。

ADR-0030 已提出 Permission、Role、直接 SubjectBinding、Scope、ReBAC 与 QueryPlan，但首版明确
不做 Group nesting。公司岗位/Team 的批量授权需要建立在该模型之上，同时不能把部门、岗位或
Group 写入 JWT/actorType。

当前实现还有一项前置风险：普通 tenant 成员角色变更/移除没有完整写入 access-event outbox，
Identity 已定义的 `IDENTITY_MEMBERSHIP_ROLE_CHANGED` 也不在 Workspace 消费合同中。若不先拆清
“Token 失效、Workspace 撤销、组织派生权限撤销”三种语义，未来调岗/离职可能继续使用旧权限，
或错误撤销独立的 Workspace 成员关系。

详细类型、数据表、接口、XQ 映射和任务拆解见
[`Ainer 组织与员工目录详细方案`](../design/organization-workforce-architecture-plan.md)。

## 决策驱动因素

- C 端应用关闭组织模块后仍可使用 tenantless USER 和产品 owner relation；
- B 端能够表达部门、员工任职、岗位、团队、汇报关系及时间边界；
- Tenant、Identity membership、员工任职、Workspace membership 和产品身份各有唯一事实所有者；
- 调岗、暂停和离职在下一次授权判断中生效，不等待 JWT 自然到期；
- 部门/岗位可参与授权，但不自动等于 Role、Permission 或产品数据范围；
- 首版保持模块化单体和 PostgreSQL 18，不引入完整 HRIS、关系图数据库或独立 PDP；
- Ainer 提供通用原语，XQ 保留 Company、Merchant、Location 与行业 Capability；
- 模块可选、可测试、可由真实消费者验证，不预建空模块。

## 备选方案

### 方案 A：沿用 `user.dept_id + role + data_scope`

实现和管理 UI 最简单，但把账号、任职和部门单归属绑定在一起；岗位名或角色会隐式扩张为数据
范围。它无法可靠处理同一用户既是顾客又是员工、多任职、外部合作方、调岗时间边界和产品资源
关系。不采用。

### 方案 B：把部门、岗位和员工表并入 Identity/Authorization Server

登录和管理 API 看似集中，却会让凭据安全边界拥有业务组织、员工隐私和产品权限生命周期；
Authorization Server 也会变成通用企业后台。Identity 必须保持账号、tenant 治理和撤销职责，
不采用。

### 方案 C：全部由 `xq-platform-next` 实现

能最快满足一个产品，但 Ainer 的 P3 明确把组织/成员列为脚手架能力，其他 B 端消费者还会重复
实现同样的不变量。XQ 应先成为真实验证者，而不是永久拥有 Ainer 通用员工目录。单独不采用；
模型在第二个独立消费者前保持 incubating，避免过早冻结公共 API。

### 方案 D：立即建设完整 HRIS

可以覆盖更广人事流程，但会引入大量高敏数据、合规和流程复杂度，且不是两个小程序后台的必要
条件。不采用；未来通过专业 HRIS/SCIM adapter 提供授权所需最小事实。

### 方案 E：可选的访问型 Organization/Workforce Directory（采用）

创建职责窄、可关闭的 `ainer-module-organization`，只拥有组织结构、员工任职和授权所需关系；
使用有效期、显式 SubjectSetBinding 和产品 QueryPlan 与 Authorization 集成。采用。

## 决策

### 1. 模块和运行边界

1. 在出现 O1 真实切片时创建单一 `ainer-module-organization`，装配在 `ainer-server`；在此前不创建
   空目录、POM、远程 client 或 migration。
2. 模块内部按 Organization 与 Workforce feature 分区。只有它们出现独立发布/团队/数据生命周期
   后，才按 ADR-0024 评估拆成两个 Maven 模块或服务。
3. `ainer-authorization-server` 继续只装配 Identity 与 OAuth/OIDC；不得为统一管理 UI 把组织/员工
   Controller 或 migration 放入 Authorization Server。
4. Organization 使用 Identity Directory 端口验证 tenant/Subject，不查询 Identity 私表，也不与
   Identity 数据库建立跨边界 FK。
5. Authorization 核心不依赖 Organization 实现。双方通过 Spring-free SubjectSet membership 与
   grant impact 端口连接，禁止注入对方 Mapper/Service 实现。
6. 初期模块标记 incubating；独立临时 Golden Consumer 先验证发布制品边界，
   `xq-platform-next` 是第一个真实产品消费者。第二个独立产品消费者完成兼容验证前不承诺全部
   领域类型为长期稳定公共 API。

### 2. 概念边界

1. Tenant 是 Identity 数据隔离与治理边界；每个 tenant 可以有零到多个 OrganizationDirectory。
2. OrganizationDirectory 是员工目录容器，不自动表示法律公司、商家或结算主体。
3. Company/LegalEntity、Merchant、BusinessLocation、Customer、Seller、MerchantOperator 与行业
   Capability 由 `xq-platform-next` 拥有；与 Organization 的关系使用 XQ 自有显式 link。
4. Identity User/Subject 是登录主体；WorkforceEngagement 是 Subject 在一个 OrganizationDirectory
   中的一段有效员工/合同人员关系。二者不能使用同一个 ID 或生命周期。
5. `TenantMembership != WorkforceEngagement != WorkspaceMember`。任何一条关系变化都不能未经
   明确策略改写另外两条。
6. Position 是 Unit 内的组织岗位，Authorization Role 是 Permission 集合；Position code/name 不能直接
   参与 `hasRole` 或 Permission 判断。
7. OrgUnit 是组织结构，不是通用 Scope 树。Team 是非层级协作集合，不能伪装为 OrgUnit 子节点。

### 3. 首版模型

O1 实现：

```text
OrganizationDirectory(id, tenantId, code, displayName, status, version)
OrgUnit(id, directoryId, tenantId, code, displayName, kind[ROOT|UNIT], status, version)
OrgUnitParent(id, directoryId, tenantId, childUnitId, parentUnitId, validPeriod, status, version)
WorkforceEngagement(
  id, directoryId, tenantId, subjectRef, engagementType,
  employeeNumber?, validPeriod, status, version)
UnitAssignment(
  id, directoryId, tenantId, engagementId, orgUnitId,
  kind[PRIMARY|SECONDARY|ACTING], validPeriod, status, version)
Position(id, directoryId, tenantId, orgUnitId, code, displayName, status, version)
PositionAssignment(
  id, directoryId, tenantId, orgUnitId,
  engagementId, positionId, unitAssignmentId,
  kind[PRIMARY|SECONDARY|ACTING], validPeriod, status, version)
```

O1 要求有效 Engagement 关联可验证的 USER Subject，不建立 Person/Worker 人事主档。以后出现无账号
人员、预入职或一人多身份映射的真实需求时另立模型，不能把 employee row 当成 Identity user。

Position 是绑定到一个 OrgUnit 的组织岗位，不是跨部门 JobDefinition。PositionAssignment 的
UnitAssignment 必须落在同一 Unit 并属于同一 Engagement；同名岗位位于不同 Unit 时使用不同
Position ID，因而 `position#assignee` 不会把另一个部门的同名岗位成员带入集合。通用职类/职级
目录有真实 HR 用例后再设计。Position 的 tenant/directory/orgUnitId 创建后不可变；跨 Unit 变更
创建新 Position 并显式转移 Assignment，不能原地改变已有 SubjectSet 的含义。

岗位授权闭环之后按真实需要增加 Team/TeamMembership、UnitLeadership 或 ReportingLine，不能同时空建。
UnitLeadership 显式关联 Unit 与 HEAD/DEPUTY PositionAssignment；ReportingLine 若实现，绑定
PositionAssignment→PositionAssignment，使同一人多任职时每份任职拥有独立汇报关系。两者都不
因“负责人”名称自动产生权限。

### 4. 生命周期和时间

1. 授权相关关系统一使用可信服务端时间和半开区间 `[validFrom, validUntil)`；一次 decision 复用
   同一个 evaluationTime。
2. 状态使用 `ENABLED|SUSPENDED|REVOKED` 作为人工门禁；未来开始/自然结束由有效期决定，不依赖
   定时任务切换 ACTIVE。
3. 同 OrganizationDirectory 内，同一 Subject 的 Engagement 有效期不得重叠；重新入职创建新 Engagement。
   可选 employeeNumber 在 OrganizationDirectory 内唯一且不复用，不能作为登录名、Subject ID 或凭据。
4. 同一 Engagement 同期最多一个 PRIMARY UnitAssignment；SECONDARY/ACTING 可以并存，但不覆盖
   主任职，也不成为授权捷径。
5. 子 Assignment、TeamMembership、UnitLeadership 与 ReportingLine 的有效期必须包含于父
   Engagement/Assignment 有效期，不能在父关系结束后继续生效。
6. 调岗以同一个时间 T 在单事务关闭旧 Assignment、创建新 Assignment；T 前后不出现双重 grant。
7. 暂停/离职首先让父 Engagement 无效。所有 Position/Team resolver 必须同时检查父 Engagement，
   因而子关系尚未清理时也不能产生 ALLOW。
8. 离职不会自动禁用 Identity 账号、删除 Customer/Seller，也不会猜测性撤销 TenantMembership、
   直接 USER Binding 或产品 owner 关系。offboarding orchestrator 必须显式列出并幂等收口。
9. 历史任职、Assignment、change audit 和 decision audit 不物理级联删除。

### 5. SubjectSet 授权扩展

1. ADR-0030 的直接 USER/SERVICE SubjectBinding 仍先独立完成；Organization 不阻塞直接 Binding 首闭环。
2. O2 新增独立 `SubjectSetBinding`，与直接 Binding 共享 Role、Scope、有效期、撤销和 Decision 语义：

```text
SubjectSetSelector(objectType, objectId, relation)  // 仅 adapter 查找键
SubjectSetRef(
  objectType, objectId, relation,
  authoritativeTenantId, directoryId)               // owner resolver 构造

BindingSubject = DirectSubject(SubjectRef) | SubjectSetRef
```

3. 首个且唯一必做集合为 unit-scoped `workforce.position:P#assignee`；Team、direct unit 和 unit
   subtree 在真实用例出现后逐个增加。
4. requester 仍是 USER/SERVICE。SubjectSet 只作为 Binding 的目标，不新增 GROUP actor，不进入 JWT，
   不支持嵌套、递归 userset 或管理员自定义关系表达式。
5. Authorization 定义 `SubjectSetDescriptorResolver` 和可批量 `SubjectSetMembershipResolver`
   registry；Organization 返回权威 tenant/directory/status/version，以及
   `MEMBER|NOT_MEMBER|UNAVAILABLE`、fact version 和 validUntil。selector 不能直接进入授权核心。
6. SubjectSetBinding 显式保存 tenant_id/directory_id；创建和每次 decision 都校验 descriptor、
   Role tenant、Scope/resource tenant 一致。未知、异常、跨 tenant 和不完整事实默认拒绝。
7. Authorization Decision `validUntil` 取 Binding、Engagement、Assignment 和产品约束的最早到期
   时间；初版不缓存 ALLOW。
8. decision audit 记录 binding ID、SubjectSetRef、Engagement/UnitAssignment/PositionAssignment
   ID/version、fact version/validUntil，不复制姓名、部门名和
   岗位标题。
9. Agent 代行时对 represented principal 解析 SubjectSet，再与 ActingGrant/Capability 取交集；
   runtime SERVICE 不被当成员工。

### 6. 防止组织管理员间接提权

修改已经绑定 Role 的 Position/Team 成员资格，本质上可能授予或撤销权限：

1. 组织显示信息、任职调动和 SubjectSet Role 绑定使用不同 Permission；
2. SubjectSetBinding 只能由 Authorization 的 `GrantAdministrationPolicy` 创建；
3. 组织 Assignment/Membership 的 grant-increasing 变更在写入前调用 `GrantImpactPolicy`，提交时重新
   校验版本；
4. 普通管理员不得新增、恢复、延长或调入自己的 Assignment，也不能绕过统一 invariant 直接恢复
   父门禁；自愿 relinquish 只走专用 grant-reducing 命令；
5. O2 禁止 system Role、GLOBAL Scope 和含 HIGH risk Permission 的 Role 使用 Organization SubjectSet；
6. SubjectSetBinding 的 tenant/directory/set/role/scope/validFrom/validUntil 创建后不可变；O2 不支持
   restore/replace/extend，任何变化必须 revoke + create 并重新执行完整 invariant。
7. Authorization 的 `SubjectSetGrantInvariant` 同时覆盖 catalog readiness、RolePermission 增加、
   Role 恢复、Binding 创建、Directory/Unit/Position 等父门禁恢复，以及集合成员
   新增/恢复/有效期延长；启用子树 SubjectSet 后还覆盖 Unit 层级移动。任何路径都不能把已绑定
   Role 事后升级为 HIGH/system permission。
8. O2 拒绝调用者给自己当前或已经计划加入的集合创建 Binding；将来放开必须由独立批准者承接。
9. grant-increasing 操作在 impact/invariant 不可用时失败关闭；暂停、离职、撤销和截止旧 Assignment
   属于 grant-reducing，本地父门禁可以先提交审计/outbox。紧急 transfer 使用 revoke-only，不能因
   新岗位分析失败而保留旧权限。
10. 员工流程创建的直接 USER Binding 带服务端生成的 WorkforceEngagement source reference；员工专用
   policy 仍要求当前有效 Engagement，Binding 本身不能绕过离职父门禁；
11. 终止 Engagement 的本地父门禁、change audit 与 outbox 先提交；Authorization 公开端口清理
   source-linked direct Binding 失败时由 outbox 重试，不能回滚暂停/离职。员工 policy 的父门禁检查
   保证清理期间也不 ALLOW；未链接来源的手工 Binding 作为 residual grant 显式列出；
12. 变更与 workforce change audit、access-change outbox 同事务；preview 只是信息，不是授权凭据；
13. 高风险岗位以后使用 source-linked 直接 Binding、审批或职责分离，不由普通部门调整自动赋权。

### 7. 列表与数据范围

不实现 `data_scope=DEPT_AND_CHILD` 或字符串拼接 `IN SQL`。三类语义分别处理：

- SubjectSetBinding 回答“哪些成员因岗位/Team 获得 Role”；
- Organization ReBAC 回答“谁是直属下属、直接 Unit 成员或受控子树成员”；
- 产品/Organization 的 `QueryAuthorizationPlanner` 返回类型化约束，由 owner Mapper 使用参数化
  SQL 翻译。

部门负责人、Team lead 或岗位名称不会自动获得员工敏感字段、全 tenant 资源、Merchant/Location
数据或产品工作队列。

### 8. 数据和接口

1. 所有 Ainer 表使用 UUIDv7，tenant-owned 表显式保存 `tenant_id UUID NOT NULL`，内部复合外键/约束
   阻止跨 tenant/directory 引用。
   UnitParent、UnitAssignment、PositionAssignment、TeamMembership、UnitLeadership、ReportingLine
   和 SubjectSetBinding 都必须显式保存 tenant_id/directory_id；Directory root 提供
   `(tenant_id, id)`，其余父表提供 `(tenant_id, directory_id, id)` 唯一键供复合 FK 使用。
   UnitAssignment 另提供 `(tenant_id, directory_id, id, engagement_id, org_unit_id)` 唯一键，
   PositionAssignment 以同列复合 FK 引用，防止借用同 Unit 另一名员工的 UnitAssignment。
2. 有效期优先评估 PostgreSQL 18 range 与 `WITHOUT OVERLAPS`/`PERIOD`；父子环用锁与参数化递归
   CTE 防止。O1 不把 ltree、closure table 或 trigger 作为前置。
3. 核心 Subject、OrgUnit、Position、状态、有效期和 Scope 使用普通列，不放 JSONB；outbox payload
   只保存稳定 ID、版本和 reason code。
4. MyBatis-Plus 只用于 infrastructure 简单 CRUD/分页；层级、有效期和授权影响查询使用显式 SQL。
5. Organization/Workforce 管理 API 位于 `ainer-server`，采用 create/transfer/suspend/terminate 等
   命令，不用任意表 CRUD 绕过不变量；OpenAPI/SDK 可由 Ainer Admin 消费。
6. 高风险写入、员工导出和审计查询纳入在线 Token 校验与 Step-up 策略；资源 tenant 从数据库/可信
   resolver 取得，不接受客户端自报。

### 9. 现有撤销链路前置修复

在 O2 宣称“调岗/离职即时失权”前必须完成并验证：

1. 普通 tenant member 角色变更在同事务写 access event，使事件前签发、仍含旧 role claim 的 Token
   在在线检查点失效；普通成员移除同时产生下游撤销事件。
2. 重新设计 access event routing：
   - user disable / membership revoke 可以撤销认证访问与明确依赖 Identity membership 的下游关系；
   - role change 只失效旧 Token/相关 Authorization 投影，不能删除独立 Workspace membership；
   - workforce change 只失效 workforce-derived Binding/事实和显式订阅的产品 assignment；
   - organization hierarchy change 只失效相关关系事实/查询计划。
3. Workspace consumer 对每个接受的 event type 有独立语义、数据库 CHECK、幂等 receipt 和负向测试；
   不允许枚举扩展后复用“统一 revoke member”逻辑。
4. 当前 access event 强制 subject，不能表达 tenant-wide disable；Organization 上线前必须定义
   tenant epoch/event 或等价在线门禁，并验证 tenant 停用后全部 Directory/SubjectSet 拒绝。
5. 新增 `/api/organization-directories/**` 等路径前更新在线校验与 Step-up 默认策略；默认关闭能力不得被描述为
   已经保护新增端点。
6. 身份、tenant、Organization、workforce 和产品撤销形成矩阵测试，覆盖重复、乱序、耗尽重放和
   部分消费者不可用。

## 后果

### 正面

- C 端主体无需部门或 tenant membership，B 端员工又能使用同一认证/授权原语；
- 部门、岗位、Team、Role 和产品 Capability 不再互相冒充；
- 多任职、调岗、离职和重新入职拥有清楚的时间与历史语义；
- XQ 可以复用通用员工目录，同时保留 Company/Merchant/Location 和行业关系的所有权；
- 模块关闭时不污染最小/C 端应用，未来可按真实条件服务化或接入 SCIM/HRIS。

### 负面与风险

- 比 `user.dept_id` 增加了有效期、版本、Assignment 和跨模块端口，管理 UI 与测试成本更高；
- SubjectSet 使普通调岗可能成为授权变更，必须实施 impact policy、审计与自提权防护；
- 模块化单体内的当前事实查询会增加授权读路径，需要批量 resolver 和代表性查询计划验证；
- Identity、Workspace 与未来 Workforce 事件语义必须先拆清，否则撤销传播可能过宽或不足；
- 第一个消费者不足以证明所有组织模型通用，incubating API 仍可能在第二个消费者前调整。

## 安全、数据与隐私

- Ainer O1 只保存授权所需的最小员工目录字段，不保存身份证件、银行、薪资、合同正文、健康或家庭
  数据；
- Subject issuer/ID、tenant、Organization 与关联关系均由服务端解析和数据库约束验证；
- change audit 与 decision audit 分表、append-only，审计/事件/日志不保存自由文本敏感资料；
- 普通目录投影、员工编号、历史、导出和审计分别授权并使用 FieldMask；
- 指标不使用 tenant、subject、员工号等高基数/个人标签；
- 暂停、离职、绑定撤销和账号禁用分别有明确定义，任何未知 provider/obligation 默认拒绝。

## 运维与迁移

1. 先修复并验证第 9 节 Identity/Workspace 撤销矩阵，不改写已执行 migration；
2. 先交付 ADR-0030 的直接 Binding，再增加 Organization O0/O1；
3. O1 migration 只创建模块自有表，Organization 与 Identity/XQ 通过端口或稳定 ID 关联；
4. O2 才创建 SubjectSetBinding 与 position resolver，Role/Binding 管理面明确标注 provenance；
5. `xq-platform-next` 先消费一个岗位+产品资源纵向切片，失败时可关闭 SubjectSet integration，直接
   Binding 和 Organization directory 数据仍可独立运行；
6. Team、ReportingLine、SCIM、子树 SubjectSet 逐项追加，不能一次性空建；
7. 拆服务时需按 ADR-0024 增加远程 adapter、超时/重试、一致性、缓存失效和事件 SLO，不能只改
   `ainer.runtime.mode`。

## 验收记录

- 本 ADR 与详细方案已完成模型、边界、撤销前置问题和 XQ 场景的设计复核；
- 当前未创建 `ainer-module-organization`、数据库表、SubjectSetBinding、API 或管理页面；
- O0–O3 的 PostgreSQL、真实 HTTP、并发、撤销、Golden Consumer 与性能验证均未完成；
- ADR 在实现与验收完成前保持 Proposed，不能在 README、发布说明或管理 UI 中描述为已交付能力。

## 参考

- [RFC 7643：SCIM Core Schema](https://www.rfc-editor.org/rfc/rfc7643.html)
- [RFC 7644：SCIM Protocol](https://www.rfc-editor.org/rfc/rfc7644.html)
- [NIST Role Based Access Control](https://csrc.nist.gov/projects/role-based-access-control)
- [OpenFGA：Usersets](https://openfga.dev/docs/modeling/building-blocks/usersets)
- [PostgreSQL 18 Temporal Constraints](https://www.postgresql.org/about/featurematrix/detail/temporal-constraints/)
- [ADR-0018：管理授权模型与租户成员管理](0018-management-authorization-and-tenant-member-management.md)
- [ADR-0020：PostgreSQL Native-First Greenfield 数据基线](0020-postgresql-native-greenfield-baseline.md)
- [ADR-0024：演进式模块化平台架构](0024-evolutionary-modular-platform-architecture.md)
- [ADR-0030：通用混合细粒度授权基线](0030-hybrid-fine-grained-authorization-baseline.md)
- [ADR-0031：Agent 代行、Capability 与 AI 上下文授权基线](0031-agent-delegation-and-ai-context-authorization.md)
