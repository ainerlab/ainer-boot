# Ainer Boot 开发手册

> 文档类型：开发操作 · 状态：生效 · 最近核对：2026-08-28 · 适用版本：`1.4.0`（当前稳定）

本文是 Ainer Boot 的日常开发操作手册。新开发者应先读 [`00-overview.md`](00-overview.md)
了解文档地图，再按本文完成第一次构建与验证。架构决策背景见 [`architecture.md`](architecture.md)
与 [`decisions/README.md`](decisions/README.md)；编码规范见 [`conventions.md`](conventions.md)。

## 1. 环境要求

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 25（LTS） | BellSoft Liberica 或 Temurin |
| Maven | 仓库 Wrapper 4.0.0-rc-6（preview） | 生产者构建**必须**用 Wrapper；系统 Maven 3.9+ 只用于消费者兼容门禁 |
| Docker | Colima / Docker Desktop / 原生 | PostgreSQL Testcontainers 必需 |
| PostgreSQL | 18.x | 唯一业务数据库基线（Testcontainers 自动拉起） |

macOS + Colima 的必需环境变量（缺一不可，详见 [`testing.md`](testing.md) §4）：

```bash
export DOCKER_HOST=unix:///Users/$(whoami)/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## 2. 第一次验证

```bash
git clone git@github.com:ainerlab/ainer-boot.git
cd ainer-boot
./mvnw --version        # 确认 Maven 4.0.0-rc-6 + JDK 25
./mvnw clean verify     # 全量 Reactor；需 Docker 在线
```

成功标准：**所有模块 SUCCESS，测试 0 skipped**。结果汇总用 `./scripts/check-surefire-results.sh`。

<details>
<summary>常见首次构建问题</summary>

- **105 skipped**：Docker 不可达——检查上述两个环境变量是否已设
- **Maven SHA 校验失败**：删除 `~/.m2/wrapper/` 后重试
- **虚拟线程矩阵失败**：非阻塞 job，不影响主质量门禁

</details>

## 3. 本地运行

**最快路径（推荐）**：Docker Compose 只起数据库，应用跑宿主机。

```bash
docker compose up -d                     # PostgreSQL 双库（默认 profile）
bash scripts/generate-dev-keys.sh        # 首次生成 RSA 签名密钥（幂等）

# 业务 Server
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ainer
export SPRING_DATASOURCE_USERNAME=ainer
export SPRING_DATASOURCE_PASSWORD=local-only-password
./mvnw -pl ainer-server spring-boot:run

# Authorization Server（另开终端）
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ainer_auth
export SPRING_DATASOURCE_USERNAME=ainer_auth
export SPRING_DATASOURCE_PASSWORD=local-only-password
./mvnw -pl ainer-authorization-server spring-boot:run
```

**注意**：Authorization Server 代码强制 `https://` issuer。`docker compose --profile full`
的一键全栈是实验性（AS 容器听 HTTP 但 issuer 是 https，RS discovery 通常无法闭环）——
日常开发不要用 full。

**OpenAPI 文档**：业务 Server 启动后 `/v3/api-docs` 与 `/swagger-ui.html` 可用
（需真 JWT 认证，springdoc 3.1.0）。

## 4. 模块清单（28 个）

```text
── 基础设施 ──
ainer-dependencies                  版本 BOM（parentless，签名独立）
ainer-framework/ainer-core         零 Spring 核心契约（错误/响应/UUIDv7/存储端口）
ainer-framework/ainer-spring       Spring 通用装配（运行模式/本地文件存储适配）
ainer-framework/ainer-security     无框架身份契约（AuthenticatedPrincipal/token profile）
ainer-framework/ainer-starter-web Web 自动配置（统一响应/全局异常/请求追踪）
ainer-framework/ainer-starter-persistence  MyBatis-Plus + Flyway + UUID TypeHandler
ainer-framework/ainer-starter-security    Resource Server 安全链（JWT 验签/401/403/503）
ainer-framework/ainer-starter-cache       Spring Cache 抽象（Caffeine 默认/Redis 可选）
ainer-framework/ainer-starter-observability Observation + requestId/trace MDC；OTLP 默认关
ainer-framework/ainer-test-support        测试基座（JwtTestSupport/RestTestClient/PG 容器）

── 业务模块 ──
ainer-module-identity              HumanAccount/ServicePrincipal/Credential（ADR-0033）
ainer-module-workspace             Workspace membership 治理 + OWNER 转移 + 审计
ainer-module-ai-runtime            模型网关（SSE/预算/费用审计）+ Agent 注册表（ADR-0043）
ainer-module-authorization         ADR-0037 混合授权（决策器/管理 API/SubjectSet/ActingGrant）
ainer-module-dictionary            树形字典（多语言/缓存/管理 API）
ainer-module-config                动态配置（热更新/版本史/AES-GCM secret）
ainer-module-notification          多渠道通知（ChannelSender 端口/SKIP LOCKED 队列/可选 webhook 与 SMTP）
ainer-module-file                  文件存储（SHA-256/限制/补偿/审计）
ainer-module-organization          组织目录（Incubating：Unit/任职/岗位/SubjectSet 解析器）
ainer-module-knowledge             Knowledge Foundation（Incubating：不可变 Revision/人工发布）
ainer-module-task                  任务调度（Incubating：SKIP LOCKED 队列/退避重试）

── 发行物 ──
ainer-server                       业务 Resource Server（全模块装配）
ainer-authorization-server         OAuth 2.1/OIDC Authorization Server
ainer-offstate-app                 P1 最小可消费应用（无外部服务冒烟）
ainer-initializer                  P2 确定性生成内核（Manifest v1 + v2 安全预设）
ainer-initializer-cli              P2 CLI（preview / init / diff / plan-add / add）
```

