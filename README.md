# Ainer Boot

> 正式品牌：Ainer · 脚手架产品：Ainer Boot · JDK 25 + Spring Boot 4.1.0
> · Maven 4.0.0-rc-6 preview

Ainer Boot（**AI-Native Extensible Runtime**，中文读音“艾纳”）是 AI-native、但不局限于 AI
的通用企业 Java 脚手架与运行基线。它从模块化单体开始，通过明确的契约、适配器和独立发行物
演进为服务化系统；它不继承 Yudao、BladeX、Dante 或 Snowy 的代码与框架范式。产品边界、
多标杆能力矩阵和 P0–P5 路线见
[Ainer Boot 产品定位、竞品能力矩阵与路线图](docs/design/ainer-scaffold-design.md)。

品牌与活动技术标识现已统一为 Ainer，脚手架产品名为 **Ainer Boot**。`v1.0.0` 已发布
（工程合同定稿，见 [`docs/ainer-boot-1.0-product.md`](docs/ainer-boot-1.0-product.md)）；
仓库仍为私有、专有，公开发行/开源许可决策未做，正式决策、产品命名、
域名状态、目标标识和迁移记录见 [ADR-0004：Ainer 品牌与技术命名基线](docs/decisions/0004-ainer-brand-and-naming-baseline.md)。

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
| `ainer-module-dictionary` / `ainer-module-config` / `ainer-module-notification` / `ainer-module-file` | ✅ | P3 企业基座四件套：管理 API、稳定错误码、scope、同事务审计（通知默认为日志发送器；WEBHOOK/EMAIL 可选用脚手架 HTTP/SMTP 投递，SMS/Push 仍经 `ChannelSender` SPI 由产品实现） |
| `ainer-module-organization` | ✅ Incubating | 组织目录：Unit/任职/分配/岗位 + `workforce.position#assignee` 成员解析（撤岗即失权，ADR-0042） |
| `ainer-module-knowledge` | ✅ Incubating | Knowledge Foundation：不可变 Revision + SUPERSEDES 血缘 + 人工发布门禁（ADR-0044） |
| `ainer-module-task` | ✅ Incubating | 任务调度：类型注册、延迟/周期执行、SKIP LOCKED 领取、指数退避、超时看门狗与管理 API（ADR-0047） |
| `ainer-server` | ✅ | JWT Resource Server、受保护 Prometheus exporter、Workspace、AI Runtime、Authorization、P3 与 Incubating 模块装配 |
| `ainer-authorization-server` | ✅ foundation | OAuth 2.1/OIDC、PKCE、条件 Passkey、typed token profile、RFC 7662/7009 与受审计 JDBC 协议仓库 |

当前版本已经在本机 Colima/Testcontainers 的真实 PostgreSQL 18.3 上通过完整 Reactor 测试，Identity、Workspace、AI runtime 与 Authorization Server 数据库用例均实际执行；M1/M2 还曾使用真实 PostgreSQL 18.4 与本地 OpenAI-compatible 合约服务完成验证。本轮另在本机 PostgreSQL 18.4 从空库启动 Authorization Server，完成专用/普通 introspection client 隔离、active、RFC 7009 撤销与 revocation epoch 查询计划验证。它是可运行的工程基线，不再是文档草案；生产高可用、容量与告警仍需单独完成。

