# ADR-0031：Agent 代行、Capability 与 AI 上下文授权基线

- 状态：Superseded
- 日期：2026-08-02
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：[ADR-0043](0043-agent-delegation-greenfield-baseline.md)（Greenfield/ADR-0037 语义合规重述；领域取舍与备选方案结论由 ADR-0043 继承）

## 背景

Ainer 已有 AI Model Gateway、`GovernedAiExecutionContext`、基础敏感数据策略、tenant 限流、预算、
`AiInvocationAuditService` 与 `ContextSnapshot` 的早期契约。ADR-0030 又定义了通用 Permission、
Role、Binding、Scope、资源关系和 AuthorizationDecision。

AI 场景还需要回答传统资源授权之外的问题：

- 哪个 Agent 正在执行，使用哪个版本和 runtime；
- 它代表谁，以及委托是否仍有效；
- 委托的 Permission、Scope 与 Capability 是否都是 principal 当前权限的子集；
- 哪些业务数据可以进入 RAG/prompt 上下文；
- 哪些 Tool/Function/Model 与自主等级可以使用；
- 高风险副作用是否需要认证升级、交易确认或人工审批；
- 委托撤销后，长任务应在哪些检查点停止；
- 授权决策、上下文、模型调用和工具副作用如何关联审计。

如果把 Agent 直接当成 SERVICE、把 modelId 当身份或把完整用户权限复制进 Agent Token，就无法表达
“谁委托谁、限定什么范围、何时失效”。如果把 Capability、ContextScope 和 AI invocation 全塞入
通用 RBAC，又会让授权模块拥有 AI 运行时的全部产品细节。

本 ADR 在 ADR-0030 的通用授权之上增加 AI 专项，不取代 ADR-0003 的 Model Gateway 或
ADR-0023 的受治理任务候选模型。详细任务和产品场景见
[`Ainer 通用授权与 AI 代行详细方案`](../design/authorization-architecture-plan.md)。

## 决策驱动因素

- Agent 是可版本化、可停用、可审计的执行参与者；
- principal 的权限不能因委托而扩大；
- 委托必须短时、范围明确、可撤销且默认不可传递；
- Tool/Model/Autonomy capability 与业务 Permission 保持正交；
- 授权在检索前、工具调用前和副作用前生效，而不是在模型生成后补检查；
- AI 上下文最小化并与 `SensitiveDataPolicy` 分层协作；
- 通用授权审计、AI invocation 审计和产品业务审计保持各自所有权；
- 远程 Token Exchange 只在真实跨服务边界出现后实现。

## 备选方案

### 方案 A：Agent 完全复用 SERVICE 身份

实现简单，但只能说明哪个运行服务调用了 API，不能说明具体 Agent 版本、被代表的 principal、委托
范围和撤销状态。SERVICE 认证继续保留为远程 runtime 凭据，但不能单独承担 Agent 授权。

### 方案 B：每个 Agent 拥有独立长期凭据和固定 principal

会制造大量长期 secret，且 Agent 往往被多个用户或 tenant 使用，principal 并非 Agent 固有属性。
model/runtime 切换也会混淆身份。拒绝。

### 方案 C：把 Capability 当成另一套 Role/Permission

容易复用现有表，但会把 Tool schema、模型族、调用次数、自主等级和副作用规则强行扁平化为字符串
权限，并造成两套角色层级。拒绝。

### 方案 D：Agent definition + 一层 ActingGrant + 类型化 Capability（采用）

远程 runtime 先通过标准 SERVICE 凭据认证；Agent 以稳定 definition/version 参与授权；principal
通过短时 ActingGrant 委托 Permission/Scope/Capability 子集。ContextScope 作为决策的数据边界
输出。采用。

## 决策

### 1. Agent 是授权参与者，不是新增认证凭据类型

1. Agent 具有稳定 `agentId`、code、version、用途、状态、owner/context 与 runtime configuration
   reference；modelId、provider 和 deployment 可以变更，均不充当身份。
2. `AgentDefinition` 由 AI Runtime/Agent Registry 拥有，不放入 Identity user/service 表。
3. 远程 Agent/runtime 调用先使用 OAuth Client Credentials、private_key_jwt、mTLS 等 SERVICE
   凭据认证；授权请求再携带服务端验证的 agentId/version/grantRef。
