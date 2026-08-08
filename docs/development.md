# Ainer 本地开发手册

> 文档类型：开发操作 · 状态：生效 · 最近核对：2026-07-30 · 适用版本：`0.1.x`

## 1. 环境要求

| 工具 | 要求 | 用途 |
|---|---|---|
| JDK | 25，允许范围 `[25,26)` | 编译与运行 |
| Maven Wrapper | 锁定 Maven 4.0.0-rc-6 preview | Reactor 构建、安装与发布 |
| 系统 Maven | 3.9+，仅兼容门禁需要 | 验证下游 Maven 3 消费者 |
| PostgreSQL | 18.x | 本地运行与迁移验证 |
| Docker-compatible runtime | 建议安装 | 执行 PostgreSQL Testcontainers 集成测试 |
| Git | 当前维护版本 | 版本控制 |

JDK 和 Maven 版本由 Maven Enforcer 强制检查。Maven 4.0.0-rc-6 仍是 preview；生产者构建必须
使用仓库内 Wrapper，不能用全局 Maven 替代。系统 Maven 3.9+ 只供
`scripts/verify-maven-consumers.sh` 验证已安装制品的下游兼容性。未经单独兼容性验证，不承诺
其他 JDK、数据库或 Windows 原生环境可用。

```bash
java -version
./mvnw --version
git status --short --branch
```

## 2. 第一次验证

从仓库根目录运行：

```bash
./mvnw clean verify
```

如果 Docker 不可用，带 `disabledWithoutDocker = true` 的 Testcontainers 测试会跳过。此结果可用于本地快速开发，但不能作为发布候选的完整数据库验证，详见 [`testing.md`](testing.md)。

局部快速反馈可以执行 `./mvnw test`，或通过 `-pl ... -am` 限定模块执行 `test` / `verify`。
`install` 不属于日常开发循环，只用于 golden consumer 或发布前的本地仓库消费验证。

## 3. 模块职责

```text
ainer-dependencies                  版本与依赖管理
ainer-framework/ainer-core         无框架核心契约
ainer-framework/ainer-spring       Spring 通用装配
ainer-framework/ainer-security     无框架身份契约
ainer-framework/ainer-starter-*    可复用自动配置
ainer-module-identity              身份与租户账号
ainer-module-workspace             租户资源与成员授权
ainer-module-ai-runtime            模型网关与调用审计
ainer-server                       业务应用发行物
ainer-authorization-server         OAuth 2.1/OIDC 发行物
ainer-offstate-app                 P1 最小可消费应用（无外部服务冒烟）
ainer-initializer                  P2 离线确定性生成内核（Manifest v1，ADR-0035）
ainer-initializer-cli              P2 离线 CLI：preview / init / diff
```

业务模块内部按 feature 组织 `api -> application -> domain`，infrastructure 实现 application/domain 定义的端口。framework 不得反向依赖业务模块。

## 4. 运行业务应用

准备一个空 PostgreSQL 数据库。Flyway 会在启动时执行 Workspace 和 AI runtime migration。

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/ainer
export SPRING_DATASOURCE_USERNAME=ainer
export SPRING_DATASOURCE_PASSWORD='local-only-password'
export AINER_SECURITY_ISSUER_URI=https://auth.local.example
export AINER_SECURITY_AUDIENCES=ainer-api
./mvnw -pl ainer-server -am spring-boot:run
```

默认端口由 Spring Boot 决定，当前为 `8080`。健康检查：

```bash
curl -i http://127.0.0.1:8080/actuator/health
curl -i http://127.0.0.1:8080/api/platform/info
```

Resource Server 默认启用。`AINER_SECURITY_RESOURCE_SERVER_ENABLED=false` 只用于隔离的公开端点验证，不是业务 API 的本地免认证模式。

## 5. 运行 Authorization Server

Authorization Server 使用独立数据库和外部 RSA PEM 密钥。完整安全说明见 [`security.md`](security.md)。

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/ainer_auth
export SPRING_DATASOURCE_USERNAME=ainer_auth
export SPRING_DATASOURCE_PASSWORD='local-only-password'
export AINER_AUTHORIZATION_SERVER_ISSUER=https://auth.local.example
export AINER_AUTHORIZATION_SIGNING_KEY_ID=ainer-local-1
export AINER_AUTHORIZATION_PRIVATE_KEY_LOCATION=file:/absolute/path/to/private.pem
export AINER_AUTHORIZATION_PUBLIC_KEY_LOCATION=file:/absolute/path/to/public.pem
./mvnw -pl ainer-authorization-server -am spring-boot:run
```

