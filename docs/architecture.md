# Ainer 架构总览

> 权威状态：M4.8A + Ainer Admin integration · 2026-07-26

## 1. 系统定位

Ainer 同时承担三种职责：

- 可发布的 Java framework 和 starter；
- 承载通用企业能力与 AI 能力的模块化应用；
- 面向社区版、企业版和行业产品的产品工程基线。

它不是把竞品模块重新命名，也不是通过配置把任意单体自动转换为微服务。

## 2. 当前模块图

```text
ainer-server                         JWT Resource Server、Actuator、平台/内部端点
├── ainer-module-workspace           租户资源、成员生命周期、撤销消费、OWNER 恢复、审计热/冷生命周期
│   ├── ainer-starter-persistence    MyBatis、Flyway、PostgreSQL、UUID
│   └── ainer-starter-web            HTTP 异常与请求追踪
├── ainer-module-ai-runtime          模型端口、Provider、策略、SSE、费用审计
│   ├── ainer-starter-persistence
│   ├── ainer-security               可信主体、租户、authority 契约
│   └── ainer-starter-web
├── ainer-starter-security           JWT 验证、SecurityContext 投影、401/403
└── ainer-starter-web

ainer-authorization-server           独立 OAuth 2.1/OIDC 发行物、Identity 管理面、Passkey、Directory、relay
├── ainer-module-identity             用户、租户、成员管理、平台预配/激活、禁用/撤销与可靠 outbox
├── Spring Security Authorization Server 7.1
└── JDBC registered client / authorization / consent / WebAuthn credential

ainer-starter-web -> ainer-spring -> ainer-core
ainer-starter-persistence -> ainer-core
ainer-starter-security -> ainer-security -> ainer-core

ainer-dependencies                   独立 BOM，统一依赖版本
```

约束：

- 箭头只允许向下游依赖上游。
- `ainer-core` 没有任何 Spring 依赖。
- Starter 不依赖业务模块。
- `ainer-server` 只做装配，不承载业务领域逻辑。
- `workspace` 与 AI runtime 各自拥有表、migration、端口和适配器；事务边界位于应用用例。
- Workspace tenant/owner 来自 `AuthenticatedActor`；所有查询绑定 tenant，只有 ACTIVE 成员参与授权，所有权转移由锁与数据库唯一索引共同保护。
- Identity Directory 和 tenant 成员列表只暴露 ACTIVE 安全投影；账号/成员状态变化与撤销 outbox
  在同一事务，不允许 Workspace 共享查询 Identity 表。Identity 的 USER-facing HTTP adapter 位于
  `ainer-authorization-server`，`ainer-server` 不装配 Identity migration，Identity 应用/领域层
  不依赖 Web。
- 跨运行时调用使用服务 JWT 和最小 scope：Directory 是同步只读端口，撤销是至少一次 outbox + Workspace receipt；网络调用不进入 Identity 数据库事务。
- 高风险安全运维使用短时双人审批：申请者和批准者是不同服务主体、分别持有最小 scope，批准事务锁定并重新验证目标状态。
- Workspace 授权审计以同事务热/冷搬迁控制热表增长，在线查询和 SIEM 稳定游标读取两表并集；归档数据仍属于 Workspace 数据库。
- persistence starter 只装配共性，不拥有任何业务表或 Repository。

## 3. 目标模块模型

```text
ainer-boot/
├── ainer-dependencies/
├── ainer-framework/
│   ├── ainer-core/
│   ├── ainer-spring/
│   ├── ainer-starter-web/
│   ├── ainer-starter-persistence/       # 已落地的 MyBatis/Flyway/PostgreSQL 共性
│   ├── ainer-starter-security/          # Resource Server 通用能力
│   ├── ainer-starter-observability/
│   └── ainer-starter-test/
├── ainer-module-identity/                # 用户、组织、角色、授权业务
├── ainer-module-workspace/               # 已租户化的资源授权参考切片
├── ainer-module-ai-runtime/              # 模型网关、调用与费用审计
├── ainer-server/                          # 已落地的业务 Resource Server 发行物
├── ainer-authorization-server/            # 已落地的独立认证发行物
└── ainer-app-*/                           # 经证据支持后拆出的服务发行物
```

