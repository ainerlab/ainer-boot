# Executive Summary

审查对象：[ADR-0033：Account、Workspace 与 Isolation 模型基线](../decisions/0033-account-workspace-isolation-model-baseline.md)。本报告刻意假设该决策在未来 5—10 年可能失败，并从 mdpress、xq-platform、AI Agent、合规隔离和迁移安全五个方向寻找反例。

**结论：C. Major revision required。**

ADR-0033 的方向有价值：Account 不应从属于企业，Workspace 可以成为跨产品复用的默认协作范围，Tenant 也不应继续作为同时承载客户、组织、权限和隔离的万能业务对象。但当前文本同时作出或留下了三组可能在实现中被固化的冲突与等价关系：

1. “Account 是全局人类身份根、`sub` 始终表示 Account”与非人 Subject/Agent 合同冲突；
2. Workspace 被同时用于默认资源 owner、协作、订阅、资产、用量和隔离，却没有充分区分 custody、商业、法律与版权关系；
3. legacy Identity Tenant ID 等于 canonical Workspace ID，也等于默认 Isolation key。

这不是命名问题，而是基数和生命周期问题。一旦这些边界的混同进入 JWT、外键、审计、对象存储路径和授权缓存，未来再拆分会同时触发身份、权限和数据迁移。最坏结果是 Ainer 只是把过去的 God Tenant 改名为 God Workspace。

Workspace 可以成为统一的**默认协作与访问范围**，但不应被定义为所有产品唯一的“顶层边界”。未来很可能同时出现：

- Workspace 上方或旁侧的 Customer/Contract、Identity Realm、Isolation Domain、Enterprise Group 等正交边界；
- Workspace 下方的 Project、Publication、Brand、Store、Knowledge Base、Agent Scope 等产品边界。

这些概念不应被塞进一棵通用 `Space` 树，也不需要现在全部建表或拆服务；ADR 只需承认它们具有独立生命周期和非 1:1 基数，避免在基础模型中封死扩展路径。

因此，本审查不是否定 Account/Workspace 方向，而是否定把它当前的适用范围升级为不可重新打开的平台不变量。ADR-0033 应在身份主体、Workspace 职责、隔离权威和迁移协议四个方面完成重大修订后再接受。

# Strong Points

ADR-0033 有以下值得保留的基础判断：

1. **Account 与 Workspace 生命周期解耦。** Account 可以在没有 Workspace 时存在，也可以加入多个 Workspace；离开一个 Workspace 不等于注销账户，关闭一个人类账户也不应级联删除企业资源。
2. **避免 Organization-first。** Organization 被放在 Enterprise Extension，而非强迫 mdpress 个人创作者接受企业组织模型，这一边界是正确的。
3. **识别 Tenant 的语义过载。** 不再向产品 API 和领域语言持续暴露 Tenant，有助于阻止“tenant = company = billing customer = data scope”的继续固化。
4. **区分授权与商业能力。** Membership、Role、Permission、Subscription、Entitlement、Quota、Usage 被明确区分，且禁止把 Pro 当 Role，这为后续解耦留下了正确语言。
5. **认可资源解析优先于客户端自报。** authoritative Workspace 由资源记录或可信 resolver 确定；Token 中的 Workspace 只是 selector/access ceiling，再与实时 Membership、Permission 求交集。Header 和路径值不能决定资源归属。
6. **采用兼容迁移而非重写。** 保留 `tenant_id`、双 claim 过渡、影子比对和分阶段退出旧语言，符合模块化单体的演进方式。
7. **没有把 Profile 变成万能人物档案。** Creator、Employee、CRM Customer 等产品身份没有被强行合并到基础 Profile。
8. **邀请不等于账户、任职不等于成员关系。** 这两个区分能避免未注册用户、员工生命周期和资源访问权被错误绑定。

这些强点足以支持“修订后继续推进”，但不足以支持“按现状冻结”。

# Critical Risks

## 1. Workspace 被定义得比证据更大

