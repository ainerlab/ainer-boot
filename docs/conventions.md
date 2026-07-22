# Aurora 工程约定

> 状态:**DRAFT v0.2** · 日期:2026-07-22
> 适用范围:aurora-boot 全工程(framework + module + server)
> 文档骨架借鉴 BladeX-Tool/CLAUDE.md(章节组织)
> **范式立场**:不预设继承 yudao,主动改 7 缺陷(见 `design/paradigm-redesign.md`)
> 协作语言:**中文**
> ⚠️ 注:§3-§5 的部分代码示例仍沿用旧命名(UserDO/SaveReqVO 等),将随阶段 2 代码落地一并改为新命名(Entity/CreateReq 等)。**规则以 §2 命名规范和 `paradigm-redesign.md` 为准。**

---

## 0. 协作准则

1. **范式立场优先**:本工程**不预设继承 yudao**,主动改掉 yudao 7 缺陷(循环依赖/数据权限粗/token 自造/命名分层乱/模块边界不清/错误码/异常处理)。写代码前必读 `design/paradigm-redesign.md`。
2. **模仿优先**:编写新功能前,先读现有同类代码,复用既有抽象(BaseEntity/BaseMapperX/CommonResult/XxxApi)。
3. **最小改动**:重构与功能新增分离,一个 PR 不做两件事。
4. **Boot4 纪律**:符合 Boot4+JDK25 适配(见 `boot4-migration-notes.md`),禁止 javax.*(除 JDK 内置)、禁止 spring.factories 自动装配。

---

## 1. 工程定位与架构

### 1.1 工程性质

- **framework 层**(`aurora-framework/`):提供 starter,是"基础设施提供者",不含业务 CRUD。**core→spring 分层**(吸收 dante-engine)。
- **业务层**(`aurora-module-*/`):**kernel vs business 分离**(缺陷5)。`module-kernel` 只放纯技术,业务模块各自独立。
- **启动层**(`aurora-server/`):空壳,靠 pom 依赖决定启动范围。

### 1.2 核心理解要点(必须记住)

1. **BOM 管版本**:`aurora-dependencies` 是唯一版本源,子模块 pom **禁止写版本号**。
2. **core 是最底层**:`aurora-core` 零 Spring 依赖;`aurora-common` 不反向依赖业务模块;跨模块契约放 `common/biz/`(依赖倒置)。
3. **跨模块契约单向**(缺陷1):业务间用 `integration/api/XxxApi`(**单向**,禁 Service 互注);"完成后通知"走**领域事件**;框架反调业务用 `common/biz/*CommonApi`(依赖倒置)。详见 `design` §5。
4. **starter 用 `@AutoConfiguration`**:装配走 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,**不写 spring.factories 的 EnableAutoConfiguration**。
5. **循环依赖零容忍**(缺陷1):`allow-circular-references: false`,`@Lazy` 禁用(除非充分理由+注释),跨域走 api 接口/事件。

---

## 2. 命名规范

### 2.1 包名

- 框架:`cn.aurora.framework.<层>.<功能>`(如 `cn.aurora.framework.mybatis.core.mapper`)
- 业务:`cn.aurora.module.<模块名>.<层>.<功能>`(如 `cn.aurora.module.kernel.controller.admin.dept`)— **≤4 层包**(缺陷4)
- 启动:`cn.aurora.server`
- common 跨模块契约:`cn.aurora.framework.common.biz.<源模块>.<功能>`(如 `cn.aurora.framework.common.biz.kernel.oauth2`)

### 2.2 类名后缀词典(按角色)

