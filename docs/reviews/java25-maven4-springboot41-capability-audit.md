# Ainer Boot — Java 25 / Maven 4 / Spring Boot 4.1 Capability Audit

> 文档类型：研究/审计快照 · 状态：生效 · 日期：2026-08-08 · 适用版本：`0.1.x`  
> 范围：只读审计；本轮不修改业务代码、不执行大规模重构。  
> 证据基准：仓库源码与构建配置（排除 `target/`）；本地 `./mvnw --version`、`java -version`、effective POM 与 Maven validate 警告。

---

## 1. Executive Summary

```text
Java 25 利用程度：      GOOD
Maven 4 利用程度：      PARTIAL
Spring Boot 4.1 利用程度： GOOD

整体判断：              GOOD
```

**直接回答核心问题：**

`ainer-boot` **不是**“把 Java 8/11 代码跑在 JDK 25 上”。领域模型与 API 层已经大量使用 `record`、sealed hierarchy、pattern matching 与 switch expression；AI SSE 路径已显式使用虚拟线程；构建工具已锁定 Maven 4.0.0-rc-6，并用内建 Consumer POM 取代 Flatten；运行时 starter 已切到 Boot 4 聚焦坐标（`webmvc` / `restclient` / OAuth2 resource server）。

同时，它也**不是**“Maven 4-native + Boot 4.1 能力全开”。Maven 源模型仍完整停留在 POM 4.0.0（ADR-0026 有意决策）；reactor 内 BOM import 仍产生 Maven 4 model WARNING；平台级虚拟线程默认、可观测性 starter、Testcontainers `@ServiceConnection`、layered JAR / build-info / 生产容器基线等脚手架能力尚未闭环。当前主要缺口是**脚手架默认契约与构建模型卫生**，而不是业务代码仍大量停留在 Java 8 写法。

**与 ADR-0029 的关系：** ADR-0029（Proposed）已正确指出“现代化应沉淀为脚手架默认能力，而非更多新语法”。本审计验证该判断仍然成立，并补充 Maven 4 模型层与跨层重复基础设施证据。本轮不实施变更。

---

## 2. Actual Technology Baseline

从仓库实际配置与本地/CI 命令验证，而不是 README 叙事。

```text
Actual Baseline
Java:
  toolchain/compiler release = 25 (maven.compiler.release=${java.version})
  enforcer                 = [25,26)
  local runtime            = OpenJDK 25.0.2 (BellSoft Liberica / sdkman)
  CI runtime               = Temurin 25 (actions/setup-java)
  source/target 冲突       = 未发现（仅使用 <release>25</release>）
  --enable-preview         = 未启用
  IDE 设置                 = 仓库无 .vscode/.idea 编译覆盖；正式基线以 Maven Enforcer + Wrapper 为准

Maven:
  Wrapper                  = 3.3.4 only-script
  distribution             = Apache Maven 4.0.0-rc-6 (+ distributionSha256Sum)
  enforcer                 = [4.0.0-rc-6,4.1.0)
  local ./mvnw --version   = Apache Maven 4.0.0-rc-6
  consumer gate            = Maven 3.9.16（仅 scripts/verify-maven-consumers.sh）

POM Model:
  modelVersion             = 4.0.0（19/19 pom.xml，含 initializer 模板）
  modelVersion 4.1.0       = 0
  reactor modules          = <modules>（非 <subprojects>）
  CI-friendly version      = ${revision} + Maven 4 Consumer POM
  flatten-maven-plugin     = 已移除

Spring Boot:
  spring.boot.version      = 4.1.0（根 pom 与 ainer-dependencies）
  Spring Framework         = 7.0.8（来自 spring-boot-dependencies-4.1.0.pom）
  子模块覆盖 Boot 版本     = 未发现
  Boot-managed 依赖手工 version override = 未发现（仅 mybatis-plus/testcontainers/archunit/webauthn4j 有意 pin）

Build Plugins:
  maven-compiler-plugin    = 3.14.0（release=25, parameters=true, 显式 annotationProcessorPaths）
  maven-surefire-plugin    = 3.5.3（useModulePath=false）
  maven-failsafe-plugin    = 未配置（集成测试走 Surefire + Testcontainers）
  spring-boot-maven-plugin = 4.1.0（repackage；无 layers/build-info）
  maven-enforcer-plugin    = 3.6.2（根 + parentless BOM）

CI Runtime:
  .github/workflows/ci.yml      = JDK 25 Temurin + ./mvnw clean verify + consumer gate
  .github/workflows/release.yml = JDK 25 Temurin + ./mvnw -Drevision=... -Prelease deploy

Container Runtime:
  Dockerfile / OCI image   = 仓库内无
  systemd 开发单元         = java -jar（无过时 --add-opens / preview JVM 参数）
```

### 2.1 Baseline correctness findings

#### Finding B-1 — 编译/运行/CI Java 基线一致

```text
Finding: Java 25 在 POM、Enforcer、本地 runtime 与 CI 一致
Category: P0 baseline (positive)
Location: pom.xml properties/java.version + enforcer; .github/workflows/ci.yml; .github/workflows/release.yml
Current implementation: maven.compiler.release=25；requireJavaVersion [25,26)；CI java-version: "25"
Modern capability: JDK 25 LTS production baseline
Why current implementation exists: ADR-0027 / ADR-0029 / AGENTS.md 锁定
Is it now redundant: N/A（正确基线）
Recommended action: NO CHANGE — 保持 Enforcer 与 Wrapper 双锁
Benefit: 可重复构建
Risk: 无
Migration difficulty: N/A
Required tests: ./mvnw clean verify 已作为门禁
Priority: NO CHANGE
```

