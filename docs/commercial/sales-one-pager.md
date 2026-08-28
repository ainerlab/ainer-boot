# Ainer Boot —— 售前技术评估一页纸

> 文档性质：售前技术说明，不是报价单、生产承诺或 SLA
> 商业事实基线：`v1.4.1` · 本文对应工程版本：`v1.4.1` · 最近核对：2026-08-28 · 源码 MIT（ADR-0051）

## 给技术决策者的一句话

**Ainer Boot 是带证据链的企业 Java 脚手架：JDK 25 + Spring Boot 4.1 + PostgreSQL 18，
身份、授权、Workspace 治理、AI 治理网关与企业基座可按模块采用。**

## 它解决什么

- 演示级登录权限需要推倒重写：提供独立 OAuth 2.1/OIDC Authorization Server、真实 JWT 与
  fail-closed 安全链；
- 简单“部门+角色+数据范围”撑不起真实企业：提供 Workspace、RBAC+ReBAC+ABAC、组织目录集合
  绑定和审计；
- 升级不敢动：提供固定版本制品、相邻版本规则、签名供应链、远端消费者与 migration 门禁；
- 既有项目接入成本高：`v1.4.0` 提供只读 `plan-add` 与幂等 `add`，显式 migration、有限 POM
  合并，不覆盖宿主代码和配置。

## 为什么可信

- 正式门禁使用真实 PostgreSQL，HTTP 安全测试使用真实签名 JWT；
- Maven 3.9.16 / Maven 4 从空仓消费，Initializer 验证新建与既有项目五个通道；
- 制品带 OpenPGP 签名、SBOM、SHA-256/SHA-512、项目 provenance，并从不可变 Release 读回；
- `v1.4.1` 是兼容性补丁发布目标；完成不可变 Release 前，当前合格稳定仍为 `v1.4.0`；
  `v1.1.0` withdrawn；`1.0.x` 为首个 LTS 工程补丁线。

## 当前边界

| 可以承诺 | 不能承诺 |
|---|---|
| 商业级工程基线、固定版本制品、Initializer、公开版本政策 | 已验证 HA、容量、灾备、托管运维或生产兜底 |
| `v1.4.0` 远端制品与参考消费者门禁通过 | 真实产品 `1.3.0 → 1.4.0` 已完成 |
| MIT Community 工程实体可评估和采用 | Pro / Enterprise 已有价格、entitlement 或合同 SLA |

## 交付形态

`BOM/Starter 制品消费` · `Initializer 新建项目` · `Initializer 增量接入` · `参考装配`

正式前端管理产品、生产连接器、RAG/完整 Agent Runtime、HA/灾备服务和商业支持不在当前已交付
范围。受控生产必须完成目标产品升级、双节点/容量、监控告警、备份/PITR、密钥轮换和安全评审。

## 下一步

- 技术事实与边界：[`product-whitepaper.md`](product-whitepaper.md)
- 评估与接入：[`customer-delivery-guide.md`](customer-delivery-guide.md)
- 商业分层提案：[`edition-tiers.md`](edition-tiers.md)
- 定价、支持渠道和联系方式：**尚未定稿**
