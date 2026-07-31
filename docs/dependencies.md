# Ainer 第三方依赖与许可证台账

> 状态：M4.8A + 公共制品策略 · 2026-07-30

本台账记录 Ainer 主动选择的构建与运行基线，服务于 clean-room、商业发行和后续 SBOM 审计。版本来自当前 Maven Reactor 的有效依赖；许可证以 Maven Central 发布 POM、上游仓库许可证文件为依据。

| 组件 | 当前版本 | 用途 | 许可证 | 来源坐标 |
|---|---:|---|---|---|
| Spring Boot | 4.1.0 | 平台 BOM、Web、JDBC、Actuator、测试 | Apache-2.0 | `org.springframework.boot:*` |
| Spring Security / Authorization Server / OAuth2 Client | 7.1.0 | Resource Server、OAuth 2.1/OIDC、JWT、Client Credentials 与 JDBC 协议仓库 | Apache-2.0 | `org.springframework.security:*` |
| MyBatis-Plus Spring Boot 4 Starter | 3.5.17 | MyBatis、Boot 4 自动装配与基础设施 CRUD 增强 | Apache-2.0 | `com.baomidou:mybatis-plus-spring-boot4-starter` |
| MyBatis-Plus JSqlParser Module | 3.5.17 | PostgreSQL 分页插件的显式运行时模块 | Apache-2.0 | `com.baomidou:mybatis-plus-jsqlparser` |
| JSqlParser | 5.2 | MyBatis-Plus 分页所需 SQL 解析器（传递依赖） | LGPL-2.1 与 Apache-2.0 双许可证 | `com.github.jsqlparser:jsqlparser` |
| Flyway Core / PostgreSQL | 12.4.0 | 数据库 migration | Apache-2.0 | `org.flywaydb:flyway-core`、`flyway-database-postgresql` |
| PostgreSQL JDBC | 42.7.11 | PostgreSQL 驱动 | BSD-2-Clause | `org.postgresql:postgresql` |
| Testcontainers | 2.0.5 | PostgreSQL 集成测试 | MIT | `org.testcontainers:*` |
| ArchUnit | 1.4.2 | 包和分层边界测试 | Apache-2.0；其发布 POM 同时声明传递 ASM 的 BSD 许可证 | `com.tngtech.archunit:archunit` |
| Micrometer Core | 1.17.0 | 在线校验、撤销传播与安全运营指标 API | Apache-2.0 | `io.micrometer:micrometer-core` |
| Micrometer Prometheus Registry | 1.17.0 | 两个可执行发行物的 Prometheus 文本格式导出 | Apache-2.0 | `io.micrometer:micrometer-registry-prometheus` |
| Apache Maven | 4.0.0-rc-6 | Ainer 生产者构建的 preview 基线 | Apache-2.0 | `org.apache.maven:apache-maven` |
| Maven Wrapper | 3.3.4 | 固定 Maven 发行版、下载地址与校验值 | Apache-2.0 | `org.apache.maven.wrapper:maven-wrapper` |
| Maven Clean Plugin | 3.5.0 | 清理构建输出 | Apache-2.0 | `org.apache.maven.plugins:maven-clean-plugin` |
| Maven Resources Plugin | 3.5.0 | 复制与过滤资源 | Apache-2.0 | `org.apache.maven.plugins:maven-resources-plugin` |
| Maven Compiler Plugin | 3.14.0 | JDK 25 编译、参数名与显式注解处理器 | Apache-2.0 | `org.apache.maven.plugins:maven-compiler-plugin` |
| Maven Surefire Plugin | 3.5.3 | 单元与模块测试执行 | Apache-2.0 | `org.apache.maven.plugins:maven-surefire-plugin` |
| Maven JAR Plugin | 3.5.1 | 创建普通 JAR 制品 | Apache-2.0 | `org.apache.maven.plugins:maven-jar-plugin` |
| Maven Install / Deploy Plugin | 3.1.4 / 3.1.4 | 固定安装与部署生命周期实现；当前尚无正式发布仓库 | Apache-2.0 | `org.apache.maven.plugins:maven-install-plugin`、`maven-deploy-plugin` |
| Maven Artifact Plugin | 3.6.1 | 构建计划与可重复制品检查入口 | Apache-2.0 | `org.apache.maven.plugins:maven-artifact-plugin` |
| Maven Enforcer Plugin | 3.6.2 | JDK 与 Maven 构建环境门禁 | Apache-2.0 | `org.apache.maven.plugins:maven-enforcer-plugin` |
| OpenAPI Generator Maven Plugin | 7.24.0 | 严格校验 Ainer Admin 契约并生成 `typescript-fetch` SDK | Apache-2.0 | `org.openapitools:openapi-generator-maven-plugin` |

## 引入规则

- 只从 Maven Central 或经批准的内部镜像解析正式依赖。
- 新增直接依赖时，在 BOM 集中管理版本，并同步更新本台账。
- 不引入来源或许可证不清晰的二进制、复制代码和生成模板。
- 商业发布前生成完整传递依赖 SBOM，并由自动化许可证扫描复核；本文件不是完整 SBOM 的替代品。
- 依赖升级必须重新执行测试、漏洞扫描和许可证差异检查，不能只修改版本号。

