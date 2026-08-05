# ADR-0028：MyBatis-Plus 基础设施增强基线

- 状态：Accepted
- 日期：2026-07-30
- 决策者：Ainer 项目维护者
- 取代：ADR-0002 第 3 项中“不引入 MyBatis-Plus”的 M1 阶段性选择
- 被取代：无

## 背景

ADR-0002 在 M1 只有一个 PostgreSQL 业务切片时，选择 MyBatis Spring Boot Starter 4.0.0，
并明确不引入 MyBatis-Plus。该选择当时用于控制依赖和抽象范围，不是永久排斥
MyBatis-Plus。M2 出现第二个数据消费者后，公共 MyBatis、Flyway、PostgreSQL 与 UUID 装配已经
进入 `ainer-starter-persistence`。

Ainer Boot 的目标已经从验证单个垂直切片转向可由 `xq-platform-next` 等外部项目消费的脚手架。
简单表的基础 CRUD 和统一分页如果全部重复编写 Mapper SQL，会增加生成器、样板代码和长期维护
成本。与此同时，Ainer 现有持久化包含 PostgreSQL CTE、`FOR UPDATE`、`SKIP LOCKED`、advisory
lock、`RETURNING`、outbox、审计归档和稳定游标等数据库原生语义；这些不能为追求统一 API 而
改写成不可审查的通用 Wrapper。

MyBatis-Plus 已提供 Spring Boot 4 专用 starter。Ainer 已用 JDK 25、Maven 4 和真实
PostgreSQL 18.3 完成兼容性原型，因此可以在不改变领域、应用、事务和授权边界的前提下，把它
作为 MyBatis 的基础设施增强层。

## 决策驱动因素

- 减少简单表 CRUD 与分页的重复实现，为 Project Initializer 和首个外部消费者提供稳定基线；
- 保留 PostgreSQL 18 Native-First、显式 SQL、Flyway 和数据库约束的权威地位；
- 保持 application、domain 与 API 不依赖 ORM 类型；
- tenant 授权必须继续由可信身份、资源关系和显式查询条件共同保证；
- UUIDv7 必须由 PostgreSQL `DEFAULT uuidv7()` 或 Ainer 明确的 ID 端口产生；
- 依赖、许可证和 Boot 4 兼容性必须可审计并由真实数据库验证。

## 备选方案

### 方案 A：继续只使用原生 MyBatis Starter

已有复杂 SQL 无需变化，依赖最少；但脚手架消费者仍需为普通 CRUD、分页和映射重复编写大量
基础代码，也无法形成一致的生成边界。它仍可工作，但不再是当前脚手架阶段的最佳默认。拒绝
作为统一基线。

### 方案 B：全面改用 MyBatis-Plus 应用范式

让业务 Service 继承 `IService` / `ServiceImpl`，让 application 或 API 直接接收 `Wrapper`、
`Page`，并把现有 XML 全部改写为链式查询。这样能最大化框架 API 使用率，却会把 ORM 类型扩散到
业务契约，弱化显式 tenant、锁、审计和 PostgreSQL 原生语义。拒绝。

### 方案 C：引入 MyBatis-Plus，但限制为 infrastructure 增强

简单 CRUD 和受控分页可使用 `BaseMapper`、Wrapper 与分页插件；复杂查询继续使用显式 Mapper
方法和 XML，Repository 端口、应用事务与领域模型不变。采用。

### 方案 D：同时提供“原生 MyBatis Starter”和“MyBatis-Plus Starter”

双 starter 会产生自动配置、版本、文档、测试和消费者组合矩阵，也会让同一项目在两个持久化
基线之间摇摆。当前没有独立消费者证明这种复杂度有价值。拒绝；Ainer 只维护统一的
`ainer-starter-persistence`。

## 决策

### 1. 依赖与装配

1. `ainer-starter-persistence` 使用
   `com.baomidou:mybatis-plus-spring-boot4-starter:3.5.17`，不使用面向 Spring Boot 2/3 的
   starter。
2. 显式依赖 `com.baomidou:mybatis-plus-jsqlparser:3.5.17`。其当前解析运行时为
   `com.github.jsqlparser:jsqlparser:5.2`。
3. 配置前缀使用 `mybatis-plus.*`；业务 Mapper XML、Flyway migration、PostgreSQL JDBC 和
   Ainer UUID TypeHandler 继续由统一 persistence starter 装配。
