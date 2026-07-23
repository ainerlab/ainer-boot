# Ainer 第三方依赖与许可证台账

> 状态：M4.3 selective online token validation 构建解析结果 · 2026-07-23

本台账记录 Ainer 主动选择的构建与运行基线，服务于 clean-room、商业发行和后续 SBOM 审计。版本来自当前 Maven Reactor 的有效依赖；许可证以 Maven Central 发布 POM、上游仓库许可证文件为依据。

| 组件 | 当前版本 | 用途 | 许可证 | 来源坐标 |
|---|---:|---|---|---|
| Spring Boot | 4.1.0 | 平台 BOM、Web、JDBC、Actuator、测试 | Apache-2.0 | `org.springframework.boot:*` |
| Spring Security / Authorization Server / OAuth2 Client | 7.1.0 | Resource Server、OAuth 2.1/OIDC、JWT、Client Credentials 与 JDBC 协议仓库 | Apache-2.0 | `org.springframework.security:*` |
| MyBatis Spring Boot Starter | 4.0.0 | SQL mapper 与 Boot 自动装配 | Apache-2.0 | `org.mybatis.spring.boot:mybatis-spring-boot-starter` |
| Flyway Core / PostgreSQL | 12.4.0 | 数据库 migration | Apache-2.0 | `org.flywaydb:flyway-core`、`flyway-database-postgresql` |
| PostgreSQL JDBC | 42.7.11 | PostgreSQL 驱动 | BSD-2-Clause | `org.postgresql:postgresql` |
| Testcontainers | 2.0.5 | PostgreSQL 集成测试 | MIT | `org.testcontainers:*` |
| ArchUnit | 1.4.2 | 包和分层边界测试 | Apache-2.0；其发布 POM 同时声明传递 ASM 的 BSD 许可证 | `com.tngtech.archunit:archunit` |
| Micrometer Core | 1.17.0 | 在线校验、撤销传播与安全运营指标 API | Apache-2.0 | `io.micrometer:micrometer-core` |
| Flatten Maven Plugin | 1.7.3 | 解析 CI-friendly `${revision}` 发布 POM | Apache-2.0 | `org.codehaus.mojo:flatten-maven-plugin` |

## 引入规则

- 只从 Maven Central 或经批准的内部镜像解析正式依赖。
- 新增直接依赖时，在 BOM 集中管理版本，并同步更新本台账。
- 不引入来源或许可证不清晰的二进制、复制代码和生成模板。
- 商业发布前生成完整传递依赖 SBOM，并由自动化许可证扫描复核；本文件不是完整 SBOM 的替代品。
- 依赖升级必须重新执行测试、漏洞扫描和许可证差异检查，不能只修改版本号。

## M2 取舍

- 没有引入 MyBatis-Plus、dynamic-datasource 或竞品公共包；持久化只使用完成两个垂直切片所需的最小依赖。
- Flyway 与 PostgreSQL 驱动版本由 Spring Boot BOM 管理，避免业务模块自行覆盖兼容矩阵。
- Testcontainers 和 ArchUnit 仅在测试作用域，不进入可执行服务器 JAR。
- OpenAI-compatible adapter 使用 JDK `HttpClient` 和 Boot 已提供的 Jackson 3，没有新增模型厂商 SDK 或新的网络客户端运行时依赖。
- MyBatis/Flyway/PostgreSQL/UUID 的公共装配进入 `ainer-starter-persistence`；这改变模块归属，不改变第三方依赖集合。

## M3 取舍

- 使用 Boot 4.1 新名称 `spring-boot-starter-security-oauth2-resource-server` 与 `spring-boot-starter-security-oauth2-authorization-server`，不继续采用已弃用的旧 starter 名称。
- OAuth client、authorization 与 consent 使用 Spring Security 官方 JDBC repository 和其 PostgreSQL 适配 schema；没有引入 Sa-Token、Keycloak 私有 SDK或自研 Token 表。
- JWT 的 Nimbus JOSE/JWT 实现由 Spring Security 官方 starter 传递引入，不在业务模块中直接使用；RSA key 只在独立 Authorization Server 装配层加载。

## M4.1 取舍

- 服务间 Token 获取使用 Spring Security 官方 `spring-security-oauth2-client` 的 Client Credentials provider、authorized-client service 与到期缓存，不自造 Token HTTP/缓存协议。
- 首个撤销 transport 使用 Boot 已有 `RestClient` 与 Jackson 3，没有引入 Feign、消息中间件 SDK 或专有服务发现依赖；outbox 端口保留后续替换 transport 的能力。
- PostgreSQL `FOR UPDATE SKIP LOCKED`、条件更新和 receipt 提供并发领取/幂等基础，没有为当前容量证据提前引入 Kafka、RabbitMQ 或 Redis。

## M4.2 取舍

- 双人审批、热/冷审计与 SIEM 稳定游标继续使用现有 PostgreSQL、MyBatis 和 Spring Security 边界，没有引入工作流引擎或 SIEM 厂商 SDK。
- 撤销传播 SLO bucket 与归档/恢复指标使用 Actuator 已引入的 Micrometer API，没有新增生产 exporter 依赖；监控后端仍属运营缺口。

## M4.3 取舍

- Resource Server 在线存活检查使用 Spring Security 官方 opaque-token introspector；Authorization Server 使用官方 RFC 7662/RFC 7009 端点和 JDBC authorization service，没有引入自研 Token 表或 Redis deny-list。
- `ainer-starter-security` 直接依赖 Micrometer Core，使可复用 starter 可以在存在 `MeterRegistry` 时记录在线校验指标，不强迫消费方引入 Actuator exporter。
- Identity revocation epoch 复用现有 access-event 表与 PostgreSQL 索引，不引入新的数据库组件；高风险请求明确接受一次在线网络与数据库查询成本。
