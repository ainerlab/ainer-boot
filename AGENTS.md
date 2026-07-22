# AGENTS.md

> Aurora Boot 的 AI 协作工作笔记。新会话先读本文件,再按需查阅 `docs/`。

## 工程定位

Aurora 是**一套代码、两种架构**(单体优先、可演进微服务)的业务开发脚手架,**JDK 25 + Spring Boot 4.1.0**。

**本质**:把 xiaoqu-platform 已深度使用的 yudao 范式,抽干净成独立品牌产物,升级到 Boot4+JDK25,并吸收 bladex(模块演进)+ dante-cloud(一套代码两种架构)工程机制。**与 xiaoqu 现有 yudao 范式 100% 兼容**(保证 6000 文件零返工)是硬约束。

## 当前阶段

**仅设计文档阶段(2026-07-22)**,尚无可编译代码。下一步是阶段 2:骨架可编译。

## 必读文档(按顺序)

1. `README.md` — 总览与当前状态
2. `docs/design/aurora-scaffold-design.md` — **架构设计主文档**(吸收决策、Boot4 适配、模块划分、跨模块契约、待决策项)
3. `docs/architecture.md` — 模块全景图
4. `docs/conventions.md` — 工程约定(命名/编码/框架开发/业务模块开发)
5. `docs/boot4-migration-notes.md` — Boot4 适配备忘(全量代码片段,写代码前必读)
6. `docs/migration/aurora-migration-plan.md` — xiaoqu 模块迁移路线(阶段 6 用)

## 关键规则(MUST)

### 范式兼容(最高优先级)

- **不得擅自改变** `BaseDO` / `BaseMapperX` / `LambdaQueryWrapperX` / `CommonResult` / `*Api` 的 API 形状。
- 改这些 = 6000 文件返工。增量增强可以(如加 `BaseConvert` 模板方法),改既有形状不行。

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

## 吸收决策(已论证,勿推翻)

| 来源 | 吸收 |
|---|---|
| bladex | ① 单体 modules/X + 演进抽 X-api ② codegen 双模板(api/api-fast)③ BOM+flatten+revision ④ 文档骨架 |
| bladex(舍弃) | blade-core-auto 注解处理器、BladeApplication SPI、R<T>+BladeController、Wrapper |
| yudao(=xiaoqu 现状) | ① 双轨跨模块契约 ② 空壳 server ③ BaseDO ④ BaseMapperX ⑤ LambdaQueryWrapperX ⑥ CommonResult+ErrorCode+ServiceException ⑦ MapStruct Convert ⑧ 内建 codegen |
| **dante-cloud**(技术栈与 Aurora 完全一致:JDK25+Boot4.1) | ① **一套代码两种架构**(`@ConditionalOnArchitecture` 配置驱动)② Strategy 接口双实现(Local/Feign)③ BusBridge 空实现短路 ④ `@EnableXxx` 模块开关 ⑤ 网关防伪造内部调用头 |
| dante-cloud(舍弃) | JPA+Hibernate 二级缓存(范式冲突,Aurora 用 MyBatis-Plus)、opaque token、passkey/国密(业务特定)、Nacos 强依赖(与单体优先冲突) |

论证详见 `docs/design/aurora-scaffold-design.md` §2。

**dante-cloud 实现边界提醒**:其 `@ConditionalOnArchitecture`、Strategy 接口、ConfigurerManager 的底层实现在另一个仓库 **dante-engine**(本地未 clone)。Aurora **借鉴其设计思路,用 Boot4 原生 `@Conditional` + Environment 自行实现**,不直接移植 dante 代码。

## 待决策项(默认值见 design §8)

- A. 认证方案(推荐 Spring Security 7 + 自建 OAuth2 token + Redis)
- B. 品牌名(推荐 Aurora,全文可一键替换)
- C. 仓库位置(已 `~/01-code/xq/aurora-boot/`)
- D. 范式兼容(推荐 100% 兼容)

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
