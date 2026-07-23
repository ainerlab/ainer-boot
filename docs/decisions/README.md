# Ainer 架构决策记录

> 文档类型：决策索引 · 状态：生效 · 最近核对：2026-07-23

ADR 记录难以逆转、跨模块或影响长期兼容性的决定。它不是实现日志，也不替代 API 或运行手册。

## 当前决策

| ADR | 状态 | 主题 |
|---|---|---|
| [0001](0001-independent-architecture-baseline.md) | Accepted | 自主架构基线与竞品隔离 |
| [0002](0002-workspace-persistence-baseline.md) | Accepted | Workspace 持久化基线 |
| [0003](0003-ai-model-gateway-baseline.md) | Accepted | AI Model Gateway 基线 |
| [0004](0004-ainer-brand-and-naming-baseline.md) | Accepted | Ainer 品牌与技术命名 |
| [0005](0005-identity-and-oauth2-security-baseline.md) | Accepted | Identity 与 OAuth 2.1 安全基线 |
| [0006](0006-workspace-tenant-authorization-baseline.md) | Accepted | Workspace tenant 与资源授权 |
| [0007](0007-workspace-membership-lifecycle-and-audit.md) | Accepted | 成员生命周期、所有权与授权审计 |
| [0008](0008-identity-directory-and-access-revocation.md) | Accepted | Directory 与访问撤销传播边界 |
| [0009](0009-cross-runtime-access-revocation-delivery.md) | Accepted | 跨运行时 Directory 与访问撤销投递 |
| [0010](0010-security-operations-and-audit-lifecycle.md) | Accepted | 安全运维双人审批与授权审计生命周期 |
| [0011](0011-selective-online-token-validation.md) | Accepted | 高风险 API 选择性在线 Token 校验 |
| [0012](0012-production-observability-and-auth-availability.md) | Proposed | 生产指标访问与 Authorization Server 可用性边界 |

## 何时需要 ADR

- 新增或改变模块边界、数据库所有权或部署拓扑；
- 选择身份协议、消息中间件、AI provider 抽象或重要第三方依赖；
- 改变事务、tenant 隔离、加密、审计或数据保留策略；
- 引入破坏性 API、配置、事件或 schema 变更；
- 做出会影响商业授权、clean-room 或长期成本的选择。

普通 bug 修复、局部重构和不改变边界的实现细节不需要 ADR。

## 生命周期

状态使用 `Proposed`、`Accepted`、`Rejected`、`Deprecated` 或 `Superseded by ADR-NNNN`。新 ADR 从 Proposed 开始，完成审查后才能 Accepted。

已接受 ADR 保留当时背景和结论。允许修正链接、错字和不改变含义的事实错误；改变结论时新增 ADR，并在旧 ADR 标记被取代，不能改写历史。

## 编号与模板

从全仓下一个四位序号开始，文件名使用：

```text
NNNN-short-kebab-title.md
```

复制 [`0000-template.md`](0000-template.md)，填写背景、决策、备选、后果、安全/数据/运维影响、验收证据和迁移方式。不得只写最终方案而省略取舍依据。