#### Finding B-2 — Maven 4 生产者锁定，Maven 3 仅消费者门禁

```text
Finding: 生产者已锁定 Maven 4.0.0-rc-6；Maven 3.9.16 仅用于下游消费验证
Category: P0 baseline (positive, with RC risk)
Location: .mvn/wrapper/maven-wrapper.properties; pom.xml enforcer; scripts/verify-maven-consumers.sh; ci.yml
Current implementation: distributionUrl → 4.0.0-rc-6；requireMavenVersion [4.0.0-rc-6,4.1.0)；CI 另装 Maven 3.9.16 只跑 consumer 脚本
Modern capability: Maven 4 producer + Consumer POM
Why current implementation exists: ADR-0026
Is it now redundant: 否
Recommended action: KEEP；记录 RC 风险——Maven 4 仍是 RC，非 GA；Enforcer 上界会拒绝 Maven 4.1.0 工具，直到显式放宽
Benefit: 防止 Maven 3 悄悄构建 reactor
Risk: RC 行为变化；未来工具升级需单独变更 Enforcer 范围
Migration difficulty: N/A
Required tests: verify-maven-consumers.sh
Priority: NO CHANGE（风险事实，非本轮重构）
```

#### Finding B-3 — Reactor 内 BOM import 产生 Maven 4 model WARNING

```text
Finding: Maven 4 报告 “BOM imports from within reactor should be avoided”
Category: P0 — Baseline / build model hygiene
Location: 根 pom.xml dependencyManagement（约 L57）；validate 输出指向 ainer-boot:${revision}
Current implementation: 根工程 import 同 reactor 的 ainer-dependencies（packaging pom）
Modern capability: Maven 4 严格模型诊断
Why current implementation exists: parentless BOM 避免 model cycle；根工程再 import（ADR-0026 已记录“同 reactor BOM 导入告警在后续模型重构中处理”）
Is it now redundant: 告警本身不是冗余；当前结构是过渡态
Recommended action: 列入 Phase A；在不破坏 parentless 自包含 BOM 与 Maven 3.9+ consumer 的前提下消除 unexplained warning
Benefit: Maven build model → zero unexplained warnings；降低未来 Maven 拒绝 malformed project 的风险
Risk: 错误拆分可能破坏 Consumer POM / 外部消费
Migration difficulty: Medium（需独立 ADR/原型）
Required tests: ./mvnw validate；verify-maven-consumers.sh；artifact:compare
Priority: P0
```

#### Finding B-4 — 无 Dockerfile / 容器镜像基线

```text
Finding: 仓库无 Dockerfile；生产容器能力未进入代码库
Category: P3 / scaffold gap（非版本漂移）
Location: 全仓 0 Dockerfile；ops/dev 仅 systemd + java -jar
Current implementation: 应用通过 spring-boot-maven-plugin repackage；无 layered JAR / image metadata
Modern capability: Boot layered JAR、build-info、Actuator probes
Why current implementation exists: 当前优先开发环境部署与 GitHub Packages 制品发布
Is it now redundant: N/A
Recommended action: 不在本审计中强行补容器；Scaffold Ready 前作为生产硬化候选项
Benefit: 未来部署一致性
Risk: 过早引入可能与当前私有发布路径重复
Migration difficulty: Medium
Required tests: 镜像构建 smoke + health probes
Priority: P3
```

---

## 3. Java 25 Audit

统计基线：约 **409** 个 `.java` 源文件；约 **92** 个文件含 `record`；**5** 个文件含 `sealed`；**0** 处 `ThreadPoolTaskExecutor` / `newFixedThreadPool` / `newCachedThreadPool`；**0** 处生产 `@Async`；**0** 处 `--enable-preview` / `ScopedValue` / `StructuredTaskScope`。

### 3.1 Already well used

| 能力 | 证据 | 判断 |
|---|---|---|
| `record` 作为 API/Command/Result/VO | `ChatCompletionRequest`、`CompletionResult`、`Workspace`、`AuthorizationDecision`、`AuthenticatedPrincipal`、`ApiResponse` 等；Request/Command/Result 命名类型未发现经典 getter DTO 例外 | 已饱和采用 |
| sealed + nested record | `Scope`（`Global/Workspace/Resource`）、`Requester`、`PrincipalSubjectRef`、`Challenge`、`DecisionObligation` | 领域有限集合已编码进类型系统 |
| instanceof / switch pattern | `AuthorizationService`、`SecurityContextAuthenticatedPrincipalResolver`、`ManifestReader`、`ReferenceTokenProfileResolver` | 解析与授权路径已现代化 |
| switch expression | `GlobalExceptionHandler.standardCode`、`AiGatewayApplicationService.providerError`、`InitializerCli.main`、`AuthorizationService` GrantPath 分发 | 传统 switch+break 赋值模式基本消失 |
| Virtual threads（有意隔离） | `AiRuntimeModuleConfiguration.aiStreamExecutor()`：`Thread.ofVirtual()` + `newThreadPerTaskExecutor` + `@Bean(defaultCandidate=false)` | 正确、克制 |
| Sequenced collection API | Passkey / audit 路径使用 `getFirst()`/`getLast()` | 已部分采用 |
| Compact / validating constructors | `CompletionResult`、`ModelInvocation`、`AuthorizationDecision`、`AuthenticatedPrincipal` | 用类型约束表达不变量 |
| Preview 禁用 | POM 无 `--enable-preview`；ADR-0027/0029 明确禁止生产 preview | 正确 |

**代表性证据 — sealed domain scope：**