这些是演进方向，不代表应一次性创建所有空模块。模块只在拥有明确职责、测试和消费者时落地。

M1 有意没有提前抽取 persistence starter。M2 的 AI invocation 成为第二个 PostgreSQL 消费者后，才把 MyBatis/Flyway/PostgreSQL/UUID 装配提炼到 `ainer-starter-persistence`。业务 Mapper、Repository、migration 和事务仍属于各自模块。

## 4. 业务模块内部结构

Ainer 采用 feature-first，并在 feature 内保持清晰的端口与适配器：

```text
dev.ainer.module.workspace
├── workspace/
│   ├── api/                HTTP 请求与响应模型、Controller
│   ├── application/        用例、事务边界、端口
│   ├── domain/             聚合、值对象、领域规则、事件
│   └── infrastructure/     MyBatis、外部客户端、消息适配器
└── shared/                 仅限本模块真正共享的类型
```

依赖方向：

```text
api -> application -> domain
infrastructure -> application/domain
domain -> Java standard library only
```

`api` 可以位于业务模块，也可以位于可执行发行物的 adapter 包；关键约束是 application/domain
不能反向依赖 Web，传输 DTO 不成为应用命令或领域模型的兼容包袱。Identity 成员管理采用后者。

不会要求每个简单 CRUD 都机械创建四层接口；复杂度必须由真实业务规则证明。

## 5. 单体与服务化

服务化包含无法由配置完成的变化：进程边界、数据所有权、网络失败、幂等、超时、重试、契约版本和分布式一致性。因此采用两个层次：

1. **共享业务代码**：领域、应用用例和稳定契约可复用。
2. **独立应用装配**：单体和每个服务拥有明确依赖清单、配置、数据库职责和发布生命周期。

`ainer.runtime.mode` 的含义严格限定为：

| 值 | 含义 |
|---|---|
| `monolith` | 当前发行物使用进程内适配器 |
| `service` | 当前发行物使用远程或消息适配器 |

它不负责拆库、启动网关、生成服务、改变事务边界或保证分布式一致性。

### 5.1 官方参考管理应用

Ainer Admin 不作为 Ainer Boot 的新 Java 模块或第三个后端发行物。它是 Ainer Studio 中
`ainer-studio/templates/ainer-admin` 的前端模板，通过 `/ainer-admin/` 同源入口连接
`ainer-authorization-server` 的 OAuth/OIDC 与 Identity 成员 API。Boot 负责协议、client、身份、
授权、OpenAPI 和 active gate；Studio 负责前端源码、Blocks、预览和交付。

第一版同源反代只改变边缘路由，不改变 Identity 数据所有权，也不把前端静态资源引入
Authorization Server JAR。完整部署边界见
[`ainer-admin-integration.md`](ainer-admin-integration.md)。

## 6. HTTP 与错误模型

成功响应和错误响应都使用 `ApiResponse<T>`：

```text
code       稳定机器码，例如 AINER.COMMON.NOT_FOUND
message    可安全展示的消息
data       成功数据；失败为 null
requestId  请求关联标识；分布式追踪启用后另行提供标准 traceId
timestamp  服务端产生响应的时间
```

HTTP status 始终保持真实语义：

| 场景 | HTTP |
|---|---:|
| 参数错误 | 400 |
| 未认证 | 401 |
| 无权限 | 403 |
| 资源不存在 | 404 |
| 状态冲突 | 409 |
| 业务规则不满足 | 422 |
| 未知服务异常 | 500 |

## 7. 安全架构

M3/M4 已形成以下边界：

