# Aurora 架构总览

> 状态:**DRAFT v0.1** · 日期:2026-07-22
> 关联:`design/aurora-scaffold-design.md`(设计详述)、`migration/aurora-migration-plan.md`(迁移路线)

---

## 1. 工程性质

Aurora 是 **framework 提供者 + 业务模块载体** 的双层工程:

- **framework 层**(`aurora-framework/`):提供 starter,可被任意项目(包括 xiaoqu 迁移后)以 GAV 依赖消费
- **业务层**(`aurora-module-*/`):遵循 framework 约定的业务模块,单体期与 framework 同仓,演进时可独立成服务

---

## 2. 模块全景图

```
aurora-boot/
│
├── pom.xml                              ① 根 parent(version + enforcer + flatten + 注解处理器)
├── aurora-dependencies/                 ② BOM(唯一版本源)
│
├── aurora-framework/                    ③ 框架层(parent = ①)
│   ├── aurora-common                    ③-a 全模块共享:pojo/exception/util/biz 契约
│   ├── aurora-starter-web               ③-b Web 层:前缀绑定/全局异常/apilog/springdoc
│   ├── aurora-starter-security          ③-c 认证:Spring Security 7 + OAuth2 token
│   ├── aurora-starter-mybatis           ③-d ORM:BaseDO/BaseMapperX/Wrapper/easy-trans
│   ├── aurora-starter-datasource        ③-e 多数据源:dynamic-datasource boot4
│   ├── aurora-starter-redis             ③-f 缓存:Redisson 4.6 + Jackson 3
│   ├── aurora-starter-biz-tenant        ③-g 多租户:拦截器/隔离/可开关
│   ├── aurora-starter-biz-data-permission ③-h 数据权限:@DataPermission 规则引擎
│   ├── aurora-starter-biz-bpm           ③-i 工作流:Flowable 8
│   ├── aurora-starter-job               ③-j 定时任务:Quartz
│   ├── aurora-starter-mq                ③-k 消息队列:Redis Stream/RabbitMQ/RocketMQ
│   ├── aurora-starter-excel             ③-l Excel:FastExcel + 字典转换
│   ├── aurora-starter-protection        ③-m 服务保障:限流/幂等/分布式锁
│   ├── aurora-starter-websocket         ③-n WebSocket
│   └── aurora-starter-test              ③-o 单测基座:H2/redis mock/BaseDbUnitTest
│
├── aurora-module-system/                ④ 样本+地基:用户/角色/部门/菜单/字典/租户/RBAC
├── aurora-module-infra/                 ⑤ 样本:文件/codegen HTTP 入口/dbdoc/job/config
├── aurora-server/                       ⑥ 启动 shell(application.yaml + logback)
│
└── docs/                                ⑦ 文档集
```

---

## 3. 模块职责详表

### 3.1 基础设施(① ②)

| 模块 | 职责 | 关键内容 |
|---|---|---|
| `pom.xml`(根) | 版本 + 构建约束 | `${revision}` + enforcer(JDK25/Maven≥3.9) + flatten + 注解处理器(Lombok/MapStruct/config-processor) |
| `aurora-dependencies` | 唯一版本源 | import `spring-boot-dependencies:4.1.0` + 所有第三方版本集中管理 |

### 3.2 框架层(③)

| 模块 | 职责 | 关键类 |
|---|---|---|
| `aurora-common` | 全模块共享基础 | `CommonResult`、`PageResult`、`PageParam`、`ErrorCode`、`ServiceException`、`biz/*CommonApi`(7 个跨模块契约)、util |
| `aurora-starter-web` | Web 层 | `/admin-api`+`/app-api` 前缀绑定、`GlobalExceptionHandler`、apilog、springdoc3、XSS |
| `aurora-starter-security` | 认证 | `TokenAuthenticationFilter`、`AuroraWebSecurityConfigurerAdapter`(Security 7 lambda DSL)、operatelog |
| `aurora-starter-mybatis` | ORM | `BaseDO`、`BaseMapperX`、`LambdaQueryWrapperX`、`QueryWrapperX`、`MPJLambdaWrapperX`、TypeHandler、easy-trans |
| `aurora-starter-datasource` | 多数据源 | dynamic-datasource boot4 封装 |
| `aurora-starter-redis` | 缓存 | `AuroraRedisAutoConfiguration`(`@AutoConfiguration(before = RedissonAutoConfigurationV4.class)`)、JSON RedisTemplate、限流锁 |
| `aurora-starter-biz-tenant` | 多租户 | `TenantBaseDO`、`TenantDatabaseInterceptor`、Redis 缓存隔离、`@TenantIgnore`、可开关 |
| `aurora-starter-biz-data-permission` | 数据权限 | `@DataPermission`、`DataPermissionRule`、dept-based rule |
| `aurora-starter-biz-bpm` | 工作流 | Flowable 8 封装 |
| `aurora-starter-job` | 定时任务 | Quartz + `@Async` |
| `aurora-starter-mq` | 消息队列 | Redis Stream/PubSub + RabbitMQ + RocketMQ(租户感知) |
| `aurora-starter-excel` | Excel | FastExcel + 字典转换 |
| `aurora-starter-protection` | 服务保障 | 限流 / 幂等 / 分布式锁 |
| `aurora-starter-websocket` | WebSocket | 发送/会话管理 |
| `aurora-starter-test` | 单测基座 | H2 + redis mock + `BaseDbUnitTest` |

