# Aurora 架构总览

> 状态:**DRAFT v0.4** · 日期:2026-07-22
> 关联:`design/aurora-scaffold-design.md`(设计详述)、`migration/aurora-migration-plan.md`(迁移路线)
> 四参考源:bladex(模块演进)+ yudao(业务范式)+ dante-cloud(一套代码两种架构)+ Snowy(数据权限/字段加密)

---

## 1. 工程性质

Aurora 是 **framework 提供者 + 业务模块载体 + 架构切换机制** 的工程:

- **framework 层**(`aurora-framework/`):提供 starter,可被任意项目(包括 xiaoqu 迁移后)以 GAV 依赖消费
- **业务层**(`aurora-module-*/`):遵循 framework 约定的业务模块,单体期与 framework 同仓,演进时可独立成服务
- **架构切换层**(`aurora-starter-architecture`):配置驱动单体↔微服务切换,**一套代码两种架构**(吸收 dante-cloud)

---

## 2. 模块全景图

```
aurora-boot/
│
├── pom.xml                              ① 根 parent(version + enforcer禁循环 + flatten + 注解处理器)
├── aurora-dependencies/                 ② BOM(唯一版本源)
│
├── aurora-framework/                    ③ 框架层(core→spring 分层,吸收 dante-engine)
│   ├── aurora-core                      ③-0 零 Spring 依赖最底层:常量/枚举/工具/异常/domain
│   ├── aurora-spring                    ③-1 Spring 基础设施:条件注解(@ConditionalOnArchitecture)+ Jackson
│   ├── aurora-common                    ③-a 全模块共享:pojo(CommonResult+traceId)/exception(枚举ErrorCode+注册表)/biz 契约
│   ├── aurora-starter-architecture      ③-★ 架构切换:@ConditionalOnArchitecture + Strategy + Local/Remote Listener
│   ├── aurora-starter-web               ③-b Web 层:前缀绑定/全局异常(HTTP status 真语义)/apilog/springdoc
│   ├── aurora-starter-security          ③-c 认证:Spring Authorization Server + ConfigurerManager(缺陷3)
│   ├── aurora-starter-mybatis           ③-d ORM:BaseEntity/BaseMapperX/Wrapper/easy-trans/EncryptTypeHandler(SM4)
│   ├── aurora-starter-datasource        ③-e 多数据源:dynamic-datasource boot4
│   ├── aurora-starter-redis             ③-f 缓存:Redisson 4.6 + Jackson 3
│   ├── aurora-starter-biz-tenant        ③-g 多租户:拦截器/隔离/可开关
│   ├── aurora-starter-biz-data-permission ③-h 数据权限:表维度+API维度(Snowy 预计算表/scopeKey)
│   ├── aurora-starter-biz-bpm           ③-i 工作流:Flowable 8
│   ├── aurora-starter-job / -mq / -excel / -protection / -websocket / -test
│
├── aurora-module-kernel/                ④ ★ 纯技术内核(拆自 yudao 上帝模块,缺陷5)
│   # auth/oauth2(SAS)/permission(role/dept/post)/dict/tenant/user/social/logger/notify/sms/mail
│   # 禁止塞业务
├── aurora-module-infra/                 ⑤ 基础设施:文件(多存储)/codegen/dbdoc/job/config
├── aurora-module-organization/          ⑥ 业务:销售组织/项目(从 system 拆出,缺陷5)
├── aurora-module-payment/               ⑦ 业务:支付桥接(从 system 拆出)
├── aurora-server/                       ⑧ 启动 shell(application.yaml + logback)
│
└── docs/                                ⑨ 文档集
```

---

## 3. 模块职责详表

### 3.1 基础设施(① ②)

| 模块 | 职责 | 关键内容 |
|---|---|---|
| `pom.xml`(根) | 版本 + 构建约束 | `${revision}` + enforcer(JDK25/Maven≥3.9) + flatten + 注解处理器(Lombok/MapStruct/config-processor) |
| `aurora-dependencies` | 唯一版本源 | import `spring-boot-dependencies:4.1.0` + 所有第三方版本集中管理 |

### 3.2 框架层(③ · core→spring 分层)

