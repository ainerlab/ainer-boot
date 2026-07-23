# Ainer 工程约定

> 适用状态：M4.3 selective online token validation

## 1. 基本原则

- 先形成可测试的最小闭环，再抽象 Starter。
- 业务代码按 feature 聚合，不建立全局 controller/service/repository 巨型目录。
- 依赖只能指向更稳定的层；framework 不能依赖业务。
- 使用构造器注入，禁止字段注入和跨模块 Service 互注。
- 循环依赖是设计错误，`allow-circular-references=false`。

## 2. Java 与构建

- JDK 25，Maven 3.9+；根 POM 使用 Enforcer 校验。
- 使用 `maven.compiler.release=25` 和 `-parameters`。
- 生产代码使用 `jakarta.*`；仅 JDK 自带处理器 API 保留 `javax.*`。
- 子模块依赖不写版本，统一由 `ainer-dependencies` 管理。
- 使用 `${revision}` 的子模块通过 Flatten Maven Plugin 发布可消费 POM；`install` 后的 POM 不得残留未解析 `${revision}`。
- `ainer-core` 禁止出现 Spring、Servlet、ORM、Jackson 注解依赖。

## 3. 包与类型命名

框架：

```text
dev.ainer.core.*
dev.ainer.spring.*
dev.ainer.web.*
```

业务：

```text
dev.ainer.module.<module>.<feature>.<layer>
```

常用后缀：

| 角色 | 后缀 |
|---|---|
| HTTP 请求 | `CreateRequest`、`UpdateRequest`、`PageRequest` |
| HTTP 响应 | `Response` |
| 跨模块契约 | `Command`、`Query`、`Result` |
| MyBatis 持久化数据行 | `Row` |
| JPA 持久化实体（未来如需） | `Entity` |
| 应用用例 | `UseCase` 或有业务含义的动词名 |
| 端口 | `Repository`、`Gateway`、`Publisher` |
| 基础设施实现 | `MybatisRepository`、`HttpGateway` |
| 自动配置 | `AutoConfiguration` |
| 配置属性 | `Properties` |

不使用 `DO`、`ReqVO`、`RespVO`、`CommonApi` 等来源特定的历史命名。

## 4. 模型边界

- HTTP 模型、应用命令、领域对象、持久化实体是不同职责，可在简单场景复用，但不能为了少写类而泄漏框架注解。
- DTO 必须显式设计；MapStruct 生成的是映射实现，不是 DTO。
- 领域对象优先不可变；ORM 实体根据框架要求使用普通类。
- 不返回 `Map`、`JSONObject` 或 Entity 作为公共契约。

## 5. HTTP

- REST 路径使用名词和标准方法，不使用 `/create`、`/update`、`/delete` 模拟动作。
- Controller 只负责协议转换、校验和调用用例，不写事务与业务规则。
- HTTP status 保持真实语义；错误响应使用稳定 `AINER.<MODULE>.<ERROR>` 代码。
- 未知异常只向客户端返回通用消息，在服务端日志中通过 request ID 定位。
- 接受外部 `X-Request-Id` 时必须校验字符和长度，防止日志注入。

## 6. 错误码

模块错误码示例：

```java
public enum WorkspaceErrorCode implements ErrorCode {
    MEMBER_ALREADY_EXISTS(
            "AINER.WORKSPACE.MEMBER_ALREADY_EXISTS",
            "成员已经存在",
            409);
}
```

- 代码发布后不得改变原语义。
- 启动时注册并检查重复。
- 禁止使用模块 hash、人工数字段位或散落常量接口。

## 7. Spring 与 Starter

- 使用 `@AutoConfiguration`。
- 在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 登记。
- 使用 `@ConditionalOnMissingBean` 提供可替换默认实现。
- 自动配置必须有 `ApplicationContextRunner` 测试，至少覆盖默认、开启、关闭和非法配置。
- `spring.factories` 只在 Boot 仍明确要求的非自动配置扩展点使用。

## 8. 数据库