### 3.3 业务层(④ ⑤)

| 模块 | 职责 |
|---|---|
| `aurora-module-system` | RBAC 核心:用户/角色/部门/菜单/字典/租户/oauth2/social/logger/notify/sms/mail + 扩展(organization/project/payment/datascope) |
| `aurora-module-infra` | 基础设施:文件(多存储)/codegen HTTP 入口/dbdoc/job/config/dashboard |

### 3.4 启动(⑥)

| 模块 | 职责 |
|---|---|
| `aurora-server` | 空壳:`AuroraServerApplication` + `application-{local,dev,prod}.yaml` + `logback-spring.xml`;pom 依赖决定启动哪些 module |

---

## 4. 分层架构(业务模块内部)

```
HTTP 请求
   │
   ▼
controller/admin/<feature>/XxxController      @PreAuthorize + @Tag/@Operation
   │                                            ↓ 参数校验(@Valid *ReqVO)
   ▼
service/<feature>/XxxService(Impl)            业务逻辑,throw ServiceException
   │                                            ↓ DO ↔ VO 转换(BeanUtils / MapStruct Convert)
   ▼
dal/mysql/<feature>/XxxMapper                 extends BaseMapperX
   │                                            ↓ LambdaQueryWrapperX / selectPage
   ▼
dal/dataobject/<feature>/XxxDO                extends BaseDO / TenantBaseDO
   │
   ▼
PostgreSQL
```

**跨模块调用**:走 `api/<feature>/XxxApi`(本地 `@Service` 注入),框架反调走 `common/biz/*CommonApi`(依赖倒置)。详见 `design/aurora-scaffold-design.md` §5。

---

## 5. 核心抽象速查

| 抽象 | 位置 | 作用 |
|---|---|---|
| `CommonResult<T>` | aurora-common | 统一响应:`code/msg/data` + `success(T)`/`error(ErrorCode)` + `getCheckedData()` |
| `PageParam` / `PageResult<T>` | aurora-common | 分页:`pageNo=1`/`pageSize=10`(max 200)/`PAGE_SIZE_NONE=-1`(全量) |
| `ErrorCode` | aurora-common | 错误码定义,全局 `ErrorCodeConstants` |
| `ServiceException` | aurora-common | 业务异常,`throw exception(ErrorCode)` |
| `BaseDO` | aurora-starter-mybatis | 5 审计字段(createTime/updateTime/creator/updater)+ `@TableLogic deleted` + `TransPojo` |
| `TenantBaseDO` | aurora-starter-biz-tenant | extends BaseDO + `tenantId` |
| `BaseMapperX<T>` | aurora-starter-mybatis | extends `MPJBaseMapper`:selectPage/selectJoinPage/selectOne/insertBatch/updateBatch/delete |
| `LambdaQueryWrapperX<T>` | aurora-starter-mybatis | extends `LambdaQueryWrapper`:xxxIfPresent 系列(值为空不拼条件) |
| `*Api` / `*ApiImpl` | module/*/api | 业务间跨模块契约(本地注入) |
| `*CommonApi` | aurora-common/biz | 框架反调业务的契约(依赖倒置) |
| `*Convert` | module/*/convert | MapStruct 转换器(复杂 DO↔VO 映射) |

---

## 6. 技术栈速查

| 类别 | 选型 | 版本 |
|---|---|---|
| JDK | Java | 25 |
| 框架 | Spring Boot | 4.1.0 |
| ORM | MyBatis-Plus(boot4 starter) | 3.5.16 |
| Join | MyBatis-Plus-Join | 1.5.7 |
| 多源 | dynamic-datasource(boot4 starter) | 4.5.0 |
| 连接池 | HikariCP | Boot 自带 |
| 缓存 | Redisson | 4.6.1 |
| 安全 | Spring Security | 7(随 Boot4) |
| 文档 | springdoc | 3.0.3 |
| 翻译 | easy-trans | 3.1.5 |
| BPM | Flowable | 8.0.0 |
| Excel | FastExcel | 1.3.0 |
| Lombok / MapStruct | — | 1.18.46 / 1.6.3 |
| DB | PostgreSQL | 18 |

> Boot4 坐标改名陷阱见 `boot4-migration-notes.md` §2.2。
