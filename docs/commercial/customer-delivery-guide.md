# Ainer Boot 客户交付指南

> 面向：已采购/评估客户的接入工程师与运维负责人 · 对应版本：`v1.2.0`+
> 本指南是工程手册的客户视角重组；细节以各专题手册为准（文末索引）。

## 1. 你会收到什么

| 交付物 | 形态 | 用途 |
|---|---|---|
| 版本化制品集 | BOM + framework/starter/模块 JAR（GPG 签名 + SBOM） | 从受控仓库拉取消费 |
| Project Initializer | CLI jar + manifest 清单规范 | 生成你的独立项目 |
| 参考装配应用 | 完整模块化单体示例 | 架构评估与配置参照 |
| 工程文档套件 | 开发/安全/数据库/API/运维手册 + ADR 决策库 | 团队自持维护 |
| 测试基座 | 真 JWT 夹具、Testcontainers 封装 | 你的 CI 直接复用 |

## 2. 环境前提

- JDK 25、Docker（集成测试用真实 PostgreSQL 容器）、本地或远端 PostgreSQL 18
- 制品仓库访问凭据（环境变量注入，零密钥入库；接入时提供可复用 settings 模板）
- 注意：生成项目自带锁定版 Maven Wrapper，**不要**用系统全局 Maven 替代

## 3. 路径 A：生成新项目（新产品推荐）

```bash
java -jar ainer-initializer-cli-<版本>-cli.jar preview manifest.yaml   # 只读校验，不落盘
java -jar ainer-initializer-cli-<版本>-cli.jar init manifest.yaml my-product/
cd my-product && ./mvnw verify    # 自带 Wrapper 与真实 PG 集成测试
```

确定性保证：同一清单两次生成字节一致；非空目标拒绝覆盖。清单字段见随附样例目录。

## 4. 路径 B：既有项目接入

在 `dependencyManagement` 导入 BOM（固定版本），按需引入 starter（web / persistence /
security / cache）。安全链需要资源服务器配置（issuer 与受众），见《安全手册》对应章节。

## 5. 首次启动与验收

1. 提供空 PostgreSQL 数据库，应用启动时迁移脚本自动建表（只向前追加，可空库重放）
2. `GET /actuator/health` 就绪探针；平台信息端点返回运行模式与版本
3. 验收基线建议：无 token 访问受保护端点应得 401；错误响应携带稳定错误码与请求追踪 ID
4. 全部模块可独立关闭（配置开关），关闭行为有启动测试背书

## 6. 升级与回滚

- 官方支持**相邻合格次版本升级 + 一级回滚**（N+1 → N）；跨多版本升级需逐级执行。
  withdrawn / 未形成制品的版本跳过（如 `v1.1.0`），升级路径是 `v1.0.0 → v1.2.0`
- 每个次版本附发布证据：签名制品读回验签记录、消费者升级矩阵结论
- 数据库迁移只向前追加；回滚是制品级回滚，schema 不自动回退（升级前快照属你的部署流程）

## 7. 日常运维指针

- 健康检查、指标端点（Prometheus 格式）、结构化日志：见《运维手册》
- 授权审计的保留、归档与 SIEM 拉取（稳定游标）：见《安全手册》审计章节
- AI 网关的费用与用量审计查询：见《AI 网关手册》
- 备份策略针对 PostgreSQL 常规实践即可——Ainer 无自有状态存储于应用侧

## 8. 责任分界（重要）

| 属于 Ainer Boot | 属于你的产品 |
|---|---|
| 可验证的工程基线与兼容承诺 | 生产高可用、容量规划、告警值班 |
| 协议完整的身份组件 | SMS/Push 等未内置渠道（经 `ChannelSender` SPI 接入；EMAIL/WEBHOOK 已有可选脚手架实现） |
| 迁移脚本的正确性与重放性 | 备份执行、快照管理 |
| 发布物签名与供应链证据 | 你的制品仓库访问控制 |

## 9. 问题上报与支持（占位）

支持渠道、响应 SLA、升级协助范围：**待商务分层定稿后补充**
（草案见 [`edition-tiers.md`](edition-tiers.md)）。
