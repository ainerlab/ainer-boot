# xiaoqu-platform → Ainer 渐进迁移路线

> 状态：v1.2 · 2026-08-02

## 1. 迁移原则

迁移不再采用“复制改包名”，也不采用“57 万行全部重写完成后一次切换”。两种方式都会形成长期分支、重复开发和不可控回归。

Ainer 采用 strangler + vertical slice：

- xiaoqu 继续承载生产业务；
- 新功能优先在 Ainer 范式中建设；
- 旧模块按业务价值、风险和边界成熟度逐片迁移；
- 每个切片可以独立上线、观测和回退；
- 旧实现只在流量完全切走后下线。

## 2. 迁移单元

迁移单元不是 Maven 模块或文件夹，而是可验证的业务能力，例如：

- 创建并查询 workspace；
- 用户登录与令牌签发；
- AI 模型调用与费用记录；
- 商品详情读取；
- 订单取消规则。

每个切片必须同时包含 API、规则、数据、权限、审计、测试和上线方案。

## 3. 标准流程

### 3.1 发现

1. 记录现有接口、调用方、数据表和异步消息。
2. 从生产日志和测试中提取真实行为，不把旧类结构当需求。
3. 标出未知规则、人工兜底和历史兼容行为。
4. 建立可执行的 characterization tests 或契约样例。

### 3.2 设计

1. 明确能力属于哪个领域。
2. 定义应用用例与数据所有权。
3. 设计向后兼容 API 或适配层。
4. 设计数据同步、回填和回退路径。
5. 通过架构评审后再编码。

### 3.3 实现

1. 在能力所有者所在的目标系统建立垂直切片：Ainer 通用平台能力进入 Ainer；Object、Listing、
   Offer、Customer 等 XQ 产品能力进入由已发布 Ainer 制品生成的 `xq-platform-next`，不得反向写入
   脚手架。
2. 用 PostgreSQL Testcontainers 验证 migration 和查询。
3. 添加权限、审计、可观测性和失败路径测试。
4. 必要时在 xiaoqu 添加薄适配器，而不是把 Ainer 代码反向塞入旧框架。

### 3.4 切流

1. 影子读取或双读比较。
2. 小比例流量和明确指标。
3. 写入切换使用 outbox/CDC/双写协调，必须有一致性对账。
4. 指标稳定后逐步扩大。
5. 保留可操作的回退开关和数据修复脚本。

### 3.5 下线

1. 确认没有调用方、定时任务和人工入口。
2. 停止旧写入，保留只读观察期。
3. 归档数据与接口文档。
4. 删除旧代码和兼容层。

## 4. 推荐波次

### M0：Ainer Foundation

状态：已完成。

- Boot 4.1/JDK 25 Reactor。
- core/spring/web/server。
- HTTP 错误与 request ID。
- 自动装配和启动测试。

### M1：全新 workspace 样本

状态：已完成。它不是旧业务迁移，而是用于证明 Ainer 数据库与模块范式的技术样本。

- workspace 创建、重命名、查询与分页。
- workspace member 唯一约束与稳定冲突错误。
- Flyway migration、MyBatis adapter、应用事务和乐观锁。
- Testcontainers PostgreSQL 自动化测试与 PostgreSQL 18 真实启动验证。
- ArchUnit 领域、应用和适配器边界。

### M2：AI Model Gateway

状态：已完成。Ainer 已形成独立于旧 xiaoqu AI 模块和任何竞品实现的 clean-room 垂直切片。

原因：这是 Ainer 的产品差异化，而且可以在不替换旧管理后台的情况下被 xiaoqu 调用。

- 已完成一个基于 JDK HttpClient 的 OpenAI-compatible provider adapter。
- 已完成流式与非流式调用，SSE 包含最终 usage 与完成事件。
- 已完成租户、主体、模型、Token、费用、耗时、状态和策略审计，且不保存 prompt/输出正文。
- 已完成超时、provider 限流/不可用映射、node-local 租户限流、PostgreSQL 日预算和基础敏感凭据模式。
- 已完成稳定 HTTP 契约；xiaoqu 的实际接入在身份边界与部署方式确认后另行实施，不在本里程碑中擅自修改旧系统。
- 已用 PostgreSQL 18.4 与本地 provider 合约服务验证成功、SSE、拒绝、失败、审计隔离和错误脱敏。

### M3：Identity 与认证

状态：foundation 已完成，权限控制面继续推进。

- 已用 authenticated principal 和可信租户 claim 替换 M2 的临时 AI tenant/subject 请求头上下文。
- 已落地用户、租户、默认成员关系和角色最小 PostgreSQL 模型。
- 已落地 ACTIVE tenant member Directory 安全投影、账号禁用、非 OWNER membership 撤销和同事务 access-event outbox。
- 已落地默认开启、可显式关闭的 Resource Server starter，以及统一 401/403。
- 已落地独立 Spring Authorization Server、外部 RSA key、JDBC client/authorization/consent 和 Client Credentials 测试。
- Authorization Code + PKCE 已用测试专用 public client、真实 HTTP 登录会话和 PostgreSQL 完成
  S256 正反门禁；public client 不配置 Refresh Token，生产 browser/OIDC client 控制面、人员账号
  控制面仍未完成。
