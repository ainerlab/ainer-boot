# AGENTS.md

> Aurora Boot 的 AI 协作工作笔记。新会话先读本文件,再按需查阅 `docs/`。

## 工程定位

Aurora 是**一套代码、两种架构**(单体优先、可演进微服务)的业务开发脚手架,**JDK 25 + Spring Boot 4.1.0**。

**本质**:**全新设计的范式**——不预设继承 yudao,从 bladex/yudao/dante-cloud/Snowy 四家择优 + **主动改掉 yudao 的 7 个范式缺陷**(循环依赖/数据权限粗/token 自造/命名分层乱/模块边界不清/错误码/异常处理)。详见 `docs/design/paradigm-redesign.md`。xiaoqu 迁移 = 按新范式重写,非改包名搬家。

## 当前阶段

**仅设计文档阶段(2026-07-22)**,尚无可编译代码。下一步是阶段 2:骨架可编译。

## 必读文档(按顺序)

1. `README.md` — 总览与当前状态
2. **`docs/design/paradigm-redesign.md`** — **★ 范式重新设计(核心)**:yudao 7 缺陷 + 新范式改进。**写任何代码前必读**
3. `docs/design/aurora-scaffold-design.md` — 架构设计主文档(吸收决策、Boot4 适配、模块划分、待决策项)
4. `docs/architecture.md` — 模块全景图
5. `docs/conventions.md` — 工程约定(命名/编码/框架开发/业务模块开发)
6. `docs/boot4-migration-notes.md` — Boot4 适配备忘(全量代码片段,写代码前必读)
7. `docs/migration/aurora-migration-plan.md` — xiaoqu 模块重写迁移路线

## 关键规则(MUST)

### 范式立场(最高优先级)

- **不预设继承 yudao**。yudao 的好东西可借鉴,7 个缺陷必须改掉(详见 `docs/design/paradigm-redesign.md`)。
- **7 缺陷清单(写代码时主动避免)**:
  1. 循环依赖:模块内 **禁止 Service 互注**,走单向 api 接口 + 领域事件;`allow-circular-references: false`,`@Lazy` 禁用(除非充分理由+注释)
  2. 数据权限:支持表维度 + API 维度双模(缺陷 2)
  3. 认证:用 Spring Authorization Server,非自造 token(缺陷 3)
  4. 命名分层:VO 收敛到 ≤3 种(Create/Update/Save Req + Resp + PageReq),实体统一一份 Entity,包路径 ≤4 层(缺陷 4)
  5. 模块边界:业务不落 kernel,kernel 只放纯技术(auth/oauth2/permission/dict/tenant/user)(缺陷 5)
  6. 错误码:枚举 + 注册表启动校验,非散落常量接口(缺陷 6)
  7. 异常:HTTP status 真语义 + body CommonResult,非全 200(缺陷 7)

### Boot4 + JDK25 纪律

- **jakarta.\***,禁止 `javax.*`(除 JDK 内置:`javax.annotation.processing.*` / `javax.lang.model.*` / `javax.tools.*`)。
- 装配走 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,**不写 spring.factories 的 EnableAutoConfiguration**。
- MyBatis-Plus / dynamic-datasource 用 **`*-spring-boot4-starter`** 坐标(非 `-spring-boot-starter` / `-spring-boot3-starter`)。
- Redisson **4.6.1** + `@AutoConfiguration(before = RedissonAutoConfigurationV4.class)`;删旧 `JavaTimeModule` 样板。
- Security **7** lambda DSL:`authorizeHttpRequests` / `requestMatchers` / `dispatcherTypeMatchers(DispatcherType.ASYNC)`。
- yaml:`spring.data.redis`(非 `spring.redis`)。
- 适配检查清单见 `docs/boot4-migration-notes.md` §10。

### 跨模块契约

- 业务间调用走 `module/*/api/XxxApi`(本地 `@Service` 注入,返回 `*RespDTO`)。
- 框架反调业务走 `common/biz/*CommonApi`(依赖倒置,接口在 common,实现在业务模块)。
- **禁止**跨模块直接 `@Resource` 别的 Service 实现类(制造耦合/循环);优先用 api 契约解耦,`@Lazy` 是兜底。

### 版本管理

- `aurora-dependencies` 是唯一版本源,子模块 pom **禁止写版本号**。
- `aurora-common` 是最底层,**不得反向依赖任何业务模块**。

### 架构切换(dante-cloud 机制)

- 跨模块调用**一律走 `XxxApi` 接口注入**,**禁止直连别的模块 Service**(单体本地 Impl,未来切 Feign 远程,业务代码零改动)。
- 单体实现打 `@ConditionalOnArchitecture(MONOLITH)`;Feign 实现留 `DISTRIBUTED` 位(阶段 8+ 补)。
- 切换开关:`aurora.architecture: monolith`(默认)| `distributed`。
- 机制详见 `docs/design/aurora-scaffold-design.md` §6。