默认监听 `9000`。issuer 必须是显式 HTTPS URL；私钥不得提交到仓库。

Ainer Admin 本地联调需要 `dev` profile 下显式创建 public client 与开发身份，并通过同源入口
复用浏览器 cookie session。固定 URI、环境变量、SDK 生成和完整 PKCE/logout 流程见
[`ainer-admin-integration.md`](ainer-admin-integration.md)；不要为本地联调开启全局 CORS。

## 6. 日常开发循环

优先运行受影响模块及其依赖：

```bash
./mvnw -pl ainer-module-workspace -am test
./mvnw -pl ainer-module-identity -am test
./mvnw -pl ainer-module-ai-runtime -am test
```

提交前运行完整验证：

```bash
./mvnw clean verify
git diff --check
git status --short
```

修改数据库时，还要核对 migration 重放；修改 HTTP 时验证真实状态码、响应体和 `X-Request-Id`；修改身份时至少覆盖无 Token、错误 audience、错误 `token_profile` 和权限不足。

## 7. 新增业务能力

1. 确定所属业务能力与数据所有者。
2. 在 application/domain 中定义稳定输入、结果、错误码和端口。
3. 编写领域或应用测试，先覆盖允许与拒绝路径。
4. 在 infrastructure 中实现 MyBatis/MyBatis-Plus、远程服务或 provider adapter。
5. 在 api 中映射 HTTP，不把 Controller DTO 直接传入领域层。
6. 增加 ArchUnit 规则或扩展现有规则，保护依赖方向。
7. 更新文档、配置字典、数据库手册和状态快照。

跨模块同步查询通过显式契约；反向通知通过可靠事件。不得为了复用而注入另一个模块的 Service 实现或直接查询对方表。

### 7.1 持久化开发

- 简单、单表且资源归属键清晰、等效的 CRUD/分页可以只在 infrastructure Mapper 使用
  `BaseMapper`、Wrapper 与 MyBatis-Plus Page；Repository 端口不得暴露这些类型。
- 复杂 PostgreSQL SQL、锁、CTE、`RETURNING`、审计和稳定游标继续写显式 Mapper
  方法与 XML。现有 XML 不需要迁移。
- Mapper XML 配置使用 `mybatis-plus.mapper-locations`；不要继续新增旧的
  `mybatis.mapper-locations`。
- 新增数据库生成 ID 的 Row 使用 `IdType.AUTO`，由 PostgreSQL `DEFAULT uuidv7()` 生成并
  回填。不得使用 `ASSIGN_ID` / `ASSIGN_UUID`。
- 无 tenant 拦截器；每个资源查询都必须显式绑定可信归属键（`workspace_id`/`account_id` 等）。
  分页请求在 API 边界校验且最大单页 100。
- 不默认使用 `IService`、`ServiceImpl`、ActiveRecord、逻辑删除或 MetaObject 自动填充。
- 本轮没有引入 MyBatis-Plus 代码生成器；生成代码必须等待 Project Initializer 的模板和
  golden consumer 设计。

完整规则见
[ADR-0028](decisions/0028-mybatis-plus-infrastructure-baseline.md) 与
[`database-design-standard.md`](database-design-standard.md)。

## 8. 新增 Starter

- Starter 只封装通用装配，不放业务表、业务 DTO 或业务规则。
- 使用 `@AutoConfiguration` 与 `AutoConfiguration.imports`。
- 属性使用 `@ConfigurationProperties`，默认值必须安全。
- 必须有启用、禁用、缺失依赖和错误配置测试。
- 新依赖版本进入 `ainer-dependencies`，业务 POM 不单独写版本。

## 9. 完成定义

- 行为、错误和权限边界有自动化测试；
- PostgreSQL 行为不使用 H2 代替；
- 数据变更只通过新的 Flyway migration；
- 配置、日志和错误不泄露秘密；
- README、专题文档、ADR、状态和 Changelog 按影响更新；
- 完整 Reactor 测试通过，并明确记录任何跳过项；
- 没有无关文件或他人的修改被覆盖。
