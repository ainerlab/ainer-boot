# ADR-0035：Ainer Project Initializer 与 Manifest v1 基线

## Status

- 状态：Accepted
- 日期：2026-08-08
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无
- 实现授权：本 ADR 授权在 `reset/0033-greenfield` 分支创建 `ainer-initializer` 模块、Manifest
  v1 模型、确定性生成器与 golden 测试；不授权连接外部仓库、写入数据库或改造现有应用装配
- 实现证据：`ainer-initializer` 与 `ainer-initializer-cli` 已交付并通过 27 tests + golden 门禁
  （确定性两轮生成字节一致、preview 只读、非空目标拒绝覆盖、普通与 postgres 变体 consumer 各自
  独立编译）；决策 4 的 postgres 变体 @Testcontainers 集成测试（`postgres:18.3-alpine`、
  `@DynamicPropertySource`、真实 `SELECT 1` 连通断言）已由
  `scripts/verify-initializer-consumer.sh` 在 CI 全通道验证（普通 smoke test 与 postgres
  集成测试均 0 skipped）

## Context

Ainer Boot 产品化路线（`docs/design/ainer-scaffold-design.md` §12）把 P2 Create & Generate
定义为：

- manifest v1 创建独立项目，安全 preview/diff，不复制 Ainer 源码；
- 同版本、同 manifest 生成无差异（生成确定性）；
- 默认不覆盖、不改菜单、不写数据库（生成安全）；
- TTFR 与 TTCRUD 目标通过；生成物通过 PostgreSQL 与 golden consumer 门禁。

P1 已交付可独立消费的 BOM/Starter 制品、最小 off-state 应用与 Maven 3.9+/4 consumer 门禁，
但仓库没有任何 Initializer 形态的代码或契约。项目结构、manifest 契约和门禁还必须同时便于
人类与 Agent 理解（设计文档 §1 已声明的约束），因此 manifest 与生成器必须在开始实现前冻结
最小基线。

## 决策

### 1. 交付形态：`ainer-initializer` 可执行模块 + 核心库

新增 `ainer-initializer` 模块，作为 Ainer Boot reactor 的一员，交付两类制品：

1. `ainer-initializer` library JAR：`ManifestV1` 模型、校验与确定性生成内核，可被测试、
   Studio 或其他工具复用；
2. `ainer-initializer-cli` 可执行 JAR（依赖 library）：`init` 子命令从 manifest 在目标
   目录生成项目，`preview`/`diff` 子命令只读展示将产生的文件与差异。

不实现 Service 化端点、不监听端口、不连接数据库。Initializer 是离线确定性生成器。

### 决策 2. Manifest v1 契约

manifest 是生成器的唯一输入契约，使用 YAML 1.2 编码，结构化字段，禁止脚本和表达式。
v1 字段：

| 字段 | 必需 | 语义与约束 |
|---|---|---|
| `schemaVersion` | 是 | 固定字符串 `v1`；其余值 fail-fast |
| `project.name` | 是 | 人类可读名称，1–120 字符 |
| `project.groupId` | 是 | 合法 Maven groupId（点分段，每段 `[A-Za-z_][A-Za-z0-9_]*`） |
| `project.artifactId` | 是 | 合法 Maven artifactId（`[a-zA-Z0-9_-]+`），用于 POM 与目录名 |
| `project.version` | 是 | 非 SNAPSHOT 或带审定的语义化版本 `[0-9]+(\.[0-9]+){2}(-…)?` |
| `project.description` | 否 | 默认取 project.name |
| `java.release` | 是 | 仅允许 `25`（与 Enforcer 一致），后续版本经 BOM/ADR 扩展 |
| `spring-boot.version` | 是 | 必须与生成器内嵌的受支持版本清单一致 |
| `project.ainerVersion` | 是 | 生成的 POM 锁定的 `dev.ainer:ainer-dependencies` BOM 版本；默认必须为非 SNAPSHOT 已发布版本，显式以 `-SNAPSHOT` 结尾需 manifest 同时声明 `allowSnapshot=true` |
| `package.name` | 是 | 派生 groupId 或显式给定；生成主类、测试与配置均以该包为根 |
| `starters` | 否 | 额外 Starter FQN 数组；`ainer-starter-web` 隐含存在，重复/未知项拒绝 |
| `database` | 否 | `none`（默认）或 `postgresql`；`postgresql` 将在测试资源生成 Testcontainers 样例 |
| `owner` | 否 | 用户显示名与邮箱，仅写入 `README` 与版权占位，不写入运行配置 |

未知字段、类型错误、冲突（如示例含 `$` 模板字面量）一律校验失败，不静默忽略。

### 决策 3. 生成语义（确定性）

- 生成器从内嵌模板集与 manifest 构造内存中的文件树（路径、字节、执行位），不读网络、
  不查注册表、不读取系统时钟（除生成记录时间戳但**不**写入文件内容）；
- 同版本生成器 + 同 manifest → 字节级相同输出（golden 测试在 `target` 下重放两次并比较）；
- 模板使用 Mustache 风格 `{{key}}` 占位，全部占位有默认值，未替换占位符视为生成失败；
- 生成目标目录必须为空或不存在；目标非空时以 `preview` 方式输出差异并拒绝写入
  （必须 `--force` + 显式确认才允许覆盖，且不删除非生成文件）。

