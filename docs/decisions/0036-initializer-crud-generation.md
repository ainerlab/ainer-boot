# ADR-0036：Initializer CRUD v1 生成与 TTCRUD 门禁

## Status

- 状态：Accepted（2026-08-09 实现闭环后升级）
- 日期：2026-08-09
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无
- 实现授权：本 ADR 授权在 `reset/0033-greenfield` 分支扩展 `ainer-initializer` 的
  Manifest v1 生成清单，加入单表 CRUD 的 migration、实体、Mapper、Service、Controller
  与 PostgreSQL 集成测试；不授权树表/主子表、OpenAPI、管理页面、RBAC 或在线生成
- 实现记录：`entities` 解析与 6 类 CRUD 生成已交付；`verify-initializer-consumer.sh`
  第三通道与 `scripts/measure-ttcrud.sh`（实测 124s，门禁 1800s）已接入 CI。

## Context

P2 Create & Generate 的退出门禁（`docs/design/ainer-scaffold-design.md` §12）包含"纵向
CRUD"与 TTCRUD（从 manifest 到含 PostgreSQL migration、授权、API、测试的可运行纵向
CRUD ≤ 30 分钟）。ADR-0035 已交付项目骨架、preview/diff 与 golden consumer 门禁，
但明确不生成 CRUD（其决策 7 将 CRUD 列为非目标）。

生成器已具备：确定性文件树、fail-fast manifest 校验、`database: postgresql` 变体
（`ainner-starter-persistence` + Testcontainers 集成测试）、普通变体与 postgres 变体
双通道 consumer 门禁（`verify-initializer-consumer.sh`）。在此基础上增量扩展不再需要
改变既有的确定性、预览与安全语义。

## 决策

### 决策 1. Manifest v1 新增 `entities` 段

v1 增加可选顶层 `entities`，YAML 列表，每个实体：

| 字段 | 必需 | 语义 |
|---|---|---|
| `name` | 是 | 实体名，`[A-Za-z][A-Za-z0-9]*`，用作类名前缀与资源路径（复数） |
| `fields[*].name` | 是 | 字段名，snake_case 或驼峰；作为列名（snake_case）与 Java 字段 |
| `fields[*].type` | 是 | `string(size)`、`int`、`long`、`decimal`、`boolean`、`instant`、
  `uuid`、`text`；`uuid`/`text` 不支持参数 |
| `fields[*].nullable` | 否 | 默认 `false`（生成 NOT NULL）；`true` 生成可空（YAML 字面量
  `null` 会解析为 null 键，因此用 `nullable` 而非 `null`） |
| `fields[*].unique` | 否 | 默认 `false`；`true` 生成唯一约束 |
| `fields[*].comment` | 否 | 列注释，默认取字段名 |

约束：

- `id` 为保留字段名，生成器自动生成 `id uuid` 主键（PostgreSQL `uuidv7()` 默认值，
  符合 ADR-0020）；
- `entities` 不允许与 `database: none` 组合——CRUD 只针对 PostgreSQL 变体；
- 实体至少一个业务字段；
- 未知字段、重复字段名、`{{` 模板字面量与类型不合法一律 fail-fast。

### 决策 2. 生成的 CRUD 文件清单

为每个实体在既有 postgres 变体文件之外追加：

| 文件 | 说明 |
|---|---|
| `src/main/resources/db/migration/V1__init.sql` | 一张表：`id uuid primary key default uuidv7()`、各业务列、
  `created_at`/`updated_at timestamptz not null`，命名约束 `ck_<table>_<col>` |
| `<pkg>/crud/<Name>Entity.java` | MyBatis-Plus 普通持久化实体 |
| `<pkg>/crud/<Name>Mapper.java` | `extends BaseMapper<...>` + 自定义 H2 无关查询 |
| `<pkg>/crud/<Name>ApplicationService.java` | 构造器注入 Mapper；create/get/page/update/delete；
  `BusinessException(StandardErrorCode.NOT_FOUND)` 语义；create 走 `INSERT ... RETURNING id`
  由 PostgreSQL 生成 uuidv7 主键（应用不制造持久化身份） |
