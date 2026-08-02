# Ainer 组织与员工目录详细方案

> 文档类型：详细设计 · 状态：Proposed · 日期：2026-08-02
>
> 关联决策：[ADR-0030](../decisions/0030-hybrid-fine-grained-authorization-baseline.md)、
> [ADR-0032](../decisions/0032-organization-workforce-directory-baseline.md)
>
> 当前实现状态仍以 [`project-status.md`](../project-status.md) 为准；本文中的目标模块、类型、表和接口均未实现。

## 1. 结论

这里所说的 B 端，**包括公司内部员工管理**：公司可以维护部门、员工任职、岗位、跨部门团队和
汇报关系，并把这些事实用于后台授权、工作队列、数据范围和审计。

Ainer 需要提供的是一个可选的、面向访问管理的 **Organization/Workforce Directory**，不是一套
薪酬、考勤、招聘和绩效齐全的 HR 系统。建议在真实切片到来时创建单一
`ainer-module-organization`，内部同时容纳组织结构与员工任职两个紧密协作的 bounded context；
在没有实现、测试和消费者之前不创建空 Maven 模块。

核心关系如下：

```text
Identity Subject ── WorkforceEngagement ── OrganizationDirectory
                                            ├── OrgUnit hierarchy
                                            ├── Position assignment
                                            ├── Team membership       （第二切片）
                                            └── Reporting line        （第二切片）

Authorization Role/Binding ── SubjectSetRef(position#assignee / team#member)
Product policy ── merchant/location/queue/order/listing 等业务关系与状态
```

必须保持五个边界：

1. `Tenant` 是身份治理与数据隔离边界，不自动等于公司、商家、部门或 App；
2. `WorkforceEngagement` 是 Subject 在组织中的有效任职关系，不等于登录账号或劳动合同；
3. `Position` 是岗位，不等于 Authorization Role；
4. `OrgUnit` 是组织结构，不是自动递归的权限 Scope；
5. 员工离职只终止员工路径，不应删除同一 Subject 的顾客、Seller 或其他产品身份。

## 2. 它如何同时服务 C 端和 B 端

组织模块是可选能力。C 端产品不装配它，也不要求顾客成为员工或 tenant member；B 端后台按真实
需要装配它。同一个稳定 USER 可以同时拥有顾客身份和一个或多个员工任职，但每次请求只能沿调用
用例明确选择的完整授权路径执行。

| 场景 | 是否依赖 Organization/Workforce | 授权事实来源 |
|---|---:|---|
| 匿名查看已发布的行业信息 | 否 | `PublicAccessPolicy`、公开状态与公开投影 |
| 顾客查看本人订单、收藏和售后 | 否 | USER 与 Customer/Order 的 owner/participant 关系 |
| 商家经营者编辑所属 Listing | 不一定 | 受验证 Acting Identity、Merchant/Location 关系和产品 Binding |
| 公司员工以任职/岗位进入运营后台 | 当该 policy 选择员工路径时 | 有效任职、岗位或 Team SubjectSet、Role/Binding、产品关系 |
| 部门负责人查看直属员工 | 是 | 显式 manager policy、汇报关系与字段约束，不是“负责人默认看全部” |
| 外部合作方在指定经营点录货 | 不应强制依赖 | Partner Acting Identity、Location assignment 与产品 Capability |
| Identity tenant OWNER 管理成员 | 否 | `TenantMembership`；它不自动获得产品运营权限 |

因此它不会让 C 端模型变成“用户—部门—角色”三件套，也不会要求每开发一个小程序就新建 tenant、
部门树或一套用户表。

## 3. 领域所有权

| 概念 | 回答的问题 | 事实所有者 | 明确不表示 |
|---|---|---|---|
| Identity Subject | 谁在登录、凭据是否有效 | `ainer-module-identity` | 员工档案、部门、业务权限 |
| Tenant / TenantMembership | 谁拥有隔离边界，谁可治理 tenant | `ainer-module-identity` | 法律公司、商家、员工任职、产品岗位 |
| OrganizationDirectory | tenant 内哪一个员工目录容器 | `ainer-module-organization` | 法律实体或结算主体 |
| OrgUnit | 任职结构中的部门/组织单元 | `ainer-module-organization` | Merchant、Business Location、权限 Scope 树 |
| WorkforceEngagement | Subject 当前是否以员工/合同人员身份服务该组织 | `ainer-module-organization` | Identity 账号、劳动合同全文、Customer |
| Position | 组织定义了什么岗位及谁在该岗位任职 | `ainer-module-organization` | Authorization Role、产品 Capability |
| Team | 哪些任职关系组成非层级协作集合 | `ainer-module-organization` | Department 子树、客户归属、项目 SLA |
| Role/Binding/Decision | 谁因哪个显式 grant 获得什么 Permission | `ainer-module-authorization` | 组织结构和产品资源状态 |
| Company / Legal Entity | 谁签约、开票、结算和承担法律责任 | `xq-platform-next` Company/Party 域 | Tenant 或 OrgUnit 的别名 |
| Merchant / Business Location | 谁经营、在哪个经营点开展业务 | `xq-platform-next` Merchant 域 | Company、Department 或 Tenant 的别名 |
| Product Capability | 谁能执行采购、品控、拍摄、发布等行业动作 | `xq-platform-next` 产品域 | HR 岗位、AI Tool Capability |
| Acting Identity | 当前以哪个可验证业务身份行事 | XQ 产品域拥有关系；Ainer 提供通用引用/审计边界 | Header、凭据或岗位名称 |

不建立一张万能的
`organization(type=TENANT|COMPANY|MERCHANT|DEPARTMENT|TEAM|LOCATION)`。这些概念的生命周期、
隔离责任和授权语义不同，强行合并会让任何一次改名、调岗或商家迁移都可能改变错误的权限。

## 4. 核心领域模型

### 4.1 OrganizationDirectory

`OrganizationDirectory` 是一个 tenant 内的员工目录容器，不宣称自己是法律公司。首版允许一个 tenant 创建
零到多个 OrganizationDirectory，以便平台 tenant 运营多个内部组织，或未来独立接入多个目录；简单部署只
创建一个。

```text
OrganizationDirectory(
  id, tenantId, code, displayName,
  status[ENABLED|SUSPENDED|REVOKED],
  version, createdAt, updatedAt)
```

约束：