4. 首版不向 `AuthenticatedActor.actorType` 增加 AGENT。通用认证主体仍为 USER/SERVICE，Agent 作为
   `DelegatedRequester` 中的执行参与者出现。
5. Agent 不固定绑定 principal；principal 是每个 ActingGrant 的一端。

建议模型：

```text
AgentDefinition(
  id, code, version, status, ownerTenant?, purpose,
  runtimeRef, defaultCapabilityProfile, createdAt, retiredAt?)

DelegatedRequester(
  credentialPrincipal,
  representedPrincipal,
  agentRef,
  actingGrantRef)
```

### 2. ActingGrant 是一等授权关系

```text
ActingGrant(
  id,
  principalSubjectRef,
  agentId + agentVersion,
  validFrom + validUntil,
  status + version + revokedAt + revokedBy,
  nonDelegable=true,
  permission subset,
  scope subset,
  capability constraints,
  contextPolicyRef)
```

规则：

1. v1 只允许 `principal → agent` 一层，不允许 agent→agent、转委托或 role inheritance。
2. grant 签发时和每个关键检查点都验证：
   - principal 当前仍有效；
   - Agent definition/version ACTIVE；
   - grant ACTIVE、在有效期内且版本未撤销；
   - permission 是 principal 当前 effective access 的子集且 Permission definition 明确
     `agentDelegable=true`；
   - scope 是 principal 当前授权范围的子集；
   - capability 未超过 Agent definition 与 grant 的共同上限。
3. `X-Acting-Identity-Id` 只可选择产品域中已经验证的业务身份。它不创建 ActingGrant，不证明
   operator/merchant 关系，也不能覆盖 principal、tenant 或 agentId。
4. grant 不复制 principal 全部 Role/Binding；只保存显式最小子集及签发版本，执行时仍需检查实时
   principal/Binding 状态。
5. grant 到期、撤销、principal 禁用、Binding 撤销或 Agent version retired 任一发生都导致后续
   检查失败。

### 3. Capability 是类型化 catalog 和约束，不是第二套 RBAC

1. AI Runtime/Tool Registry 拥有 Capability 定义：tool/function/model/autonomy level、输入 schema、
   超时、幂等、副作用等级和审批要求。
2. Authorization 只消费稳定 capability ID、版本与受控 constraint，不拥有厂商 SDK、Tool schema
   或业务实现。
3. Permission 回答“能否执行某业务动作”，Capability 回答“Agent 可以用什么方式执行”；二者必须
   同时满足。
4. 首版 constraint 只实现真实用例需要的类型，例如：
   - allowed tool IDs；
   - allowed model families；
   - maximum autonomy level；
   - maximum tool calls / external writes；
   - requires approval for side effect。
5. 不使用逗号字符串、任意 JSON 表达式或新的 Role hierarchy 表达 capability。

### 4. ContextScope 是 AuthorizationDecision 的数据边界

1. 不建立第二个“上下文授权系统”。通用 AuthorizationDecision 输出
   `AuthorizedDataBoundary` / `ContextAuthorizationObligation`。
2. RAG、知识库和业务查询在检索前应用 tenant、resource、relation、purpose 与 data
   classification 过滤；禁止先把数据放进 prompt 再检查。
3. `ContextSnapshot` 只记录实际采用的受控来源引用、as-of、policyVersion、decisionId、grantId 与
   数据分级元数据；默认不保存 prompt/输出正文。
4. `SensitiveDataPolicy` 在授权之后继续做内容级出网检查。授权回答“允许访问”，敏感数据政策回答
   “是否允许发送给当前 provider”；任何一层拒绝都不出网。
5. provider 的数据保留、训练使用、区域和合同约束属于 provider policy，必须和数据分级共同决定
   Context 是否可出网。

### 5. 风险与人在回路

1. AI 动作使用 ADR-0030 的 `ALLOW|DENY|CHALLENGE`，但 challenge 类型必须明确：
   - AuthenticationChallenge：需要用户完成近期强认证；
   - TransactionConfirmationChallenge：用户确认绑定 action/resource/version digest 的具体操作；
   - ApprovalChallenge：进入明确的人工审批状态机。
