# ADR-0021：供应通知最终投递回执边界

- 状态：Proposed
- 日期：2026-07-26
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无

## 背景

M4.8A 已把租户供应通知写入 Identity outbox，并通过独立 tenantless OAuth2 Client Credentials
向 HTTPS 通知网关发布。网关返回 2xx 后，Ainer 把 outbox 标记为 `PUBLISHED` 并销毁本地可解密
payload。

`PUBLISHED` 只证明通知网关已持久接收，不能证明邮件服务器、短信运营商或站内信终端完成交付。
如果把二者合并为一个状态，运营面会错误地把网关接收成功解释为用户可达，无法区分：

- Ainer 到网关的传输失败；
- 网关到供应商的派发失败；
- 供应商确认交付；
- 退信、无效号码、策略拦截等终态失败。

同时，Ainer 不应把邮件、短信供应商 SDK 和回执方言引入 Identity。供应商差异应由外部通知网关
归一化，Identity 只接收与供应请求相关的最小终态事实。

## 决策驱动因素

- 保持 `PUBLISHED` 的精确定义，不夸大最终送达证据；
- 回调必须可认证、tenantless、最小权限、可审计和幂等；
- 供应商重复、乱序或重放回调不能制造多个矛盾终态；
- Identity 不保存通知正文、联系地址、供应商原始 body 或敏感错误详情；
- 外部通知网关负责供应商协议、模板和原始回执映射；
- 在没有真实网关地址、凭据和供应商沙箱时，不伪造生产联调结论。

## 备选方案

### 方案 A：继续把 HTTP 2xx 当成最终送达

实现最少，但语义错误，无法表达退信或供应商终态失败。拒绝。

### 方案 B：Identity 直接集成每个邮件/短信供应商

可以读取最完整的供应商回执，但会让安全核心依赖供应商 SDK、Webhook 方言和模板系统，扩大秘密、
网络与升级面。拒绝。

### 方案 C：只在指标系统保存回执

便于告警，但指标不是权威业务事实，无法稳定幂等、审计或回答单条通知状态。拒绝。

### 方案 D：外部网关归一化终态，Identity 保存最小回执事实

网关负责供应商差异，Identity 以 notification UUID 关联，使用独立 OAuth scope、gateway client
白名单和幂等事件 ID 接收终态回执。采用。

## 决策

1. outbox `publication_status=PUBLISHED` 继续只表示外部通知网关已持久接收，不改名为
   `DELIVERED`。
2. 外部网关通过
   `POST /internal/identity/tenant-provisioning-notification-receipts`
   回传规范化终态。
3. 第一版只接受：
   - `DELIVERED`：供应商确认已向目标系统完成交付；不表示自然人已阅读；
   - `FAILED`：网关确认本次通知无法继续交付，退信、无效地址和供应商拒绝均映射为有限
     `failureCode`。
4. 回调只携带 `eventId`、`notificationId`、`status`、`occurredAt` 和可选 `failureCode`。
   不接收或保存正文、联系地址、供应商原始 payload、Token、secret 或自由文本错误。
5. 回调调用方必须是：
   - `actor_type=SERVICE`；
   - 无 `tenant_id`；
   - 持有 `identity.provisioning-notifications.receipts.write`；
   - `sub` 位于精确 gateway client ID 白名单。
6. 回调 credential 与 outbound relay、平台 identity operator、metrics、introspection 和其他
   运维 credential 分离；默认关闭，通过独立 bootstrap 或正式 client 生命周期配置。
7. `(gateway_client_id, gateway_event_id)` 是上游幂等边界；同一 notification 只接受一个终态。
   相同事件和相同内容回放返回原结果，不重复写入；相同键但不同内容或矛盾终态返回 409。
8. 只有 outbox 已经是 `PUBLISHED` 的 notification 才能登记回执。`PENDING`、`FAILED`、
   `CANCELLED` 或不存在的 notification 失败关闭。
9. 回执 ID 使用 PostgreSQL 18 `uuidv7()` 并由数据库校验。回执保存 gateway client、受限事件 ID、
   终态、受限失败码、供应商发生时间、Ainer 接收时间和 request ID。
10. `occurredAt` 可以早于接收时间以容纳异步回调，但不能超过 Ainer 接收时间五分钟以上；
    该容差不替代生产 NTP 监控。
11. Identity 只保存回执权威事实，不保存供应商原始日志。原始供应商事件、模板、退信详情和合规
    留存由外部通知域负责。
12. 真实最终送达证据必须包含真实外部网关、供应商沙箱或正式通道、回执映射、OAuth credential、
    重放和失败演练。仅通过本地 HTTP stub 或数据库测试不能宣称生产送达闭环。

## 数据、安全与运维影响

- 新增 Identity-owned `ainer_identity_notification_delivery_receipt` 表；
- notification outbox 与 receipt 是一对零或一关系，outbox 仍保留传输事实；
- receipt API 和 gateway client bootstrap 默认关闭；
- `DELIVERED`、`FAILED` 首次写入分别产生低基数 Counter，重复回放不重复计数；
- 收到未知 notification、非 `PUBLISHED` 状态、冲突终态或未授权 client 时应告警，但日志不得包含
  联系地址或原始回执 body；
- 回滚通过关闭回调端点完成，已登记回执作为历史事实保留，不物理删除。

## 验收证据

- PostgreSQL 18 从空库执行 migration，并验证 UUIDv7、唯一键、状态与失败码约束；
- 单元测试覆盖参数校验、未来时间、相同回放、幂等冲突和矛盾终态；
- 随机端口 HTTP 测试覆盖匿名、错误 scope、tenant-bound SERVICE、未知 gateway client 和正常
  gateway 回调；
- 真实事务测试覆盖只有 `PUBLISHED` 可写、回放不重复、不同 gateway event 不制造第二终态；
- 响应和数据库列检查证明不包含正文、联系地址、secret、Token 或供应商原始 body；
- 完整 Maven 测试及真实外部网关联调证据分别记录，不能互相替代。

## 迁移

先发布 schema、scope、默认关闭的 endpoint 与 bootstrap；在测试环境为外部网关创建独立 client，
配置精确白名单，验证发送、`DELIVERED`、`FAILED`、重放和告警后再灰度启用。没有回执能力的现有
网关可以继续使用 outbound relay，但其通知只能停留在“网关已接收”，不得显示为最终送达。

## 参考

- [ADR-0005：Identity 与 OAuth 2.1 安全基线](0005-identity-and-oauth2-security-baseline.md)
- [ADR-0013：受审计 OAuth tenant 服务客户端生命周期](0013-audited-oauth-service-client-lifecycle.md)
- [ADR-0019：Identity 供应、租户上下文与所有权治理](0019-identity-provisioning-tenant-context-and-ownership-governance.md)
- [ADR-0020：PostgreSQL Native-First Greenfield 数据基线](0020-postgresql-native-greenfield-baseline.md)