- `tenantId` 必须来自可信请求主体上限与 Identity Directory 校验，不能从请求体任意指定；
- `code` 在 tenant 内稳定且不复用，显示名称可以改变；
- `SUSPENDED/REVOKED` 是所有下属组织关系的父门禁；
- OrganizationDirectory ID、Tenant ID、Company ID 和 Merchant ID 永远不复用同一个值表达多重语义；
- XQ 若需把 Company/Merchant 与 OrganizationDirectory 关联，由 XQ 拥有显式 link，不在 Ainer 表中增加
  Company/Merchant 外键。

### 4.2 OrgUnit 与层级

```text
OrgUnit(
  id, directoryId, tenantId,
  code, displayName, kind[ROOT|UNIT],
  status[ENABLED|SUSPENDED|REVOKED],
  version, createdAt, updatedAt)

OrgUnitParent(
  id, directoryId, tenantId, childUnitId, parentUnitId,
  validPeriod=[from,to), status, version)
```

首版只把 `ROOT` 和普通 `UNIT` 固定为领域类型；“事业部、中心、部门、小组”等由显示层分类或产品
配置呈现，不把易变化的中文名称变成权限语义。每个 OrganizationDirectory 同一时刻只有一个有效 ROOT，
普通 Unit 同一时刻至多一个有效父节点。

层级规则：

- 父子必须属于同一个 tenant 和 OrganizationDirectory；
- 禁止自环、祖先移入后代、跨 OrganizationDirectory 移动和无上限递归；
- 层级变更按 OrganizationDirectory 串行化，并用参数化递归 CTE 检查环；
- `OrgUnit` 不物理删除，撤销前必须处理有效子单元与任职分配；
- `UNIT` 的上级关系采用半开有效期，调动时在同一事务关闭旧关系并建立新关系；
- 子树不会自动继承 Role、Permission 或产品数据访问；需要子树语义时必须由注册策略明确选择。

### 4.3 WorkforceEngagement

使用 `WorkforceEngagement`，而不是把 `employee` 字段直接塞入 Identity User。它代表某个稳定
Subject 在一个 OrganizationDirectory 中的一段员工/合同人员任职关系：

```text
WorkforceEngagement(
  id, directoryId, tenantId,
  subjectRef(issuer, subjectType[USER], subjectId),
  engagementType[EMPLOYEE|CONTRACTOR], employeeNumber?,
  validPeriod=[from,to),
  status[ENABLED|SUSPENDED|REVOKED],
  version, revokedAt?, revokedBy?, createdAt, updatedAt)
```

首版要求有效 Engagement 关联已经存在且可验证的 USER Subject。Ainer 不建立 Person/Worker 人事主档，
也不维护没有登录/授权需要的员工档案；将来出现“先入职后开户、一人多账号”真实需求时，再设计
`Worker + WorkerIdentityLink`，不能现在预建空模型。

生命周期规则：

- 同一 Subject 可以在不同 OrganizationDirectory 同时任职；同一 OrganizationDirectory 内任职期间不得重叠；
- `employeeNumber` 只是可选的内部员工编号；若存在，在 OrganizationDirectory 内唯一且不重新分配，
  不能充当登录名、Subject ID 或授权凭据；
- 未来开始和自然结束由 `[validFrom, validUntil)` 判断，不依赖午夜定时任务切换状态；
- `status` 是人工门禁，`SUSPENDED/REVOKED` 立即阻止所有组织派生授权；
- 离职先截止或撤销 Engagement，随后收口岗位、Team 和产品 assignment；即使子记录尚未清理，
  resolver 也必须因父 Engagement 无效而拒绝；
- 重新入职创建新的 Engagement，不能恢复或覆盖旧任职历史；
- 离职不会自动禁用 Identity USER，也不会删除 Customer、Seller、订单或收藏；
- `TenantMembership` 和 Engagement 是两条独立关系。移除 tenant member 不等于离职，离职也不自动
  把同一主体的所有 tenant 治理关系改写掉；组合 offboarding 用例必须显式执行需要的步骤。

### 4.4 UnitAssignment

```text
UnitAssignment(
  id, directoryId, tenantId, engagementId, orgUnitId,
  assignmentKind[PRIMARY|SECONDARY|ACTING],
  validPeriod=[from,to), status, version,
  createdAt, updatedAt)
```

- 一个 Engagement 同一时刻最多一个 PRIMARY UnitAssignment，可以有多个 SECONDARY/ACTING；
- PRIMARY 只用于默认展示和明确声明的产品策略，不是“主部门拥有全部权限”的捷径；
- 调部门在同一事务以同一个服务端时间 `T` 关闭旧分配并创建新分配，保证 `T` 时刻没有重叠空窗
  或双重授权；
- 调部门不会隐式退出 Team、撤销产品资源关系或改变 Acting Identity，调用方必须在命令中明确
  后续处理；
- 自己给自己调岗、跨 tenant 关联、伪造生效时间和并发覆盖必须失败。

### 4.5 Position 与 PositionAssignment

```text
Position(
  id, directoryId, tenantId, orgUnitId,
  code, displayName,
  status[ENABLED|SUSPENDED|REVOKED],
  version, createdAt, updatedAt)

PositionAssignment(
  id, directoryId, tenantId, orgUnitId,
  engagementId, positionId, unitAssignmentId,
  assignmentKind[PRIMARY|SECONDARY|ACTING],
  validPeriod=[from,to), status, version,
  createdAt, updatedAt)
```

`Position` 是绑定到一个 OrgUnit 的组织岗位，例如“供应链部/采购专员”“财务部/审核员”。它不是
一个 Role，也不直接包含 Permission。PositionAssignment 引用的 UnitAssignment 必须落在 Position
所属的同一个 OrgUnit，且二者必须属于同一个 Engagement，因此 `position#assignee` 自带确定的
员工任职与部门边界；同名岗位分布在不同 Unit 时必须创建不同 Position ID。跨 Unit 复用的
JobDefinition/职级目录不是 O1 目标，有真实 HR 用例后再单独设计。Position 的 directory、tenant 与
orgUnitId 创建后不可变；“把岗位移到另一部门”必须在目标 Unit 创建新 Position 并显式转移
Assignment，不能原地改变已有 SubjectSet 的含义。

岗位只有在 Authorization 管理员显式把 Role 绑定到
`workforce.position:<positionId>#assignee` 后才产生组织派生授权。岗位名称、编码或页面勾选不能在
运行时直接变成 `hasRole` 判断。

### 4.6 Team、UnitLeadership 与 ReportingLine（第二切片）

Team 是非层级、可跨部门的协作集合；ReportingLine 是任职之间的汇报关系。它们不能塞入 OrgUnit
树，也不能因同一个“负责人”字段而合并：

