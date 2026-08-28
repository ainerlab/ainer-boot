# Ainer Boot 商业分层框架（待定稿）

> 商业方案状态：**Proposed**，不构成报价、授权或支持承诺
> 商业事实基线：`v1.4.0` · 最近核对：2026-08-28
> 技术事实来源：[`project-status.md`](../project-status.md)；产品路线：
> [`design/ainer-scaffold-design.md`](../design/ainer-scaffold-design.md)

本文把**已经存在的 MIT Community 工程实体**与**尚未定稿的付费层提案**分开。技术事实随发布
更新，不再用“整套文档仍是草案”掩盖版本漂移；Pro / Enterprise 的能力、价格、entitlement、
支持和交付系统只有在独立决策与验收完成后才可对外承诺。

## 1. 分层原则

1. **可信内核不阉割**：标准协议、安全默认值、真实 HTTP 语义、迁移与供应链证据属于 Community；
2. **付费价值来自持续服务**：升级协助、企业连接器、治理工具、运营控制台、合规交付与响应 SLA；
3. **代码能力与服务承诺分开**：仓库中存在功能不等于有人承担生产运行和合同责任；
4. **路线图不是货品**：未交付能力必须写“提案/未交付”，不能进入报价能力清单。

## 2. 当前事实对照（截至 `v1.4.0`）

| 能力域 | 当前已交付 | 当前未交付或未验收 |
|---|---|---|
| 制品与工具 | 公开 MIT 源码；版本化 BOM/framework/starter/模块；Initializer v1/v2、`plan-add`/`add` | 商业模板市场、entitlement、私有镜像托管服务 |
| 安全身份 | OAuth 2.1/OIDC、JWT、在线校验/撤销、Passkey 协议与自动化门禁 | 生产 browser client 生命周期、真实设备矩阵、多节点会话、正式密钥轮换 |
| 授权与组织 | RBAC+ReBAC+ABAC、Workspace、组织目录、审计和粗粒度端点门禁 | Ainer Admin、完整 obligation executor、生产撤权 SLA、SCIM/企业 SSO 对接 |
| 企业基座 | 文件/字典/配置/通知/缓存/任务服务端能力；EMAIL/WEBHOOK 可选实现 | SMS/Push 生产连接器、供应商最终送达与运营控制台 |
| AI 与知识 | 模型网关、白名单、限流/预算/费用审计；Incubating Knowledge/Agent 基线 | RAG、Evaluation、完整 Agent Runtime、集群限流和运营控制台 |
| 运维与供应链 | health/受保护 metrics、SBOM、签名、checksum、provenance、immutable Release | 双节点/容量资格、Prometheus dashboard/告警、备份/PITR/灾备、独立安全评审 |
| 消费与升级 | Maven 3/4 空仓消费、Initializer 五通道；历史产品升级链留档至 `1.2.0` | 真实产品 `1.3.0 → 1.4.0` 与生产级升级/回滚演练 |

## 3. 候选商业层（尚未形成 SKU）

| 层 | 候选价值 | 当前状态 |
|---|---|---|
| **Community** | MIT 工程内核、文档、公开制品与社区版本政策 | **实体已存在**；不含合同支持或生产兜底 |
| **Pro** | 连接器、策略/模板包、升级辅助工具、团队支持 | **未定稿、未形成可售 SKU** |
| **Enterprise** | SSO/SCIM、合规交付、HA/灾备评审、专属 LTS、升级演练与 SLA | **未定稿、未形成可售 SKU** |

当前可以分发和评估的是 Community 工程实体，不应写成“Pro/Enterprise 已有但尚未定价”。付费层
是否采用两层或三层、哪些条目是软件许可或专业服务，都还需要商业决策与交付能力验收。

## 4. 不应分层或包装的内容

- OAuth/OIDC、真实 HTTP 语义、安全默认值、错误脱敏与隔离规则不应成为付费后才安全的功能；
- 消息中间件、多数据源/分库分表、Spring Cloud 全家桶和“一项配置变微服务”不是当前产品目标；
- 未完成的 HA、灾备、外部客户案例、RAG/Agent/Evaluation 和管理控制台不能先进入销售清单；
- MIT 不授予 Ainer 商标权，也不自动提供官方认证、支持、赔付或生产责任。

## 5. 定稿退出条件

1. 决定 Community + Enterprise 两层制还是 Community / Pro / Enterprise 三层制；
2. 为每个付费条目标注“软件、连接器、托管能力或专业服务”，并提供验收证据；
3. 建立 entitlement、授权交付、升级与终止流程；
4. 确定价格、税费、订阅/买断/项目制和续费规则；
5. 根据真实团队产能确定可兑现的支持时间、升级窗口、SLA 与赔付边界；
6. 至少完成一个外部客户试点和一次目标环境生产资格评审，再决定是否公开 Enterprise 承诺。
