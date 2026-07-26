# ADR-0019：Identity 供应、租户上下文与所有权治理

- 状态：Accepted
- 日期：2026-07-26
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无

## 背景

ADR-0018 已建立第一段可用的 Identity 管理面：租户 OWNER/ADMIN 可以查询、加入、调整和移除
非 OWNER 成员，Authorization Server 也可以通过默认关闭的 bootstrap 创建首个平台租户和 OWNER。
该切片有意留下三个边界：

1. 后续租户和用户仍没有生产供应入口；
2. 用户可以拥有多个 membership，但登录只读取 `is_default=true` 的关系并签发单一
   `tenant_id`，非默认租户尚无安全的上下文选择流程；
3. OWNER 不能通过通用成员接口变更，而 Identity 尚无专用所有权转移。

这三项不是彼此独立的 CRUD。若平台先创建可直接使用的固定密码，会把长期凭据暴露给运营系统；
若用修改全局 `is_default` 表示每次租户切换，多设备和并发会话会相互覆盖；若在目标用户无法取得
目标租户 Token 时实现 OWNER 转移，则无法证明接收方本人已在正确租户上下文中同意高权限变更。

Ainer 面向长期维护和商业交付，Identity tenant 还是客户、数据隔离、配额和未来 entitlement 的
主要边界。本 ADR 因而把平台供应、用户激活、租户上下文和 OWNER 转移作为一个连续治理阶段设计，
但按可独立验收的三个切片交付。

## 决策驱动因素

- ACTIVE tenant 不能被平台流程创建为长期无 OWNER 状态；
- 平台控制面不能让 USER Token 获得跨租户超级权限；
- 新用户的长期密码、Passkey 或恢复材料不能由平台运营方代为选择或读取；
- 每个 access token 只表达一个已经实时验证的 tenant 上下文；
- 租户切换不能通过请求头、自报 claim 或修改全局默认租户完成；
- OWNER 转移必须由不同自然人完成，并同时具有强认证、事务原子性和数据库唯一性；
- 权限变化后旧 Token 必须进入既有 revocation epoch / 在线校验失效链路；
- 管理 API、状态机、审计和幂等语义需要能支撑自动开户、控制台和后续商业交付系统；
- 租户角色与商业套餐必须保持两个不同的模型。

## 备选方案

### 方案 A：继续使用启动 bootstrap 创建每个租户

实现成本最低，但要求修改环境变量和重启，无法提供在线幂等、激活、审计和生命周期，也会把
bootstrap 的首环境用途误用成日常业务控制面。拒绝。

### 方案 B：平台直接创建 ACTIVE 用户并返回临时密码

可以快速登录，但平台、工单或日志链路可能接触长期认证材料，后续还需要强制改密、密码过期和
临时凭据恢复语义。拒绝。日常供应只创建一次性激活凭据，其明文只返回或投递一次，数据库只存哈希。

### 方案 C：切换租户时更新 `is_default`

不需要修改 OAuth 流程，但 `is_default` 是账号级共享状态；浏览器 A 切换租户会改变浏览器 B 和
移动端下一次签发结果，也无法表达多个并行租户会话。拒绝。`is_default` 只保留为首次登录落点偏好。

### 方案 D：由当前 OWNER 单方立即转移

事务实现简单，但输错目标主体或账号被冒用会直接交出最高租户权限。拒绝作为正常流程。正常转移
要求当前 OWNER 发起、目标 ADMIN 本人接受；OWNER 丢失另走受职责分离保护的恢复流程。

### 方案 E：统一 USER 与 SERVICE 为平台管理员

会把跨租户权限带入浏览器 Token，并模糊自动化供应、租户内管理和平台安全运维的职责。拒绝。
租户内治理继续使用 tenant-bound USER；平台供应继续使用 tenantless SERVICE。

## 决策

### 1. 总体边界与交付顺序

1. M4.8 按以下顺序交付，后一个切片不得绕过前一个切片的不变量：
   - M4.8A：平台 tenant/user 供应与一次性激活；
   - M4.8B：人员多租户上下文选择；
   - M4.8C：Identity OWNER 专用转移。
