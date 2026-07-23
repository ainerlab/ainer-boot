# Changelog

Ainer Boot 的用户可见变化记录在此文件。格式参考 Keep a Changelog，版本遵循语义化版本。

## [Unreleased]

### Added

- 建立 JDK 25、Spring Boot 4.1.0 与 Maven 多模块工程基线。
- 增加 Core、Spring、Web、Persistence、Security Starter 与独立 BOM。
- 增加 Workspace tenant 资源、成员生命周期、所有权转移和授权审计。
- 增加 AI Model Gateway、SSE、策略、预算与调用审计。
- 增加 Identity、OAuth 2.1/OIDC Authorization Server、安全 Directory 与访问撤销 outbox。
- 增加服务 JWT `actor_type`、OAuth 2.0 Client Credentials Token client、受 scope/tenant 保护的跨运行时 Directory adapter。
- 增加 PostgreSQL outbox lease/retry/exhausted relay、HTTPS 事件 transport、Workspace receipt 幂等消费者与 REVOKED 成员状态。
- 增加撤销积压、失败、重试耗尽、重复消费与实际撤销数量指标。
- 增加 Identity 耗尽事件查询与短时双人重放控制面，保留原 event ID 和下游幂等语义。
- 增加 Workspace REVOKED OWNER 双人恢复、热审计归档、热冷统一查询和稳定游标 SIEM 拉取。
- 增加撤销传播 Timer/SLO bucket、OWNER 缺失、拒绝窗口、归档量与归档失败指标。
- 增加高风险 API 的选择性 RFC 7662 在线 Token 校验、无正向缓存、inactive 401 与依赖失败 503。
- 增加专用 introspection client bootstrap、普通 client 拒绝、RFC 7009 撤销和 Identity revocation epoch。
- 为两个发行物增加受保护的 Prometheus exporter，以及无 tenant、短 Token、最小 scope 的独立 metrics client bootstrap。
- 增加 tenant 服务 Client 的一次性 secret、蓝绿轮换、显式退役和同事务审计控制面。
- 增加 Authorization Code + PKCE 的真实浏览器会话与 PostgreSQL 协议门禁。
- 建立架构决策、HTTP API、开发、测试、数据库、配置、运行和发布文档体系。

### Security

- tenant 与 subject 只从已验证 JWT 投影，不接受外部身份请求头。
- Authorization Server 使用外部 RSA PEM 密钥，业务服务验证 issuer 与 audience。
- 内部 Directory 与撤销事件端点强制 `actor_type=SERVICE`、最小 scope；事件端点还校验可信 publisher subject。
- 旧撤销事件不会影响事件发生后新建的 Workspace membership；安全禁用允许撤销 OWNER，避免为维持管理不变量继续放行禁用账号。
- 重放与 OWNER 恢复强制 SERVICE 身份、request/approve scope 分离、不同 `sub`、tenant 二次绑定与默认 15 分钟过期。
- 恢复新 OWNER 不会重新激活原 REVOKED OWNER；SIEM 导出另要求精确可信 exporter subject 并记录批次操作审计。
- 高风险请求在线校验失败关闭；专用 introspection client 不得绑定 tenant 或携带业务 scope，人员旧 Token 受 Identity 当前状态和最新撤销事件共同约束。
- `/actuator/prometheus` 只接受无 tenant 的 SERVICE JWT 与 `platform.metrics.read`；关闭业务 Resource Server 也不会匿名公开指标。
- PKCE public client 只允许 S256，拒绝缺失/`plain` challenge、错误 verifier、授权码重放和未注册回调；当前基线不向 public client 签发 refresh token。
- JDBC authorization 仅对白名单内的 Ainer 人员主体开放 Jackson 多态反序列化，认证后擦除凭证且协议记录不保存 password 属性。
- AI 审计默认不保存 prompt、模型输出、API key 或供应商错误正文。

### Fixed

- 修复 Identity access-event 新记录遗漏 `available_at`，确保状态变化与可领取 outbox 事实可在同一事务写入真实 PostgreSQL。

### Known limitations

- 当前仍为 `0.1.0-SNAPSHOT` foundation，不是生产就绪发行版。
- 在线撤销只覆盖配置的高风险路径；普通低风险自包含 JWT 仍存在自然到期窗口。
- Authorization Server 已成为高风险 API 的在线依赖；Prometheus 导出与独立抓取凭据已有代码基线，但生产高可用、容量、凭据退役轮换、dashboard 和告警路由尚未完成。
- PKCE 自动化使用测试专用 public client；生产 browser/OIDC client 控制面、登录体验、MFA 与会话治理尚未完成。
- 审计归档仍位于同一 PostgreSQL 数据库，没有 WORM/法律保留、外部不可变副本、生产 SIEM 消费者和告警路由。
- 正式 CI、制品发布、备份恢复、经真实流量验证的 SLO 与商业授权交付尚未建立。

[Unreleased]: docs/project-status.md
