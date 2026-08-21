# Ainer 工程约定

> 文档类型：长期规范 · 状态：生效 · 最近核对：2026-08-21 · 适用版本：`1.1.x`

## 1. 基本原则

- 先形成可测试的最小闭环，再抽象 Starter。
- 业务代码按 feature 聚合，不建立全局 controller/service/repository 巨型目录。
- 依赖只能指向更稳定的层；framework 不能依赖业务。
- 使用构造器注入，禁止字段注入和跨模块 Service 互注。
- 循环依赖是设计错误，`allow-circular-references=false`。
- 封装政策，不封装语法糖；不建立万能 `common`、`tool` 或静态 `XxxUtil`。

## 2. Java 与构建

- **注释语言统一中文**（2026-08-19 决策）：`src/main/java` 的类级 javadoc 与方法级
  注释使用中文；技术名词、类名/方法名、SQL 关键字、RFC/ADR 编号与 `@code`/`@link`
  标签保留英文原文。错误码默认消息、日志中的业务语义同样使用中文。理由：付费客户与
  维护团队为中文团队（文档、错误消息、协作语言均已中文），英文注释在中文团队维护下
  会退化为翻译腔或被忽视。存量英文注释按模块批次翻译，不阻塞合并；新增代码一律中文。
- JDK 25；Ainer 生产者构建统一使用 Maven Wrapper 锁定的 Maven 4.0.0-rc-6 preview，
  根 POM 使用 Enforcer 校验。不得用开发机全局 Maven 替代 Wrapper。
- 使用 `maven.compiler.release=25` 和 `-parameters`。
- 生产代码使用 `jakarta.*`；仅 JDK 自带处理器 API 保留 `javax.*`。
- 子模块依赖不写版本，统一由 `ainer-dependencies` 管理。
- `${revision}` 的消费侧 POM 由 Maven 4 内建 Consumer POM 处理，不使用 Flatten Maven Plugin；
  `.mvn/maven-user.properties` 固定 `maven.consumer.pom.flatten=false`，安装或发布后的标准
  Consumer POM 不得残留未解析的 `${revision}`。
- clean、resources、compiler、surefire、jar、install、deploy 与 artifact 等实际使用的构建插件
  必须显式锁定版本；插件升级作为独立变更验证。
- JDK 23+ 注解处理器必须在 Maven Compiler Plugin 中显式声明，不能依赖普通 optional
  dependency 的 classpath 自动扫描；需要生成配置元数据的模块必须断言对应产物存在。
- Maven 3.9+ 只用于独立下游项目导入 BOM、消费已安装或已发布制品的兼容门禁，不得构建、
  安装或发布 Ainer Reactor。
- 默认完整门禁为 `./mvnw clean verify`；`install` 只用于 golden consumer 或发布前的本地仓库
  消费验证。
- `ainer-core` 禁止出现 Spring、Servlet、ORM、Jackson 注解依赖。

完整构建与 Consumer POM 决策见
[ADR-0026](decisions/0026-maven-4-build-and-consumer-pom-baseline.md)。

### 2.1 公共制品与工具

- Git 仓库边界不等于 Maven 制品边界；当前保持单仓、多制品。
- 公共制品使用能力名称，不使用 `ainer-tool`、`ainer-common`、`ainer-misc`。
- 集合、字符串、时间和文件优先使用 JDK；Spring 工具只在 Spring-bound 模块使用。
- JSON 使用构造器注入的 Boot `ObjectMapper`；HTTP 使用 JDK `HttpClient`、Spring `RestClient`
  或明确需要响应式时的 `WebClient`。
