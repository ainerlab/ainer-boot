# Spring Boot 4.1 / JDK 25 验证记录

> 版本：v1.0 · 2026-07-22
> 性质：基于 Ainer 当前代码与官方文档的验证记录，不保存竞品源码片段。

> 构建基线说明：本文保留 2026-07-22 使用 Maven 3.9 与 Flatten Maven Plugin 的历史验证语境。
> 当前生产者构建基线已经由
> [ADR-0026](decisions/0026-maven-4-build-and-consumer-pom-baseline.md)
> 取代为 Maven Wrapper + Maven 4.0.0-rc-6 preview；历史命令不能作为当前构建指引。

## 1. 官方基线

- Spring Boot 4.1.0 于 2026-06-10 发布。
- 官方要求至少 Java 17，兼容至 Java 26。
- 官方 Maven 最低版本为 3.6.3；Ainer 为保证现代插件能力，项目门禁提高到 Maven 3.9+。
- Boot 4.1 使用 Spring Framework 7.0.8+、Tomcat 11 / Servlet 6.1 和 Jackson 3。
  当前生产 BOM 为 **4.1.1**（2026-08-20，Framework 7.0.9 / Security 7.1.1）。

官方资料：

- [Spring Boot 4.1.0 release](https://spring.io/blog/2026/06/10/spring-boot-4/)
- [Spring Boot 4.1.1](https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now)
- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)

## 2. Ainer 已验证环境

```text
Java       25.0.2 LTS
Maven      3.9.16
Spring Boot 4.1.0
Spring     7.0.8
Tomcat     11.0.22
```

以下命令已经通过：

```bash
mvn test
```

验证覆盖：

- 十四个 Reactor project 正确解析；
- JDK/Maven Enforcer；
- 纯 Java 核心测试；
- Boot 自动装配条件测试；
- MockMvc 真实 422 错误语义；
- 随机端口启动 Tomcat 11 并访问平台信息端点。
- MyBatis Spring Boot Starter 4.0.0、Flyway 与 PostgreSQL 18（2026-07-22 历史基线）；
- Testcontainers 2.0.5 测试装配与 ArchUnit 1.4.2 边界测试。
- JDK HttpClient + Jackson 3 的 OpenAI-compatible 非流式与 SSE 合约；
- AI runtime 的策略、费用计算、供应商错误脱敏和 PostgreSQL 审计。

## 2.1 当前 Maven 4 构建基线

当前源码 POM 仍使用 `modelVersion` 4.0.0，`ainer-dependencies` 仍是 parentless、自包含 BOM；
本轮只替换构建工具和消费侧 POM 处理方式，不混入 POM 4.1、`packaging=bom`、父工程推导或依赖
版本推导。

- 生产者构建通过 Maven Wrapper 3.3.4 锁定 Maven 4.0.0-rc-6。该版本仍是 preview，
  Wrapper 只能使用 Apache 官方持久发布端点和官方发行包校验值，不得指向临时候选目录。
- 默认完整门禁是 `./mvnw clean verify`；`install` 只用于隔离本地仓库的外部 consumer 验证。
- Maven 4 内建 Consumer POM 处理 `${revision}`，Flatten Maven Plugin 已移除；
  `.mvn/maven-user.properties` 明确使用 `maven.consumer.pom.flatten=false`。
- Maven 3.9+ 只验证下游项目能导入 BOM 和消费已安装或已发布制品，不再构建 Ainer Reactor。
- clean、resources、compiler、surefire、jar、install、deploy 和 artifact 等插件显式锁定版本。
- JDK 23+ 注解处理器显式进入 Compiler Plugin 配置；生成 Spring Boot 配置元数据的模块必须对
  JAR 内 `META-INF/spring-configuration-metadata.json` 增加断言。

完整迁移边界和验收方式见 ADR-0026。

## 3. 构建注意事项

本节的 Maven 3.9 / Flatten 内容只保留历史兼容背景；当前执行规则以上一节和 ADR-0026 为准。

### 独立 BOM

`ainer-dependencies` 必须保持 parentless。根 POM 导入子 BOM 时，如果子 BOM 又继承根 POM，会产生 Maven model import cycle。

### CI-friendly 版本

以下是本记录形成时的历史方案，已经被 ADR-0026 取代：

子模块 parent 使用 `${revision}`。Flatten Maven Plugin 在 `process-resources` 生成可发布 POM，否则单独消费本地安装的子模块时可能无法解析 parent 版本。`mvn clean install` 后应抽查本地仓库 POM 不再包含 `${revision}`。

### Java 版本

根 POM 使用：

```xml
<maven.compiler.release>25</maven.compiler.release>
<maven.compiler.parameters>true</maven.compiler.parameters>
```

Enforcer 将 Java 限制为 `[25,26)`，避免未来 JDK 自动升级掩盖兼容问题。升级 JDK 时应显式修改并验证。