2. AuthenticationChallenge 可使用 RFC 9470；另外两类不伪装成 OAuth 401。
3. Agent 不能自行把 CHALLENGE 标记为完成。完成事实来自 Authorization Server 或产品审批/确认服务。
4. 完成 challenge 后必须重新检查 grant、principal、resource 和 capability，不能直接恢复旧 ALLOW。
5. 写工具必须声明副作用与幂等语义；未知 Tool、未知副作用等级或缺审批实现时默认拒绝。

### 6. 长任务检查点与撤销

长任务至少在以下时刻重新授权：

```text
task/run 开始
  → 每次业务数据或 Knowledge 检索前
  → Context 发送 provider 前
  → 每次 Tool 调用前
  → 每个不可逆或外部副作用前
  → 结果发布/写回产品事实前
```

1. 每次检查使用当前 grant、Binding、Agent version 与资源状态，不缓存整个会话的永久 ALLOW。
2. grant 撤销后，新检查点必须 DENY，并向 Run 发出协作取消信号。
3. 当前正在执行的 provider 请求可能不能立即中止；adapter 应尽可能取消 HTTP/SSE 并把最终状态
   记录为 cancelled/denied，不伪装成成功。
4. 撤销不能收回已经发送给外部模型或工具的数据。方案通过最小 Context、短检查周期、provider
   policy 和数据分级降低风险，不做无法兑现的“即时抹除”承诺。
5. 未来若增加 grant/decision cache，必须定义 epoch/invalidation 和最大撤销窗口并另立 ADR。

### 7. 审计保持分层并可关联

保留四类记录：

| 记录 | 所有者 | 主要内容 |
|---|---|---|
| ActingGrant lifecycle/change audit | Authorization | 谁授予/刷新/撤销谁、范围、版本与时间 |
| Authorization decision audit | Authorization | principal、agent、grant、action、resource、outcome、policyVersion |
| AI invocation/tool audit | AI Runtime | provider/model/tool、Token/费用、耗时、状态和 AI policy |
| Product business audit | 产品域 | 草稿、发布、价格、订单、退款等业务状态变化 |

它们通过 `decisionId`、`grantId`、`agentId/version`、`invocationId`、`runId`、`requestId` 与 `traceId`
关联，不合并物理表。AI invocation audit 继续承担预算与调用状态；通用授权审计不能接管结算事务。

审计不保存 Token、grant secret、prompt、模型输出、工具正文或资源正文；新增正文存储仍需独立数据
分类、同意/授权、加密、保留、访问审计和删除设计。

### 8. 数据模型与所有权

#### AI Runtime/Agent Registry 所有

- `ainer_ai_agent_definition`；
- `ainer_ai_agent_version`（若版本不能合理内嵌 definition 生命周期）；
- Tool/Capability catalog 与版本；
- AI invocation、tool execution、Run/ContextSnapshot 等 AI 数据。

#### Authorization 所有

- `ainer_authorization_acting_grant`；
- `ainer_authorization_grant_permission`；
- `ainer_authorization_grant_scope`；
- `ainer_authorization_grant_capability`；
- grant lifecycle/change audit；
- 通用 decision audit 中的 grant/agent 关联列。

规则：

- ID 使用 PostgreSQL 18 UUIDv7；
- permission/scope/capability 子集使用结构化子表，不把核心查询字段塞进 JSONB；
- grant 与 Agent 跨模块/未来跨库关系使用稳定 ID 和契约，不建立虚假跨库外键；
- grant 本地签发事务同时写 lifecycle audit，失败则回滚；
- 复杂授权解析和有效期/撤销查询使用显式参数 SQL；
- 不为尚未确定的任意 capability condition 创建 DSL 表。

### 9. Token Exchange 只作为远程投影

出现真实跨服务 Agent 消费者后，使用 RFC 8693 Token Exchange 把有效 ActingGrant 投影为短 TTL、
单 audience 的委托 Token：

```text
sub             represented principal
act             {"sub":"authenticated runtime SERVICE subject"}
agent_id         private claim: Agent definition ID
agent_version    private claim
tenant_id?       resolved authorized tenant; omitted for tenantless grant
aud              one target resource server
scope            requested OAuth scopes ∩ subject-token ceiling ∩ client allowlist ∩ grant projection ceiling
grant_id         private claim: online-checkable reference
policy_version
jti
```

