# Ainer 架构总览

> 权威状态：`v1.4.0` 正在发布准备；`v1.3.0` 是当前稳定版本；`v1.0.0` 为升级起点与 `1.0.x` LTS；运行基线 Spring Boot 4.1.1；`v1.1.0` withdrawn · 核对 2026-08-28

## 1. 系统定位

Ainer 同时承担三种职责：

- 可发布的 Java framework 和 starter；
- 承载通用企业能力与 AI 能力的模块化应用；
- 面向社区版、企业版和行业产品的产品工程基线。

它不是把竞品模块重新命名，也不是通过配置把任意单体自动转换为微服务。

正式架构名称为
[演进式模块化平台架构](decisions/0024-evolutionary-modular-platform-architecture.md)：

- 用战略 DDD 识别领域、上下游和数据所有权，战术 DDD 只用于存在真实不变量的能力；
- 用端口和适配器隔离 HTTP、数据库、消息、身份、AI Provider 与其他外部系统；
- 以模块化单体作为默认业务运行形态，Authorization Server 因安全边界独立发行；
- 满足明确触发条件和工程准备条件后，再通过独立装配与 remote adapter 按需服务化；
- 不为简单 CRUD 机械增加层次，也不把微服务基础设施预建成脚手架默认依赖。

## 2. 当前模块图

```text
ainer-server                         JWT Resource Server、Actuator、平台/内部端点
├── ainer-module-workspace           Workspace 资源、成员生命周期、OWNER 转移、审计热/冷
├── ainer-module-ai-runtime          模型网关、策略、SSE、费用审计、Agent 注册表
├── ainer-module-authorization       ADR-0037 决策器、Binding/审计、端点 adapter
├── ainer-module-dictionary          树形字典 + 多语言 + Spring Cache
├── ainer-module-config              动态配置 + 版本史 + AES-GCM secret
├── ainer-module-notification        ChannelSender 端口 + SKIP LOCKED 队列 + 可选 webhook/SMTP
├── ainer-module-file                文件元数据 + 大小/类型限制 + 管理 API
├── ainer-module-organization        组织目录（Incubating，ADR-0042）
├── ainer-module-knowledge           Knowledge Foundation（Incubating，ADR-0044）
├── ainer-module-task                任务调度（Incubating，ADR-0047）
├── ainer-starter-security           JWT 验证、SecurityContext 投影、401/403
├── ainer-starter-persistence        MyBatis-Plus/MyBatis、Flyway、PostgreSQL、UUID
├── ainer-starter-web                HTTP 异常与请求追踪
└── ainer-starter-observability      Observation + requestId/trace MDC；OTLP 默认关

ainer-authorization-server           独立 OAuth 2.1/OIDC 发行物、Identity 管理面、Passkey
├── ainer-module-identity            HumanAccount/ServicePrincipal/Credential foundation
├── Spring Security Authorization Server 7.1
└── JDBC registered client / authorization / consent / WebAuthn credential

ainer-starter-web -> ainer-spring -> ainer-core
ainer-starter-persistence -> ainer-core
ainer-starter-security -> ainer-security -> ainer-core
ainer-starter-cache -> Spring Cache + Caffeine/Redis + 分布式锁
ainer-starter-observability -> ObservationRegistry + requestId/trace MDC（OTLP 默认关）

ainer-dependencies                   独立 BOM，统一依赖版本
```

`ainer-initializer` 是构建期工具而非运行时模块。Manifest v1 保持已发布兼容合同；`v1.3.0` 新增的
Manifest v2 仅在显式选择 `simple-service + workspace` 时生成带安全边界的独立消费者项目，
并由该项目自己的 Wrapper、真实 JWT 与 PostgreSQL 验证。它不会把生成项目并入 Ainer reactor，
也不会复制 Ainer 源码；完整决策见
[ADR-0052](decisions/0052-initializer-v2-secure-vertical-slice.md)。
`v1.4.0` 发布候选进一步为已有单模块 Maven/Spring Boot 项目提供 `plan-add` / `add`：调用者显式给出
Flyway 起始版本，工具只新增切片文件、有限合并顶层 POM，并通过 manifest package 下的配置类
导入 Workspace；不修改宿主 Application、配置、README 或 Wrapper。多模块、Gradle、plugin/profile
策略和自动 migration 编号不在首版范围，见
[ADR-0053](decisions/0053-initializer-existing-project-and-authorization-composition.md)。

约束：

- 箭头只允许向下游依赖上游。
- `ainer-core` 没有任何 Spring 依赖。
- Starter 不依赖业务模块。
- `ainer-server` 只做装配，不承载业务领域逻辑。
- `workspace` 与 AI runtime 各自拥有表、migration、端口和适配器；事务边界位于应用用例。
- Workspace 的资源 owner 来自 `USER_NEUTRAL_V1` 的 `sub`（HumanAccount）；所有查询绑定
  `workspace_id`，只有 ACTIVE membership 参与授权，所有权转移由锁与数据库唯一索引共同保护。
