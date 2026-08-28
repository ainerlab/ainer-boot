# Ainer Boot 商业文档套件

> 文档类型：对外商业文档 · 技术事实状态：生效 · 商务方案状态：待决策 · 维护人：产品
> 商业事实基线：`v1.4.0` · 最近核对：2026-08-28
> 对应工程合同快照：[`ainer-boot-1.0-product.md`](../ainer-boot-1.0-product.md)；
> 能力现状唯一权威：[`project-status.md`](../project-status.md)

本目录承载面向**潜在买家、试点客户与技术评估者**的商业材料。技术事实必须跟随合格发布同步，
商务分层、价格和 SLA 则只有在形成可兑现决策后才能转为正式承诺。两者不能再混在一个“草案”
标签下，也不能因商务决策尚未完成而让已发布能力长期停留在旧版本。

| 工程文档回答 | 本目录回答 |
|---|---|
| 这个系统是什么、怎么验证 | 为什么值得采用、评估会得到什么 |
| 每个能力的证据在哪 | 证据如何支撑采购与试点决策 |
| 怎么开发与运维 | 怎么接入、验收边界和责任分界是什么 |

## 阅读顺序

1. [`product-whitepaper.md`](product-whitepaper.md) —— 产品白皮书：定位、能力域、证据链、交付形态与边界
2. [`edition-tiers.md`](edition-tiers.md) —— Community / Pro / Enterprise 商业分层提案及当前可交付状态
3. [`customer-delivery-guide.md`](customer-delivery-guide.md) —— 评估、试点、接入、升级与运维交接指南
4. [`sales-one-pager.md`](sales-one-pager.md) —— 售前技术评估一页纸（不是报价单或 SLA）
5. [`gap-analysis-and-next-steps.md`](gap-analysis-and-next-steps.md) —— 可售性与生产资格差距（内部排期依据）

## 当前对外口径

- 当前合格稳定版本是 `v1.4.0`；`v1.1.0` withdrawn / non-qualifying，禁止消费。
- Ainer Boot 已是可远端消费、带签名供应链与升级规则的企业 Java 工程脚手架。
- `v1.4.0` 的远端 Maven 3/4、Initializer 五通道与不可变 Release 门禁已通过；真实产品消费者的
  `1.3.0 → 1.4.0`、migration replay 与一级回滚仍待完成。
- 当前只可表述为**商业级工程基线**和**受控生产候选**，不能表述为开箱即用的企业生产平台、
  已验证 HA、托管服务或已有合同 SLA。
- MIT 社区制品已经存在；Pro / Enterprise 付费 SKU、entitlement、价格、支持与交付系统尚未定稿。

## 目标买家画像

双轨叙事，以**企业采购**为主轴：

- **企业采购 / 平台架构师**（主）：关注安全、审计、供应链、升级治理和生产责任边界；
- **业务团队 / 交付型公司**（辅）：关注从第一个业务提交开始的 TTFR、Initializer 与接入成本。

## 未决商业决策

| 决策 | 已确定事实 | 尚未形成的承诺 |
|---|---|---|
| 对外许可 | 源码 MIT（ADR-0051），Ainer 商标权不随 MIT 授予 | 商标检索/注册与官方背书规则 |
| 版本分层 | Community 工程实体已存在 | Pro / Enterprise SKU、entitlement 与可售边界 |
| 定价与商务模式 | 无正式价格 | 订阅/买断/项目服务、税费和付款条款 |
| 支持与 SLA | 社区文档和版本政策已公开 | 响应时间、值班、赔付、专属 LTS 与升级协助 |
| 品牌资产 | 活动品牌为 Ainer | 正式官网、域名取得和视觉资产由 Studio/品牌工作流决定 |

## 同步与写作纪律

- 合格发布准备必须让本目录每一份 Markdown 的“商业事实基线”与目标版本一致；
  `scripts/check-commercial-docs.sh` 在 CI 与 Release 中失败关闭。
- 已发布能力写版本与证据；开发分支或路线图能力必须标注“未交付/提案”。
- 不宣称生产就绪、HA、灾备、托管服务、外部客户案例或合同 SLA，除非对应验收已经完成。
- 对外稿不点名第三方竞品，采用范式类别对比；内部矩阵见
  [`design/ainer-scaffold-design.md`](../design/ainer-scaffold-design.md)。
- 动态测试数、制品数与最新缺口只引用 `project-status.md`，不在商业材料复制第二份易漂移数字。