- 已建立默认关闭的 Passkey 完整代码主线：UV-required、RP/Origin 失败关闭、无凭证密码
  bootstrap、已登记账号条件门禁、真实虚拟 authenticator 签名 ceremony、软撤销/最后凭证保护、
  恢复码与管理员双人恢复、受控首次 enrollment、登录限速和 Resource Server step-up；恢复通知、
  真实设备矩阵、共享限流和多节点 session 仍未完成。
- Identity 权威运行时已提供 tenant 成员列表、加入、角色变更与软移除 API，使用 USER capability
  scope + 可信 tenant claim + ACTIVE OWNER/ADMIN 资源角色并同事务审计；首个平台 tenant/OWNER
  使用默认关闭、严格幂等且带事务 advisory lock 的 Authorization Server bootstrap。
- 已按 [ADR-0019](../decisions/0019-identity-provisioning-tenant-context-and-ownership-governance.md)
  落地 M4.8A 预配与激活核心：tenantless SERVICE、成对 capability、operator 白名单、幂等/并发
  预留、短时限次 grant、AES-GCM notification outbox、已有用户本人接受与原子创建 ACTIVE
  tenant/OWNER；随后补齐独立 OAuth2 client、HTTPS notification gateway publisher、调度、
  幂等与积压指标、tenant/user 安全分页、未完成申请显式取消，以及 provider-neutral 的
  `DELIVERED|FAILED` 终态回执接收基线。真实外部通知网关/供应商联调、最终送达验证、生产告警和
  0-skipped 发布门禁仍是 M4.8A 后续收口。
- 后续再依次推进 Authorization Server 租户上下文选择与 Identity OWNER 双方确认转移；
  `is_default` 只保留为首次登录落点，不作为并行会话的租户切换机制。
- 旧 Token 在过渡期通过边界适配，不复制旧 Token 表到 Ainer。

### M4：低耦合业务切片

状态：首个 Workspace 权限与成员生命周期切片已完成；首个 xq 产品纵向切片已经选定但尚未实现。

- Workspace 已由 M1 技术样本升级为可信租户资源：创建者/tenant 来自 JWT，客户端不能自报 owner。
- Workspace 查询、更新和分页已显式绑定 tenant，并只接受当前主体的 ACTIVE 成员关系。
- `workspace.read/write` scope 与 OWNER/ADMIN/MEMBER 资源角色共同授权；跨租户和非成员访问默认隐藏资源。
- 新邀请先为 PENDING，只有同 tenant 的受邀 JWT 主体本人接受后才激活，不通过共享 Identity 表建立模块耦合。
- 角色变更、移除和所有权转移已使用独立用例；数据库部分唯一索引与 Workspace 行锁保护单一 ACTIVE OWNER。
- 关键允许/拒绝授权决策已进入独立事务审计；Identity Directory、禁用/撤销事务与 access-event outbox 已在 Identity 侧落地。
- Workspace 审计已支持 `workspace.audit.read` + ACTIVE OWNER/ADMIN 的 tenant/resource 绑定分页查询。
- 跨运行时 Directory adapter、outbox relay、Workspace 幂等撤销消费者、热/冷归档和 SIEM 拉取
  均已落地；生产外部不可变副本、告警路由与多节点容量验证仍未完成。

首个 xq 产品纵向切片固定为：

1. `xq-zhiwu` 匿名读取公开行业信息流与详情；
2. 已认证用户选择服务端验证过的 Acting Identity；
3. 商家 operator 建立 Object/Version 与 Industry Listing 草稿；
4. 具备相应 capability 的主体完成采购、品控、拍摄或录货协作；
5. 审核并发布 Industry Listing；
6. `xq-shop-next` 独立发布并读取 Consumer Offer 流与详情；
7. 顾客以非 Workspace membership 主体完成喜欢、收藏或咨询；
8. 运营后台通过 Effective Access 执行受限审核并查看关联审计。

该切片同时验证 platform-app 注册、匿名/顾客/业务 Acting Identity 分轨、通用 Role/Binding、领域
relation、员工任职/岗位事实、产品 capability、对象存储、共享 Object 事实、独立 Listing/Offer
发布语义、审计、OpenAPI SDK 和两个小程序接口。完整授权模型见
[`Ainer 通用授权与 AI 代行详细方案`](../design/authorization-architecture-plan.md)。公司内部部门、
员工任职与岗位的迁移必须遵循
[`Ainer 组织与员工目录详细方案`](../design/organization-workforce-architecture-plan.md)：
`TenantMembership != WorkforceEngagement`、Position 不等于 Role、BusinessLocation 不等于 OrgUnit，
离职不删除同一 Subject 的 Customer/Seller 身份。第一阶段不以购物车、标准订单、支付或 AI 对话为主线。

