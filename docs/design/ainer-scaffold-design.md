# Ainer 平台架构设计

> 版本：M4 v1.1 · 2026-07-23
> 状态：当前权威设计；代码优先，文档不得描述尚未验证为“已完成”的能力

## 1. 产品定位

Ainer 不是另一个换皮后台管理系统。它是一套面向 AI 应用、企业管理系统和商业化交付的产品工程基线：

- 开发者可以快速创建可靠的模块化单体；
- 企业可以获得标准认证、租户治理、审计、可观测性和升级能力；
- AI 产品可以统一接入模型、Agent、RAG、工具、费用与安全策略；
- 在业务和组织规模证明有必要后，可以把明确边界演进为独立服务。

技术基线为 JDK 25、Spring Boot 4.1.0、Spring Framework 7、Jakarta EE 11 和 PostgreSQL。

## 2. 不做什么

- 不继承 yudao 的三层模板和循环依赖。
- 不复制 BladeX、Dante、Snowy 或其他竞品代码。
- 不承诺一个 YAML 属性完成单体到微服务的全部迁移。
- 不在没有消费者时创建大量 `api`、Feign、MQ 和 Starter 空壳。
- 不把所有 xiaoqu 代码一次性重写后再上线。
- 不把 AI 简化为一个聊天 CRUD 模块。

## 3. 竞品研究后的取舍

| 来源 | 研究价值 | Ainer 的选择 |
|---|---|---|
| BladeX | 多发行物、Starter 产品线、商业交付 | 学产品工程，不复制受商业许可限制的源码和模板 |
| RuoYi-Vue-Pro | 业务功能覆盖、Boot 4 迁移样本、测试语料 | 作为需求清单和行为参考，不作为架构母体 |
| Dante | OAuth2/OIDC、Authorization Server、安全组件与装配 | 学标准协议和装配思想；单体/微服务采用独立发行物 |
| Snowy | 开源获客、代码生成场景、收费产品矩阵 | 学商业漏斗和场景设计；不采用静态事件总线、弱类型 API、源码密钥或 SQL 拼接 |

所有实现遵守 clean-room：先形成 Ainer 自己的需求和验收测试，再基于官方标准独立实现。

## 4. 已落地基础

### 4.1 构建

- 根 Reactor、独立 BOM、JDK/Maven Enforcer。
- Boot 4.1.0 依赖管理和可执行 JAR 插件。
- `mvn test` 覆盖全部模块。

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
- MyBatis 4 基础设施适配器和显式 UUID TypeHandler。
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
- 跨运行时 Directory adapter、outbox relay、Workspace 消费者与强实时 JWT 撤销仍是后续能力。

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

只有满足以下证据之一才拆服务：

- 需要独立扩缩容；
- 安全或合规要求独立隔离；
- 团队需要独立发布并能承担运行责任；
- 数据所有权已经清晰；
- 单体中的资源竞争无法通过进程内治理解决。

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
- MyBatis 是基础设施适配器，不进入应用用例接口。
- migration 是数据库变更的唯一来源。
- 查询条件全部参数绑定，权限过滤不能拼接 SQL。
- 事务边界位于应用用例。

第一个样本选择 `workspace`，因为它足以验证唯一约束、成员关系、分页、冲突错误和审计字段，又不会提前引入复杂认证。该切片已经完成。M2 的 AI invocation 成为第二个消费者后，已把稳定重复的 MyBatis/Flyway/PostgreSQL/UUID 装配抽取为 `ainer-starter-persistence`；业务 migration、Mapper、Repository 与事务不进入 starter。

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

## 12. 实施路线

1. **Foundation v0.1：已完成**——core、spring、web、server、真实 HTTP 错误。
2. **M1 Persistence slice：已完成**——workspace + PostgreSQL + MyBatis + migration + Testcontainers + ArchUnit。
3. **M2 AI Gateway：已完成**——OpenAI-compatible Provider、流式/非流式、策略、预算与用量/费用审计。
4. **M3 Identity & Authorization：foundation 已完成**——可信 JWT、Identity、独立 Authorization Server、Directory 与撤销 outbox。
5. **M4 Access controls：foundation 已完成**——Workspace tenant/成员/所有权授权、审计写入与查询。
6. **Enterprise controls**——跨运行时撤销传播、租户治理、审计归档、配额与运维。
7. **Productization**——代码生成、升级工具、版本矩阵、社区/商业发行。
8. **Evidence-based services**——根据真实瓶颈创建独立服务发行物。
