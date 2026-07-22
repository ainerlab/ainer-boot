# Aurora Boot

> 状态:**DRAFT v0.2** · 2026-07-22 · 仅有设计文档阶段,尚无可编译代码

**一套代码、两种架构**(单体优先、可演进微服务)的业务开发脚手架。

- 技术基线:**JDK 25 + Spring Boot 4.1.0**(Spring Framework 7 / Jakarta EE 11 / Servlet 6.1)
- 定位:把 xiaoqu-platform 已深度使用的 yudao 范式,**抽干净成独立品牌产物**,升级到 Boot4+JDK25,并吸收 bladex(模块演进)+ **dante-cloud(一套代码两种架构)** 工程机制
- 身份:全新独立 greenfield 仓库,与 xiaoqu 现有 yudao 范式 **100% 兼容**(保证 6000 文件零返工)

---

## 当前状态

| 阶段 | 状态 | 产出 |
|---|---|---|
| 0. 决策对齐 | ⏳ 待确认 4 项(见下) | — |
| **1. 设计文档落盘** | ✅ **完成(本次 v0.2)** | `docs/` 5 份 + 本 README |
| 2. 骨架可编译 | ⬜ 未开始 | BOM + common + web/mybatis/redis/security + **architecture(架构切换)** + server |
| 3. system+infra 样本 | ⬜ 未开始 | 完整 CRUD 链路 |
| 4. 重能力接入 | ⬜ 未开始 | tenant/data-permission/datasource/bpm |
| 5. 代码生成器 | ⬜ 未开始 | 后端 codegen |
| 6. 架构切换骨架 | ⬜ 未开始 | `@ConditionalOnArchitecture` + Strategy Local 实现 + BusBridge 短路 |
| 7. 迁移启动 | ⬜ 未开始 | xiaoqu 13+ 模块逐层迁入 |
| 8+. 微服务演进 | ⬜ 未开始 | 启用 distributed 架构 + Feign + 网关防伪造头 |

---

## 文档导航

| 文档 | 内容 |
|---|---|
| [docs/design/aurora-scaffold-design.md](docs/design/aurora-scaffold-design.md) | **架构设计主文档**:吸收清单(bladex 4 项 + yudao 8 项,逐项论证)、Boot4 适配、模块划分、跨模块契约双轨制、待决策项 |
| [docs/migration/aurora-migration-plan.md](docs/migration/aurora-migration-plan.md) | **迁移路线**:xiaoqu 13+ 现有模块逐层迁入(L0-L6),含真实依赖图与风险点 |
| [docs/boot4-migration-notes.md](docs/boot4-migration-notes.md) | **Boot4 适配备忘**:全量真实代码片段(Redisson 4.6 / Security 7 / BaseDO / yaml / 坐标陷阱) |
| [docs/architecture.md](docs/architecture.md) | **架构总览**:模块全景图、分层架构、核心抽象速查 |
| [docs/conventions.md](docs/conventions.md) | **工程约定**:命名/编码/框架开发/业务模块开发/数据库/缓存/日志/Git |

阅读建议:先 [design](docs/design/aurora-scaffold-design.md) 理解定位与吸收决策,再 [architecture](docs/architecture.md) 看模块全景。

---

## 待决策项(文档内已给推荐默认值,可调)

| 项 | 推荐默认 | 备选 | 影响 |
|---|---|---|---|
| **A. 认证方案** | Spring Security 7 + 自建 OAuth2 token 表 + Redis | Sa-Token / Spring Authorization Server | 影响 security starter 全部设计 |
| **B. 品牌名** | Aurora(与 xq-ui/xq-starter/aurora-admin 一脉) | 你指定 | groupId / 包名 / 仓库名(全文可一键替换) |
| **C. 仓库位置** | `~/01-code/xq/aurora-boot/`(已创建) | 别处 | — |
| **D. 范式兼容** | 与 xiaoqu yudao 范式 100% 兼容(BaseDO/BaseMapperX/CommonResult/api 契约) | 改范式 | 改则 6000 文件返工 |

---

## 技术栈速查

| 类别 | 选型 | 版本 |
|---|---|---|
| JDK | Java | **25** |
| 框架 | Spring Boot | **4.1.0** |
| ORM | MyBatis-Plus(`*-spring-boot4-starter`) | 3.5.16 |
| 多源 | dynamic-datasource(`*-spring-boot4-starter`) | 4.5.0 |
| 缓存 | Redisson | 4.6.1 |
| 安全 | Spring Security | 7(随 Boot4) |
| 文档 | springdoc | 3.0.3 |
| 翻译 | easy-trans | 3.1.5 |
| BPM | Flowable | 8.0.0 |
| DB | PostgreSQL | 18 |

> Boot4 坐标改名陷阱(`mybatis-plus-spring-boot4-starter` 等)详见 [boot4-migration-notes.md](docs/boot4-migration-notes.md) §2.2。

---

## 模块全景

```
aurora-boot/
├── pom.xml                      # parent: ${revision} + enforcer(JDK25/Maven≥3.9) + flatten + 注解处理器
├── aurora-dependencies/         # BOM:唯一版本源
├── aurora-framework/            # 框架层(15 starter)
│   ├── aurora-common            # pojo/exception/util/biz 契约(7 个 *CommonApi)
│   ├── aurora-starter-web       # 前缀绑定/全局异常/apilog/springdoc
│   ├── aurora-starter-security  # 认证(Security 7 + OAuth2 token)
│   ├── aurora-starter-mybatis   # BaseDO/BaseMapperX/WrapperX/easy-trans
│   ├── aurora-starter-datasource# 多数据源
│   ├── aurora-starter-redis     # Redisson 4.6 + Jackson 3
│   ├── aurora-starter-biz-*     # tenant/data-permission/bpm
│   └── aurora-starter-{job,mq,excel,protection,websocket,test}
├── aurora-module-system/        # 样本+地基:用户/角色/部门/字典/租户/RBAC
├── aurora-module-infra/         # 样本:文件/codegen HTTP 入口/dbdoc
└── aurora-server/               # 启动 shell
```

详见 [architecture.md](docs/architecture.md)。

---

## 设计来源

本脚手架基于四个项目的真实代码深度调研:
- **bladex** (`/Users/xq/01-code/xq/bladex/`):BladeX-Tool 45 模块、BladeX-Boot 单体、BladeX-Biz 业务样本、CLAUDE.md 两份。**吸收**:单体 modules/X + 演进抽 X-api、codegen 双模板、BOM+flatten、文档骨架
- **ruoyi-vue-pro** (`origin/master-jdk25` 分支):JDK25 + Boot4.1.0 的活样板(git show 读取,未 checkout)。**吸收**:业务范式全 8 项(= xiaoqu 现状)
- **dante-cloud** (`/Users/xq/01-code/xq/dante-cloud/`):JDK25 + Boot4.1 + Spring Cloud 2025.1.2,v4.1.0.4。**吸收**:**一套代码两种架构**(`@ConditionalOnArchitecture` 配置驱动)、Strategy 双实现、BusBridge 短路、`@EnableXxx` 开关、网关防伪造头。注意:其框架核心在另一个仓库 dante-engine(本地未 clone),Aurora 借鉴设计、自行实现
- **xiaoqu-platform** (`/Users/xq/01-code/xq/xiaoqu-platform/`):13+ 模块、~6000 文件、~57 万行,全平台深度跑 yudao 范式(迁移目标)

吸收论证详见 [design/aurora-scaffold-design.md](docs/design/aurora-scaffold-design.md) §2。
