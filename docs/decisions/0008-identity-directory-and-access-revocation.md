# ADR-0008：Identity Directory 与访问撤销传播边界

- 状态：Accepted
- 日期：2026-07-23

## 背景

ADR-0007 通过 PENDING 邀请和受邀主体本人接受，保证未经确认的 subject 不能直接获得 Workspace 权限，但管理员在创建邀请前仍缺少一个安全的用户选择来源。与此同时，Identity 的账号或租户成员关系被禁用后，只能阻止后续登录，已经签发的自包含 JWT 与下游资源关系不会自动即时失效。

直接让 Workspace 查询 Identity 表会破坏数据所有权，也无法在 Authorization Server 与业务 Resource Server 使用独立数据库时工作。仅发送进程内 Spring 事件又会在进程崩溃或未来拆分服务时丢失撤销通知。

## 决策

1. Identity 拥有 Directory 查询契约。查询只返回 ACTIVE tenant、ACTIVE user、ACTIVE membership 的安全投影：tenant、subject、username、display name 与 tenant role；密码哈希、账号锁定细节和 OAuth 协议数据不得进入 Directory 结果。
2. Directory 的 tenant 必须由受信任调用适配器从认证上下文或服务授权中确定。应用契约显式接收 tenant ID，但公共 HTTP API 不允许客户端越权选择任意 tenant。
3. Workspace 不读取 Identity 表，也不依赖 Identity Service 实现。管理员选择邀请目标时应使用 Directory；在跨运行时 Directory adapter 完成前，PENDING + 目标主体本人接受仍是最终授权边界，不能把尚未接线的查询描述成强同步校验。
4. Identity 提供账号禁用与租户成员撤销事务。账号禁用会阻止该用户后续取得人员 access token；成员撤销会让对应 tenant membership 不再进入账号/Directory 查询。
5. 每次实际影响访问权的状态变化，都在同一数据库事务写入 `ainer_identity_access_event` outbox。事件只包含 event ID、类型、tenant ID、subject ID、payload version 与时间，不包含用户名、显示名、密码、Token 或自由文本原因。
6. outbox 事件至少一次投递，消费者按 event ID 幂等。发布状态、尝试次数与最后稳定错误码属于投递控制面；不能把供应商异常正文写入 outbox。
7. 本阶段只建立可靠事件事实和查询端口，不预设 Kafka、RabbitMQ 或云消息产品。真正外部化时可使用 Spring Modulith Event Publication Registry 或独立 outbox relay，但必须保留原事务写入与幂等语义。
8. 自包含 JWT 的撤销不是删除数据库行即可完成。RFC 7009 的 token revocation 不能让所有离线 JWT Resource Server 自动获得实时状态；现阶段使用短生命周期 access token，并由撤销事件收敛下游资源关系。需要强实时撤销的发行版必须增加 introspection、共享 deny-list/revocation epoch 或等价在线检查，并另立决策。
9. Identity OWNER membership 的普通撤销必须等待专用 tenant ownership transfer。全局账号禁用属于安全处置，可以禁用 OWNER，但必须产生撤销事件并进入后续人工恢复/转移流程。

## 本阶段交付

- ACTIVE tenant member 的精确查询与有限目录搜索；
- 用户禁用、非 OWNER tenant membership 撤销；
- 与状态变化同事务写入的 access event outbox；
- Workspace 授权审计的 tenant/workspace 绑定分页查询；
- 单元测试、PostgreSQL migration 与 Testcontainers 集成测试。

跨运行时 Directory HTTP adapter、outbox relay、Workspace 撤销消费者、tenant ownership transfer、审计告警和强实时 JWT 撤销不在本次假装完成。

## 后果

正面：

- 用户选择数据由 Identity 所有，并形成不泄露凭据的稳定投影。
- 禁用和撤销事实不会因应用进程在提交后崩溃而消失。
- 模块化单体和未来服务化可以共享同一业务语义，而不共享数据库。

代价与限制：

- outbox 在 relay 和消费者落地前只代表可靠待发布事实，不代表下游已完成撤销。
- 短生命周期 JWT 仍存在到期前窗口；高风险部署必须采用在线撤销策略。
- Directory 的公共控制面仍需服务身份、tenant 授权、限流和查询审计。

## 参考

- [Spring Authorization Server 支持的协议端点](https://docs.spring.io/spring-authorization-server/reference/overview.html)
- [RFC 7009：OAuth 2.0 Token Revocation](https://www.rfc-editor.org/rfc/rfc7009)
- [Spring Modulith：Event Publication Registry](https://docs.spring.io/spring-modulith/reference/events.html)
- [RFC 7643：SCIM Core Schema](https://www.rfc-editor.org/rfc/rfc7643)