```13:37:ainer-module-authorization/src/main/java/dev/ainer/authorization/domain/Scope.java
public sealed interface Scope permits Scope.Global, Scope.Workspace, Scope.Resource {
    boolean covers(ResourceRef resource);

    record Global() implements Scope { /* ... */ }

    record Workspace(UUID workspaceId) implements Scope {
        public Workspace {
            Objects.requireNonNull(workspaceId, "workspaceId");
        }
        // ...
    }
```

**代表性证据 — 虚拟线程隔离 executor：**

```86:92:ainer-module-ai-runtime/src/main/java/dev/ainer/module/ai/AiRuntimeModuleConfiguration.java
    // 仅用于 AI SSE 流式任务，按名显式注入；标记 defaultCandidate=false 避免被当作 Boot 通用
    // TaskExecutor/ExecutorService 默认候选，从而不影响 MVC 异步、@Async 与虚拟线程自动配置（ADR-0029 第 5 项）。
    @Bean(defaultCandidate = false, destroyMethod = "close")
    ExecutorService aiStreamExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("ainer-ai-stream-", 0).factory());
    }
```

### 3.2 Underused

#### Finding J-1 — 平台级虚拟线程尚未默认开启

```text
Finding: spring.threads.virtual.enabled 未配置；MVC/JDBC 仍走平台线程
Category: P2 — High-value modernization（有门禁）
Location: ainer-server / ainer-authorization-server application.yaml（无该项）；ADR-0029 P0-5；project-status.md
Current implementation: 仅 AI SSE 专用虚拟线程 executor；Tomcat 请求线程未切 VT
Modern capability: Spring Boot virtual-thread integration
Why current implementation exists: ADR-0029 要求双模式压测（MVC+JDBC P95、Hikari 等待、MDC/SecurityContext 传播）通过后再默认
Is it now redundant: 否——未压测前保持关闭是正确的
Recommended action: NEEDS BENCHMARK；通过后再考虑新 MVC 项目默认开启
Benefit: blocking IO（JDBC/HTTP）可扩展性与更简单并发模型
Risk: 连接池饱和、MDC/SecurityContext 传播缺口、调度器行为变化
Migration difficulty: Medium（主要是压测与回归，不是改代码）
Required tests: 双模式压测矩阵 + 现有集成测试
Priority: P2（门禁未过前 NO CHANGE）
```

#### Finding J-2 — AI 流式路径上下文传播未显式处理

```text
Finding: aiStreamExecutor 提交的任务未复制 MDC / SecurityContext
Category: P1/P2 交叉（正确性风险）
Location: AiGatewayApplicationService.stream() + RequestIdFilter（MDC）+ SecurityContextHolder 使用者
Current implementation: RequestIdFilter 在 Servlet 线程 put MDC；流任务在虚拟线程执行，主要依赖已捕获的 Command/principal
Modern capability: 显式 context 参数、Micrometer Context Propagation、或任务包装；非 ScopedValue 全局隐式状态
Why current implementation exists: SSE offload 需要脱离请求线程读 provider 流
Is it now redundant: 否；但传播缺口是真实风险
Recommended action: 验证流路径是否读取 MDC/SecurityContext；若需要 requestId 审计关联，显式捕获后传入，不要 ThreadLocal→ScopedValue 盲迁
Benefit: 可观测性与审计一致性
Risk: 过度包装引入复杂 async infrastructure
Migration difficulty: Low–Medium
Required tests: 流式路径 MDC/requestId 断言；取消/错误路径
Priority: P2
```

#### Finding J-3 — Pattern matching 仍有小幅清理空间

```text
Finding: GlobalExceptionHandler.handleBinding 混用 pattern 与旧式 cast
Category: P3 — Opportunistic cleanup
Location: ainer-framework/ainer-starter-web/.../GlobalExceptionHandler.java:35-39
Current implementation: instanceof MethodArgumentNotValidException methodArgument + ((BindException) exception)...
Modern capability: switch pattern / 双分支都用 binding pattern
Why current implementation exists: 历史写法演进中途
Is it now redundant: 部分样板可删
Recommended action: 局部改写为 switch；不要大范围“模式匹配化”
Benefit: 清晰度
Risk: 极低
Migration difficulty: Low
Required tests: 现有 web error 测试
Priority: P3
```

#### Finding J-4 — AI SSE 事件可用 sealed 聚合（可选）

```text
Finding: AiStreamDeltaEvent / AiStreamUsageEvent / AiStreamErrorEvent 为平行 record
Category: P3
Location: ainer-module-ai-runtime/.../gateway/api/AiStream*Event.java + controller 字符串 eventName 分发
Current implementation: 三个独立 record + 事件名字符串
Modern capability: sealed interface AiStreamEvent permits ...
Why current implementation exists: SSE 协议本身用 event name 区分
Is it now redundant: 否；类型密封收益有限，因边界在 wire format
Recommended action: 仅当内部处理路径需要穷尽匹配时再引入；不要为语法升级而改协议层
Benefit: 内部 dispatch 穷尽性
Risk: 过度建模
Migration difficulty: Low
Required tests: SSE 合约测试
Priority: P3 / 接近 NO CHANGE
```

### 3.3 Redundant legacy implementations

**未发现**典型 Java 8 时代线程池框架、TransmittableThreadLocal、自研 Tenant Context Holder、经典可变 DTO 海量堆叠。

自定义上下文仅见：

| 机制 | 位置 | 判断 |
|---|---|---|
| MDC requestId | `RequestIdFilter` | KEEP — Ainer API 契约（`X-Request-Id` + `ApiResponse.requestId`） |
| SecurityContextHolder | Spring Security 标准用法 | KEEP — 禁止用 ScopedValue 替换身份上下文（ADR-0029） |
| 无自定义 Tenant/User Holder | — | GOOD — subject 来自 JWT/`AuthenticatedPrincipal` |