| `<pkg>/crud/<Name>Controller.java` | `GET /api/<plural>`（分页）、`GET /api/<plural>/{id}`、
  `POST /api/<plural>`, `PUT /api/<plural>/{id}`, `DELETE /api/<plural>/{id}`；
  全部返回 `ApiResponse` envelope，携带 `X-Request-Id` |
| `<pkg>/crud/<Name>CrudIntegrationTest.java` | `@SpringBootTest` + Testcontainers：
  create→get→update→list→delete 全链路断言，0 skipped |

分页参数：`size`（默认 20，上限 100）、`page`（默认 1）。排序字段固定 `created_at desc`，
v1 不暴露任意排序字段（防注入面最小化）。所有 SQL 使用参数绑定。

### 决策 3. 类型映射

| manifest 类型 | MySQL 无（PostgreSQL 列） | Java 类型 | MyBatis 映射 |
|---|---|---|---|
| `string(n)` | `varchar(n)` | `String` | 默认 |
| `text` | `text` | `String` | 默认 |
| `int` | `integer` | `Integer` | 默认 |
| `long` | `bigint` | `Long` | 默认 |
| `decimal` | `numeric(19,4)` | `BigDecimal` | 默认 |
| `boolean` | `boolean` | `Boolean` | 默认 |
| `instant` | `timestamptz` | `Instant` | `InstantTypeHandler`（MyBatis-Plus 内置时区处理） |
| `uuid` | `uuid` | `java.util.UUID` | `UuidTypeHandler`（`ainner-starter-persistence` 已注册） |

### 决策 4. 生成安全与确定性延续

- 生成 CRUD 文件與既有骨架遵循同一确定性：两次生成字节级一致；
- CRUD 文件只在 `database: postgresql` 且 `entities` 非空时出现；
- Mapper 中禁止拼接 SQL、禁止 `${}` 映射；全部参数 `#{}` 绑定；
- 生成的 Controller 不带 `@PreAuthorize`/鉴权注解——身份与授权语义由消费者在其
  `ainner-starter-security` 装配层决定，生成器不替消费者假定安全模型不会预置未知
  的实现细节；文档注明"生成物尚未接入身份验证与授权，上线前必须装配"。

### 决策 5. 验证门禁

`verify-initializer-consumer.sh` 增加第三通道（entity + postgres 变体）：

- 生成：确定性两轮 diff；
- 无 Ainer 源码复制检查（对全部生成 Java 文件）；
- 真实 PostgreSQL 集成测试 0 skipped（含 CRUD 全链路 create→get→update→list→delete→404）；
- 验证 `pom.xml` 中不含 `mybatis-plus-generator`（人工模板，不引代码生成器依赖）。

新增 `scripts/measure-ttcrud.sh` 并接入 CI：从含 `entities` 的 manifest 到 Testcontainers
CRUD 集成测试全绿，官方口径实测 124 秒（门禁 1800 秒）。

## 备选与取舍

- **不引入 mybatis-plus-generator**：生成品为确定性手写模板；代码生成器编译期
  反向读取数据库、网络或环境，违背生成安全与确定性；
- **不为 skeleton 预置鉴权**：v1 保持生成物对安全装配的显式声明，避免生成器
  假定模型与其外部认证绑定（安全相关 ADR 未进 initializer 契约）。

## Consequences 与迁移

- `entities` 是新可选字段：现有 manifest 不受影响（向后兼容）；
- 扩展字段类型/新约束需先改本 ADR 或新增 ADR，再改解析、再改模板；
- 树表/主子表、OpenAPI 与管理页面继续在各纵向 ADR 阶段交付；
- 生成的 CRUD 集成测试必须 uses Testcontainers；没有 Docker 的 CI gate 会跑 0
  skipped 双通道（本地执行 consumer 门禁需要 Docker）。

## 相关文档

- `docs/design/ainer-scaffold-design.md` §12.1（TTCRUD 口径）；
- ADR-0035（Manifest v1 与生成确定性基线）；
- ADR-0020（PostgreSQL/UUIDv7）。