- `ainer-server` 默认作为 OAuth2 Resource Server，验证 issuer、签名、有效期和 audience。
- `ainer-authorization-server` 是独立发行物，协议数据使用 Spring Security JDBC repository。
- 浏览器/移动端优先 Authorization Code + PKCE；机器调用使用 Client Credentials；实际 grant 由每个 registered client 白名单决定。设备可使用 Device Code，系统间委托可评估 Token Exchange。
- M4.5 已用测试专用 public client 验证 PKCE S256、真实表单登录、授权码单次交换、错误 verifier
  与回调地址拒绝；生产 browser client 控制面、会话治理和登录 UI 仍是独立能力。
- Ainer Admin 在 `dev` profile 下使用固定 public client、PKCE S256、default tenant 与四个最小
  scope；成员 API 在 JWT 后继续读取官方 authorization active 状态，当前 access token 可自助
  撤销。该开发 client 与同源自动化证据不等于生产 browser client 生命周期控制面。
- M4.6 使用 Spring Security 7.1 WebAuthn 建立默认关闭的 Passkey 主线：UV-required、真实签名
  ceremony、条件 MFA、ACTIVE/REVOKED 生命周期、恢复码/管理员双人恢复、受控首次 enrollment、
  登录限速和 Resource Server step-up。最后一个 ACTIVE Passkey 不允许普通自助删除；真实设备
  矩阵、恢复通知、共享限流和多节点 session 尚未完成。
- 短信、微信和企业身份源通过认证编排或标准扩展授权接入，不复活 password grant。
- 业务模块从 `AuthenticatedActor` 获取 `sub`、`tenant_id`、`actor_type` 和 authorities，不读取
  JWT，也不接受客户端身份请求头；缺失/未知 actor type 失败关闭。
- AI API 已强制 `ai.invoke` scope。
- Workspace API 强制 `workspace.read` / `workspace.write` scope，并以数据库 ACTIVE OWNER/ADMIN/MEMBER 关系决定具体资源权限；tenant 与 owner 不能由客户端指定。
- Identity tenant 成员 API 强制 USER actor、`tenant.members.read|write`、可信 tenant claim 与
  数据库 ACTIVE OWNER/ADMIN 关系；写操作同事务审计且不能通过通用接口修改 OWNER。
- Identity 平台预配 API 强制 tenantless SERVICE、tenant/user 成对 capability 与精确 operator
  client ID 白名单。申请事务只预留 tenant/subject，使用与首租户 bootstrap 共享的 tenant
  code/username advisory lock，并创建只存摘要的一次性 grant 或已有用户接受通知；联系目标与激活
  明文只进入 AES-GCM 保护的 outbox。新用户消费 grant 或已有用户本人以 USER Token 接受时，才在
  单一事务创建核心 tenant/user（若需要）/OWNER。`REQUESTED` 始终不是可授权身份事实。
- 平台 tenant/user 查询是独立的只读安全投影，分别要求对应 read capability，采用
  `page/size/total` 且单页最多 100，不暴露密码、OAuth、membership 或通知数据。未完成预配通过
  显式 `/cancellations` 子资源关闭；request、预期 grant、未发布通知 payload 与阶段审计同事务，
  不提供通用状态 PATCH。
- provisioning notification 通过独立应用端口和默认关闭的 OAuth2/HTTPS gateway adapter 交付；
  Identity 不拥有邮件/短信 SDK、模板或供应商账号。notification ID 是下游幂等键，网关确认后
  销毁本地可解密 payload；`PUBLISHED` 只表示网关持久接收，不代表最终触达。
- 外部通知网关负责把供应商回执归一化为 `DELIVERED|FAILED`，再使用另一组 tenantless SERVICE
  credential 回调 Identity。Identity 只保存一条与 UUIDv7 notification 关联的最小终态事实，
  不保存供应商原始 body、联系地址或自由文本错误；该接收端代码基线不等同于真实供应商联调证据。