## 公共工具与标准能力选择

Ainer 不通过万能静态工具类隐藏 JDK、Spring 或第三方库。选择顺序为：

1. JDK 标准能力；
2. 当前模块已经依赖的 Spring/Boot 官方能力；
3. 经过 BOM、许可证、安全和维护状态评审的窄范围第三方库；
4. 只有需要表达 Ainer 政策或隔离外部系统时，才建立类型化端口或适配器。

实际代码按三层处理：

1. **简单操作直接使用标准能力**：集合判断、不可变副本、路径操作和时间计算直接使用 JDK；
2. **统一配置通过 Bean 注入**：Jackson `ObjectMapper`、`RestClient`、`HttpClient`、`Clock` 等由
   configuration 创建并通过构造器注入，不使用静态全局实例；
3. **外部系统通过类型化边界访问**：微信、对象存储、Identity Directory、AI Provider 等建立
   `Gateway`、`Client`、`Repository` 或 `Codec`，业务代码不处理 URL、JSON、文件路径和厂商 DTO。

| 能力 | 默认选择 | Ainer 中的使用方式 | 不采用 |
|---|---|---|---|
| 字符串 | `String`、`isBlank()`、`Objects`、`Pattern`；Spring 模块可用 `StringUtils.hasText()` | 校验规则放在值对象、命令或配置验证中 | 全局 `StringUtil`、含业务规则的字符串助手 |
| 集合 | `List`、`Set`、`Map`、`Collection`、`Collections`、Stream、`List.copyOf()` | domain 优先返回不可变副本；复杂集合算法以有业务含义的方法命名 | `CollectionUtil.isEmpty()` 等只改写一行 JDK 的包装 |
| JSON | Boot 管理的 Jackson 3 `ObjectMapper` | 在 adapter/configuration 中构造器注入；稳定文档格式建立 `XxxCodec` 和版本化 schema | 静态全局 `JsonUtil`、domain 直接读写 JSON、吞掉解析异常 |
| 文件 | `java.nio.file.Path`、`Files`、`FileSystem`、`StandardCopyOption` | 只在 adapter 使用；校验根目录、规范化路径、大小、类型与生命周期 | 基于字符串拼路径的 `FileUtil`、domain 持有本地文件路径 |
| HTTP | 简单独立客户端使用 JDK `HttpClient`；Spring 同步调用使用 `RestClient`；真正响应式链路使用 `WebClient` | 每个外部系统建立类型化 `Gateway`/`Client`，集中 URL、认证、超时、错误映射和重试策略 | `HttpUtil.get/post(String)`、业务代码传任意 URL、默认信任所有 TLS |
| 时间 | `Instant`、`LocalDate`、`OffsetDateTime`、`Duration`、`Clock` | 用例注入 `Clock`；数据库与 API 明确时区和精度 | `DateUtil`、`new Date()` 散落、依赖系统默认时区 |
| 对象映射 | 显式构造器/工厂；重复且机械的映射可使用 MapStruct | 映射位于 API 或 infrastructure 边界 | 反射式 `BeanUtil.copyProperties()` 隐藏字段丢失 |
| 校验 | Jakarta Validation 用于传输边界；领域构造器和值对象维护不变量 | 错误映射为稳定 Ainer error code | 把所有规则塞进通用正则或 ValidationUtil |
| 加密 | JCA/JCE 或经评审的安全库，通过专用加密服务使用 | 显式算法、密钥引用、nonce、key version 和失败语义 | 通用 AES/RSA/SM 工具、硬编码密钥、固定 IV |

新增 Apache Commons、Guava、Hutool 或其他工具库不是默认禁止，但必须由 JDK/Boot 无法合理满足的
具体能力驱动。不得为了一个 `isEmpty`、文件复制或字符串判断引入整套工具依赖。

### 正反示例

简单集合操作直接使用 JDK，并约定集合类型不返回 `null`：

```java
List<Item> items = List.copyOf(result.items());
if (items.isEmpty()) {
    return ItemSelection.empty();
}
```

不使用把协议、序列化和任意 URL 暴露给业务代码的万能工具：

```java
String json = JsonUtil.toJson(snapshot);
String response = HttpUtil.post(url, json);
```

改为建立具有业务语义的类型化端口：

```java
public interface WechatSessionGateway {
    WechatSession exchange(LoginCode code);
}

public interface ContextSnapshotCodec {
    EncodedSnapshot encode(ContextSnapshot snapshot);
    ContextSnapshot decode(EncodedSnapshot snapshot);
}
```

适配器通过构造器接收统一配置的官方客户端。以下只展示依赖结构，省略具体协议方法：

```java
final class RestClientWechatSessionAdapter {

    private final RestClient restClient;

    RestClientWechatSessionAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    // 该 adapter 实现 WechatSessionGateway，并集中处理端点、认证、超时、
    // 响应 DTO 与稳定错误映射。
}

final class ContextSnapshotJacksonAdapter {

    private final ObjectMapper objectMapper;

    ContextSnapshotJacksonAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 该 adapter 实现 ContextSnapshotCodec，并集中处理 schema 版本和解析失败，
    // 不向 domain 泄漏 Jackson 异常。
}
```

