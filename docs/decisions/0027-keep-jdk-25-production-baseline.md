# ADR-0027：保留 JDK 25 生产基线并跟踪 JDK 27

- 状态：Accepted
- 日期：2026-07-30
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无

## 背景

Ainer 正在把 Maven 4.0.0-rc-6 设为生产者构建基线，同时将作为 `xq-platform-next` 的脚手架，
服务 `xq-shop` 2.0 与 `xq-zhiwu`。项目评估过把 Java 基线从 JDK 25 直接提升到 JDK 27。

截至 2026-07-30，JDK 27 仍是 Early-Access Build 32，计划于 2026-09-15 GA。Spring Boot
4.1 的官方系统要求只声明兼容到 Java 26。Ainer 使用的 ArchUnit 1.4.2 内置字节码解析器也只
识别到 class major 70；使用 `--release 27` 生成 major 71 后，架构测试无法导入任何被测类。

这不是业务代码错误，而是当前工具生态尚未形成完整的 JDK 27 正式版兼容链。若同时采用 Maven 4
RC、JDK 27 EA 和超出 Spring Boot 官方范围的组合，会让脚手架的构建问题难以隔离。

## 决策驱动因素

- 脚手架首先需要可重复、可诊断的生产者构建；
- `xq-platform-next` 需要稳定的编译、测试、运行和下游消费基线；
- 架构测试不能通过忽略空规则或关闭门禁来迁就新字节码；
- Maven 4 已经是本阶段主动引入的 preview 变量，不再叠加 JDK EA 基线；
- Java 升级必须带来明确收益，不能只改变版本号。

## 备选方案

### 立即使用 JDK 27 与 `--release 27`

可以最早使用 JDK 27 API 和字节码，但当前超出 Spring Boot 4.1 官方兼容范围，并导致
ArchUnit 1.4.2 架构测试失效。采用 ArchUnit 快照、私有补丁或关闭架构规则都会把临时兼容问题
变成 Ainer 的长期维护成本，因此不采用。

### 使用 JDK 27 构建、以 `--release 26` 发布

该方案可以运行 JDK 27，同时保持当前依赖可读取的字节码，但会把“构建 JDK”和“语言/产物基线”
拆成两个版本。Ainer 当前没有必须依赖 JDK 27 运行时的能力，这种复杂度没有对应收益，因此不
作为默认方案。

### 先升级到 JDK 26

Spring Boot 4.1 官方兼容 Java 26，但 JDK 26 不能提供与 JDK 25 LTS 相同的长期维护价值，还会
增加一次短期迁移。Ainer 没有必须使用 Java 26 API 的需求，因此不采用。

## 决策

- Ainer 的生产者构建、测试和运行基线保持 JDK 25；
- Maven Compiler Plugin 继续使用 `--release 25`，产物 class major 保持 69；
- 根工程与 parentless BOM 的 Maven Enforcer 继续要求 Java `[25,26)`；
- 不启用 Java preview features，也不为 JDK 27 添加默认 Maven profile；
- 不降低、跳过或改写 ArchUnit 规则来制造 JDK 27 构建通过；
- JDK 27 仅作为未来升级候选，可以进行不影响正式门禁的独立探索；
- 真正升级时新增 ADR 取代本决定，不在本 ADR 中静默改写版本。

JDK 27 至少满足以下条件后才重新进入实施评审：

1. JDK 27 GA 发行包和校验信息可从官方持久端点获得；
2. Spring Boot 使用版本的官方兼容范围包含 Java 27；
3. ArchUnit 等读取项目字节码的正式发布版本支持 class major 71；
4. Maven 4 全 Reactor、架构测试、真实 PostgreSQL 集成测试、两个可执行 JAR 启动和
   Maven 3.9+/Maven 4 golden consumer 全部通过；
5. 若采用 `--release 27`，下游最低运行版本变化已经明确写入发布与迁移文档。

## 后果

### 正面

- Maven 4 迁移可以在稳定的 JDK 25 LTS 上独立验证；
- 架构测试和现有 Boot 4.1 兼容链不需要临时补丁；
- Ainer 制品不会过早强迫所有下游使用未 GA 的 JDK；
- 后续 JDK 27 升级有明确、可复查的进入条件。

### 负面与风险

- 暂时不能使用 JDK 26/27 新增 API、语言能力或只在新运行时可用的优化；
- JDK 27 GA 后仍需重新执行完整验证，不能依据 EA 试跑直接升级；
- 如果依赖生态长期不支持 major 71，升级时间会晚于 JDK 27 GA。

## 安全、数据与隐私

本决定不改变身份、tenant、数据库、秘密或审计边界。保持单一 JDK 基线可以减少测试工具失效后
安全架构规则被误跳过的风险。任何未来 JDK 升级仍必须执行现有安全与 PostgreSQL 门禁。

## 运维与迁移

本轮不产生运行时迁移：开发机、CI、发布机和服务器继续使用 JDK 25。JDK 27 探索环境不得修改
正式 Wrapper、Enforcer、编译级别或发布制品。未来升级必须统一更新构建环境、运行环境、文档和
下游最低版本，并保留可回退的 JDK 25 发布版本。

## 验收记录

- 根 POM 的 Java 与编译基线为 25，Enforcer 范围为 `[25,26)`；
- parentless BOM 使用相同 Java Enforcer；
- golden consumer 使用 `--release 25`；
- 2026-07-30 使用官方 OpenJDK 27 EA Build 32
  （`27-ea+32-2315`）进行过隔离评估，下载包 SHA-256 为
  `24467e3871b4b28c4d8ce3f377ca54a7c2b4fab4a18e74018571f8c1334bd9e5`；
- JDK 27 下 Maven 4 `validate` 和 `--release 27` 编译能够开始执行，但 ArchUnit 1.4.2
  对 major 71 报 `Unsupported class file major version 71`，因此没有把该试跑记录成通过；
- 恢复 JDK 25 后的 Maven 4 完整回归结果记录在项目状态文档。

## 参考

- [JDK 27 Early-Access Builds](https://jdk.java.net/27/)
- [OpenJDK 27 Specification](https://openjdk.org/projects/jdk/27/spec/)
- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [ArchUnit 1.4.2 release](https://github.com/TNG/ArchUnit/releases/tag/v1.4.2)
- [ADR-0026：Maven 4 构建与 Consumer POM 基线](0026-maven-4-build-and-consumer-pom-baseline.md)
