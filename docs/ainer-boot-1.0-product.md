# Ainer Boot 1.0 产品说明

> 文档类型：产品说明（1.0 时间点快照） · 版本：`v1.0.0`（2026-08-18） · 状态：已发布
> 维护协议：本文件描述 1.0 合同快照，不随主线滚动更新；能力变化以
> [`CHANGELOG.md`](../CHANGELOG.md) 与 [`project-status.md`](project-status.md) 为准。

**读者模型**：本文件是**对内工程合同快照**，不是对外宣传页。三个固定事实先于一切能力
描述：(1) `v1.0.0` 是 `v0.2.0` 的**字节冻结**（零代码差异的合同声明发布，不带能力增量），
生效的是 1.x 兼容承诺；(2) `1.0.x` 的 LTS 是对**脚手架制品的工程补丁线**（缺陷/安全修复
与兼容窗口，ADR-0045/0046），不是「已签发的生产环境」声明；(3) **1.0 发布当时**仓库为
私有/专有。后续许可与可见性以 [ADR-0051](decisions/0051-mit-license-and-public-repository.md)
为准（MIT + 公开仓库），不以本条为当前权威。

## 1. 一句话定位

**Ainer Boot**（AI-Native Extensible Runtime Boot）是一个 **AI 原生、但不局限于 AI** 的
通用企业 Java 脚手架与运行基线：JDK 25 + Spring Boot 4.1 + PostgreSQL 18 的模块化单体，
自带可信的身份、授权、工作区治理、AI 模型网关与企业基座，让产品团队从「第一个业务提交」
开始，而不是从「搭后台」开始。

`v1.0.0` 冻结 `v0.2.0` 的全部字节——它是产品合同定稿发布，不是能力大版本；本文件描述的
能力全部已在 `0.1.0`–`0.2.0` 中交付并由发布门禁验证。

## 2. 解决什么问题

传统 Java 脚手架给一个「能跑的后台」，但产品团队真正要付的代价在后面：

| 常见脚手架的隐性成本 | Ainer Boot 1.0 的回答 |
|---|---|
| 登录/权限是演示级的，承载真实身份与授权需求前要推倒重写 | 独立 OAuth 2.1/OIDC Authorization Server、Passkey、真 JWT 端到端安全链 |
| `user.dept_id + role + data_scope` 表达不了真实组织与多任职 | Workspace 成员治理 + ADR-0037 混合授权（RBAC+ReBAC+ABAC）；组织目录与 `workforce.position#assignee` 岗位集合绑定为 **Incubating**（撤岗即失权已验证，不承诺 API 稳定） |
| 错误码、响应信封、分页各自为政 | 统一 `ApiResponse` 信封、真实 HTTP 状态码、`AINER.*` 稳定错误码、`X-Request-Id` 全响应追踪 |
| 数据库方言混用，测试靠 H2 自欺 | PostgreSQL 18 唯一基线；集成测试全量真实库重放，CI 强制 **0 skipped** |
| AI 能力是「再加一个 SDK」 | 治理式模型网关：预算/限流/Token 费用审计/脱敏，调用必经网关 |
| 升级靠玄学 | 版本化制品 + 签名供应链 + 相邻 minor 升级/一级回滚窗口 + 双参考消费者真实矩阵 |
| 从脚手架到「我的项目」要改一百处 | Project Initializer：manifest v1 确定性生成，自带 Wrapper/测试/部署基线 |

## 3. 产品全景（六个能力域 + 开发者工具链）

### A. 现代运行基座（Stable）

- JDK 25、Spring Boot 4.1、PostgreSQL 18 唯一业务数据库基线（不保留方言兼容层）
- 生产者构建锁定 Maven 4.0.0-rc-6（preview）+ Maven 3.9 消费者兼容双通道；虚拟线程默认启用（压测决策记录在案）
- 模块化单体默认交付；按需服务化（`monolith|service` 只切适配器，不改拓扑/事务边界）
- Docker Compose 开发环境：**默认 profile 只提供 PostgreSQL 双库**（日常开发推荐应用跑宿主机）；`full` profile 一键全栈为实验性（AS 强制 https issuer 而容器听 HTTP，纯 Compose 下 RS discovery 通常无法闭环，见 `development.md`）

