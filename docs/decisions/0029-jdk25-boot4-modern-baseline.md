# ADR-0029：JDK 25 / Spring Boot 4 现代化基线

- 状态：Proposed
- 日期：2026-07-30
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无

## 背景

Ainer Boot 已经不是“JDK 8 写法运行在 JDK 25 上”。它采用 Jackson 3、Spring Security 7、Boot 4
OAuth2 Starter、MyBatis-Plus Boot 4 Starter；业务代码大量使用 `record`、模式匹配、switch
expression、`getFirst()/getLast()`；AI 流式任务已经使用虚拟线程；并已通过 ADR-0027 明确锁定
JDK 25 生产基线、禁用 Java preview features。

但截至 2026-07-30 的审查，JDK 25 与 Spring Boot 4 的价值尚未沉淀成脚手架默认能力。当前的“现代
化”更多体现在语法使用上，而非脚手架契约。以下是审查形成时、后续实现开始前的历史快照：

- 当时 `ainer-starter-web` 仍使用已废弃的兼容坐标 `spring-boot-starter-web`
  （`ainer-framework/ainer-starter-web/pom.xml:28`），而非 Boot 4 聚焦的
  `spring-boot-starter-webmvc`；Web 测试依赖也仍为手工拼装。该项随后已在当前工作树改为
  `spring-boot-starter-webmvc` 与 `spring-boot-starter-webmvc-test`，不得把本条历史快照误读为
  当前实现状态。
- 出站 HTTP 没有统一基线。4 处代码直接调用 `RestClient.create()` 或
  `RestClient.builder()`（identity directory、resource server 自动配置、authorization server
  的 identity 事件与 provisioning 通知发布器），绕开 Boot 管理的统一超时、TLS、观测、定制器与
  SSRF 防护；全仓 0 处 `@HttpExchange`。
- 22 个 `@ConfigurationProperties` 类中 0 个带类级 `@Validated`，全部属性类都未形成统一校验
  基线；多数仍是可变 JavaBean，构造器绑定、嵌套 `@Valid` 和
  `spring-configuration-metadata.json` 未形成契约。
- 全仓 0 处 `@NullMarked`，空安全没有基线，Spring Framework 7 的 JSpecify 采用不会自动约束
  Ainer 自身代码。
- AI 流式执行器 `aiStreamExecutor`（`AiRuntimeModuleConfiguration.java:83`）是 `@Bean` 但没有
  标记 `defaultCandidate = false`，可能影响 Boot 通用任务执行器自动配置；虚拟线程也尚未做平台
  线程/虚拟线程双模式压测。

Ainer Boot 的目标是成为可被 `xq-platform-next`（服务于 `xq-shop` 2.0 与 `xq-zhiwu`）消费的脚手架。
如果这些能力只在审查记录里、靠后续业务开发人员“记得使用”，那么 `xq-platform-next` 一诞生就会缺
少它们。真正的优势应体现在脚手架默认能力上，而不是更多新语法。

## 决策驱动因素

- 脚手架能力必须默认存在，不依赖下游业务开发人员主动选择或记忆；
- `xq-platform-next`、`xq-shop` 2.0 与 `xq-zhiwu` 应从项目诞生即获得统一出站 HTTP、配置契约、
  空安全基线和受控虚拟线程；
- Boot 4 最值得立即兑现的能力（`webmvc` starter、统一 `RestClient.Builder`、构造器绑定配置、
  JSpecify 空安全、虚拟线程治理）必须在创建 `xq-platform-next` 之前落地为 T0 技术工作包；
- JDK 25 的更大价值在运行时与诊断（连续 JFR、AOT Cache、依赖内部 API 检测），而不仅是业务 API；
- 保持生产基线稳定：preview 特性、incubator API、WebFlux/JPMS/GraalVM 默认迁移不进入生产基线。

## 备选方案

### 方案 A：不设统一基线，继续靠语法现代化

继续鼓励使用 `record`、模式匹配等新语法，但不建立出站 HTTP、配置契约、空安全和虚拟线程的脚手架
默认。这是当前状态。它会工作，但 Ainer 的价值停留在“写法新”，`xq-platform-next` 仍要各自重新发
明这些能力，且不同模块会出现分叉。拒绝作为下一阶段默认。

### 方案 B：立即全局开启虚拟线程、全面重构成响应式或 JPMS 模块化

直接设置 `spring.threads.virtual.enabled=true` 作为默认，或为追求“现代化”迁移到 WebFlux、JPMS、
默认 GraalVM Native Image。这类改动收益未经验证、回退成本高，且会与 ADR-0027 的稳定基线目标冲
突。拒绝作为本阶段默认；其中虚拟线程改为先双模式压测通过再默认。

### 方案 C：分阶段确立“JDK 25 / Boot 4 现代化基线”

把下一阶段明确命名为“JDK 25 / Boot 4 现代化基线”，按 T0（创建 `xq-platform-next` 前必须完成）
和 T1（Scaffold Ready 前完成）分层落地，同时明确不进入生产基线的清单。所有能力默认进入 starter
与门禁，而非依赖记忆。采用。