2. Identity 领域、应用服务、状态机与持久化继续位于 `ainer-module-identity`；HTTP DTO、OAuth
   会话适配和 security chain 位于 Identity 数据所属的 `ainer-authorization-server`。
   `ainer-server` 不装配 Identity migration，也不建立第二份身份事实源。
3. 平台供应路径使用 `/internal/platform/identity/**`，只接受 tenantless SERVICE Token。
   人员自助与租户治理路径使用 `/api/**`，只接受 USER Token。两类端点不得共享“任一 actor
   均可”的控制器或应用入口。
4. 现有首次环境 bootstrap 保留，但只用于空环境建立第一个平台信任根；M4.8A 上线后，日常创建
   tenant/user 不再使用 bootstrap。

### 2. M4.8A：平台供应与一次性激活

5. 平台控制面至少提供以下业务资源，不暴露数据库表 CRUD：
   - `POST /internal/platform/identity/tenant-provisioning-requests`：幂等创建 tenant、候选 OWNER
     membership 与激活流程；
   - `GET /internal/platform/identity/tenant-provisioning-requests/{id}`：读取状态，不返回任何
     secret；
   - `GET /internal/platform/identity/tenants`：受限分页查询 tenant 安全投影；
   - `GET /internal/platform/identity/users`：受限分页查询用户安全投影；
   - 用户锁定、禁用、恢复等高风险生命周期操作后续使用显式业务操作资源，不提供通用 PATCH 状态。
6. 创建请求必须带稳定 `Idempotency-Key` 和受限 `changeReference`。相同 operator、操作类型和
   幂等键重复调用返回同一结果；相同键但不同规范化请求摘要返回冲突。不得用“查到相同 tenant
   code 就算成功”替代幂等记录。
7. tenant code、用户登录标识和联系地址在进入事务前规范化。请求可以：
   - 为不存在的用户预留待激活账号标识；
   - 为已存在的 ACTIVE 用户建立待接受的供应关系；
   - 对已占用 tenant code、冲突用户状态或不兼容的未完成流程失败关闭。
8. 日常供应不接受调用方选择的长期密码。新用户收到高熵、单次、短 TTL 激活凭据；数据库只保存
   哈希、状态、到期时间、尝试计数和投递元数据。凭据成功消费时由用户本人设置初始密码，随后可按
   Passkey 策略完成 enrollment；激活明文不能再次查询。Passkey-only 激活需要独立 ceremony 设计，
   不由第一实现伪装成已经支持。
9. 平台请求只在 provisioning 表中预留 tenant ID、subject ID（新用户）、规范化 tenant code 和
   OWNER 目标，不提前向核心 tenant/user/membership 表写入半激活记录，也不复用 `LOCKED` 或
   `DISABLED` 冒充待激活。新用户消费激活凭据，或已有用户在认证会话中接受供应关系时，才在单一
   事务中创建 ACTIVE tenant、ACTIVE user（若需要）与唯一 ACTIVE OWNER membership。ACTIVE tenant
   因而从第一条核心事实起就有且只有一个 ACTIVE OWNER；失败或过期流程只留下不可授权的供应记录。
10. 对已有用户，不向平台返回认证材料。平台只得到 request ID、tenant ID、subject ID（若已经
    可确定）、状态和投递结果；接受动作由该用户的认证会话完成。
11. 通知通过应用端口与 outbox 交付，Identity 事务不直接依赖邮件、短信或第三方 SDK。联系字段、
    验证状态、模板版本、投递结果和重试策略需要单独建模；业务审计不保存消息正文或激活明文。
12. M4.8A 的最小 capability 分离为：
    - `platform.tenants.read`
    - `platform.tenants.write`
    - `platform.users.read`
    - `platform.users.write`
    写 scope 不隐式包含读 scope，具体端点显式声明所需 capability。
13. 调用方还必须同时满足：`actor_type=SERVICE`、无 `tenant_id`、正确 issuer/audience、精确
    operator client ID 白名单。tenant-bound SERVICE、USER、仅持有 scope 但不在 operator
    白名单的 client 全部拒绝。
