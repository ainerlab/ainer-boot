# Ainer 项目状态

> 文档类型：时间敏感快照 · 状态：持续更新 · 核对时间：2026-07-23 · 工程版本：`0.1.0-SNAPSHOT`

本文只记录当前事实和验证证据，不替代架构规范与 ADR。每个里程碑结束、发布候选形成或主要风险变化时更新核对时间。

## 1. 当前阶段

Foundation M4.3 选择性在线 Token 校验工程基线已完成，下一阶段进入生产启用与安全运营验证。项目已经从纯设计文档进入可编译、可运行、可测试的 Spring Boot 4.1 多模块工程，但尚未达到生产或商业发行就绪。

## 2. 已完成

- JDK 25、Maven Reactor、独立 BOM 与 Spring Boot 4.1.0 基线；
- 无 Spring 依赖的核心错误和身份参与者契约；
- Web、Persistence、Security Starter 及自动装配测试；
- Workspace PostgreSQL 垂直切片、tenant 隔离、成员生命周期、单一 OWNER、授权审计写入与分页读取；
- AI Model Gateway 非流式/SSE、模型白名单、限流、预算、Token/费用和脱敏审计；
- Identity tenant/user/membership、安全 Directory、账号禁用、成员撤销和事务 access-event outbox；
- 独立 Authorization Server、JDBC client/authorization/consent、外部 RSA key、Client Credentials 和 JWT tenant/audience claims；
- 人员/服务 JWT `actor_type` 隔离、官方 OAuth2 Client Credentials Token 获取与缓存；
- 默认关闭的跨运行时 Directory HTTP adapter/client、tenant-bound/平台 scope 与失败关闭邀请校验；
- PostgreSQL outbox lease、重试/耗尽、HTTP relay、Workspace receipt 幂等消费者与 `REVOKED` membership；
- 撤销积压、发布失败、重试耗尽、重复消费与实际撤销数指标；
- Identity 耗尽事件查询、短时双人重放申请、tenant/服务身份隔离和操作审计；
- 无 ACTIVE OWNER 的 Workspace 双人恢复流程，恢复后原 REVOKED OWNER 保持撤销；
- Workspace 授权审计热保留/同库归档、热冷统一查询、稳定游标 SIEM 拉取与导出审计；
- 撤销首次成功传播 Timer/SLO bucket，以及 OWNER 缺失、拒绝窗口、热/归档数和归档失败指标；
- Resource Server 高风险路径/方法选择性 RFC 7662 在线校验，无 active 正向缓存，inactive 401、依赖失败 503；
- Authorization Server 专用 introspection client 隔离、RFC 7009 撤销、官方 JDBC authorization 包装与协议级普通 client 拒绝；
- Identity 当前状态与最新 access-event 组成的人员 Token revocation epoch，以及在线校验放行/拒绝/失败/延时指标；
- ADR-0001 至 ADR-0011、架构、HTTP API、安全、数据、测试、运行与发布基础文档。

## 3. 最近验证证据

2026-07-23 执行完整 `mvn clean test`：14 个 Reactor 模块成功，144 个测试，0 failure，0 error；其中 98 个实际执行通过，46 个 Testcontainers 测试因当前机器没有 Docker 而明确跳过。

同日使用本机真实 PostgreSQL 18.4 从空库执行 Identity 四份、Workspace 八份全量 migration。除 M4.1 的 outbox 领取/撤销证据外，本轮实际执行了耗尽原事件双人重放事务、REVOKED OWNER 提升新 OWNER 事务、安全操作审计约束，以及授权审计归档 CTE、热冷统一查询和导出审计。原事件内容保持不变，旧 OWNER 保持 REVOKED，归档后热表 0/归档表 3；两个一次性数据库均已删除。loopback HTTP 测试实际验证 Client Credentials Token 获取/缓存和 Bearer 事件发布。