- Identity 只暴露 ACTIVE HumanAccount 安全投影，不允许 Workspace 共享查询 Identity 表。
  Identity 的 HTTP adapter 位于 `ainer-authorization-server`，`ainer-server` 不装配 Identity
  migration，Identity 应用/领域层不依赖 Web。
- 人员 Token 撤销通过 `security_epoch`/`sec_epoch` claim 在线比对，不依赖进程内异步事件或
  跨运行时 relay；网络调用不进入 Identity 数据库事务。
- 高风险安全运维使用短时双人审批：申请者和批准者是不同服务主体、分别持有最小 scope，批准事务锁定并重新验证目标状态。
- Workspace 授权审计以同事务热/冷搬迁控制热表增长，在线查询和 SIEM 稳定游标读取两表并集；归档数据仍属于 Workspace 数据库。
- persistence starter 只装配共性，不拥有任何业务表或 Repository。MyBatis-Plus 只增强
  infrastructure 的简单 CRUD 与受控分页；复杂 XML、显式资源归属 SQL 和 PostgreSQL 原生 SQL
  保持业务模块所有。

## 3. 目标模块模型

```text
ainer-boot/
├── ainer-dependencies/
├── ainer-framework/
│   ├── ainer-core/
│   ├── ainer-spring/
│   ├── ainer-starter-web/
│   ├── ainer-starter-persistence/       # 已落地的 MyBatis-Plus/MyBatis/Flyway/PostgreSQL 共性
│   ├── ainer-starter-security/          # Resource Server 通用能力
│   ├── ainer-starter-cache/             # Spring Cache + Caffeine/Redis + 分布式锁（ADR-0039）
│   ├── ainer-starter-observability/
│   └── ainer-starter-test/
├── ainer-module-identity/                # HumanAccount/ServicePrincipal/Credential foundation（去租户化）
├── ainer-module-authorization/           # ADR-0037：决策器、6 表持久化、管理/查询与 Spring 端点适配
├── ainer-module-dictionary/              # 树形字典 + 多语言 + Spring Cache（ADR-0040）
├── ainer-module-config/                  # 动态配置 + 类型安全 + 热更新 + 版本历史 + secret（ADR-0040）
├── ainer-module-notification/            # ChannelSender 端口 + SKIP LOCKED 队列 + 可选 webhook/SMTP（ADR-0040）
├── ainer-module-file/                    # 文件元数据 + 管理 API（ADR-0040）
├── ainer-module-organization/            # Incubating：组织目录（ADR-0042）
├── ainer-module-knowledge/               # Incubating：Knowledge Foundation（ADR-0044）
├── ainer-module-task/                    # Incubating：任务调度（ADR-0047）
├── ainer-module-workspace/               # 去租户化的资源授权参考切片（仅 workspace_id/成员关系）
├── ainer-module-ai-runtime/              # 模型网关、调用与费用审计
├── ainer-server/                          # 业务 Resource Server（装配 workspace/ai/auth/P3/Incubating 模块）
├── ainer-authorization-server/            # 已落地的独立认证发行物
└── ainer-app-*/                           # 满足明确拆分条件后创建的服务发行物
```

这些是演进方向，不代表应一次性创建所有空模块。模块只在拥有明确职责、测试和消费者时落地。

`ainer-module-authorization` 以 Accepted 的
[ADR-0037](decisions/0037-post-greenfield-authorization-baseline.md) 为当前基线，ADR-0030 仅保留为
被取代的历史设计。公开领域契约、Permission/Role/Binding/Scope、纯决策器与类型化集合查询保持
Spring-free；PostgreSQL adapter 拥有 6 张表、撤销时重评估和变更/决策审计。管理面由版本化
`GrantAdministrationPolicy` 精确登记可信 SERVICE 与 assignable Permission/Scope/target，默认拒绝。

Spring 适配只位于 `dev.ainer.authorization.spring`。Servlet 安全过滤链先完成 JWT 认证和通用
`authenticated()` 门禁；Spring MVC 解析出 `HandlerMethod` 后，`AinerAuthorizeInterceptor` 读取
`@AinerAuthorize` 并在 controller 执行前调用
`AinerRequestAuthorizationManager<RequestAuthorizationContext>`。拒绝统一映射为 Ainer 403，决策 ID
和内部 reason 不进入响应。不能把 MVC 注解属性假设为可供更早执行的 `AuthorizationFilter` 读取。

当前支持面与边界：