Personal Workspace、Team Workspace 和 Enterprise Workspace 确实都可以提供稳定 ID、生命周期、成员关系和默认资源命名空间，因此可以共享一个最小 Workspace 契约。但三者的完整治理并不相同：

- Personal Workspace 通常随一个人的注册、恢复和隐私权请求演化；
- Team Workspace 依赖邀请、协作和轻量所有权转移；
- Enterprise Workspace 受合同、企业托管身份、审批、法务保留和业务连续性约束。

“所有 Workspace 始终有且仅有一个 active OWNER”就是差异被抹平的例子。单一 OWNER 作为个人或小团队的治理策略未必错误，问题是 ADR 没有说明它是恢复责任人、最终治理裁决者还是财产权人，并把同一语义和基数固定给所有 profile。企业 Workspace 的法律/商业归属可能由 Legal Entity 或 Customer Contract 表达，运维治理则可能需要 custodian、审批组或不同恢复机制；把这些全部压到一个员工 Account 上会在离职、冻结或争议时形成单点。

此外，ADR 尚未决定 Personal Workspace 是每个 Account 一个、每个产品一个、每个产品部署/provisioning scope 一个，还是每个 Identity Authority 一个，却已经决定复用 Identity Tenant ID。产品 provisioning scope 与身份 issuer/trust realm 是两个不同维度。对同时使用 mdpress 和 xq-platform 的人：

- 共享 Personal Workspace 会耦合两个产品的删除、订阅、数据驻留和资源可见性；
- 每产品各建一个 Personal Workspace 又要求 Product/Provisioning Scope 进入唯一性和归属规则。

这是 ID、隔离和生命周期的基础问题，不能在冻结顶层模型后留作实现细节。

## 2. Account-first 与通用 Subject/JWT 合同发生冲突

“Account 是人类注册和恢复的生命周期根”是合理的；但“全局”的信任范围没有被限定，而“JWT `sub` 始终表示 Account”又与非人 Token profile 不兼容。

未来的认证、授权和归因链至少涉及五种参与者或状态，它们并不都是同一种 Subject：

- 人类 Account；
- 企业或平台拥有的 Service Principal；
- AI Agent 的稳定执行/审计引用；
- credential actor 与 represented subject 组成的委托链；
- 没有认证 Subject 的 Anonymous request，以及产品明确需要时才存在的 Guest session。

企业托管身份还暴露了“全局”的危险。一个人可能先用个人微信身份注册 mdpress，后被企业通过 OIDC/SCIM 预配 xq-platform。若因邮箱相同自动合并为全局 Account，企业停用 SSO 或员工离职可能错误锁死个人内容；若不合并，同一自然人又会拥有多个合法 Account。因此自然人、登录身份和安全账户不能视为同一对象，Account 也只能在明确的 Identity Authority/Realm 内稳定。

平台级授权与审计应使用 issuer-qualified、typed `SubjectRef`。在既有身份/Agent 基线下，它首先覆盖 Human Account 和 Service Principal；`AgentActorRef` 是独立的稳定执行参与者，不直接持有凭据，也不应被静默提升成 Subject。Anonymous 没有 SubjectRef；只有产品明确允许游客持久写、草稿或 AI 试用时，才需要短期 GuestSession/GuestRef 和显式转正协议。`Principal`/`Actor` 是请求期投影，`LoginIdentity` 是认证绑定，它们都不应成为新的万能 Identity 聚合。

当前 ADR 一方面把主拓扑写成 Account → WorkspaceMembership，另一方面又允许 Membership 绑定包含 ServiceAccount/Agent 的 SubjectRef；既有 Agent 基线则把 credential Subject 与 Agent ref 分开。这会留下关键歧义：非人主体能否成为 OWNER/ADMIN？若能，它可能意外获得人员治理和账单能力；若不能，Agent 和集成又通过什么获得受限访问？该问题必须在基线中明确，并与既有 Agent 决策对齐。

## 3. Tenant 名称可以退出，隔离权威不能消失