14. 平台 credential 不与 metrics、introspection、OAuth client control、Passkey 恢复或事件重放
    credential 共用。正式生产中写操作至少使用独立 operator，并为禁用 tenant/用户、恢复 OWNER
    等破坏性操作增加 request/approve 或等价职责分离；普通查询不能升级为写能力。

### 3. M4.8B：租户上下文选择

15. `is_default` 只表示首次登录或未显式选择时的默认落点，不是“用户只能访问一个 tenant”的
    授权约束，也不在每次切换时更新。
16. 新增 `GET /api/me/tenants`，只返回当前 USER 的 ACTIVE membership 安全投影：tenant ID、
    code、name、role、是否默认。LOCKED/DISABLED tenant、用户或 membership 不返回。
17. Authorization Code + PKCE 人员流程增加 tenant selection：
    - 用户认证后读取实时 ACTIVE membership；
    - 只有一个可用 tenant 时可以选择默认落点；
    - 多个可用 tenant 时必须显式选择，或使用经过验证的最近选择作为界面提示；
    - 选择结果绑定当前 Authorization Server 会话和当前 authorization request；
    - Token customizer 再次读取 membership，签发恰好一个 `tenant_id` 和该 tenant 的角色。
18. 客户端提交的 tenant ID、查询参数、Cookie 或请求头都只是选择候选，不能直接进入 JWT。最终
    claim 必须来自 Identity 实时关系；跨主体、非成员、非 ACTIVE 或在确认期间被撤销的选择失败。
19. 不提供“任意 tenant ID 的 Token Exchange”作为第一实现。未来若采用 RFC 8693，仍必须要求
    USER 主体、实时 membership、目标 audience/client 白名单与不可扩大 scope，并另立 ADR。
20. 同一用户可以在不同设备或不同浏览器会话持有不同 tenant 上下文。每个 access token 仍只包含
    一个 tenant；Resource Server 不接受 tenant 列表 claim，也不从业务请求覆盖 token tenant。
21. tenant 选择本身不改变角色、不创建 membership。成员撤销、账号锁定、tenant 禁用和 OWNER
    变化必须写入既有 Identity access event，使高风险在线校验拒绝旧 Token；低风险离线 JWT
    仍受短 TTL 到期窗口约束。
22. Passkey 凭据继续绑定稳定用户主体，而不是复制为每 tenant 一份；恢复/enrollment 的安全记录
    必须绑定操作发生时的 tenant 和有效 membership。多租户选择不得扩大 Passkey 恢复权限。

### 4. M4.8C：OWNER 专用转移

23. 正常 OWNER 转移使用“双自然人确认”状态机：
    - 当前 ACTIVE OWNER 在目标 tenant 上下文中发起 `REQUESTED`；
    - 目标必须是同 tenant 的 ACTIVE ADMIN，不能是 MEMBER、非默认/默认状态本身不影响资格；
    - 目标 ADMIN 在同 tenant 上下文中完成强认证后接受；
    - 接受事务原子完成角色交换并进入 `EXECUTED`。
24. 建议端点：
    - `POST /api/tenants/{tenantId}/ownership-transfers`
    - `GET /api/tenants/{tenantId}/ownership-transfers/{transferId}`
    - `POST /api/tenants/{tenantId}/ownership-transfers/{transferId}/acceptances`
    - `POST /api/tenants/{tenantId}/ownership-transfers/{transferId}/cancellations`
    路径表示业务操作产生的资源，不使用 `/changeOwner` 一类 RPC 动词。
25. 发起与接受都要求 USER、可信 tenant/sub、`tenant.ownership.transfer` capability、实时 ACTIVE
    membership、正确资源角色，以及 `amr` 强因子和短 `auth_time`。目标接受不能由当前 OWNER、
    ADMIN 代办或 SERVICE 模拟。
26. transfer 至少包含 `REQUESTED`、`EXECUTED`、`CANCELLED`、`EXPIRED`；若实现把“接受”和
    “执行”拆成不同事务，才增加 `ACCEPTED` 中间态，否则不制造不可恢复的瞬时状态。
