# xiaoqu-platform → Ainer 渐进迁移路线

> 状态：v1.0 · 2026-07-22

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

1. 在 Ainer 建立垂直切片。
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
- 旧 Token 在过渡期通过边界适配，不复制旧 Token 表到 Ainer。

### M4：低耦合业务切片

状态：首个 Workspace 权限与成员生命周期切片已完成，其他业务切片待选择。

- Workspace 已由 M1 技术样本升级为可信租户资源：创建者/tenant 来自 JWT，客户端不能自报 owner。
- Workspace 查询、更新和分页已显式绑定 tenant，并只接受当前主体的 ACTIVE 成员关系。
- `workspace.read/write` scope 与 OWNER/ADMIN/MEMBER 资源角色共同授权；跨租户和非成员访问默认隐藏资源。
- 新邀请先为 PENDING，只有同 tenant 的受邀 JWT 主体本人接受后才激活，不通过共享 Identity 表建立模块耦合。
- 角色变更、移除和所有权转移已使用独立用例；数据库部分唯一索引与 Workspace 行锁保护单一 ACTIVE OWNER。
- 关键允许/拒绝授权决策已进入独立事务审计；Identity Directory、禁用/撤销事务与 access-event outbox 已在 Identity 侧落地。
- Workspace 审计已支持 `workspace.audit.read` + ACTIVE OWNER/ADMIN 的 tenant/resource 绑定分页查询。
- 跨运行时 Directory adapter、outbox relay、Workspace 幂等撤销消费者、热/冷归档和 SIEM 拉取
  均已落地；生产外部不可变副本、告警路由与多节点容量证据仍未完成。

优先选择：

- 独立查询类能力；
- 新建业务；
- 对外依赖少、规则清晰、有测试的能力。

不优先选择 trade 大模块整体迁移，也不把 ai/cdp/wecom 三方耦合圈原样搬入。

### M5：核心交易域

先按能力拆解 trade，而不是迁移整个 Maven 模块：

- trade-core；
- settlement/finance；
- advisor；
- attribution；
- AI assistant。

每个边界都必须先消除跨表读取并建立数据所有权，之后才决定是否成为独立 Ainer 模块或服务。

## 5. 数据策略

- 新表只由 Ainer migration 创建。
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