Tenant 不需要重新成为产品可见的万能对象；恰恰相反，未来仍会存在多种彼此正交的运维、商业和信任边界，继续用 Tenant 或改用 Workspace 统一代称都会重现语义过载：

- 数据驻留、KMS key、备份恢复、legal hold 和保留策略；
- region/cell/shard 路由；
- 私有部署或客户专属部署；
- 企业合同、付款主体和采购边界；
- 身份 issuer、SSO 和 federation 信任边界。

这些边界彼此也不相等。一个集团合同或私有部署可以包含多个 Workspace；一个 Workspace 的不同受监管资源也可能绑定不同地域或加密域。因此 `IsolationContext.key == Workspace.id` 只能作为共享库 row ownership 的默认实现，不能成为所有隔离维度的领域不变量。

`IsolationContext` 是请求期解析出的值对象，不能独自承担 placement policy、KMS、retention、region 和部署生命周期的权威来源。ADR 至少要为 `IsolationDomain`/`DataPlacementBinding`、Identity Realm 和 Commercial Contract 等独立语义保留扩展关系。它们可先由实际 owning domain 以引用或策略表达；在第二个消费者证明复用前，不必全部提升为 Foundation 聚合，更不意味着引入微服务。

## 4. Workspace 正在成为新的 God Object

当前模型让 Workspace 同时成为 Resource、Collaboration、Subscription、Entitlement、Asset、Usage 和 Isolation 的共同根。共同带有 `workspace_id` 不等于属于同一个聚合。

合理的职责边界是：

| 归属 | 应包含 | 不应被推导为 |
|---|---|---|
| Workspace core | 稳定空间身份、状态、默认资源命名空间、成员治理、治理策略引用 | 法律主体、付款客户、物理隔离域、所有资源的版权人 |
| Workspace extension | Personal/Team/Enterprise 治理策略、产品安装/设置、经证明需要独立生命周期的下层 scope | Foundation 中预建一棵通用空间树 |
| 独立领域 | Subscription/Billing、Entitlement allocation、Quota/Metering、Asset storage/provenance/rights、Data placement、产品 Resource | Workspace aggregate 内的附属字段或同生命周期子对象 |

这些独立领域可以将 Workspace 作为作用范围，但必须拥有自己的状态机和权威来源。例如取消订阅不应删除 Workspace；资产从一个空间转移也不应改写其历史作者或版权；用量结算失败不应改变 Membership。

## 5. mdpress 达到千万用户后，语义问题比建 Workspace 的成本更大

每个注册用户自动创建 Personal Workspace 在数据库规模上并非根本性失败，真正问题是以下关系并非一一对应：

- **作者身份：** 一个 Account 可以使用多个笔名、品牌和公众号；一个品牌也可以由多个 Account 和 Agent 共同创作。
- **内容版权：** 作者、当前保管 Workspace、Rights Holder、Licensee 和 Publisher 可能各不相同。
- **内容资产：** 草稿可能先存在 Account-private 范围；若产品明确允许持久游客写入，也可能先在短期 Guest 范围，后被显式转入团队。内容还可能被协作、授权、复制、联合署名或跨 Workspace 发布。
- **AI Memory：** 可能属于 Account-private、Workspace-shared、Agent、Brand、Document 或一次 Task，不能默认全部继承 Workspace 生命周期。
- **订阅：** 个人 Pro 可能覆盖一个 Account 的多个产品/空间；Enterprise Contract 可能覆盖多个 Workspace；用量却仍按具体 Workspace、Agent 或任务计量。
- **公开身份：** Account/Profile 是安全与个人资料对象，不应兼任长期公开的 Creator/Content Identity。账号关闭后，署名和版权证据仍须存在。

因此，mdpress 成功后的候选产品概念包括 CreatorIdentity、Publication/Brand、Authorship/ContentRights、Personal Library 和多层 Memory Scope；是否引入应由真实生命周期和第二个用例证明，而不是要求 MVP 一次建全。并非所有内容都应变成 Account-level Content；不同资源类型应能显式选择 account-private、workspace-scoped、platform-global 或 multi-party ownership policy。