模块内部分层：`api → application → domain`，`infrastructure` 实现端口。framework 不依赖
业务模块；业务模块之间通过显式契约交互。

## 5. 日常开发循环

```bash
# 定向开发（只跑受影响模块 + 依赖）
./mvnw -pl ainer-module-<name> -am test

# 全量验证（合并前必须通过）
./mvnw clean verify

# 检查测试结果
./scripts/check-surefire-results.sh

# 消费者兼容门禁（涉及公开契约变化时）
./scripts/verify-maven-consumers.sh
./scripts/verify-initializer-consumer.sh
```

**提交纪律**：`type(scope): 中文描述`；保持用户已有改动不回滚；`git diff --check` 通过。

## 6. 新增业务模块

参照 `ainer-module-file`（最简洁完整）或 `ainer-module-organization`（含组织关系）。

1. **创建模块骨架**：pom（parent `ainer-boot`）+ FeatureMarker + ModuleConfiguration
2. **注册到 Reactor**：根 pom `<modules>` + `ainer-dependencies` BOM + `scripts/release-artifacts.txt`
   + `ainer-server` pom & `@Import` + off-state 测试排除属性 + 发布计数脚本三处硬编码更新
3. **数据层**：Flyway migration（`V<yyyyMMdd>HHmm__<name>_baseline.sql`）；UUIDv7 CHECK；
   复合 FK 防跨 Workspace 引用；append-only 审计表
4. **领域层**：record + 枚举 + 校验（值对象模式；不可变；构造器校验）
5. **应用层**：Repository 端口 + ApplicationService（scope 强制 + 错误码 + 同事务审计）
6. **基础设施**：Row + Mapper 接口 + XML（显式 SQL，`#{}` 绑定）+ Mybatis Repository 适配
7. **API**：Controller + DTO record（不暴露 entity/Row）+ 真实 HTTP 状态码
8. **测试**：服务层集成（PG Testcontainers）+ 真 JWT HTTP（JwtTestSupport）
9. **文档**：database.md 表清单 + 00-overview 模块树 + project-status 记录

### 关键模式（必须遵守）

- **scope 检查**：应用服务内 `principal.hasScope("module.read")`（无 `SCOPE_` 前缀）
- **错误码**：`AINER.<MODULE>.<ERROR>` 枚举 + 真实 HTTP 状态码
- **时间精度**：PostgreSQL `timestamptz` 微秒精度——服务层时间入口统一
  `truncatedTo(ChronoUnit.MICROS)`（Linux 纳秒时钟 + PG 截断会漂移）
- **乐观锁**：CAS 返回 boolean，服务层失败抛 `CONCURRENT_MODIFICATION`（409）
- **分页**：`page ≥ 1, 1 ≤ size ≤ 100`，越界 422 拒绝（不静默收敛）
- **审计**：业务写成功**之后**写审计（REQUIRES_NEW 或同事务）
- **注释**：中文（技术名词/类名/SQL/ADR 保留英文）
- **跨测试隔离**：嵌套 `@TestConfiguration` / `@SpringBootConfiguration` 会泄漏——
  所有测试 fixture bean 加 `@ConditionalOnProperty` 守卫

## 7. 新增 Starter

1. `@AutoConfiguration` + `AutoConfiguration.imports`
2. `@ConditionalOnMissingBean` 默认 Bean + 产品覆盖点
3. `spring-configuration-metadata.json` 进消费者门禁
4. 自动装配测试（含条件开/关正负例）