## 决策

下一阶段命名为“JDK 25 / Boot 4 现代化基线”。所有 T0 项在创建 `xq-platform-next` 之前完成；T1
项在 Scaffold Ready 之前完成。

`T0/T1` 只表示本 ADR 内部的技术工作包，必须映射到
[`Ainer Boot 产品定位、竞品能力矩阵与路线图`](../design/ainer-scaffold-design.md)
的全局 `P0–P5`，不得作为第二套产品阶段。实现进度和动态验收结果只维护在
[`project-status.md`](../project-status.md)；本 ADR 只保留决策形成时的验证记录与稳定验收要求。

### T0：创建 `xq-platform-next` 前必须完成

1. **Boot 4 模块化对齐。** `ainer-starter-web` 的 `spring-boot-starter-web` 替换为
   `spring-boot-starter-webmvc`；测试依赖改为采用 Boot 4 聚焦的测试 Starter，停止各模块手工拼装
   测试依赖。对齐 Boot 4 迁移指南，避免继续使用废弃坐标。
2. **统一出站 HTTP 能力。** 业务与服务间出站调用统一注入 Boot 管理的 `RestClient.Builder`，由
   starter 集中配置超时、TLS、重定向、观测与 SSRF 防护；稳定的内部服务契约采用 `@HttpExchange`
   声明式客户端，并以 HTTP Service Client Group 管理 base URL、超时、TLS 与观测。AI SSE 流式调
   用保留 JDK `HttpClient`，因其确需流式解析与中断控制；该例外必须在 starter 中显式记录。
3. **配置即契约。** 逐步将 `@ConfigurationProperties` 改为构造器绑定的不可变配置，启用 Jakarta
   Validation 与嵌套 `@Valid`；所有公开 starter 生成 `spring-configuration-metadata.json`。密
   钥、密码等敏感配置不得简单改写成会自动输出 `toString()` 的 record。
4. **空安全基线。** 从 `ainer-core`、安全模块与公共 API 开始采用包级 `@NullMarked`，真实可空位
   置显式标注 `@Nullable`；CI 接入 NullAway 进行静态检查。Spring Framework 7 的 JSpecify 不会
   自动检查 Ainer 自身代码。
5. **修正虚拟线程接入。** `aiStreamExecutor` 等 Ainer 自定义 `ExecutorService` 标记为
   `@Bean(defaultCandidate = false)`，避免影响 Boot 通用任务执行器自动配置；随后增加平台线程/虚
   拟线程双模式测试矩阵（MVC + JDBC 吞吐与 P95/P99、Hikari 连接等待、AI Provider 并发上限、
   MDC/SecurityContext/Trace 传播、SSE 中断与优雅停机、JFR 虚拟线程诊断）。双模式通过后，再让新
   生成的阻塞式 MVC 项目默认 `spring.threads.virtual.enabled=true`。虚拟线程提高等待型并发，不
   增加数据库连接与外部服务容量。

### T1：Scaffold Ready 前完成

6. 建立 `ainer-starter-observability`：`ObservationRegistry`、可选 OTLP、结构化日志、
   requestId/traceId 关联。
7. 建立 `ainer-test-support`：`RestTestClient`、Testcontainers `@ServiceConnection`、PostgreSQL
   公共测试基座。
8. 为 Jackson 3 设置最大嵌套深度、字符串长度等读取限制，并增加序列化契约测试。
9. 明确错误协议是继续使用现有 `ApiResponse`，还是采用带 `code`、`requestId` 扩展的
   `ProblemDetail`。
10. 仅在旧客户端与新客户端必须同时维持不同契约时才启用 Spring 7 API Versioning；“产品 2.0”不等
    于“API v2”。

### JDK 25 运行时与诊断基线

11. 默认提供有容量上限的连续 JFR 录制。
12. 建立 JDK AOT Cache 冷启动实验，比较启动时间、就绪时间与 RSS。
13. 保持 G1 为默认 GC；ZGC、Compact Object Headers 仅作为压测 Profile。
14. CI 增加 `jdeps --jdk-internals` 与 `jdeprscan --for-removal`，禁止依赖 JDK 内部 API，禁止常
    规使用 `--add-opens`。

### 不进入生产基线

15. Structured Concurrency、Primitive Patterns 等 Java preview 特性。
16. Vector API 等 incubator API。
17. 用 `ScopedValue` 隐藏租户、用户或授权上下文。
18. 为“现代化”迁移到 WebFlux、JPMS 或默认 GraalVM Native Image。

## 后果

### 正面

- `xq-platform-next`、`xq-shop` 2.0 与 `xq-zhiwu` 从诞生即获得统一出站 HTTP、配置契约、空安全
  基线与受控虚拟线程，不依赖业务开发人员主动记忆；