- 外部系统建立类型化 `Gateway`、`Client` 或 `Codec`，不得暴露任意 URL 和厂商 DTO。
- 只有需要表达安全、身份、超时、错误、幂等或版本政策时才建立 Ainer 包装。
- 新制品与独立仓库准入条件见
  [ADR-0025](decisions/0025-public-artifacts-utilities-and-repository-boundary.md)，具体标准能力选择见
  [`dependencies.md`](dependencies.md#公共工具与标准能力选择)。

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

- 表、字段、类型、约束、索引、资源归属完整性和 AI 数据设计必须遵守
  [`database-design-standard.md`](database-design-standard.md)；本节只保留工程层速查。
- PostgreSQL 是真实测试目标。
- PostgreSQL 18 是唯一数据方言；新 Ainer 持久化 ID 默认 UUIDv7，所有身份与资源归属键都是 UUID。
  Greenfield（ADR-0033）后不再有 `tenant_id` claim，不为旧 tenant 实现保留兼容层。
- migration 使用 `VyyyyMMddHHmm__description.sql`，一个文件做一件事；已发布 migration 不修改。
- SQL 使用参数绑定。权限条件不得通过字符串拼接进入 SQL。
- `CREATE INDEX CONCURRENTLY` 与事务 migration 分开执行。
- 集成测试使用 Testcontainers PostgreSQL，禁止依赖 H2 compatibility mode 证明兼容性。
- MyBatis/MyBatis-Plus adapter 位于业务模块 infrastructure；应用、领域与 API 不依赖 Mapper、
  Wrapper、MyBatis-Plus Page 或 ORM 注解。
- 简单单表 CRUD 与分页可以在 infrastructure 使用 `BaseMapper`、Wrapper 和 Page；不得默认
  继承 `IService` / `ServiceImpl`、使用 ActiveRecord 或生成通用 CRUD Controller。
- CTE、锁、`RETURNING`、advisory lock、审计、稳定游标等复杂或安全敏感路径继续使用
  显式 Mapper 方法和 XML，不为统一框架 API 改写。
- `ainer-starter-persistence` 只提供 MyBatis-Plus/MyBatis/Flyway/PostgreSQL/UUID 共性装配，
  不放业务 Row、Mapper、Repository 或 migration。
- 全局 ID 类型使用 `IdType.AUTO`，让 PostgreSQL `DEFAULT uuidv7()` 生成并回填 ID；禁止使用
  `ASSIGN_ID` / `ASSIGN_UUID` 生成 Ainer 持久化身份。
- 无 tenant 多租户拦截器。Repository 与 SQL/Wrapper 必须显式绑定可信资源归属键
  （`workspace_id`/`account_id` 等）；未来即使引入拦截器也只可作为纵深防御，不能替代授权。
- 不默认启用逻辑删除和 MetaObject 自动填充。分页固定 PostgreSQL 方言、`maxLimit=100`，且在
  interceptor 链尾。
- PostgreSQL UUID 使用显式 TypeHandler 并以 `Types.OTHER` 绑定，不能假设驱动或框架自动完成转换。
- 事务边界位于应用用例；涉及聚合与附属记录的写入必须有失败回滚测试。

完整持久化增强边界见
[ADR-0028](decisions/0028-mybatis-plus-infrastructure-baseline.md)。

## 9. 安全与隐私

- 密码、Token、API key、完整手机号和模型敏感输入禁止写日志。
- 密钥来自 KMS/Vault/环境注入，不进入源码和默认 YAML。
- 字段加密采用带认证的加密模式，每条记录使用唯一 nonce，并携带 key version。
- 权限拒绝默认关闭，不能因为用户上下文缺失而静默放行。
- 资源 owner 只来自 typed principal（`USER_NEUTRAL_V1` `sub` 是 HumanAccount、`SERVICE_V1` `sub`
  是 ServicePrincipal），不能由请求体、查询参数或普通请求头指定。
- 所有资源 Repository 方法和 SQL 必须显式接收并绑定资源归属键（`workspace_id` 等）；scope 不能
  替代资源成员关系与所有权校验。
- 非成员查询优先返回 404 防止资源枚举；已确认成员但操作权限不足返回 403。
- 邀请记录不能直接获得资源访问权；目标主体必须使用本人可信身份完成激活，授权查询只认 ACTIVE 成员。
- OWNER 的授予、降级和移除必须通过具有锁、回滚与数据库唯一约束的专用所有权用例，不能复用通用成员更新。
- 高价值授权变更必须记录允许与拒绝决策。审计记录与业务访问日志分离；受保护写操作不能吞掉审计失败。
- 审计查询必须有独立 scope、资源管理员校验以及资源绑定条件；读取审计本身也需要审计。
- Identity 只允许返回显式安全投影，禁止复用包含 password hash、锁定状态或 OAuth 协议字段的账号对象。
- 自包含 JWT 不得被描述为数据库状态变化后立即失效；账号撤销通过 `sec_epoch`/`security_epoch`
  在线比对实时生效（`RevocationAwareOAuth2AuthorizationService`），普通 `@Async`/
  `@TransactionalEventListener` 不能承担可靠撤销通知。

## 10. AI

- 业务模块通过 `ModelProvider` / 应用服务等 Ainer 端口调用 AI，不直接依赖厂商 SDK或供应商 DTO。
- 每次调用记录主体（typed `sub`）、请求 ID、模型、Token/费用、耗时、状态和策略决策。
- Prompt 和模型输出正文默认不落库、不写日志；只允许记录不可逆 fingerprint 与治理元数据。
- Provider API key 只通过 secret 注入，默认 URL 必须为 HTTPS；原始供应商错误正文不得向外传播。
- 流式调用必须有明确的最终 usage/完成语义；没有供应商 usage 时必须标记估算，不能伪装成实际计量。
- 预算预占必须在调用 provider 前完成。集群级预算使用共享存储作为权威账本；进程内限流必须明确标注 node-local。
- 价格是受控运维配置，必须记录币种与每百万输入/输出 Token 单价；不能把某个供应商的临时价格硬编码到领域层。
- 外部 subject header 不能充当身份凭证；应用上下文最终来自 Resource Server 验证后的 typed principal。
- 工具必须声明权限、输入 schema、超时和幂等策略。
- RAG 检索必须执行资源权限过滤，不能在生成答案后再补权限。

## 11. 测试

- 测试名称描述行为，不描述方法实现。
- 修复缺陷必须先增加可复现测试。
- 重要 Starter、数据库适配、认证和 AI provider 需要失败路径测试。
- AI provider 合约至少覆盖请求字段、Bearer header、非流式、SSE、最终 usage、usage fallback、超时/限流和错误脱敏。
- AI 数据集成测试至少覆盖 migration、预算并发暴露、拒绝/失败审计和租户隔离。
- 全量验收命令：

```bash
./mvnw clean verify
```

## 12. Git

提交格式：`type(scope): 中文描述`。一次提交只表达一个可审查的意图，禁止把格式化、重构和新功能混在一起。
