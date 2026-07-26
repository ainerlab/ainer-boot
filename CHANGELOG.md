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
- 增加默认关闭的 Passkey/WebAuthn 协议基础、UV-required options、条件人员门禁、JDBC
  credential 生命周期、软撤销、最后凭证保护和审计。
- 增加默认关闭的 Passkey 恢复：自助恢复码（高熵、一次性、bcrypt 哈希、per-subject 失败锁定）
  与管理员双人恢复（复刻 Workspace owner recovery 的 request/approve 骨架）。恢复码赎回或管理员
  恢复批准会吊销目标账号全部 ACTIVE Passkey，越过最后凭证保护，用户可重新 bootstrap。
- 增加默认关闭的登录限速（node-local 固定窗口，按客户端 IP 节流登录类端点，超额 429
  `AINER.COMMON.RATE_LIMITED` + `Retry-After`）与默认 `optional` 的受控首次 Passkey enrollment
  （`require-invite` 模式下首登需操作员预授权，成功后授权置 `CONSUMED`，replacement 不受影响）。
- 增加默认关闭的 resource server step-up 授权策略（`RecentStrongAuthenticationFilter`：高风险
  路径要求人员 Token 的 `amr` 含强因子且 `auth_time` 在 `max-auth-age` 内，否则 403
  `AINER.SECURITY.RECENT_STRONG_AUTHENTICATION_REQUIRED`）。
- 增加 Authorization Server 上的 tenant 成员列表、加入、角色变更和软移除 API；授权同时要求
  USER actor、`tenant.members.read|write`、可信 tenant claim 与数据库 ACTIVE OWNER/ADMIN，
  所有实际写入同事务记录成员安全审计。
- 增加默认关闭的首个平台 tenant/OWNER bootstrap：完整状态严格幂等、部分占用失败关闭、不会
  覆盖密码，并以 PostgreSQL 事务 advisory lock 串行化多实例初始化。
- 增加 `dev` profile 下默认关闭的 `ainer-admin-dev` public client 与双用户开发 fixture，固定
  Authorization Code + PKCE S256、Ainer Admin 回调、四个最小 scope、default tenant 和无
  Refresh Token 策略。
- 增加当前 access token 自助撤销与 Ainer Admin 成员 API active gate；成员请求逐次读取官方
  authorization，inactive 返回 401，在线依赖故障返回 503 且不降级。
- 增加 `ainer-admin-v1.yaml`、固定 Maven TypeScript SDK 生成入口，以及同一 browser session 的
  PKCE → 成员治理 → revoke → OIDC logout PostgreSQL 端到端门禁。
- 增加 `/ainer-admin/` 同源反代、登录回调、SDK 装配、退出顺序与开发初始化的长期集成手册。
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
- Passkey 生产 Origin 只允许 HTTPS 并受 RP ID scope 限制；已登记账号的 OAuth authorization
  和凭证管理要求 WebAuthn 因子，最后一个 ACTIVE credential 不允许自助删除。
- Passkey 恢复/enrollment 的目标 `(tenant_id, subject_id)` 必须属于 ACTIVE 默认 Identity
  membership；成员管理的 scope 不能替代实时 tenant 资源角色，通用接口不能修改 OWNER。
- Resource Server 必须从 JWT 取得显式 `actor_type=USER|SERVICE`；step-up 仅认可 USER，
  匿名请求保留标准 401，未来 `auth_time` 不能超过受控 clock skew。
- Ainer Admin browser client 无 secret、只允许 PKCE S256；成员 API 与当前 Token 撤销均要求
  官方 authorization 仍 active，同源登录、Token 交换和 logout 复用同一 browser session。
- AI 审计默认不保存 prompt、模型输出、API key 或供应商错误正文。

### Fixed

- 修复 Identity access-event 新记录遗漏 `available_at`，确保状态变化与可领取 outbox 事实可在同一事务写入真实 PostgreSQL。
- 修复 OAuth2 授权记录无法反序列化 Passkey 用户的 `WebAuthnAuthentication` 主体：显式注册
  `WebauthnJacksonModule` 并把其协议主体类型加入多态白名单，否则任何 Passkey 用户走授权码
  流程都会在换 Token 时失败。
- 修复 Passkey 登录用户签发的 access token 缺少 Ainer claims：token customizer 此前只识别
  密码登录的 `AinerUserDetails` 主体，不识别 WebAuthn 的 `PublicKeyCredentialUserEntity`；
  现按 username 解析为 `AinerUserDetails`，Token 正确携带稳定 `sub`、`tenant_id`、`roles`、
  `amr=pwd,mfa,pop` 与 `auth_time`。
- 修复凭证管理端点 `/webauthn/register/**` 未被条件 MFA 门禁保护：Spring Security WebAuthn
  协议 filter 在授权 filter 之前短路，已登记账号原本只用密码即可登记或删除凭证；新增
  `AinerPasskeyCredentialManagementGateFilter` 在协议 filter 之前显式运行同一
  `AuthorizationManager`，缺因子时重定向到登录入口，未登记账号的首次 bootstrap 不受影响。
- 修复 Passkey 恢复与 enrollment 操作只校验请求中的 tenant/subject、未验证目标 Identity
  membership 的问题；数据库复合外键与应用 ACTIVE membership guard 共同阻止跨 tenant 目标。
- 修复登录限流漏掉 WebAuthn options、context path 下匹配不稳定以及 429 非统一错误体；现仅匹配
  配置的 POST PathPattern，返回统一 envelope、`Retry-After`/no-store 并记录 allow/deny 指标。
- 修复 step-up 把匿名请求提前改写为 403、未区分 SERVICE、直接使用系统时钟且接受任意未来
  `auth_time`；现由认证链保留 401，并使用可注入 `Clock` 与有界 clock skew。

### Known limitations

- 当前仍为 `0.1.0-SNAPSHOT` foundation，不是生产就绪发行版。
- 在线撤销只覆盖配置的高风险路径；普通低风险自包含 JWT 仍存在自然到期窗口。
- Authorization Server 已成为高风险 API 的在线依赖；Prometheus 导出与独立抓取凭据已有代码基线，但生产高可用、容量、凭据退役轮换、dashboard 和告警路由尚未完成。
- PKCE 自动化除通用测试 client 外已覆盖固定的 Ainer Admin 开发 public client；Passkey 代码
  主线已有虚拟 authenticator 签名 ceremony、
  受控 enrollment、恢复与 step-up，但尚缺恢复通知、主流真实设备矩阵、共享限流与多节点会话；
  生产 browser/OIDC client 控制面和登录体验也尚未完成。
- tenant ownership transfer、平台级 tenant/user 管理和成员管理 UI 尚未完成。
- 审计归档仍位于同一 PostgreSQL 数据库，没有 WORM/法律保留、外部不可变副本、生产 SIEM 消费者和告警路由。
- 正式 CI、制品发布、备份恢复、经真实流量验证的 SLO 与商业授权交付尚未建立。

[Unreleased]: docs/project-status.md