### B. 安全身份底座（Stable）

- 独立 OAuth 2.1/OIDC Authorization Server：Authorization Code + PKCE、Client Credentials、
  RFC 7662 introspection、RFC 7009 revocation
- Identity foundation：HumanAccount / ServicePrincipal / Credential（密码 / Passkey /
  OIDC subject），typed token profile（`USER_NEUTRAL_V1` / `SERVICE_V1`）+ `sec_epoch`
  在线撤销比对。Passkey 为协议能力：默认关闭、未做真实设备兼容矩阵认证、1.0 不含其 UI
- Resource Server：真 JWT 端到端链、高风险路径在线校验失败关闭、step-up 强认证策略
- 测试真实性按模块分层如实标注：字典/配置/通知/文件/组织/知识/授权（含集合绑定与委托）
  的 HTTP 测试使用真 RSA 签名 JWT + `NimbusJwtDecoder` 验签（`JwtTestSupport`）；两个
  参考消费者同链。**1.0.0 制品中的历史例外**（AI 网关模块 HTTP 测试曾用不验签的测试
  decoder、Workspace 模块仅有服务层集成测试）已在 1.0.x 线补齐：AI 网关测试已转真链
  （9 项），Workspace 新增 `/api/workspaces` 真 JWT HTTP 门禁测试（401/403/201/非成员
  404/审计行）——见 CHANGELOG 与 `project-status.md`

### C. 工作区与治理（Stable）

- Workspace membership 资源边界（OWNER/ADMIN/MEMBER），OWNER 专用锁定事务转移；
  无 ACTIVE OWNER 时提供**默认关闭**的双人恢复（只提升现有 ACTIVE 成员，不恢复已
  REVOKED 的原 OWNER）
- 授权审计热表 + 同库归档、稳定游标 SIEM 拉取

### D. 通用授权（Stable + Incubating）

- **Stable**：ADR-0037 混合授权——Permission/Role/Binding、结构化 Scope
  （Workspace/Resource/Global）、Spring Security adapter、管理 API、防提权矩阵、决策审计、
  类型化集合查询计划（Ainer 产出类型化约束 `Q`，由产品 Repository 翻译为参数化 SQL——
  Ainer 不输出 SQL）。**SubjectSet 集合绑定的决策引擎与管理 API**（创建防提权矩阵、决策时
  实时成员解析）。`workforce.position#assignee` 集合族依赖组织目录，与组织模块同属
  **Incubating**，不进入 1.x Stable 兼容承诺。**1.0.0 时点的 adapter 边界（ADR-0037）**：
  `@AinerAuthorize` 只支持 `resourceType=request` 的空-obligation 粗门禁；资源级 target
  resolver、方法级 AOP、obligation 执行器与 RFC 9470 均未交付——高价值写仍必须在应用
  服务内显式调用授权决策。后续切片以 `project-status.md` / CHANGELOG 为准，不回写本快照。
- **Incubating**：组织目录（部门/任职/调岗/岗位，含调岗单事务与任职期不重叠约束）、
  Agent 代行 A1（一层委托 + 委托检查点实时解析：Agent 退役/权限收缩/撤委托即拒）

### E. AI 运行时（Stable + Incubating）

- **Stable**：模型网关（OpenAI-compatible 非流式/SSE）、模型白名单、限流、预算、
  Token/费用/耗时/策略决策审计；供应商错误与 API key 不进客户端/日志
- **Incubating**：Agent 定义注册表（code+version、退役即失权）

### F. 企业基座与知识（Stable + Incubating）