### 3.4 Not worth adopting

| 能力 | 理由 |
|---|---|
| 全体 DTO → record 机械转换 | API/领域已是 record；MyBatis `*Row`、`AinerUserDetails`、含密钥 Properties **不应** record |
| ScopedValue 替换 SecurityContext/MDC | 与框架生命周期冲突；ADR 明确禁止用于隐藏授权上下文 |
| 全局虚拟线程无压测开启 | ADR-0029 已拒绝 |
| JPMS `module-info` | 无业务需求；增加发布与测试成本 |
| 把所有 enum 改 sealed exception 树 | `ProviderFailure.Kind` 等当前规模下 enum+switch 更清晰 |

**ConfigurationProperties 特别说明（纠正常见误判）：**

当前 `@ConfigurationProperties` 已是**构造器绑定不可变类**（无 setter；`project-status.md` P0-3 已记录）。故意不用 record 的原因是含密钥/密码字段时避免 `toString()` 泄露。这是 **NO CHANGE / GOOD DESIGN**，不是“落后于 Java 25”。

### 3.5 Preview / watch list

```text
WATCH — Structured Concurrency (StructuredTaskScope)
WATCH — ScopedValue（仅非身份上下文的受限场景；默认不采用）
WATCH — 其他 Java 25 preview / incubator API

ADOPT — 无（项目无 preview-feature policy 允许生产采用）
```

仓库未意外开启 `--enable-preview`。

### 3.6 Record 候选分级（抽样）

| 类型 | 分级 | 理由 |
|---|---|---|
| `*Request` / `*Response` / `*Command` / `*Result` / domain VO | ALREADY RECORD | 已采用 |
| `AuthorizationDecision`、`Scope.*`、`Requester.*` | ALREADY RECORD (+ sealed) | 已采用 |
| `*Row`（MyBatis） | NOT SUITABLE FOR RECORD | 可变映射 + setter |
| `AinerUserDetails` | NOT SUITABLE FOR RECORD | `CredentialsContainer.eraseCredentials()` |
| `*Properties`（含密钥） | NOT SUITABLE FOR RECORD | 不可变 class 已足够；record toString 风险 |
| `BusinessException` / `ProviderFailure` | NOT SUITABLE FOR RECORD | 异常层次 |
| `ProjectTree` | NO CHANGE | 行为型不可变容器，class 更自然 |

---

## 4. Maven 4 Audit

### 4.1 Already Maven-4-native

| 能力 | 证据 |
|---|---|
| Maven 4 Wrapper 锁定 + SHA | `.mvn/wrapper/maven-wrapper.properties` → `4.0.0-rc-6` |
| Enforcer 拒绝 Maven 3 生产者 | 根 POM + `ainer-dependencies` |
| 内建 Consumer POM 取代 Flatten | `.mvn/maven-user.properties`：`maven.consumer.pom=true`、`flatten=false`；无 flatten-maven-plugin |
| `${revision}` CI-friendly 版本 | 根与 BOM；release.yml `-Drevision=` |
| 下游双工具消费门禁 | `scripts/verify-maven-consumers.sh`（M3.9 + M4） |
| JDK 23+ 注解处理器显式声明 | compiler `annotationProcessorPaths` → `spring-boot-configuration-processor` |

这是 **“Maven 4 工具 + POM 4.0 源模型 + 原生 Consumer POM”** 混合基线，与 ADR-0026 一致，不是疏漏。

### 4.2 Still Maven-3-style

| 项 | 现状 | 说明 |
|---|---|---|
| `modelVersion` | 全部 4.0.0 | ADR-0026：本阶段有意不迁 4.1 |
| `<modules>` | 根 + `ainer-framework` | 未用 `<subprojects>` |
| parent 坐标 | 16 个子 POM 全量重复 `groupId/artifactId/version` | 未用 POM 4.1 父坐标推导 |
| BOM packaging | `pom` + import | 未用 `packaging=bom` |
| lifecycle | 传统 `<phase>` | 未用 `before:`/`after:`/`installAtEnd` |
| plugin 版本显式锁定 | 根 `pluginManagement` | 有意可重复；部分可能与 Maven 4 defaults 重叠 |

**可删除重复的粗计量（若未来迁 POM 4.1 且验证通过）：**

```text
可删除重复 parent version 声明：     ~16（子 POM <parent><version>）
可删除 BOM dependencyManagement
  中同版本 ${revision}：             ~12（ainer 内部制品坐标）
可删除重复 <revision> 属性定义：     1（根与 parentless BOM 各一份；BOM 自包含可能仍需保留）
可删除重复 parent groupId：          视推导规则，最多 ~16
```

**注意：** parentless BOM **不能**简单靠根工程推导，否则会失去自包含发布模型（ADR-0026 硬约束）。因此“删光所有重复”不是合法目标。

### 4.3 Redundant build workarounds

| 项 | CURRENT PURPOSE | MAVEN 4 REPLACEMENT | CAN REMOVE? | RISKS |
|---|---|---|---|---|
| flatten-maven-plugin | （历史）展开 `${revision}` | 内建 Consumer POM | **已移除** | — |
| `maven.consumer.pom.flatten=false` | 固定已验证行为，避免实验性全量展开 | Maven 4 开关本身 | 否 | 改为 true 会显著改变发布 POM |
| `-Drevision` CLI | 发布版本注入 | 仍需要 CI-friendly 属性 | 否 | — |
| parentless BOM 双份 enforcer/distributionManagement | 避免 model cycle + 自包含 | POM 4.1 `packaging=bom` 需验证 | 暂否 | 破坏 consumer |
| 排除 `*-build.pom`（脚本） | Maven 4 双 POM 适配 | 仍需门禁理解双 POM | 否 | 误校验 build POM |
| Maven 3.9 consumer 安装（CI） | 下游兼容 | 不由 Maven 4 替代 | 否 | 丢失 M3 消费者保证 |

