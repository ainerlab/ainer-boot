# ADR-0038：P4 范围精简与企业基建前置

- 状态：Accepted
- 日期：2026-08-12
- 决策者：Ainer 项目维护者
- 取代：无（局部修订 `design/ainer-scaffold-design.md` §13 P3/P4 验收标准）
- 被取代：无

## 背景

`scaffold-design.md` §13 定义了 P0–P5 产品化阶段。P4「AI-Native Enterprise Scaffold」的验收标准
包含两类截然不同的内容：

1. **传统企业基建**：通知、任务、观测等常用模块闭环；树表/主子表生成。
2. **AI 差异化**：Agent/Tool/RAG/Evaluation 具备身份、权限、预算、数据治理门禁。

将这两类混入同一阶段存在三个问题：

- **竞争错位**：通知/任务/缓存/字典是竞品（RuoYi/BladeX/Snowy）的覆盖优势领域。Ainer-Boot 用
  clean-room 重新实现这些通用模块，既无法在覆盖度上竞争，又稀释了自身的差异化投入。
- **阻塞产品**：文件存储、字典/配置是几乎所有业务开发的阻塞项，但它们被归入 P4，排在 P3 首个
  外部消费者之后。实际上 xq-platform-next 在 P3 阶段就需要它们。
- **AI 被埋没**：Ainer-Boot 的核心定位（§1）是「AI-native 平台内核」，但 AI 差异化能力
  （Agent 代行、Knowledge Foundation）被排在 P4 的传统模块之后，导致差异化最后才做。

同时，`scaffold-design.md` §1 明确：

> AI-native 表示模型、Agent、工具、RAG 与评测从一开始就受身份、权限、预算、数据政策、审计和
> 可观测性治理……普通企业应用不启用 AI 模块时仍必须获得完整、可靠的脚手架体验。

这要求 AI 能力是「一等可选」，企业能力是「可靠基座」，而非 P4 一次性堆叠。

## 决策驱动因素

- Ainer-Boot 的护城河是「标准身份 + 混合细粒度授权 + clean-room 现代基线 + AI 治理」，不是
  CRUD 模块覆盖度；
- 脚手架应是「最小的、能开发任何后台的平台内核」，而非「自带所有后台功能的成品」；
- 通知/任务/缓存等模块的业务语义高度产品特定，做成通用模块要么过度设计要么不够用；
- scaffold-design §13.3 原则「P3 不等待 P4 或 P5」应延伸为「产品需求驱动模块落地，阶段不阻塞」。

## 决策

### 1. P3 验收标准扩展：纳入最小企业基建

P3 从「Minimum Admin & First Consumer」扩展为包含**开发任何产品后台都需要的最小基座**：

| 基建项 | 范围 | 实现方式 |
|---|---|---|
| **文件存储** | 上传、下载、删除、元数据 | `FileStoragePort` SPI + 本地文件系统 adapter（产品可加 S3/OSS adapter） |
| **字典** | `(type, code, label, sort)` 键值查询 + 缓存 | 极简 PostgreSQL 表 + MyBatis + 内存缓存；不做完整管理面 |
| **配置** | `(namespace, key, value)` + `@ConfigurationProperties` 类型安全绑定 | 极简 PostgreSQL 表 + Spring `Environment` 集成 |

这些是**阻塞产品开发的必需品**，不是可选的「企业功能」。P3 必须提供它们。

### 2. P4 验收标准收窄：聚焦 AI 治理深化

P4 从「AI-Native Enterprise **Scaffold**」收窄为「AI 治理深化」：

| 原 P4 内容 | 处置 | 理由 |
|---|---|---|
| Agent/Tool/RAG/Evaluation | **保留为 P4 核心** | 这是 Ainer-Boot 的差异化灵魂 |
| 通知、任务、观测模块 | **移出 P4，改为端口 + 最佳实践文档** | 业务语义高度产品特定；Ainer 提供端口契约，产品自实现 |
| 缓存、幂等、outbox | **移出 P4，改为文档指导** | 同上；outbox 已在 Workspace 撤销链路中有先例可参考 |
| 树表/主子表生成 | **推迟，按产品需求驱动** | Initializer manifest v1 架构已支持扩展，不提前做 |

**P4 重新定义**：

> **P4 AI Governance Deepening**：Agent 代行（ADR-0031）、Knowledge Foundation（ADR-0034）、
> Evaluation/Guardrails 形成身份、权限、预算、数据治理和回归门禁的完整闭环。每个 AI 模块继承
> 平台内核的安全约束（ADR-0037 授权、身份隔离、审计）。AI 能力作为「一等可选」模块，关闭时不
> 影响企业应用的完整脚手架体验。

### 3. 通知/任务/缓存等改为端口 + 文档

Ainer-Boot 为以下领域提供**端口契约 + 最佳实践文档**，但不提供通用实现模块：

