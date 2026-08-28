# Ainer Boot 评估与客户交付指南

> 面向：技术评估、试点项目、接入工程师与运维负责人 · 商务合同状态：未定稿
> 商业事实基线：`v1.4.0` · 对应版本：`v1.4.0` · 最近核对：2026-08-28
> 本指南是工程手册的采用者视角重组；专题细节以文末工程手册为准。

## 1. 当前可获得的交付物

| 交付物 | 形态 | 用途 |
|---|---|---|
| 版本化制品集 | BOM + framework/starter/模块 JAR（OpenPGP 签名 + SBOM） | 固定版本远端消费 |
| Project Initializer | CLI JAR + Manifest v1/v2 规范 | 新建项目或向既有项目增量接入 |
| 参考装配应用 | Resource Server + Authorization Server | 架构评估、配置与安全边界参照 |
| 工程文档套件 | 开发/安全/数据库/API/运维手册 + ADR | 团队自持维护 |
| 测试基座 | 真实签名 JWT 夹具、PostgreSQL Testcontainers 封装 | 产品 CI 复用 |

这些是 MIT 工程交付物，不表示已签署商业支持合同。报价、专属交付、响应 SLA 与值班支持当前
均未形成正式产品条款。

## 2. 环境前提

- JDK 25；Docker-compatible runtime（集成测试使用 PostgreSQL 18 容器）；
- 本地或远端 PostgreSQL 18；
- GitHub Packages 读取凭据通过环境变量或 Maven settings 注入，不写入仓库；
- 生成项目必须使用自身锁定的 Maven 3.9.16 Wrapper，不能借用 Ainer 生产者的 Maven 4 Wrapper。

## 3. 路径 A：生成新项目

```bash
java -jar ainer-initializer-cli-1.4.0-cli.jar preview manifest.yaml
java -jar ainer-initializer-cli-1.4.0-cli.jar init manifest.yaml my-product/
cd my-product
./mvnw verify
```

Manifest v1 保持兼容；Manifest v2 必须显式选择 `schemaVersion: v2`、`preset: simple-service`、
`accessControl: workspace` 和产品自有错误命名空间。同一版本、同一 Manifest 与规范化输入的生成
结果应保持一致，非空目标默认拒绝覆盖。

## 4. 路径 B：既有项目增量接入

`v1.4.0` 首版只支持已导入同版本 Ainer BOM 的单模块 Maven/Spring Boot 项目。先执行只读规划，
审核文件与 POM 变化后再执行幂等写入：

```bash
java -jar ainer-initializer-cli-1.4.0-cli.jar \
  plan-add manifest-v2.yaml /path/to/existing-project --migration-version 3
java -jar ainer-initializer-cli-1.4.0-cli.jar \
  add manifest-v2.yaml /path/to/existing-project --migration-version 3
```

调用者必须显式指定第一个 Flyway 版本。工具只新增切片文件并有限合并顶层 POM，不修改宿主
Application、application.yml、README、Wrapper 或既有 migration；多模块、Gradle、plugin/profile
策略和自动 migration 编号不在当前范围。

## 5. 首次启动与验收

1. 提供空 PostgreSQL 18 数据库，从空库执行全部 migration；
2. 验证 Authorization Server、issuer/JWK，再启动 Resource Server；
3. 验证 `/actuator/health`、平台信息、无 Token 401、缺权限 403、稳定错误码与 request ID；
4. 验证产品自己的 Workspace/资源 ownership、越权负向路径与审计失败回滚；
5. 关闭可选模块，验证 off-state 构建与启动；
6. 生产候选还必须完成目标入口 HTTPS、真实身份 client、密钥/secret、监控、备份恢复与容量门禁。

## 6. 升级与回滚

- 合格版本链为 `v1.0.0 → v1.2.0 → v1.3.0 → v1.4.0`；`v1.1.0` withdrawn，不得消费。
- 版本政策支持相邻合格 minor 升级与一级应用回滚；跨多个版本时逐级重放。
- `v1.4.0` 的远端制品、Maven 3/4、Initializer 五通道与不可变 Release 已通过发布门禁；真实产品
  消费者的 `1.3.0 → 1.4.0`、migration replay 与一级回滚仍未完成，不能把流水线参考消费者当成
  客户生产升级证明。
- 数据库 migration 只向前追加；回滚 JAR 不自动回退 schema。升级前必须在可恢复的数据副本上
  验证，并形成目标产品自己的备份、PITR 和恢复点选择方案。

## 7. 日常运维指针

- 启停、在线撤销、指标、故障处理与数据保护：[`../operations.md`](../operations.md)
- 身份、scope、Passkey、审计与 SIEM：[`../security.md`](../security.md)
- AI 网关、费用、限流与错误脱敏：[`../ai-gateway.md`](../ai-gateway.md)
- 发布、制品、签名、升级与回滚：[`../releasing.md`](../releasing.md)

当前应用提供受保护 Prometheus exporter，不等于已部署 Prometheus、dashboard、告警路由和值班；
当前 migration 可重放，也不等于已经完成生产备份或灾难恢复。

## 8. 责任分界

| Ainer Boot 当前负责 | 采用者/目标产品必须负责 |
|---|---|
| 公共制品、Initializer、工程合同与发布证据 | 产品业务、数据、资源 ownership 与端到端验收 |
| 协议级身份、授权与审计能力 | 生产 client 生命周期、职责分离、密钥托管与轮换 |
| migration 正确性与参考重放门禁 | 生产备份、PITR、恢复演练与变更窗口 |
| 受保护指标端点与运行手册 | Prometheus/dashboard、阈值、告警路由和值班 |
| EMAIL/WEBHOOK 可选脚手架与通知 SPI | 供应商账号、最终投递、SMS/Push 实现和供应商合规 |

## 9. 当前支持状态

社区文档、Issue 与版本政策可以用于技术评估；商业支持渠道、响应 SLA、专属升级协助、赔付和
生产兜底尚未定稿。任何试点或客户项目都应在合同中单独写清交付范围、验收证据、责任人和退出条件，
不能引用本指南推定未签署的服务承诺。