这里的示例强调依赖方向，不要求所有外部调用使用完全相同的类结构。调用方关心的是微信会话和
上下文快照，不关心底层使用 `RestClient`、JDK `HttpClient` 还是 Jackson。公共制品与工具边界的
完整决策见
[ADR-0025](decisions/0025-public-artifacts-utilities-and-repository-boundary.md)。

## Maven 4 构建取舍

- Ainer 生产者构建使用 Maven Wrapper 锁定 Maven 4.0.0-rc-6；该版本仍是 preview，不把它描述
  为 Maven 4 稳定版。Wrapper 发行包必须来自 Apache 官方持久发布端点并校验摘要。
- Maven 4 内建 Consumer POM 负责处理当前 POM 4.0 与 `${revision}`，第三方 Flatten Maven
  Plugin 已从构建和本台账移除；`maven.consumer.pom.flatten=false` 固定当前已验证行为，不表示
  继续使用 Flatten。
- clean、resources、jar、install、deploy 等默认生命周期插件与 compiler、surefire、enforcer
  一样显式锁定，避免 Maven 版本变化静默改变构建输入。
- parentless `ainer-dependencies` 在自身 `validate` 阶段执行与根工程相同的 JDK/Maven Enforcer，
  防止误用 Maven 3 时先把 BOM 写入本地或远程仓库再失败。
- Maven 3.9+ 只验证下游项目能导入 BOM 并消费已安装或已发布制品，不再参与 Ainer Reactor
  的生产者构建。
- JDK 23+ 不再默认扫描 classpath 运行注解处理器；使用 Spring Boot 配置处理器等模块必须在
  Compiler Plugin 中显式声明，并验证生成的配置元数据。
- 本次不迁移 POM 4.1、`packaging=bom`、父工程推导或依赖版本推导；`ainer-dependencies`
  继续保持 parentless、自包含的 POM 4.0 BOM。

完整决策和验收门禁见
[ADR-0026](decisions/0026-maven-4-build-and-consumer-pom-baseline.md)。

## 当前持久化依赖取舍

- `ainer-starter-persistence` 使用 Spring Boot 4 专用
  `mybatis-plus-spring-boot4-starter:3.5.17`，不使用 Boot 2/3 starter，也不并存原生与 Plus
  两套 Ainer persistence starter。
- 分页所需 `mybatis-plus-jsqlparser:3.5.17` 是显式直接依赖；其当前传递 SQL 解析器为
  JSqlParser 5.2。升级这两个版本时必须重新核对依赖树、许可证和 PostgreSQL SQL 回归。
- MyBatis-Plus 只增强 infrastructure 的简单 CRUD 与分页。复杂 XML、PostgreSQL
  CTE/锁/`RETURNING`、outbox、审计和稳定游标继续使用显式 SQL。
- 当前没有引入 `mybatis-plus-generator`、dynamic-datasource、逻辑删除或通用自动填充运行时。
  代码生成属于后续 Project Initializer 设计，不进入应用运行时依赖。
- 全局 `IdType.AUTO` 保留 PostgreSQL `DEFAULT uuidv7()` 的数据库生成与回填语义；不使用
  `ASSIGN_ID` / `ASSIGN_UUID`。tenant interceptor 当前不启用，显式 tenant 条件仍是必须规则。

完整边界、兼容原型与迁移方式见
[ADR-0028](decisions/0028-mybatis-plus-infrastructure-baseline.md)。

## M2 取舍

> 本节保留 2026-07-22 的 M2 历史语境，不是当前依赖清单。其“不引入 MyBatis-Plus”选择已经
> 由 ADR-0028 取代；不引入 dynamic-datasource 和竞品公共包的结论仍有效。

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

## M4.3 生产可观测性切片取舍

- 两个可执行发行物直接引入 Boot BOM 管理的 Micrometer Prometheus registry；可复用安全 starter 仍只依赖 Micrometer Core，不强迫所有消费方选择 Prometheus。
- Prometheus Java client 由 registry 传递引入，不在 Ainer 代码中直接依赖其 API；商业发布前仍需由 SBOM 和许可证扫描复核完整传递依赖。
- 指标访问复用 Spring Security JWT 与 OAuth2 Client Credentials，不引入监控厂商 SDK、自研静态 Token 或新的会话组件。

## Ainer Admin 契约切片取舍

- OpenAPI Generator 只在显式 `ainer-admin-sdk` Maven profile 中运行，不进入服务器运行时依赖。
- 生成物写入模块 `target/`，Ainer Boot 不提交派生 TypeScript；唯一前端源码仍由 Ainer Studio
  的 `templates/ainer-admin` 维护。
- SDK 只生成 Ainer JSON API。Authorization Code + PKCE、OIDC discovery 和 RP-Initiated
  Logout 继续交给标准前端协议库，避免生成并维护自制 OAuth 实现。