27. 每个 tenant 同时最多一个未完成 transfer，由数据库部分唯一索引保证。请求具有短 TTL，
    发起者可在执行前取消；目标拒绝可以记为 CANCELLED 并保留原因。过期状态可惰性收口，但读取和
    接受都必须把已到期请求视为不可执行。
28. 执行事务必须：
    - 锁定 tenant 与旧/新 OWNER membership；
    - 再次验证 tenant、用户和双方 membership 均为 ACTIVE；
    - 把目标 ADMIN 提升为 OWNER；
    - 把原 OWNER 降为 ADMIN，不自动移除；
    - 写入所有权操作审计；
    - 为双方写入 Identity access event/outbox，使旧角色 Token 进入撤销链路；
    - 任何一步失败全部回滚。
29. Identity 数据库新增部分唯一索引，保证每个 tenant 最多一个 ACTIVE OWNER：

    ```sql
    CREATE UNIQUE INDEX ux_ainer_identity_membership_active_owner
        ON ainer_identity_membership (tenant_id)
        WHERE role = 'OWNER' AND status = 'ACTIVE';
    ```

    “至少一个 ACTIVE OWNER”由 tenant 激活、转移和恢复应用事务保证，不能伪称普通数据库约束可以
    跨多行完整表达。
30. OWNER 丢失、账号锁定或不可达不是正常 transfer。恢复流程使用 tenantless SERVICE 的独立
    request/approve credential、不同 service subject、短 TTL 和操作审计；恢复只能选择现有
    ACTIVE ADMIN，不得顺便恢复被禁用主体。恢复和正常转移不得共用端点或授权规则。

### 5. 数据、审计与事件

31. 新增的数据概念至少包括：
    - tenant provisioning request / 唯一预留 / idempotency record；
    - activation grant（哈希、TTL、状态、尝试与消费时间）；
    - notification outbox / delivery projection；
    - tenant ownership transfer；
    - platform identity operation audit 与 ownership audit。
32. 业务状态与对应审计必须同事务提交。审计记录 operation ID、tenant、actor、target、阶段、
    reason/change reference、request ID、发生时间和有限结果元数据；不得保存密码、激活明文、
    Passkey material、Token、邮件/短信正文或完整 HTTP body。
33. 激活、tenant/user 禁用、membership 撤销、OWNER 转移等影响访问的操作复用
    `ainer_identity_access_event` 与 outbox，不再建立一条无法被 Resource Server 消费的平行撤销
    机制。事件必须幂等、可租约投递并保留耗尽恢复能力。
34. migration 只向前新增。既有 migration 不改写；从 M4.7 数据升级时，先检查 ACTIVE OWNER
    重复和孤儿 tenant，再创建唯一索引。发现历史冲突必须阻止发布并给出修复报告，不能静默选择
    一个 OWNER。

### 6. 与商业能力的边界

35. Identity tenant 是客户与隔离边界，但本 ADR 不把套餐字段塞入 tenant、membership 或
    `TenantRole`。`OWNER/ADMIN/MEMBER` 回答“该主体能做什么”；未来 entitlement 回答“该客户购买
    了什么”。
36. Community / Pro / Enterprise、license、订阅、配额和商业模块启用由后续独立
    `ainer-module-entitlement` 或等价边界负责。Identity 只发布稳定 tenant 生命周期事实，不依赖
    计费厂商、价格表或许可证实现。
37. 商业开户系统通过 M4.8A 平台 API 编排供应，不直写 Identity 表。即使 entitlement/支付失败，
    也只能调用显式暂停/取消用例，不能绕过 Identity 审计和撤销事件。

## 后果

### 正面

- 首次 bootstrap 之外，平台终于拥有可自动化、幂等、可审计的日常开户入口；
- 运营方不接触用户长期认证材料；
- 多租户用户可以在并行会话中安全选择 tenant，而不会争用全局默认值；
- OWNER 转移有双方确认、强认证、数据库唯一索引、事务审计和旧 Token 撤销五层保护；
- Identity 授权与未来商业 entitlement 保持正交，避免套餐逻辑污染安全角色。

### 负面与风险

- Authorization Server 需要管理 tenant selection 会话，浏览器多节点部署因此需要共享会话或
  可验证的无状态选择上下文，不能继续只证明单节点行为；
