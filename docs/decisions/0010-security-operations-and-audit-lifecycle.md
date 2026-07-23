# ADR-0010：安全运维双人审批与授权审计生命周期

- 状态：Accepted
- 日期：2026-07-23
- 决策者：Ainer 核心维护者
- 取代：无
- 被取代：无

## 背景

M4.1 已把 Identity 禁用事实可靠投递到 Workspace，并允许安全撤销 OWNER，但重试耗尽事件只能告警，失去 ACTIVE OWNER 的 Workspace 也没有安全恢复入口。现有 Workspace 授权审计只有在线分页读取，没有热数据保留边界、归档、SIEM 导出或可计算的异常指标。

直接提供“重试”或“恢复 OWNER”按钮会把高风险权限集中到单个运维凭据；直接删除过期热审计会破坏调查链；在尚未选择商业 SIEM 前绑定某个厂商的推送协议也会提高开源与商业发行成本。

## 决策驱动因素

- 恢复和重放只能收敛既有事实，不能伪造新的身份恢复事件或跨 tenant 扩权；
- 高风险操作需要职责分离、最小权限、短时有效和不可重复执行；
- 审计热表可控增长，但归档过程不能形成丢失窗口；
- SIEM 契约要稳定、可续传、厂商无关，并记录谁导出了什么范围；
- 指标必须支持撤销传播 SLO 与 OWNER 缺失、拒绝激增、重放耗尽告警；
- 默认关闭所有内部运维 HTTP 控制面，避免空配置意外暴露。

## 备选方案

### 单管理员加确认字符串

实现简单，但泄露一个 Client Credentials 凭据即可申请并执行，确认字符串不构成独立授权，拒绝。

### 临时 SQL 手工修复

无法稳定执行 tenant 条件、并发锁、审计与幂等，环境容易永久漂移，只保留为经过独立事故审批的最后恢复手段，不作为产品能力。

### 立即绑定某个 SIEM 推送 SDK

可以快速接入单个产品，但会把核心模块绑定到外部协议、重试与许可证。本阶段采用标准 HTTPS 拉取契约和稳定游标；具体 SIEM adapter 可在外围实现。

### 热审计到期直接删除

成本最低，但会破坏历史查询和事件调查，拒绝。只有先完成同事务归档且归档记录可读，热记录才允许删除。

## 决策

1. Identity 耗尽事件重放和 Workspace OWNER 恢复均采用两阶段操作：`REQUESTED` 后由另一服务身份 `APPROVE_AND_EXECUTE`。申请者与批准者的 `sub` 必须不同，分别持有 request/approve scope，操作默认 15 分钟过期且至多执行一次。
2. tenant-bound scope 只能操作服务 JWT `tenant_id` 对应 tenant；只有显式 `.all` scope 可以选择其他 tenant。两阶段都独立重复 tenant 检查，不继承申请者权限。
3. 操作请求只接受受约束的 `incidentReference`，不接受自由文本原因、Token、密码或客户数据。请求、批准、执行结果写入模块所属数据库的安全操作审计，包含操作 ID、tenant、目标、服务主体、阶段和时间。
4. Identity 只允许对仍处于 `PENDING`/`FAILED`、没有有效 lease 且 `attempt_count >= maxAttempts` 的原事件重放。批准事务锁定请求与事件，把原事件重置为可领取 `PENDING`；event ID、事件内容和发生时间保持不变，因此 Workspace receipt 继续提供幂等保护。
5. 重放控制面提供按 tenant 的耗尽事件分页查询。错误正文只返回稳定错误码；历史 `last_error_code` 可导出，但不得存放供应商正文。
6. OWNER 恢复只允许在 Workspace 没有 ACTIVE OWNER、至少存在一个 REVOKED OWNER、目标是同 tenant/Workspace 的 ACTIVE 非 OWNER 成员时执行。批准事务锁定 Workspace 并重新验证全部条件，再提升目标；REVOKED OWNER 不被重新激活或自动降级。
7. Workspace 授权审计设热保留期。归档任务使用小批量、`FOR UPDATE SKIP LOCKED` 和单事务 `INSERT archive ... DELETE hot`；删除热记录的前提是同 ID 归档记录已存在。归档表在本阶段不自动删除。
8. 普通 Workspace 审计查询统一读取热表与归档表，迁移对调用者透明。归档表保留原 audit ID 与全部调查字段，并增加 `archived_at`。
9. SIEM 使用默认关闭的内部 HTTPS 拉取 API。服务 JWT 必须具有 `workspace.audit.export` 或 `.all` scope，并匹配配置的可信 exporter subject；按 `occurredAt + id` 升序稳定游标读取热表与归档表的并集。每个导出批次写安全操作审计。
10. 当前归档只解决数据库内热/冷分层，不宣称 WORM、数字签名或法律意义的不可抵赖。生产环境仍需把 SIEM/归档副本发送到独立权限域、只读或对象锁存储，并对数据库角色和备份实施保护。
11. Workspace consumer 记录端到端撤销传播 Timer，并发布 SLO bucket。默认目标为 60 秒，允许按环境收紧；负值时钟偏差按 0 计并另行监控时钟。
12. 运行指标至少覆盖：耗尽事件数、OWNER 缺失数、最近窗口拒绝数、热/归档审计数、归档失败、恢复/重放请求与执行、撤销传播时延。指标存在不等于 exporter、dashboard 和告警路由已经部署。