### 4.4 Recommended Maven-4 capabilities（后续，非本轮）

1. **消除 reactor BOM import WARNING**（P0）——可与依赖管理拓扑重构一起做。  
2. **独立 ADR 评估 POM 4.1.0**：`subprojects`、parent inference、dependency version inference、`packaging=bom`。收益是减 XML 重复；前提是 Consumer POM + M3.9 消费仍绿。  
3. **审查 Maven 4 defaults** 是否使部分显式 plugin 配置变为噪音（`installAtEnd`/`deployAtEnd` 当前未配置，无需为“用新特性”而添加）。

### 4.5 Not worth changing

| 项 | 理由 |
|---|---|
| 立即全体迁 modelVersion 4.1.0 | ADR-0026 明确 deferred；扩大验证面 |
| 为用 `<subprojects>` 而改名 | 纯语法迁移，无功能收益 |
| 删除 parentless BOM | 会引入 model cycle 或失去自包含 |
| 重新引入 Flatten | 与 Maven 4 Consumer POM 重叠 |
| 用 Maven Toolchains 替代 Enforcer | 当前 Wrapper+Enforcer+CI 已足够冻结 JDK/Maven；toolchains 增加本地配置负担 |

#### Finding M-1 — Enforcer 上界拒绝未来 Maven 4.1.0 工具

```text
Finding: requireMavenVersion [4.0.0-rc-6,4.1.0) 会拒绝 Maven 4.1.0
Category: P0 awareness / follow-up when upgrading tools
Location: pom.xml 与 ainer-dependencies/pom.xml enforcer
Current implementation: 上界开区间到 4.1.0
Modern capability: 版本门禁
Why current implementation exists: 锁定已验证 RC6
Is it now redundant: 否
Recommended action: 升级 Maven 工具版本时同步放宽并全量回归；现在不要“提前放宽”
Benefit: 可重复
Risk: 忘记放宽会导致无法采用 GA
Migration difficulty: Low（改配置）+ High（回归）
Required tests: full verify + consumers
Priority: NO CHANGE now / P0 when upgrading
```

---

## 5. Spring Boot 4.1 Audit

### 5.1 Good use of Boot

| 能力 | 证据 |
|---|---|
| Boot 4 聚焦 starter | `spring-boot-starter-webmvc`、`spring-boot-starter-restclient`、`spring-boot-starter-security-oauth2-resource-server`、`spring-boot-starter-security-oauth2-authorization-server` |
| 注入 Boot 管理的 ObjectMapper | `AinerResourceServerAutoConfiguration`、`AinerSecurityFailureWriter` — 无手工 `@Bean ObjectMapper` |
| Boot 4.1 HTTP client API | `HttpClientSettings` + `ClientHttpRequestFactoryBuilder.detect()` 用于 token introspection |
| Actuator probes | `management.endpoint.health.probes.enabled: true`；暴露 health/info/prometheus |
| OAuth2 Resource Server 标准接线 | `.oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))` + `DefaultBearerTokenResolver` |
| Auto-config 测试 | `ApplicationContextRunner` / `WebApplicationContextRunner` 用于 starter 回归 |
| Graceful shutdown | `server.shutdown: graceful` |

**代表性证据 — Boot 4.1 HTTP client settings：**

```71:84:ainer-framework/ainer-starter-security/src/main/java/dev/ainer/security/autoconfigure/AinerResourceServerAutoConfiguration.java
    public OpaqueTokenIntrospector ainerOnlineTokenIntrospector(
            AinerResourceServerProperties properties,
            RestClient.Builder restClientBuilder) {
        // ...
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(online.getConnectTimeout())
                .withReadTimeout(online.getReadTimeout());
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        RestClient restClient = restClientBuilder
                .requestFactory(requestFactory)
                // ...
                .build();
        return new RestClientOpaqueTokenIntrospector(introspectionUri.toString(), restClient);
    }
```

### 5.2 Custom infrastructure that remains justified

| 组件 | 位置 | 为什么必须保留 |
|---|---|---|
| `ApiResponse` + `GlobalExceptionHandler` | `ainer-core` / `ainer-starter-web` | Ainer HTTP 错误契约；不是 Boot `ProblemDetail` 的无脑替代删除对象（ADR-0029 T1-9 仍 open） |
| `RequestIdFilter` / `RequestIds` | `ainer-starter-web` | `X-Request-Id` + 响应体 requestId |
| `OnlineAccessTokenValidationFilter` | `ainer-starter-security` | 高风险路径 fail-closed introspection（401/503） |
| `RecentStrongAuthenticationFilter` | `ainer-starter-security` | step-up（`amr`/`auth_time`） |
| `SecurityContextAuthenticatedPrincipalResolver` + TokenProfile | `ainer-security` / starter | JWT → 领域 `AuthenticatedPrincipal` |
| `AinerOAuth2AuthorizationJsonMapperFactory` | authorization-server | OAuth2 JDBC 持久化专用 JsonMapper（Security/WebAuthn modules + mixin） |
| `aiProviderHttpClient`（JDK HttpClient） | `AiRuntimeModuleConfiguration` | SSE 流式例外；ADR-0029 文档化 |
| `aiStreamExecutor` | 同上 | 隔离 VT executor，避免污染 Boot 默认执行器 |
| `AinerRateLimiter` / `SubjectRateLimiter` | auth-server / ai-runtime | node-local 限速语义，非假装分布式 |
| `PrometheusEndpointRequestMatcher` | security | 安全模块不依赖 Actuator classpath 的路径匹配 |
| MyBatis-Plus + Flyway starter 组合 | `ainer-starter-persistence` | 平台持久化选择（ADR-0028），非 Boot 重复 |

