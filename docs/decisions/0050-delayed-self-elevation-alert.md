# ADR-0050：岗位集合绑定延迟自提权的 Alert 切片

- 状态：Accepted
- 日期：2026-08-25
- 决策者：Ainer 项目维护者
- 取代：无（闭合商业评审 M2 剩余项的**产品选择**；不改写
  [ADR-0042](0042-organization-directory-greenfield-baseline.md) 的实时解析结论）
- 被取代：无

## 背景

`GrantAdministrationGuard.requireSetBindingCreation` 只能挡住「创建集合绑定时，
操作者已经是 `workforce.position#assignee` 成员」。创建之后若同一 subject 被安排进
该岗位，`WorkforcePositionMembershipResolver` 会在决策时把原绑定对该人生效——这是
评审所称的**延迟自提权**（先签绑定、再入岗）。

成员解析 `UNAVAILABLE` 时拒绝创建（2026-08-20）已经关闭失败打开的一半。剩余问题是
入岗后的窗口，不是创建瞬间快照能覆盖的。

## 决策驱动因素

- 不得在没有真实 HR 流程消费者的情况下自动撤销绑定或阻断入岗；
- 高价值授权变化必须可审计、可观测；
- 组织模块不得注入授权模块的 Service 实现，只能走显式端口；
- UNAVAILABLE 创建拒绝保持不变。

## 备选方案

### 方案 Alert（采用）

入岗成功后查询「该任职 subject 是否曾作为 actor 创建过指向本岗位的
`workforce.position#assignee` 且仍 ACTIVE 的集合绑定」。命中则写组织高价值审计
与计数器，**不撤销、不阻断入岗**。

### 方案 Recheck 自动冻结（未选）

入岗或成员变化时自动冻结/撤销当事 subject 创建的集合绑定。能闭合提权窗口，但会
在合法「先建岗位授权模板、后入岗自己」的 HR 流程里造成数据丢失，且需要补偿与
审批面。留给真实 HR 消费者。

### 方案 禁止 bind-before-assignment（未选）

禁止对尚无在岗者的岗位创建集合绑定，或禁止绑定创建者日后入岗。会把授权准备与
组织事实强行耦合同一步骤，当前没有消费者证明这是可接受的合同。

## 决策

采用 **Alert**：

1. 组织 `assignPosition` 在写入岗位任职并完成 `ASSIGNED` 审计之后，通过授权模块
   端口查询：该 engagement 的 `(subjectIssuer, subjectId)` 是否作为
   `ainer_authorization_change_audit` 中 `target_type=SET_BINDING` / `action=CREATE`
   的 actor，对应一条仍 `ACTIVE` 的 `workforce.position#assignee` 集合绑定
   （`set_object_id` 为本岗位，`set_workspace_id` 为任职工作区）。
2. 命中则追加组织审计 `operation=DELAYED_SELF_ELEVATION`（实体仍为
   `POSITION_ASSIGNMENT`），并递增
   `ainer.organization.delayed_self_elevation` 计数器（MeterRegistry 可选）。
3. 查询或指标失败不得阻断入岗；审计写入仍在同一事务——与现有组织审计失败关闭
   一致。若产品要把 Alert 做成「审计失败也放行」，必须另改审计合同。
4. 端口缺失（组织切片未装配授权模块）时跳过，不假装已巡检。
5. 创建集合绑定时的 UNAVAILABLE 拒绝与「当时已是成员」拒绝保持不变。

明确**不包含**：自动撤销、入岗拒绝、职责分离 enforced、通用 recheck 框架。

## 后果

### 正面

- 延迟自提权从「已知无信号」变成「入岗当时可审计、可告警」；
- 不改 Binding 生命周期，不破坏现有 O2 实时解析合同。

### 负面与风险

- Alert 不能阻止提权生效；安全运营必须订阅该审计/指标；
- 只识别「自己创建的」绑定。他人创建、事后把创建者改成当事 subject 的路径不在
  本切片（变更审计 actor 不可改写）。

## 安全、数据与隐私

审计只记录稳定身份引用、岗位任职 id、操作名与 requestId，不写权限目录正文或
Token。跨模块查询走授权库已有表，不复制授权行到组织库。

## 运维与迁移

无 schema 变更（沿用 `ainer_org_change_audit` 的已有 `operation` 列）。上线后把
`DELAYED_SELF_ELEVATION` 与计数器纳入授权/组织告警即可。回滚即停止写该操作名，
历史行保留。

## 验收记录

- 组织 + 授权同库 PostgreSQL 集成测试：任职 subject 曾创建本岗位集合绑定 →
  入岗成功且出现 `DELAYED_SELF_ELEVATION` 审计；未创建则只有 `ASSIGNED`。
- UNAVAILABLE 创建拒绝的既有测试不得回退。

## 参考

- [ADR-0042](0042-organization-directory-greenfield-baseline.md)
- [ADR-0037](0037-post-greenfield-authorization-baseline.md)
- 商业评审 M2（`docs/reviews/2026-08-19-commercial-grade-code-review.md`）
