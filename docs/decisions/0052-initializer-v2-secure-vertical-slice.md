# ADR-0052：Initializer v2 安全纵向切片基线

- 状态：Accepted
- 日期：2026-08-27
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无
- 修订：ADR-0035/0036 的 Manifest v1 与旧模板保持可用；本 ADR 以新 schema 版本加性扩展，
  不改写既有结论

## 背景

Manifest v1 已能确定性生成项目骨架、PostgreSQL 变体与单表 CRUD，但 ADR-0036 有意把身份、
授权和 OpenAPI 留给消费者。真实消费者因此仍需手工完成一段高风险且高度重复的工作：拆分 API、
应用和持久化模型，为每条 SQL 绑定 Workspace，接入可信 JWT、成员校验、资源 scope、授权审计、
乐观锁、稳定错误码和真实 PostgreSQL 负向测试。

这段空白不应通过继续扩张 v1 模板来填补。v1 已是 1.x 已发布生成合同；把安全语义悄悄加入同一
schema 会使相同 manifest 在升级 CLI 后产生完全不同的项目边界。另一方面，直接生成
`@AinerAuthorize` 注解也不足以证明对象级授权：参考装配的注解首先是端点粗门禁，资源所有权、
ACTIVE membership 与 Workspace SQL 隔离仍必须由应用服务显式执行。

## 决策驱动因素

- 生成项目必须默认失败关闭，不能把 scope 当作资源成员关系；
- v1 manifest、生成字节和库调用方返回类型必须保持兼容；
- 生成结果需要独立于 Ainer reactor 编译和测试，不复制 Ainer 源码；
- 数据隔离、授权审计、HTTP/OpenAPI 和负向安全矩阵必须同时进入生成合同；
- 首个切片应足够窄，避免在没有真实消费者前预建树表、主子表、微服务拓扑或 UI。

## 备选方案

### 方案 A：继续扩展 Manifest v1

文件较少，但会改变已发布 schema 的默认安全、依赖和分层语义；同一 v1 manifest 在新旧 CLI 下
不再具有可解释的一致性。放弃。

### 方案 B：只生成 `@AinerAuthorize` 与 scope

实现成本低，但注解门禁不能替代 Workspace ACTIVE membership、对象归属查询与
`workspace_id` SQL 条件，也无法为每次高价值决策提供持久化审计。放弃。

### 方案 C：增加 Manifest v2 的窄安全预设

v1 保持原样；只有显式选择 v2 的消费者才进入更强的生成合同，并通过独立消费者门禁验证。
采用。

## 决策

### 1. Schema 与兼容入口

Initializer 新增 `ProjectManifest` 公共抽象与 `ManifestReader.readProject(Reader)` 多版本入口。
原有 `ManifestReader.read(Reader)` 继续只返回 `ManifestV1`，保持既有库消费者的源码级合同。
CLI 使用多版本入口并按 manifest 类型选择生成器。

首个 v2 只接受以下组合：

```yaml
schemaVersion: v2
preset: simple-service
accessControl: workspace
errorNamespace: CATALOG
project:
  name: Catalog Service
  groupId: dev.example.catalog
  artifactId: catalog-service
  version: 1.0.0
spring-boot: 4.1.1
ainner: 1.2.0
java: 25
database: postgresql
entities:
  - name: product
    fields:
      - name: name
        type: string(120)
      - name: sku
        type: string(64)
        unique: true
```

约束：

- `preset` 仅允许 `simple-service`；
- `accessControl` 仅允许 `workspace`；
- `database` 必须为 `postgresql`，且至少声明一个实体；
- `errorNamespace` 必须匹配 `[A-Z][A-Z0-9_]{1,31}`，由消费者拥有，不能冒充
  `AINER.*` 框架错误；
- v2 暂不接受 `fields.initial`；任何当前模板不能兑现的输入必须 fail-fast，不能静默忽略；
- PostgreSQL 标识符超过 63 字节、与内建列重名或归一化后重复时 fail-fast；过长的索引与
  约束名使用确定性 SHA-256 后缀，避免 PostgreSQL 静默截断碰撞。

### 2. 生成的纵向边界

每个实体生成明确的三层文件：

- `api`：请求/响应 DTO 与 Controller；不暴露 MyBatis 或持久化 Row；
- `application`：命令、应用 Record、分页结果、稳定错误码、应用服务与授权决策审计；
- `infrastructure`：Row 与显式 MyBatis Mapper；所有用户输入使用 `#{}` 参数绑定。

资源路径固定为 `/api/workspaces/{workspaceId}/<resources>`。每条资源查询、分页、更新和删除 SQL
都显式携带 `workspace_id`；唯一约束按 `(workspace_id, business_column)` 建立。分页页大小限制为
1–100，更新和删除要求 `version` 乐观锁。持久化 ID 使用 PostgreSQL `uuidv7()` 默认值，应用代码
不调用 `UUID.randomUUID()` 创建资源身份。

### 3. 安全与审计

每个应用用例必须按以下顺序失败关闭：

