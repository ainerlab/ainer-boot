# ADR-0026：Maven 4 构建与 Consumer POM 基线

- 状态：Accepted
- 日期：2026-07-30
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无

## 背景

Ainer 需要以可发布的 BOM、Framework、Starter 和应用制品支撑外部生成项目。现有构建使用
`${revision}` 与 Flatten Maven Plugin 生成消费侧 POM，但 Maven 4 已经提供 Consumer POM
处理能力，两套机制在当前用途上重叠。继续同时维护会增加构建阶段、发布模型和故障定位成本。

Maven 4.0.0-rc-6 已能完成 Ainer 当前多模块工程的测试、安装和外部消费验证，但它仍是
release candidate，而不是 Maven 4 的生产稳定版。与此同时，POM 4.1、`packaging=bom`、
父工程推导和依赖版本推导会改变源模型及发布模型，不能与构建工具切换绑成一次大迁移。

JDK 23 起，`javac` 不再默认扫描 classpath 并自动运行注解处理器。Ainer 使用 JDK 25，
因此 Spring Boot 配置元数据等注解处理必须从隐式副作用变成显式构建输入。

## 决策驱动因素

- 让生产者构建只有一个 POM 转换来源；
- 确保安装和发布的 POM 可被 Maven 3.9+ 与 Maven 4 外部项目消费；
- 锁定构建工具和插件输入，减少开发机与 CI 的差异；
- 保留当前已验证的 parentless、自包含 BOM，避免发布模型被父工程推导破坏；
- 让默认开发命令聚焦编译与测试，不污染本地仓库；
- 为后续 POM 4.1 迁移保留独立验证和回退边界。

## 备选方案

### 继续使用 Maven 3 与 Flatten Maven Plugin

该方案变化最小，但不能建立 Maven 4 的统一生产者基线，并继续保留 Maven Core 与第三方 Flatten
插件之间重叠的发布模型处理，因此不采用。

### Maven 4 与 Flatten Maven Plugin 并存

可以暂时保留旧发布路径，但会让同一个消费侧 POM 同时受两套机制影响。出现版本、父工程或
dependencyManagement 问题时，难以判断转换来源，因此不采用。

### 一次性迁移全部 POM 4.1 能力

把根工程、普通模块和 BOM 同时迁移到 POM 4.1，并启用 `packaging=bom`、父工程推导和依赖版本
推导，可以减少部分 XML，但会扩大验证面。尤其是 BOM 一旦从父工程继承坐标或版本属性，发布后
可能失去自包含性，因此本阶段不采用。

## 决策

### Maven 4 生产者基线

- Ainer 的构建、安装和发布统一使用 Maven 4.0.0-rc-6；
- Maven Wrapper 锁定该精确版本，并校验下载的 Maven 发行包；
- Wrapper 的 `distributionUrl` 必须指向 Apache 官方、持久的发布端点，不得依赖临时候选目录；
- Wrapper 配置必须提供与官方发行包一致的 `distributionSha256Sum`；
- Maven Enforcer 和 CI 必须拒绝使用低于该基线的 Maven 构建 Ainer；
- Maven 4.0.0-rc-6 仍是 preview 基线；采用它不代表 Maven 4 已进入生产稳定版。

Maven 3.9+ 不再是 Ainer 的生产者构建工具。它只用于验证已经安装或发布的 Ainer 制品能否被
下游项目消费，不得用于执行 Ainer reactor 的构建、安装或发布。

### Consumer POM 与 Flatten

- 移除 Flatten Maven Plugin 及其生命周期执行；
- 由 Maven 4 的安装和部署模型处理当前 POM 4.0 与 CI-friendly `${revision}`；
- 在 `.mvn/maven-user.properties` 中明确设置
  `maven.consumer.pom.flatten=false`，避免启用额外的全量 POM 展开；
- 安装和发布后的标准 Consumer POM 不得残留未解析的 `${revision}`；
- Consumer POM 的正确性以真实外部项目消费结果为准，不能只检查 reactor 内部构建成功。

`maven.consumer.pom.flatten=false` 不表示继续使用第三方 Flatten 插件。它固定 Maven 4
Consumer POM 的当前行为边界：保留已验证的标准发布模型，不选择会显著展开 POM 的实验路径。
Maven 4 还会并存安装面向构建工具的 `*-build.pom`；其中可以保留源模型的 CI-friendly 表达式，
门禁只检查下游按标准坐标读取的 `<artifactId>-<version>.pom`，并以 Maven 3.9+/Maven 4 的真实
外部消费结果兜底。

### 源 POM 与 BOM 边界

- 本阶段所有源 POM 继续使用 `modelVersion` 4.0.0；
- `ainer-dependencies` 继续是 parentless、自包含的 POM 4.0 BOM；
- BOM 的坐标、版本属性和 dependencyManagement 不得依赖 reactor 根 POM 的继承或推导；
- parentless BOM 自身执行与根工程相同的 JDK/Maven Enforcer，必须在任何 `install` 或
  `deploy` 写入之前拒绝 Maven 3 生产者构建；
- 根工程暂时保留当前 BOM 使用方式，其同 reactor BOM 导入告警在后续模型重构中处理；
- POM 4.1、`packaging=bom`、父工程推导、依赖版本推导和根工程依赖管理重构必须另行验证并形成
  独立决策，不属于本次切换。

不得直接对当前 reactor 执行未经审查的自动 POM 4.1 推导结果。任何后续迁移都必须同时验证源
模型、安装后的 POM 以及 Maven 3.9+/Maven 4 外部消费者。

