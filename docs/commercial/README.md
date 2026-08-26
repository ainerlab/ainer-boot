# Ainer Boot 商业文档套件

> 文档类型：对外商业文档 · 状态：草案 v0.1 · 维护人：产品
> 对应工程合同快照：[`ainer-boot-1.0-product.md`](../ainer-boot-1.0-product.md)；
> 能力现状唯一权威：[`project-status.md`](../project-status.md)

本目录承载面向**潜在买家与客户**的商业文档。它与工程文档的分工：

| 工程文档回答 | 本目录回答 |
|---|---|
| 这个系统是什么、怎么验证 | 为什么值得买、买了得到什么 |
| 每个能力的证据在哪 | 证据如何支撑采购决策 |
| 怎么开发与运维 | 怎么接入、交付物长什么样 |

## 阅读顺序

1. [`product-whitepaper.md`](product-whitepaper.md) —— 产品白皮书：定位、问题论证、六大能力域价值、质量与信任证据链、交付形态
2. [`edition-tiers.md`](edition-tiers.md) —— 版本分层框架草案（Community / Pro / Enterprise 能力边界）
3. [`customer-delivery-guide.md`](customer-delivery-guide.md) —— 客户交付文档套件（接入、验收、运维交接的客户视角重组）
4. [`sales-one-pager.md`](sales-one-pager.md) —— 销售物料一页纸
5. [`gap-analysis-and-next-steps.md`](gap-analysis-and-next-steps.md) —— 可售性差距分析与发展路线建议（内部讨论用）

## 目标买家画像

双轨叙事，以**企业采购**为主轴：

- **企业采购 / 平台架构师**（主）：有安全、审计、供应链与升级治理要求；白皮书以信任模型
  与证据链为主叙事
- **业务团队 / 交付型公司**（辅）：要的是「第一个业务提交就开始」；以 TTFR、Initializer 与
  交付物清单为辅叙事

## 待定决策（写作前置依赖，未决策前相关内容留占位）

| 决策 | 影响 | 当前状态 |
|---|---|---|
| 对外许可模式（LICENSE/NOTICE） | 白皮书 §许可、一页纸法务行 | **MIT**（ADR-0051）；商标仍按 ADR-0004 |
| Community / Pro / Enterprise 边界定稿 | 分层框架从「草案」转「可承诺」 | 本文给出基于代码事实的草案 |
| 定价与商务模式（订阅/买断/服务） | 一页纸价格区、分层框架商务列 | 完全留空 |
| 品牌资产（logo、官网域名） | 全部物料视觉 | Studio 侧独立负责 |

## 写作纪律（对内约束，同样适用于对外稿）

- 只陈述已发布能力（当前合格稳定 `v1.0.0`；`v1.1.0` withdrawn）；主线已并入未发版权益标注「随 `v1.2.0` 发布」
- 不宣称生产就绪 / HA / 托管服务——产品交付的是可验证的工程基线
- 对外稿不点名第三方竞品，采用范式类别对比（内部矩阵见
  [`design/ainer-scaffold-design.md`](../design/ainer-scaffold-design.md)，仅供内部参照）
- 动态数字（测试数、制品数）引用 `project-status.md` 而非硬编码
