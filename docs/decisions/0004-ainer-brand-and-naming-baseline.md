# ADR-0004：Ainer 品牌与技术命名基线

- 状态：Accepted
- 日期：2026-07-22
- 替代：Aurora 作为正式品牌的既有假设
- 技术迁移：Completed（2026-07-22）

## 背景

项目计划同时提供开源脚手架、企业版、AI 平台与后续行业产品，因此名称不仅是代码仓库代号，也会进入域名、文档、Maven 坐标、商业授权、客户合同和产品矩阵。

`Aurora` 已被大量公司和产品使用，主要域名也不具备可控性，无法满足长期品牌资产建设要求。本轮命名以以下约束为准：

- 4～5 个字母或同等记忆长度，容易拼写和口头传播；
- 名称本身能承载真实的 AI 原生技术定位，不依赖事后附会；
- 不与 Spring Boot 或 Java 强绑定，以容纳未来的云服务、开发工具和行业产品；
- 能形成社区版、企业版、AI、Studio、Cloud 等一致的产品命名；
- 域名与同领域品牌冲突处于可进一步清理的范围。

## 决策

### 1. 正式品牌

正式品牌采用 **Ainer**，标准写法为首字母大写的 `Ainer`，机器标识统一使用小写 `ainer`。

- 英文读法：`AI-ner`；
- 中文读音：`艾纳`；
- 品牌释义：**AI-Native Extensible Runtime**；
- 开源脚手架正式产品名：**Ainer Boot**。

`AI-Native Extensible Runtime` 是 Ainer 的长期技术定位：AI 是平台的一等运行能力，可扩展性通过稳定契约、Starter、Provider、工具和独立发行物实现，Runtime 表示它不只是代码模板，而是可运行、可治理和可商业交付的平台底座。

“艾纳”当前只作为中文读音和传播用简称，不代表已经完成中文商标注册或取得对应权利。

### 2. 产品命名体系

产品线采用统一的 `Ainer + 能力名` 结构：

```text
Ainer Boot          开源 Spring Boot 4 脚手架与运行基线
Ainer Community     社区发行版
Ainer Enterprise    企业治理、LTS、升级与商业支持
Ainer AI            Model Gateway、Agent、RAG、Evaluation 与 AI 治理
Ainer Studio        开发、生成、配置与扩展工具
Ainer Cloud         托管运行与云端商业服务
```

行业产品可以拥有独立商品名，但其技术归属使用 `Powered by Ainer`，不再复用 `Aurora` 创建新产品名。

### 3. 域名与品牌资产状态

首选域名为 `ainer.dev`。2026-07-22 通过 Google Registry RDAP 进行的时点查询未返回已注册域名记录，但这不等同于预留、购买或权利取得。

在域名实际注册、商标完成专业检索前：

- 文档只能把 `ainer.dev` 表述为“首选域名”或“待取得资产”；
- 不得对外宣称已经拥有相关域名或商标；
- 不以初步网络搜索代替中国及目标市场的正式商标检索。

初步冲突检索发现 `Ainer` 在日本零售和瑞士汽车/建筑等非软件行业存在同名主体，尚未发现主流 AI 平台或企业软件产品使用完全相同的品牌。该结果只用于命名筛选，不构成法律意见或商标可注册结论。

### 4. Aurora 的后续地位

`Aurora` 从本决策生效起降级为历史内部代号：

- 新的对外文档、产品、网站和商业材料不得继续把 Aurora 作为正式品牌；
- 历史 ADR 可以保留 Aurora，以维持决策发生时的事实语境；
- 现有源码中的 `aurora-*`、`cn.aurora.*`、`aurora.*`、`AURORA_*`、`X-Aurora-*`、`AURORA.*` 和数据库对象目前只是过渡技术标识；
- 在专门的技术重命名变更完成前，不在普通功能提交中零散替换这些标识。

## 目标技术标识

技术重命名完成后的默认目标如下：

| 对象 | 目标 |
|---|---|
| 仓库 | `ainer-boot` |
| Maven artifact | `ainer-*` |
| Maven groupId | `dev.ainer` |
| Java package | `dev.ainer.*` |
| 配置前缀 | `ainer.*` |
| 环境变量 | `AINER_*` |
| HTTP 上下文头 | `X-Ainer-*` |
| 稳定错误码 | `AINER.<MODULE>.<ERROR>` |
| 自有数据库对象前缀 | `ainer_*` |