### 插件与注解处理器

- 显式锁定当前生命周期使用的 clean、resources、jar、install 和 deploy 插件版本；
- compiler、surefire、enforcer、Spring Boot 等已有构建插件继续显式锁定；
- 插件版本升级是独立变更，不与 Maven 4 基线切换隐式捆绑；
- JDK 23+ 所需注解处理器必须在 Maven Compiler Plugin 中显式声明；
- 注解处理器不得仅以普通 optional dependency 的形式依赖 classpath 自动扫描；
- 生成配置元数据或其他编译期制品的模块必须增加产物断言。

### 命令语义

- 日常开发与 CI 的默认完整门禁是 `./mvnw clean verify`；
- 局部快速反馈可以执行 `./mvnw test` 或限定模块的 `verify`；
- `install` 只用于 golden consumer 或发布前的本地仓库消费验证；
- 发布流程通过 Wrapper 执行，不接受开发机全局 Maven 版本替代 Wrapper；
- 只有在确实需要 Maven 3.9+ consumer 门禁时，才调用独立的 Maven 3.9+ 环境。

### 构建与消费门禁

Maven 4 基线必须持续通过以下门禁：

1. Maven 4 Wrapper 完成全 reactor 的 `clean verify`；
2. 单元测试、架构测试和可用的 PostgreSQL/Testcontainers 集成测试通过；
3. Maven 4 完成隔离本地仓库的 `install`，外部 golden consumer 能导入 BOM 并消费 Starter；
4. Maven 3.9+ 外部 consumer 对同一批已安装或已发布制品完成等价构建；
5. 安装或发布的标准 Consumer POM 不含未解析的 `${revision}`，也不依赖 reactor 私有路径；
6. 在干净环境重复构建时，构建计划和可发布制品满足项目定义的可重复构建检查；
7. 使用注解处理器的模块包含预期生成物，例如 Spring Boot 配置元数据；
8. 正式制品发布前逐步建立并执行许可证、SBOM、源码、Javadoc、签名和 consumer smoke 门禁。

## 后果

### 正面

- Ainer 只有 Maven 4 Core 负责消费侧 POM，不再维护重复的 Flatten 生命周期；
- Wrapper、校验值和插件版本共同收紧构建输入；
- Maven 3.9+ 兼容性由真实下游项目验证，而不是让旧 Maven 继续控制生产者模型；
- parentless BOM 继续保持可独立发布和消费；
- POM 4.1 的收益与风险可以在独立阶段评估；
- JDK 25 下的注解处理结果不再依赖编译器隐式扫描。

### 负面与风险

- Maven 4.0.0-rc-6 仍是 preview，后续 RC 或 GA 可能要求再次调整；
- 所有贡献者和 CI 必须使用 Wrapper，直接运行旧版全局 Maven 会失败；
- 需要维护 Maven 4 生产者与 Maven 3.9+/Maven 4 消费者两类验证环境；
- 保留 POM 4.0 意味着本阶段不能获得 POM 4.1 的 XML 精简能力；
- 显式插件和注解处理器配置会增加少量 POM 内容。

## 安全、供应链与可重复性

- Wrapper 发行包必须校验，不能只依赖 HTTPS URL；
- 插件和生产依赖继续受 BOM、依赖台账、许可证与漏洞检查约束；
- 发布使用的 Maven、JDK、插件版本和源码状态必须可追溯；
- consumer smoke 必须使用隔离仓库，避免本机缓存掩盖缺失 POM 或依赖；
- 可重复构建检查不得忽略签名、时间戳或生成元数据带来的非确定性，应明确区分可比较主体。

## 运维与迁移

- 先完成 Wrapper、Enforcer、生命周期插件、注解处理器和 Flatten 移除；
- 再执行 Maven 4 reactor、安装 POM、Maven 4 consumer 和 Maven 3.9+ consumer 验证；
- CI 和开发文档统一切换为 `./mvnw`，历史 ADR 中用于描述旧状态的命令无需追溯改写；
- Maven 4 后续 RC 或 GA 升级必须重新执行全部生产者与消费者门禁；
- POM 4.1 迁移不得作为本 ADR 的顺手清理，只有独立原型通过后才进入实施决策；
- 如果官方持久发布端点或校验信息不可用，Wrapper 发布配置不得回退到临时候选目录。

## 验收方式

- `./mvnw --version` 显示精确的 Maven 4.0.0-rc-6，并通过 Wrapper 发行包校验；
- `./mvnw clean verify` 在 JDK 25 下通过；
- Maven 4 隔离安装后的 14 个标准 Consumer POM 可解析，且不存在没有当前版本属性兜底的
  `${revision}`；
- 独立 Maven 4 consumer 与 Maven 3.9+ consumer 均能只通过 BOM 和已发布坐标完成构建；
- 构建日志不再执行 Flatten Maven Plugin；
- 生命周期插件和注解处理器均可从源 POM 中明确识别；
- 使用 `@ConfigurationProperties` 的公共模块包含预期配置元数据；
- 两次干净构建通过项目定义的可重复性比较；
- POM 4.1、`packaging=bom` 和版本推导未混入本阶段变更。

## 参考

- [Ainer 工程约定](../conventions.md)
- [Ainer 第三方依赖与许可证台账](../dependencies.md)
- [Ainer 开发指南](../development.md)
- [Ainer 测试指南](../testing.md)
- [Ainer 发布指南](../releasing.md)
- [ADR-0025：公共制品、工具类与仓库边界](0025-public-artifacts-utilities-and-repository-boundary.md)
