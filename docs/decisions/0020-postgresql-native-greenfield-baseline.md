# ADR-0020：PostgreSQL Native-First Greenfield 数据基线

- 状态：Accepted
- 日期：2026-07-26
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无

## 背景

Ainer 是面向未来的新脚手架，不是旧项目的数据库兼容层。早期实现为了快速验证垂直切片，在
Java 中直接使用 `UUID.randomUUID()` 生成 UUIDv4，Workspace 与 AI runtime 还把可信 JWT 中的
`tenant_id` 保存为 `varchar(128)`。这些选择证明了模块边界和 PostgreSQL 持久化链路，但不应反过来
限制长期规范。

PostgreSQL 18 已提供原生 `uuidv7()`、UUID 版本/时间提取、temporal constraints、virtual
generated columns、DML `RETURNING OLD/NEW`、B-tree skip scan、受控 `COPY` 错误上限和更丰富的
在线约束演进能力。如果 Ainer 继续以“兼容当前实现”为默认，会在正式发布前固化本可避免的
随机索引写入、字符串 tenant、应用重复计算和弱数据库不变量。

截至本决策日期，PostgreSQL 19 仍是 Beta 2。它适合进入前向验证和能力观察，但不能成为
生产稳定性声明。

## 决策驱动因素

- Ainer 是 greenfield 产品，不承担 yudao、MySQL、H2、旧表结构或旧 ID 策略兼容责任；
- 新项目应优先使用 PostgreSQL 18 的稳定原生能力，而不是最低公分母 SQL；
- 持久化 ID 需要分布式唯一、时间有序、可在 B-tree 中保持更好的写入局部性；
- `tenant_id` 是 Ainer 自有安全边界，不能同时表达内部 UUID 和任意外部 opaque string；
- 数据库约束应承担可表达的不变量，减少应用层重复和竞态窗口；
- 前向设计不能等同于在生产使用仍处于 Beta 的数据库版本；
- 上游 OAuth/WebAuthn 协议表必须保持官方互操作性，这属于协议正确性而非旧项目兼容。

## 备选方案

### 方案 A：保持 UUIDv4 和字符串 tenant

改动最少，但让长期脚手架迁就几天内形成的实现，继续产生随机 B-tree 写入，并让内部 tenant 与
外部 identity provider 标识混为一谈。拒绝。

### 方案 B：所有 ID 都使用数据库 sequence

索引局部性好，但只保证单数据库内唯一，暴露可枚举数量，并削弱拆分、事件和离线标识能力。
拒绝作为通用身份策略；纯数据库内部序号仍可按规范例外使用。

### 方案 C：Ainer 持久化身份默认 UUIDv7

保留原生 `uuid`、分布式唯一和不可轻易枚举的特性，同时获得时间有序的索引局部性。PostgreSQL
18 可以原生生成和校验。采用。

### 方案 D：立即以 PostgreSQL 19 Beta 作为生产基线

能更早使用新能力，但 Beta 行为和 API 仍可能变化，生态与运维证据不足。拒绝。PG19 进入
实验性 CI；GA 后再通过新决策提升最低版本。

## 决策

### 1. 数据库版本与兼容边界

1. Ainer 的最低且唯一生产数据库基线是 PostgreSQL 18，不提供 MySQL/H2/旧 PostgreSQL 方言。
2. 业务 SQL、DDL、约束、索引和运维可以直接使用 PostgreSQL 18 稳定能力。
3. “不兼容旧项目”不表示放弃正常发布安全。已运行环境的 schema 仍由 Flyway、expand-contract、
   备份恢复和滚动发布纪律保护。
4. 在 1.0 前允许通过一次明确、可审计的 baseline reset 清理早期 Ainer migration，但必须同步
   重建所有开发/测试数据库、更新 checksum 和验证脚本；不得静默改写共享环境历史。
5. PostgreSQL 19 只进入非阻塞前向验证和设计观察；GA、JDBC/Testcontainers/备份恢复证据齐备后
   才能成为生产基线。

### 2. UUIDv7

6. 新增 Ainer 自有聚合、实体、事件、审计、outbox 和操作 request 的持久化主键默认使用
   UUIDv7。
7. 新表的 Ainer-owned ID 默认由 PostgreSQL 生成并校验：

   ```sql
   id UUID NOT NULL DEFAULT uuidv7(),
   CONSTRAINT ck_example_id_version
       CHECK (uuid_extract_version(id) = 7)
   ```

8. 插入方通过 `INSERT ... RETURNING id` 取得数据库生成值。需要在持久化前形成稳定 ID 的少数
   用例必须依赖统一 `AinerIdGenerator` 端口；其实现必须生成 RFC 9562 UUIDv7、可注入 `Clock`、
   可测试并处理同毫秒并发。业务代码不得直接调用 `UUID.randomUUID()` 生成持久化身份。
9. UUIDv7 只表达 ID 生成时间。业务仍必须保存 `created_at`、`occurred_at` 等权威时间，并使用
   `(business_time, id)` 稳定排序。
10. secret、Token、nonce、恢复码和 PKCE 材料使用各自协议要求的高熵随机值，不能因为 UUIDv7
    方便而复用。
11. 对外暴露 UUIDv7 会泄露大致生成时间；确有时序隐私要求的接口使用独立 public identifier，
    不能降低内部主键设计。

### 3. tenant 身份

12. Ainer 自有 `tenant_id` 在数据库、Java 领域、JWT claim 投影和内部事件中统一为 UUID。
13. 外部身份源的 tenant 标识必须保存为独立的
    `(issuer, external_tenant_id)` 映射，不能塞入 Ainer `tenant_id`。