### 认证与 Sa-Token 互斥(Snowy 纪律)

- Aurora 用 **Spring Security**(yudao 路线)。**禁止引入 Sa-Token**(与 Spring Security 互斥)。
- 吸收 Snowy 数据权限(S1)时,**剥离 `StpUtil`/`StpLoginUserUtil`**,`DataScope` 改挂 Spring Security 的 `LoginUser` 上下文。
- Snowy 的 SSO 协议层(14 厂商)若要复用,需改写为 Spring Security 的 OAuth2/justauth 适配,**不直接搬 Sa-Token 版**。

## 吸收决策(已论证,勿推翻)

| 来源 | 吸收 |
|---|---|
| bladex | ① 单体 modules/X + 演进抽 X-api ② codegen 双模板(api/api-fast)③ BOM+flatten+revision ④ 文档骨架 |
| bladex(舍弃) | blade-core-auto 注解处理器、BladeApplication SPI、R<T>+BladeController、Wrapper |
| yudao(=xiaoqu 现状) | ① 双轨跨模块契约 ② 空壳 server ③ BaseDO ④ BaseMapperX ⑤ LambdaQueryWrapperX ⑥ CommonResult+ErrorCode+ServiceException ⑦ MapStruct Convert ⑧ 内建 codegen |
| **dante-cloud + dante-engine**(技术栈与 Aurora 完全一致:JDK25+Boot4.1,已本地 clone) | ① **一套代码两种架构**(`@ConditionalOnArchitecture` 枚举即条件三层委托)② Local/Remote Listener 成对(单体不连 Kafka)③ `@EnableXxx` 模块开关 ④ framework core→spring 分层 ⑤ 网关防伪造内部调用头。**注意:Strategy 双实现需自研**(dante v4.1.0.4 已删除) |
| dante-cloud(舍弃) | JPA+Hibernate 二级缓存(范式冲突,Aurora 用 MyBatis-Plus)、opaque token、passkey/国密(业务特定)、Nacos 强依赖(与单体优先冲突) |
| **Snowy**(v3.6.5,Boot 3.5/JDK17,真实代码核实) | ① **API 维度数据权限 + 预计算表 + scopeKey 去重**(四家独有,剥离 Sa-Token)② **SM4 字段级透明加密 + TypeHandler**(四家独有)③ easy-trans `@Trans` 字段翻译 ④ CommonResult+traceId ⑤ 防重提交注解 ⑥ 代码生成 4 业务形态 |
| Snowy(舍弃) | ⚠️ **Sa-Token 全家桶**(与 Spring Security 互斥)、StpUtil 静态调用(改 SecurityContext)、"插件式动态加载"幻觉(实为物理模块,bladex 已覆盖)、JSONObject 弱类型 API(yudao 强类型不降级)、hutool CronUtil 定时(无集群协调) |

论证详见 `docs/design/aurora-scaffold-design.md` §2。

**dante-engine 实现边界提醒**(已本地 clone `/Users/xq/01-code/xq/dante-engine`,54 模块):
- ✅ **可直接移植代码**:条件注解(`@ConditionalOnArchitecture` 体系,`dante-framework/dante-spring`)、Local/Remote Listener 成对、`@EnableXxx` 开关、core→spring 分层
- ⚠️ **需自研**:Strategy 双实现(dante v4.1.0.4 **已删除** Local/Feign 双实现,改成 UAA 直连库;Aurora 要自己设计)、ConfigurerManager(绑定 SAS,首期不上)
- ⚠️ **关键纠正**:dante **没有** BusBridge 空实现类(全仓零命中),单体不连 Kafka 是靠 Local/Remote Listener 成对 + `@ConditionalOnClass(StreamBusBridge)` 双保险实现
- 移植复杂度评估见 `docs/design/aurora-scaffold-design.md` §6.9

## 待决策项(默认值见 design §9)

- A. 认证方案(推荐 **Spring Authorization Server + ConfigurerManager**,改掉 yudao 自造 token,缺陷 3)
- B. 品牌名(推荐 Aurora,全文可一键替换)
- C. 仓库位置(已 `~/01-code/xq/aurora-boot/`)
- D. 范式立场(已确定:**不预设继承 yudao,主动改 7 缺陷**,见 `paradigm-redesign.md`)

## 构建(阶段 2 后可用)

```bash
mvn clean compile -Dmaven.test.skip=true              # 全量编译
mvn clean compile -pl aurora-module-system -am        # 单模块
mvn -pl aurora-server -am spring-boot:run -Dspring-boot.run.profiles=local  # 启动
mvn test                                               # 测试
```

> 当前(阶段 1)无 pom 和 Java 代码,上述命令阶段 2 后生效。

## Git 提交

格式:`type(scope): 中文描述`,scope 用模块名。例:
```
feat(aurora-starter-redis): 适配 Redisson 4.6 + Jackson3
docs(design): 补充 Boot4 适配备忘
```

## 交互语言

全程中文。
