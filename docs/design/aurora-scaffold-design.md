# Aurora 脚手架 · 架构设计

> 状态:**DRAFT v0.1** · 日期:2026-07-22
> 技术基线:**JDK 25 + Spring Boot 4.1.0**(Spring Framework 7 / Jakarta EE 11 / Servlet 6.1)
> 定位:单体优先、可演进微服务的业务开发脚手架
> 身份:全新独立 greenfield 仓库,**与 xiaoqu-platform 现有 yudao 范式 100% 兼容**(保证 6000 文件零返工)

---

## 0. 阅读顺序

1. [§1 定位与约束](#1-定位与约束)—— 为什么是"范式提炼 + 升级 + 独立",而非"范式创新"
2. [§2 吸收清单](#2-吸收清单bladex--yudao)—— 从两个脚手架各吸收什么、舍弃什么,逐项附理由
3. [§3 技术栈基线](#3-技术栈基线boot4--jdk25)—— 已 git show 核实的精确坐标
4. [§4 模块划分](#4-模块划分)—— 工程骨架
5. [§5 跨模块契约](#5-跨模块契约双轨制)—— yudao 最精华设计
6. [§6 Boot4+JDK25 适配](#6-boot4jdk25-适配清单)—— 踩坑点全列(详见 `boot4-migration-notes.md`)
7. [§7 分阶段实施](#7-分阶段实施计划)—— 从骨架到迁移
8. [§8 待决策项](#8-待决策项)—— 默认值与备选

---

## 1. 定位与约束

### 1.1 一句话定位

**Aurora = 把 xiaoqu-platform 已深度使用的 yudao 范式,抽干净成独立品牌产物,升级到 Boot4+JDK25,并择优吸收 bladex 的工程机制。**

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

### 1.4 运行模式

**单体优先 + 演进路线预留**:

- 单体期:每业务模块一个 Maven module,模块间走 `api/` 包契约 + 本地 `@Service` 注入,**零网络开销**
- 演进期:把高频被调的 `api/`+DTO 抽为 `X-api` 独立子模块(yudao 在 `trade-api` 上已局部实践),为未来 Feign 切换留口子
- 微服务期:同一 `api/` 接口加 `@FeignClient` 即可切远程调用,业务代码零改动

---

## 2. 吸收清单(bladex + yudao)

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

### 2.4 两方融合的关键判断

| 设计点 | bladex | yudao | Aurora 选择 |
|---|---|---|---|
| 统一响应 | `R<T>`(带 success) | `CommonResult<T>`(无 success + 异常体系) | **yudao**(Boot3/4 现代写法) |
| VO 转换 | Wrapper(反射) | Convert(MapStruct) | **yudao**(性能+类型安全) |
| 版本管理 | blade-bom 子模块 | yudao-dependencies 独立顶级 | **融合**:结构跟 yudao(独立顶级),flatten 配置参数抄 bladex(pomElements) |
| 模块拆分 | 单体全揉 / 微服务 api+service | 统一 module(单体形态) | **bladex 演进路径**:单体 modules/X,演进抽 X-api |
| 跨模块契约 | Feign IXxxClient(独立 api jar) | 双轨(api 包本地 + biz/CommonApi 下沉) | **yudao 双轨**(最精华),演进借鉴 bladex 抽独立 api jar |
| 装配生成 | blade-core-auto 编译期处理器 | 手写 AutoConfiguration.imports | **yudao 手写**(Boot4 已不需要 spring.factories) |
| 启动入口 | BladeApplication.run + LauncherService SPI | 裸 SpringApplication.run + spring.config.import | **yudao 裸 run**(单体无聚合场景) |

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

单体期的 `api/` 包 + `dal/dataobject/` 的 DTO 部分,**整体上移**为独立 `aurora-module-<name>-api` 子模块:

```
aurora-module-trade/              # 实现端(单体时即此形态)
└── cn.<brand>.module.trade/
    ├── controller/ service/ dal/mysql/ convert/ ...

aurora-module-trade-api/          # 契约端(微服务演进时新增)
└── cn.<brand>.module.trade.api/
    ├── api/order/TradeOrderApi   # 接口(单体本地 impl,微服务加 @FeignClient)
    ├── dto/order/                # *RespDTO
    └── enums/                    # 共享枚举
```

业务代码**零改动**:调用方从 `@Resource TradeOrderApi`(本地)到 `@Resource TradeOrderApi`(@FeignClient)注入方式不变。

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

## 6. Boot4 + JDK25 适配清单

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

## 7. 分阶段实施计划

| 阶段 | 目标 | 产出物 | 验收 |
|---|---|---|---|
| **0. 决策对齐** | 确认 §8 四项 | 决策记录 | 文档批准 + 4 项回执 |
| **1. 设计文档落盘** | 本步(进行中) | docs/ 5 份 + README + AGENTS | 文档评审通过 |
| **2. 骨架可编译** | Boot4+JDK25 跑通空壳 | BOM + common + web/mybatis/redis/security + server + `GET /hello` 返回 CommonResult | mvn install + spring-boot:run + curl + springdoc UI 出 |
| **3. system+infra 样本** | 打通完整 CRUD | security 全链路 + system(用户/角色/部门/字典)+ infra(文件)+ demo 增删改查分页 | 登录拿 token → CRUD → 落 PG → 操作日志 |
| **4. 重能力接入** | 逐个可开关 | tenant(默认关)/data-permission/datasource(多源)/bpm(Flowable8) | 各能力开关测试;多租户数据隔离验证 |
| **5. 代码生成器** | 后端 codegen | 连库读表 → DO/Mapper/Service/Controller/VO/SQL;预留 api-fast/api 双模板 | 一键生成可编译可运行 CRUD |
| **6. 迁移启动** | 按 L0→L6 迁现有模块 | 详见 `migration/aurora-migration-plan.md` | 各层验收通过 |

---

## 8. 待决策项

以下为**推荐默认值**,文档已据此撰写,可在评审后调整(品牌名/groupId 全文可一键替换)。

### A. 认证授权方案(推荐 A)

| 方案 | 优点 | 代价 | Boot4 成熟度 |
|---|---|---|---|
| **A. Spring Security 7 + 自建 OAuth2 token 表 + Redis**(推荐) | 团队最熟;xiaoqu 现状即此;多租户/数据权限/操作日志集成成本最低;ruoyi master-jdk25 已验证 Boot4 跑通 | 自管 token 表/刷新/登出逻辑 | ✅ 有活样本 |
| B. Sa-Token | API 极简、中文文档好、上手快 | 与 yudao 系数据权限/租户/操作日志需大量重写适配;greenfield 要自建整套集成 | ⚠️ 需验证 Boot4 starter |
| C. Spring Authorization Server | 最"正规"标准 OAuth2 授权服务器 | 最重;password grant 已弃用;单体场景过度设计 | ✅ 标准但重 |

**推荐 A 的理由**:首期全内置多租户+数据权限+BPM,这些与 yudao 系 security/operatelog/tenant 深度耦合,方案 A 集成成本最低且有 Boot4 活样本可照抄。

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
- **xiaoqu-platform**:`/Users/xq/01-code/xq/xiaoqu-platform/`(13+ 模块、~6000 文件、~57 万行)
- **Spring Boot 4**:2025-11-20 GA,Spring Framework 7,Jakarta EE 11,Servlet 6.1,最低 JDK17/推荐 25