- **Stable（P3 四件套）**：文件存储（上传/下载/删除、SHA-256、大小/类型限制、补偿与
  审计）、字典（树形/多语言/缓存）、配置（类型安全/热更新/版本史/AES-GCM secret 加密）、
  通知（**多渠道端口 `ChannelSender` + 模板 + SKIP LOCKED 队列 + 指数退避；默认实现是
  开发用日志发送器，不含真实 SMS/Email/Push/Webhook 投递**——生产渠道由产品实现 SPI）；
  Spring Cache 抽象（Caffeine 默认、Redis 可选）与分布式锁
- **Incubating**：Knowledge Foundation K1/K2——不可变知识 Revision + SUPERSEDES 血缘 +
  asOf 精确解析；**发布是人工门禁**（AI 只能提案，SERVICE 调用发布端点一律 403）

### G. 开发者工具链（Stable）

- **Project Initializer**：manifest v1 声明式生成独立项目（确定性：同 manifest 两轮生成
  字节一致）、CRUD 模板、生成项目自带锁定版 Maven Wrapper；两个参考消费者均由它生成
- `ainer-test-support`：真 JWT 夹具（JwtTestSupport）、TestRestClient 封装、PostgreSQL
  容器基座
- 发布供应链：GPG 签名制品、SBOM、项目签名 provenance、远端全量读回验签、不可变
  GitHub Release（每次发布 122/122 主制品逐一带签名读回）

## 4. 1.0 产品合同

**Stable（1.x 内保持兼容：HTTP API 路径与响应结构、`AINER.*` 错误码、SPI 签名、migration
只向前追加）**——13 项逐项核对与证据见
[ADR-0040 验收记录](decisions/0040-p3-enterprise-base-and-1.0-product-contract.md)：
框架 8 模块、Identity、Workspace、Authorization、AI Runtime、P3 四件套、Initializer、
Docker Compose、HTTP 契约、JWT 安全链、Cache 抽象、AES-GCM、UUIDv7 持久化身份。

**Incubating（可用，不承诺 API 稳定）**：组织目录（O1/O2 已交付）、Agent 代行（A1 已
交付）、Knowledge（K1/K2 已交付）；任务调度（P4）未建设。

**非目标（1.0 明确不做）**：消息中间件（Kafka/RocketMQ 等，触发条件未满足）、菜单/前端
route 权限引擎、商业连接器、前端管理面（Ainer Studio 是独立产品）、多数据源/分库分表、
Spring Cloud 全家桶。完整清单见 ADR-0040。

## 5. 质量与信任模型

- **测试真实性**：集成测试使用真实 PostgreSQL 18 Testcontainers；CI 与发布 run 强制
  0 skipped（动态测试数以发布 run 的 Surefire 读回为准，维护于 `project-status.md`；
  JWT 真实性按模块分层，见 §B 的如实标注）
- **兼容承诺的验证方式**：不是声明而是证据——两个独立参考消费者（不同业务域）在每次
  minor 发布上全量测试 + 升级/回滚矩阵；schema 兼容靠空库 migration 全量重放；config
  兼容靠配置元数据契约进消费者门禁
- **供应链**：annotated tag 与源码/默认分支三方一致才可发布；目标版本已存在即失败；
  发布后全量制品读回验签 + immutable Release 读回断言

## 6. 版本与支持

- `1.0.x` 是首个 **LTS 工程补丁线**（对脚手架制品的支持承诺）：接收缺陷/安全补丁（零
  API/配置/schema 变化），自 `1.1.0` 发布起再支持一个 minor 周期（ADR-0045/0046）。这与
  §9 的边界并不矛盾：LTS 承诺的是**制品的补丁与兼容窗口**，不构成「消费者生产部署已就绪」
  的签发——生产就绪属于各产品自己的部署门禁
- 官方支持**相邻 minor 升级 + 一级回滚**（N+1 → N 为有效回滚终点）；每个 minor 的发布
  证据必须包含至少一个真实消费者全量通过
- 首条完整证据链：`rc.2 → rc.3 → 0.1.0 → 0.2.0 → 1.0.0`，每级升级与回滚终点均由
  消费者验证并留档