```text
Team(id, directoryId, tenantId, code, displayName, owningUnitId?, status, version)
TeamMembership(
  id, directoryId, tenantId, teamId, engagementId,
  relation[MEMBER|LEAD], validPeriod, status, version)

UnitLeadership(
  id, directoryId, tenantId, orgUnitId, leaderPositionAssignmentId,
  kind[HEAD|DEPUTY], validPeriod, status, version)

ReportingLine(
  id, directoryId, tenantId,
  reportPositionAssignmentId,
  managerPositionAssignmentId,
  kind[PRIMARY|DOTTED],
  validPeriod, status, version)
```

UnitLeadership 显式表达谁在一段时间内担任部门负责人；同一 Unit 同期最多一名 HEAD，可以有多名
DEPUTY；leader PositionAssignment 必须通过有效 UnitAssignment 落在该 Unit。汇报关系绑定
`PositionAssignment` 而不是 USER：同一人多任职时，每份任职可以有不同负责人。
同一个 report assignment 同期最多一条 PRIMARY 汇报线，可以有多条 DOTTED 汇报线；必须阻止
自我汇报和 PRIMARY 环。

Team lead 和 UnitLeadership 都不会自动获得产品或员工敏感数据。Team/Unit leader 可在以后作为
代码注册的 SubjectSet；ReportingLine 优先作为产品/员工查询中的显式 ReBAC 事实，例如“只看
直属下属的公开工作资料”。递归经理链首版不做。

## 5. 时间与历史语义

所有会影响授权的关系统一使用可信服务端时间和半开区间 `[validFrom, validUntil)`：

- 一次授权判断只取一个 `evaluationTime`，所有 provider 复用该时间；
- `validUntil` 为空表示开放结束时间，不表示永久不可撤销；
- UnitAssignment、PositionAssignment、TeamMembership、UnitLeadership 与 ReportingLine 的有效期
  必须包含于各自父 Engagement/Assignment 的有效期；子关系不能在父关系结束后继续有效；
- Decision 的 `validUntil` 取 Binding、Engagement、Assignment、TeamMembership 和产品约束中最早
  到期时间；
- 状态是人工覆盖门禁，时间区间是计划生效边界，二者同时满足才有效；
- 历史回溯更正必须使用高风险操作并同时保存 `effectiveAt` 和 `recordedAt`，不能改写已经产生的
  AuthorizationDecision；首版不建设完整双时态仓库；
- 物理实现优先评估 PostgreSQL 18 range、`WITHOUT OVERLAPS`/`PERIOD` 约束；最终 DDL 必须在真实
  PostgreSQL 18 Testcontainers 中验证，并遵循数据库设计规范，不能只靠应用先查后写防重叠。

## 6. 与通用授权的集成

### 6.1 不把部门或岗位塞进 Token

认证请求者仍然只有真实 USER/SERVICE。OrgUnit、Position 和 Team 是当前数据关系，不进入
`actorType`，也不把所有成员关系写入 JWT。有效成员关系可能随调岗、离职和 Team 变动即时改变，
授权必须读取当前事实。

ADR-0030 的首个闭环仍先交付只面向 USER/SERVICE 的直接 `SubjectBinding`。组织集成作为紧随其后的
受控扩展：

```text
SubjectSetSelector(objectType, objectId, relation)             // 仅 API 查找键
SubjectSetRef(
  objectType, objectId, relation,
  authoritativeTenantId, directoryId)                          // 仅服务端 resolver 构造

BindingSubject = DirectSubject(SubjectRef) | SubjectSetRef

SubjectSetBinding(
  id, tenantId, directoryId, subjectSetRef, roleId, scopeRef,
  validPeriod, status, version, revoked*)
```

Direct SubjectBinding 与 SubjectSetBinding 分表持久化、共享同一 Role/Scope/Decision 语义。API 中的
selector 只用于定位候选，Authorization 在创建 Binding 前必须调用 owner provider 得到带权威
tenant/directory 的 SubjectSetRef；普通请求不能构造 core ref。这样既不在尚未实现时破坏直接
Binding schema，也能让审计清楚区分“直接授予”和“因当前集合成员资格生效”。

### 6.2 首批 SubjectSet

只允许代码注册的类型和 relation，不接受任意字符串、管理员 SQL 或嵌套表达式：

| SubjectSetRef | 成员语义 | 首次切片 |
|---|---|---:|
| `workforce.position:P#assignee` | 当前有效 Engagement 中担任岗位 P 的 Subject | 是 |
| `workforce.team:T#member` | 当前有效 TeamMembership 的 Subject | 第二切片 |
| `workforce.team:T#lead` | 当前有效 Team lead | 第二切片 |
| `workforce.org-unit:U#leader` | 当前有效 Unit HEAD/DEPUTY | 第二切片 |
| `workforce.org-unit:U#direct-assignee` | 直接分配到 U 的 Subject | 第二切片后评估 |
| `workforce.org-unit:U#subtree-assignee` | U 与后代单元的 Subject | 有规模与安全用例后评估 |

首版不做 SubjectSet 嵌套、Team 包含 Team、通用 Group actor、递归 userset 或任意关系图数据库。
OrgUnit 子树是 Organization provider 的类型化关系，不是通用 ACL Scope 递归。

### 6.3 解析端口

Authorization 模块定义 Spring-free、可批量的端口，Organization 模块实现它：

```java
public interface SubjectSetDescriptorResolver {
    SubjectSetDescriptor resolve(SubjectSetSelector selector, UUID expectedTenantId);
}

public interface SubjectSetMembershipResolver {
    boolean supports(SubjectSetType type);

    SubjectSetMembershipBatchResult resolve(
            SubjectRef subject,
            Set<SubjectSetRef> candidates,
            Instant evaluationTime);
}
```

每个结果至少包含 `MEMBER|NOT_MEMBER|UNAVAILABLE`、关系事实版本和 `validUntil`。未知 set、跨 tenant、
provider 异常、超时或事实不完整都不能产生 ALLOW。Authorization 首版不缓存 ALLOW；将来增加缓存时
必须以事实版本/事件失效和 Decision `validUntil` 共同约束。

Binding 创建和每次决策都必须验证 SubjectSetRef 当前 descriptor：set 状态有效，binding 的
`tenantId/directoryId` 与 descriptor 一致，Role 属于同 tenant，Scope/resource 的权威 tenant 也一致。
数据库不建立跨模块 FK，但不能以“UUID 很难猜”替代 owner 解析。