### 5.3 Custom infrastructure duplicated by Boot

| 组件 | 判断 | 说明 |
|---|---|---|
| `ClientCredentialsServiceTokenProvider` | **部分冗余 / 未接线** | 手工组装 `InMemoryClientRegistrationRepository` + `AuthorizedClientServiceOAuth2AuthorizedClientManager`；生产路径零引用，仅测试使用。可用 Boot OAuth2 Client auto-config 替代或删除直至有真实消费者 |
| 多模块测试依赖四件套 | **样板重复** | 多数模块手写 `spring-boot-test` + `spring-test` + junit + assertj，而非 `spring-boot-starter-test` / `webmvc-test`（仅 `ainer-starter-web` 用了 webmvc-test） |
| `ainer-module-ai-runtime` 显式 `jackson-databind` | **疑似多余依赖声明** | 已由 web starter 传递；需依赖分析后删除 |
| `/api/platform/info` vs `actuator/info` | **弱重叠** | `PlatformInfoController` 暴露 runtimeMode + Java feature；可作为公开 API 保留，或部分改为 `InfoContributor`——**不要误删业务 API** |

**未发现**手工 MeterRegistry、手工 ObjectMapper HTTP bean、手工 Tomcat 定制、手工 Redis、自研 tracing wrapper、自研通用线程池框架等大规模重复。

### 5.4 Boot 4.1 opportunities

| 机会 | 证据缺口 | 优先级 |
|---|---|---|
| 平台虚拟线程默认（压测后） | 无 `spring.threads.virtual.enabled` | P2 |
| 统一出站 HTTP / SSRF 治理 starter | AI HttpClient 仅 `followRedirects(NEVER)`；无集中 allow/deny | P1（安全相关，只建议） |
| `ainer-starter-observability` | 无 Observation/OTel/Tracing；仅有 requestId MDC + 域 Micrometer counters | P1 |
| `ainer-test-support` + `@ServiceConnection` | 集成测试重复 `@Container` + `@DynamicPropertySource` | P1 |
| Jackson 3 读取限制 / customizer | 无 `Jackson2ObjectMapperBuilderCustomizer` 深度限制（ADR-0029 T1-8） | P2 |
| layered JAR + build-info | Boot plugin 仅 repackage | P3 |
| RestTestClient 统一测试 HTTP | 多处 JDK `HttpClient` smoke | P3 |
| `@HttpExchange` service clients | 全仓 0；ADR-0029 已 deferred | NO CHANGE now |

### 5.5 Features irrelevant to ainer-boot

下列 Boot / 生态能力**当前不应为了“用上 4.1”而引入**：

| 特性 | 为什么无关 / 不该现在做 |
|---|---|
| WebFlux / 响应式栈 | 平台基线是 MVC + blocking JDBC/AI IO；ADR-0029 拒绝默认响应式迁移 |
| GraalVM Native Image 默认 | ADR-0029 明确不进入生产基线 |
| Spring Data Redis / Cache 抽象 | 仓库无 Redis 业务需求 |
| R2DBC | PostgreSQL + MyBatis-Plus 已定 |
| GraphQL starter | 无产品需求 |
| Spring Session | 授权走 OAuth2/OIDC + JWT/opaque，不走 servlet session 中心化 |
| 全面 `@HttpExchange` 化 | 现有 client-credentials 场景显式注入更简单；已 deferred |
| Boot DevTools 作为基线 | 开发便利，非平台契约 |
| 多数据源自动配置大礼包 | 单 PostgreSQL 业务库基线 |

---

## 6. Cross-Layer Redundancy

| 交叉层 | 重复/张力 | 结论 |
|---|---|---|
| Java VT vs Spring Boot VT | AI 自建 VT executor；Boot 全局 VT 未开 | **互补，非重复**；全局开关待压测 |
| Java ThreadLocal vs Spring Security | 仅标准 SecurityContext + MDC | **无自研 context framework** |
| Java HttpClient vs Boot RestClient | AI SSE 用 JDK；introspection 用 Boot RestClient+HttpClientSettings | **有意分层** |
| Spring Observation vs 自研 metrics | 域 Counter/Timer/Gauge 存在；无二次 HTTP instrumentation | **域指标 KEEP**；缺统一 OTel starter |
| Spring Actuator vs PlatformInfo | 弱重叠 | 公开 API 可保留 |
| Maven Consumer POM vs Flatten | Flatten 已删除 | **已收敛** |
| Maven 4 vs CI 脚本 | consumer 脚本仍适配 `*-build.pom` 与 M3 | **必要门禁，非冗余** |
| Maven pluginManagement vs Boot parent | Ainer 不用 `spring-boot-starter-parent`，自管 plugin 版本 | **KEEP** — 与 parentless BOM / 多模块发行物模型一致 |
| 自研限速 vs Resilience4j | 内存 node-local limiter | **KEEP** — 语义不同 |
| ApiResponse vs ProblemDetail | 双错误风格张力 | **暂时 KEEP**；若统一需独立 ADR |

### 6.1 自研基础设施总表