M4.3 另使用本机 PostgreSQL 18.4 从空库执行 Authorization Server 五份 migration 并实际启动发行物。协议 smoke 证明普通 client introspection 返回 401、专用 client 对新 Token 返回 `active=true`、RFC 7009 revocation 返回 200 且随后 `active=false`。真实 JDBC 往返同时暴露并修复了 Boot 4/Jackson 3 对 JDK 私有不可变 claim 集合的反序列化拒绝。对 5,000 条合成 access event 的 revocation epoch 查询使用 `idx_ainer_identity_access_event_subject` Index Only Scan，实测执行约 0.036 ms；旧/等于 epoch 的 Token inactive，事件后的 Token active，membership 禁用后当前身份 inactive。一次性数据库和 RSA 测试密钥目录均已删除。

该结果证明当前源码基线可构建，且关键 migration 已在真实 PostgreSQL 执行；它不替代 Docker 环境中的完整发布候选测试，也不证明生产容量、备份恢复或高可用。

## 4. 已知缺口

### 访问控制

- 选择性在线校验只覆盖配置的高风险 API；普通低风险自包含 JWT 仍有自然到期窗口；
- Authorization Server 已成为高风险 API 在线依赖，但尚未完成生产高可用、容量、专用凭据轮换、dashboard 与告警；
- 重放与 OWNER 恢复已做服务 `sub` 分离，但生产 IAM 仍需证明凭据由不同人员/职责保管；
- 授权审计归档仍位于同一数据库，没有 WORM、数字签名、法律保留和最终删除策略；
- SIEM 只有默认关闭的拉取 API，没有部署外部消费者、不可变副本或告警路由；
- Directory/relay/consumer 与 M4.2 控制面默认关闭，尚未在真实多节点环境完成容量、故障注入和滚动 30 天撤销 SLO 验证。

### Identity 与 OAuth

- 人员账号、tenant 和 Client 管理控制面不完整；
- Authorization Code + PKCE 端到端、MFA、密钥轮换和恢复流程未完成；
- tenant ownership transfer 的 Identity 控制面尚未完成。

### AI 平台

- 限流仍是单进程基线，未形成集群级一致性；
- provider 凭据托管、指标、trace、输出策略、评测、RAG 与 Agent runtime 尚未完成；
- 供应商兼容面仅覆盖当前 OpenAI-compatible 最小协议。

### 工程与运营

- 没有正式 CI、制品签名、SBOM、发布仓库和自动部署；
- 没有具名模块维护者矩阵、`CODEOWNERS` 和正式审查责任分配；
- 没有生产备份恢复、容量测试、正式错误预算/告警路由和灾难恢复演练；
- 没有稳定版兼容政策、商业许可证文本和付费产品交付系统；
- Testcontainers 在当前本地机器因 Docker 缺失会跳过。

## 5. 下一里程碑

M4.3 的代码和协议基线已经落地，下一里程碑优先完成生产启用：Authorization Server 多实例高可用与容量证据、专用 introspection 凭据轮换、Resource Server 灰度与安全降级审批、指标 exporter/dashboard/告警和多节点故障验证。同时把 M4.2 的 SIEM 拉取与操作审计接入生产级 IAM 职责分离和外部不可变存储。

完成条件包括：发布候选环境的 Testcontainers 测试不跳过；高风险 online validation 在目标容量与故障注入下满足确定的延时/可用性目标；request/approve 与 introspection 凭据在运营上真正分离并可轮换；外部审计副本可验证、可恢复；初始撤销 SLO 经多节点证据修正后形成正式错误预算。随后继续补齐人员账号/Client 控制面、Authorization Code + PKCE、MFA、tenant ownership transfer 与密钥轮换。

## 6. 更新规则

- 只写已经有代码、migration、测试或明确证据的完成项；
- 测试数量和版本变动后更新本页，不散落到长期规范；
- 缺口关闭时同时更新对应 ADR、专题文档和 Changelog；
- 发布版本形成后保留历史 Changelog，本页只保留最新状态。