`position#assignee` 只有在 OrganizationDirectory、WorkforceEngagement、OrgUnit、UnitAssignment、
Position 和 PositionAssignment 均属于同一 tenant/directory，且各自状态与有效期都成立时才返回
MEMBER；不能只查询 PositionAssignment 一张表。Team/leader resolver 以后也必须检查 Engagement 和
所有父门禁，任何孤儿或状态冲突默认拒绝。

唯一 live predicate 为：Directory/Unit/Position 状态有效，Engagement、UnitAssignment 与
PositionAssignment 的状态有效且区间都包含同一个 evaluationTime，Position.orgUnitId 等于
PositionAssignment.orgUnitId 并等于 UnitAssignment.orgUnitId，所有 tenant/directory 相等。
PositionAssignment.engagementId 还必须等于 UnitAssignment.engagementId，且两者指向同一个有效
Engagement；子有效期包含于该 UnitAssignment/Engagement。membership fact 与 decision provenance
至少记录 Engagement、UnitAssignment、PositionAssignment 的 ID/version，以及 descriptor version；
`validUntil` 取所有关系的最早结束时间。

Evaluator 的岗位路径是：

```text
真实 USER principal
  ∩ OAuth scope ceiling
  ∩ principal ∈ workforce.position:P#assignee
  ∩ live SubjectSetBinding(P#assignee → Role → Permission → Scope)
  ∩ 产品拥有的资源关系、状态、渠道和风险约束
  → ALLOW | DENY | CHALLENGE
```

Agent 代行时，SubjectSet 对被代表的 principal 求值，再与 ActingGrant/Capability 取交集；不能把
runtime SERVICE 当成公司员工。

### 6.4 组织变更也是潜在授权变更

如果岗位或 Team 已经绑定 Role，把某人分配进去就可能间接授予 Permission。因此：

- `organization.*.write` 与 `authorization.subject-set-bindings.write` 是不同权限；
- 能编辑组织显示信息不等于能调动成员，能管理 Team 不等于能把自己加入高权限 Team；
- 首版禁止 system Role、GLOBAL Scope 和含 HIGH risk Permission 的 Role 绑定 Organization SubjectSet；
- SubjectSetBinding 的 tenant、directory、SubjectSetRef、Role、Scope、validFrom 与 validUntil 创建后
  不可变；O2 不支持恢复、换 Role/Set、扩大 Scope 或延长有效期，任何变化都必须 revoke + create，
  从而重新执行 descriptor、assignable ceiling、风险和当前/计划成员检查；
- Authorization 维护统一 `SubjectSetGrantInvariant`，覆盖 Permission catalog readiness、
  RolePermission 增加、Role 恢复、SubjectSetBinding 创建、Directory/Unit/Position 等父门禁恢复、
  启用子树 SubjectSet 后的 Unit 层级移动、成员新增/恢复/延长有效期与相关版本变化；不能只在创建
  Binding 的那一刻检查风险；
- RolePermission 变更若会让任何当前/未来有效 SubjectSetBinding 获得 HIGH/system permission，整个
  变更失败。Permission definition 同 code 改变 risk/agentDelegable 等元数据继续按 catalog digest
  冲突失败启动，不能静默改变已有 Binding 含义；
- 创建 SubjectSetBinding 时检查调用者当前及 Binding 有效期内已计划的集合成员资格；O2 中调用者
  属于或已计划加入该集合时拒绝。以后放开必须使用独立批准者与职责分离；
- 通过入职/岗位流程创建的员工型直接 USER Binding 必须带服务端生成的
  `GrantSourceRef(WORKFORCE_ENGAGEMENT, engagementId, version)`；普通客户端不能自报或改写来源；
- 员工专用产品/管理 policy 即使采用直接 Binding，也必须把“当前有效 Engagement/Acting Identity”
  作为关系约束；Binding 单独存在不能绕过离职父门禁；
- 新建/恢复/延长 PositionAssignment、TeamMembership 等 grant-increasing 变更前调用 Authorization
  的 `GrantImpactPolicy`，返回受影响的 Binding、风险和是否允许自动生效；依赖不可用时失败关闭；
- 暂停、离职、撤销或截止旧 Assignment 属于 grant-reducing 变更，不依赖 impact analyzer 成功即可
  由本地父门禁提交审计/outbox；正常 transfer 先验证新 grant，再原子关闭旧/创建新，紧急场景提供
  revoke-only 命令，不能因新岗分析失败而保留被盗或应撤销的旧权限；
- 普通组织管理员不得新增、恢复、延长或调入自己的 Assignment，也不得通过移动他人绕过可授予
  Role 的边界；自愿 relinquish 只能走专用 grant-reducing 命令；
- 管理 API 在提交前可返回类型化 impact preview，提交时必须重新读取版本并判断，preview 不是
  授权凭据；
- 变更事务保存 workforce change audit，并写 access-change outbox；Authorization decision audit
  只记录实际发生的访问判断，不与员工变更审计合表。

高风险岗位授权后续应使用直接 Binding、审批或职责分离，而不是在 O2 中追求“任何部门移动都能
自动赋予任何权限”。

### 6.5 “部门数据范围”的现代替代

不采用传统的 `role.data_scope = DEPT_AND_CHILD` 和字符串拼 SQL。它把三件不同的事混在一起：

1. 谁因部门成员资格获得一个 Role；
2. 某个经理能管理哪些员工任职；
3. 某个产品动作能查询哪些业务资源。

Ainer 分别表达：

- SubjectSetBinding：岗位/Team 成员整体获得显式 Role；
- Organization ReBAC：直属下属、Unit 成员或受控子树等当前关系；
- `QueryAuthorizationPlanner`：把允许范围翻译成类型化 query constraint，由 owner 模块使用参数化
  SQL 执行。

例如“审核员查看分配队列”应由 Review Team/Case assignment 产生队列约束，而不是因为审核员与
同事属于“运营部”就看见整个 tenant；“经理查看直属员工”也不自动推出可以看工资、身份证件或
顾客订单。

## 7. 管理权限建议

Permission code 由模块 contributor 注册，首版建议：

| Permission | 用途 | 默认风险 |
|---|---|---|
| `organization.directories.read` | 查看安全员工目录容器投影 | LOW |
| `organization.directories.write` | 创建、暂停、撤销 OrganizationDirectory | HIGH |
| `organization.units.read` | 查看部门树安全投影 | LOW |
| `organization.units.write` | 创建、移动、暂停部门 | MEDIUM |
| `organization.workforce.read` | 查看员工任职安全投影 | MEDIUM |
| `organization.workforce.write` | 入职、暂停、离职 | HIGH |
| `organization.assignments.write` | 调部门和岗位 | HIGH |
| `organization.teams.write` | 管理 Team 与成员 | HIGH |
| `organization.leadership.write` | 管理 UnitLeadership | HIGH |
| `organization.reporting.write` | 管理汇报关系 | HIGH |
| `organization.audit.read` | 查询受限变更审计 | HIGH |
| `authorization.subject-set-bindings.write` | 把 Role 绑定到受控 SubjectSet | HIGH |