- PostgreSQL 是真实测试目标。
- migration 使用 `VyyyyMMddHHmm__description.sql`，一个文件做一件事；已发布 migration 不修改。
- SQL 使用参数绑定。权限条件不得通过字符串拼接进入 SQL。
- `CREATE INDEX CONCURRENTLY` 与事务 migration 分开执行。
- 集成测试使用 Testcontainers PostgreSQL，禁止依赖 H2 compatibility mode 证明兼容性。
- MyBatis adapter 位于业务模块 infrastructure；应用与领域层不依赖 Mapper。
- `ainer-starter-persistence` 只提供 MyBatis/Flyway/PostgreSQL/UUID 共性装配，不放业务 Row、Mapper、Repository 或 migration。
- PostgreSQL UUID 使用显式 TypeHandler 并以 `Types.OTHER` 绑定，不能假设驱动或框架自动完成转换。
- 事务边界位于应用用例；涉及聚合与附属记录的写入必须有失败回滚测试。

## 9. 安全与隐私

- 密码、Token、API key、完整手机号和模型敏感输入禁止写日志。
- 密钥来自 KMS/Vault/环境注入，不进入源码和默认 YAML。
- 字段加密采用带认证的加密模式，每条记录使用唯一 nonce，并携带 key version。
- 权限拒绝默认关闭，不能因为用户上下文缺失而静默放行。
- 资源 tenant/owner 只来自 `AuthenticatedActor`，不能由请求体、查询参数或普通请求头指定。
- 所有租户资源 Repository 方法和 SQL 必须显式接收并绑定 tenant；scope 不能替代资源成员关系与所有权校验。
- 跨租户或非成员查询优先返回 404 防止资源枚举；已确认成员但操作权限不足返回 403。
- 邀请记录不能直接获得资源访问权；目标主体必须使用同 tenant 的可信身份完成激活，授权查询只认 ACTIVE 成员。
- OWNER 的授予、降级和移除必须通过具有锁、回滚与数据库唯一约束的专用所有权用例，不能复用通用成员更新。
- 高价值授权变更必须记录允许与拒绝决策。审计记录与业务访问日志分离；受保护写操作不能吞掉审计失败。
- 审计查询必须有独立 scope、资源管理员校验以及 tenant/resource 双条件；读取审计本身也需要审计。
- Identity Directory 只允许返回显式安全投影，禁止复用包含 password hash、锁定状态或 OAuth 协议字段的账号对象。
- 跨运行时撤销使用同事务 outbox 与幂等消费者。普通 `@Async`/`@TransactionalEventListener` 不能单独承担不可丢失通知。
- 自包含 JWT 不得被描述为数据库状态变化后立即失效；实时撤销必须有在线校验机制。

## 10. AI

- 业务模块通过 `ModelProvider` / 应用服务等 Ainer 端口调用 AI，不直接依赖厂商 SDK或供应商 DTO。
- 每次调用记录租户、主体、请求 ID、模型、Token/费用、耗时、状态和策略决策。
- Prompt 和模型输出正文默认不落库、不写日志；只允许记录不可逆 fingerprint 与治理元数据。
- Provider API key 只通过 secret 注入，默认 URL 必须为 HTTPS；原始供应商错误正文不得向外传播。
- 流式调用必须有明确的最终 usage/完成语义；没有供应商 usage 时必须标记估算，不能伪装成实际计量。
- 预算预占必须在调用 provider 前完成。集群级预算使用共享存储作为权威账本；进程内限流必须明确标注 node-local。
- 价格是受控运维配置，必须记录币种与每百万输入/输出 Token 单价；不能把某个供应商的临时价格硬编码到领域层。
- 外部 tenant/subject header 不能充当身份凭证；应用上下文最终来自已认证 principal 与可信租户解析。
- 工具必须声明权限、输入 schema、超时和幂等策略。
- RAG 检索必须执行租户和资源权限过滤，不能在生成答案后再补权限。

## 11. 测试

- 测试名称描述行为，不描述方法实现。
- 修复缺陷必须先增加可复现测试。
- 重要 Starter、数据库适配、认证和 AI provider 需要失败路径测试。
- AI provider 合约至少覆盖请求字段、Bearer header、非流式、SSE、最终 usage、usage fallback、超时/限流和错误脱敏。
- AI 数据集成测试至少覆盖 migration、预算并发暴露、拒绝/失败审计和租户隔离。
- 全量验收命令：

```bash
mvn test
```

## 12. Git

提交格式：`type(scope): 中文描述`。一次提交只表达一个可审查的意图，禁止把格式化、重构和新功能混在一起。