## 7. 生态与参考消费者

| 消费者 | 域 | 接入方式 | 证据 |
|---|---|---|---|
| `xq-platform-next` | 翡翠行业信息与协作网络（xq-zhiwu / xq-shop-next 共同后台） | Initializer 生成 + 远端制品固定版本 | 完整升级链至 1.0.0 含回滚；JWT/授权/migration/SDK 纵向切片 |
| `python-learning-service` | Python 交互式学习产品的平台后端 | Initializer 生成 + 远端制品固定版本 | 0.1.0 冷仓接入 → 1.0.0；Evidence 存档切片（learner JWT 归属隔离，8 项测试——切片深度**薄于 xq** 的 14 项；Tutor/AI Runtime 尚未进入该消费者） |

新消费者接入只允许「版本化制品升级」（BOM + Starter 固定版本），拒绝源码副本与开发分支
依赖（scaffold-design §13.5）。

## 8. 快速开始

**路径一：生成一个新项目**（推荐起点）

```bash
# CLI 制品：dev.ainer:ainer-initializer-cli:1.0.0（jar，classifier=cli），
# 从 GitHub Packages 下载（认证见下），或使用 Release 资产中的 CLI 证据包：
java -jar ainer-initializer-cli-1.0.0-cli.jar preview manifest.yaml   # 只读校验
java -jar ainer-initializer-cli-1.0.0-cli.jar init manifest.yaml my-product/
cd my-product && ./mvnw verify   # 生成项目自带锁定版 Wrapper 与真实 PG 测试
```

manifest v1 格式见 ADR-0035 与 `ainer-initializer/src/test/resources/manifest/v1/` 样例。

**路径二：既有项目消费制品**

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>dev.ainer</groupId>
      <artifactId>ainer-dependencies</artifactId>
      <version>1.0.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
<dependencies>
  <!-- 最小 Web 起步（按需换用 persistence/security 等 starter） -->
  <dependency>
    <groupId>dev.ainer</groupId>
    <artifactId>ainer-starter-web</artifactId>
  </dependency>
</dependencies>
```

私有 GitHub Packages 认证经环境变量注入（`${env.GITHUB_PACKAGES_USER/TOKEN}` 到 settings
server `ainer-github-packages`，两个消费者仓库的 `.mvn/github-packages-settings.xml` 是可
复用模板：零密钥入库）。

**运行本地环境**：`docker compose up -d` 提供 PostgreSQL 双库（默认 profile）；应用用
`./mvnw spring-boot:run` 在宿主机启动。实验性 `--profile full` 一键全栈的已知限制见
`development.md`（见 §A）。

## 9. 边界与许可

- 1.0.0 是**工程合同定稿**，不是生产就绪或托管服务声明。源码许可的后续决策见
  [ADR-0051](decisions/0051-mit-license-and-public-repository.md)（MIT）；商标仍按
  ADR-0004，分层定价仍是草案（ADR-0040）
- 文档示例不含真实密钥/客户数据/prompt 正文；生产高可用、容量与告警需按产品部署单独
  完成——本产品交付的是可验证的工程基线，不是托管服务

## 10. 深入阅读

| 想了解 | 读 |
|---|---|
| 当前状态、验证记录与缺口 | [`project-status.md`](project-status.md) |
| 架构、模块与数据所有权 | [`architecture.md`](architecture.md) |
| 产品路线与竞品能力矩阵 | [`design/ainer-scaffold-design.md`](design/ainer-scaffold-design.md) |
| 为什么这样设计（决策链） | [`decisions/README.md`](decisions/README.md)（ADR-0033 Greenfield、0037 授权、0040 合同、0041 供应链、0042–0044 G3 三域、0045/0046 版本与 LTS） |
| HTTP/API 契约 | [`api.md`](api.md) |
| 数据库规范 | [`database.md`](database.md)、[`database-design-standard.md`](database-design-standard.md) |
| 如何贡献/协作 | [`../AGENTS.md`](../AGENTS.md) |
