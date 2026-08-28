# Ainer Boot 产品白皮书

> 面向：企业采购、平台架构师、技术决策者 · 技术事实状态：生效 · 商务条款：待决策
> 商业事实基线：`v1.4.1` · 本文对应工程版本：`v1.4.1` · 最近核对：2026-08-28

## 1. 一句话定位

**Ainer Boot**（AI-Native Extensible Runtime）是一个 AI 原生、但不局限于 AI 的通用企业
Java 脚手架与运行基线：JDK 25 + Spring Boot 4.1 + PostgreSQL 18 的模块化单体，自带可信的
身份、授权、工作区治理、AI 模型网关与企业基座，让产品团队从第一个业务提交开始，而不是从
重复搭建后台基础设施开始。

它不是“能跑的 demo 后台”，而是一套**可验证的工程合同**：Stable 能力有自动化测试、真实
PostgreSQL 门禁、远端消费者验证和签名供应链。但工程合同不自动等于客户生产资格；高可用、
容量、备份恢复、告警值班和产品业务验收仍要在目标部署中完成。

## 2. 企业自建后台的真实成本

多数团队低估的不是搭建成本，而是演示级起点在真实业务面前的重构成本：

| 常见起点的隐性代价 | Ainer Boot 的回答 |
|---|---|
| 登录/权限承载真实身份前要推倒重写 | 独立 OAuth 2.1/OIDC Authorization Server、Passkey 协议能力、真实 JWT 安全链 |
| “用户-部门-角色-数据范围”表达不了真实组织关系 | Workspace 治理 + RBAC/ReBAC/ABAC + 组织目录岗位集合绑定 |
| 错误码、响应信封、分页各自为政 | 真实 HTTP 状态、稳定错误码族、统一响应与请求追踪 |
| 测试用内存数据库掩盖方言差异 | PostgreSQL 18 唯一业务数据库基线，正式门禁要求真实库零跳过 |
| AI 只是再加一个 SDK，费用与数据边界失控 | 模型白名单、限流、预算、Token/费用审计和错误脱敏 |
| 升级与回滚没有证据 | 版本化制品、签名供应链、相邻版本兼容规则和参考消费者门禁 |

## 3. 能力域

### A. 现代运行基座

JDK 25、Spring Boot 4.1、PostgreSQL 18；模块化单体默认交付，满足明确条件后按需服务化。
`ainer.runtime.mode` 只选择适配器，不伪装成部署拓扑切换。

### B. 安全身份底座

独立 OAuth 2.1/OIDC Authorization Server（PKCE、Client Credentials、在线校验与撤销）；
人员/服务令牌档案、选择性在线校验失败关闭、step-up 和默认关闭的 Passkey 协议能力。

### C. Workspace 与治理

Workspace 资源边界、ACTIVE membership、OWNER 专用事务转移和双人恢复；授权审计热表、
同库归档与 SIEM 稳定游标。生产职责分离、外部不可变副本和法律保留仍由目标产品验收。

### D. 通用授权

Permission/Role/Binding、结构化 Scope、RBAC+ReBAC+ABAC 决策、管理 API、防提权矩阵、决策
审计和组织目录集合绑定。端点注解是粗粒度门禁，高价值写与资源 ownership 仍由应用服务显式
授权；完整 obligation executor 与方法级 AOP 不是当前承诺。

### E. AI 运行时

OpenAI-compatible 非流式/SSE 网关、模型白名单、限流、日预算、Token/费用与策略审计；供应商
错误正文和密钥不进入客户端或日志。RAG、Evaluation、完整 Agent Runtime 与集群级限流仍未交付。

### F. 企业基座与知识

文件元数据与存储约束、树形字典、类型安全配置、多渠道通知队列（日志默认，EMAIL/WEBHOOK
为可选脚手架实现，SMS/Push 经 SPI 接入）、缓存/分布式锁、任务调度，以及 Incubating 的组织、
Knowledge Foundation 与 Agent 代行能力。