如果域名最终未取得或正式商标检索否定当前方案，`groupId`、包名和对外发布坐标必须在首个公开版本前重新确认。

## 技术迁移纪律

品牌记录和技术重命名分成两个可审查变更。本 ADR 只确立名称，不实施半完成的全仓替换。

技术重命名必须在专门变更中一次完成：

1. 重命名仓库目录、Maven 模块、artifact 与 Reactor 引用；
2. 重命名 Java package、自动配置清单和架构测试规则；
3. 重命名配置、环境变量、HTTP header、错误命名空间和平台信息；
4. 处理数据库 migration、表名、索引、约束、示例和测试数据；
5. 更新脚本、文档路径、命令示例和依赖许可证台账；
6. 全仓搜索旧标识，只允许历史 ADR 和明确的迁移说明继续出现 `Aurora`；
7. 运行完整 `mvn test`，验证可编译、可启动、migration 和 API 契约。

在没有外部消费者和公开发行版时，优先直接完成干净重命名，不提前维护双配置、双错误码或双包名兼容层。若重命名前已经出现真实消费者，则必须另行记录兼容窗口和弃用策略。

已经发布并执行的数据库 migration 仍不得原地修改；需要时通过新的 migration 完成对象迁移。尚未发布的 greenfield migration 可以随首个公开版本前的原子重命名同步调整。

## 实施记录

2026-07-22 已完成品牌对应的全仓技术重命名：

- 仓库根目录迁移为 `ainer-boot`；
- Maven 坐标迁移为 `dev.ainer:ainer-*`，全部 Reactor 模块使用 `ainer-*`；
- Java package 迁移为 `dev.ainer.*`，Ainer 专属自动配置、属性类和启动类同步改名；
- 配置前缀、环境变量、HTTP Header、稳定错误码和平台信息统一为 Ainer 命名；
- greenfield 数据库表、索引、约束、Mapper 和测试统一使用 `ainer_*`；
- 当前设计与迁移文档路径统一为 `ainer-*`，历史 ADR 保留决策发生时的旧名称；
- 活动源码与当前文档的旧标识清查结果为零；
- `mvn clean test` 从空构建产物重新编译 10 个 Reactor 模块并成功，46 项测试中 0 失败、0 错误；本机无 Docker，16 项 Testcontainers PostgreSQL 测试按设计跳过。

技术命名迁移已经完成。域名取得和正式商标检索仍是品牌资产发布前置条件，不因代码改名而自动完成。

## 后果

正面：

- 品牌名称与 AI 原生、可扩展 Runtime 的技术定位直接对应；
- 不把长期商业品牌锁定在 Java 或 Spring Boot 单一实现上；
- 开源、企业版、AI、Studio 和 Cloud 可以形成一致产品家族；
- 在首个公开版本前仍有机会干净收敛全部技术标识。

代价与风险：

- 当前仓库会在品牌已确定、技术标识未迁移之间存在一个短暂过渡期；
- 全仓重命名会影响包名、配置、错误码、Header、migration、测试和文档，必须作为高一致性变更执行；
- 域名可用性会变化，名称也仍需正式商标检索和法律审查；
- 非软件行业存在同名主体，不能使用“全球唯一”之类绝对化宣传。

## 完成条件

本决策在以下条件全部满足后结束品牌资产过渡状态：

- [ ] 首选域名或经批准的替代域名已经实际取得；
- [ ] 中国及主要目标市场的商标冲突完成专业检索并形成记录；
- [x] 技术重命名通过完整测试，仓库不再混用两套活动标识；
- [x] README、发布坐标和产品矩阵统一使用 Ainer；
- [ ] 正式官网与商业发布材料统一使用 Ainer。

## 调研来源

- [Google Registry：`.dev` 域名说明](https://www.registry.google/tlds/dev/)
- [Google Registry：RDAP 查询入口](https://www.registry.google/rdap-lookup/)
- [日本株式会社 Ainer 公司页](https://jp.linkedin.com/company/%E6%A0%AA%E5%BC%8F%E4%BC%9A%E7%A4%BEainer)
- [瑞士 AINER GmbH 企业记录](https://www.moneyhouse.ch/en/company/ainer-gmbh-11298702251)

这些来源只证明本决策时点的初步域名和名称冲突调查，不替代注册机构的实时结果、商标数据库检索或专业法律意见。