Identity 的 tenant OWNER 只拥有 tenant 治理能力，不因名称自动获得以上全部 Permission。首次
bootstrap 可由受控平台流程创建 Organization 管理 Role 和 Binding；ADMIN/MEMBER 不做硬编码映射。

读权限也必须按字段分类：普通员工目录只返回 display name、部门、岗位和工作状态等安全投影；
员工编号、联系方式、历史变更和审计使用独立 Permission/FieldMask。Ainer 首版不保存证件、银行、
薪资、合同正文等高敏 HR 数据。

## 8. 应用用例与失败语义

### 8.1 创建 OrganizationDirectory 与根节点

1. 从认证主体和目标 tenant 解析可信 tenant；
2. 校验 tenant 当前有效，调用通用授权做 `organization.directories.write`；
3. 在同一事务创建 OrganizationDirectory、唯一 ROOT、change audit 与 outbox；
4. 重复幂等键返回已有结果，code 冲突返回 409，审计/outbox 失败则整体回滚。

### 8.2 入职

1. 解析稳定 SubjectRef，不相信请求体中的用户名或 openId；
2. 拒绝跨 issuer/tenant 的未绑定身份，检查同 OrganizationDirectory 任职时间不重叠；
3. 创建 Engagement、PRIMARY UnitAssignment 和可选 PositionAssignment；
4. 执行 grant impact 检查；
5. 同事务写变更审计/outbox；下次授权以当前事实判断，不等待 Token 到期。

### 8.3 调部门/调岗

1. 锁定 Engagement 与相关 Assignment，校验 expected version；
2. 以单一服务端时间 `T` 生成变更集；
3. 预览 Team、SubjectSet Binding 和产品 assignment 影响；
4. 在同一事务关闭旧分配、创建新分配、写审计/outbox；
5. 未明确迁移的 Team/产品关系保持不变并在响应中提示，不能静默跟随部门树变化。

### 8.4 暂停与离职

暂停用于临时阻断，离职/撤销是终态：

- 父 Engagement 门禁先失效，组织派生授权在下一次判断立即 DENY；
- 本地事务先使父 Engagement 无效并写 change audit/outbox；Authorization 公开端口可同步清理
  source-linked direct Binding，但清理失败不得回滚暂停/离职，而是由 outbox 幂等重试并告警。
  员工 policy 对父门禁的实时检查保证清理期间也不能 ALLOW；任何未链接来源的手工 Binding 作为
  residual grant 明确列出并由受权管理员处置；
- 同事务记录 termination reason code、actor、时间和关联 requestId，不保存自由文本敏感详情；
- outbox 通知 Authorization、XQ Acting Identity/产品 assignment、长任务和必要缓存；
- 直接 USER Binding、TenantMembership、Identity account 和产品 owner 关系不会被猜测性删除；
  offboarding orchestrator 必须列出并显式收口目标，部分失败进入可重试状态与告警；
- 已发生的订单、操作和 decision audit 保留稳定 Subject/Engagement 引用，不级联删除。

### 8.5 查询员工目录

列表请求必须先取得 `AuthorizedQueryPlan`，再由 Organization Mapper 翻译成参数化 SQL。禁止先查
全 tenant 员工再逐行 `authorize`，也禁止 Controller 拼接 Unit ID 列表。分页使用稳定排序和
keyset/受控分页；导出是独立高风险动作，不复用普通列表 Permission。

## 9. 模块与依赖方向

目标结构：

```text
ainer-server
├── ainer-module-organization
│   ├── organization/api
│   ├── organization/application
│   ├── organization/domain
│   ├── organization/infrastructure
│   └── workforce/...                 # 同模块内按 feature 组织
├── ainer-module-authorization
├── ainer-starter-security
└── ainer-starter-persistence
```

依赖与装配规则：

- Organization domain/application 不依赖 Spring Web、MyBatis-Plus 或 Authorization 实现；
- Organization 通过 `AuthorizationDecisionPort` / `GrantImpactPolicy` 调用授权公开端口；
- Authorization 只依赖 Spring-free `SubjectSetMembershipResolver` 契约，Organization 以 adapter
  注册实现，双方不注入对方 Service/Mapper；
- Identity tenant/subject 校验通过 Directory port，Organization 不查询 Identity 私表；
- Organization migration、Repository、Mapper、审计和 outbox 都由自身拥有；
- MyBatis-Plus 只用于 infrastructure 的简单 CRUD/分页；层级、有效期、影响查询使用显式参数化 SQL；
- 默认仍是模块化单体。出现独立扩缩容/安全隔离/跨语言消费者并满足 ADR-0024 前，不创建远程
  Organization 服务或空 Feign adapter。

`ainer-module-organization` 在 O1 才创建，初期标记 incubating。`xq-platform-next` 是第一个真实
消费者；第二个独立消费者完成兼容验证前，不把全部领域类型承诺为长期稳定公共 API。这个限制不
妨碍 P3 用真实产品验证模块，也避免为假想企业场景提前冻结错误模型。

## 10. PostgreSQL 18 数据设计

所有表遵循 [`database-design-standard.md`](../database-design-standard.md)：UUIDv7、显式 tenant
条件、跨 tenant 复合约束、`timestamptz`/`Instant`、乐观锁、普通列承载核心关系、真实 PostgreSQL
集成测试。目标表如下：

