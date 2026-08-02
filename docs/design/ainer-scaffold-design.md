# Ainer Boot 产品定位、竞品能力矩阵与路线图

> 版本：v1.5 · 2026-07-31
> 文档类型：长期产品与架构设计 · 状态：当前权威设计
>
> 本文负责产品定位、能力目标、产品化阶段和长期边界；当前完成度、测试数字、临时缺口与下一步
> 只以 [`project-status.md`](../project-status.md) 为准。

## 1. 产品定位

Ainer Boot 是 **AI-native、PostgreSQL-native、标准身份优先、模块化、可独立消费和持续升级**
的通用企业 Java 脚手架与运行基线。它不是 AI 专用后台，也不是另一个换皮管理系统；AI 是一等
可选能力，普通企业应用不启用 AI 模块时仍必须获得完整、可靠的脚手架体验。

这里的 AI-native 表示模型、Agent、工具、RAG 与评测从一开始就受身份、权限、预算、数据政策、
审计和可观测性治理，而不是要求每个生成项目都包含聊天页面。

面向未来不等于追逐实验性技术。Ainer 的优势是没有必须保留的 JDK 8、Spring Boot 2/3、
MySQL-first、自建 Token、历史包名和旧生成模板兼容层，可以直接采用现代稳定基线；preview、
incubator、默认微服务或动态插件只有经过明确评估才进入产品。项目结构、manifest、契约和门禁
还必须同时便于人类与 Agent 理解：确定性生成器和自动化验证始终是最终约束，Agent 不能绕过它们。

Ainer Boot 面向 AI 应用、企业管理系统和商业化交付提供以下产品能力：

- 开发者可以快速创建可靠的模块化单体；
- 企业可以获得标准认证、租户治理、审计、可观测性和升级能力；
- AI 产品可以统一接入模型、Agent、RAG、工具、费用与安全策略；
- 在满足明确扩容、隔离、组织和工程准备条件后，可以把清晰边界演进为独立服务。

技术基线为 JDK 25、Spring Boot 4.1.0、Spring Framework 7、Jakarta EE 11 和 PostgreSQL。
正式架构名称和 DDD、端口适配器、模块化单体与服务化的关系见
[ADR-0024：演进式模块化平台架构](../decisions/0024-evolutionary-modular-platform-architecture.md)。

产品与仓库边界如下：

| 产品或仓库 | 职责 | 不承担 |
|---|---|---|
| **Ainer Boot** | BOM、Framework、Starter、Test Support、Build Tools、Initializer、通用平台模块与参考装配 | `xq` 商品、采购、客户等产品语义 |
| **Ainer Studio** | 管理端模板、Blocks、页面与模块生成、预览和视觉交付 | Java 平台内核、业务数据所有权 |
| **`xq-platform-next`** | 规划中的首个外部产品消费者，为两个 2.0 小程序承载产品业务 | Ainer 源码副本、Ainer 通用模块的长期 fork |
| **现有 `xq-server`** | 在迁移期继续运行，并作为业务事实与迁移来源 | Ainer Boot 的架构母体或新项目模板 |

## 2. 不做什么

- 不继承 yudao 的三层模板和循环依赖。
- 不复制 BladeX、Dante、Snowy 或其他竞品代码。
- 不承诺一个 YAML 属性完成单体到微服务的全部迁移。
- 不在没有消费者时创建大量 `api`、Feign、MQ 和 Starter 空壳。
- 不把所有 xiaoqu 代码一次性重写后再上线。
- 不把 AI 简化为一个聊天 CRUD 模块。

## 3. 竞品基准与独立取舍

### 3.1 研究方法

竞品只用于建立产品基准，不作为源码或模板来源。研究按三段隔离：

1. **观察**：只记录官方公开页面、文档、演示与仓库能够证明的用户任务和产品行为；
2. **规格**：转写为中性的 user story、Ainer 领域语言与黑盒验收，移除竞品包名、表名、菜单名
   和专有命名；
3. **实现**：依据 Ainer 规格、官方标准与独立测试实现，不向 AI 提供竞品源码做翻译、换名或重写。

矩阵不评价竞品内部质量，也不按 Star、代码量或功能数量排名。价格、版本、许可证表述和具体功能
属于带日期的研究快照；更新外部事实不会自动改变 Ainer 的长期决策。

