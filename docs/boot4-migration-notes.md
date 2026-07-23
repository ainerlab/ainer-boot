# Spring Boot 4.1 / JDK 25 验证记录

> 版本：v1.0 · 2026-07-22
> 性质：基于 Ainer 当前代码与官方文档的验证记录，不保存竞品源码片段。

## 1. 官方基线

- Spring Boot 4.1.0 于 2026-06-10 发布。
- 官方要求至少 Java 17，兼容至 Java 26。
- 官方 Maven 最低版本为 3.6.3；Ainer 为保证现代插件能力，项目门禁提高到 Maven 3.9+。
- Boot 4.1 使用 Spring Framework 7.0.8+、Tomcat 11 / Servlet 6.1 和 Jackson 3。

官方资料：

- [Spring Boot 4.1.0 release](https://spring.io/blog/2026/06/10/spring-boot-4/)
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
- MyBatis Spring Boot Starter 4.0.0、Flyway 与 PostgreSQL 18；
- Testcontainers 2.0.5 测试装配与 ArchUnit 1.4.2 边界测试。
- JDK HttpClient + Jackson 3 的 OpenAI-compatible 非流式与 SSE 合约；
- AI runtime 的策略、费用计算、供应商错误脱敏和 PostgreSQL 审计。

## 3. 构建注意事项

### 独立 BOM

`ainer-dependencies` 必须保持 parentless。根 POM 导入子 BOM 时，如果子 BOM 又继承根 POM，会产生 Maven model import cycle。

### CI-friendly 版本

子模块 parent 使用 `${revision}`。Flatten Maven Plugin 在 `process-resources` 生成可发布 POM，否则单独消费本地安装的子模块时可能无法解析 parent 版本。`mvn clean install` 后应抽查本地仓库 POM 不再包含 `${revision}`。

### Java 版本

根 POM 使用：

```xml
<maven.compiler.release>25</maven.compiler.release>
<maven.compiler.parameters>true</maven.compiler.parameters>
```

Enforcer 将 Java 限制为 `[25,26)`，避免未来 JDK 自动升级掩盖兼容问题。升级 JDK 时应显式修改并验证。

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

Ainer 当前确认可组合使用：

- MyBatis Spring Boot Starter 4.0.0；
- Boot 4 的 `spring-boot-starter-flyway`；
- Flyway PostgreSQL database module；
- PostgreSQL JDBC 与 PostgreSQL 18；
- Testcontainers 2.0.5 PostgreSQL module。

Boot 4 中 Flyway 自动配置不再仅靠 `flyway-core` 隐式获得；应用显式依赖 Boot Flyway starter，PostgreSQL 方言由独立 database module 提供。MyBatis 对 PostgreSQL UUID 不应依赖隐式推断，Ainer 注册显式 UUID TypeHandler。M2 出现第二个数据消费者后，上述共性已经进入 `ainer-starter-persistence`，业务 migration 与 Mapper 仍留在模块内。

## 7. AI Provider 验证

M2 没有引入 Spring AI 或厂商 SDK，而是用 JDK 25 `HttpClient` 验证 OpenAI-compatible `/v1/chat/completions`：

- Boot/Jackson 3 可以序列化请求并解析非流式 response；
- SSE 按 `data:` frame 解析，并识别 `[DONE]`；
- `stream_options.include_usage=true` 能承载最终 usage，缺失时显式标记本地估算；
- `HttpClient.Redirect.NEVER` 避免认证 header 被透明重定向到其他主机；
- 响应累计大小受限，供应商错误正文不进入稳定 Ainer 错误。

这只证明 Ainer 当前 adapter 与兼容协议，不等同于“Spring AI 与 Boot 4.1 已验证”。后者仍需独立 PoC。

## 8. Spring Security 7.1 协议验证

M3/M4.5 已完成 Spring Authorization Server 与 Security 7 的当前最小闭环：外部 RSA key、官方
JDBC client/authorization/consent、Client Credentials、人员 JWT、RFC 7662 introspection、
RFC 7009 revocation、Authorization Code + PKCE，以及 Resource Server JWT + 选择性在线校验。
真实 PostgreSQL 验证还覆盖 JDBC claim/人员 principal 反序列化、凭证不落协议记录和 Identity
revocation epoch。

PKCE 证据覆盖测试专用 public client、S256、表单登录、授权码单次交换、错误 verifier、缺失或
`plain` challenge 和未注册回调拒绝。它不代表生产 browser client 注册/轮换、登录 UI、MFA、
Refresh Token 策略、设备码、Token Exchange、密钥轮换或多节点容量已经验证。

## 9. 尚未验证，不得写成既定事实

以下能力在 Ainer 当前 Reactor 中尚未落地，接入前必须单独 PoC：

- MyBatis-Plus Boot 4 Starter；Ainer 当前只验证了原生 MyBatis starter；
- dynamic-datasource；
- Redisson 4 与 Spring Data Redis 4/Jackson 3；
- Spring AI 与 Boot 4.1；
- Flowable 8；
- easy-trans；
- springdoc。

PoC 通过前，不在权威文档中承诺具体第三方版本或“零兼容问题”。

## 10. 每个新 Starter 的验收

```text
□ BOM 中集中管理版本
□ 无多余传递依赖
□ AutoConfiguration.imports 正确
□ 默认行为安全且可覆盖
□ ApplicationContextRunner 覆盖默认/启用/禁用/非法配置
□ 无 javax Java EE 残留
□ 无敏感默认值
□ mvn test 通过
□ 若连接外部组件，Testcontainers 或合约测试通过
```