| 表 | 所有者 | 关键字段与约束 |
|---|---|---|
| `ainer_organization_directory` | Organization | UUIDv7 id、tenant_id、code、status、version；tenant+code 唯一且不复用 |
| `ainer_organization_unit` | Organization | id、directory_id、tenant_id、code、kind、status、version；复合 FK 保证同 tenant/directory |
| `ainer_organization_unit_parent` | Organization | UUIDv7 id、tenant_id、directory_id、child/parent、valid_period、status、version；同 child 有效期不重叠；应用+递归 CTE 防环 |
| `ainer_organization_workforce_engagement` | Workforce | id、tenant_id、directory_id、issuer/type/subject、engagement_type、employee_number?、valid_period、status、version；同 subject 任职期不重叠；员工编号在 directory 内唯一且不复用 |
| `ainer_organization_unit_assignment` | Workforce | id、tenant_id、directory_id、engagement/unit、kind、valid_period、status、version；复合 FK；PRIMARY 同期唯一 |
| `ainer_organization_position` | Workforce | id、tenant_id、directory_id、org_unit_id、code、display_name、status、version；同 Unit code 稳定唯一 |
| `ainer_organization_position_assignment` | Workforce | id、tenant_id、directory_id、org_unit_id、engagement/position/unit_assignment、kind、valid_period、status、version；复合 FK 证明同 tenant/directory/unit/engagement |
| `ainer_organization_team` | 第二切片 | id、tenant_id、directory_id、code、owning_unit?、status、version |
| `ainer_organization_team_membership` | 第二切片 | id、tenant_id、directory_id、team/engagement、relation、valid_period、status、version |
| `ainer_organization_unit_leadership` | 第二切片 | id、tenant_id、directory_id、unit/leader_position_assignment、kind、valid_period、status、version；HEAD 同期唯一 |
| `ainer_organization_reporting_line` | 第二切片 | id、tenant_id、directory_id、report/manager assignment、kind、valid_period、status、version；PRIMARY 同期唯一 |
| `ainer_organization_change_audit` | Organization | append-only UUIDv7、tenant/directory、actor、action、target type/id、before/after version、reason code、request/trace、occurred_at |
| `ainer_organization_access_change_event` | Organization | transactional outbox：event id/type/version、tenant/directory、aggregate ref/version、occurred_at、payload、delivery state |
| `ainer_authorization_subject_set_binding` | Authorization | id、tenant_id、directory_id、SubjectSet type/object/relation、role、scope、有效期、状态、版本；不建跨模块 FK，但创建/决策都解析权威 descriptor |

设计要求：

- 访问查询同时携带 tenant_id 与 owner ID，不能只靠随机 UUID 防越权；
- 每张 tenant-owned 关系表显式保存 `tenant_id`、`directory_id`；Directory root 提供
  `(tenant_id, id)` 唯一键，其余被引用父表提供 `(tenant_id, directory_id, id)` 唯一键，关系表
  使用包含 tenant/directory 的复合 FK；
- UnitAssignment 额外提供 `(tenant_id, directory_id, id, engagement_id, org_unit_id)` 唯一键；
  PositionAssignment 以同五列复合 FK 引用它，并以包含 `org_unit_id` 的复合 FK 引用 Position，确保
  不能借用同 Unit 另一名员工的 UnitAssignment；
- Subject issuer/type/id、SubjectSet type/object/relation、状态和有效期使用明确列，不放 JSONB；
- outbox payload 只放稳定 ID、版本和 reason code，不放姓名、联系方式或员工详情；
- 关联有效期优先使用 PostgreSQL 18 原生 temporal constraint；若最终查询计划要求普通 from/until
  列，也必须以数据库约束保证不重叠，不能把正确性只留给 Java；
- 不使用 trigger 隐式分配权限，不使用 ltree 作为 O1 前置。代表性组织规模的递归 CTE 达不到目标
  后，再评估 closure/read model；
- 不物理级联删除任职、Assignment、审计或访问事件。

## 11. HTTP、OpenAPI 与管理面

建议以命令式资源 API 暴露，不提供“任意表 CRUD”：

```text
GET/POST   /api/organization-directories
GET/PATCH  /api/organization-directories/{directoryId}
GET/POST   /api/organization-directories/{directoryId}/units
POST       /api/organization-directories/{directoryId}/unit-moves
GET/POST   /api/organization-directories/{directoryId}/workforce-engagements
POST       /api/workforce-engagements/{id}/suspensions
POST       /api/workforce-engagements/{id}/terminations
POST       /api/workforce-engagements/{id}/unit-transfers
POST       /api/workforce-engagements/{id}/position-assignments
POST       /api/organization-grant-impact-previews
GET        /api/organization-directories/{directoryId}/change-audits
```

规则：

- create/transfer/terminate 使用请求幂等键和 expected version；
- 生命周期命令不使用通用 DELETE/PATCH status 绕过不变量；
- Path 中的 tenant/directory 只是查找键，权威 tenant 从资源和可信 principal 解析；
- impact preview 返回稳定摘要、受影响 Role/Permission 分类和过期时间，提交时重新判断；
- OpenAPI 明确字段分级、错误码、409 并发/约束冲突与 401/403/CHALLENGE；
- Ainer Admin 只消费发布的 OpenAPI SDK，不查询数据库或复制状态枚举；
- 客户端不接收 Permission catalog 之外的任意策略表达式，也不能上传 SQL/SpEL/Rego。

建议错误码族：

```text
AINER.ORGANIZATION.NOT_FOUND
AINER.ORGANIZATION.CROSS_TENANT_REFERENCE
AINER.ORGANIZATION.HIERARCHY_CYCLE
AINER.ORGANIZATION.VERSION_CONFLICT
AINER.ORGANIZATION.OVERLAPPING_EFFECTIVE_PERIOD
AINER.ORGANIZATION.ENGAGEMENT_INACTIVE
AINER.ORGANIZATION.GRANT_IMPACT_DENIED
AINER.ORGANIZATION.SELF_ASSIGNMENT_DENIED
```

## 12. 审计、撤销、隐私与运维

### 12.1 两类审计不合表

- Organization change audit：谁创建/调动/暂停/离职了哪个 Engagement/Assignment；
- Authorization decision audit：谁因 direct/set binding 对哪个资源执行什么动作，结果为何。

Decision audit 增加命中的 binding ID、SubjectSetRef、Engagement/UnitAssignment/PositionAssignment
ID/version、membership fact/descriptor version 和 `validUntil`，不记录姓名、岗位标题、部门名称等
可变/个人字段。产品副作用仍由产品域记录自己的业务审计。

### 12.2 撤销传播

- 无 ALLOW cache 时，下一次 decision 直接使用当前 Engagement/Assignment；
- 长任务在副作用前按 ADR-0031 重新授权；
- access-change outbox 以至少一次语义通知本地/远程消费者，消费者按 event ID 幂等；
- 撤销 SLO 从数据库提交到所有声明的 cache/long-running checkpoint 失效分别度量；
- outbox 投递失败不恢复已撤销关系，但必须告警和重试；
- 不能只清 JWT cache 就宣称岗位权限已撤销，也不能因为 JWT 尚有效继续接受旧岗位事实。

### 12.3 隐私