### 3.2 多标杆角色矩阵

| 标杆 | 公开可见的标杆角色 | Ainer 吸收的产品行为 | 明确不继承 | Ainer 独立验收 |
|---|---|---|---|---|
| Yudao / RuoYi-Vue-Pro | 通用后台与业务功能广度、文档、示例和代码生成 | 建立完整能力清单、可运行样例、前后端生成与迁移教程 | 历史包名、弱模块边界、统一 200、非 Ainer 数据与测试范式 | 从空项目生成含 migration、权限、API、测试和管理页面的纵向切片 |
| BladeX | BOM/Starter/Boot/Cloud 产品分层、商业交付、垂直产品、AI 运行时与开发者 Skills | 建立制品产品线、版本矩阵、升级与 LTS；让确定性生成器与 Agent Skills 共享同一 manifest 和规则 | 商业源码、模板、专有命名；为模仿产品矩阵预建微服务和中间件空壳 | 外部项目只消费制品即可创建、升级；AI 辅助变更必须通过同一 golden consumer 门禁 |
| Snowy | 快速启动、模块/插件场景、RBAC/数据范围、代码生成和国产化场景 | 提供清晰模块契约、单表/树表/主子表生成场景与兼容矩阵 | 把静态 Maven 分包宣传成动态插件；弱类型公共 API、字符串权限 SQL、源码密钥和 MySQL-first 模板 | 模块启停不改 core；生成结果保持类型化 API、参数绑定、PostgreSQL migration 和负向授权测试 |
| Dante | OAuth2/OIDC、Authorization Server、安全组件与单体/服务独立装配 | 采用标准协议、独立身份发行物、负向安全测试与可替换适配器 | “一个配置自动完成单体到微服务”的误读；没有消费者时预建远程适配器 | OIDC/OAuth 协议测试、独立装配测试和 local/remote 契约测试 |
| RuoYi-Vue-Plus | 多租户及缓存、幂等、任务、文件、SSE/WS、脱敏、监控等实用横切积木 | 形成企业后台常用能力清单，并以小 Starter 或模块包按需交付 | 非 Ainer 的认证实现、以拦截器替代领域授权、多数据库方言优先 | 每个可选能力具备 off-state 启动测试、真实 HTTP 语义与 PostgreSQL 集成验证 |

Ainer 的综合目标是：

> 具备 Yudao/Snowy 的开发效率、BladeX 的产品工程、Dante 的标准身份安全，以及
> RuoYi-Vue-Plus 的实用横切能力；同时坚持 PostgreSQL Native-First、可验证模块边界、
> 真实 HTTP 语义和 AI 原生治理。

这意味着 Ainer 可以实现与成熟后台脚手架相当的通用管理能力，但不会把任一竞品的模块目录
整体搬入 core。功能广度通过可选模块包、参考应用和 Studio 逐步交付，平台内核只保留稳定、
跨产品复用且能够独立验证的能力。

### 3.3 目标能力矩阵

下表表达长期最低产品目标，不代表当前已经交付。当前状态只查询
[`project-status.md`](../project-status.md)。