| 角色 | 后缀 | 示例 |
|---|---|---|
| 配置类 | `AutoConfiguration` / `Properties` | `AuroraRedisAutoConfiguration`、`WebProperties` |
| 工具类 | `Util` / `Utils` | `BeanUtils`、`DateUtils` |
| 拦截器 | `Interceptor` | `TenantDatabaseInterceptor` |
| 处理器 | `Handler` | `DefaultFieldHandler`、`GlobalExceptionHandler` |
| 过滤器 | `Filter` | `TokenAuthenticationFilter` |
| 常量 | `Constants` / `Constant` | `RedisKeyConstants` |
| 枚举 | `Enum` / `Type` | `UserTypeEnum`、`CommonStatusEnum` |
| ★ 实体 | **`Entity`**(**原 DO**,缺陷4) | `UserEntity extends BaseEntity` |
| ★ 视图对象 | **`Resp`/`CreateReq`/`UpdateReq`/`PageReq`**(**原 RespVO 等 7 种,缺陷4**) | `UserResp`、`UserCreateReq`、`UserPageReq` |
| 数据传输 | `DTO` | `UserRespDTO`(跨模块,MapStruct 生成) |
| Mapper | `Mapper` | `UserMapper extends BaseMapperX<UserEntity>` |
| Service 接口 | `Service` | `UserService` |
| Service 实现 | `ServiceImpl` | `UserServiceImpl` |
| Controller | `Controller` | `UserController` |
| 跨模块契约接口 | `Api` | `DeptApi`(业务间)、`OAuth2TokenCommonApi`(框架反调) |
| 转换器 | `Convert` | `UserConvert`(@Mapper,MapStruct) |
| ★ 领域事件 | **`Event`**(缺陷1) | `OrderCreatedEvent`(`event/` 包) |
| Redis DAO | `RedisDAO` | `UserRedisDAO` |
| 异常 | `Exception` | `ServiceException`、`ServerException` |

### 2.3 变量与常量

- 变量:**语义化**,避免 `data1`/`temp`/`obj`。
- 常量:`UPPER_SNAKE_CASE`,定义在 `interface XxxConstants` 或枚举中。
- 表名:`<前缀>_<下划线命名>`,逻辑删除字段统一 `deleted`(Boolean),审计字段统一 `create_time/update_time/creator/updater`。

### 2.4 VO 命名约定(★ 收敛,缺陷4)

> yudao 有 7 种 VO 后缀(ReqVO/RespVO/PageReqVO/SaveReqVO/SimpleRespVO/ExportReqVO/ExcelVO)。Aurora **收敛到 3 种**。

| 类型 | 命名 | 用途 |
|---|---|---|
| ★ 写请求 | `XxxCreateReq` / `XxxUpdateReq`(或合并 `XxxSaveReq`) | 新增/修改入参 |
| ★ 读响应 | `XxxResp` | 接口返回(列表/详情共用,字段按需) |
| ★ 分页请求 | `XxxPageReq extends PageReq` | 分页查询(条件 + 分页参数)→ `PageResult<XxxResp>` |
| ~~SimpleRespVO~~ | 取消,用 `XxxResp` + 字段过滤 | — |
| ~~ExportReqVO/ExcelVO~~ | 取消,用 `XxxPageReq` + 导出注解 | — |

VO 放对应 controller 子包下(`controller/admin/vo/`),不再按领域再分一层(消除 yudao 6 层包)。

---

## 3. 编码规范

### 3.1 格式(.editorconfig)

- 缩进:4 空格(Java)/ 2 空格(yaml/vue)
- 编码:UTF-8
- 换行:LF
- 列宽:120

### 3.2 类级注解顺序(Controller 示例)

```java
@Tag(name = "管理后台 - 用户")
@RestController
@RequestMapping("/system/user")
@Validated
public class UserController { ... }
```

### 3.3 字段级注解顺序(DO 示例)

```java
@TableName("system_user")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UserDO extends BaseDO {
    @TableId
    private Long id;

    @Schema(description = "用户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "小趣")
    private String name;
}
```

### 3.4 Lombok

- DO:`@Data` + `@EqualsAndHashCode(callSuper = true)` + `@ToString(callSuper = true)`(继承 BaseDO 时)。
- Service Impl:`@Service` + 必要时 `@Slf4j`。
- 禁止 `@Builder` 滥用(默认走 setter/全参构造)。

### 3.5 Java 特性(JDK 25)

- 允许:record(用于不可变 DTO)、switch 表达式、var(局部变量类型推断,仅限明显场景)、text block。
- 禁止:在 DO/VO 上用 record(与 MyBatis-Plus/Lombok 注解冲突)。

### 3.6 Import 分组

顺序:java.* → javax.*(仅 JDK 内置)→ jakarta.* → org.springframework.* → 第三方 → cn.aurora.*。组间空行。

### 3.7 异常处理

