# Ainer 文档中心

> 文档类型：导航与治理 · 状态：生效 · 最近核对：2026-07-26 · 适用版本：`0.1.x`

本目录是 Ainer Boot 的长期知识入口。文档不只解释“现在有什么”，还必须说明约束、证据、未完成边界和未来如何安全修改。

## 1. 文档分类

| 类型 | 作用 | 主要文档 | 维护方式 |
|---|---|---|---|
| 产品入口 | 说明定位、能力和最短启动路径 | [`README.md`](../README.md) | 用户可见能力变化时更新 |
| 长期规范 | 约束架构、接口、编码、数据和测试方式 | [`architecture.md`](architecture.md)、[`api.md`](api.md)、[`conventions.md`](conventions.md)、[`database.md`](database.md)、[`testing.md`](testing.md) | 代码与规范必须同一变更交付 |
| 开发与运维 | 说明开发、配置、运行、集成和发布步骤 | [`development.md`](development.md)、[`configuration.md`](configuration.md)、[`operations.md`](operations.md)、[`ainer-admin-integration.md`](ainer-admin-integration.md)、[`releasing.md`](releasing.md) | 命令、配置、集成契约或发行物变化时更新 |
| 决策记录 | 保存重要设计的背景、选择和后果 | [`decisions/README.md`](decisions/README.md) | 已接受 ADR 不改写结论，以新 ADR 取代 |
| 阶段状态 | 记录已完成、验证证据、风险和下一步 | [`project-status.md`](project-status.md)、[`CHANGELOG.md`](../CHANGELOG.md) | 每个里程碑和发布候选更新 |
| 专题设计 | 深入描述某一能力 | [`security.md`](security.md)、[`ai-gateway.md`](ai-gateway.md) | 对应能力或威胁模型变化时更新 |
| 研究与迁移 | 保存兼容性验证和旧系统迁移路线 | [`boot4-migration-notes.md`](boot4-migration-notes.md)、[`migration/ainer-migration-plan.md`](migration/ainer-migration-plan.md) | 新证据出现时追加，不能写成已交付事实 |

“长期规范”和已接受 ADR 是约束来源；`project-status.md` 是时间敏感快照。不得把计划中的能力写入长期规范并描述为已经实现。

## 2. 推荐阅读路径

### 新开发者

1. [`README.md`](../README.md)
2. [`project-status.md`](project-status.md)
3. [`development.md`](development.md)
4. [`architecture.md`](architecture.md)
5. [`conventions.md`](conventions.md)
6. [`testing.md`](testing.md)
7. 修改 HTTP 时阅读 [`api.md`](api.md)
8. 与任务相关的 ADR 和专题文档

### 架构或模块边界变更

1. [`design/paradigm-redesign.md`](design/paradigm-redesign.md)
2. [`architecture.md`](architecture.md)
3. [`decisions/README.md`](decisions/README.md)
4. 新建或取代 ADR，再开始实现

### 数据库变更

1. [`database.md`](database.md)
2. [`testing.md`](testing.md)
3. 所属模块的现有 migration 与集成测试

### 安全与身份变更

1. [`security.md`](security.md)
2. ADR-0005 至 ADR-0012
3. [`configuration.md`](configuration.md)
4. 涉及官方管理应用时阅读 [`ainer-admin-integration.md`](ainer-admin-integration.md)

### AI 能力变更

1. [`ai-gateway.md`](ai-gateway.md)
2. ADR-0003
3. [`security.md`](security.md) 中的数据与身份约束

## 3. 文档维护协议

每个功能变更都必须回答以下问题：

1. 用户可见能力或启动方式是否变化？变化则更新根 `README.md`。
2. HTTP 路径、字段、状态码、scope 或错误是否变化？变化则更新 `api.md`。
3. 模块边界、事务、安全、兼容性或商业承诺是否变化？变化则新增 ADR。
4. 配置键、默认值或密钥要求是否变化？变化则更新 `configuration.md`。
5. 表、索引、migration 或数据库归属是否变化？变化则更新 `database.md`。
6. 测试命令、门禁或跳过行为是否变化？变化则更新 `testing.md`。
7. 运行、诊断、备份或回滚方式是否变化？变化则更新 `operations.md` 或 `releasing.md`。
8. 里程碑状态是否变化？变化则更新 `project-status.md` 和 `CHANGELOG.md`。

文档与代码发生冲突时，不得默默选择一方：先确认是实现偏离已接受决策，还是文档已经失效；修复冲突并在同一变更中留下依据。

## 4. 写作规则

- 使用中文说明设计与运维语义；代码标识、协议名和命令保留原文。
- 所有命令从仓库根目录执行，例外必须显式写出工作目录。
- 示例只使用占位符，不记录真实域名、密码、Token、私钥和供应商响应正文。
- 使用相对链接，移动文件时同步修复入链。
- 时间敏感事实必须标注核对日期；动态测试数量只写入 `project-status.md`。
- 计划使用“拟议”“未实现”，已完成能力必须能指向代码、migration 或测试证据。
- 重大删除保留迁移和弃用说明，不能只删除旧文档让历史语境消失。

## 5. 所有权

文档所有权跟随代码所有权：修改某个模块的人同时负责其 API、配置、数据、测试和运行说明。跨模块规范由核心维护者审查，安全与数据库变更必须有对应领域审查。

当前项目尚未建立具名维护者名单和 `CODEOWNERS`。团队扩展到多人稳定维护后，应在仓库中记录模块负责人、备份负责人和审查范围；在此之前，任何人都不能因为“没有文档负责人”而合并已知失真的说明。

## 6. 文档完成定义

文档变更至少满足：链接可达、命令与当前 POM/配置一致、没有真实秘密、没有把未来能力写成现状，并通过 `git diff --check`。影响代码的变更仍必须执行 [`testing.md`](testing.md) 规定的测试，不能用文档审查代替运行验证。