- Boot 4 最值得兑现的能力在 starter 与门禁层面落地，分叉与重复造轮子的风险下降；
- 虚拟线程在双模式验证通过后才默认开启，避免误以为它同时扩容数据库与外部服务；
- JDK 25 的运行时与诊断价值通过连续 JFR、AOT Cache 实验与内部 API 门禁兑现；
- 明确的“不进入生产基线”清单防止 preview/incubator 能力污染稳定基线。

### 负面与风险

- T0/T1 是一批跨 starter 的改造，需在稳定基线上分批完成，短期增加构建与评审成本；
- 统一 `RestClient.Builder` 与 `@HttpExchange` 需要为现有 4 处直接构造点设计迁移与回退；
- 构造器绑定配置的迁移需要逐个核对敏感字段，避免泄露风险；
- 空安全基线引入 NullAway 后，既有代码可能出现批量告警，需要分模块灰度；
- 虚拟线程双模式压测需要可重复的性能基线与真实 PostgreSQL/AI Provider 环境，否则结论不可靠。

## 安全、数据与隐私

- 统一出站 HTTP 必须在 starter 层提供 SSRF 防护、TLS 与超时默认值，禁止绕过；AI SSE 例外必须显
  式记录，不得成为通用后门。
- 配置契约改造不得把密钥、密码、token 等敏感字段改写成会自动输出 `toString()` 的 record，迁移
  时必须逐项审查。
- `ScopedValue` 不得用于隐藏租户、用户或授权上下文；身份、tenant 与授权边界继续由现有受验证的
  身份上下文、显式 tenant 条件与成员关系门禁承担。
- 空安全基线不改变现有身份、tenant、数据库、秘密或审计边界；`@NullMarked` 只约束可空性契约。
- 本 ADR 不改变 ADR-0020 的数据所有权与 ADR-0028 的持久化边界。

## 运维与迁移

1. T0 按顺序落地：先 Boot 4 模块化对齐与统一出站 HTTP（starter 层），再配置契约与空安全基线，
   最后修正虚拟线程接入并执行双模式压测。
2. 每完成一项 T0，把动态验证记录更新到 `project-status.md`，并把能力固化为 starter 默认与架构/CI
   门禁，而非仅保留文档约定。
3. 虚拟线程默认开启仅在双模式压测通过、并明确数据库连接池与外部服务容量不随之扩容后，才作用于
   新生成的阻塞式 MVC 项目；存量项目不自动翻转。
4. JDK AOT Cache 与 ZGC/Compact Object Headers 仅作为实验或压测 Profile，不修改生产启动脚本与
   默认 GC。
5. 回退策略：任一 T0 改造若引发回归，优先回退该 starter 变更并保留原行为，不在生产依赖树中并
   存两套基线；preview/incubator 能力一律不进入门禁，无需回退窗口。

## 验证记录

2026-07-30 完成审查与路线设计；审查形成时尚未修改项目文件。以下内容是当时的历史快照：

- `spring-boot-starter-web`（废弃兼容坐标）当时位于
  `ainer-framework/ainer-starter-web/pom.xml:28`；
- 4 处 `RestClient.create()` / `RestClient.builder()` 直接构造，0 处 `@HttpExchange`；
- 22 个 `@ConfigurationProperties` 中 0 个带类级 `@Validated`；
- 全仓 0 处 `@NullMarked`；
- `aiStreamExecutor` 位于 `AiRuntimeModuleConfiguration.java:83`，未标记
  `defaultCandidate = false`。

截至 2026-07-31，当前工作树已把 `ainer-starter-web` 切换为
`spring-boot-starter-webmvc`，并以 `spring-boot-starter-webmvc-test` 替代该模块原先手工拼装
的测试依赖，完成 T0 第 1 项的 Web Starter 实现范围。该实现进度不改变本 ADR 的 `Proposed`
状态；当前工作树的最终 Maven 4 验证状态以 `project-status.md` 为准。

以下为计划中验证，完成后追加记录：

- T0 各项落地后的 starter 自动配置测试、集成测试与架构/CI 门禁结果；
- 虚拟线程平台线程/虚拟线程双模式压测矩阵（吞吐、P95/P99、Hikari 等待、MDC/SecurityContext/Trace
  传播、SSE 中断与优雅停机、JFR 诊断）；
- JDK AOT Cache 冷启动实验（启动时间、就绪时间、RSS）；
- CI `jdeps --jdk-internals` 与 `jdeprscan --for-removal` 门禁结果。

## 参考

- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot RestClient](https://docs.spring.io/spring-boot/reference/io/rest-client.html)
- [Spring Boot Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)
- [Spring Boot AOT Cache](https://docs.spring.io/spring-boot/reference/packaging/aot-cache.html)
- [Spring Framework Null-safety](https://docs.spring.io/spring-framework/reference/core/null-safety.html)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [ADR-0027：保留 JDK 25 生产基线并跟踪 JDK 27](0027-keep-jdk-25-production-baseline.md)
- [ADR-0028：MyBatis-Plus 基础设施增强基线](0028-mybatis-plus-infrastructure-baseline.md)