[`v1.1.0`](https://github.com/ainerlab/ainer-boot/releases/tag/v1.1.0) 是当前稳定版本：
商业级代码评审（PR #24–#27）的首次制品发布——安全 5 项（starter 默认 fail-closed、
SubjectSet 跨工作区提权、决策审计接线等）+ 正确性 6 项（CAS 静默失效、审计顺序、分页
越界等）+ 加性 API（引擎生产路径激活、OpenAPI 运行时文档、Knowledge 负载可读），并纳入
评审遗留收口：任务调度模块 `ainer-module-task`（ADR-0047，Incubating）、授权管理守卫
拒绝审计与 `trusted-managers` issuer 绑定匹配。
全部为加性变更与缺陷修复，无 schema 变化；`1.0.x` 作为 LTS 补丁线继续受支持（ADR-0045/0046）。
双参考消费者并存：`xq-platform-next`（`rc.2 → … → 1.0.0` 完整升级链含回滚）与
`python-learning-service`（`0.1.0 → 1.0.0` 冷仓接入）。
[`v1.0.0`](https://github.com/ainerlab/ainer-boot/releases/tag/v1.0.0) 保持不可变，作为
1.1.0 的升级起点与 `1.0.x` LTS 线的基线。

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

M4.3 已为高风险 API 建立选择性在线撤销基线：本地 JWT 认证后按路径/方法执行 RFC 7662，无 active 正向缓存；inactive 返回 401，Authorization Server 依赖失败返回 503；人员 Token 同时受 Identity 当前状态与 revocation epoch 约束，普通业务 client 不能调用 introspection。

生产指标代码基线现已开始落地：两个发行物提供受 JWT 保护的 Prometheus exporter，抓取凭据使用独立无 tenant metrics client。tenant-bound Client Credentials 也已具备默认关闭、服务端生成一次性 secret、scope/operator 双白名单、蓝绿轮换、显式退役和同事务审计的内部控制面；browser/OIDC 与平台 client 尚未纳管。

Authorization Code + PKCE 已建立真实 PostgreSQL 与浏览器 HTTP 会话门禁，覆盖 S256、登录、
授权码单次交换和回调地址拒绝；该验证使用测试专用 public client，不代表生产 browser client
控制面或登录体验已经交付。

M4.6 已完成默认关闭的 Passkey 代码主线：真实签名 ceremony、条件 MFA、恢复码、管理员双人恢复、
受控首次 enrollment、登录限速和 Resource Server step-up 均有自动化验证。本轮又修复恢复/enrollment
跨租户目标绑定、限速 HTTP 契约以及 step-up 匿名/服务身份/未来时间语义。主流真实设备矩阵、恢复
通知、共享限流和多节点会话仍未完成，详见 ADR-0014 至 ADR-0017。

M4.7 首个管理面切片已经落地：Identity 所属的 `ainer-authorization-server` 提供租户成员列表、
加入、角色变更和软移除 API，
同时要求 USER actor、`tenant.members.read|write`、可信 tenant claim 与数据库 ACTIVE
OWNER/ADMIN 关系；所有写入同事务审计，通用接口不能操作 OWNER。首个平台 tenant/OWNER 可用默认
关闭的严格幂等 bootstrap 创建，部分占用或状态漂移会失败关闭，详见
[ADR-0018](docs/decisions/0018-management-authorization-and-tenant-member-management.md)。

Ainer Admin 后端融合基线已经收口：`dev` profile 提供固定 `ainer-admin-dev` public client 与
安全开发身份 fixture；成员 API 逐请求校验官方 authorization active 状态；当前 access token
可自助撤销；`ainer-admin-v1.yaml` 可严格校验并生成 TypeScript SDK。同一 browser client 的真实
PostgreSQL 端到端测试已覆盖 PKCE → 成员列表/添加/双向改角色/软移除 → revoke → OIDC logout。
第一版采用 `/ainer-admin/` 同源反代、不启用 Refresh Token 或全局 CORS，完整契约见
[Ainer Admin 集成手册](docs/ainer-admin-integration.md)。

M4.8 的已接受设计见
[ADR-0019](docs/decisions/0019-identity-provisioning-tenant-context-and-ownership-governance.md)：
按“平台 tenant/user 幂等供应与一次性激活 → 人员多租户上下文选择 → OWNER 双方强认证转移”
推进。该顺序避免把 `is_default` 当作跨设备租户切换状态，也避免在目标管理员无法取得目标 tenant
Token 时提前实现不可本人确认的 OWNER 转移。租户角色与未来 Community / Pro / Enterprise
entitlement 保持独立。

M4.8A 已形成“预配、激活与控制面”代码基线：平台申请仍只预留标识，同时为新用户创建短时、限次、
只存 SHA-256 摘要的一次性 grant，并把唯一明文连同联系目标写入 AES-256-GCM 保护的 notification
outbox；已有 ACTIVE 用户不产生认证材料，只生成按 Identity subject 路由的接受通知。新用户凭
grant 设置首个长期密码，已有用户则必须以本人 USER Token 和 `identity.provisioning.accept`
接受；两条路径都在单一事务中创建 ACTIVE tenant 与唯一 OWNER membership，失败不留下核心孤儿
记录。平台响应和数据库可查询列均不暴露激活明文。Authorization Server 已提供默认关闭的
OAuth2 Client Credentials + HTTPS 通知网关 relay，以稳定 notification ID 做下游幂等键，并在
网关持久接收或请求取消后销毁可解密 payload；具体邮件/短信/站内信供应商和模板仍属于外部通知域。
平台还形成了默认关闭的终态回执接收基线：外部网关使用另一组 tenantless SERVICE credential、
精确白名单和 `identity.provisioning-notifications.receipts.write` scope，把供应商结果归一化为
`DELIVERED` 或 `FAILED`；Identity 只保存 UUIDv7 notification 关联、受限事件/失败码和时间，不接收
正文、联系地址或供应商原始 body。`DELIVERED` 仍只表示供应商确认交付，不表示自然人已阅读。
平台现在还提供 tenant/user 的受限安全分页，以及对未完成申请的显式幂等取消；取消会在同一事务
收口 request、一次性 grant、未发布通知 payload 和阶段审计。外部通知网关联调、最终送达验证、
生产边缘限速/告警和 0-skipped 发布门禁仍未完成，因此当前还不能宣称可达的生产开户已经闭环。

生产并行工作仍需部署真实 Prometheus、dashboard/告警，并完成 Authorization Server 多实例容量、
故障切换和平台旧凭据退役验证；随后继续 IAM 职责分离、外部不可变审计副本、多节点 SLO、
恢复通知与真实设备兼容矩阵、browser/OIDC client 控制面和签名密钥轮换。
