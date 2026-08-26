# Ainer Boot 版本分层框架（草案）

> 状态：**草案**——能力边界基于已交付代码事实与产品路线推导。源码许可已定为 MIT
> （ADR-0051）；定稿仍需定价与支持 SLA。本文不构成对外承诺。
> 事实来源：[`design/ainer-scaffold-design.md`](../ainer-scaffold-design.md) §3.3 能力矩阵、
> [`project-status.md`](../project-status.md)

## 1. 分层原则

从能力矩阵导出的三条切分线：

1. **内核 vs 增值**：可独立验证、跨产品复用的平台内核进 Community；依赖运营设施
   （控制台、模板库、连接器）或人力服务（SLA、升级协助）的进商业层
2. **协议能力 vs 运营能力**：标准协议实现（OAuth/OIDC/HTTP 语义）永远在 Community——
   它们是信任基座；运营效率工具（管理界面、批量工具）在上层
3. **治理强度**：审计留存、合规导出、职责分离等合规驱动能力随层级递进

## 2. 三层定义

| 能力域 | Community | Pro | Enterprise |
|---|---|---|---|
| **制品与构建** | BOM + 全部 framework/starter 制品消费；Initializer 确定性生成 | 行业/组织模板包 | 私有制品镜像同步、内部仓库托管协助 |
| **安全身份** | 完整 OAuth 2.1/OIDC 授权服务器、JWT 双轨令牌、Passkey 协议能力、step-up | 条件 MFA 策略调优、登录风控参数包 | SSO 对接、SCIM、职责分离咨询 |
| **授权引擎** | 混合授权引擎全量（RBAC+ReBAC+ABAC）、防提权矩阵、决策审计、组织目录集合绑定 | 策略模板库 | 高级策略设计与评审服务 |
| **企业基座** | 文件/字典/配置/通知/缓存/任务调度全模块（生产渠道 SPI 自接入） | 商业渠道连接器（SMS/Email/Webhook 托管适配） | 合规留存策略、归档导出对接 |
| **AI 运行时** | 模型网关、白名单、限流、预算、费用审计、Agent 注册表 | 预置评测集与回归基线 | Agent/RAG/Evaluation 控制台、Guardrails |
| **知识库** | Knowledge Foundation（不可变版本 + 人工发布门禁） | — | OKF 导入/导出、Context Assembly（路线图） |
| **API 与前端** | HTTP 契约、OpenAPI、TypeScript SDK、参考 Admin 模板 | Studio Blocks 组件库授权 | 设计系统定制 |
| **运维与供应链** | Compose、健康检查、指标端点、SBOM、签名发布物 | — | HA 架构评审、灾备方案、专属 LTS 窗口 |
| **升级支持** | 社区文档 + 相邻版本升级路径 | 升级助手工具 | 专属升级/回滚演练服务、SLA 响应 |

## 3. 当前交付状态对照（截至合格发布 v1.2.0）

| 层 | 已交付 | 未交付（不得对外承诺） |
|---|---|---|
| Community 范围 | 上表前四行 Community 列的**全部内容**均已交付并过发布门禁 | — |
| Pro 范围 | 无（全部待建） | 渠道连接器、策略模板库、评测集 |
| Enterprise 范围 | 无（全部待建） | SSO/SCIM、控制台、HA 服务、SLA |

结论：**当前可售的是 Community 层的完整实体**，Pro/Enterprise 是路线图承诺而非现有
货品。对外沟通中不得把第二、三列表述为「已有」。

## 4. 明确不分层的内容

以下为产品非目标，任何层级均不含（ADR-0040）：消息中间件集成、菜单/前端路由权限引擎、
多数据源/分库分表、Spring Cloud 全家桶、「一个配置变微服务」。这些是边界声明而非预留
卖点。

## 5. 定稿前必须完成的决策

1. Pro 层是否有独立存在价值（vs Community + Enterprise 两层制）
2. Enterprise 服务型条目（咨询/演练/SLA）按订阅还是按项目计价
3. Community 源码许可已定为 MIT（ADR-0051）；商标与官方背书仍按 ADR-0004
4. 各层的支持响应承诺（工程团队产能约束下可兑现的 SLA）