| 模块 | 职责 | 关键类 |
|---|---|---|
| `aurora-core` | ★ 零 Spring 依赖最底层 | 常量、枚举、工具、异常、domain(吸收 dante-core) |
| `aurora-spring` | ★ Spring 基础设施 | `@ConditionalOnArchitecture`、`AbstractEnumSpringBootCondition`/`ConditionEnum`、Jackson(吸收 dante-spring) |
| `aurora-common` | 全模块共享基础 | `CommonResult`(+traceId)、`PageResult`、`PageParam`、枚举 `ErrorCode` + 注册表(缺陷6)、`ServiceException`、`biz/*CommonApi`(缺陷3调整后契约)、util |
| `aurora-starter-architecture` | 架构切换 | Strategy 接口约定、Local/Remote Listener |
| `aurora-starter-web` | Web 层 | 前缀绑定、`GlobalExceptionHandler`(**HTTP status 真语义**,缺陷7)、apilog、springdoc3 |
| `aurora-starter-security` | 认证(**SAS**,缺陷3) | Spring Authorization Server + ConfigurerManager(dante 设计,需自研) |
| `aurora-starter-mybatis` | ORM | `BaseEntity`(原 BaseDO)、`BaseMapperX`、`LambdaQueryWrapperX`、`EncryptTypeHandler`(SM4,缺陷S2)、easy-trans |
| `aurora-starter-datasource` | 多数据源 | dynamic-datasource boot4 |
| `aurora-starter-redis` | 缓存 | `@AutoConfiguration(before = RedissonAutoConfigurationV4.class)`、JSON RedisTemplate |
| `aurora-starter-biz-tenant` | 多租户 | `TenantEntity`(原 TenantBaseDO)、`TenantDatabaseInterceptor`、Redis 隔离、可开关 |
| `aurora-starter-biz-data-permission` | 数据权限(**双模**,缺陷2) | `@DataPermission`(表维度)+ API 维度 DataScope/预计算表/scopeKey(Snowy) |
| `aurora-starter-biz-bpm` | 工作流 | Flowable 8 |
| `aurora-starter-job` / `-mq` / `-excel` / `-protection` / `-websocket` / `-test` | 定时/消息/Excel/服务保障/WS/单测 | (同 yudao 范畴) |

### 3.3 业务层(④ ⑤ ⑥ ⑦ · kernel/business 分离,缺陷5)

| 模块 | 职责 |
|---|---|
| `aurora-module-kernel` | ★ **纯技术内核**(拆自 yudao 上帝模块 system):auth/oauth2(SAS)/permission(role/dept/post)/dict/tenant/user/social/logger/notify/sms/mail。**禁止塞业务** |
| `aurora-module-infra` | 基础设施:文件(多存储)/codegen HTTP 入口/dbdoc/job/config |
| `aurora-module-organization` | ★ 业务:销售组织/项目(从 system 拆出,缺陷5) |
| `aurora-module-payment` | ★ 业务:支付桥接(从 system 拆出) |

### 3.4 启动(⑥)

| 模块 | 职责 |
|---|---|
| `aurora-server` | 空壳:`AuroraServerApplication` + `application-{local,dev,prod}.yaml` + `logback-spring.xml`;pom 依赖决定启动哪些 module |

---

## 4. 分层架构(业务模块内部 · 新分层,缺陷4)

```
HTTP 请求
   │
   ▼
controller/{admin,app}/XxxController          @PreAuthorize + @Tag/@Operation
   │                                            ↓ 参数校验(@Valid XxxCreateReq/XxxPageReq)
   ▼
service/XxxService(Impl)                       业务逻辑(单向依赖,禁互注,缺陷1),throw ServiceException
   │                                            ↓ Entity ↔ Resp 转换(MapStruct Convert)
   ▼
repository/entity/XxxMapper                    extends BaseMapperX
   │                                            ↓ LambdaQueryWrapperX / selectPage
   ▼
repository/entity/XxxEntity                    extends BaseEntity / TenantEntity(原 BaseDO)
   │
   ▼
PostgreSQL
```

**跨模块调用**(缺陷1 治理):业务间走 `integration/api/XxxApi`(**单向**,禁 Service 互注);"完成后通知"走**领域事件**(`event/`,破二元环);框架反调业务走 `common/biz/*CommonApi`(依赖倒置)。详见 `design/aurora-scaffold-design.md` §5。

---

## 5. 核心抽象速查

