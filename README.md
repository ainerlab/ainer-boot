# Ainer Boot

> 正式品牌：Ainer · 脚手架产品：Ainer Boot · JDK 25 + Spring Boot 4.1.1
> · Maven 4.0.0-rc-6 preview

Ainer Boot（**AI-Native Extensible Runtime**，中文读音“艾纳”）是 AI-native、但不局限于 AI
的通用企业 Java 脚手架与运行基线。它从模块化单体开始，通过明确的契约、适配器和独立发行物
演进为服务化系统；它不继承 Yudao、BladeX、Dante 或 Snowy 的代码与框架范式。产品边界、
多标杆能力矩阵和 P0–P5 路线见
[Ainer Boot 产品定位、竞品能力矩阵与路线图](docs/design/ainer-scaffold-design.md)。

品牌与活动技术标识现已统一为 Ainer，脚手架产品名为 **Ainer Boot**。`v1.0.0` 已发布
（工程合同定稿，见 [`docs/ainer-boot-1.0-product.md`](docs/ainer-boot-1.0-product.md)）。
本仓库按 [MIT License](LICENSE) 开源（[ADR-0051](docs/decisions/0051-mit-license-and-public-repository.md)）；
MIT **不授予** Ainer 商标权。产品命名、域名状态与标识规则见
[ADR-0004](docs/decisions/0004-ainer-brand-and-naming-baseline.md)。第三方依赖见
[`NOTICE`](NOTICE) 与 [`docs/dependencies.md`](docs/dependencies.md)。

## 当前可用能力

| 模块 | 状态 | 职责 |
|---|---|---|
| `ainer-dependencies` | ✅ | 独立 BOM，统一管理 Boot 与 Ainer 模块版本 |
| `ainer-core` | ✅ | 零 Spring 依赖的错误契约、响应模型与注册表 |
| `ainer-spring` | ✅ | Spring 基础设施、运行模式属性和条件装配 |
| `ainer-starter-web` | ✅ | 真实 HTTP 状态、统一响应、请求 ID、全局异常处理 |
| `ainer-starter-persistence` | ✅ | MyBatis-Plus/MyBatis、Flyway、PostgreSQL UUID 与受控分页的公共装配 |
| `ainer-security` | ✅ | 与框架无关的可信参与者与 authority 契约 |
| `ainer-starter-security` | ✅ | Resource Server、人员/服务 JWT 投影、选择性 RFC 7662 在线校验、tenantless 指标授权与统一 401/403/503 |
| `ainer-module-identity` | ✅ foundation | HumanAccount、ServicePrincipal、LoginIdentity、Credential 与 `security_epoch` 身份基线 |
| `ainer-module-workspace` | ✅ | Workspace 资源、ACTIVE membership、OWNER 专用转移与授权审计热/归档契约 |
| `ainer-module-ai-runtime` | ✅ | OpenAI-compatible 网关、SSE、策略、预算与用量/费用审计 |
| `ainer-module-authorization` | ✅ 工程基线 | ADR-0037 Workspace 语义的 RBAC+ReBAC+ABAC 决策器、PostgreSQL Binding/审计、管理 API、类型化集合查询，以及真实 JWT 下由 MVC 拦截器执行的 `@AinerAuthorize` 端点粗粒度门禁；资源 target resolver、obligation executor 与方法级 AOP 仍属后续 |
| `ainer-starter-cache` | ✅ | Spring Cache 抽象（Caffeine 默认 / Redis 可选）与分布式锁（ADR-0039） |
| `ainer-starter-observability` | ✅ 最小集 | ObservationRegistry、requestId/traceId MDC；OTLP 默认关闭（ADR-0029 T1#6） |
| `ainer-module-dictionary` / `ainer-module-config` / `ainer-module-notification` / `ainer-module-file` | ✅ | P3 企业基座四件套：管理 API、稳定错误码、scope、同事务审计（通知默认为日志发送器；WEBHOOK/EMAIL 可选用脚手架 HTTP/SMTP 投递，SMS/Push 仍经 `ChannelSender` SPI 由产品实现） |
| `ainer-module-organization` | ✅ Incubating | 组织目录：Unit/任职/分配/岗位 + `workforce.position#assignee` 成员解析（撤岗即失权，ADR-0042） |
| `ainer-module-knowledge` | ✅ Incubating | Knowledge Foundation：不可变 Revision + SUPERSEDES 血缘 + 人工发布门禁（ADR-0044） |
| `ainer-module-task` | ✅ Incubating | 任务调度：类型注册、延迟/周期执行、SKIP LOCKED 领取、指数退避、超时看门狗与管理 API（ADR-0047） |
| `ainer-initializer` / `ainer-initializer-cli` | ✅ Stable | Manifest v1 兼容生成；v2 `simple-service + workspace` 安全纵向切片（随 `v1.3.0` 发布）；开发分支另提供已有单模块 Maven 项目的只读 `plan-add` 与幂等 `add`（显式 Flyway 版本、有限 POM 合并，ADR-0053） |
| `ainer-server` | ✅ | JWT Resource Server、受保护 Prometheus exporter、Workspace、AI Runtime、Authorization、P3 与 Incubating 模块装配 |
| `ainer-authorization-server` | ✅ foundation | OAuth 2.1/OIDC、PKCE、条件 Passkey、typed token profile、RFC 7662/7009 与受审计 JDBC 协议仓库 |