## 6. xq-platform 的大型集团不能被 Workspace → Organization 一棵树覆盖

大型珠宝集团可能同时具有集团客户、多个合同、多个法人、多个品牌、多个经营单元、多个门店和跨区域数据策略。它们不是同一种层级：

- `Customer/CommercialAccount/Contract` 表达采购、SLA、结算和服务关系；
- `LegalEntity` 表达法定主体；
- `Organization/Department` 表达人员与汇报结构；
- `BusinessUnit/Amoeba` 表达经营核算；
- `Brand`、`Store/Location` 表达市场和运营边界；
- `Workspace` 表达产品中的默认协作与访问范围。

这些关系可能是 N:M。例如一个合同覆盖多个品牌 Workspace；一个法人参与多个经营单元；同一组织目录被多个产品 Workspace 使用；一个 Workspace 又可能包含多个法人数据。把 Organization 限定为只属于一个 Workspace，或把所有概念塞进 Organization 树，会破坏集团合并报表、共享主数据和跨空间治理。

Organization、LegalEntity、BusinessUnit、Brand 和 Store 应先由 xq Enterprise Extension 持有；Customer/Contract 也应先由实际商业 owning domain 负责，待第二个消费者证明后再考虑提升。Identity Realm 和 Isolation Domain 首先是平台必须允许的信任/部署语义，不代表现在就要建设通用模块。Foundation 不需要理解珠宝组织模型，但不能阻止这些概念引用一个或多个 Workspace，也不能用 Workspace 父子关系替代它们。

## 7. AI Agent 不是 Account，也不天然属于单一 Workspace

Agent 至少涉及六个不同关系：

1. `AgentDefinition` 的配置、版本和管理归属；
2. runtime `ServicePrincipal` 的凭据身份；
3. 不直接持证的稳定 `AgentActorRef` 与审计归因；
4. represented Subject 与当前 ActingGrant 的授权来源，以及未来可能由后继 ADR 定义的安装权限；
5. 本次 invocation 的 Workspace 和 resource scope；
6. billed-to、usage attribution 和 memory scope。

一个 Workspace 安装的自动发布 Agent 不应在创建者离职后停摆，也不应继续持有创建者的幽灵权限。平台提供的共享 Agent 又可能在多个 Workspace 中运行。Agent 因此不能被建模为 Human Account，不能默认通过人员 WorkspaceMembership 获得 OWNER/ADMIN，也不能仅靠“属于某个 Workspace”解释权限、账单和记忆。

资源审计至少要能区分 credential actor、effective/represented subject、Agent ref、authority/grant 和 invocation Workspace。AgentDefinition、runtime credential、长期安装/授权及 Memory 不必属于同一个聚合。

## 8. 迁移最大的风险是授权扩大和权限复活

当前 Identity tenant membership 并不必然等价于 legacy inner Workspace access。ADR 已明确禁止把现有内层 `ainer_workspace` 直接解释为 canonical Workspace，也保留了 legacy nested membership scope，这是重要保护。残余风险出现在未来逐类重分类或迁移这些资源时：若没有逐资源证明授权等价，tenant 级成员仍可能看到过去只对内层 Workspace 成员开放的数据。ADR 也有意把 Tenant OWNER/ADMIN 解释为顶层 Workspace 治理角色，但角色名称相同不能自动证明新旧治理能力等价，仍需 privilege diff。

复用 `ainer_identity_tenant.id` 的决定还早于资产盘点。如果一个 legacy tenant 需要拆成多个 canonical Workspace，或多个 tenant 需要归并到一个商业/部署边界，裸 ID 等值无法表达 split、merge、realm 和 lineage。私有部署复制数据时，相同 UUID 也未必代表同一信任域对象。