4. 本次不引入 `mybatis-plus-generator`。代码生成器必须在 Project Initializer 阶段单独设计
   模板所有权、可重复生成、升级和 golden consumer 门禁，不进入应用运行时。

### 2. 允许使用的范围

5. `BaseMapper`、`Wrappers`、MyBatis-Plus `Page` 和相关注解只允许出现在业务模块的
   `infrastructure` 持久化适配器与其测试中。
6. 简单、单表、权限条件清晰的 CRUD 和分页可以使用 MyBatis-Plus。Repository 端口仍以 Ainer
   领域或应用类型表达结果；application、domain 和 HTTP API 不得暴露 MyBatis-Plus 的 Mapper、
   Wrapper、Page、注解或异常。
7. 不把 `IService`、`ServiceImpl`、ActiveRecord 或通用 CRUD Controller 作为 Ainer 默认
   应用范式。事务边界继续位于应用用例，聚合不变量和错误映射不能下沉给框架基类。
8. 现有自定义 XML 与 MyBatis-Plus Mapper 共存。CTE、锁、`RETURNING`、advisory lock、
   outbox 领取/确认、审计归档、热冷合并、稳定游标和其他 PostgreSQL 原生或安全敏感查询继续使用
   显式 SQL；不为“统一写法”而改写。

### 3. ID、tenant 与数据政策

9. MyBatis-Plus 全局 ID 类型固定为 `IdType.AUTO`。它用于让空 ID 的 insert 省略主键值，由
   PostgreSQL `DEFAULT uuidv7()` 生成并通过 JDBC generated keys 回填；这是 ADR-0020
   “数据库生成并返回 ID”语义在简单 `BaseMapper` insert 中的适配器实现。需要显式取得旧值、
   新值或多个返回列的写入仍使用 PostgreSQL `RETURNING`。不得使用 MyBatis-Plus 默认的
   `ASSIGN_ID` 或 `ASSIGN_UUID` 生成 Ainer 持久化身份。
10. 必须在持久化前获得 ID 的少数用例继续遵守 ADR-0020，使用统一 `AinerIdGenerator` 端口；
    不能以 ORM 便利绕过 UUIDv7 规则。
11. MyBatis-Plus tenant interceptor 当前不启用。所有租户资源 Repository 签名和 SQL/Wrapper
    仍必须显式接收并绑定可信 tenant；未来即使增加 tenant interceptor，也只能作为纵深防御，
    不能充当身份、资源成员关系或授权边界。
12. 不默认启用逻辑删除、MetaObject 自动填充或通用审计字段注入。删除、状态变更、时间来源和
    审计是领域与数据库语义，必须显式设计并测试。
13. PostgreSQL 分页插件固定 `DbType.POSTGRE_SQL`，单页最大 `100`。若未来增加其他
    `InnerInterceptor`，分页 interceptor 必须放在链尾；业务 API 仍需在传输边界校验自己的页码
    和大小。

## 后果

### 正面

- 简单 CRUD 与分页可以减少重复 Mapper SQL，脚手架和后续生成器拥有一致的基础设施目标；
- 原有 XML、Repository 端口、事务、PostgreSQL 原生能力和模块数据所有权不需要迁移；
- MyBatis-Plus API 被限制在 adapter 内，不成为领域模型或公共 API 的长期兼容负担；
- 数据库生成 UUIDv7、显式 tenant 条件和真实 PostgreSQL 测试仍是权威基线。

### 负面与风险

- 团队需要同时理解 MyBatis 与 MyBatis-Plus，不能把 Wrapper 当成所有查询的替代品；
- JSqlParser 增加运行时依赖和 SQL 解析兼容面，升级时必须重新验证 PostgreSQL 查询与分页；
- `BaseMapper` 提供的通用方法可能被误用来绕过 tenant、状态或审计规则，需要架构测试、评审和
  集成测试持续约束；
- `IdType.AUTO` 的数据库生成键回填依赖 JDBC 与数据库行为，必须保留真实 PostgreSQL 回归测试；
- MyBatis-Plus、MyBatis-Spring、MyBatis 与 Spring Boot 的版本组合需要由 BOM 集中管理和验证。

## 安全、数据与隐私

- tenant interceptor 不承担授权。tenant 与 subject 仍只来自验证后的身份上下文，资源访问仍需
  显式 tenant 条件、成员关系、所有权和 scope 等现有门禁；