| 领域 | 端口/指导 | 产品自实现 |
|---|---|---|
| 通知 | `NotificationPort`（接受结构化通知意图，产品选渠道：SMS/Email/Push/Webhook） | 产品实现渠道 adapter |
| 任务调度 | 文档指导（Spring `@Scheduled` / `TaskScheduler` 集成模式） | 产品按调度策略实现 |
| 缓存 | 文档指导（Spring Cache + PostgreSQL 一致性模式） | 产品按一致性需求选择 |
| 幂等 | `IdempotencyPort`（基于 `requestId` + 短期去重） | 产品可复用或替换 |
| outbox | 文档指导（参考 Workspace 撤销链路的事务 outbox 模式） | 产品按投递语义实现 |

**设计原则**：端口定义在 `ainer-core` 或 `ainer-spring`（零业务语义），文档在 `docs/design/`。
产品通过 Initializer 生成的项目结构中按需实现。这避免 Ainer 变成「万能工具包」，同时为产品提供
明确的契约和最佳实践。

### 4. 组织目录（ADR-0032）精简

ADR-0032 原设计包含组织、员工任职、岗位、团队、SubjectSet binding 的完整模型。P3 阶段只实现
**精简版**：

- 扁平组织（`Organization` + `WorkforceAssignment`），不做复杂层级；
- 与 ADR-0037 授权的 SubjectSetBinding 联动（一跳）；
- 复杂层级、跨组织、岗位继承推迟到 P5 或真实需求驱动。

## 修订后的 P3/P4 验收标准

| 阶段 | 使命 | 验收标准（修订） |
|---|---|---|
| **P3** | 用可用管理面、最小企业基座和真实产品证明脚手架边界 | Identity、可选精简组织/任职、RBAC/数据范围、**文件存储 SPI**、**字典/配置**、菜单与审计形成关键 E2E；Initializer 生成 `xq-platform-next`；不含 Ainer 源码副本或 SNAPSHOT；两个小程序 SDK 可编译；至少一个真实纵向切片和一次 Ainer minor 升级通过 |
| **P4** | AI 治理深化：Agent、Knowledge、Evaluation 形成完整安全闭环 | Agent/Tool/RAG/Evaluation 具备身份、权限、预算、数据治理、人工反馈和回归门禁；AI 模块关闭时企业应用不受影响；通知/任务/缓存等通过端口 + 文档支持，不提供通用实现 |

## 非目标

- 不删除 scaffold-design §1 的「通用企业」定位——Ainer 仍是通用脚手架，只是实现策略从「自带
  所有模块」变为「提供端口 + 最小基座 + 文档指导」；
- 不取消 P4——AI 治理深化仍是重要阶段，只是范围收窄、不再混入传统企业模块；
- 不提前实现通知/任务/缓存的通用模块——等至少两个独立消费者证明语义稳定后再评估；
- 不降低 P5 的要求——生态、升级、LTS 仍是最终目标。

## 后果

### 正面

- P3 提供文件/字典/配置，解除产品开发的基建阻塞；
- P4 聚焦 AI 差异化，避免与竞品在 CRUD 覆盖度上竞争；
- 端口 + 文档策略保持 Ainer 的「小制品」定位，避免膨胀为万能工具包；
- 产品自实现通知/任务等，获得更贴合业务语义的实现，而非被迫适配通用模块。

### 负面与风险

- 产品团队需要自己实现通知/任务等（有端口契约和文档指导，但仍需投入）；
- 端口 + 文档的方式需要维护文档质量，否则产品团队缺乏指导；
- 「最小企业基座」的边界需要持续判断——文件/字典/配置做了，但哪些是「最小」需要按真实产品
  反馈调整。

## 运维与迁移

1. `scaffold-design.md` §13 P3/P4 验收标准更新（本 ADR 接受后同步修改）；
2. 文件存储 SPI 作为 P3 首批代码实现；
3. 字典/配置紧随其后；
4. 通知/任务/缓存的端口契约和文档在 P3 或 P4 按需补齐。

## 参考

- [scaffold-design.md §13 P0–P5](../design/ainer-scaffold-design.md)
- [ADR-0024 演进式模块化平台架构](0024-evolutionary-modular-platform-architecture.md)
- [ADR-0025 公共制品与仓库边界](0025-public-artifacts-utilities-and-repository-boundary.md)
- [ADR-0031 Agent 代行与 AI 上下文授权](0031-agent-delegation-and-ai-context-authorization.md)
- [ADR-0032 组织与员工目录基线](0032-organization-workforce-directory-baseline.md)
- [ADR-0034 Knowledge Foundation 与 AI Context Model](0034-knowledge-foundation-and-ai-context-model.md)
- [ADR-0037 post-Greenfield 授权基线](0037-post-greenfield-authorization-baseline.md)