双 claim 的字符串相等同样不能证明语义相等。ADR 已要求 audience/claim-contract version 隔离、不一致时 fail-closed，方向正确；缺口是尚未覆盖 USER、SERVICE、Agent 的 profile 矩阵，以及 Refresh Token、authorization/session/consent、managed service client、授权缓存和撤销 epoch 的 drain/rotate 顺序。若旧、新 Resource Server 对同一兼容值采用不同领域解释，仍可能形成 confused deputy。

ADR 已要求 cutover epoch、单一 writer、每请求只读一个 authority、shadow mismatch 和失败关闭。问题是这些原则尚未被细化成可验收的跨 pod 协议。**若** single-writer 只靠部署约定而没有 writer fencing，投影没有 aggregate version/watermark，回滚也没有反向同步，就会出现以下权限复活时间线：

1. 旧 Authority A 撤销 Membership，但投影到新 Authority B 的 outbox 延迟；
2. 新 pod 读取 B，仍把旧 Token 判为有效；
3. OWNER 转移事件乱序，B 短暂出现零个或两个 OWNER；
4. 蓝绿部署期间旧 pod 写 A、新 pod 写 B，两边唯一约束都成立但全局冲突；
5. 切换到 B 后回滚 reader 到 A，未反向同步的撤销被“复活”。

因此问题不是 ADR 允许 A/B 同时写，而是它尚未给出足以证明该情况不可能发生的验收协议。规范性不变量应是每阶段唯一 authority、撤销 fail-closed、事件有序且可判 stale、切换可 fencing、回滚不复活权限；writer fencing、传播 watermark 和按 Workspace 的 cutover generation 是候选实现控制，应由迁移实施 ADR 选型并验证。

# Counter Examples

| 场景 | ADR-0033 隐含假设 | 失败表现 | 需要的独立概念或关系 |
|---|---|---|---|
| mdpress 用户同时使用多个 Ainer 产品 | 一个 Account 的 Personal Workspace 唯一性无关产品/部署 | 共享会耦合删除与合规；分开又无法解释唯一 ID 和跨产品订阅 | Product/Provisioning Scope、显式 Personal Workspace provisioning policy、跨 Workspace Commercial Account |
| 创作者加入经纪团队并授权品牌发布 | 内容有唯一 Workspace owner 即可解释所有权 | 作者、版权人、保管空间、发布方和收款方分离 | CreatorIdentity、RightsHolder、License、Publisher、Custody Workspace |
| 珠宝集团拥有多品牌、多法人、多区域 | Enterprise Workspace 下挂 Organization 足够 | 合同、SSO、CMK、品牌、法人和经营单元被迫复制或错误合并 | Customer/Contract、Identity Realm、Isolation Domain、LegalEntity、Brand、BusinessUnit |
| 私有部署中的多个 Workspace | Workspace ID 就是完整隔离边界 | 一个部署/密钥域包含多个空间；导入导出时裸 UUID 冲突 | Deployment Realm、qualified reference、DataPlacementBinding |
| Marketplace 跨空间交易 | 订单和资产必须有单一 authoritative Workspace | 买家、卖家、平台、escrow 和履约方均参与，单 owner 失真 | Transaction/Marketplace scope、Party roles、custody 与 legal ownership 分离 |
| 企业 OIDC/SCIM 用户也有个人 mdpress 账号 | 同一自然人应映射为全局 Account | 自动合并导致企业接管个人账户；不合并又违反“全局”假设 | Identity Realm、显式 link/federation、enterprise-managed LoginIdentity |
| Workspace 安装长期运行的 Agent | Agent 可依附某 Account 或普通 Membership | 创建者离职后停摆，或 Agent 保留幽灵权限并可能成为万能 Admin | ServicePrincipal、AgentActorRef、bounded grant；未来安装权限由后继 ADR 定义 |
| 匿名用户试写并消耗 AI | 所有持久行为都有 Account | 被迫创建假 Account，或 AI 用量、草稿转正和反滥用无归属 | 明确持久游客行为后才引入 TTL GuestSession/GuestRef、claim/transfer protocol、guest meter |
| 一个 Enterprise Contract 覆盖 xq 与 mdpress | Subscription 自然属于单一 Workspace | 重复采购、权益复制、退款和配额冲突 | Commercial Account/Contract 与 Workspace 集合的显式 allocation |