| 基础设施 | 为什么存在 | Java25/M4/Boot41 后是否可删 |
|---|---|---|
| 错误码 + BusinessException + ApiResponse | 稳定字符串错误契约 | 否 |
| RequestId 过滤器 | API/日志关联 | 否 |
| Resource Server 域 filters | online validation / step-up | 否 |
| TokenProfile / AuthenticatedPrincipal | 身份领域模型 | 否 |
| Authorization sealed domain | 授权 S0 | 否 |
| AI Gateway + Provider + 审计 | 产品核心 | 否 |
| OAuth2 Authorization JsonMapper | JDBC 授权持久化 | 否 |
| ClientCredentialsServiceTokenProvider | 历史/测试服务令牌 | **可删或改接 Boot OAuth2 Client** |
| Maven consumer 验证脚本 | 发布正确性 | 否 |
| parentless BOM | 外部可消费平台 | 否 |
| 通用线程池框架 | — | **不存在（GOOD）** |
| TTL / 自研 Context Holder | — | **不存在（GOOD）** |

---

## 7. Top Modernization Candidates

| Priority | Finding | Current | Replacement | Benefit | Risk |
|---|---|---|---|---|---|
| P0 | Reactor BOM import WARNING | 根 POM import 同 reactor `ainer-dependencies` | 后续模型重构（独立 ADR）；保持 parentless 自包含 | 构建模型稳定、零 unexplained warning | 破坏 Consumer POM / 外部消费 |
| P1 | 无可观测性 starter | requestId MDC + 手工域 metrics | `ainer-starter-observability`（Observation/OTel 可选 + requestId↔trace） | 统一诊断、少重复 instrumentation | 过度采集 / 敏感数据入 span |
| P1 | 出站 HTTP/SSRF 未平台化 | 模块各自 HttpClient/RestClient；AI 仅禁 redirect | 统一 RestClient.Builder 定制 + URL/IP 策略（安全设计先行） | 安全基线默认存在 | 误杀合法回调/供应商 URL |
| P1 | 测试样板重复 | `@Container`+`@DynamicPropertySource`；测试依赖四件套 | `ainer-test-support` + `@ServiceConnection` + starter-test | 更少样板、更高一致性 | 降低集成保真度（需避免） |
| P1 | `ClientCredentialsServiceTokenProvider` 未接线 | 手工 OAuth2 client 栈 | 删除或改接 Boot OAuth2 Client | 少维护无用 glue | 若有隐藏调用需先检索 |
| P2 | 平台 VT 默认 | 未开启 | 压测通过后 `spring.threads.virtual.enabled` | 简化并发模型 | 池化资源与上下文传播 |
| P2 | AI 流 MDC/Security 传播 | 未显式传播 | 显式捕获参数或最小包装 | 审计/追踪正确 | 引入 async framework 过度设计 |
| P2 | Jackson 读取限制 | 默认 Jackson | Boot customizer + 契约测试 | 安全与稳健性 | 破坏合法大 payload |
| P3 | Boot layered/build-info | 仅 repackage | plugin executions | 部署/可追溯 | 低 |
| P3 | 小范围 pattern/switch 清理 | 少量 cast | 局部语法 | 可读性 | 极低 |

---

## 8. Things We Should NOT Change

明确 **NO CHANGE**（当前设计正确，或收益不足以覆盖成本）：

1. **Java 25 / Maven 4 / Spring Boot 4.1 基线本身** — 已冻结，不讨论回退。  
2. **禁用 Java preview / incubator** — 正确。  
3. **POM 源模型暂留 4.0.0** — ADR-0026 有意决策；不要为“看起来 Maven 4-native”强行迁 4.1。  
4. **parentless `ainer-dependencies` BOM** — 自包含与避免 model cycle 优先于减 XML。  
5. **MyBatis `*Row` 保持可变 class** — 不要 record 化。  
6. **含密钥的 `@ConfigurationProperties` 保持不可变 class（非 record）** — 已完成构造器绑定。  
7. **`ApiResponse` 错误协议** — 在独立 ADR 前不要改成 ProblemDetail。  
8. **AI SSE 使用 JDK `HttpClient`** — 文档化例外，合理。  
9. **`aiStreamExecutor` 独立 VT bean + `defaultCandidate=false`** — 正确隔离。  
10. **不在无压测情况下全局开启虚拟线程**。  
11. **不用 ScopedValue 替换 SecurityContext / 租户上下文**。  
12. **Authorization / Identity 域 filters 与 sealed 授权模型** — 这是产品，不是 Boot glue。  
13. **不引入 WebFlux、JPMS、GraalVM 默认、Redis、R2DBC**。  
14. **Maven 3.9+ consumer 门禁** — 生产者 Maven 4 不能取消下游兼容验证。  
15. **机械把所有平行类型改 sealed / 所有线程池改 VT / 所有 POM 改 subprojects** — 属于 OVER-ENGINEERING。

### 8.1 Over-engineering 已发现/需警惕

```text
OVER-ENGINEERING（当前轻度）:
- ClientCredentialsServiceTokenProvider：为尚未接线的场景维护完整 OAuth2 client 手工栈

OVER-ENGINEERING（应避免的下一步）:
- 无需求启用 Structured Concurrency preview
- 全体 ConfigurationProperties 强行改 record
- 为 Maven 4.1 特性增加大量 POM 技巧却不提升 consumer 正确性
- 自研一套 ScopedValue 身份上下文总线
- 封装 Boot 已提供的 RestClient.Builder / ObservationRegistry 再造 framework
```

---

## 9. Recommended Follow-up Plan

本轮**不执行**。建议后续按阶段决策：

### Phase A — correctness / baseline

1. 处理 Maven 4 “BOM imports from within reactor” WARNING（独立设计 + consumer 回归）。  
2. 升级 Maven 工具版本时同步调整 Enforcer 上界并全量验证。  
3. 保持 Wrapper SHA / JDK 25 / Boot 4.1.0 锁定纪律。

### Phase B — remove obsolete infrastructure