O1 只保存授权和员工目录真正需要的最小字段。姓名、手机号和邮箱优先从受控 Identity Directory
安全投影按需读取或建立有明确刷新/删除语义的投影；不得复制密码、OAuth 协议表、身份证件、银行、
薪资、合同正文和家庭信息。

搜索、导出、审计和历史查询分别授权。日志、指标、事件标签不包含姓名、手机号、subjectId、
employeeNumber 等高基数字段；指标按 outcome/reason/type 聚合。

## 13. SCIM 与外部 HR 系统

SCIM 2.0 是未来 Directory 互操作 adapter，不是 Ainer 内部领域模型。RFC 7643 的 Enterprise User
扩展包含 `employeeNumber`、`organization`、`division`、`department` 和 `manager` 等属性，适合
导入/导出投影，但扁平字符串不足以表达 Ainer 的有效期、多任职、PositionAssignment 和授权影响。

以后接入 SCIM/HRIS 时必须额外定义：

- 外部 source、externalId 与 tenant/OrganizationDirectory 的绑定；
- 单向还是双向事实所有权，禁止双方无冲突规则地互相覆盖；
- 幂等、增量游标、失败重放、删除/停用映射和审计；
- 先创建 Identity Subject 还是只导入候选人员的明确流程；
- 外部 Group 是否映射 Team/Position SubjectSet；默认不接受任意嵌套 Group 扩权；
- 回滚和对账，不把 SCIM PATCH 直接映射为无授权的表更新。

O1 不实现 SCIM Server、HRIS 双向同步或通用审批引擎。

## 14. `xq-platform-next` 映射

### 14.1 推荐映射

```text
Ainer USER
  ├── TenantMembership                  # tenant 治理
  ├── WorkforceEngagement               # 公司员工/合同人员关系
  │   ├── OrgUnitAssignment
  │   ├── PositionAssignment
  │   └── TeamMembership
  ├── Customer                          # xq-shop-next 产品身份
  ├── Seller                            # XQ 产品身份
  └── MerchantOperator/ActingIdentity   # XQ 产品关系

Company ── operates ── Merchant ── has ── BusinessLocation
```

一个 Merchant 只有在需要自己的管理员、员工目录、秘密、数据隔离和审计边界时才考虑独立 tenant；
不是每个商家、经营点或小程序都创建 tenant。即使早期恰好一 tenant 对一 Company，也必须保留显式
关联，不能复用 ID 或把偶然的一对一写成永久不变量。

### 14.2 关键场景

1. 平台内部 HR 可创建员工 Engagement 和采购 PositionAssignment，但这一步本身不产生
   `industry.procurement.submit`；还需要显式 SubjectSetBinding 与经营点/流程状态约束。
2. 员工在 Position P 并不代表可以处理所有 Merchant。岗位 Role 的 Scope、Location assignment
   和产品状态必须同时满足。
3. 外部货主或合作录拍员可通过 Partner/Merchant Acting Identity + Location assignment 工作，
   不必伪造 Employee 或 TenantMembership。
4. Review Team 只处理被分配的区域/队列；退出 Team 后即使仍在运营部，也不能继续处理该队列。
5. 同一 USER 在 `xq-shop-next` 默认走 Customer 路径。只有后台 client/audience、显式 Acting
   Identity 与员工/产品授权都成立时，才能进入内部投影。
6. 员工离职立即终止组织派生的后台权限，但保留其合法顾客数据；XQ 消费 outbox 显式撤销 Employee
   Acting Identity、个人工作 assignment 和产品 Binding。
7. 财务岗位不自动获得审批权。还要满足 finance Binding、资源关系、金额阈值、非本人申请与
   Step-up/approval。
8. 未来仓储、伙伴或管理 App 复用同一 Subject/Engagement/Permission 原语，只新增受控
   platform_app、client/audience 与产品关系，不新增 actorType 或用户表。

### 14.3 XQ 自己拥有的内容

`xq-platform-next` 必须自己拥有 Company/LegalEntity、Merchant、BusinessLocation、Customer、
Seller、MerchantOperator、采购/品控/拍摄/发布 Capability、客户归属/SLA、Listing/Offer/订单状态和
public projection。Ainer Organization 只提供通用员工目录与授权扩展点，不能读取或直写这些产品表。

## 15. 实施切片

### F0：修复现有撤销语义

在新增员工派生授权前，先修复当前基座的两个不一致：普通 tenant member 角色变更/移除没有完整
写入 access-event outbox；Identity 已定义的 `IDENTITY_MEMBERSHIP_ROLE_CHANGED` 又不在 Workspace
consumer 的事件枚举和数据库约束中。不能简单让 Workspace 接受新枚举后继续调用统一的“撤销成员”
逻辑，否则 tenant Role 变化会误删独立 Workspace membership。

F0 必须分别验证：

- role change 使事件前签发且携带旧 role claim 的 Token 在在线检查点失效；
- membership revoke 传播到明确依赖 Identity membership 的消费者；
- user disable、role change、membership revoke、workforce change 与 organization move 各自路由到
  正确处理器，重复/乱序事件仍幂等；
- 当前 subject-scoped access event 不能表达 tenant-wide disable；在 Organization 上线前必须增加
  明确的 tenant epoch/event 或等价在线门禁，并验证整个 tenant 的 Directory/SubjectSet 都立即拒绝；
- Workforce change 只撤销派生 grant/事实，不误删 Identity、Workspace、Customer 或 product owner
  关系；
- `/api/organization-directories/**` 高风险写、导出和审计在真正创建时加入在线校验与 Step-up 默认策略。

### O0：合同与负向门禁

- 固定 OrganizationDirectory、OrgUnit、Engagement、Position 与 Assignment 不变量；
- 在 Authorization 中增加服务端解析的 `SubjectSetRef`、descriptor resolver、批量 membership
  resolver 和 provenance 契约，但不改变 requester actorType；
- 固定 Permission、错误码、审计、outbox 与 off-state；
- 用架构测试禁止 Identity/Authorization/Product Mapper 交叉引用；
- 所有 unknown provider、跨 tenant、过期关系、父 Engagement 无效场景默认拒绝。

### O1：最小员工目录闭环

- 创建 `ainer-module-organization`；实现 OrganizationDirectory、OrgUnit、WorkforceEngagement、
  UnitAssignment、Position、PositionAssignment；
- 完成 PostgreSQL 18 migration、有效期/唯一性/环检测、事务审计和 outbox；
- 完成管理 API、OpenAPI SDK、安全投影、分页和并发控制；
- 模块关闭时最小 Ainer 应用仍能构建和启动；
- 此阶段尚不因组织关系自动授予 Role。