这些反例说明未来确实需要 Workspace 上层、旁侧和下层概念，但不支持引入一个新的通用父对象。正确约束不是“所有东西都有 Workspace parent”，而是“每个有独立生命周期、权威来源或基数的边界都有明确类型和关联”。

# Required Changes

以下修改是接受 ADR-0033 前的语义门槛，不要求同步修改 Java、数据库或拆分服务。

## 1. 缩小 Workspace 的规范性承诺

将 Workspace 定义为：**稳定的默认资源命名空间、协作治理范围和常用授权 scope**。删除或弱化“所有产品唯一顶层业务根”的表述。

按资源类型声明归属策略，而不是全平台强制单一 owner：

- workspace-scoped；
- account-private；
- platform-global；
- contract/party-scoped；
- multi-party；
- 仅由 Workspace custody，但 rights/billing/isolation 另有归属。

不应要求每条记录都携带全部关系；应由领域按需选择 `ownedBy`、`custodiedBy`、`createdBy`、`rightsHolder`、`billedTo` 和 `placedIn` 中有意义的关系。

## 2. 将平台安全合同改为 Subject-qualified，人类 onboarding 保持 Account-first

至少冻结以下语义，而不必创建万能 Subject 表：

| 概念 | 规范性职责 |
|---|---|
| Identity Authority/Realm | 定义 issuer、信任域和主体 ID 的解释范围；可先是协议边界，不承诺新表、模块或管理 API |
| Human Account | Realm 内的人类账户、安全状态、恢复与关闭生命周期 |
| LoginIdentity | 外部/本地认证标识到 Human Account 的受控绑定 |
| issuer-qualified、typed SubjectRef | Human Account/ServicePrincipal 的授权、资源创建者和审计引用；具体 tuple/存储形式由身份 ADR 决定 |
| ServicePrincipal | 非人凭据、轮换和调用主体 |
| AgentDefinition/AgentActorRef | Agent 配置版本与稳定行为归因；和 SubjectRef、凭据分离 |
| Principal/Actor/Delegation | 请求期 credential actor、effective subject 和代行链 |
| Anonymous/Guest | Anonymous 无 SubjectRef；仅在允许持久游客行为时才创建 TTL GuestSession/GuestRef |

Human Account 的 WorkspaceMembership 优先只表达人员治理关系。ServicePrincipal 应通过有限的 SubjectBinding 获权，Agent 继续使用既有一层 principal → agent ActingGrant，并明确禁止非人参与者成为通用 OWNER。若仍允许 generic Membership，则必须提供 subject type × role 矩阵和不可变量。

JWT `sub` 不应再被规定为始终 Account。应按 issuer、audience、actor type 和 claim contract version 定义封闭 Token profile：USER token 可用 `sub=Account`，SERVICE token 使用 ServicePrincipal，Agent 委托沿用既有 credential/effective subject + agent ref 契约，Anonymous 没有 `sub`。审计保留 credential subject、effective/represented subject、Agent ref 和 grant/decision ID。

还必须冻结企业控制权边界：企业 IdP/SCIM 可以管理 enterprise LoginIdentity binding、WorkforceEngagement 和 WorkspaceMembership，但不得因邮箱相同自动 merge、关闭或接管个人 Human Account；SCIM 预配默认只是目录项/邀请而非已激活 Account；offboarding 只撤销企业身份路径和企业空间访问。

## 3. 把正交边界写进模型允许范围

ADR 应明确以下关系不能默认 1:1。下表是常见基数示例，不是新的全平台固定基数：