| 抽象 | 位置 | 作用 |
|---|---|---|
| `CommonResult<T>` | aurora-common | 统一响应:`code/msg/data`+`traceId` + `success(T)`/`error(ErrorCode)` |
| `PageParam` / `PageResult<T>` | aurora-common | 分页:`pageNo=1`/`pageSize=10`(max 200)/`PAGE_SIZE_NONE=-1` |
| `ErrorCode`(枚举) | aurora-common | ★ 错误码改为**枚举 + 注册表启动校验**(缺陷6,非散落常量) |
| `ServiceException` | aurora-common | 业务异常 |
| `BaseEntity` | aurora-starter-mybatis | ★ 原 BaseDO 重命名;5 审计字段 + `@TableLogic` + `TransPojo` |
| `TenantEntity` | aurora-starter-biz-tenant | extends BaseEntity + `tenantId`(原 TenantBaseDO) |
| `BaseMapperX<T>` | aurora-starter-mybatis | extends `MPJBaseMapper`:selectPage/selectJoinPage/selectOne/insertBatch/... |
| `LambdaQueryWrapperX<T>` | aurora-starter-mybatis | extends `LambdaQueryWrapper`:xxxIfPresent 系列 |
| `XxxApi` + `impl/`(Local/Feign) | module/*/integration/api | ★ 业务间**单向**契约(单体本地/微服务 Feign) |
| 领域事件 `*Event` | module/*/event | ★ 破循环依赖:反向通知走事件(缺陷1) |
| `*CommonApi` | aurora-common/biz | 框架反调业务契约(依赖倒置) |
| `*Convert` | module/*/integration/dto | MapStruct 转换器(Entity↔Resp↔DTO,缺陷4 统一模型) |
| `@ConditionalOnArchitecture` | aurora-spring | 架构条件注解:`MONOLITH`(默认)/`DISTRIBUTED` |
| `@DataPermission` / API 维度 DataScope | aurora-starter-biz-data-permission | 数据权限双模(缺陷2) |
| `EncryptTypeHandler` | aurora-starter-mybatis | 字段级透明加密 SM4(Snowy S2) |

---

## 6. 运行架构:一套代码、两种架构(吸收 dante-cloud)

Aurora 通过配置 `aurora.architecture` 在单体/微服务间切换,**业务代码零改动**:

```
aurora:
  architecture: monolith     # monolith(默认) | distributed
```

| 关注点 | monolith(默认) | distributed(演进) |
|---|---|---|
| 跨模块调用 | `XxxApi` 本地 Impl(`@ConditionalOnArchitecture(MONOLITH)`) | `XxxApi` Feign 远程 |
| 跨进程事件 | Local Listener(默认,进程内事件,不连 Kafka) | Remote Listener(`@ConditionalOnArchitecture(DISTRIBUTED)` + `@ConditionalOnClass(StreamBusBridge)`) |
| 服务发现 | 无(同进程) | Nacos/Polaris |
| 配置中心 | 本地 `application.yaml` | Nacos(可选) |
| 网关 | 无 | Spring Cloud Gateway + 防伪造内部调用头拦截 |

机制三层:① `@ConditionalOnArchitecture` 条件注解 ② Strategy 接口双实现(Local/Feign)③ 配置驱动 Bean 装配。详见 `design/aurora-scaffold-design.md` §6。

---

## 7. 技术栈速查

| 类别 | 选型 | 版本 |
|---|---|---|
| JDK | Java | 25 |
| 框架 | Spring Boot | 4.1.0 |
| ORM | MyBatis-Plus(boot4 starter) | 3.5.16 |
| Join | MyBatis-Plus-Join | 1.5.7 |
| 多源 | dynamic-datasource(boot4 starter) | 4.5.0 |
| 连接池 | HikariCP | Boot 自带 |
| 缓存 | Redisson | 4.6.1 |
| 安全 | **Spring Authorization Server**(缺陷3)+ Spring Security 7 | 随 Boot4 |
| 文档 | springdoc | 3.0.3 |
| 翻译 | easy-trans | 3.1.5 |
| BPM | Flowable | 8.0.0 |
| Excel | FastExcel | 1.3.0 |
| Lombok / MapStruct | — | 1.18.46 / 1.6.3 |
| DB | PostgreSQL | 18 |

> Boot4 坐标改名陷阱见 `boot4-migration-notes.md` §2.2。