### 决策 4. 文件清单（v1 模板）

生成的最小项目：

| 文件 | 说明 |
|---|---|
| `pom.xml` | parent 非 Ainer root，而是 `dev.ainer:ainer-dependencies` BOM import，依赖
  使用 BOM 管理版本；项目不复制 Ainer 源码 |
| `mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties` | Apache Maven Wrapper
  3.3.4；固定 Maven 3.9.16 官方发行包与摘要，POSIX `mvnw` 带执行位 |
| `src/main/java/<pkg>/Application.java` | `@SpringBootApplication` 主类 |
| `src/main/java/<pkg>/ping/PingController.java` | `GET /api/ping` 返回平台 envelope（演示
  真实 HTTP 与 `X-Request-Id`） |
| `src/main/resources/application.yml` | 最小配置（应用名、`management` 不暴露敏感端点） |
| `src/test/java/<pkg>/ApplicationSmokeTest.java` | MockMvc 启动与 ping 契约测试 |
| `.gitignore` | 与 Ainer 相同最小集 |
| `README.md` | 启动命令、制品来源与安全说明 |

`database=postgresql` 时追加：PostgreSQL 驱动与 Testcontainers 依赖、`spring.datasource`
  环境变量占位配置与对应 Testcontainers 集成测试（`@Testcontainers` + `postgres:18.3-alpine`）。

> 实施补正（2026-08-13）：首个产品消费者 `xq-platform-next` 复核发现，`v0.1.0-rc.2`
> 的 README 与决策 6 已要求 `./mvnw`，但生成树没有包含 Wrapper，门禁又借用了 Ainer
> 生产者仓库的 Wrapper，因而掩盖了缺口。该实现补正不改变本 ADR 的结论：v1 模板增加
> 上述 Wrapper 三件套，使用 Apache Maven Wrapper
> 3.3.4 固定 Maven 3.9.16 及其官方 SHA-256；生成树保存 POSIX 执行位，`diff` 检查执行位，
> consumer/TTFR/TTCRUD 门禁只执行生成项目自己的 Wrapper。修复必须通过新版本坐标发布，不能
> 覆盖不可变的 `v0.1.0-rc.2`。

不生成：菜单、路由、管理页面、数据库表、OpenAPI、CI 工作流。CRUD 生成属于 P2-P4 的纵向
切片，由 Studio/后续迭代交付（ADR-0035 不授权）。菜单与页面语义在 studio 模板且不随
Initializer 落库。

### 决策 5. Preview 与 diff

- `preview`：只读计算完整文件树，输出“将要创建 n 个文件、N 字节、结构树”；
- `diff`：传入已有目录，计算目标新文件、已修改文件与已删除文件（删除仅建议列出，绝不
  执行删除）；
- 两者都不执行任何写入、连接或网络请求。

### 决策 6. golden 门禁与验收

- `determinism-golden` 测试：同一 manifest 在同一版本下两次生成字节级一致；
- `preview-does-not-write` 测试：预览后目标目录字节数/内容不变；
- `refuses-nonempty-dir` 测试：目标已有文件时拒绝覆盖；
- consumer 门禁（扩展现有 `verify-maven-consumers.sh` 或其姊妹脚本）：用生成的
  `pom.xml` 创建消费者工程，`./mvnw` 构建并启动冒烟（`/actuator/health` 或 `/api/ping`
  200），PostgreSQL 集成测试 0 skipped 时才算通过。

### 决策 7. 非目标（v1 明确不做）

- 不做在线服务、模板市场、组织模板与策略包；
- 不做升级助手、模块安装/移除、CRUD 与页面生成；
- 不做 Maven Central 之外的仓库扫描与自动环境检测；
- 不复制任何竞品模板，不引用 `RuoYi`、yudao 等生成结构；
- 不在生成目录写入 Ainer 源码副本——生成的工程只引用已发布制品。

## 备选与取舍

- **不实现数据库与网络依赖**：P2 首版保持确定性、可审计与可测试，把可变环境（DB/网络）
  留给 golden consumer 阶段；
- **库 + CLI 分离**：与 Studio 及未来 web assembly/agent 工具共用内核，CLI 只是入口；
- **Manifest 不承载循环结构**：v1 明确字段化、无脚本，避免生成器变成解释器；
- **确定性优先于美观**：输出文件时间戳、行尾与编码固定，golden 可回放。

## Consequences 与迁移

- 新模块只依赖 `ainer-core`（错误模型/依赖方向保持 `ainer-core <- … <- initializer`）；
- manifest 新增字段必须：先扩展 ADR/设计文档、再实现解析、再 golden 测试重新生成基线；
- 后续版本（v2 等）通过新增字段或新增版本号演进，v1 兼容性撤销需 ADR；
- 内部消费者（`xq-platform-next`）创建时必须以 P1 已发布制品为目标，初始 `pom.xml`
  依赖版本必须是 released 非 SNAPSHOT，或经明确 ADS 的 SNAPSHOT 场景。

## 相关文档

- `docs/design/ainer-scaffold-design.md` §12、§13；
- `docs/releasing.md` §4（制品签名与 provenance 基线）；
- ADR-0024（模块化单体）、ADR-0020（PostgreSQL/UUIDv7）、ADR-0029（JDK 25/Boot 4）。