| 边界 | 与 Workspace 的典型基数 | 负责什么 |
|---|---|---|
| Human Account | M:N | 人类账户生命周期与人员参与 |
| Commercial Account/Contract | 1:N 或 N:M | 采购、订阅、SLA、付款与权益分配来源 |
| Identity Realm | 常见 1:N，也可 M:N/federation | issuer、SSO、managed identity 与信任边界 |
| Isolation Domain | 常见 N:1，资源级可形成 M:N | region/cell、KMS、retention、legal hold、私有部署与 placement policy |
| Party/LegalEntity/Organization | N:M 或产品内层级 | 法律主体、人员组织及企业治理 |
| Product/Provisioning Scope | 由产品决定，不能从 Identity Realm 推导 | Personal Workspace 的创建、唯一性和跨产品生命周期 |
| Product Scope | Workspace 内或跨 Workspace | Project、Publication、Brand、Store、Knowledge Base、Agent Scope 等独立生命周期范围 |

这张矩阵是扩展许可，不是要求预建全部聚合。只有出现独立生命周期、不同 authority 或非 1:1 基数时才引入具体概念，避免为了抽象而抽象。

## 4. 拆开 Workspace 周围的独立领域

- Subscription/Billing 不得被固定为只绑定 Workspace；它可绑定实际购买主体，再显式 allocation 到 Account、Workspace 或 Workspace 集合。Commercial Account 是候选，而非 Foundation 必建聚合。
- Entitlement/Quota 是商业结果与策略输入，不是 Role；Usage/Metering 独立记录消费主体、作用范围和 billed-to。
- Asset 分离二进制/对象存储、当前 custody、provenance、版权和许可。
- IsolationContext 由持久的 Workspace 和实际存在的 trust/deployment/placement policy 解析，不作为这些 authority 的替代品。
- Personal/Team/Enterprise 改为治理 profile/policy，而不是保证可自由互转的永久枚举。ADR 应分别定义 OWNER 在各 profile 中的治理语义和 cardinality；若引入 custodian/recovery authority，还要明确其不是法律或商业 owner。

## 5. 为两个产品保留明确扩展点

mdpress 应允许产品域在真实生命周期证明后引入 CreatorIdentity、Brand/Publication、Authorship/ContentRights、Personal Library 和多层 AI Memory Scope，而不是要求 MVP 预建全部概念；Personal Workspace 可采用 lazy、幂等 provisioning，避免跨产品重复和大量永不使用空间。

xq-platform 应能在 Enterprise Extension 引入 Customer、Enterprise Group、LegalEntity、Organization、BusinessUnit/Amoeba、Brand 和 Store/Location，并允许它们与 Workspace 建立 N:M 关系。Foundation 提供 Account、Workspace、Subject 和安全基线，并为商业/隔离 owning domain 保留可组合关系；具体能力在出现可复用证据后再提升。

## 6. 补全 Agent 的长期授权和归因模型

ADR-0033 应与 Agent 基线一致，明确区分 AgentDefinition、runtime ServicePrincipal、AgentActorRef、represented Subject、authority/grant、invocation Workspace、billed-to 和 memory scope。Agent 不应通过伪造 Human Account 或人员治理 Membership 获得能力。当前一层 principal → agent ActingGrant 可继续作为 v1；创建者离职后仍需长期自治的 Workspace Agent 应成为后继 ADR 的强制复审触发器，由后继 ADR 定义 installation/policy authority，不能在本报告中静默扩大授权模型。

## 7. 重写 Tenant/Workspace 迁移的不可逆部分