| 能力域 | Community 最低目标 | Studio / Enterprise / AI 扩展 | 独立验收 | 阶段 |
|---|---|---|---|---|
| 制品与 Starter | 可消费 BOM、core、web、security、persistence、observability、test support | 企业适配器、受支持版本矩阵与 LTS | Maven 3.9+/4 外部消费者只从制品仓库构建和启动 | P1 |
| Project Initializer | manifest 创建独立项目，安全 preview/diff，不复制 Ainer 源码 | 组织模板、行业模板与受控策略包 | 同版本、同 manifest 生成无差异；默认不覆盖、不改菜单、不写数据库 | P2 |
| CRUD 与模块生成 | 单表、树表、主子表的 migration、API、应用层、持久化、测试和 OpenAPI | Studio 管理页面、批量重构与升级辅助 | 生成结果通过 PostgreSQL、tenant 负向授权和 golden consumer 门禁 | P2–P4 |
| Identity 与管理面 | 用户、tenant、组织/成员、角色、资源权限、数据范围、菜单、字典与配置 | SSO、SCIM、审批、职责分离和高级策略 | 浏览器 E2E、跨 tenant 负向矩阵、操作审计与协议测试 | P3–P5 |
| 通用企业能力 | 文件、通知、任务、缓存、幂等、outbox、审计、SSE/WS 的按需模块 | 商业连接器、合规留存、运营控制台 | 每个模块可关闭，且不修改 core 即可安装或移除 | P3–P5 |
| API 与管理端交付 | 真实 HTTP、统一错误、OpenAPI、TypeScript SDK 和参考 Admin | Studio Blocks、设计系统与版本化 SDK | SDK 被真实前端编译，协议变更有兼容报告 | P2–P5 |
| AI 原生能力 | Model Gateway、身份、预算、策略、用量和费用审计 | Agent、Tool、RAG、Evaluation、Guardrails 与运营控制台 | 至少一个真实纵向场景覆盖权限、费用、数据治理、人工反馈和回归评测 | P4 |
| 运维与供应链 | Compose、健康检查、指标、结构化日志、许可证、SBOM 与可重复发布 | HA、灾备、签名、provenance、LTS 与补丁服务 | 发布物可追溯；正式 PostgreSQL 门禁 0 skipped | P0–P5 |
| 扩展与升级 | 稳定扩展点、模块兼容清单、BOM 升级 | 商业模块市场、entitlement、升级助手 | 连续两个 minor 的外部消费者升级验证；公共能力须有至少两个消费者 | P5 |

### 3.4 公开来源索引

本矩阵最近核对于 **2026-07-31**，竞品行为判断只采用以下官方公开来源：