- 新邀请是 PENDING，只有同 tenant 的目标 JWT 主体本人接受后才激活；启用可选 Directory client 时，邀请创建前还必须通过远程 ACTIVE member 校验。Workspace 不跨模块读取 Identity 私有表。
- 通用角色变更不能触碰 OWNER；所有权转移锁定 Workspace 并由 PostgreSQL 部分唯一索引保证最多一个 ACTIVE OWNER。
- 跨租户和非 ACTIVE 成员访问按 404 隐藏资源；成员角色不足返回 403。关键允许/拒绝授权决策持久化到独立事务审计。PENDING/ACTIVE membership 接到可信 Identity 撤销事件后单调变为 REVOKED，不再参与授权；OWNER 也可因全局安全禁用而被撤销。
- Workspace 审计查询额外要求 `workspace.audit.read` 和 ACTIVE OWNER/ADMIN，并绑定 tenant/workspace 分页；M4.2 增加热表保留、归档、SIEM 拉取、拒绝窗口与 OWNER 缺失指标。
- Identity 已提供 ACTIVE Directory、安全禁用/撤销事务、可恢复 outbox relay 和投递指标。Workspace 以 event ID receipt 幂等消费，旧事件不撤销事件发生后新建的 membership。
- 内部 HTTP adapter 使用短生命周期 Client Credentials JWT、issuer/audience、`actor_type=SERVICE`、scope 与可信 publisher subject；生产仍需 TLS、受控网络，后续可叠加 mTLS 或服务网格身份。
- 耗尽事件重放复用原 event ID；OWNER 恢复只提升现有 ACTIVE 成员，不恢复被禁用主体。两者由不同 request/approve Client 完成并写模块所属安全操作审计。
- M4.3 在上述本地 JWT 认证后为高风险路径追加 RFC 7662 在线校验；Authorization Server 使用 Identity 当前状态与最新 access-event 作为人员 Token revocation epoch。普通低风险 JWT 请求仍有自然到期窗口，不能宣称所有 API 强实时撤销。
- 两个发行物的 Prometheus endpoint 使用相同的 tenantless SERVICE + `platform.metrics.read` 契约；指标 client 与业务、Directory、relay、SIEM 和 introspection client 分离。共享 PostgreSQL 只是 Authorization Server 多实例前提，尚未验证的浏览器会话、容量和故障切换不能写成已完成 HA。

## 8. AI 原生能力

M2 已按以下调用边界落地：

```text
AI HTTP / application port
  -> Model allow-list / prompt size / sensitive pattern
  -> Tenant node-local rate limit
  -> PostgreSQL daily-budget reservation
  -> ModelProvider port
  -> OpenAI-compatible JDK HttpClient adapter
  -> Usage / Cost / Status audit

后续 Agent Runtime
  -> Tool Registry + Permission
  -> RAG / Retrieval
  -> Memory
  -> Evaluation / Guardrails
```

业务模块不能直接散落调用模型厂商 SDK。所有调用必须经过模型网关，确保模型切换、费用控制、审计和数据策略可执行。

后续 Run / Artifact 与 Knowledge 只按真实纵向切片增量实现，候选语义见
[`design/ai-runtime-data-model.md`](design/ai-runtime-data-model.md) 和
[`design/knowledge-data-model.md`](design/knowledge-data-model.md)；两份提案不能作为一次性建表依据。

当前边界说明：

- 非流式和 SSE 都产生同一 `AiInvocation` 审计；SSE 最终 usage 事件是正常完成的结算点。
- 数据库日预算依赖租户 advisory lock，能在共享 PostgreSQL 范围内防止并发穿透；每分钟限流当前是 node-local，不是集群精确配额。
- prompt 与模型正文不落库，只持久化不可逆 fingerprint 和治理元数据。
- 租户与主体只从验证后的 JWT `tenant_id` / `sub` 构造；旧的 AI 身份请求头已经移除。
- OpenAI-compatible DTO 只存在于 infrastructure；Ainer 端口不暴露供应商协议。

## 9. 验证策略

- 纯 Java 规则：JUnit。
- 自动装配：`ApplicationContextRunner`。
- Web 语义：MockMvc 与随机端口集成测试。
- PostgreSQL、Redis、消息组件：Testcontainers，不用 H2 模拟生产数据库。
- 包和模块边界：ArchUnit + Maven 模块依赖。
- migration：空库执行、重复执行策略、升级路径和真实 PostgreSQL 验证。