1. **先盘点，后决定 ID。** 删除 Phase 0 对 `IdentityTenant.id == canonical Workspace.id` 的无条件冻结。规范性要求是 realm-qualified、版本化、可解释 split/merge 的兼容映射；alias ledger，例如 `LegacyTenantRef(realm, id) -> WorkspaceRef(realm, id)`，只是一种候选实现。只有经证明确为 1:1 时才允许同值优化。
2. **允许 split/merge。** 新旧引用必须保留 lineage、不可歧义或复用；具体 ID/tombstone 机制由迁移 ADR 设计，跨信任域引用必须可被 issuer/realm 限定。
3. **逐资源证明授权等价。** 保留 ADR 已有的 legacy nested scope 禁令；未来重分类时不得批量提升成员权限。Tenant ADMIN/OWNER 到新治理角色的映射必须提供 privilege diff 和无权限扩大证明。
4. **每阶段只有一个 lifecycle/membership authority。** 这是必须冻结的不变量；带 aggregate version 的投影、按 Workspace cutover generation、writer fencing 和 propagation watermark 是候选控制，由迁移 ADR 选择并形成可执行验收。
5. **撤销 fail-closed。** 高风险授权在新 authority 尚未证明同步时必须读取有效 authority 或拒绝；writer 切换后的 rollback 不得复活权限。反向同步、重新 fencing 或只允许 roll-forward 是实施 ADR 必须明确的策略。
6. **补全既有 audience/version Token 合同。** 不以 `tenant_id == workspace_id` 字符串比较作为唯一语义证明；每个 audience 只接受一个明确 profile，兼容适配器一次性解析为 typed WorkspaceRef，域层不得同时消费两个 raw claim。覆盖 USER/SERVICE/Agent、refresh/session/consent/managed service client、缓存和撤销 epoch。
7. **扩展 data-plane inventory。** 除数据库外，覆盖 Redis、搜索索引、队列/outbox/DLQ、对象存储/CDN、KMS/AEAD associated data、备份、导出、数仓、Webhook、OAuth records 和审计证据。
8. **历史证据不改写。** 审计和历史事件保留原 legacy ref，追加 alias 与翻译版本；事件使用稳定 event ID 和 aggregate version，防止旧事件重放覆盖新状态。

## 8. 增加失效条件，而不是禁止重新讨论核心根

删除 Open Questions/Acceptance Record 中“不得重新打开 Account 身份根、Workspace 资源根、Tenant 历史兼容角色”的绝对限制。基线应列出强制复审触发器：

- legacy tenant 与 Workspace 不是 1:1；
- 一个合同覆盖多个 Workspace；
- 一个 Workspace 跨多个 residency/KMS domain；
- 私有部署需要 federation 或跨 realm 导入；
- Workspace split/merge；
- Service/Agent 或明确存在的 Guest 行为无法由现有身份/委托 profile 安全表达；
- 资源出现 multi-party ownership 或跨 Workspace rights/custody。

重新评审前，Acceptance Record 至少应完成以下 walkthrough 和基数验证：跨产品 Personal Workspace、企业托管身份与个人身份并存、集团多法人多区域、Marketplace 交易、内容版权转移、Agent 创建者离职、匿名草稿转正，以及 membership revoke 在混合版本部署中的 fail-closed 行为。

# Decision

**C. Major revision required。**

可以保留的方向是：Account 与 Workspace 独立、Workspace 作为默认协作/访问范围、Organization 作为企业扩展、Role 与 Entitlement 分离、Tenant 退出产品语言，以及通过模块化单体渐进迁移。

不能按现状接受的，是把以下内容冻结为平台不变量：

- Account 的“全局”范围未限定，且 `sub` 始终为 Account 与非人 Token profile 冲突；
- Workspace 的默认 resource home/owner 没有与商业、法律、版权 custody 和物理隔离关系建立明确逃生口；
- Personal/Team/Enterprise 被固定为同一 OWNER 语义和 cardinality，而完整治理差异仍未解决；
- `IdentityTenant.id == Workspace.id == IsolationContext.key`；
- 上述核心结论在验证反例前不得重新打开。

ADR-0033 若不修改，短期仍能支撑 mdpress MVP 和中小型 xq 客户，但成功规模越大，越会在跨产品身份、企业托管、内容版权、集团治理、Agent 授权、合规部署和迁移撤销上积累不可逆耦合。完成本报告列出的语义修订后，Workspace 仍可以成为 Ainer Foundation 的重要默认坐标；它不应成为新的万能 Tenant。