### G. 开发者工具链

Project Initializer 支持 Manifest v1 兼容生成、Manifest v2 `simple-service + workspace` 安全
纵向切片，以及 `v1.4.0` 新增的已有单模块 Maven 项目只读 `plan-add` 与幂等 `add`。生成项目使用
自己的 Maven 3.9.16 Wrapper；多模块、Gradle、自动 migration 编号和任意 POM 改写不在首版范围。

## 4. 为什么可以信任：分层证据模型

| 承诺 | 当前证据 | 不能外推的结论 |
|---|---|---|
| 源码基线可构建、数据路径可执行 | JDK 25/Maven 4 Reactor、真实 PostgreSQL 集成测试、真实签名 JWT HTTP 测试 | 生产容量、HA、灾备 |
| 公共制品可消费 | `v1.4.1` 远端 Maven 3.9.16/Maven 4 空仓消费与 Initializer 五通道通过 | 某个客户产品已升级成功 |
| 制品来源可复核 | annotated tag/source、逐制品签名读回、SBOM、checksum、provenance、immutable Release | GitHub Attestation 或 SLSA 等级认证 |
| 历史升级规则已被产品验证 | 参考消费者已留档至 `1.2.0` 的升级/回滚链 | `1.3.0 → 1.4.0` 已完成真实产品验收 |

当前产品消费者的 `1.3.0 → 1.4.0`、migration replay 与一级回滚仍是明确退出条件。发布流水线
内的 Golden Consumer 和生成项目不能替代产品仓库中的业务、数据和部署验收。

## 5. 版本与支持

- `v1.4.1` 是当前合格稳定版本；`v1.1.0` withdrawn；`v1.0.0` 是升级起点；`1.0.x` 是首个
  LTS 工程补丁线。
- `v1.3.0` 交付 Manifest v2 安全纵向切片；`v1.4.0` 交付已有项目 `plan-add` / `add` 与模块
  授权策略组合 SPI。
- 版本政策支持相邻合格 minor 升级与一级应用回滚；生产采用者必须在自己的数据副本、业务切片
  和部署环境重放，schema 不自动回退。
- 当前没有合同支持渠道、响应 SLA、赔付、托管值班或专属 LTS；这些仍是商业决策。

## 6. 交付形态

| 形态 | 内容 | 适合 |
|---|---|---|
| 制品消费 | BOM + Starter + 模块制品，固定版本从 GitHub Packages 拉取 | 已有项目手工选择能力 |
| Initializer 新建 | 从声明式清单生成独立项目，带 Wrapper、测试与 migration 基线 | 新产品起步 |
| Initializer 增量接入 | `plan-add` 只读规划，`add` 幂等新增并有限合并顶层 POM | 已有单模块 Maven/Spring Boot 项目 |
| 参考装配 | 两个可执行应用与模块化单体装配示例 | 架构评估与配置参照 |

不含：正式前端管理产品、消息中间件集成、多数据源/分库分表、托管服务和生产运维托管。

## 7. 当前采用边界

| 问题 | 当前结论 |
|---|---|
| 能否用于工程研发和技术试点 | 可以，使用固定 `v1.4.1` 制品并执行自身验收 |
| 能否用于受控生产 | 有条件；必须关闭真实消费者升级、双节点/容量、监控告警、备份/PITR、密钥轮换与安全评审 |
| 是否是开箱即用的企业生产平台 | 不是 |
| 是否已有可采购的 Pro / Enterprise SKU | 没有，分层、价格、entitlement、交付系统与 SLA 均未定稿 |

## 8. 许可与下一步

- 源码许可：MIT（见仓库 `LICENSE` / `NOTICE` 与 ADR-0051），不授予 Ainer 商标权。
- 商业分层提案：[`edition-tiers.md`](edition-tiers.md)。
- 技术评估与接入：[`customer-delivery-guide.md`](customer-delivery-guide.md)。
- 当前能力、验证与缺口：[`project-status.md`](../project-status.md)。