## 8. 测试策略

| 层级 | 工具 | 要求 |
|---|---|---|
| 真实 JWT HTTP | `JwtTestSupport` + `RestTestClient` + PG Testcontainers | 401/403/201/409 全矩阵；真 RSA 验签链 |
| 服务层集成 | PG Testcontainers | 不变量/并发/审计断言 |
| 纯决策/领域 | JUnit + AssertJ | 无 Spring 上下文 |

**禁用**：H2、Mockito（仓内零依赖）、stub Principal（必须走真验签链）。

完整规范见 [`testing.md`](testing.md)。

## 9. 完成定义（Definition of Done）

- [ ] 行为、错误和权限边界有自动化测试
- [ ] PostgreSQL 行为不使用 H2 代替
- [ ] 数据变更只通过新的 Flyway migration
- [ ] 配置、日志和错误不泄露秘密
- [ ] README、专题文档、ADR、状态和 Changelog 按影响更新
- [ ] 注释使用中文（技术名词保留英文）
- [ ] 完整 Reactor 测试通过（0 skipped）
- [ ] `git diff --check` 通过
- [ ] 没有无关文件或他人的修改被覆盖

## 10. 参考消费者

| 仓库 | 位置 | 用途 |
|---|---|---|
| `xq-platform-next` | `~/01-code/xq/xq-platform-next` | 完整升级链验证（rc.2→1.2.0 含回滚）；JWT/授权/SDK 纵向切片 |
| `python-learning-service` | `/Users/xq/01-code/self/python-learning-service` | 冷仓接入验证（0.1.0→1.2.0）；Evidence 存档切片 |

两者均通过版本化 BOM/Starter 消费远端制品，不含 Ainer 源码副本。当前固定
`dev.ainer:ainer-dependencies:1.3.0`。两仓是本地工程验证仓库，没有 remote、没有部署。

## 11. 发布流程

详见 [`releasing.md`](releasing.md)。摘要：

1. 发布准备 PR（CHANGELOG + README + project-status 版本行）
2. 合入 → dev CI 全绿 → 发布窗口清空 Actions caches（`total_count=0`）
3. annotated tag `v<version>` 必须 peel 到当时 `origin/dev` 头 → release workflow
4. 完整门禁：签名 deploy、132 主制品远端读回验签、空仓消费者、Initializer 五项目门禁、
   SBOM/provenance、immutable Release
5. 按第 12 节做双参考消费者升级矩阵

**注意**：仓库与 28 个 Maven 包均为 public。GitHub Packages 仍需登录拉取（`read:packages`）。
`v1.3.0` 已合格发布；`v1.1.0` withdrawn，禁止消费或复用。发布前确认目标版本不存在；
rc 退役仍按 ADR-0048。

## 12. 参考消费者升级矩阵

合格发布后按 [ADR-0045](decisions/0045-versioning-lts-and-patch-baseline.md) 做**相邻合格
minor** 升级，并至少让一个消费者完成一级回滚。withdrawn / non-qualifying 版本跳过：
`1.0.0 → 1.2.0` 因 `v1.1.0` 无 Release、无制品，算相邻合格次版本。

```bash
export DOCKER_HOST=unix:///Users/$(whoami)/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
export GITHUB_PACKAGES_USER=<GitHub 用户名>
export GITHUB_PACKAGES_TOKEN=$(gh auth token)   # 需 read:packages 或 write:packages

# 只改 BOM import 版本，不改消费者工程自身 <version>
# 隔离本地仓库，证明解析的是远端 Packages，不是 ~/.m2 或生产者 reactor
REPO="$(mktemp -d)"
./mvnw -s .mvn/github-packages-settings.xml \
  -Dmaven.repo.local="$REPO" clean verify
```

记录 Surefire `tests/failures/errors/skipped`。`xq-platform-next` 再把 BOM 改回上一合格
版本冷仓验证回滚，然后固定回新版本。`python-learning-service` 只做冷仓升级。

结论写入 [`project-status.md`](project-status.md)，提交留在各消费者本机 git。
2026-08-26 已完成：xq `1.0.0 → 1.2.0`（14/0/0/0）+ 回滚后固定；pil `1.0.0 → 1.2.0`
（8/0/0/0）。
`1.2.0 → 1.3.0` 双参考消费者矩阵尚未执行；`v1.4.0` 发布后优先由真实产品消费者从
`1.3.0` 重放增量接入与一级回滚。不得用 Release workflow 内的生成项目替代独立消费者仓库证据。