当前版本已经在本机 Colima/Testcontainers 的真实 PostgreSQL 18.3 上通过完整 Reactor 测试，Identity、Workspace、AI runtime 与 Authorization Server 数据库用例均实际执行；M1/M2 还曾使用真实 PostgreSQL 18.4 与本地 OpenAI-compatible 合约服务完成验证。本轮另在本机 PostgreSQL 18.4 从空库启动 Authorization Server，完成专用/普通 introspection client 隔离、active、RFC 7009 撤销与 revocation epoch 查询计划验证。它是可运行的工程基线，不再是文档草案；生产高可用、容量与告警仍需单独完成。

[`v1.3.0`](https://github.com/ainerlab/ainer-boot/releases/tag/v1.3.0) 是当前稳定版本：
它在 [`v1.2.0`](https://github.com/ainerlab/ainer-boot/releases/tag/v1.2.0) 基础上发布 Initializer v2
`simple-service + workspace` 安全纵向切片。Manifest v1 与既有读取、生成合同保持不变，Ainer
framework 无数据库 migration 变化；`1.0.x` 作为 LTS 补丁线继续受支持（ADR-0045/0046）。
`v1.1.0` tag **withdrawn / non-qualifying**（无 Release、无 Packages），禁止消费。
[`v1.0.0`](https://github.com/ainerlab/ainer-boot/releases/tag/v1.0.0) 保持不可变，作为
`1.2.0` 的升级起点与 `1.0.x` LTS 线的基线。
双参考消费者并存：`xq-platform-next`（`rc.2 → … → 1.2.0` 完整升级链含回滚）与
`python-learning-service`（`0.1.0 → 1.2.0` 冷仓接入）。

完整产品说明（能力域、合同、质量模型与快速开始）见
[`docs/ainer-boot-1.0-product.md`](docs/ainer-boot-1.0-product.md)；动态门禁只以
[`docs/project-status.md`](docs/project-status.md) 为准。

## 架构立场

- **演进式模块化平台架构**：使用 DDD 识别领域边界，使用端口和适配器隔离外部依赖，以模块化
  单体交付当前系统，并在满足明确条件后按需服务化；完整决策见
  [ADR-0024](docs/decisions/0024-evolutionary-modular-platform-architecture.md)。
- **小制品而非万能工具包**：公共能力以 BOM、Framework、Starter、Test Support 和 Build Tools
  分别发布，当前保持 Git 单仓，不建立 `ainer-tool` 大包；见
  [ADR-0025](docs/decisions/0025-public-artifacts-utilities-and-repository-boundary.md)。
- **MyBatis-Plus 只增强基础设施**：简单 CRUD/分页可使用 MyBatis-Plus，复杂 PostgreSQL SQL
  与现有 XML 保持显式，ORM 类型不进入 application/domain/API；见
  [ADR-0028](docs/decisions/0028-mybatis-plus-infrastructure-baseline.md)。
- **模块化单体优先**：先把边界、事务与测试做对，再根据真实负载拆服务。
- **两个装配模型，不是 YAML 魔法**：单体与服务化使用独立可执行发行物；`ainer.runtime.mode` 只选择当前发行物内的本地或远程适配器。
- **标准优先**：认证规划采用 Spring Security、OAuth 2.1/OIDC 与 Spring Authorization Server，不自造 Token 协议。
- **身份发行与业务服务分离**：业务应用验证标准 JWT，高风险请求可选择性追加标准 introspection；Ainer Authorization Server 是可替换的独立发行物。
- **AI 是一等能力**：模型网关、费用、配额和审计已经形成最小闭环；后续继续建设 Agent、RAG、工具、评测与可观测性。
- **真实 HTTP 语义**：客户端错误返回 4xx，服务错误返回 5xx；响应体提供稳定业务码与 `requestId`，但不覆盖 HTTP 语义。
- **Clean-room 自研**：竞品代码只用于理解问题和验证事实，不复制受限实现、命名或模板。

## 快速验证

Ainer 的生产者构建统一使用仓库内 Maven Wrapper，它锁定 Maven 4.0.0-rc-6；该版本仍是
preview，不表示 Maven 4 已进入稳定版。请使用 JDK 25，并从仓库根目录执行：

```bash
./mvnw --version
./mvnw clean verify
```

系统 Maven 3.9+ 只用于 `scripts/verify-maven-consumers.sh` 的下游兼容门禁，不能替代 Wrapper
构建、安装或发布 Ainer reactor。Initializer 生成的独立项目例外：必须使用生成目录自己的
`./mvnw`，它固定 Maven 3.9.16 与发行包摘要。
安全纵向切片 manifest 显式选择 `schemaVersion: v2`、
`preset: simple-service`、`accessControl: workspace` 与自有 `errorNamespace`；完整合同和样例见
[ADR-0052](docs/decisions/0052-initializer-v2-secure-vertical-slice.md)。该能力已随 `v1.3.0`
发布；消费者应使用正式 Release，不得把本仓库 SNAPSHOT 或开发分支当作稳定发行物。
已有项目增量接入在开发分支使用同一份 Manifest v2，并要求调用者明确指定第一个 Flyway 版本：

```bash
java -jar ainer-initializer-cli-<version>-cli.jar \
  plan-add manifest-v2.yaml /path/to/existing-project --migration-version 3
java -jar ainer-initializer-cli-<version>-cli.jar \
  add manifest-v2.yaml /path/to/existing-project --migration-version 3
```

`plan-add` 不写盘；`add` 只新增生成文件、幂等保留同字节文件，并有限合并顶层 POM。它不会猜测
migration、修改宿主 Application/application.yml/README/Wrapper，也不支持多模块或 Gradle；完整合同见
[ADR-0053](docs/decisions/0053-initializer-existing-project-and-authorization-composition.md)。
若 Apache 刚发布新的 rc、持久下载端点尚在同步，新环境首次启动 Wrapper 可能暂时返回 404；
不要把仓库 URL 改到会被删除的临时候选目录，当前同步状态见
[`docs/project-status.md`](docs/project-status.md)。

数据库集成测试使用 Testcontainers；本机没有 Docker 时会明确跳过，不会退回 H2。运行应用前提供一个空 PostgreSQL 数据库，Flyway 会自动建表：

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ainer
export SPRING_DATASOURCE_USERNAME=ainer
export SPRING_DATASOURCE_PASSWORD=your-local-password
export AINER_SECURITY_ISSUER_URI=https://issuer.example
export AINER_SECURITY_AUDIENCES=ainer-api
./mvnw -pl ainer-server -am spring-boot:run
```

启动后可验证平台与 Workspace API。Workspace 的 tenant、owner 只取自 Bearer JWT，token 需要 `workspace.read` / `workspace.write` scope；请求体不能指定身份归属：

```bash
curl -i http://127.0.0.1:8080/api/platform/info
curl -i http://127.0.0.1:8080/actuator/health
curl -i -X POST http://127.0.0.1:8080/api/workspaces \
  -H "Authorization: Bearer ${AINER_ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: local-workspace-1' \
  -d '{"name":"Ainer研发空间"}'
```

`AINER_SECURITY_RESOURCE_SERVER_ENABLED=false` 只允许用于隔离环境验证公开平台端点；Workspace 和 AI 等可信身份能力在缺少 `AuthenticatedActor` 时应启动失败或拒绝，不能借此作为本地免认证后门。

AI runtime 默认关闭。启用时必须显式配置 HTTPS provider、API key、模型白名单、预算和价格；只有本机合约测试才允许额外设置 `AINER_AI_ALLOW_INSECURE_HTTP=true`：

```bash
export AINER_AI_ENABLED=true
export AINER_AI_BASE_URL=https://your-openai-compatible-provider.example
export AINER_AI_API_KEY=replace-with-secret-injection
export AINER_AI_DEFAULT_MODEL=your/model
export AINER_AI_ALLOWED_MODELS=your/model
export AINER_AI_TENANT_DAILY_BUDGET=10.00
export AINER_AI_INPUT_PER_MILLION_TOKENS=1.00
export AINER_AI_OUTPUT_PER_MILLION_TOKENS=2.00
```

调用示例：

```bash
curl -i -X POST http://127.0.0.1:8080/api/ai/chat/completions \
  -H "Authorization: Bearer ${AINER_ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"USER","content":"介绍 Ainer"}],"maxOutputTokens":512}'
```

AI API 要求 access token 携带合法 `sub`（HumanAccount/ServicePrincipal）、`token_profile`、
`claim_contract_version=1`、`aud=ainer-api` 和 `ai.invoke` scope；外部身份请求头不再生效。
完整签发与验证配置见 [Identity 与 OAuth 2.1 使用基线](docs/security.md)，SSE 和模型配置见
[AI Model Gateway 使用与运维](docs/ai-gateway.md)。

所有响应都会携带 `X-Request-Id`。平台信息接口返回：

```json
{
  "code": "AINER.COMMON.OK",
  "message": "OK",
  "data": {
    "name": "Ainer Boot",
    "runtimeMode": "MONOLITH",
    "javaFeatureVersion": 25
  },
  "requestId": "...",
  "timestamp": "..."
}
```

## 工程结构

```text
ainer-boot/
├── pom.xml
├── ainer-dependencies/
├── ainer-framework/
│   ├── ainer-core/
│   ├── ainer-spring/
│   ├── ainer-security/
│   ├── ainer-starter-web/
│   ├── ainer-starter-persistence/
│   └── ainer-starter-security/
├── ainer-module-identity/
├── ainer-module-workspace/
├── ainer-module-ai-runtime/
├── ainer-module-authorization/
├── ainer-module-task/
├── ainer-server/
├── ainer-authorization-server/
└── docs/
```

## 文档入口

从 **[Ainer 文档总览：从这里开始](docs/00-overview.md)** 进入。它提供项目心智模型、按任务阅读
路线、完整文档地图和维护规则，不需要先猜应该打开哪个文件。

开始开发前还应确认
[当前项目状态与已知缺口](docs/project-status.md)；
参与规则见 [CONTRIBUTING.md](CONTRIBUTING.md)，阶段变化见 [CHANGELOG.md](CHANGELOG.md)。

## 下一里程碑

`v1.3.0` 发布后的最高优先级是把已有项目增量接入与 Workspace/Authorization 组合合同形成下一个
正式版本，并让首个真实产品消费者从远端制品重放该升级与回滚。动态完成项、缺口和后续顺序只在
[`docs/project-status.md`](docs/project-status.md) 维护，README 不复制时间敏感任务清单。