该产品切片还必须以真实 HTTP/序列化验证：tenantless USER 能凭 Customer owner/participant 关系
访问本人资源而不能访问他人资源；Anonymous 与已登录 public path 得到相同公开字段投影；Industry
Listing 的发布状态/权限不能推出 Consumer Offer 已发布或可发布，Consumer Offer 也不能反向改变
Industry Listing。上述产品代码、表和 migration 均位于 `xq-platform-next`，Ainer 只提供已发布的
通用契约、适配器与 Golden Consumer 门禁。

后续仍优先选择独立查询、新建业务，以及对外依赖少、规则清晰、有测试的能力。不优先迁移
trade 大模块，也不把 ai/cdp/wecom 三方耦合圈原样搬入。

### M5：核心交易域

先按能力拆解 trade，而不是迁移整个 Maven 模块：

- trade-core；
- settlement/finance；
- advisor；
- attribution；
- 有独立数据、权限和验收场景支持的 AI 辅助能力。

每个边界都必须先消除跨表读取并建立数据所有权，之后才决定是否成为独立 Ainer 模块或服务。

## 5. 数据策略

- 新表只由数据所有者所在项目的 migration 创建：Ainer migration 只创建 Ainer 通用表；Object、
  Industry Listing、Consumer Offer、Customer、订单等产品表只由 `xq-platform-next` 所属模块的
  migration 创建。
- 已发布 migration 不修改。
- 数据回填脚本可重复、可审计、可断点续跑。
- 双写必须包含幂等键和对账任务。
- 读模型可通过 CDC 构建，但不能成为绕过数据所有权的永久方案。
- 删除旧表必须经过独立审批和备份，不属于普通代码迁移提交。

## 6. 验收门槛

每个迁移切片至少满足：

- 功能契约与旧行为差异已记录；
- 正常和失败路径自动化测试通过；
- 权限和租户隔离验证通过；
- 数据对账无未解释差异；
- 延迟、错误率和资源指标达标；
- 回退演练成功；
- 旧调用方清单归零后才能删除旧实现。

## 7. 禁止事项

- 全局包名替换后宣称迁移完成。
- 为追求编译通过继续保留循环依赖。
- 使用 H2 证明 PostgreSQL migration 正确。
- 迁移时同时改变所有业务规则和所有数据结构。
- 没有对账和回退方案就进行双写切换。
- 把竞品受限源码作为迁移捷径。

## 8. 2.0 项目映射与切流边界

以下映射于 2026-07-30 确认，属于后续路线的统一命名：

本文 `M0–M5` 只表示 `xiaoqu` 迁移波次；Ainer Boot 的全局 `P0–P5` 产品化阶段与首个外部
消费者合同以
[`Ainer Boot 产品定位、竞品能力矩阵与路线图`](../design/ainer-scaffold-design.md)
为准，两套编号不得混用。

| 产品范围 | 1.0 项目 | 2.0 项目 |
|---|---|---|
| 顾客端 | `xq-shop` | `xq-shop-next`，正式切换后接管 `xq-shop` 名称 |
| 行业信息与协作端 | `xq-assistant` | **`xq-zhiwu`** |
| 两个 2.0 小程序的共同后台 | `xiaoqu-platform` 中的旧实现 | `xq-platform-next` |

`xq-assistant` 的 2.0 版本只能称为 `xq-zhiwu`。它是面向翡翠同行、珠宝公司、商家、采购与供给
人员的公开行业信息与协作网络；采购、品控、拍照和录货是其中按 relation/capability 授权的协作
能力，不是产品被定义为内部员工工具的理由，也不能因为旧项目名含 assistant 而被当成通用 AI 助手。

两个小程序按接口面切分，不按 tenant 切分：

- shop API 面向 `xq-shop-next` 的匿名访问者与顾客主体；
- zhiwu API 面向 `xq-zhiwu` 的匿名访问者、行业参与者与业务 Acting Identity；
- admin API 面向运营管理端；
- 三个接口面共享明确所有权的产品领域，不共享 Controller DTO 或持久化对象；
- 每个切片保持单一写入主系统，通过影子读、对账、灰度和可操作回退完成切流。

具体 URL 前缀和旧接口兼容策略在实现前由独立 API 决策确定；项目名称映射不自动决定 URL。

`xq-platform-next` 通过已发布的 Ainer Maven 制品组合构建，不复制 Ainer 源码，也不在
`xiaoqu-platform` 内继续扩张新的第二套平台内核。现有 `xq-server` 只作为运行中的业务事实和
迁移来源；历史 `mysql` 等数据库厂商包名、目录和抽象不得复制到新后台。