1. 删除或正式接入 `ClientCredentialsServiceTokenProvider`。  
2. 清理疑似多余的显式 `jackson-databind` 依赖声明。  
3. 统一测试 starter 依赖，去掉无意义四件套复制。  
4. 审查部署脚本就绪探针是否应对齐 Actuator liveness/readiness（不删业务管理 API）。

### Phase C — high-value modernization

1. `ainer-starter-observability`（Observation + 可选 OTLP + requestId/trace 关联）。  
2. 出站 HTTP / SSRF 平台治理（安全设计先行，不自动替换现有 AI HttpClient）。  
3. `ainer-test-support`（PostgreSQL `@ServiceConnection`、共享基类、RestTestClient）。  
4. 虚拟线程双模式压测 → 通过后平台默认。  
5. AI 流式路径上下文传播验证与最小修复。  
6. Jackson 3 读取限制与契约测试。

### Phase D — optional experiments

1. POM 4.1.0 / `packaging=bom` / parent & version inference 原型（必须双 consumer 绿）。  
2. layered JAR、build-info、JFR 持续采集、jdeps/jdeprscan CI。  
3. 局部 pattern matching / sealed SSE 事件等机会型清理。  
4. Structured Concurrency — **WATCH only**。

---

## 10. Finding Index（完整条目精选）

### F-VT-1 — AI stream executor

```text
Finding: AI SSE 已正确使用虚拟线程隔离执行器
Category: Already well used
Location: AiRuntimeModuleConfiguration.aiStreamExecutor；AiGatewayApplicationService
Current implementation: Executors.newThreadPerTaskExecutor(Thread.ofVirtual()...)
Modern capability: Java virtual threads
Why current implementation exists: 阻塞式 provider SSE 读取 offload
Is it now redundant: 否
Recommended action: KEEP
Benefit: 已获得
Risk: 与全局 VT 开关叠加时需回归
Migration difficulty: N/A
Required tests: AiGateway 流式测试 + Qualifier 测试
Priority: NO CHANGE
```

### F-CTX-1 — 无自研 ThreadLocal 身份总线

```text
Finding: 不存在 Tenant/User Context Holder / TTL
Category: Already well used
Location: 全仓搜索 ThreadLocal/InheritableThreadLocal/TransmittableThreadLocal
Current implementation: 仅 MDC requestId + Spring SecurityContextHolder + ThreadLocalRandom
Modern capability: 显式 AuthenticatedPrincipal 传递
Why current implementation exists: Greenfield 身份模型
Is it now redundant: N/A
Recommended action: NO CHANGE；禁止 ScopedValue 隐式身份
Benefit: 更少隐藏状态
Risk: 无
Migration difficulty: N/A
Required tests: N/A
Priority: NO CHANGE
```

### F-REC-1 — Record 采用已饱和

```text
Finding: API/领域值对象已普遍 record；剩余 class 多数有正当理由
Category: Already well used / Not worth adopting
Location: 约 92 个含 record 的源文件；19 个 *Row.java；Properties 不可变 class
Current implementation: 见 §3
Modern capability: records
Why current implementation exists: 清晰不可变建模
Is it now redundant: N/A
Recommended action: 停止机械 record 化；仅审查新代码
Benefit: 已获得
Risk: 误改 Row/UserDetails/Properties
Migration difficulty: N/A
Required tests: N/A
Priority: NO CHANGE
```

### F-BOOT-1 — 无大规模 auto-config 重复

```text
Finding: 未维护手工 ObjectMapper/Tomcat/Redis/通用 Executor/MeterRegistry 框架
Category: Good use of Boot
Location: starters 与模块 configuration 扫描
Current implementation: 域 Bean + Boot auto-config
Modern capability: Spring Boot auto-configuration
Why current implementation exists: 平台域逻辑
Is it now redundant: 域逻辑否；测试样板与未接线 client 有少量冗余
Recommended action: Phase B/C 清理样板与未接线代码
Benefit: 更少 glue
Risk: 误删域安全逻辑
Migration difficulty: Low–Medium
Required tests: starter auto-config tests + security integration
Priority: P1（仅针对真正冗余项）
```

---

## 11. Audit Method & Limits

**已验证：**

- 根/子 POM、Wrapper、Enforcer、`.mvn/maven-user.properties`
- CI/Release workflows
- `./mvnw --version`、`java -version`
- `spring-boot-dependencies` 中 `spring-framework.version=7.0.8`
- `./mvnw validate` 的 BOM import WARNING
- 源码级搜索：record/sealed/VT/ThreadLocal/executors/@Async/RestClient/Observation/Dockerfile 等
- ADR-0026、ADR-0029、`project-status.md`、文档总览约束

**未在本轮执行：**

- 完整 `./mvnw clean verify`（耗时门禁；基线版本不依赖其结果即可确认）
- 虚拟线程双模式压测
- 生产环境 JFR/容器实测
- 将 POM 4.1 自动改写并尝试构建

**原则重申：**

> 选择 Java 25、Maven 4、Spring Boot 4.1，是为了更少基础设施、更强类型约束、更简单并发与更清晰构建模型——不是为了版本号或新语法本身。

---

## 12. Verdict One-liner

`ainer-boot` 对 **Java 25 语言/领域建模** 与 **Spring Boot 4.1 运行时 starter** 的利用已达 **GOOD**；对 **Maven 4** 主要兑现了工具链与 Consumer POM，源模型仍是有意保留的 POM 4.0，整体 **PARTIAL**；跨层最大价值不在“再写新语法”，而在消除 **reactor BOM 警告、未接线 OAuth client glue、测试/可观测性/出站 HTTP 脚手架缺口**，并在压测后审慎推进虚拟线程平台默认。