约束：

- RFC 8693 `act` 必须是 JSON object；当前模型中它表示真正持有凭据的 runtime SERVICE，Agent 用
  独立 `agent_id/version` 私有 claim 表示；
- OAuth client authentication 始终按 token endpoint 配置执行；可选 `actor_token` 只在 actor 需要与
  client authentication 分开证明时使用，两者不是同一机制；
- subject token、runtime SERVICE、grant、tenant、audience 与 requested OAuth scope ceiling 必须在
  token endpoint 校验；业务 Permission/Capability 仍由 ActingGrant/PDP 在线判断，不折叠进 RFC
  `scope`；
- 不签发 Refresh Token；
- v1 不允许交换出的 Token 再做转委托；
- Token Exchange 不替代 ActingGrant 数据库或在线撤销；
- 自包含 Token TTL 不得长于批准的撤销 SLA；高风险边界继续在线检查；
- 没有远程消费者时不提前实现该 adapter。

### 10. RAR、DPoP 与其他协议

- RFC 9396 并不限制为第三方或一次性交易；Ainer 当前仅在出现需要结构化授权详情的真实第三方交易
  场景时评估采用，不作为内部 AuthorizationRequest、ActingGrant 或 Capability 的替代；
- DPoP 可在远程 Agent/移动端威胁模型要求发送者约束 Token 时评估，不作为两个小程序首版全局
  前置条件；
- CAEP/共享撤销流只在跨系统即时撤销成为真实需求后评估；
- 协议采用必须先说明客户端支持、密钥生命周期、重放保护、故障语义和运维成本。

## 非目标

- 不把 modelId、provider、prompt 或 tool name 当作 Agent 身份；
- 不把 Agent 固定绑定单一 principal；
- 不新增长期 Agent password/client secret 作为首选凭据模型；
- 不允许 agent→agent、递归委托或 grant 转授；
- 不把 Capability 建成另一套 Role hierarchy；
- 不把 ContextScope 建成独立 ACL 真相源；
- 不把业务价格、订单、listing 等事实迁入 AI/Authorization 表；
- 不把 AI invocation audit 与 authorization decision audit 合并；
- 不承诺撤回已发送给外部 provider 的数据；
- 不在没有远程消费者时提前实现 Token Exchange、RAR、DPoP 或 CAEP；
- 不把 `X-Acting-Identity-Id`、普通 Header 或模型输出当授权事实。

## 实施顺序

本 ADR 在 ADR-0030 S0–S3 之后推进：

### A1：Agent definition 与本地一层 grant

- AgentDefinition/version/status；
- ActingGrant + permission/scope 子集；
- grant 签发、到期、撤销和 lifecycle audit；
- 本地 `DelegatedRequester` 决策；
- principal 权限收缩和 agent 版本停用负向测试。

### A2：Capability 与 Context authorization

- Tool/Capability catalog 边界；
- capability constraint 子集；
- AuthorizedDataBoundary obligation；
- RAG/业务查询在检索前过滤；
- ContextSnapshot 与 decision/grant/policyVersion 关联；
- provider 出网前 `SensitiveDataPolicy` 二次检查。

### A3：Tool、副作用、Challenge 与长任务撤销

- 检索/Tool/副作用/发布检查点；
- Authentication/Transaction/Approval challenge 的执行适配；
- 协作取消与 SSE/provider 中断；
- 审计链路关联和失败关闭；
- grant 撤销后后续检查拒绝。

### A4：真实远程边界出现后再做 Token Exchange

- 自定义 token endpoint grant 校验；
- 短 TTL、单 audience、sub/act/grant_id claims；
- 无 Refresh Token、不可传递；
- introspection/撤销、重放和跨 tenant 负向矩阵；
- remote adapter 与 local adapter 契约测试。

## 后果

### 正面

