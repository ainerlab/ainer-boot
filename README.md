# Ainer Boot

> 正式品牌：Ainer · M4.3 selective online token validation · 2026-07-23 · JDK 25 + Spring Boot 4.1.0

Ainer（**AI-Native Extensible Runtime**，中文读音“艾纳”）是面向 AI 时代的企业应用平台底座。它从模块化单体开始，通过明确的契约、适配器和独立发行物演进为服务化系统；它不继承 yudao、BladeX、Dante 或 Snowy 的代码与框架范式。

品牌与活动技术标识现已统一为 Ainer，开源脚手架产品名为 **Ainer Boot**。正式决策、产品命名、域名状态、目标标识和迁移记录见 [ADR-0004：Ainer 品牌与技术命名基线](docs/decisions/0004-ainer-brand-and-naming-baseline.md)。

## 当前可用能力

| 模块 | 状态 | 职责 |
|---|---|---|
| `ainer-dependencies` | ✅ | 独立 BOM，统一管理 Boot 与 Ainer 模块版本 |
| `ainer-core` | ✅ | 零 Spring 依赖的错误契约、响应模型与注册表 |
| `ainer-spring` | ✅ | Spring 基础设施、运行模式属性和条件装配 |
| `ainer-starter-web` | ✅ | 真实 HTTP 状态、统一响应、请求 ID、全局异常处理 |
| `ainer-starter-persistence` | ✅ | MyBatis、Flyway、PostgreSQL 与 UUID 的公共装配 |
| `ainer-security` | ✅ | 与框架无关的可信参与者与 authority 契约 |
| `ainer-starter-security` | ✅ | Resource Server、人员/服务 JWT 投影、选择性 RFC 7662 在线校验与统一 401/403/503 |
| `ainer-module-identity` | ✅ | 用户/租户、安全 Directory、禁用/撤销、revocation epoch、可租约 outbox 与双人重放端口 |
| `ainer-module-workspace` | ✅ | 可信租户资源、成员治理、幂等撤销、OWNER 恢复、授权审计热/归档与 SIEM 契约 |
| `ainer-module-ai-runtime` | ✅ | OpenAI-compatible 网关、SSE、策略、预算与用量/费用审计 |
| `ainer-server` | ✅ | JWT Resource Server、可选 Directory client、撤销 consumer/SLO、OWNER 恢复与审计运营端点 |
| `ainer-authorization-server` | ✅ foundation | OAuth 2.1/OIDC、受限 introspection/RFC 7009、Identity 状态感知、Directory/relay 与 JDBC 协议仓库 |

当前版本已经通过完整 Reactor 测试；M1/M2 曾使用真实 PostgreSQL 18.4 与本地 OpenAI-compatible 合约服务完成验证。M3 至 M4.3 增加 Identity、服务身份、Directory/outbox、Workspace 撤销、双人恢复、审计归档和选择性在线 Token 校验测试；当前机器没有 Docker 时数据库测试会明确跳过。本轮还在本机 PostgreSQL 18.4 从空库启动 Authorization Server，完成专用/普通 introspection client 隔离、active、RFC 7009 撤销与 revocation epoch 查询计划验证。它是可运行的工程基线，不再是文档草案；生产高可用、容量与告警仍需单独完成。

## 架构立场

- **模块化单体优先**：先把边界、事务与测试做对，再根据真实负载拆服务。
- **两个装配模型，不是 YAML 魔法**：单体与服务化使用独立可执行发行物；`ainer.runtime.mode` 只选择当前发行物内的本地或远程适配器。
- **标准优先**：认证规划采用 Spring Security、OAuth 2.1/OIDC 与 Spring Authorization Server，不自造 Token 协议。
- **身份发行与业务服务分离**：业务应用验证标准 JWT，高风险请求可选择性追加标准 introspection；Ainer Authorization Server 是可替换的独立发行物。
- **AI 是一等能力**：模型网关、费用、配额和审计已经形成最小闭环；后续继续建设 Agent、RAG、工具、评测与可观测性。
- **真实 HTTP 语义**：客户端错误返回 4xx，服务错误返回 5xx；响应体提供稳定业务码与 `requestId`，但不覆盖 HTTP 语义。
- **Clean-room 自研**：竞品代码只用于理解问题和验证事实，不复制受限实现、命名或模板。

## 快速验证

```bash
mvn test
```

数据库集成测试使用 Testcontainers；本机没有 Docker 时会明确跳过，不会退回 H2。运行应用前提供一个空 PostgreSQL 数据库，Flyway 会自动建表：

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ainer
export SPRING_DATASOURCE_USERNAME=ainer
export SPRING_DATASOURCE_PASSWORD=your-local-password
export AINER_SECURITY_ISSUER_URI=https://issuer.example
export AINER_SECURITY_AUDIENCES=ainer-api
mvn -pl ainer-server -am spring-boot:run
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

AI API 要求 access token 包含合法 `sub`、`tenant_id`、`aud=ainer-api` 和 `ai.invoke` scope；外部身份请求头不再生效。完整签发与验证配置见 [Identity 与 OAuth 2.1 使用基线](docs/security.md)，SSE 和模型配置见 [AI Model Gateway 使用与运维](docs/ai-gateway.md)。

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
├── ainer-server/
├── ainer-authorization-server/
└── docs/
```

## 文档入口

从 [Ainer 文档中心](docs/README.md) 按角色和任务进入。长期参与开发前至少阅读：

1. [当前项目状态与已知缺口](docs/project-status.md)
2. [本地开发手册](docs/development.md)
3. [架构总览](docs/architecture.md)
4. [HTTP API 契约](docs/api.md)
5. [工程约定](docs/conventions.md)
6. [测试与质量门禁](docs/testing.md)
7. [数据库与 Migration 手册](docs/database.md)
8. [配置与秘密管理](docs/configuration.md)
9. [ADR 索引与模板](docs/decisions/README.md)

运行和发布分别见 [运行与故障处理手册](docs/operations.md) 与 [版本和发布规范](docs/releasing.md)。参与规则见 [CONTRIBUTING.md](CONTRIBUTING.md)，阶段变化见 [CHANGELOG.md](CHANGELOG.md)。

## 下一里程碑

M4.3 已为高风险 API 建立选择性在线撤销基线：本地 JWT 认证后按路径/方法执行 RFC 7662，无 active 正向缓存；inactive 返回 401，Authorization Server 依赖失败返回 503；人员 Token 同时受 Identity 当前状态与 revocation epoch 约束，普通业务 client 不能调用 introspection。

下一步不是继续扩张安全抽象，而是把该边界接入生产运营：Authorization Server 高可用与容量、专用凭据轮换、指标 exporter/dashboard/告警、IAM 职责分离、外部不可变审计副本和多节点 SLO 验证。M3 仍需完成人员账号/Client 控制面、tenant ownership transfer、Authorization Code + PKCE 端到端验证、MFA 与密钥轮换。