## 后果

### 正面

- 单个运维 Client 无法独立恢复所有权或重放撤销事件；
- 重放复用原 event ID，不绕过下游幂等语义；
- OWNER 恢复不会重新授予已禁用主体访问权；
- 热表可控增长，历史查询和 SIEM 续传不因归档中断；
- 核心实现不依赖具体 SIEM 厂商。

### 负面与风险

- 需要维护职责分离的两组 Client、scope 和凭据生命周期；
- 同数据库归档仍受同一数据库管理员权限和故障域影响；
- 游标拉取是至少一次读取，SIEM 消费者仍需按 audit ID 去重并持久化 checkpoint；
- 大规模归档、分区表、法律保留和最终删除需要基于真实容量另立设计。

## 安全、数据与隐私

所有内部控制面只接受已验证 `actor_type=SERVICE` 的 JWT。tenant 与服务身份来自 Token，目标 tenant 必须再次绑定。人员 Token、同一服务自批、过期请求、并发重复批准、非耗尽事件、存在 ACTIVE OWNER 或非 ACTIVE 恢复目标全部失败关闭。

审计与导出不包含密码、Bearer Token、client secret、prompt 或供应商正文。`incidentReference` 只允许安全标识字符且有长度上限。导出接收方必须按安全日志数据分类处理主体 ID 与 tenant ID。

## 运维与迁移

先执行新增表 migration，再发布保持控制面关闭的应用；创建彼此独立的 request、approve 和 SIEM exporter Client 后，逐项启用。不得把 request 与 approve scope 授予同一 Client。回滚应用时保留新表；已形成的操作审计、归档与请求记录不得删除。

归档首次启用前应在备份副本验证批量大小、WAL、锁等待与查询计划。SIEM 消费者先从最早游标回放并按 audit ID 去重，确认外部保存与告警后再把热保留期作为生产策略启用。

## 验收证据

2026-07-23 的实现与验收包括：

- application 测试覆盖不同服务审批、同服务拒绝、过期、tenant 隔离、非耗尽重放拒绝、存在 ACTIVE OWNER 时拒绝和跨 tenant 恢复拒绝；
- 重放测试与真实 SQL 执行证明 event ID/内容/发生时间不变，只将状态、attempt、lease 和错误重置为可重试，receipt 幂等仍成立；
- OWNER 恢复测试与真实 SQL 执行证明新目标成为唯一 ACTIVE OWNER，原 OWNER 保持 REVOKED，请求/执行阶段审计存在；
- 归档集成测试代码覆盖同事务搬迁、统一查询和稳定游标；本机 PostgreSQL 18.4 实际将 3 条热记录归档为热表 0/归档表 3，并从并集按顺序读回全部记录；
- Controller 测试覆盖 SERVICE/USER、最小 scope、tenant-bound 与 `.all`、可信 exporter subject 和重放/恢复/导出计数；
- 消费者测试证明端到端撤销 Timer 只记录首次成功处理，重复 receipt 不增加样本；
- 完整 `mvn clean test` 通过；当前动态测试总数、实际执行数和 Docker 跳过数统一记录在 [`project-status.md`](../project-status.md)；
- 本机 PostgreSQL 18.4 从两个空库成功执行 Identity 4 份、Workspace 8 份 migration，并执行前述关键事务；一次性验证库已删除。

本 ADR 的 Accepted 范围是应用控制面、数据不变量与本机 PostgreSQL 事务基线。Docker 环境中的全部 Testcontainers 执行、生产 IAM 职责分离、指标 exporter/告警、外部不可变存储、容量和多节点 SLO 仍是发布前门禁，不在本次验收中被宣称完成。

## 参考

- [NIST SP 800-53 Rev. 5.1 AC-5 Separation of Duties](https://csrc.nist.gov/CSRC/media/Projects/risk-management/800-53%20Downloads/800-53r5/SP_800-53_v5_1-derived-OSCAL.pdf)
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
- [Micrometer Histograms and Percentiles](https://docs.micrometer.io/micrometer/reference/concepts/histogram-quantiles.html)
- [PostgreSQL 18 INSERT](https://www.postgresql.org/docs/18/sql-insert.html)
- [ADR-0007：Workspace 成员生命周期、所有权与授权审计](0007-workspace-membership-lifecycle-and-audit.md)
- [ADR-0009：跨运行时 Directory 与访问撤销投递](0009-cross-runtime-access-revocation-delivery.md)