### O2：`position#assignee` 授权闭环

- Authorization 增加独立 SubjectSetBinding 持久化与 `GrantImpactPolicy`；
- SubjectSetBinding 持久化权威 tenant/directory，并对 RolePermission、Binding、父门禁和成员变更
  统一执行 `SubjectSetGrantInvariant`；
- Organization 实现唯一首批 resolver：`workforce.position#assignee`；
- Effective Access 展示 direct/position provenance；decision audit 保存相关 Assignment ID/version、
  descriptor/fact version 与 validUntil；
- 验证入职、未来生效、多任职、调岗、暂停、离职、重入职、并发与跨 tenant；明确覆盖
  PositionAssignment 借用另一 Engagement 的 UnitAssignment 时数据库拒绝且 resolver 不产生 ALLOW；
- 验证 SubjectSetBinding 的授权目标与有效期不可原地恢复、替换或延长，只能 revoke + create；
- 禁止 HIGH/system/GLOBAL SubjectSet grant，待审批与职责分离能力完成后再放开。

### O3：XQ 真实产品消费者与第二类关系

- `xq-platform-next` 只通过发布制品注册产品 Permission、Scope、facts provider 和 query plan；
- 完成一个真实岗位 + 经营点 + Listing/工作队列纵向切片；
- 实现 Team/TeamMembership、UnitLeadership 或 direct ReportingLine 中最先出现真实需求的一种，
  不能三套同时空建；
- Ainer minor 升级后消费者无需复制源码或补丁；
- 第二个独立消费者验证后，再评估稳定公共 API、SCIM adapter 和更多 SubjectSet。

O0/O1 不阻塞 ADR-0030 的直接 Binding 首闭环；O2 紧随其后。Team、递归部门、汇报链和 SCIM
不能成为创建 `xq-platform-next` 的无限前置条件。

`xq-platform-next` 的创建检查点仍是 P1/P2 的已发布制品与 Initializer 可用、以及授权 I0/S0–S3
直接闭环通过；随后在 P3 作为 Organization O1/O2 的第一个真实消费者共同验证。不能等到完整 HR、
Team、SCIM 或 P5 才创建产品，也不能为了提前创建而复制 Ainer 源码或依赖未发布 SNAPSHOT。

## 16. 验收矩阵

| 场景 | 预期 |
|---|---|
| C 端 USER 无 Engagement 读取本人订单 | 产品 owner path 可 ALLOW；不得要求员工关系 |
| 员工在有效 Position、Binding 和资源关系内操作 | ALLOW，审计包含 set provenance |
| PositionAssignment 未来生效 | 生效点前 DENY，生效点及之后按完整路径判断 |
| PositionAssignment 借用同 Unit 另一员工的 UnitAssignment | 写入被复合 FK 拒绝；resolver 对遗留异常数据仍不产生 ALLOW |
| `T` 时刻调岗 | `T` 前只命中旧岗，`T` 后只命中新岗，无双重 ALLOW |
| Engagement SUSPENDED/REVOKED | 即使 JWT、岗位和 Binding 尚在，下一次判断 DENY |
| 重新入职 | 新 Engagement 生效；旧记录不能恢复为当前成员 |
| 同 Subject 多 OrganizationDirectory 任职 | 只在目标 directory/tenant/scope 完整匹配时 ALLOW |
| 仅岗位名称等于“管理员” | DENY，不解析名称或 code 为 Permission |
| 部门负责人访问非直属员工敏感字段 | DENY，除非显式 policy/FieldMask/Scope 均满足 |
| HR 把自己加入已绑定高权限岗位 | DENY，并保存失败变更审计 |
| SubjectSet provider 超时/异常 | DENY/503 按执行点合同处理，不回退 direct guess 或旧 ALLOW |
| 修改已生效 SubjectSetBinding 的 Role、Scope 或有效期 | 不提供原地修改/恢复/延长；只能 revoke + create，并重新执行完整授权不变量 |
| OrgUnit 移入后代 | 409，事务无部分变更 |
| 跨 tenant Unit/Position/Engagement 引用 | 403/404 安全语义，数据库约束仍阻止写入 |
| 离职员工以 Customer 身份查看自己的合法订单 | Customer path 不因离职被删除 |
| 列表查询 | 先生成受控 QueryPlan，SQL 参数绑定，无逐行 N+1 authorize |
| 模块关闭 | C 端/最小应用可启动，Authorization direct binding 不依赖 Organization bean |

正式验收还必须覆盖 migration 从空库和升级重放、PostgreSQL 并发、时间边界、outbox 幂等、审计
失败回滚、OpenAPI compatibility、两个 tenant 的负向矩阵，以及代表性组织规模的递归/批量解析
查询计划。

## 17. 明确非目标

首版不建设：

- 人事主档、劳动合同、招聘、入职材料、薪酬、奖金、税务、社保、福利；
- 绩效、技能、晋升、请假、考勤、排班、工时、差旅和费用；
- 身份证件、银行账户、家庭成员和健康信息；
- 编制/职位槽位规划、组织模拟、完整双时态历史仓库；
- 通用工作流/审批引擎、SCIM Server 或 HRIS 双向同步；
- OrgUnit 自动权限继承、Role hierarchy、嵌套 Group、递归 manager chain；
- 万能关系 tuple store、独立 PDP、部门树 SQL 拼接和每行授权；
- 把 Tenant、Company、Merchant、Location、Department 或 App 合并为同一实体。

如果未来需要上述能力，优先接入专业 HRIS/IdP 并通过受控 adapter 同步授权需要的最小事实；只有
两个以上独立消费者持续需要且边界稳定的能力，才考虑晋升为 Ainer 通用模块。

## 18. 参考

- [RFC 7643：SCIM Core Schema](https://www.rfc-editor.org/rfc/rfc7643.html)
- [RFC 7644：SCIM Protocol](https://www.rfc-editor.org/rfc/rfc7644.html)
- [NIST Role Based Access Control](https://csrc.nist.gov/projects/role-based-access-control)
- [OpenFGA：Usersets](https://openfga.dev/docs/modeling/building-blocks/usersets)
- [PostgreSQL 18 Temporal Constraints](https://www.postgresql.org/about/featurematrix/detail/temporal-constraints/)
- [Ainer 通用授权与 AI 代行详细方案](authorization-architecture-plan.md)
- [ADR-0024：演进式模块化平台架构](../decisions/0024-evolutionary-modular-platform-architecture.md)
- [ADR-0030：通用混合细粒度授权基线](../decisions/0030-hybrid-fine-grained-authorization-baseline.md)