2026-07-30 曾隔离评估 JDK 27 EA Build 32。Spring Boot 4.1 官方兼容范围仍止于 Java 26，
而 ArchUnit 1.4.2 无法读取 `--release 27` 产生的 class major 71，因此没有把 JDK 27 设为
项目基线，也没有通过跳过架构规则绕过问题。当前继续使用 JDK 25 与 `--release 25`；重新评审
条件见 [ADR-0027](decisions/0027-keep-jdk-25-production-baseline.md)。

### 自动配置

Starter 自动配置登记在：

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

`spring.factories` 不用于 `EnableAutoConfiguration`。Boot 仍要求的其他 SPI 必须依据对应官方文档单独判断。

## 4. Jakarta 与 Jackson

- Servlet、Validation、Persistence 等使用 `jakarta.*`。
- `javax.annotation.processing`、`javax.lang.model`、`javax.tools` 属于 JDK，不迁移。
- Boot 4 的 Jackson 3 group/artifact 发生变化，应用代码优先依赖 Boot 提供的抽象和自动配置。
- AI provider 已使用 Jackson 3 tree API 解析供应商响应，禁止照搬 Jackson 2 的废弃调用。
- 在 `ainer-core` 中不放 Jackson 注解，从根上避免 JSON 实现污染核心契约。
- Spring Authorization Server JDBC 会持久化并重新反序列化 JWT claims。Boot 4/Jackson 3 的安全类型校验会拒绝 `java.util.ImmutableCollections$List12` 等 JDK 私有实现，因此持久化 claim 集合使用 `ArrayList` 等公开可验证类型，不能直接传入 `List.of(...)`。
- 仅服务于 Authorization Server 协议端点的自定义 `AuthenticationProvider` 应在对应 `SecurityFilterChain` 内注册，不声明为全局 Spring Bean；否则会污染应用级 `AuthenticationManager`，干扰 `UserDetailsService` 自动装配和表单登录。

## 5. 配置纪律

```yaml
spring:
  main:
    allow-circular-references: false
```

- 不在主配置中硬编码 active profile。
- 不提交密码、Token、AI key、证书私钥和数据库凭据。
- 默认开启 graceful shutdown 和 Actuator health/info。
- `ainer.runtime.mode` 只控制适配器，不代表部署拓扑。

## 6. PostgreSQL 持久化验证

Ainer 在 2026-07-22 的原始验证中确认可组合使用：

- MyBatis Spring Boot Starter 4.0.0；
- Boot 4 的 `spring-boot-starter-flyway`；
- Flyway PostgreSQL database module；
- PostgreSQL JDBC 与 PostgreSQL 18；
- Testcontainers 2.0.5 PostgreSQL module。

Boot 4 中 Flyway 自动配置不再仅靠 `flyway-core` 隐式获得；应用显式依赖 Boot Flyway starter，PostgreSQL 方言由独立 database module 提供。MyBatis 对 PostgreSQL UUID 不应依赖隐式推断，Ainer 注册显式 UUID TypeHandler。M2 出现第二个数据消费者后，上述共性已经进入 `ainer-starter-persistence`，业务 migration 与 Mapper 仍留在模块内。

### 6.1 当前 MyBatis-Plus Boot 4 验证

2026-07-30，Ainer 将当前持久化装配验证为：

- `com.baomidou:mybatis-plus-spring-boot4-starter:3.5.17`；
- `com.baomidou:mybatis-plus-jsqlparser:3.5.17`，当前传递 JSqlParser 5.2；
- Spring Boot 4.1.0、JDK 25、Maven 4.0.0-rc-6；
- PostgreSQL 18.3 Testcontainers、Flyway、显式 UUID TypeHandler 和既有 Mapper XML。

真实 PostgreSQL 原型已经通过 `BaseMapper` insert/select、数据库
`DEFAULT uuidv7()` 生成键回填与 UUID version 7 断言、自定义 XML 共存、显式 tenant 条件和
PostgreSQL 分页。自动配置还验证全局 `IdType.AUTO` 与 `maxLimit=100`。

这一验证只授权 [ADR-0028](decisions/0028-mybatis-plus-infrastructure-baseline.md) 定义的
infrastructure 增强：不把 MyBatis-Plus 类型泄漏到 application/domain/API，不启用 tenant
interceptor、逻辑删除或 MetaObject 自动填充，也不引入代码生成器。

同日补充门禁已通过：Maven 4 `clean verify` 完成 14 个 Reactor project、303 项测试且
0 failure / 0 error / 0 skipped；Identity、Workspace、AI Runtime 与 Authorization Server 的
真实 PostgreSQL 测试覆盖既有复杂 XML。隔离 `scripts/verify-maven-consumers.sh` 还验证了
Maven 4 producer/Consumer POM、可重复制品检查，以及 Maven 3.9.16 / Maven 4 外部项目导入 BOM、
消费 `ainer-starter-persistence` 并编译 `BaseMapper<?>`。

## 7. AI Provider 验证