- 宿主完整 `DomainAuthorizationPolicy` 对已认领 permission 保持绝对优先；模块可通过
  `AuthorizationPolicyContributor` 为宿主未认领的 permission 同时贡献权限元数据、scope 天花板与
  domain 策略。多模块认领同一 permission 失败关闭。Workspace 用该合同贡献自身三个粗门禁，
  ACTIVE membership、OWNER/ADMIN 与对象归属仍由 Workspace 应用服务重新校验（ADR-0053）。
- 未注册目标解析器时，门禁是 `resourceType=request` 的合成资源粗闸门；产品可注册
  `AuthorizationTargetResolver` bean（第一个非空结果胜出）从请求解析类型化 `ResourceRef`，
  其类型必须与 permission 注册的 resourceType 一致，否则 fail-closed 拒绝。参考装配已注册
  Workspace 路径解析器：有 `{id}` 时 Binding 必须对上该工作区，无路径 id 时仍是粗闸门。
- CHALLENGE（高风险权限缺少近期强认证）映射为 401 并携带 RFC 9470
  `WWW-Authenticate: Bearer error="insufficient_user_authentication"` 挑战头。
- ALLOW 携带的 `PublicProjection` 是响应投影数据而非待执行义务，不阻断放行；完整决策经请求属性
  `ainer.authorization.decision` 暴露给 controller，由其显式消费投影描述符。
- 高价值写与真实资源归属仍必须在应用服务显式授权；`DecisionObligationExecutor`（待出现第二个
  真实义务类型）与方法级 AOP（当前零消费者）仍属后续切片，不得宣称已支持。

本地隔离制品已由 Maven 3.9+ 与 Maven 4 Golden Consumer 验证。`0.1.0-rc.1` 只有一次远端签名
deploy 记录，因 tag/source 不一致和证据不完整已撤回；合格的 immutable RC、远端空仓消费、外部
产品纵向切片与生产授权失效 SLA 仍按 [`docs/project-status.md`](project-status.md) 推进。该模块不会
接管 Identity、WorkspaceRole 或产品领域关系。

`ainer-module-organization` 已于 2026-08-14 交付（ADR-0042 取代 ADR-0032）：Workspace 锚点、
任职期不重叠约束、命令式管理 API 与决策时实时成员解析；同批交付 Knowledge Foundation
（ADR-0044，`ainer-module-knowledge`）与授权 SubjectSet/ActingGrant 扩展。三者均为
Incubating API，装配在 `ainer-server`，不并入
Authorization Server。组织目录详细设计见
[`Ainer 组织与员工目录详细方案`](design/organization-workforce-architecture-plan.md) 与
[ADR-0042](decisions/0042-organization-directory-greenfield-baseline.md)。

M1 有意没有提前抽取 persistence starter。M2 的 AI invocation 成为第二个 PostgreSQL 消费者
后，才把 MyBatis/Flyway/PostgreSQL/UUID 装配提炼到 `ainer-starter-persistence`。2026-07-30
又按 [ADR-0028](decisions/0028-mybatis-plus-infrastructure-baseline.md) 在同一个 starter 内引入
Spring Boot 4 专用 MyBatis-Plus，作为简单 CRUD 与分页的 infrastructure 增强。业务 Mapper、
Repository、migration 和事务仍属于各自模块；不建立原生/Plus 双 starter。

公共 framework 采用“Git 单仓、Maven 多制品”，不建立万能 `ainer-tool`，也不在当前阶段拆分
独立 Engine 仓库。公共制品分类、工具类替代规则、发布前收口和未来拆仓条件见
[ADR-0025](decisions/0025-public-artifacts-utilities-and-repository-boundary.md)。

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

不会要求每个简单 CRUD 都机械创建四层接口；只有存在相应业务规则和边界时才引入复杂结构。
MyBatis-Plus 的 `BaseMapper`、Wrapper、Page 和注解只能位于 infrastructure；application、
domain 与 API 仍使用 Ainer 自己的端口和模型。`IService`、`ServiceImpl` 与 ActiveRecord 不作为
默认业务架构。

## 5. 单体与服务化

服务化包含无法由配置完成的变化：进程边界、数据所有权、网络失败、幂等、超时、重试、契约版本和分布式一致性。因此采用两个层次：

1. **共享业务代码**：领域、应用用例和稳定契约可复用。
2. **独立应用装配**：单体和每个服务拥有明确依赖清单、配置、数据库职责和发布生命周期。

是否拆分服务以及拆分前必须完成的准备条件，以
[ADR-0024](decisions/0024-evolutionary-modular-platform-architecture.md) 为准。

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
- Ainer Admin 在 `dev` profile 下使用固定 public client、PKCE S256 与四个最小
  scope；成员 API 在 JWT 后继续读取官方 authorization active 状态，当前 access token 可自助
  撤销。该开发 client 与同源自动化验证不等于生产 browser client 生命周期控制面。