1. 主体只来自已经验证的 Bearer JWT；业务资源当前只接受 HUMAN 主体；
2. 读写分别要求 `<resources>.read` / `<resources>.write` scope；
3. 调用 Workspace 应用服务验证同一 subject 对目标 Workspace 的 ACTIVE membership；
4. 通过后才执行带 `workspace_id` 的资源 SQL。

高价值访问决策写入实体专属 append-only audit 表。审计服务使用 `REQUIRES_NEW`，记录
Workspace、资源、subject、动作、ALLOW/DENY、原因码、requestId 与时间，不保存 Token 或请求体；
审计插入失败会传播并阻断受保护用例。ALLOW 表示授权检查通过，不表示后续业务事务一定成功。

### 4. HTTP 与 OpenAPI

生成项目默认启用 `ainer-starter-security`，仅公开 `/actuator/health`；`/api/ping`、业务 API 和
`/v3/api-docs` 都需要有效 JWT。HTTP status 是权威语义，生成的消费者错误使用稳定字符串，例如
`CATALOG.PRODUCT.NOT_FOUND`、`CATALOG.PRODUCT.ACCESS_DENIED` 与
`CATALOG.PRODUCT.CONCURRENT_MODIFICATION`。

运行时 OpenAPI 由 BOM 管理的 `springdoc-openapi-starter-webmvc-ui` 提供。生成 POM 开启 Java
parameter metadata，并在 Controller 上显式命名路径和查询参数，避免依赖编译器默认行为。

### 5. 独立消费者门禁

`verify-initializer-consumer.sh` 在既有 v1 普通、PostgreSQL、CRUD 三通道之外增加 v2 安全通道：

- 同 manifest 两次生成逐字节一致，`diff` 无漂移；
- 生成项目不复制 Ainer 源码，使用自己的 Maven 3.9.16 Wrapper；
- API 不暴露持久化 Row，应用层不暴露 MyBatis 抽象，SQL 不使用 `${}`；
- 从空库执行 Ainer Workspace migration 与消费者 migration；
- 使用 RSA 真签名 JWT 验证 401、缺 scope 403、非成员拒绝、跨 Workspace 404、分页上限、
  乐观锁 409、DENY 审计落库和受保护 OpenAPI；
- PostgreSQL Testcontainers 全部测试必须 0 skipped。

## 后果

### 正面

- 新消费者可以从安全、可运行的纵向切片起步，而不是从开放 CRUD 再手工补权限；
- v1 与 v2 的行为边界清晰，升级 CLI 不会暗改旧 manifest；
- 数据隔离、授权与审计同时落到应用服务、SQL、migration 和负向测试，不依赖口头约定。

### 负面与风险

- 每个实体生成的文件数量和测试启动成本显著高于 v1；
- `simple-service + workspace` 不是通用拓扑，不能用它宣称模块化单体、树表、主子表或服务化生成
  已完成；
- 当前每个测试类各自启动 PostgreSQL，优先换取隔离与证据清晰；后续若优化复用，仍必须保持
  空库 migration 与 0-skipped 语义。

## 安全、数据与隐私

身份只来自已验证 JWT，外部身份请求头不进入生成合同。所有资源 SQL 都绑定可信
`workspace_id`，scope 不能替代 ACTIVE membership。审计不保存 Token、prompt、请求体或响应正文。
生成测试密钥只存在于 test source，生成 README 明确禁止用于生产。

## 运维与迁移

Manifest v1 无迁移要求，CLI 仍可生成原有项目。采用 v2 的新项目需要 PostgreSQL 18、可验证的
JWT issuer/JWK set 与 Workspace migration；缺少 JWT decoder 时应用必须启动失败。v2 尚未发布前，
不得把开发分支 SNAPSHOT 当成稳定制品；发布后远端 CLI 仍需重跑同一四通道门禁。

## 验收记录

- 2026-08-27：Initializer/Core/CLI 定向测试 52 项（含新增 v2 边界测试）全部通过，0 skipped；
- 2026-08-27：独立生成项目在 PostgreSQL 18.3 上通过 3 项测试，覆盖真实 JWT 安全 CRUD 与
  受保护 OpenAPI，0 failure / 0 error / 0 skipped；
- 2026-08-27：`verify-initializer-consumer.sh` 四通道通过；普通、PostgreSQL、CRUD、secure-v2
  四个独立生成项目共 12 项测试，0 failure / 0 error / 0 skipped；
- 2026-08-27：`./mvnw clean verify` 通过，28 模块、541 tests / 0 failure / 0 error /
  0 skipped；动态总数继续只在 `docs/project-status.md` 维护。

## 参考

- [ADR-0035](0035-project-initializer-and-manifest-v1-baseline.md)
- [ADR-0036](0036-initializer-crud-generation.md)
- [ADR-0037](0037-post-greenfield-authorization-baseline.md)
- [ADR-0020](0020-postgresql-native-greenfield-baseline.md)
- [`docs/design/ainer-scaffold-design.md`](../design/ainer-scaffold-design.md)