- Wrapper 中的动态排序、列选择和条件只能来自服务端白名单；不得拼接用户输入或任意 SQL 片段；
- 逻辑删除不能代替数据保留、撤销、法律保留或安全审计；
- 自动填充不能隐式生成 actor、tenant、业务时间或审计事实；
- MyBatis-Plus 不改变 prompt、凭据、个人信息、outbox 和审计数据的现有分级与保留规则。

## 运维与迁移

1. 由 Ainer BOM 集中管理 MyBatis-Plus 和 JSqlParser 模块版本；
2. `ainer-starter-persistence` 从原生 MyBatis Boot starter 切换到 Boot 4 专用
   MyBatis-Plus starter，应用配置键迁移为 `mybatis-plus.*`；
3. 保留现有 Mapper 接口、XML、Repository 和 migration，不进行全量 ORM 重写；
4. 先用 starter 级 PostgreSQL 原型验证生成键、XML 共存和分页，再执行完整 Reactor 与外部
   consumer 门禁；
5. 升级 MyBatis-Plus 或 JSqlParser 时，至少重跑 starter 原型、全部 Mapper 集成测试和 SQL
   关键路径；若解析或生成键回归，优先回退依赖版本，不修改数据库语义迁就框架。

该切换不包含 schema migration，也不改变业务表所有权。回滚到原生 starter 时必须同步恢复
配置前缀和自动配置测试，不能在生产依赖树中并存两套 starter。

## 验收结果

2026-07-30 已在 JDK 25、Maven 4.0.0-rc-6 和
`postgres:18.3-alpine` Testcontainers 上完成 persistence starter 原型：

- `BaseMapper.insert` 能让 PostgreSQL `DEFAULT uuidv7()` 生成 ID，并把生成值回填为
  `java.util.UUID`；断言 UUID 版本为 7；
- `BaseMapper.selectById` 可读取插入行；
- 同一个 Mapper 可同时继承 `BaseMapper` 和调用自定义 XML；
- 自定义 XML 使用显式 `tenant_id` 参数，只返回目标 tenant 数据；
- PostgreSQL 分页插件可对显式 tenant 条件执行分页，返回正确 total 和单页记录；
- 自动配置测试确认全局 `IdType.AUTO`、显式 UUID TypeHandler、PostgreSQL pagination 和
  `maxLimit=100`。

同日完成了其余门禁：

- Maven 4.0.0-rc-6 `clean verify` 在 JDK 25 与可用 Colima/Testcontainers 环境中通过全部
  14 个 Reactor project，共执行 303 项测试，0 failure、0 error、0 skipped；
- Identity、Workspace、AI Runtime 与 Authorization Server 的既有 PostgreSQL 集成测试实际
  执行，覆盖原有复杂 XML、CTE、锁、`RETURNING`、outbox 与审计路径；
- 依赖树只包含 MyBatis-Plus Boot 4 starter 3.5.17，并解析为 MyBatis 3.5.19、
  MyBatis-Spring 4.0.0 与 JSqlParser 5.2，没有并存原生 MyBatis Boot starter；
- `scripts/verify-maven-consumers.sh` 在隔离仓库完成 Maven 4 producer/Consumer POM 与可重复
  制品检查；Maven 3.9.16 和 Maven 4 外部 golden consumer 均能导入 Ainer BOM、消费
  `ainer-starter-persistence` 并编译对 `BaseMapper<?>` 的引用。

这些结果接受的是本 ADR 定义的受限 infrastructure 增强，不把 Ainer 整体提升为生产或商业
发行就绪。Maven 4 rc-6 仍是 preview，空缓存 Wrapper 下载限制和正式 CI/发布仓库缺口继续记录
在项目状态中。

## 参考

- [MyBatis-Plus 安装](https://baomidou.com/getting-started/install/)
- [MyBatis-Plus 分页插件](https://baomidou.com/plugins/pagination/)
- [MyBatis-Plus 多租户插件](https://baomidou.com/plugins/tenant/)
- [ADR-0002：Workspace 持久化基线](0002-workspace-persistence-baseline.md)
- [ADR-0020：PostgreSQL Native-First Greenfield 数据基线](0020-postgresql-native-greenfield-baseline.md)
- [Ainer 数据库设计规范](../database-design-standard.md)