14. Workspace 和 AI runtime 当前 `varchar(128)` tenant 是 1.0 前必须移除的实现债，不是允许
    新模块复制的兼容模式。

### 4. PostgreSQL 18 Native-First 能力

15. 有效期、排班、订阅、占用等真正的时间区间优先使用 range/multirange，并评估
    `WITHOUT OVERLAPS` 与 `PERIOD` 外键，而不是先写“查询后插入”的竞态逻辑。
16. 确定性、同一行可推导且需要统一读取语义的值可以使用显式 `VIRTUAL` 或 `STORED` generated
    column；状态、金额、tenant、权限和审计事实不得变成生成列。
17. 更新后需要旧值与新值时优先评估 PostgreSQL 18 `RETURNING OLD/NEW`，避免额外查询；它不能
    取代独立的持久化安全审计。
18. 批量导入先进入 staging table；允许容错时必须使用有限 `REJECT_LIMIT` 并保存拒绝统计，
    不允许无限跳过错误。
19. skip scan、BRIN、GIN、GiST、部分索引、覆盖索引和分区按真实查询与规模选择；Native-First
    不等于无证据地启用所有特性。
20. AIO、data checksums、VACUUM、统计、备份与恢复属于 PostgreSQL 运行基线，在运维文档和环境
    验收中落实，不混入业务 DDL。

### 5. 协议与扩展

21. Spring Authorization Server、Spring Security WebAuthn 等官方 JDBC schema 保持上游要求；
    Ainer-owned 生命周期和审计表仍使用本决策。
22. `pgvector`、PostGIS 等 extension 必须有所有权、版本、备份、升级和退出方案；“AI 原生”不
    等于默认把所有扩展装入业务库。

## 后果

### 正面

- 新增数据模型直接体现 PostgreSQL 18，而不是兼容性最低公分母；
- UUID 主键保持分布式唯一，同时改善 B-tree 写入局部性和自然排序；
- Ainer tenant 在所有自有边界上拥有唯一、可验证的类型；
- 时间区间、派生数据、批量导入和变更返回可以使用数据库原生语义；
- 当前实现不再拥有否决长期设计的权力。

### 负面与风险

- 现有持久化代码和测试中的 `UUID.randomUUID()` 需要逐项区分并迁移；
- 数据库生成 ID 要求 Repository 支持 `RETURNING`，部分领域工厂需要调整；
- JDK 25 没有标准 UUIDv7 工厂；若实现应用生成器，必须自行严格验证或采用经审查依赖；
- Workspace/AI tenant UUID 化会改变 JWT 投影、领域值对象、Mapper、migration 和测试；
- UUIDv7 暴露近似生成时间，不能直接用于需要时序隐私的公开标识；
- PG18 特性越深入，未来切换到非 PostgreSQL 数据库的成本越高；这是明确接受的取舍。

## 安全、数据与隐私

- UUIDv7 不是 secret，不得用于认证或授权凭证；
- Ainer `tenant_id` 必须来自受验证 issuer 的 claim 并解析为 UUID，解析失败时关闭访问；
- 外部 tenant 映射必须绑定 issuer，避免两个身份源字符串碰撞；
- generated column、range、JSON 和导入 staging 仍受数据分级、tenant 和保留策略约束；
- 数据库原生 OAuth authentication 不替代应用 OAuth 2.1/OIDC，也不改变业务身份边界。

## 运维与迁移

1. 先更新长期规范和测试门禁；
2. 实现统一 UUIDv7 生成/返回策略，分类替换持久化 ID 的 `UUID.randomUUID()`；
3. 新表立即使用 `DEFAULT uuidv7()` 与版本 CHECK；
4. 设计 Workspace/AI `tenant_id` UUID 化和干净 baseline；
5. 在 PostgreSQL 18 Testcontainers 与本地真实数据库验证 UUID 版本、并发、索引和 migration；
6. 增加 PostgreSQL 19 非阻塞测试任务，PG19 GA 前不写生产承诺。

当前尚未形成稳定版或生产数据，Ainer 不承诺保留早期开发数据库内容。任何 baseline reset 仍需
显式执行说明，防止开发者误把 checksum 变化当作普通升级。

## 验收证据

已完成：

- PostgreSQL 18 官方能力与 PG19 Beta 状态核对；
- 当前 migration 与生产代码 UUID/tenant 类型静态盘点；
- 数据库规范 1.2 与本 ADR 建立目标边界。

尚未完成：

- Ainer UUIDv7 generator/`RETURNING` adapter；
- 持久化 UUIDv4 调用迁移；
- Workspace/AI tenant UUID 化；
- UUIDv4/v7 B-tree 写入、索引体积和分页基准；
- PostgreSQL 19 CI 兼容任务；
- 1.0 clean baseline reset。

## 参考

- [PostgreSQL 18 UUID functions](https://www.postgresql.org/docs/18/functions-uuid.html)
- [PostgreSQL 18 release notes](https://www.postgresql.org/docs/18/release-18.html)
- [PostgreSQL 18 constraints](https://www.postgresql.org/docs/18/ddl-constraints.html)
- [PostgreSQL 18 generated columns](https://www.postgresql.org/docs/18/ddl-generated-columns.html)
- [PostgreSQL 18 DML RETURNING](https://www.postgresql.org/docs/18/dml-returning.html)
- [PostgreSQL 18 COPY](https://www.postgresql.org/docs/18/sql-copy.html)
- [PostgreSQL 19 Beta 2 announcement](https://www.postgresql.org/about/news/postgresql-19-beta-2-released-3350/)
- [Java 25 UUID API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/UUID.html)