- M4.6 使用 Spring Security 7.1 WebAuthn 建立默认关闭的 Passkey 主线：UV-required、真实签名
  ceremony、条件 MFA、ACTIVE/REVOKED 生命周期、恢复码/管理员双人恢复、受控首次 enrollment、
  登录限速和 Resource Server step-up。最后一个 ACTIVE Passkey 不允许普通自助删除；真实设备
  矩阵、恢复通知、共享限流和多节点 session 尚未完成。
- 短信、微信和企业身份源通过认证编排或标准扩展授权接入，不复活 password grant。
- 业务模块从 typed `AuthenticatedPrincipal` 获取 `sub`、`token_profile`、`claim_contract_version`
  和 authorities，不读取 JWT，也不接受客户端身份请求头；缺失/未知 profile/actor 组合失败关闭。
- AI API 已强制 `ai.invoke` scope；主体必须是 `USER_NEUTRAL_V1`。
- Workspace API 强制 `workspace.read` / `workspace.write` scope，并以数据库 ACTIVE
  OWNER/ADMIN/MEMBER 关系决定具体资源权限；owner 不能由客户端指定。
- 新邀请是 PENDING，只有目标 JWT 主体本人接受后才激活；被邀主体必须已是 ACTIVE HumanAccount。Workspace 不跨模块读取 Identity 私有表。
- 通用角色变更不能触碰 OWNER；所有权转移锁定 Workspace 并由 PostgreSQL 部分唯一索引保证最多一个 ACTIVE OWNER。
- 非 ACTIVE 成员访问按 404 隐藏资源；成员角色不足返回 403。关键允许/拒绝授权决策持久化到独立事务审计。PENDING/ACTIVE membership 在账号禁用时由授权查询在线判定失效（`security_epoch` 比对），不再参与授权；OWNER 也可因全局安全禁用而失效。
- Workspace 审计查询额外要求 `workspace.audit.read` 和 ACTIVE OWNER/ADMIN，并绑定 workspace 分页；热表保留、归档、SIEM 拉取、拒绝窗口与 OWNER 缺失指标已落地。
- Identity 提供 ACTIVE HumanAccount 安全投影、安全禁用/撤销事务与 `security_epoch` 在线判定，不依赖 outbox、跨运行时 relay 或进程内异步事件。
- 内部 HTTP adapter 使用短生命周期 Client Credentials JWT、issuer/audience、`token_profile=SERVICE_V1`、`claim_contract_version=1`、scope 与可信 publisher subject；生产仍需 TLS、受控网络，后续可叠加 mTLS 或服务网格身份。
- OWNER 恢复只提升现有 ACTIVE 成员，不恢复被禁用主体。两者由不同 request/approve Client 完成并写模块所属安全操作审计。
- M4.3 在上述本地 JWT 认证后为高风险路径追加 RFC 7662 在线校验；Authorization Server 使用 Identity 当前账号状态与 `sec_epoch` 作为人员 Token revocation epoch。普通低风险 JWT 请求仍有自然到期窗口，不能宣称所有 API 强实时撤销。
- 两个发行物的 Prometheus endpoint 使用同一声明 `token_profile=SERVICE_V1`、`claim_contract_version=1`
  与 `platform.metrics.read` 契约；指标 client 与业务、SIEM、introspection 和 browser client
  控制面 client 分离。共享 PostgreSQL 只是 Authorization Server 多实例前提，尚未验证的浏览器会话、容量和故障切换不能写成已完成 HA。

## 8. AI 原生能力

M2 已按以下调用边界落地：

```text
AI HTTP / application port
  -> Model allow-list / prompt size / sensitive pattern
  -> Subject node-local rate limit
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
- 数据库日预算依赖 subject advisory lock，能在共享 PostgreSQL 范围内防止并发穿透；每分钟限流当前是 node-local，不是集群精确配额。
- prompt 与模型正文不落库，只持久化不可逆 fingerprint 和治理元数据。
- AI 主体只从验证后的 typed principal（`USER_NEUTRAL_V1` `sub`）构造；旧的 AI 身份请求头已经移除。
- OpenAI-compatible DTO 只存在于 infrastructure；Ainer 端口不暴露供应商协议。

## 9. 验证策略

- 纯 Java 规则：JUnit。
- 自动装配：`ApplicationContextRunner`。
- Web 语义：MockMvc 与随机端口集成测试。
- PostgreSQL、Redis、消息组件：Testcontainers，不用 H2 模拟生产数据库。
- 包和模块边界：ArchUnit + Maven 模块依赖。
- migration：空库执行、重复执行策略、升级路径和真实 PostgreSQL 验证。