- **后端只 throw**:`throw exception(ErrorCode)` 或 `throw exception(ErrorCode, args)`。
- **全局兜底**:`GlobalExceptionHandler` 统一转 `CommonResult.error(...)`。
- **禁止**:Controller 内手动 `try-catch` 后返回 `CommonResult.error(...)`(除非有特殊降级需求)。
- **禁止**:`catch (Exception e)` 吞异常(必须 log 或 rethrow)。

---

## 4. 框架开发规范(framework 层)

### 4.1 新建 Starter 的目录结构

```
aurora-starter-<feature>/
└── src/main/java/cn/aurora/framework/<feature>/
    ├── config/                    @AutoConfiguration 配置类
    ├── properties/ 或 props/     @ConfigurationProperties
    ├── annotation/                自定义注解(@TenantIgnore 等)
    ├── aspect/                    AOP 切面
    ├── interceptor/               拦截器
    ├── handler/                   处理器(DefaultDBFieldHandler 等)
    ├── filter/                    Filter
    ├── constant/                  常量
    ├── exception/                 框架异常
    └── util/                      工具类
└── src/main/resources/
    └── META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 4.2 自动配置类写法

```java
@AutoConfiguration
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnBean(RedisConnectionFactory.class)
public class AuroraRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        // ...
    }
}
```

**要点**:
- 类标 `@AutoConfiguration`(非 `@Configuration`)
- 用 `@ConditionalOnMissingBean` 允许下游覆盖
- 在 imports 文件登记,**不写 spring.factories**
- 需要前置/后置:`@AutoConfiguration(before = Xxx.class)` / `@AutoConfiguration(after = Yyy.class)`

### 4.3 向后兼容要求(Breaking Change 清单)

改动以下任一项,**必须升 major 版本 + 全模块回归**:
- 改 public API 签名(BaseDO/BaseMapperX/CommonResult/*Api 的方法)
- 改配置 key(application.yaml 属性)
- 改 Bean 名称 / `@ConditionalOnXxx` 条件
- 升级 major 依赖(Spring Boot / MyBatis-Plus / Redisson)

---

## 5. 业务模块开发规范(module 层)

### 5.1 新增业务模块步骤

1. 参考标准模块:`aurora-module-system`(最完整样本)
2. 先读现有代码,复用 BaseDO/BaseMapperX/CommonResult/*Api
3. 用代码生成器(阶段 5 完成)一键生成骨架,再补业务逻辑

### 5.2 分层与包结构

详见 `architecture.md` §3.3 和 `design/aurora-scaffold-design.md` §4.1。核心:

```
module/<name>/
├── controller/{admin,app}/<feature>/
├── service/<feature>/
├── dal/{dataobject,mysql,redis}/<feature>/
├── api/<feature>/              ← 跨模块契约(业务间)
├── convert/<feature>/          ← MapStruct(复杂映射)
├── enums/
├── framework/                  ← 本模块 Spring 配置
├── job/ (可选)
└── mq/ (可选)
```

### 5.3 Controller 约定

```java
@Tag(name = "管理后台 - 用户")
@RestController
@RequestMapping("/system/user")
@Validated
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/create")
    @Operation(summary = "新增用户")
    @PreAuthorize("@ss.hasPermission('system:user:create')")
    public CommonResult<Long> createUser(@Valid @RequestBody UserSaveReqVO reqVO) {
        return success(userService.createUser(reqVO));
    }

    @GetMapping("/page")
    @Operation(summary = "用户分页")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public CommonResult<PageResult<UserRespVO>> getUserPage(@Valid UserPageReqVO reqVO) {
        return success(userService.getUserPage(reqVO));
    }
}
```

**要点**:
- 返回 `CommonResult<T>`,用 `success(T)` / `error(ErrorCode)`
- `@PreAuthorize("@ss.hasPermission('module:feature:action')")` 做权限
- admin 端路径 `/admin-api/<module>/<feature>`(自动绑定前缀),app 端 `/app-api/...`
- 复杂入参用 `@Valid @RequestBody *ReqVO`,分页用 `@Valid *PageReqVO`(extends PageParam)

### 5.4 Service 约定

```java
public interface UserService {
    Long createUser(UserSaveReqVO reqVO);
    PageResult<UserRespVO> getUserPage(UserPageReqVO reqVO);
}

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public Long createUser(UserSaveReqVO reqVO) {
        // 校验
        validateUserExists(...);
        // 转换 + 插入
        UserDO user = BeanUtils.toBean(reqVO, UserDO.class);
        userMapper.insert(user);
        return user.getId();
    }

    @Override
    public PageResult<UserRespVO> getUserPage(UserPageReqVO reqVO) {
        PageResult<UserDO> result = userMapper.selectPage(reqVO, new LambdaQueryWrapperX<UserDO>()
            .likeIfPresent(UserDO::getName, reqVO.getName()));
        return new PageResult<>(BeanUtils.toBean(result.getList(), UserRespVO.class), result.getTotal());
    }
}
```

**要点**:
- 简单转换用 `BeanUtils.toBean`(hutool 封装)
- 复杂映射用 MapStruct `Convert`
- **throw ServiceException** 表示业务错误,不手动返回 error
- Mapper 查询用 `LambdaQueryWrapperX` 的 `xxxIfPresent`(值为空不拼条件)

### 5.5 Mapper 约定

```java
public interface UserMapper extends BaseMapperX<UserDO> {

