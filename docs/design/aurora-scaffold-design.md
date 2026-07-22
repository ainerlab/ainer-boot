# Aurora 脚手架 · 架构设计

> 状态:**DRAFT v0.3** · 日期:2026-07-22
> 技术基线:**JDK 25 + Spring Boot 4.1.0**(Spring Framework 7 / Jakarta EE 11 / Servlet 6.1)
> 定位:单体优先、可演进微服务(**一套代码、两种架构**)的业务开发脚手架
> 身份:全新独立 greenfield 仓库,**与 xiaoqu-platform 现有 yudao 范式 100% 兼容**(保证 6000 文件零返工)
> 参考源:**bladex**(单体→微服务模块拆分) + **yudao/ruoyi-vue-pro**(业务范式) + **dante-cloud**(一套代码两种架构)

---

## 0. 阅读顺序

1. [§1 定位与约束](#1-定位与约束)—— 为什么是"范式提炼 + 升级 + 独立",而非"范式创新"
2. [§2 吸收清单](#2-吸收清单bladex--yudao--dante-cloud)—— 从三个脚手架各吸收什么、舍弃什么,逐项附理由
3. [§3 技术栈基线](#3-技术栈基线boot4--jdk25)—— 已 git show 核实的精确坐标
4. [§4 模块划分](#4-模块划分)—— 工程骨架
5. [§5 跨模块契约](#5-跨模块契约双轨制)—— yudao 最精华设计
6. [§6 单体↔微服务一套代码](#6-单体微服务一套代码)—— dante-cloud 最关键差异化价值的 Aurora 落地
7. [§7 Boot4+JDK25 适配](#7-boot4jdk25-适配清单)—— 踩坑点全列(详见 `boot4-migration-notes.md`)
8. [§8 分阶段实施](#8-分阶段实施计划)—— 从骨架到迁移
9. [§9 待决策项](#9-待决策项)—— 默认值与备选

---

## 1. 定位与约束

### 1.1 一句话定位

**Aurora = 把 xiaoqu-platform 已深度使用的 yudao 范式,抽干净成独立品牌产物,升级到 Boot4+JDK25,并吸收 bladex 的模块演进路径 + dante-cloud 的"一套代码两种架构"机制。**

### 1.2 为什么不是"范式创新"

深度盘点 xiaoqu-platform 后的硬事实:

- xiaoqu 现有 **~6000 Java 文件 / ~57 万行**,13+ 业务模块
- 全平台已深度跑在 yudao 范式上:`BaseDO` / `BaseMapperX` / `LambdaQueryWrapperX` / `CommonResult` / `module/*/api/XxxApi` 契约 / `common/biz/*CommonApi` 下沉
- 其中 mall/trade 单模块族 13 万行、AI 模块 27 个功能域、cdp/wecom 全新自研——这些是**真 IP**

**推论**:脚手架若改范式 = 6000 文件返工。因此**与 xiaoqu 现有 yudao 范式 100% 兼容是硬约束**(见 [§8 决策项 D](#8-待决策项))。Aurora 的价值不在发明新范式,而在:

1. **范式提炼**:把散在 xiaoqu 各处的约定,固化成独立、可复用的 framework 产物
2. **技术栈升级**:Boot 3.5.13 → 4.1.0,JDK 已就绪 25
3. **品牌独立**:零 `cn.iocoder.yudao` / `cn.xiaoqu` 包名残留,自有 GAV
4. **工程增强**:吸收 bladex 的演进友好型设计(单体→微服务路径、双模板 codegen)

### 1.3 与 xiaoqu 的关系

| 维度 | 关系 |
|---|---|
| 仓库 | **独立** greenfield 仓库,不与 xiaoqu 同仓 |
| 包名 | Aurora 自有(`cn.<brand>.*`),xiaoqu 迁移时改包名迁入 |
| 依赖方向 | 单向:xiaoqu(未来)依赖 Aurora 的 framework;Aurora **不反向依赖** xiaoqu 业务模块 |
| 演进 | Aurora 是 framework 提供者;xiaoqu 业务模块逐层迁入(见 `migration/aurora-migration-plan.md`) |

### 1.4 运行模式:一套代码、两种架构(吸收 dante-cloud)

这是 Aurora 的核心叙事,也是 dante-cloud 相对 bladex/yudao **唯一的决定性差异化价值**。

> 三方对比:
> - **bladex**:单体(全揉 modules 包)和微服务(api+service 物理拆分)是**两套代码**,演进靠重写模块边界。
> - **yudao**:单体和微服务是**两个独立工程**(`ruoyi-vue-pro` 单体 vs `yudao-cloud`),共享部分 framework。
> - **dante-cloud**:**一套代码**,靠配置 `herodotus.platform.architecture: monolith|distributed` 在运行时切换,业务代码零改动。**这是 Aurora 要走的路。**

Aurora 落地这套机制(详见 §6):

- **单体期(默认)**:配置 `aurora.architecture: monolith`,跨模块走 `api/` 接口的**本地实现**(`@Service` 注入,零网络开销)
- **微服务期(演进)**:配置 `aurora.architecture: distributed`,同一 `api/` 接口切换为 **Feign 远程实现**;`@ConditionalOnArchitecture` 条件注解控制 Bean 注册;**业务代码、接口签名零改动**
- 切换开关是 YAML 一行配置,不改代码

关键工程细节(吸收 dante-cloud/engine,已基于 engine 真实代码核实,见 §6):
- **条件注解体系**:`@ConditionalOnArchitecture` 用"枚举即条件"三层委托设计(`AbstractEnumSpringBootCondition` + `ConditionEnum` 接口),新增维度只加枚举
- **Local/Remote 切换**:跨模块 `api/` 接口配 Local 实现(单体)/ Feign 实现(微服务);跨进程事件用 Local Listener(默认)/ Remote Listener(分布式)成对
- **单体不连 Kafka**:单体模式 Remote Listener 不装配(配合 `@ConditionalOnClass(StreamBusBridge.class)` 双保险),进程内事件用 Local Listener

> ℹ️ **dante-engine 已本地 clone**(54 模块)。条件注解/分层/`@EnableXxx` 可直接移植代码;Strategy 双实现 dante v4.1.0.4 已删除,Aurora 需自研。详见 §6.9 移植复杂度评估。

---

## 2. 吸收清单(bladex + yudao + dante-cloud)

### 2.1 从 bladex 吸收(精选 4 项)

| # | 吸收项 | bladex 做法 | Aurora 做法 | 价值 |
|---|---|---|---|---|
| B1 | **单体 `modules/X` 一个模块 + 演进时抽 `X-api`** | BladeX-Boot 单体全揉 modules 包;微服务拆 `-api`/`-service` | 单体期每模块一个 Maven module,`api/` 包内置;演进时把 `api/`+DTO 抽为 `X-api` 子模块 | bladex 单体→微服务是清晰活样本 |
| B2 | **代码生成双模板矩阵(api / api-fast)** | 单体用 `api-fast`(无 feign),微服务用 `api`(含 feign);6 前端 × crud/tree/sub | 首期后端一套(controller/service/mapper/do/vo/sql);预留 api-fast/api 双模板位;前端按需(element-plus / wot-ui) | 最契合单体→微服务演进 |
| B3 | **BOM + flatten + ${revision}** | blade-bom 子模块 + flatten oss + pomElements 配置 | `aurora-dependencies` 独立顶级 pom + flatten(blade-bom 的 pomElements 配置更完整) | 两方唯一完全共识项 |
| B4 | **命名规范词典 + 文档骨架** | 类名后缀词典、模块全景图、框架开发模板 | 借鉴 BladeX-Tool/CLAUDE.md 章节骨架(见 `conventions.md`) | 成熟文档结构 |

### 2.2 论证后舍弃的 bladex 机制(附理由)

| 舍弃项 | bladex 做法 | 舍弃理由(已代码核实) |
|---|---|---|
| `blade-core-auto` 编译期注解处理器 | 扫描 `@Component`/`@FeignClient`/`@AutoListener` 自动生成 spring.factories + AutoConfiguration.imports | ① Boot3+ **已不读** spring.factories 的 auto-config;② 单体无 Feign,其 90% 价值(Feign 聚合)消失;③ JSR 269 在 JDK25 虽仍可用,但维护成本 > 收益(手写 imports 文件每文件仅几行) |
| `BladeApplication` + LauncherService SPI | 包装 SpringApplicationBuilder,硬编码 Nacos/Seata config.import + ServiceLoader 加载 LauncherService | ① 硬编码的 nacos config.import 是单体干扰项;② `blade.env`/`blade.dev-mode` 等约定属性是 bladex 内部 starter 消费的,不沿用 bladex starter 则无意义;③ 标准 `spring.config.import` 更符合 Boot4 |
| `R<T>` + BladeController 强继承 | R 带 success 字段 + 静态工厂;BladeController 含文件下载强继承 | ① `success` 字段与 HTTP 语义重复;② BladeController 连 bladex 自己核心 controller 都不全继承(耦合重);③ 改吸收 yudao 的 CommonResult(无 success + 全局异常兜底)是 Boot3/4 现代写法 |
| Wrapper(Entity→VO 反射转换) | BaseEntityWrapper + BeanUtil 反射 + 缓存翻译塞入 | ① 反射性能差;② 无接口契约,重构易漏;③ 改用 MapStruct(编译期生成 + 性能完胜),便捷性 default 方法补 |

### 2.3 从 yudao 吸收(精华 8 项,保证 6000 文件零返工)

| # | 吸收项 | 为什么 |
|---|---|---|
| Y1 | **双轨跨模块契约**:业务间 `module/*/api/XxxApi`(本地 @Service 注入),框架 starter 反调业务用 `common/biz/*CommonApi`(依赖倒置) | yudao 最精华设计,解决"starter 不能依赖业务模块"核心矛盾。详见 [§5](#5-跨模块契约双轨制) |
| Y2 | **空壳 server + pom 组合决定启动范围** | 零业务代码,fat jar,注释切模块即可裁剪。单体扩展性最好 |
| Y3 | **BaseDO**(5 审计字段 + 逻辑删除 + TransPojo) | 6000 文件已用,复刻即零返工 |
| Y4 | **BaseMapperX**(分页/Join/ForUpdate/批量全套 default 方法) | 开发体验核心 |
| Y5 | **LambdaQueryWrapperX**(xxxIfPresent 系列) | 条件构造便捷性 |
| Y6 | **CommonResult + ErrorCode + ServiceException + 全局兜底** | 后端只 throw,统一兜底转响应 |
| Y7 | **MapStruct Convert**(编译期生成) | 类型安全 + 性能,替代 bladex 反射 Wrapper |
| Y8 | **内建 codegen**(连库读表 → 全套后端 + SQL + 可选前端) | 生产力杠杆 |

### 2.4 从 dante-cloud 吸收(精选 4 项 + 安全补丁 1 项)

> dante-cloud + dante-engine(JDK25 + Boot4.1 + Spring Cloud 2025.1.2,与 Aurora 技术栈完全一致)是参考源里**唯一与 Aurora 定位("一套代码两种架构")对齐**的。核心价值在"配置驱动架构切换",数据层(JPA)与 Aurora(MyBatis-Plus)范式冲突,故选择性吸收。**dante-engine 已本地 clone**,以下基于真实代码核实。

| # | 吸收项 | dante 真实做法 | Aurora 做法 | 移植性 |
|---|---|---|---|---|
| D1 | **配置驱动架构切换** | `@ConditionalOnArchitecture` + `aurora.architecture: monolith\|distributed`,**枚举即条件**三层委托设计(`AbstractEnumSpringBootCondition` + `ConditionEnum`) | 同,4 文件 ~120 行 | ✅ 直接移植代码 |
| D2 | **跨模块 Local/Feign 双实现** | ⚠️ dante v4.1.0.4 **已删除**双实现(改为 UAA 直连库);`StrategyUserDetailsService` 退化单实现 | Aurora **自研**:接口 + Local Impl(`@ConditionalOnArchitecture(MONOLITH)`)+ Feign(分布式) | ⚠️ 需自研 |
| D3 | **单体不连 Kafka**(Local/Remote Listener 成对) | ⚠️ **非** BusBridge 空实现;而是 Local Listener(默认)+ Remote Listener(`@ConditionalOnArchitecture(DISTRIBUTED)` + `@ConditionalOnClass(StreamBusBridge)`)成对 | 同模式,Listener 代码极简 | ✅ 直接移植模式 |
| D4 | **`@EnableXxx` 模块开关** | `@Import(XxxConfiguration.class)` 一行注解,Configuration 内组合扫描 | 同 | ✅ 直接移植 |
| D5 | **framework 分层 core→spring** | `dante-core`(零 Spring 依赖)→ `dante-spring`(条件注解体系)→ 其余 | `aurora-common` → `aurora-spring`(条件注解)→ 其余 | ✅ 直接移植架构 |
| D6 | **网关防伪造内部调用头** | `GlobalCertificationFilter` 检测外部请求带 `X-Herodotus-From-In` 头直接 403 | 微服务演进时网关补此拦截 | ✅ 演进阶段补 |

### 2.5 论证后舍弃的 dante-cloud 机制(附理由)

| 舍弃项 | dante-cloud 做法 | 舍弃理由 |
|---|---|---|
| **JPA + Hibernate 二级缓存 + JetCache 多租户** | `MultiTenantFilter` + `HerodotusRegionFactory`,数据层全 JPA | **范式根本冲突**。Aurora 是 MyBatis-Plus 阵营(xiaoqu 6000 文件已用),强塞 JPA 会撕裂架构;多租户用 MyBatis-Plus TenantLineHandler |
| **opaque token + introspection** 资源服务器 | `resourceserver.opaquetoken` 中心化 introspection | 中心化 introspection 每次请求多一次网络调用;Aurora 用 JWT 验签更轻(单体期)/ 可选 introspection(强撤销场景) |
| **passkey / 国密数字信封 / 三级等保** | SAS 扩展 grant type + `DigitalEnvelopeProcessor` | 业务特定,非通用脚手架刚需;过早引入是负担,按业务需求再补 |
| **Nacos 配置全外部化** | application.yaml 只剩 bootstrap,业务配置全在 Nacos | 与"单体优先"冲突(单体不该强依赖 Nacos);微服务演进阶段再考虑 |
| **Spring Authorization Server + ConfigurerManager** | SAS + 把 HttpSecurity 配置器封装成可注入 Manager | 比 yudao 自造 token 更正统,但实现复杂度高;与 §9 决策项 A 相关,**若选 OAuth2 标准方案再参考**,首期不引入 |
| **三基础设施 facility starter(Nacos/Polaris/Zookeeper)** | `facility-spring-boot-starter` pom 注释切换 | 单体阶段默认无注册中心,演进时再做适配层 |

### 2.6 三方融合的关键判断

| 设计点 | bladex | yudao | dante-cloud | Aurora 选择 |
|---|---|---|---|---|
| 统一响应 | `R<T>`(带 success) | `CommonResult<T>`(无 success) | — | **yudao**(Boot3/4 现代写法) |
| VO 转换 | Wrapper(反射) | Convert(MapStruct) | — | **yudao**(性能+类型安全) |
| 数据层 | MyBatis-Plus | MyBatis-Plus | **JPA** | **MyBatis-Plus**(yudao/bladex,Aurora 阵营) |
| 版本管理 | blade-bom 子模块 | yudao-dependencies 独立顶级 | ecosystem-parent + 双 BOM 三层 | **融合**:结构跟 yudao(独立顶级),flatten 参数抄 bladex(pomElements) |
| **单体↔微服务** | 两套代码(模块物理拆) | 两个工程(vue-pro/cloud) | **一套代码**(配置切换) | **dante-cloud 配置驱动** + bladex 演进抽 X-api |
| 模块拆分 | 单体全揉 / 微服务 api+service | 统一 module(单体形态) | 配置条件注解 | **bladex 演进路径** + dante 条件装配 |
| 跨模块契约 | Feign IXxxClient | 双轨(api 包本地 + biz/CommonApi) | `@Inner` + Feign Contract + 网关拦伪造头 | **yudao 双轨**(最精华)+ dante 网关防伪造(安全补丁) |
| 装配生成 | blade-core-auto 编译期处理器 | 手写 AutoConfiguration.imports | `@ConditionalOnXxx` + `@EnableXxx` | **yudao 手写** imports + **dante `@EnableXxx` 显式开关** |
| 启动入口 | BladeApplication.run + SPI | 裸 SpringApplication.run | 裸 run + 条件注解 | **裸 run**(单体无聚合场景) |
| 认证 | 自造 token | Spring Security + 自造 OAuth2 token | **Spring Authorization Server + ConfigurerManager** | **首期 yudao 路线**(决策项 A),OAuth2 标准化时参考 dante |

---

## 3. 技术栈基线(Boot4 + JDK25)

> 所有坐标已通过 `git show origin/master-jdk25:<path>` 在 ruoyi-vue-pro 的 jdk25 分支上核实,**非记忆**。完整代码片段见 `boot4-migration-notes.md`。

### 3.1 核心版本

| 类别 | 选型 | 版本 | 备注 |
|---|---|---|---|
| JDK | Java | **25** | enforcer 强制 ≥25(ruoyi 缺这项,我们补) |
| 框架 | Spring Boot | **4.1.0** | Spring FW 7 / Jakarta EE 11 / Servlet 6.1 |
| 构建 | Maven | ≥3.9 | enforcer 强制 |
| ORM | MyBatis-Plus | 3.5.16 | ⚠️ **`mybatis-plus-spring-boot4-starter`**(坐标改名) |
| Join | MyBatis-Plus-Join | 1.5.7 | `BaseMapperX extends MPJBaseMapper` |
| 多源 | dynamic-datasource | 4.5.0 | ⚠️ **`dynamic-datasource-spring-boot4-starter`** |
| 连接池 | HikariCP | Boot 自带 | 不用 Druid(xiaoqu 已精简,保持) |
| 缓存 | Redisson | **4.6.1** | `RedissonAutoConfigurationV4` |
| 文档 | springdoc | 3.0.3 | 原生 OpenAPI3,不用 Knife4j |
| 翻译 | easy-trans | 3.1.5 | `BaseDO implements TransPojo` |
| BPM | Flowable | 8.0.0 | 重能力(按需) |
| Lombok | — | 1.18.46 | JDK25 适配版 |
| MapStruct | — | 1.6.3 | |
| flatten | — | 1.7.2 | `oss` 模式 |
| compiler-plugin | — | 3.14.0 | 支持 JDK25 编译 |
| surefire | — | 3.5.3 | |
| DB | PostgreSQL | 18 | 与 xiaoqu 一致 |

### 3.2 Boot4 坐标改名陷阱(最易踩坑)

三家都为 Boot4 **单独发了 artifactId**,不是原 starter 名:

```
✅ mybatis-plus-spring-boot4-starter        (非 mybatis-plus-spring-boot3-starter)
✅ dynamic-datasource-spring-boot4-starter  (非 dynamic-datasource-spring-boot-starter)
✅ druid-spring-boot-4-starter              (非 druid-spring-boot-starter) ← 我们不用,但记录
```

Redisson 4.6.1 需排除 `spring-boot-starter-actuator`(避免与 Boot4 actuator 冲突)。

---

## 4. 模块划分

```
aurora-boot/
├── pom.xml                               # parent: ${revision} + enforcer(JDK25/Maven≥3.9) + flatten + 注解处理器
├── aurora-dependencies/                  # BOM:唯一版本源,import spring-boot-dependencies 4.1.0
├── aurora-framework/                     # 框架层(parent=aurora)
│   ├── aurora-common                     # pojo(CommonResult/PageResult/PageParam) + exception(ErrorCode/ServiceException) + util + biz/*CommonApi(跨模块契约)
│   ├── aurora-starter-web                # /admin-api+/app-api 前缀绑定 + 全局异常 + apilog + springdoc3
│   ├── aurora-starter-security           # 认证(见 §8 决策A,默认 Spring Security 7 + 自建 OAuth2 token)
│   ├── aurora-starter-mybatis            # BaseDO/BaseMapperX/LambdaQueryWrapperX/TypeHandler/easy-trans
│   ├── aurora-starter-datasource         # dynamic-datasource boot4(多数据源) ◆重能力
│   ├── aurora-starter-redis              # Redisson 4.6 + Jackson3(删旧 JavaTimeModule 样板)
│   ├── aurora-starter-biz-tenant         # TenantBaseDO + MP拦截器 + Redis隔离 + 可开关 ◆重能力
│   ├── aurora-starter-biz-data-permission# @DataPermission 规则引擎 ◆重能力
│   ├── aurora-starter-biz-bpm            # Flowable 8 封装 ◆重能力
│   ├── aurora-starter-job                # Quartz 定时任务
│   ├── aurora-starter-mq                 # Redis Stream / RabbitMQ / RocketMQ
│   ├── aurora-starter-excel              # FastExcel 导入导出 + 字典转换
│   ├── aurora-starter-protection         # 限流/幂等/分布式锁
│   ├── aurora-starter-websocket          # WebSocket
│   └── aurora-starter-test               # 单测基座(H2 + redis mock + BaseDbUnitTest)
├── aurora-module-system/                 # 样本+地基:用户/角色/部门/菜单/字典/租户/RBAC/oauth2/social/logger/notify/sms/mail
├── aurora-module-infra/                  # 样本:文件/codegen HTTP 入口/dbdoc/job/config/file 多存储
├── aurora-server/                        # 启动 shell + application.yaml + logback
└── docs/                                 # 本文档集
```

### 4.1 单体期的模块内部包结构(每模块统一)

```
cn.<brand>.module.<name>/
├── controller/
│   ├── admin/<feature>/          # 管理端接口(自动绑定 /admin-api 前缀)
│   │   └── vo/                   # *ReqVO / *RespVO / *PageReqVO / *SaveReqVO
│   └── app/<feature>/            # 用户端接口(自动绑定 /app-api 前缀)
├── service/<feature>/            # XxxService + XxxServiceImpl
├── dal/
│   ├── dataobject/<feature>/     # XxxDO extends BaseDO / TenantBaseDO
│   ├── mysql/<feature>/          # XxxMapper extends BaseMapperX
│   └── redis/<feature>/          # XxxRedisDAO(可选)
├── api/<feature>/                # 跨模块契约:XxxApi + XxxApiImpl + dto/(*RespDTO)
├── convert/<feature>/            # MapStruct Convert(复杂映射)
├── enums/                        # ErrorCodeConstants / 业务枚举
├── framework/                    # 本模块的 Spring 配置(security/datapermission 等)
├── job/                          # 定时任务(可选)
└── mq/                           # 消息生产/消费(可选)
```

### 4.2 演进到微服务时的拆分(预留口子)

单体期每模块一个 Maven module,`api/` 包内置。演进到微服务时,**把 `api/` + DTO 抽为独立 `aurora-module-<name>-api` 子模块**(yudao 在 `trade-api` 上已实践):

```
aurora-module-trade/              # 实现端
└── cn.aurora.module.trade/{controller, service, dal/mysql, convert, ...}

aurora-module-trade-api/          # 契约端(演进时新增)
└── cn.aurora.module.trade.api/{api/order/TradeOrderApi, dto/order/, enums/}
```

这样拆分后,跨模块调用从"依赖整个实现模块"瘦身为"只依赖契约 jar"。**具体的架构切换机制(本地实现 ↔ Feign 远程,零代码改动)见 [§6](#6-单体微服务一套代码)。**

---

## 5. 跨模块契约(双轨制)

这是 yudao 最精华的设计,Aurora 直接继承。

### 5.1 业务间契约:`module/*/api/XxxApi`

**场景**:业务模块 A 调用业务模块 B 的能力(如 mall 调 system 查用户)。

```java
// aurora-module-system: api/dept/DeptApi.java(接口,与 Impl 同模块)
public interface DeptApi {
    DeptRespDTO getDept(Long id);
    List<DeptRespDTO> getDeptList(Collection<Long> ids);
    void validateDeptList(Collection<Long> ids);
}

// aurora-module-system: api/dept/DeptApiImpl.java(@Service,注入本模块 Service)
@Service
public class DeptApiImpl implements DeptApi {
    @Resource private DeptService deptService;
    @Override
    public DeptRespDTO getDept(Long id) {
        return BeanUtils.toBean(deptService.getDept(id), DeptRespDTO.class);
    }
}

// aurora-module-mall: 调用方(本地注入,零网络开销)
@Resource private DeptApi deptApi;
```

**关键约定**:
- 接口 + Impl + DTO **同模块同包**(`api/<feature>/` + `api/<feature>/dto/`)
- 返回值**永远用 `*RespDTO`**,绝不返回 DO(DO→DTO 用 `BeanUtils.toBean`)
- 调用方 Maven 硬依赖整个被调模块(单体可接受;微服务期改为依赖 `*-api` 瘦 jar)

### 5.2 框架反调业务契约:`common/biz/*CommonApi`(依赖倒置)

**场景**:框架 starter 需要调用业务能力,但 starter 不能依赖业务模块。

> 经典矛盾:`security` starter 的 `TokenAuthenticationFilter` 要校验 token,但 token 表在 `module-system`。starter 在 framework 层,不能反向依赖业务 module。

**解法**:接口下沉到 `aurora-common`,业务模块提供实现。

```java
// aurora-common: biz/system/oauth2/OAuth2TokenCommonApi.java(框架层定义接口)
public interface OAuth2TokenCommonApi {
    OAuth2AccessTokenCheckRespDTO checkAccessToken(String accessToken);
}

// aurora-module-system: api/oauth2/OAuth2TokenApiImpl.java(业务层提供实现)
@Service
public class OAuth2TokenApiImpl implements OAuth2TokenCommonApi {
    @Resource private OAuth2AccessTokenService oauth2AccessTokenService;
    @Override
    public OAuth2AccessTokenCheckRespDTO checkAccessToken(String accessToken) {
        return oauth2AccessTokenService.checkAccessToken(accessToken);
    }
}

// aurora-starter-security: TokenAuthenticationFilter(框架层注入接口,钩回业务实现)
private final OAuth2TokenCommonApi oauth2TokenApi;
// ... oauth2TokenApi.checkAccessToken(token);
```

### 5.3 Aurora common/biz/ 的 7 个 CommonApi(xiaoqu 现状)

| CommonApi | 位置 | DTO | 用途 |
|---|---|---|---|
| `OAuth2TokenCommonApi` | common.biz.system.oauth2 | OAuth2AccessTokenCreate/Check/RespDTO | token 校验/创建(security starter 调) |
| `PermissionCommonApi` | common.biz.system.permission | DeptDataPermissionRespDTO | 权限/数据权限校验(security + data-permission starter 调) |
| `TenantCommonApi` | common.biz.system.tenant | — | 租户列表/校验(tenant starter 调) |
| `DictDataCommonApi` | common.biz.system.dict | DictDataRespDTO | 字典查询(excel/web starter 调) |
| `OperateLogCommonApi` | common.biz.system.logger | OperateLogCreateReqDTO | 操作日志记录 |
| `ApiAccessLogCommonApi` | common.biz.infra.logger | ApiAccessLogCreateReqDTO | API 访问日志 |
| `ApiErrorLogCommonApi` | common.biz.infra.logger | ApiErrorLogCreateReqDTO | API 错误日志 |

> ⚠️ **迁移第 0 步必须先迁这 7 个 CommonApi 到 aurora-common**,否则全平台编译断裂。

---

## 6. 单体↔微服务:一套代码

这是 dante-cloud 最关键的差异化价值。以下实现基于 **dante-engine 真实代码核实**(v4.1.0.4,`/Users/xq/01-code/xq/dante-engine/dante-framework/dante-spring`),非猜测。dante-engine 已本地 clone(54 模块)。

### 6.1 目标

**同一份业务代码,通过 YAML 一行配置在单体/微服务间切换,业务代码、接口签名、模块边界零改动。**

```
aurora:
  architecture: monolith     # ← 切换开关:monolith(默认) | distributed
```

### 6.2 机制 ①:条件注解 `@ConditionalOnArchitecture`(可直接移植)

> **核实结论**:dante-engine 的条件注解是**"枚举即条件"的三层委托设计**——把"是否激活"的判断下放到枚举常量自身,而非写在 Condition 里。新增一种架构维度只需加一个枚举 + 一个 2 行 Condition 子类。**4 个文件 + 1 枚举,共 ~120 行,Boot4 零兼容性问题,可直接移植代码。**

放置位置:借鉴 dante 的 `dante-spring`,Aurora 放 `aurora-starter-architecture`(或按 dante 分层放更底层的 `aurora-spring`,见 §6.7)。

```java
// ① 枚举约定接口(可复用于其它维度,如 DataAccessStrategy)
public interface ConditionEnum {
    boolean isActive(Environment environment);
    String getConstant();
    default boolean isActive(Environment environment, String property) {
        String value = environment.getProperty(property);
        return StringUtils.isNotBlank(value) && value.equalsIgnoreCase(getConstant());
    }
}

// ② 架构枚举:枚举自己实现 isActive(DISTRIBUTED 正向匹配,MONOLITH 取反 → 默认单体)
public enum Architecture implements ConditionEnum {
    DISTRIBUTED {
        @Override public boolean isActive(Environment env) {
            return isActive(env, "aurora.architecture");  // 读 aurora.architecture 属性
        }
        @Override public String getConstant() { return name(); }
    },
    MONOLITH {
        @Override public boolean isActive(Environment env) {
            return !DISTRIBUTED.isActive(env);  // ← 关键:默认单体(无配置即单体)
        }
        @Override public String getConstant() { return name(); }
    };
}

// ③ 抽象基类:委托给枚举自身的 isActive
public abstract class AbstractEnumSpringBootCondition<T extends ConditionEnum> extends SpringBootCondition {
    protected abstract Class<? extends Annotation> getAnnotationClass();
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        T[] enums = (T[]) metadata.getAnnotationAttributes(getAnnotationClass().getName()).get("value");
        // ... 读取注解 value,委托 enums.isActive(context.getEnvironment()) 返回 match/noMatch
    }
}

// ④ 具体 Condition:只绑定注解类型(2 行)
class OnArchitectureCondition extends AbstractEnumSpringBootCondition<Architecture> {
    @Override protected Class<? extends Annotation> getAnnotationClass() {
        return ConditionalOnArchitecture.class;
    }
}

// ⑤ 注解
@Target({ElementType.TYPE, ElementType.METHOD})  // ★ 可用于 @Bean 方法级
@Retention(RetentionPolicy.RUNTIME)
@Conditional(OnArchitectureCondition.class)
public @interface ConditionalOnArchitecture {
    Architecture value();
}
```

**设计精髓**:`AbstractEnumSpringBootCondition` + `ConditionEnum` 接口是通用骨架,后续若要加"数据访问策略切换"(`@ConditionalOnDataAccessStrategy`),只需一个 `DataAccessStrategy` 枚举(实现 `ConditionEnum`)+ 一个 2 行 Condition,**无需改框架**。

### 6.3 机制 ②:跨模块调用的 Local/Remote 切换(需自研,不能照搬)

> ⚠️ **重要修正**:我之前(基于 dante-cloud 装配层)以为 dante 有 `LocalStrategyUserDetailsService`/`FeignStrategyUserDetailsService` 双实现。核实 dante-engine v4.1.0.4 发现**这两个类已被删除**——dante 现在 UAA 直连库,`StrategyUserDetailsService` 退化成单实现接口注入。**Aurora 不能照搬,需自行设计 Local/Feign 双实现。**

Aurora 的设计(借鉴 dante 的"接口注入 + Strategy"思路,用 Boot4 原生实现):

```java
// 业务契约接口(单体/微服务共享,定义在 api 包)
public interface TradeOrderApi {
    TradeOrderRespDTO getOrder(Long id);
}

// 单体实现:直连本地 Service(默认装配)
@Service
@ConditionalOnArchitecture(Architecture.MONOLITH)
public class TradeOrderApiLocalImpl implements TradeOrderApi {
    @Resource private TradeOrderService tradeOrderService;
    @Override public TradeOrderRespDTO getOrder(Long id) {
        return BeanUtils.toBean(tradeOrderService.getOrder(id), TradeOrderRespDTO.class);
    }
}
// 微服务实现:契约接口加 @FeignClient(演进时启用,阶段 8+)
```

### 6.4 机制 ③:消息/事件的 Local/Remote Listener 成对(可直接移植模式)

> ⚠️ **重要修正**:dante-engine **没有** BusBridge 空实现类(全仓零命中)。单体短路的真实做法是 **Local Listener(默认/无条件)+ Remote Listener(`@ConditionalOnArchitecture(DISTRIBUTED)`)成对**,配合 `@ConditionalOnClass(StreamBusBridge.class)` 双保险。

```java
@AutoConfiguration
public class AuroraServiceMessageAutoConfiguration {

    // 单体:本地 Listener(始终开启,进程内事件)
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnArchitecture(Architecture.MONOLITH)
    static class LocalMessageConfiguration {
        @Bean public LocalMessageListener localMessageListener(...) { ... }
    }

    // 分布式:远程 Bus Listener(仅分布式 + classpath 有 Bus 才开启)
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnArchitecture(Architecture.DISTRIBUTED)
    @ConditionalOnClass(StreamBusBridge.class)
    static class RemoteMessageConfiguration {
        @Bean public RemoteMessageListener remoteMessageListener(...) { ... }
    }
}
```

**效果**:单体模式完全不引入 Spring Cloud Bus / Kafka 依赖,Remote 配置类不装配;切到分布式时,Remote Listener 自动接管,通过 Bus 跨进程传播。

### 6.5 机制 ④:`@EnableAuroraXxx` 模块开关(可直接移植)

> 核实:dante 的 `@EnableHerodotusXxx` 统一是 `@Import(一个 @Configuration)` 一行注解,Configuration 内部用 `@ComponentScan`/`@Import` 组合整个模块。Boot4 完全兼容,零改动可搬。

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(AuroraLogicSystemConfiguration.class)  // ← 唯一机制:@Import
public @interface EnableAuroraSystem {}

// 被引用的 Configuration 组合扫描范围
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"cn.aurora.module.system"})
@Import({AuroraSecurityConfiguration.class})  // 可链式 Import
public class AuroraLogicSystemConfiguration {}
```

与 `@AutoConfiguration`(隐式自动装配)互补:`@EnableXxx` 是**显式开关**,让使用方主动声明"我要启用这个模块"。

### 6.6 单体↔微服务切换对照

| 关注点 | monolith(默认) | distributed(演进) |
|---|---|---|
| 跨模块调用 | `XxxApi` Local Impl(`@ConditionalOnArchitecture(MONOLITH)`) | `XxxApi` Feign 远程 |
| 跨进程事件 | Local Listener(始终开启,进程内) | Remote Listener(`@ConditionalOnArchitecture(DISTRIBUTED)` + `@ConditionalOnClass(StreamBusBridge)`) |
| 服务发现 | 无(同进程) | Nacos/Polaris |
| 配置中心 | 本地 `application.yaml` | Nacos(可选) |
| 网关 | 无 | Spring Cloud Gateway + 防伪造内部调用头(D5) |

### 6.7 framework 分层(吸收 dante-engine)

dante-engine 的分层很干净,Aurora 照搬:

- `aurora-common`(对应 `dante-core`):**零 Spring 依赖**最底层,常量/枚举/工具/异常/domain
- `aurora-spring`(对应 `dante-spring`):Spring 基础设施层,**条件注解体系**(`@ConditionalOnArchitecture` 等)+ Jackson 序列化器
- 其余 starter 在 `aurora-spring` 之上

> 注:`@ConditionalOnDataAccessStrategy`(LOCAL/REMOTE 数据访问策略切换)在 dante 放 `dante-web`,Aurora 可统一放 `aurora-spring` 简化分层。

### 6.8 与 yudao 双轨契约的关系

- **不冲突,是叠加增强**。yudao 的"业务间 api 接口 + 框架反调 biz/CommonApi"是**契约定义层**;dante 的"配置驱动架构切换"是**实现装配层**。
- Aurora 的 `XxxApi` 接口在单体期用 Local Impl,微服务期切 Feign——**接口本身不变**,契约层零改动。

### 6.9 移植复杂度评估(基于真实代码)

| 机制 | 复杂度 | 可移植性 | 说明 |
|---|---|---|---|
| ① `@ConditionalOnArchitecture` 体系 | **简单** | **可直接移植代码** | 4 文件 + 1 枚举 ~120 行,纯 Spring API,Boot4 零兼容问题 |
| ② Local/Feign 双实现 | **中等** | **需自研** | dante v4.1.0.4 已删除双实现;Aurora 需自己设计接口+双实现+条件装配 |
| ③ Local/Remote Listener 成对 | **简单** | **可直接移植模式** | 不是 BusBridge;Listener 代码极简,Boot4 兼容 |
| ④ `@EnableXxx` 开关 | **简单** | **可直接移植** | `@Import` 一行注解,零改动 |
| ⑤ framework 分层 core→spring | **简单** | **可直接移植架构** | dante-core 零依赖 → dante-spring 条件注解,分层干净 |
| ConfigurerManager(SAS OAuth2) | **中等** | **需用 SAS 原生重写** | 聚合根思路好,但 dante 用的 SAS Customizer 在新版本有变化;首期不上(决策项 A) |

### 6.10 首期落地范围(克制)

- **首期(阶段 2-6)**:机制 ①②③④⑤ 就位。默认只有 Local 实现(单体),**Feign 远程实现留到阶段 8+**。
- **意义**:脚手架从第一天就内置"一套代码两种架构"的**机制骨架**,业务代码按"注入接口、不直连 Service"范式写,未来切微服务无需返工。

---

## 7. Boot4 + JDK25 适配清单

> 详见 `boot4-migration-notes.md`(含全量代码片段)。此处仅列踩坑点。

1. ⚠️ **坐标改名**:`mybatis-plus-spring-boot4-starter`、`dynamic-datasource-spring-boot4-starter`、`druid-spring-boot-4-starter`
2. **Redisson 4.6.1**:`@AutoConfiguration(before = RedissonAutoConfigurationV4.class)`;Spring Data Redis 4 用 **Jackson 3**,`RedisSerializer.json()` 原生支持 `LocalDateTime`,**删掉**旧 `JavaTimeModule` 手写样板
3. **Security 7 lambda DSL**:`authorizeHttpRequests`(非 `authorizeRequests`)、`requestMatchers`(非 `antMatchers`)、`dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()`(放行 SSE)
4. **easy-trans 3.1.5**:`BaseDO implements TransPojo` + `@JsonIgnoreProperties("transMap")`
5. **装配文件**:`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`(无 spring.factories)
6. **yaml**:`spring.data.redis`(非 `spring.redis`)、`spring.cache.type: REDIS`、`spring.main.allow-circular-references: true`(yudao 实测真实需要)
7. **jakarta.\*** 全量替换 javax.*(greenfield 无历史包袱,天然满足)
8. **enforcer 补强**:`requireJavaVersion≥25` + `requireMavenVersion≥3.9`

---

## 8. 分阶段实施计划

| 阶段 | 目标 | 产出物 | 验收 |
|---|---|---|---|
| **0. 决策对齐** | 确认 §9 四项 | 决策记录 | 文档批准 + 4 项回执 |
| **1. 设计文档落盘** | ✅ 完成(本次 v0.2) | docs/ 5 份 + README + AGENTS | 文档评审通过 |
| **2. 骨架可编译** | Boot4+JDK25 跑通空壳 | BOM + common + web/mybatis/redis/security + architecture(条件注解框架)+ server + `GET /hello` 返回 CommonResult | mvn install + spring-boot:run + curl + springdoc UI 出 |
| **3. system+infra 样本** | 打通完整 CRUD | security 全链路 + system(用户/角色/部门/字典)+ infra(文件)+ demo 增删改查分页 | 登录拿 token → CRUD → 落 PG → 操作日志 |
| **4. 重能力接入** | 逐个可开关 | tenant(默认关)/data-permission/datasource(多源)/bpm(Flowable8) | 各能力开关测试;多租户数据隔离验证 |
| **5. 代码生成器** | 后端 codegen | 连库读表 → DO/Mapper/Service/Controller/VO/SQL;预留 api-fast/api 双模板 | 一键生成可编译可运行 CRUD |
| **6. 架构切换骨架** | "一套代码两种架构"机制就位 | `@ConditionalOnArchitecture`(枚举即条件三层委托)+ Local/Feign 双实现(Local 默认)+ Local/Remote Listener 成对(单体不连 Kafka)+ `@EnableXxx` 开关;Feign 实现留空位 | 单体模式验证通过;`aurora.architecture` 属性可读 |
| **7. 迁移启动** | 按 L0→L6 迁现有模块 | 详见 `migration/aurora-migration-plan.md` | 各层验收通过 |
| **8+. 微服务演进** | 启用 distributed 架构 | Feign 实现 + 服务发现 + 配置中心 + 网关防伪造头(D5) | 切 `aurora.architecture: distributed` 启动成功,跨服务调用通 |

---

## 9. 待决策项

以下为**推荐默认值**,文档已据此撰写,可在评审后调整(品牌名/groupId 全文可一键替换)。

### A. 认证授权方案(推荐 A)

| 方案 | 优点 | 代价 | Boot4 成熟度 |
|---|---|---|---|
| **A. Spring Security 7 + 自建 OAuth2 token 表 + Redis**(推荐) | 团队最熟;xiaoqu 现状即此;多租户/数据权限/操作日志集成成本最低;ruoyi master-jdk25 已验证 Boot4 跑通 | 自管 token 表/刷新/登出逻辑 | ✅ 有活样本(yudao) |
| B. Sa-Token | API 极简、中文文档好、上手快 | 与 yudao 系数据权限/租户/操作日志需大量重写适配;greenfield 要自建整套集成 | ⚠️ 需验证 Boot4 starter |
| C. Spring Authorization Server + ConfigurerManager | 最"正规"标准 OAuth2 授权服务器;**dante-cloud 的 ConfigurerManager 模式可封装 SAS 复杂配置,业务方零配置**;支持 passkey/国密/社交扩展 | 最重;password grant 已弃用需适配;dante 底层实现在 dante-engine(未本地),需自行实现 ConfigurerManager | ✅ dante-cloud 有 Boot4 活样本 |

**推荐 A 的理由**:首期全内置多租户+数据权限+BPM,这些与 yudao 系 security/operatelog/tenant 深度耦合,方案 A 集成成本最低且有 Boot4 活样本可照抄。**若未来需要标准化 OAuth2(开放平台/第三方接入/passkey),可参考 dante-cloud 的方案 C 升级**。

### B. 品牌名(推荐 Aurora)

候选 **Aurora**(极光/启明),与 `xq-ui/xq-starter/aurora-admin` 一脉相承。
- 建议坐标:`<groupId>cn.aurora</groupId>`(可调)
- 包名:`cn.aurora.framework.*` / `cn.aurora.module.*`
- 仓库名:`aurora-boot`

### C. 仓库位置(推荐 ~/01-code/xq/aurora-boot/)

已创建于 `~/01-code/xq/aurora-boot/`,git init 完成。

### D. 范式兼容承诺(推荐:100% 兼容)

确认脚手架与 xiaoqu 现有 yudao 范式 100% 兼容:
- `BaseDO`(5 审计字段 + `@TableLogic` + `TransPojo`)
- `BaseMapperX extends MPJBaseMapper`(分页/Join/ForUpdate/批量 default 方法)
- `LambdaQueryWrapperX`(xxxIfPresent 系列)
- `CommonResult` + `ErrorCode` + `ServiceException`
- `module/*/api/XxxApi` + `common/biz/*CommonApi` 双轨契约
- VO 命名:`*ReqVO` / `*RespVO` / `*PageReqVO` / `*SaveReqVO`

**这是保证 6000 文件零返工的前提。** Aurora 可在兼容基础上做增量增强(如提供 `BaseConvert` 模板方法补便捷性),但不改变既有 API 形状。

---

## 附录:调研依据

本文档基于以下真实代码核实:
- **bladex**:`/Users/xq/01-code/xq/bladex/`(BladeX-Tool 45 模块、BladeX-Boot 单体、BladeX-Biz 业务样本、CLAUDE.md 两份)
- **ruoyi-vue-pro**:`origin/master-jdk25` 分支(JDK25 + Boot4.1.0,git show 读取,未 checkout)
- **dante-cloud**:`/Users/xq/01-code/xq/dante-cloud/`(JDK25 + Boot4.1 + Spring Cloud 2025.1.2,v4.1.0.4;装配层代码)
- **dante-engine**:`/Users/xq/01-code/xq/dante-engine/`(54 模块,框架核心实现;条件注解在 `dante-framework/dante-spring`,已逐行核实 `@ConditionalOnArchitecture` 体系)
- **xiaoqu-platform**:`/Users/xq/01-code/xq/xiaoqu-platform/`(13+ 模块、~6000 文件、~57 万行)
- **Spring Boot 4**:2025-11-20 GA,Spring Framework 7,Jakarta EE 11,Servlet 6.1,最低 JDK17/推荐 25
