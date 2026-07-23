# ADR-0009：跨运行时 Directory 与访问撤销投递

- 状态：Accepted
- 日期：2026-07-23
- 决策者：Ainer 核心维护者
- 取代：无
- 被取代：无

## 背景

ADR-0008 已在 Identity 数据库建立安全 Directory 应用契约，以及与用户禁用/成员撤销同事务提交的 access-event outbox，但两个运行时尚未接线：Workspace 无法跨进程验证邀请目标，outbox 也不会自动收敛 Workspace 中已经存在的资源成员关系。

直接共享数据库会破坏 Identity 与 Workspace 的数据所有权。使用普通异步事件会在进程崩溃时丢失通知。只在 HTTP payload 传 tenant 又会把内部接口变成可越权选择租户的入口。

## 决策驱动因素

- Identity 与 Workspace 保持独立数据库所有权；
- 内部接口使用标准 OAuth 2.0 Bearer JWT，不自造签名请求头；
- 事件至少一次投递，重复、重试和进程崩溃不能扩大权限；
- 撤销必须单调收紧访问权，不能因 OWNER 不变量而继续放行已禁用账号；
- 默认配置安全关闭，缺失服务凭据时不能匿名启用内部接口；
- 不在本阶段锁定 Kafka、RabbitMQ 或云消息产品。

## 备选方案

### 共享 Identity 表

实现简单，但会把业务运行时绑定到身份数据库 schema，无法独立发布或拆库，拒绝。

### 进程内 Spring 事件

不能覆盖两个独立发行物，也无法在提交后崩溃时恢复，拒绝作为可靠撤销通道。

### 立即引入消息中间件

可以实现投递，但当前没有容量、拓扑和产品交付证据支持具体产品选择。本阶段先实现与 transport 无关的 outbox 领取/确认端口和 HTTP transport，保留以后替换空间。

### HTTP 直接调用但不使用 outbox

Identity 事务会被远程可用性绑定，且提交与调用之间仍有丢失窗口，拒绝。

## 决策

1. `ainer-authorization-server` 提供默认关闭的 Identity Directory 内部 HTTP adapter。调用者必须持有已验证 JWT、`actor_type=SERVICE` 与 `identity.directory.read` 或 `identity.directory.read.all` scope。
2. tenant-bound scope 只能查询 Token `tenant_id` 对应租户；只有显式授予 `identity.directory.read.all` 的平台服务才能在路径中选择其他 tenant。两种模式都必须是服务身份，普通人员 Token 不允许调用。
3. `ainer-server` 提供可选的 Directory HTTP client，并在启用时接入 Workspace 邀请用例。远程查询返回 NOT_ACTIVE 时拒绝创建邀请；远程身份或传输失败按 503 关闭失败。未启用 client 时继续使用 ADR-0007 的 PENDING + 目标主体本人接受边界。
4. Identity outbox 增加 `available_at`、lease owner/expiry、尝试次数和稳定错误码。relay 使用 PostgreSQL `FOR UPDATE SKIP LOCKED` 并发领取小批量事件，网络发布发生在数据库事务之外。
5. 发布成功后按 event ID 与 lease owner 标记 PUBLISHED；失败后清除 lease、设置下一次可用时间并递增重试。达到最大尝试次数的 FAILED 事件不再自动领取，保留用于告警和人工重放。
6. 首个 transport 使用 HTTPS JSON + OAuth 2.0 Client Credentials。Token 获取、缓存和到期刷新由共享 service-token client 完成；client secret 不进入 outbox、错误或日志。
7. Workspace 内部消费端点要求 `actor_type=SERVICE`、`identity.access-events.publish` scope，并可额外限制可信 publisher subject。事件 payload tenant 只对这个受信任的 Identity publisher 生效，不对普通租户客户端开放。
8. Workspace 在同一事务先按 event ID 插入 receipt，再把同 tenant、同 subject、且创建时间不晚于事件发生时间的 PENDING/ACTIVE membership 变为 REVOKED。receipt 重复时返回幂等成功，不重复修改。
9. REVOKED 不参与任何资源授权。Identity 用户禁用可以撤销 Workspace OWNER，使 Workspace 暂时没有 ACTIVE OWNER；安全处置优先于可管理性，后续通过专用恢复/所有权转移流程处理。
10. 本阶段事件只有撤销语义，没有恢复语义。乱序撤销不能影响事件发生后新建的 membership；未来增加恢复事件必须携带可比较的访问 epoch 或版本并另立决策。
11. Directory adapter、relay 和 consumer 均默认关闭。启用时缺少 HTTPS URL、client ID/secret、可信 publisher 或不安全配置必须启动失败；仅自动化测试可显式允许 HTTP。