    default UserDO selectByMobile(String mobile) {
        return selectOne(UserDO::getMobile, mobile);
    }
}
```

**要点**:
- `extends BaseMapperX<T>`(自带 selectPage/selectOne/insertBatch 等)
- 单字段查询用 default 方法 + `selectOne(SFunction, value)`
- 复杂 SQL 写 XML(同包 `XxxMapper.xml`,PostgreSQL 语法)

### 5.6 跨模块调用约定(★ 单向,缺陷1)

- **业务间**:注入 `XxxApi`(`integration/api/`,Local 实现/未来 Feign),返回 `*RespDTO`(绝不返回 Entity)。**依赖单向**:A→B 允许,B 不得反向依赖 A。
- **反向通知**:"完成 A 后触发 B" 用**领域事件**(`event/XxxEvent` + `@EventListener`),A 不持有 B 的引用。**禁止**用 Service 互注处理此类联动。
- **框架反调业务**:starter 注入 `XxxCommonApi`(接口在 common/biz,实现在业务模块)。
- **禁止**:跨模块/跨域直接 `@Resource` 别的 Service(制造循环依赖);`@Lazy` 默认禁用,极少场景需用必须注释理由。

---

## 6. 数据库规范

### 6.1 表设计

- 表名:`<模块前缀>_<下划线命名>`(如 `system_user`、`mall_product_spu`)
- 必含字段:`id`(bigint,雪花)、`create_time`、`update_time`、`creator`、`updater`、`deleted`(Boolean,逻辑删除)
- 多租户表额外:`tenant_id`(bigint)
- 主键:`@TableId`(默认雪花 ASSIGN_ID)
- 逻辑删除:`@TableLogic deleted`(Boolean)

### 6.2 索引命名

- 普通索引:`idx_<字段>`
- 唯一索引:`uk_<字段>`
- 联合索引:`idx_<字段1>_<字段2>`

### 6.3 Migration(SQL)

- 文件命名:`V<YYYYMMDD>_<seq>__<desc>.sql`(Flyway 风格,PostgreSQL 语法)
- 一个文件做一件事
- 已执行 migration **不得修改**,新增 migration 修正
- `INSERT` 用 `ON CONFLICT DO NOTHING`(防重复执行)
- `CREATE INDEX CONCURRENTLY` 不允许在事务 migration 内

---

## 7. 缓存规范

- `@Cacheable` 用于普通配置/元数据缓存
- 细粒度控制用 RedisDAO 模式:`Service -> XxxRedisDAO -> StringRedisTemplate / RedissonClient`
- **禁止** Service 直接注入 `StringRedisTemplate` / `RedissonClient`
- key 定义在 `RedisKeyConstants`,**禁止硬编码**
- 用 `StringRedisTemplate`,非 `RedisTemplate`

---

## 8. 日志规范

- 类:`@Slf4j`(Lombok)
- 级别:ERROR(异常)/ WARN(可预期异常)/ INFO(关键业务节点)/ DEBUG(调试)
- 占位符:`log.info("用户登录,userId={},mobile={}", userId, mobile);`(**禁止字符串拼接**)
- 敏感信息(密码/token/手机号全量)**禁止**打日志

---

## 9. 构建与验证

### 9.1 编译验证

```bash
# 全量编译
mvn clean compile -Dmaven.test.skip=true