- Yudao / RuoYi-Vue-Pro：
  [官方仓库](https://github.com/YunaiV/ruoyi-vue-pro)、
  [官方发布记录](https://github.com/YunaiV/ruoyi-vue-pro/releases)；
- BladeX：
  [产品矩阵](https://bladex.cn/products)、
  [BladeX AI](https://bladex.cn/products/bladex-ai)、
  [SpringBlade](https://github.com/chillzhuang/SpringBlade)、
  [BladeTool](https://github.com/chillzhuang/blade-tool)、
  [公开 Agent Skills](https://github.com/chillzhuang/SpringBlade/tree/master/.claude/skills)；
- Snowy：
  [官方仓库 v3.6.5](https://github.com/xiaonuobase/Snowy/tree/v3.6.5)、
  [官方文档](https://doc.xiaonuo.vip/snowy_vue/)；
- Dante Cloud：
  [官方仓库](https://gitee.com/dromara/dante-cloud)；
- RuoYi-Vue-Plus：
  [官方仓库](https://github.com/dromara/RuoYi-Vue-Plus)。

任何源码、模板或商业材料的可用性仍受
[ADR-0001](../decisions/0001-independent-architecture-baseline.md) 和正式许可证审查约束；
公开可见不等于允许复制。

## 4. 已验证的设计样本

本节只用已验证样本解释后续产品约束，不承担完整状态清单，也不随每次测试数字变化更新。当前
验证结果和新增缺口以 [`project-status.md`](../project-status.md) 为准。

### 4.1 构建

- 根 Reactor、独立 BOM、JDK/Maven Enforcer。
- Boot 4.1.0 依赖管理和可执行 JAR 插件。
- `./mvnw clean verify` 覆盖全部模块。

### 4.2 核心依赖方向

```text
ainer-core <- ainer-spring <- ainer-starter-web <- business module <- ainer-server
ainer-core <- ainer-starter-persistence <- business module
```

`ainer-core` 当前只有：

- `ErrorCode`
- `StandardErrorCode`
- `BusinessException`
- `ErrorCodeRegistry`
- `ApiResponse<T>`

它没有 Spring 依赖，因此可被 Servlet、消息消费者、批处理和未来原生镜像共同使用。

### 4.3 运行模式

`@ConditionalOnRuntimeMode` 提供 `MONOLITH` 和 `SERVICE` 两种适配器选择。其边界是：

- 选择本地实现或远程实现；
- 不创建或拆分进程；
- 不改变数据库所有权；
- 不把本地事务变成分布式事务；
- 不自动启用网关、注册中心或消息中间件。

### 4.4 Web 基线

- 每个请求建立或验证 `X-Request-Id`，并写入 MDC。
- 业务异常映射到自身 HTTP 状态。
- 参数、权限、资源和未知错误保持 4xx/5xx 语义。
- 未知错误不向客户端暴露异常信息。
- 错误码为稳定字符串，不采用手工数字段位或 hash。

### 4.5 Workspace 可信资源切片

- `workspace` 创建、读取、分页、重命名、PENDING 邀请与本人接受 API。
- PostgreSQL UUID、约束、索引和 Flyway migration。
- 应用层事务确保 workspace 与 OWNER 成员原子创建。
- scope 与 ACTIVE OWNER/ADMIN/MEMBER 共同授权，全部资源 SQL 显式绑定 tenant。
- 角色变更、移除与专用所有权转移；部分唯一索引保证最多一个 ACTIVE OWNER。
- 关键允许/拒绝决策写入独立事务审计；审计查询要求 `workspace.audit.read` 与 ACTIVE OWNER/ADMIN。
- 乐观锁版本控制，重复成员与并发变化映射为稳定 409 错误。
- MyBatis-Plus/MyBatis 基础设施适配器和显式 UUID TypeHandler。
- Testcontainers 集成测试与 ArchUnit 包边界门禁。

### 4.6 AI Model Gateway 切片

- Ainer 自有 `ModelProvider`、命令、结果与 stream observer 契约。
- JDK HttpClient OpenAI-compatible adapter，支持非流式和 SSE 最终 usage。
- 模型白名单、提示大小、基础敏感凭据、node-local 速率和 PostgreSQL 日预算策略。
- 成功、失败与拒绝统一审计 Token、费用、耗时、状态和策略，不保存 prompt/输出正文。
- Provider 合约测试、ArchUnit 和 PostgreSQL Testcontainers 集成测试。
- PostgreSQL 18.4 + 本地兼容 provider 的真实进程验证。

### 4.7 Identity 与 Authorization 切片

- 独立 OAuth 2.1/OIDC Authorization Server、外部 RSA key 与官方 JDBC 协议存储。
- 用户、租户、默认成员关系、delegating password hash 和可信 JWT tenant/subject 投影。
- ACTIVE tenant/user/membership 的 Directory 安全投影，不暴露密码哈希与 OAuth 协议数据。
- 账号禁用、非 OWNER membership 撤销，以及和状态变化同事务的 access-event outbox。
- 跨运行时 Directory adapter、outbox relay、Workspace 幂等消费者，以及高风险路径选择性在线
  Token 校验与 revocation epoch。
- USER tenant 成员管理、首租户严格 bootstrap，以及 tenantless SERVICE 平台预配、激活、显式取消
  与 tenant/user 安全分页；
  申请只建立不可授权预留，激活使用只存摘要的短时 grant、加密通知 outbox 或已有用户本人接受，
  成功后才原子创建核心 Identity。

## 5. 模块化单体

Ainer 首个交付形态是模块化单体，因为它能以最低运维成本验证领域边界和产品市场匹配。

每个业务模块拥有：

- 自己的领域模型与应用用例；
- 自己的数据库表和 migration；
- 对外暴露的最小契约；
- 模块内测试和集成测试；
- 明确的负责人和变更记录。

禁止：

- 跨模块直接访问 Mapper 或数据库表；
- 跨模块注入 Service 实现；
- 通过 `@Lazy` 修复依赖环；
- 把找不到归属的功能塞进 `system` 或 `common`。

## 6. 服务化演进

服务拆分必须先满足至少一项触发条件：

- 需要独立扩缩容；
- 安全或合规要求独立隔离；
- 团队需要独立发布并能承担运行责任；
- 单体中的资源竞争无法通过进程内治理解决。

同时必须满足全部工程准备条件：

- 数据所有权已经清晰，不依赖跨模块私表查询；
- 已有稳定 port、API 或事件合同及契约测试；
- 已定义超时、重试、幂等、失败和版本兼容语义；
- 已具备 migration、可观测性、发布、回滚和运行责任。

拆分步骤：

1. 固化应用契约和契约测试。
2. 明确服务拥有的数据，消除跨模块直接查表。
3. 将本地端口实现替换为远程适配器。
4. 增加超时、重试、熔断、幂等和可观测性。
5. 对跨边界事务使用 outbox、saga 或补偿，不引入隐式分布式事务。
6. 建立独立可执行应用和发布流水线。

## 7. 持久化设计

M1 已使用 PostgreSQL Testcontainers 建立自动化集成测试，并用本地 PostgreSQL 18 完成真实启动与故障注入验证；不以 H2 兼容模式替代生产数据库。

原则：

- 领域模型不携带 Web 注解。
- 持久化 Entity 不作为 API 响应。
- MyBatis-Plus/MyBatis 是基础设施适配器，不进入应用用例接口或 HTTP API。
- migration 是数据库变更的唯一来源。
- 查询条件全部参数绑定，权限过滤不能拼接 SQL。
- 事务边界位于应用用例。

第一个样本选择 `workspace`，因为它足以验证唯一约束、成员关系、分页、冲突错误和审计字段，又不会提前引入复杂认证。该切片已经完成。M2 的 AI invocation 成为第二个消费者后，已把稳定重复的 MyBatis/Flyway/PostgreSQL/UUID 装配抽取为 `ainer-starter-persistence`；业务 migration、Mapper、Repository 与事务不进入 starter。

当前按
[ADR-0028](../decisions/0028-mybatis-plus-infrastructure-baseline.md)
在统一 persistence starter 中使用 Spring Boot 4 专用 MyBatis-Plus：

- `BaseMapper`、Wrapper 与 Page 只服务 infrastructure 的简单 CRUD/分页；
- 复杂 XML、锁、CTE、`RETURNING`、outbox、审计和稳定游标继续使用显式 SQL；
- application、domain 和 API 不暴露 MyBatis-Plus 类型，不默认使用
  `IService` / `ServiceImpl` / ActiveRecord；
- PostgreSQL `DEFAULT uuidv7()` 通过全局 `IdType.AUTO` 生成并回填，禁止
  `ASSIGN_ID` / `ASSIGN_UUID`；
- tenant interceptor 当前不启用，所有 tenant 查询仍显式绑定可信 tenant；
- 不默认启用逻辑删除或自动填充，分页最大单页 100；
- `mybatis-plus-generator` 本轮未引入，代码生成属于 P2 Project Initializer 的独立设计。

## 8. 认证与授权

### 8.1 标准路径

- 浏览器、管理后台、移动端：Authorization Code + PKCE。
- 服务身份：Client Credentials，后续结合 mTLS/private_key_jwt。
- 设备：Device Code。
- 系统间委托：按需评估 Token Exchange。
- OIDC：Discovery、UserInfo、标准 Claims。

Spring Authorization Server 当前标准能力不包含 password grant。短信、微信小程序、企微等登录需要作为认证前置编排或扩展授权单独建模，不能在文档中把 password grant 写成标准能力。

### 8.2 授权

- URL 权限只是入口；领域用例必须执行资源级授权。
- 数据权限以主体、动作、资源、上下文建模，不绑定 servletPath 字符串。
- 复杂范围查询使用参数化策略或预计算授权关系；禁止拼接 `IN SQL`。
- 缺失身份或策略时默认拒绝。

## 9. AI runtime

### 9.1 模型网关

M2 已经完成一个最小闭环：OpenAI-compatible provider、非流式/SSE、模型白名单、提示长度与高风险凭据模式、node-local 限流、PostgreSQL 日预算、Token/费用/耗时/状态/策略审计，以及供应商错误脱敏。

模型网关长期统一负责：

- Provider/模型路由与降级；
- 租户配额、并发和预算；
- Token、费用、延迟和错误审计；
- Prompt 与响应数据策略；
- 重试、超时、流式响应和幂等；
- Provider 密钥隔离。

M2 的明确限制：

- provider key 仍由外部 secret 注入，尚未实现 KMS credential reference；
- 每分钟限流是 node-local，日预算才是共享 PostgreSQL 范围内的权威控制；
- prompt 与输出正文默认不落库，敏感模式也不是完整 DLP；
- tenant/subject 已由验证后的 JWT 提供，外部身份请求头不再接受；
- 路由降级、幂等、输出策略、指标和评测仍属于后续里程碑。

### 9.2 Agent runtime

- Tool Registry：schema、权限、超时、幂等、审批策略。
- RAG：索引、检索、引用、租户与资源级 ACL。
- Memory：作用域、保留期限、脱敏和删除权。
- Evaluation：离线数据集、在线采样、回归阈值。
- Guardrails：输入输出策略、Prompt Injection 与数据泄露防护。

Run / Artifact 与 Knowledge 的数据模型不在本总纲中展开，分别见
[`ai-runtime-data-model.md`](ai-runtime-data-model.md) 和
[`knowledge-data-model.md`](knowledge-data-model.md)。两者当前是 Proposed，并明确排除通用 Step、
万能 Feedback、无版本知识段和无真实消费者的物理表。

### 9.3 边界

业务模块依赖 Ainer 定义的端口，不直接依赖 OpenAI、Anthropic、DashScope 等厂商 SDK。Provider adapter 可以独立发布和收费。

## 10. 商业产品结构

```text
Ainer Community
  ├── core / spring / web / persistence
  ├── 示例模块与开发文档
  └── 基础代码生成

Ainer Enterprise
  ├── SSO、组织与高级授权
  ├── 多租户、审计、合规与运维控制台
  ├── 升级工具、LTS、补丁和技术支持
  └── 私有化交付

Ainer AI Platform
  ├── Model Gateway / Agent / RAG / Evaluation
  ├── 费用、配额、安全和运营控制台
  └── 企业模型与知识资产治理

Industry Products
  └── 客服、营销、知识库、CRM、零代码等垂直产品
```

收费核心应是持续升级、企业治理、AI 运营能力、行业模块和交付服务，而不是故意削弱社区版。

## 11. 质量门禁

| 变更 | 最低验证 |
|---|---|
| 纯 Java 核心 | 单元测试、API 兼容评估 |
| AutoConfiguration | ApplicationContextRunner 开/关/默认测试 |
| HTTP | MockMvc + 随机端口集成测试 |
| PostgreSQL | Testcontainers + migration 重放 |
| 模块边界 | Maven 依赖 + ArchUnit |
| OAuth/OIDC | 协议集成测试、密钥轮换和负向安全测试 |
| AI provider | 合约测试、录制响应、费用和超时测试 |

## 12. 产品化路线与退出门禁

`P0–P5` 专指 Ainer Boot 的脚手架产品化阶段。既有 `Foundation/M1–M6` 是 Ainer 实现历史，
`xiaoqu` 文档中的 `M0–M5` 是产品迁移波次，三者不得混用。历史验证记录、当前所处阶段和动态
缺口只在 [`project-status.md`](../project-status.md) 维护。

| 阶段 | 目标 | 必要退出门禁 |
|---|---|---|
| **P0 Baseline Integrity** | 让代码、文档、测试、数据与许可证事实可信 | PostgreSQL 18 正式门禁 0 skipped；未验收能力保持 Proposed 或默认关闭；秘密扫描与依赖许可证无未处置问题；权威文档与 ADR 无冲突 |
| **P1 Scaffold Ready** | 把平台内核变成可发布、可独立消费的制品 | 非 SNAPSHOT BOM/Starter 发布；Maven 3.9+ 与 Maven 4 独立消费者通过；最小应用关闭全部可选模块仍能启动；source/Javadoc、LICENSE/NOTICE、SBOM、checksum/signature/provenance 和兼容政策齐全 |
| **P2 Create & Generate** | 安全、确定性地创建项目和纵向 CRUD | manifest v1、preview/diff、默认不覆盖/不改菜单/不写数据库；同版本同 manifest 生成无差异；TTFR 与 TTCRUD 目标通过；生成物通过 PostgreSQL 与 golden consumer 门禁 |
| **P3 Minimum Admin & First Consumer** | 用可用管理面和真实产品证明脚手架边界 | Identity、组织/成员、RBAC/数据范围、菜单/字典/配置、文件与审计形成关键 E2E；Initializer 生成 `xq-platform-next`；不含 Ainer 源码副本或 SNAPSHOT；两个小程序 SDK 可编译；至少一个真实纵向切片和一次 Ainer minor 升级通过 |
| **P4 AI-Native Enterprise Scaffold** | 达到通用企业后台能力下限并形成 AI 差异化 | 通知、任务、观测等常用模块闭环；Agent/Tool/RAG/Evaluation 具备身份、权限、预算、数据治理、人工反馈和回归门禁；模块开关组合可构建与启动；生成器覆盖树表和主子表 |
| **P5 Ecosystem & Commercial Delivery** | 建立生态、升级、LTS 和商业交付闭环 | 至少两个独立消费者；模块安装/移除不改 core；连续两个 minor 完成升级验证；兼容清单、升级助手、entitlement、LTS/补丁与行业模块交付流程落地 |

P3 不等待 P4 或 P5。否则 Ainer 会继续成为只被自身使用的平台，而不是经过外部产品验证的
脚手架。服务化也不是固定阶段：只有满足
[ADR-0024](../decisions/0024-evolutionary-modular-platform-architecture.md) 的触发条件与全部工程
准备条件后，才增加独立服务发行物。

### 12.1 量化产品验收目标

以下是阶段退出时必须验证的目标，不是对当前版本的交付声明：

| 指标 | 目标与口径 |
|---|---|
| TTFR（Time to First Run） | 在官方参考环境、前置工具已安装且制品仓库可达时，从空目录到 `/actuator/health=UP` 不超过 10 分钟 |
| TTCRUD | 从 manifest 到含 PostgreSQL migration、tenant 授权、API、测试、OpenAPI 与管理页面的可运行纵向 CRUD 不超过 30 分钟 |
| 独立消费 | 外部消费者中的 Ainer 源码副本为 0，进入 P3 后 SNAPSHOT Ainer 依赖为 0 |
| 生成确定性 | 同一 Ainer 版本、同一 manifest 和同一规范化环境重复生成，文件差异为 0 |
| 生成安全 | 默认覆盖既有源码、修改运行中菜单、连接或写入数据库的行为均为 0 |
| 数据库可信度 | 正式 PostgreSQL 集成门禁 skipped 为 0；H2/MySQL compatibility test 为 0 |
| 可选性 | 每个声明为可选的 Starter 或模块均有 off-state 构建与启动测试 |
| 兼容性 | 每次发布分别评估 Java API、HTTP、配置、JWT/scope、schema、事件、Starter 和生成 manifest |
| 供应链 | 发布制品 100% 具备 checksum、SBOM、来源和许可证结果；未知许可证与未豁免 Critical/High 漏洞为 0 |
| 升级性 | 外部消费者只通过 BOM、公开扩展点与明确 migration 升级，不复制框架补丁 |
| 公共能力晋升 | 产品能力至少由两个独立消费者证明语义稳定后，才进入 Ainer 公共契约 |

## 13. 首个外部消费者合同

### 13.1 名称与产品事实

首个外部消费者确定为 `xq-platform-next`，为以下两个 2.0 小程序提供共同后台：

- `xq-shop` 2.0 的开发期项目为 `xq-shop-next`；
- `xq-assistant` 2.0 的正式项目名为 **`xq-zhiwu`**；
- `xq-zhiwu` 是面向翡翠同行、珠宝公司、商家、采购与供给人员的公开行业信息与协作网络；
- 录货是具备 capability 的全局能力，不是产品定位、底部 Tab 或内部员工工作台；
- `xq-shop-next` 是面向消费者的独立发现、决策与交易产品，不是旧商城页面重写或
  `xq-zhiwu` 的商家后台；
- 两端共享中立的 Object/Version/Media/Evidence 事实，但 Industry Listing、Consumer Offer、
  价格、发布、服务、交易和售后语义独立。

名称映射和产品范围属于迁移合同，后续设计、接口、测试和发布说明不得把
`xq-assistant 2.0` 另行映射为其他项目，也不得因历史名称或旧代码将其误建模为 AI 对话产品、
员工供应链工具或旧页面重写。

### 13.2 应用、租户与身份边界

`xq-shop` 与 `xq-zhiwu` 是两个 `platform_app`/渠道入口，不是两个 tenant：

- `platform_app` 至少拥有 `appCode`、渠道类型、微信 AppId、密钥引用、回调、状态、品牌与功能开关；
- 社交身份绑定唯一键至少包含 `platformAppId + provider + openId`；
- tenant 只表达组织与数据隔离，不用于区分小程序；
- Ainer Identity 的固定 `OWNER/ADMIN/MEMBER` 只表达 tenant 治理，不承载录货员、采购或商家
  发布人等业务角色；
- Ainer 在首个消费者前提供通用 Permission/Role/Binding/Decision 扩展，XQ 自己拥有 Public
  Actor、Acting Identity、operator relation、业务 grant 和 Merchant/Business Location scope；
- `X-Acting-Identity-Id` 只是服务端有效关系中的身份选择器，不是授权凭据；
- 公开行业图谱不要求访问者成为 Workspace/tenant member，业务写仍需 permission、资源 scope
  和领域关系校验；
- `xq-shop` 顾客使用独立 customer/member 主体，不能被强制建成 OWNER/ADMIN/MEMBER 租户成员；
- Authorization 组合层必须支持没有 Workspace membership 的 C 端主体，同时继续拒绝客户端自报
  tenant、subject 或 owner。

### 13.3 代码与制品边界

Ainer 负责稳定、跨产品可复用的能力：

- BOM、core、web、security、persistence、observability、test starter；
- 可选 Identity、Workspace 与 AI Runtime；
- platform-app 注册、外部身份 SPI、对象存储 SPI；
- 幂等、outbox、HTTP/OpenAPI/SDK 和项目初始化原语。

`xq-platform-next` 负责产品语义：

- Industry Public Actor、Acting Identity、relation、行业 capability 与 data scope；
- customer/member、匿名身份接续与内部平台人员隔离；
- Object Asset、Object Version、Industry Listing、Consumer Offer 与各自独立发布状态；
- 商家、经营点、录货、搜索、求货、QuoteEvent、Agreement、Deal、偏好、服务和后续交易；
- 微信、旧系统和其他产品 adapter；
- 具体 AI 场景、prompt、source adapter 和人工反馈规则。

现有 `xq-server` 只作为迁移期运行系统、业务事实来源和 `xq-legacy` adapter 的输入，不作为新
后台的模板或依赖。历史上以 `mysql` 等数据库厂商命名的包、目录或抽象不得进入
`xq-platform-next`；新代码按业务能力和适配器职责命名，PostgreSQL 方言只存在于所属模块的
infrastructure/migration 边界。

产品模块不得反向进入 Ainer。只有至少两个独立消费者证明语义稳定，且不存在产品规则泄漏时，
才评估将能力上提为 Ainer 公共契约。

建议起始结构保持模块化单体：

```text
xq-platform-next/
├── xq-dependencies/
├── xq-apps/
│   ├── xq-authorization-server/
│   └── xq-server/
├── xq-interfaces/
│   ├── xq-shop-api/
│   ├── xq-zhiwu-api/
│   └── xq-admin-api/
├── xq-modules/
│   ├── xq-module-platform-access/
│   ├── xq-module-industry/
│   │   ├── industry-access/
│   │   ├── object/
│   │   ├── merchant/
│   │   ├── industry-listing/
│   │   └── collection/
│   └── xq-module-consumer/
│       ├── customer-relation/
│       ├── consumer-offer/
│       └── service-transaction/
├── xq-adapters/
│   ├── xq-wechat/
│   └── xq-legacy/
├── contracts/openapi/
└── ops/
```

第一阶段不按每个 feature 拆 Maven 模块，不引入网关、注册中心或消息中间件。关键能力先定义
port 和 local adapter；只有满足 ADR-0024 的拆分条件后再增加 remote adapter 或独立发行物。

### 13.4 创建门禁

创建 `xq-platform-next` 正式业务基线前，必须同时满足：

1. 初始化器能在临时目录生成独立消费者，生成结果不包含 Ainer 源码副本；
2. 消费者只通过 Maven 仓库解析 Ainer BOM、Starter 和可选模块；
3. `./mvnw clean verify` 使用 PostgreSQL 18，数据库测试 `0 skipped`；
4. 空库 migration、启动、JWT 保护接口、真实 HTTP 错误和 request ID 验证通过；
5. Workspace、AI 等可选模块关闭时，最小应用仍可构建和启动；
6. OpenAPI 生成的 TypeScript 客户端能被两个 weapp-vite 项目编译；
7. 开发 Compose、秘密注入、许可证、SBOM、版本与升级政策形成可重复验证；
8. 通用 Permission、Role、Role Permission、Subject Binding、结构化 Scope 与 Authorization
   Decision 最小闭环已被外部 Golden Consumer 验证；
9. Ainer Admin 能通过 OpenAPI/SDK 管理最小 Role/Binding 并展示 Effective Access，隐藏菜单或
   修改前端状态不能绕过服务端；
10. 撤销 binding 后，仍有效 Token 在批准的授权失效 SLA 内不能继续执行受保护业务写。

满足这些门禁后立即创建首个消费者，并通过真实纵向切片继续校验脚手架；不得把“所有企业功能
完成”作为创建产品仓库的前置条件。