- Agent、model、runtime service 和 principal 不再混为一个身份；
- 委托范围可见、可审计、可过期、可撤销且默认不可传递；
- Agent 不能获得超出 principal 的 Permission、Scope 或 Capability；
- Context 在检索前受控，能够和现有敏感数据政策、快照和调用审计合流；
- 高风险 AI 副作用能复用通用 Challenge、Step-up 与产品审批，而不是依赖 prompt 约束；
- 本地先行、远程按需，避免 Token Exchange 和外部策略基础设施成为首版负担。

### 负面与风险

- 每个关键检查点增加授权读取与审计，需要控制调用次数、索引和批量查询；
- grant 与 principal 实时权限求交会增加实现复杂度，但这是可撤销和不扩权的必要成本；
- provider 请求可能无法瞬时取消，撤销 SLA 必须区分“停止新动作”与“收回已发送数据”；
- Capability catalog、Tool schema 和产品 Permission 分属不同所有者，需要版本兼容和契约测试；
- 远程 Token Exchange 会使 Authorization Server 成为委托签发依赖，届时必须补容量与 HA 验证。

## 安全、数据与隐私

- grant 默认最小权限、短有效期、不可传递；签发者不能授予自己没有或不可委托的权限；
- Agent/context/tool 的任何未知状态默认拒绝；
- Context 只取授权查询结果，prompt、模型输出和工具输入均视为不可信；
- 外部 provider 前同时执行授权数据边界、数据分级和敏感内容策略；
- 高风险工具必须幂等、受审计，并在副作用前重新授权；
- grant 与 Token 不进入日志、错误或模型上下文；
- prompt/输出正文继续默认不落库；
- 任何 remote delegation token 都使用短 TTL、单 audience，并具备重放/撤销策略。

## 运维与迁移

1. 先发布 Agent/grant schema 与本地 adapter，现有 Model Gateway 调用不自动获得代行能力；
2. 以单个 Agent 场景 opt-in，不能用 shadow ALLOW 执行工具；
3. grant/agent version 停用前检查活跃 Run，停用后阻止新检查点并发送协作取消；
4. 回滚时关闭 Agent 场景，不降级为以 SERVICE scope 或 prompt 指令放行；
5. grant、lifecycle audit、decision audit 和 invocation audit 保留可读；
6. Token Exchange 只在 local contract 稳定、真实 remote consumer 存在并完成容量/撤销设计后发布。

## 验收记录

截至 2026-08-02，本 ADR 只完成设计，尚未新增 Agent definition、ActingGrant、Capability catalog、
Context authorization、Token Exchange adapter 或 migration，状态保持 Proposed。接受前至少完成：

- 一层 principal→agent grant 的签发、到期、撤销与防转委托测试；
- permission/scope/capability 三重子集校验；
- principal Binding 撤销或 Agent version retired 后后续检查拒绝；
- Context 检索前过滤、provider 出网前敏感策略和未授权数据不进入 snapshot/prompt；
- 每个 Tool/副作用前重新授权，CHALLENGE 不执行动作；
- 长任务协作取消与“不能撤回已出网数据”的明确状态语义；
- authorization、grant、context、invocation、tool 与产品审计通过关联 ID 可追踪且不合表；
- PostgreSQL 18 migration、并发撤销、append-only audit 和跨 tenant 负向矩阵；
- 真实 remote consumer 出现后才验收 Token Exchange A4，不以空 adapter 接受本 ADR。

## 参考

- [详细方案](../design/authorization-architecture-plan.md)
- [ADR-0003：AI Model Gateway 基线](0003-ai-model-gateway-baseline.md)
- [ADR-0023：受治理 AI 任务执行模型](0023-governed-ai-task-execution-and-identity-weekly-report.md)
- [ADR-0030：通用混合细粒度授权基线](0030-hybrid-fine-grained-authorization-baseline.md)
- [Spring Security OAuth 2.1 Authorization Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/)
- [RFC 8693：OAuth 2.0 Token Exchange](https://www.rfc-editor.org/rfc/rfc8693.html)
- [RFC 9396：OAuth 2.0 Rich Authorization Requests](https://www.rfc-editor.org/rfc/rfc9396.html)
- [RFC 9470：OAuth 2.0 Step Up Authentication Challenge Protocol](https://www.rfc-editor.org/rfc/rfc9470.html)
- [OpenFGA Authorization for Agents](https://openfga.dev/docs/modeling/agents)