# 单模块编译(含依赖)
mvn clean compile -pl aurora-module-system -am

# 打 fat jar
mvn clean package -Dmaven.test.skip=true
```

### 9.2 启动

```bash
mvn -pl aurora-server -am spring-boot:run -Dspring-boot.run.profiles=local
```

### 9.3 测试

```bash
mvn test                              # 全量
mvn test -Dtest=UserServiceTest#testCreateUser   # 单测
```

单测基座:`aurora-starter-test` 提供 `BaseDbUnitTest`(H2 + redis mock)。

### 9.4 循环依赖检查(★ 零容忍,缺陷1)

> Aurora 与 yudao 根本不同:`allow-circular-references: false`(yudao 是 true),循环依赖 = 设计错误,**编译/启动即失败**,不兜底。

若出现循环依赖报错,**必须重构**,不允许 `@Lazy` 掩盖:
1. **领域事件破环**(首选):把"A 完成后通知 B"改为 A 发 `event/XxxEvent`,B 用 `@EventListener` 监听。A 不依赖 B。
2. **api 契约解耦**:把跨域的 Service 直调改为 `integration/api/XxxApi` 单向接口。
3. **合并同域 Service**:若两个 Service 本属同一聚合(如 yudao 的 SPU↔SKU),合并为一个 Service 或重新划分子域。
4. **仅在极少数有充分架构理由时**(如框架基础设施与业务的引导期依赖),经评审可用 `@Lazy`,**必须注释说明**。

`@Lazy` 默认视为坏味道,PR review 重点检查。

---

## 10. Git 提交规范

格式:`type(scope): 中文描述`

- type:`feat` / `fix` / `refactor` / `chore` / `docs` / `test` / `perf` / `style`
- scope:模块名(如 `aurora-starter-redis` / `aurora-module-system`)
- 描述:中文,动词开头

示例:
```
feat(aurora-starter-redis): 适配 Redisson 4.6 + Jackson3
fix(aurora-module-system): 用户分页 deptId 条件未生效
refactor(aurora-common): 抽取 OAuth2TokenCommonApi 到 biz 包
docs(design): 补充 Boot4 适配备忘
chore(aurora-dependencies): 升级 mybatis-plus-spring-boot4-starter 3.5.16
```

---

## 11. 框架组件速查表

| 组件 | 用途 | 示例 |
|---|---|---|
| `CommonResult.success(T)` | 成功响应 | `return success(userService.create(reqVO));` |
| `CommonResult.error(ErrorCode)` | 失败响应(框架兜底,业务用 throw) | — |
| `throw exception(ErrorCode)` | 抛业务异常 | `throw exception(USER_NOT_EXISTS);` |
| `BeanUtils.toBean(src, Target.class)` | DO↔VO 转换 | `BeanUtils.toBean(do, UserRespVO.class)` |
| `BaseMapperX.selectPage(pageParam, wrapper)` | 分页查询 | `userMapper.selectPage(reqVO, wrapper)` |
| `LambdaQueryWrapperX.likeIfPresent(...)` | 条件构造(值为空不拼) | `new LambdaQueryWrapperX<UserDO>().likeIfPresent(UserDO::getName, name)` |
| `@PreAuthorize("@ss.hasPermission('x:y:z')")` | 权限校验 | Controller 方法上 |
| `@TenantIgnore` | 跳过租户隔离 | Mapper 方法/Service 方法上 |
| `@DataPermission` | 数据权限 | Service 方法上 |
| `@Resource XxxApi` | 跨模块调用 | 注入业务 Api |
| `@Resource XxxCommonApi` | 框架反调业务 | starter 注入 |

---

## 12. 风格一致性

1. **模仿优先**:先读 `aurora-module-system` / `aurora-module-infra` 的同类代码,再动手。
2. **查找复用**:新需求先 grep 是否已有抽象可用(BaseMapperX/LambdaQueryWrapperX/BeanUtils)。
3. **最小改动**:不重构无关代码。
4. **交互语言**:全程中文。
