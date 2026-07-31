# ADR-0002：Workspace 持久化基线

- 状态：Accepted
- 日期：2026-07-22
- 被部分取代：[ADR-0028](0028-mybatis-plus-infrastructure-baseline.md)（仅第 3 项
  “不引入 MyBatis-Plus”的 M1 阶段性工具选择）
- 当前关系：本 ADR 保留 M1 历史基线；其余 PostgreSQL、Flyway、UUID、事务、模块数据所有权和
  抽取时机决策继续有效。

## 背景

Foundation v0.1 证明了 JDK 25、Spring Boot 4.1、核心错误契约、自动装配和 Web 语义，但尚未证明真实数据库、业务模块边界与事务策略。Aurora 需要一个足够小、又能覆盖聚合写入、成员关系、分页、唯一约束和并发更新的垂直切片。

## 决策

1. 第一个业务切片采用 `workspace`，由模块独占领域模型、应用用例、表、migration、Mapper 和 HTTP API。
2. 使用 PostgreSQL 作为唯一数据库基线；自动化集成测试使用 Testcontainers，不使用 H2 compatibility mode。
3. M1 采用 MyBatis Spring Boot Starter 4.0.0，不引入 MyBatis-Plus。Mapper 是 infrastructure adapter，应用层只依赖 Repository 端口。
4. 数据库结构只由 Flyway migration 演进。Boot 4 显式依赖 Flyway starter 与 PostgreSQL database module。
5. UUID 在 Java 中保持 `UUID` 类型、在 PostgreSQL 中使用原生 `UUID`，通过显式 TypeHandler 以 `Types.OTHER` 绑定。
6. 事务边界位于应用用例。创建 workspace 与 OWNER 成员必须原子完成，并用失败注入测试回滚。
7. workspace 重命名使用版本字段和条件更新实现乐观锁；成员唯一性由数据库主键强制，并转换为稳定 409 业务错误。
8. 不在第一个消费者出现时抽取通用 persistence starter；待第二个真实数据模块出现后，以重复实现为证据提炼公共能力。该升级条件已在 M2 由 AI invocation 满足。

## 已验证行为

- 空 PostgreSQL 数据库可由 Flyway 创建两张表、约束和索引，并通过 validate。
- 应用可在 PostgreSQL 18 上完成启动以及创建、读取、分页、重命名和成员冲突 HTTP 契约。
- OWNER 写入失败时，已写入的 workspace 会回滚。
- MyBatis 读写 PostgreSQL 原生 UUID 正常，乐观锁条件更新有效。
- ArchUnit 阻止 domain 依赖框架，并阻止 application 依赖 API/infrastructure。

## 后果

正面：

- Aurora 拥有了第一条从 HTTP 到 PostgreSQL 的可执行工业基线。
- 数据所有权、事务、错误语义和模块边界由代码与测试共同约束。
- 后续 AI、identity 和业务切片可以复用已验证的决策，而不是复制竞品数据层。

代价：

- 本地完整数据库测试需要 Docker/Testcontainers 或等价的真实 PostgreSQL 环境。
- M1 曾在 workspace 内保留少量数据库装配代码；M2 已将两个消费者稳定重复的装配提炼到 persistence starter。
- 乐观锁冲突需要客户端重新读取并重试，不能静默覆盖并发更新。

## 升级条件

M2 的 AI invocation 成为第二个 PostgreSQL 业务模块后，已经抽取 `aurora-starter-persistence`。当前仅 UUID TypeHandler 与 MyBatis/Flyway/PostgreSQL 装配进入 framework；业务表、migration、领域 Repository、事务与错误码继续留在所属模块。

后续只有在两个以上模块再次证明共性时，才扩展 starter。审计字段、分页模型和软删除等仍不得因为“常见”而提前固化。

## 当前演进

2026-07-30，Ainer 在真实 PostgreSQL 18.3 上验证 Spring Boot 4 专用 MyBatis-Plus starter 后，
接受 ADR-0028：MyBatis-Plus 只作为 infrastructure 的简单 CRUD/分页增强，原有复杂 XML、
Repository 端口、应用事务和显式 tenant 条件保持不变。本节只说明 M1 之后的决策关系，不改写
本 ADR 发生时的背景和结论。