- 激活通知引入联系字段、隐私、重试、退信和供应商运维问题；
- tenant/user 平台写权限是新的高价值 credential，需要专门轮换、职责分离和告警；
- 在线撤销依赖、低风险 JWT 自然到期窗口和外部不可变审计等现有限制仍然存在；
- 三个切片共同构成完整体验，但必须逐个验收，不能以尚未落地的后续切片描述当前能力。

## 运维与迁移

建议发布顺序：

1. 先发布 schema、不变量检查、审计和默认关闭的平台 read API；
2. 建立独立 platform identity operator client、scope 白名单、secret store 与审计告警；
3. 启用 provisioning write API 和通知测试通道，验证幂等、过期、重试及无孤儿 ACTIVE tenant；
4. 发布 `/api/me/tenants` 与 tenant selection，会话多节点方案验证后再接生产 browser client；
5. 发布 OWNER transfer，先灰度到测试 tenant，验证并发、撤销传播和恢复演练；
6. 全部稳定后，才把商业开户或管理 UI 接入，不允许 UI 直连数据库或 bootstrap。

回滚以关闭对应端点和停止新流程为主。已创建的 tenant、用户、membership、请求、审计和 outbox
事实不得物理删除；未完成流程通过显式 CANCELLED/EXPIRED 收口。唯一 OWNER 索引属于安全不变量，
上线后不作为普通功能开关回滚。

## 验收标准

每个切片至少提供：

- 空库与 M4.7 升级库的 PostgreSQL migration 证据；
- 随机端口 HTTP、实际 RSA JWT 与真实 PostgreSQL 集成测试；
- USER/SERVICE、tenant-bound/tenantless、scope、operator、跨 tenant 和非 ACTIVE 负向矩阵；
- 并发和重复请求测试，不以单线程 happy path 代替数据库不变量；
- 业务状态、审计、access event 与 outbox 的同事务回滚证据；
- 统一错误 envelope、no-store、受限日志与秘密不落库检查；
- 配置失败关闭、指标、告警建议、运维回滚和故障恢复文档；
- 完整 `mvn test` 且数据库测试实际执行、0 skipped。

M4.8A 额外证明幂等键与 tenant code 并发预留冲突、激活明文仅出现一次、过期/重放拒绝、通知重试、
失败/过期供应不污染核心 Identity 表，以及 ACTIVE tenant 从首次写入起就不孤儿；
M4.8B 额外证明并行 tenant 会话互不影响、伪造选择拒绝、选择后撤销失效；M4.8C 额外证明并发接受
只能成功一次、始终最多一个 ACTIVE OWNER、双方旧 Token 进入 revocation epoch。

## 本 ADR 不包含

- 管理控制台 UI 与品牌登录页面；
- 社交登录、企业 IdP、SCIM、短信登录或 Device Code；
- entitlement、license、计费、价格和支付；
- USER 跨租户超级管理员；
- 任意 audience/scope 的通用 Token Exchange；
- 真实邮件/短信供应商选型；
- 现有生产 IAM、HA、WORM 审计和灾难恢复的完成声明。

## 参考

- [ADR-0005：Identity 与 OAuth 2.1 安全基线](0005-identity-and-oauth2-security-baseline.md)
- [ADR-0007：Workspace 成员生命周期、所有权与授权审计](0007-workspace-membership-lifecycle-and-audit.md)
- [ADR-0008：Directory 与访问撤销传播边界](0008-identity-directory-and-access-revocation.md)
- [ADR-0010：安全运维双人审批与授权审计生命周期](0010-security-operations-and-audit-lifecycle.md)
- [ADR-0011：高风险 API 选择性在线 Token 校验](0011-selective-online-token-validation.md)
- [ADR-0013：受审计 OAuth tenant 服务客户端生命周期](0013-audited-oauth-service-client-lifecycle.md)
- [ADR-0017：Resource Server Step-up 授权策略](0017-resource-server-step-up-policy.md)
- [ADR-0018：管理授权模型与租户成员管理](0018-management-authorization-and-tenant-member-management.md)