## 后果

### 正面

- Identity 与 Workspace 不共享数据库也能形成可恢复的撤销闭环；
- 重复投递和进程重启不会恢复权限或重复产生副作用；
- transport 可在未来替换为消息系统，领域事件和幂等消费语义不变；
- 内部 Directory 与事件端点具备明确服务身份、scope 和 tenant 授权。

### 负面与风险

- HTTP transport 会在 Workspace 不可用时形成 outbox 积压；
- Client Credentials secret 与 Token 获取链路需要独立轮换和可观测性；
- OWNER 被安全撤销后可能没有在线管理员，需要专用恢复流程；
- 自包含 JWT 仍可能在过期前调用其他尚未消费撤销事件的资源服务，本决策不宣称强实时全局撤销。

## 安全、数据与隐私

Directory 只返回 tenant、subject、username、display name 与 role。事件只包含 event ID、type、tenant、subject、payload version 和时间。两类接口都不得返回密码哈希、Token、client secret、自由文本原因或供应商正文。

服务 JWT 必须验证签名、issuer、audience、有效期和 scope。内部 URL 仍需受控网络与 TLS；OAuth Token 不是取消网络隔离、限流和审计的理由。

## 运维与迁移

先部署包含 Workspace receipt/membership REVOKED migration 的消费者，再启用 Identity relay。Directory client 可以独立启用。回滚 relay 只停止新投递，不删除 outbox；回滚消费者应用时 schema 保持向后兼容。

运维至少观测：PENDING/FAILED/PUBLISHED 数量、最老待发布时间、发布结果、重试耗尽、消费重复和实际撤销 membership 数。当前 HTTP transport 不自动删除历史 outbox/receipt。

## 验收证据

2026-07-23 已形成以下实现与证据：

- Authorization Server 集成测试覆盖 Client Credentials `actor_type=SERVICE`、人员 Token 拒绝、tenant-bound 与平台级 Directory 查询隔离；
- Identity PostgreSQL 集成测试覆盖 outbox 领取、lease 过期、重试上限、lease owner 和发布确认；
- Workspace PostgreSQL 集成测试覆盖重复事件、跨 tenant、OWNER 撤销、旧事件不影响后建成员和事务回滚；
- loopback HTTP 合约测试真实执行 Client Credentials Token 获取/缓存与事件 Bearer 发布，不依赖商业身份服务；
- relay 指标区分 PENDING、可重试 FAILED、重试耗尽、最老就绪事件年龄和发布结果；消费者指标记录接收、重复与实际撤销数；
- 本机 PostgreSQL 18.4 从空库执行 Identity 三份和 Workspace 六份 migration，并实际运行 `FOR UPDATE SKIP LOCKED` 领取、发布确认、OWNER 撤销、跨 tenant 与乱序保护 SQL；一次性数据库已清理；
- 完整 Reactor 测试结果记录在 [`project-status.md`](../project-status.md)。当前机器没有 Docker 时 Testcontainers 测试会明确跳过，发布候选仍必须在 Docker 可用环境实际执行。

据此接受本决策。它完成的是跨运行时资源关系最终撤销，不代表自包含 JWT 已实现强实时全局撤销。

## 参考

- [Spring Security OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Authorization Server Configuration Model](https://docs.spring.io/spring-authorization-server/reference/configuration-model.html)
- [PostgreSQL SELECT locking clause](https://www.postgresql.org/docs/current/sql-select.html)
- [ADR-0008：Identity Directory 与访问撤销传播边界](0008-identity-directory-and-access-revocation.md)
