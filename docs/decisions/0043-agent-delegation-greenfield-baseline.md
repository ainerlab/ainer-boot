# ADR-0043：Agent 代行 Greenfield 基线

- 状态：Accepted
- 日期：2026-08-15
- 决策者：Ainer 项目维护者
- 取代：[ADR-0031](0031-agent-delegation-and-ai-context-authorization.md)（tenant 语义与
  ADR-0030 时代的授权引用已过时，本文以 Workspace/Greenfield 语义合规取代）
- 被取代：无

## 背景

ADR-0031 完成了 Agent 代行、Capability 与 AI 上下文授权的领域设计（Agent 不是认证凭据、
ActingGrant 一层委托、Capability 类型化、ContextScope 作为决策输出、风险 Challenge 分型、
长任务检查点、审计分层、Token Exchange 仅作远程投影），其备选方案结论与非目标继续有效。
需要重述的部分：

- 全部 tenant 表述（`ownerTenant`、Token Exchange 的 `tenant_id`、「跨 tenant 负向矩阵」）
  随 ADR-0033 Greenfield 删除；
- 通用授权基线由 ADR-0030 换为 [ADR-0037](0037-post-greenfield-authorization-baseline.md)：
  SubjectRef 三元组、`Scope.Workspace/Resource/Global`、无 ALLOW 缓存的实时解析；
- ADR-0042 O2 已证明「决策时实时解析」撤销语义：组织事实（撤岗）与授权事实（撤 Binding）
  都在下一次决策即时生效。ActingGrant 复用同一语义。

未提及的条款按 ADR-0031 原文继续有效，其中 tenant 一律读作 Workspace。本 ADR 只授权 A1
最小切片（见「分阶段」）；A2/A3/A4 仍按需推进。

## 决策（重述部分）

### 1. 锚点与 Subject

1. `ActingGrant.principalSubjectRef` 使用 ADR-0037 的 `SubjectRef(issuer, subjectId, USER)`；
   被代表者只能是 USER。Agent 定义归属 AI Runtime（Workspace 可选锚定，`workspaceId` 可空，
   表示平台级 Agent）。
2. Scope 子集使用 `Scope.Workspace/Resource`；GLOBAL 不可委托（同 ADR-0042 O2 对集合的
   约束，委托不得扩大到全局）。
3. 「跨 tenant 负向矩阵」替换为「跨 Workspace 负向矩阵」。

### 2. 撤销语义：决策时实时解析

ADR-0031 §2.2/§6 的「每个检查点重新验证」在 Greenfield 下由拉取式解析直接满足，与
ADR-0037/0042 同构，不需要事件传播：

1. 委托检查点实时读取：grant 状态与有效期、principal 当前 live Bindings（权限收缩、
   Binding 撤销立即反映）、Agent definition 状态（retire 立即反映）。
2. grant 撤销、principal 权限收缩、Binding 撤销、Agent retired 任一发生，后续检查点
   即 DENY；正在执行的 provider 请求按 ADR-0031 §6 的协作取消语义处理。
3. 无委托事实缓存、无 ALLOW 缓存。

### 3. A1 最小切片（本文授权交付）

| 所有者 | 交付 |
|---|---|
| AI Runtime | `ainer_ai_agent_definition`（code/version/status/purpose/runtime_ref）+ 管理 API（create/retire）+ `AgentStatusResolver` 实现 |
| Authorization | `ainer_authorization_acting_grant`（permission 子表 + 单一结构化 Scope）+ 签发/撤销/查询 API + lifecycle 审计 + 委托检查点决策 + decision audit 的 agent/grant 关联列 |

签发防扩权（全部强制）：

1. permission 必须已注册且 `agentDelegable=true`、且属于 principal 当前 live effective
   access（Binding 解析）——签发时与每个检查点都验证；
2. scope 必须被 principal 某条 live Binding 的 scope 覆盖（同 Workspace）；
3. GLOBAL 不可委托；system-only 权限不可委托；
4. grant 不可传递（`nonDelegable` 恒真，无字段可改）；
5. 撤销走动作名词端点，lifecycle 审计同事务。

### 4. Capability / Context / Challenge / Token Exchange

维持 ADR-0031 §3–§5、§9–§10 原文（tenant 替换为 Workspace），属 A2–A4，不在本切片；
不预建空端口实现。`Permission.agentDelegable` 字段已存在于目录中，本切片开始真实消费。

## 验收

- A1 交付时追加验证记录：签发子集校验（不可委托权限/超集权限/GLOBAL 拒绝）、委托检查
  ALLOW、principal Binding 撤销后同一 grant 检查立即 DENY（权限收缩）、Agent retire 后
  DENY、grant 撤销后 DENY、lifecycle/decision 审计、跨 Workspace 负向。
- **A1（2026-08-15）已交付并验证**：授权侧 `ainer_authorization_acting_grant`（+permission
  子表，加性 migration V202608150100；GLOBAL 不可表达；decision audit 增 agent/grant 可空关联列），
  签发/撤销/查询 API `/api/authorization/acting-grants/**` + lifecycle 审计 + 委托检查点
  `ActingGrantApplicationService.check`（拉取式：grant/principal live bindings/agent 状态每次实时
  解析）；默认 `AgentDefinitionStatusResolver` fail-closed。AI Runtime 侧独立
  `AiAgentModuleConfiguration`（`ainer.ai.agents.enabled` 默认开启，与网关解耦）+
  `ainer_ai_agent_definition`（code+version 唯一）+ `/api/ai/agents` 注册/退役/查询，服务同时实现
  状态解析端口。验证：ai-runtime 28/0/0/0（新增委托端到端 4 项：检查 ALLOW、Agent 退役即拒
  AGENT_RETIRED、principal Binding 撤销即拒 PRINCIPAL_SHRUNK、grant 撤销即拒、签发不可委托/
  超集权限 422）。
- A2–A4 交付前不得宣称 Capability catalog、Context 授权或 Token Exchange 能力。

## 参考

- [ADR-0031（被取代）](0031-agent-delegation-and-ai-context-authorization.md)
- [ADR-0033 Greenfield](0033-account-workspace-subject-isolation-greenfield-baseline.md)
- [ADR-0037 post-Greenfield 授权](0037-post-greenfield-authorization-baseline.md)
- [ADR-0042 组织目录 Greenfield](0042-organization-directory-greenfield-baseline.md)