M2 没有引入 Spring AI 或厂商 SDK，而是用 JDK 25 `HttpClient` 验证 OpenAI-compatible `/v1/chat/completions`：

- Boot/Jackson 3 可以序列化请求并解析非流式 response；
- SSE 按 `data:` frame 解析，并识别 `[DONE]`；
- `stream_options.include_usage=true` 能承载最终 usage，缺失时显式标记本地估算；
- `HttpClient.Redirect.NEVER` 避免认证 header 被透明重定向到其他主机；
- 响应累计大小受限，供应商错误正文不进入稳定 Ainer 错误。

这只证明 Ainer 当前 adapter 与兼容协议，不等同于“Spring AI 与 Boot 4.1 已验证”。后者仍需独立 PoC。

## 8. Spring Security 7.1 协议验证

M3/M4.6 已完成 Spring Authorization Server 与 Security 7 的当前最小闭环：外部 RSA key、官方
JDBC client/authorization/consent、Client Credentials、人员 JWT、RFC 7662 introspection、
RFC 7009 revocation、Authorization Code + PKCE，以及 Resource Server JWT + 选择性在线校验。
真实 PostgreSQL 验证还覆盖 JDBC claim/人员 principal 反序列化、凭证不落协议记录和 Identity
revocation epoch。

PKCE 验证覆盖测试专用 public client、S256、表单登录、授权码单次交换、错误 verifier、缺失或
`plain` challenge 和未注册回调拒绝。它不代表生产 browser client 注册/轮换、登录 UI、MFA、
Refresh Token 策略、设备码、Token Exchange、密钥轮换或多节点容量已经验证。

Spring Security 7.1 WebAuthn 已在 Authorization Server 中完成依赖与协议装配验证。Ainer 使用
官方 `JdbcPublicKeyCredentialUserEntityRepository` / `JdbcUserCredentialRepository` 协议格式，
通过自有 wrapper 补 ACTIVE/REVOKED、软撤销、最后凭证保护和审计；creation/request options
覆盖官方默认值并强制 `userVerification=required`。条件 MFA 只约束已登记人员的
`/oauth2/authorize` 和凭证管理，不给 Client Credentials、internal API 或 metrics 增加人员因子
要求。

当前自动化已用虚拟 authenticator 真实执行 registration/authentication 签名 ceremony，并覆盖
options、条件拒绝、PKCE bootstrap、`amr/auth_time`、恢复/enrollment 与 JDBC 生命周期；尚未完成
主流真实设备/浏览器兼容矩阵、恢复通知、共享限流或多节点 session。Ainer 没有用全局
`@EnableMultiFactorAuthentication` 改写服务安全链，而是在 browser chain 精确为 password 与
WebAuthn authentication filter 开启 factor accumulation；升级 Spring Security 时需要继续用
真实 HTTP 门禁验证该扩展点。

## 9. 尚未验证，不得写成既定事实

以下能力在 Ainer 当前 Reactor 中尚未落地，接入前必须单独 PoC：

- dynamic-datasource；
- Redisson 4 与 Spring Data Redis 4/Jackson 3；
- Spring AI 与 Boot 4.1；
- Flowable 8；
- easy-trans；
- springdoc。

PoC 通过前，不在权威文档中承诺具体第三方版本或“零兼容问题”。
MyBatis-Plus Boot 4 Starter 已从本清单移除，其已验证范围以第 6.1 节和 ADR-0028 为准，
不得把局部原型扩大表述为所有 Mapper 或发布候选验证完成。

## 10. 每个新 Starter 的验收

```text
□ BOM 中集中管理版本
□ 无多余传递依赖
□ AutoConfiguration.imports 正确
□ 默认行为安全且可覆盖
□ ApplicationContextRunner 覆盖默认/启用/禁用/非法配置
□ 无 javax Java EE 残留
□ 无敏感默认值
□ ./mvnw clean verify 通过
□ 若连接外部组件，Testcontainers 或合约测试通过
```

## 11. 2026-08-26：对齐 This Week in Spring（4.1.1）

[This Week in Spring — August 25, 2026](https://spring.io/blog/2026/08/25/this-week-in-spring-august-25)
当周发布了 Boot 4.1.1 / 4.2.0-M1 / 4.0.8、Spring AI 2.0.1、Spring Data 2026.0.1、
Spring Cloud 2025.1.3 等。Ainer **只把生产基线升到 Boot 4.1.1**：

- 4.2.0-M1 的 AMQP 1.0 与 Buildpacks 镜像缓存不是当前产品目标，milestone 不进脚手架 BOM；
- Spring AI 仍按 ADR-0003 排除；2.0.1 的 CVE 修复只作为将来独立 PoC 的地板版本；
- Spring Cloud / Integration / AMQP / Batch 不引入。

空 `issuer-uri` 处理（Boot #50849）对本仓 Resource Server 的
`${AINER_SECURITY_ISSUER_URI:}` 默认值有直接意义，随 BOM 升级自动获得。
