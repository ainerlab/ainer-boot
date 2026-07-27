# Ainer 项目状态

> 文档类型：时间敏感快照 · 状态：持续更新 · 核对时间：2026-07-27 · 工程版本：`0.1.0-SNAPSHOT`

本文只记录当前事实和验证证据，不替代架构规范与 ADR。每个里程碑结束、发布候选形成或主要风险变化时更新核对时间。

## 1. 当前阶段

Foundation 已完成 M4.8A 与 Ainer Admin 后端融合：M4.7 tenant 成员管理与首租户严格 bootstrap
已落地，平台
预配申请又具备幂等摘要、并发预留、短时一次性 grant、加密 notification outbox、已有用户本人
接受、原子创建 ACTIVE tenant/OWNER、显式取消、tenant/user 安全分页，以及默认关闭的
OAuth2/HTTPS 通知网关 relay 和 provider-neutral `DELIVERED|FAILED` 终态回执接收基线。
固定 Ainer Admin 开发 browser client、开发身份、Token 自助撤销、成员 API active gate、
OpenAPI/SDK 与完整浏览器链路测试也已纳入同一分支。
M6 已在独立候选分支实现由 Authorization Server 承载的品牌 `/login`，固定消费 Studio
视觉合同 1.0.0，并保持 CSRF、SavedRequest、PKCE/OIDC 与条件 MFA 协议边界；候选尚未部署。
`REQUESTED` 仍不是可授权身份事实；真实外部通知网关/供应商联调、供应商回执映射、最终送达证据、
生产限速/告警尚未完成，0-skipped 仍需在正式发布候选环境重复执行。当前工程是可编译、可运行、
可用真实 PostgreSQL 验证的 Spring Boot 4.1 多模块基线，但尚未达到生产或商业发行就绪。

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
- 两个发行物的 Prometheus registry 与 exporter、tenantless SERVICE + `platform.metrics.read` 授权，以及独立一分钟 metrics client bootstrap；
- 默认关闭的 tenant 服务 Client 控制面：一次性服务端随机 secret、scope 白名单、tenantless
  operator 双重授权、蓝绿新 ID 轮换、显式退役和同事务操作审计；
- 退役感知 registered client/authorization 包装：阻止新 Token、历史 Token introspection
  inactive，同时保留官方 JDBC authorization 历史可读性；
- 独立的一分钟 client-control operator bootstrap，以及配置失败关闭和真实 PostgreSQL 生命周期
  门禁；
- 测试专用 public client 的 Authorization Code + PKCE S256 真实浏览器会话门禁：登录、
  authorization code 单次交换、错误 verifier、缺失/`plain` challenge 和非法 redirect URI 拒绝；
- JDBC authorization 的 Ainer 人员 principal 精确 Jackson 白名单、认证后凭证擦除、协议记录
  password 排除，以及 public client 无 refresh token 基线；
- 默认关闭的 Spring Security Passkey/WebAuthn、严格 RP/Origin/UV 配置、按账号条件 MFA、
  JDBC 协议与 ACTIVE/REVOKED 生命周期、软撤销、并发最后凭证保护和操作审计；
- 人员 Token 的标准 `amr` / `auth_time` 基线，以及 browser chain 精确 factor accumulation，
  不改变 Client Credentials、internal API 和 metrics 安全链；
- Passkey 真实签名 ceremony 端到端门禁：虚拟 authenticator 驱动 registration/authentication
  签名闭环，Passkey 用户走授权码流程后 access token 携带 `amr=pwd,mfa,pop`、`auth_time`、
  稳定 `sub`/`tenant_id`/`roles`；
- 凭证管理端点（`/webauthn/register/**`）真正受条件 MFA 门禁保护，已登记账号在缺因子时
  无法登记或删除凭证；
- 默认关闭的 Passkey 恢复：自助恢复码（首次登记后签发，高熵一次性、bcrypt 哈希、per-subject
  失败锁定），赎回即吊销该账号全部 ACTIVE Passkey 并写安全操作审计；管理员双人恢复复刻
  Workspace owner recovery 的 request/approve 骨架，approve 吊销目标全部 ACTIVE Passkey；
- 默认关闭的登录限速（node-local 固定窗口，按客户端 IP 节流 `/login`、`/login/webauthn`，
  超额 429）与默认 `optional` 的受控首次 Passkey enrollment（`require-invite` 模式下首登需操作员
  预授权，成功后授权置 CONSUMED，replacement 不受影响）；
- 默认关闭的 resource server step-up 授权策略（`RecentStrongAuthenticationFilter`：高风险路径
  要求人员 Token 的 `amr` 含强因子且 `auth_time` 在 `max-auth-age` 内，否则 403
  `AINER.SECURITY.RECENT_STRONG_AUTHENTICATION_REQUIRED`），首次让 Ainer 签发的 `amr`/`auth_time`
  真正参与授权决策；
- 恢复/enrollment 目标使用 ACTIVE default Identity membership 应用门禁与复合外键双重 tenant 绑定；
  登录限流补齐 WebAuthn options、统一 429/no-store/指标，step-up 补齐匿名 401、USER 限定与
  有界 clock skew；
- Identity 权威运行时提供 tenant 成员列表、加入、角色变更和软移除 API，使用 USER scope + 可信
  tenant claim + 实时 ACTIVE OWNER/ADMIN 四重门禁，所有实际写入同事务审计且不允许通用接口修改 OWNER；
- 首个平台 tenant/OWNER 使用默认关闭、严格幂等、不覆盖密码且由 PostgreSQL transaction advisory
  lock 串行化的 Authorization Server bootstrap；业务 Server 不装配 Identity migration；
- Ainer Admin `dev` public client、双用户 fixture、当前 access token 自助撤销、成员 API
  active gate、`ainer-admin-v1.yaml` 与 TypeScript SDK 生成入口；
- 同一 `ainer-admin-dev` browser session 的 PKCE → default tenant → 成员列表/添加/双向改角色/
  软移除 → revoke → OIDC logout 真实 PostgreSQL 端到端门禁；
- Ainer 品牌服务端登录页：Studio 合同与 Tokens 固定哈希、四种服务端状态、服务端 CSRF、
  SavedRequest、统一凭据错误、HTML 429/`Retry-After`、明确认证基础设施异常 503、精确 CSS
  代理，以及不改变 WebAuthn/MFA filter 的兼容基线；
- 默认关闭的平台 Identity 预配申请控制面：tenantless SERVICE、tenant/user 成对 read/write
  scope、精确 operator 白名单、独立一分钟 operator bootstrap、operator 级幂等、规范化摘要、
  tenant code/新 username 并发预留、惰性过期、同事务平台审计和安全状态查询；
- 平台预配与首租户 bootstrap 共享 tenant code/username advisory lock；申请只预生成
  PostgreSQL UUIDv7 tenant/subject，不提前写核心 tenant/user/membership；
- 新用户短时限次 activation grant（256-bit secret、数据库只存 SHA-256 摘要）、带 key version
  的 AES-256-GCM notification outbox、失败重试与 key rotation 读取；平台投影/审计不返回 secret、
  联系地址、密文或请求摘要；
- 新用户消费 grant 时本人设置首个长期密码；已有 ACTIVE 用户必须用本人 USER Token 与
  `identity.provisioning.accept` 接受。成功事务原子创建 ACTIVE tenant、user（若需要）与 OWNER；
  失败计数锁定、过期、回放和核心写入失败均不产生孤儿 ACTIVE tenant；
- 平台 tenant/user 核心事实安全分页分别受对应 read scope 保护，单页最多 100，不返回密码、
  OAuth、membership、通知或 activation 数据；未激活申请通过显式 cancellation 子资源幂等关闭，
  request、预期 grant、未发布 payload 销毁与阶段审计同事务；
- 默认关闭的预配通知终态回执 API：独立 tenantless gateway client、专用
  `identity.provisioning-notifications.receipts.write` scope、精确白名单、只允许已
  `PUBLISHED` notification、gateway event/notification 双重幂等、UUIDv7 回执和最小安全字段；
- `ainer-dev.xiaoqu99.com` 手工触发的可复现 dev 发布与真实公网环境：独立 PostgreSQL 18.3、
  loopback Authorization Server systemd、版本化 JAR/Studio/Admin、原子切换/校验回滚、
  Let's Encrypt 和精确同源 Nginx 配置；真实 Chromium 已完成 PKCE、成员治理、revoke、
  OIDC logout 和退出后重新登录门禁；
- ADR-0001 至 ADR-0011、ADR-0015 至 ADR-0020 与 ADR-0022 已接受，ADR-0012 至 ADR-0014
  及 ADR-0021 处于 Proposed；
  架构、HTTP API、安全、数据、测试、运行与发布基础文档已建立。

## 3. 最近验证证据

2026-07-27 在 `https://ainer-dev.xiaoqu99.com` 完成首次真实公网联合验收。Authorization Server
release 为 `3f9420a4425f11e78feace776fe0b15853a0b884`，Ainer Studio/Admin release 为
`d13fe026cd5422f85f03c443e09f825c05e114a1`；systemd 与独立 PostgreSQL 18.3 均在线，空库
实际执行 16 份 migration，公开 discovery issuer 与规范 origin 一致。无网络拦截的 Chromium
实际完成表单登录、Authorization Code + PKCE、成员读取/添加、MEMBER → ADMIN → MEMBER、软移除、
当前 access token 撤销、RP-Initiated Logout 和退出后重新访问要求登录。公网延迟暴露的 Studio
退出导航/路由守卫竞态已由 `d13fe02` 修复并复验通过；fixture 运行开关已关闭，密码不在 Java
EnvironmentFile。

2026-07-27 的 M6 品牌登录候选在 macOS Colima、Testcontainers 2.0.5 与
`postgres:18.3-alpine` 环境完成 `mvn clean test`：14 个 Reactor 模块成功，71 个测试套件、
281 个测试全部实际执行通过，0 failure、0 error、0 skipped。真实 Chromium 直接访问候选
Authorization Server 的 `/login` 与 `/login?error`，并以同一服务端模板/CSS 检查合同规定的
429/503 视觉状态；normal、credential-error、rate-limited、service-unavailable 四种状态均完成
1440×900 和 390×844 截图复核，桌面/移动共 8 组 axe-core 4.12.1 扫描均为 0 violation。
服务端测试同时覆盖 CSRF、SavedRequest/PKCE、通用凭据错误、一次性 503、HTML 429、
`Retry-After`、no-store 与既有 Passkey/WebAuthn ceremony。Studio 合同和 Tokens 的 SHA-256
分别保持 `e8e50c266957c7fe14af4b4e30508dd6fe52f43c12029261d8a44e5d51ce2786` 与
`2a8eeed8d598ebc647163662a7de8f7bb0d0ce2e3a171e2392e638ba75c095d8`。该候选尚未推送或部署，
不能替代现有 dev 公网验收。

2026-07-27 部署工具通过 `bash -n`、隔离路径/精确代理静态门禁和 `git diff --check`。Java
`mvn test` 的 14 模块构建成功，但执行机当时没有 Docker，Authorization Server 的 30 个
Testcontainers 测试按既有 `disabledWithoutDocker` 策略跳过；该次运行不替代下述 0-skipped
基线，也不能作为公网 dev 部署证据。首次上线仍必须用服务器真实 PostgreSQL migration 和远程
Chromium 联合验收关闭门禁。

2026-07-26 在 macOS Colima、Testcontainers 2.0.5 与 `postgres:18.3-alpine` 环境执行完整
`mvn test`：14 个 Reactor 模块成功，67 个测试套件、271 个测试全部实际执行通过，
0 failure、0 error、0 skipped。

本轮 Ainer Admin 证据使用固定 `ainer-admin-dev` public client、同一 HTTP cookie session 与
`postgres:18.3-alpine` 从空库执行 Authorization Server 16 份 migration。端到端覆盖
Authorization Code + PKCE S256、无 Refresh Token、default tenant/OWNER claims、成员 GET、添加
已有用户、MEMBER → ADMIN → MEMBER、软移除与 `[ADDED, ROLE_CHANGED, ROLE_CHANGED, REMOVED]`
审计；自助撤销后旧 access token 被 active gate 返回 401，ID token 仍可完成
`/connect/logout` 并精确返回 `/ainer-admin/auth/logged-out`。全量回归还把旧成员 API 集成测试
从“只签名 JWT”改为持久化真实 active authorization，避免测试绕过新的在线活性边界。
本次已把 `dev@a22e121` 的 M4.8A 合入 Ainer Admin 分支。Java、POM、`application.yaml`、Admin
OpenAPI 与 M4.8A migration 自动合并且没有 migration 版本碰撞；7 个冲突全部位于 README、
Changelog、ADR 索引和状态文档，并已保留双方内容。安全链顺序保持为协议端点、内部控制面、
M4.8A 激活、Admin 成员/revoke active gate、指标和默认登录；M4.8A tenantless SERVICE Token
夹具与 Admin active authorization 夹具彼此隔离。融合回归还修正了 M4.8A 新用户激活测试把
`IdentityAccount.roles()` 误写为领域角色 `OWNER` 的断言，使其与既有 Spring authority
`ROLE_OWNER` 契约一致；对外 Token `roles` claim 仍为 `OWNER`。
`docs/README.md` 继续只是目录门面，`docs/00-overview.md` 是唯一权威入口并已纳入
`ainer-admin-integration.md`。严格 OpenAPI 校验和 TypeScript SDK 生成成功；
`ainer-admin-v1.yaml` SHA-256 保持
`1269a0e325f645ab9371a7783635e0a7cdfe1bfad4cd11b56bc6ade5f2468056`。

本轮 M4.7 新增 Identity 管理面证据：Identity 模块从空库执行 6 份 migration，真实 PostgreSQL
覆盖成员列表、按 username/subjectId 加入、角色变更、软移除、DISABLED 重激活、OWNER 保护与每次
写入的 operation/reason/request ID 审计。Authorization Server 从空库执行 13 份 migration，
随机端口 HTTP 使用实际 RSA Bearer JWT 覆盖匿名 401、缺 scope/SERVICE/MEMBER/跨 tenant 403，以及
加入、列表、改角色、移除和 3 条审计落库；同时证明成员 API 只使用 Identity 权威数据库。
bootstrap 用例证明首次创建、重复执行不覆盖密码、部分 tenant/username 占用失败关闭。

此前 M4.8A 预配申请证据：Identity 模块从空库执行 7 份 migration，真实 PostgreSQL 覆盖规范化
请求、相同摘要幂等重放、同幂等键下 tenant name/change reference 变化冲突、tenant code 双线程并发
预留只成功一次、过期释放、ACTIVE 用户复用、LOCKED 用户拒绝、与 bootstrap 共享冲突门禁、核心
tenant/user/membership 零污染和 request/phase audit。Authorization Server 从空库执行 14 份
migration，随机端口 HTTP 使用实际 RSA Bearer JWT 覆盖匿名 401、缺 header 400、缺成对 scope、
tenant-bound SERVICE、USER、白名单外 operator 的 403，以及 POST/GET、安全投影、no-store、
幂等冲突和审计落库。配置与 bootstrap 单元测试覆盖空 operator、TTL 边界、弱 secret 和策略不匹配
既有 client 的启动失败。

本轮激活核心增量证据：本机 PostgreSQL 18.4 随机 schema 经 Flyway 实际执行 Identity 全部 8 份
migration，跑通申请、AES-GCM 通知解密、错误 secret 次数持久化、成功原子激活和回放拒绝，结束后
schema 已清理；同一批 migration 还在单事务临时 schema 中完整执行并回滚。新增不依赖 Docker 的
4 个测试覆盖 AES-GCM round-trip、tamper、未知 key、旧 key rotation 读取与 provider 失败延迟重试；
配置测试覆盖 activation TTL/次数/key ring 失败关闭。完整 Identity PostgreSQL 用例又加入锁定、
过期、已有用户 subject 绑定、默认 tenant 保留、核心写入失败回滚和密文不含 secret 断言。

本轮通知 transport 增量已实现独立 tenantless relay client bootstrap、OAuth2 Client Credentials、
HTTPS gateway publisher、稳定 `Idempotency-Key`、调度领取、失败分类和 pending/failed/exhausted/
cancelled/oldest-ready 指标。HTTP 合约测试证明 Bearer、版本化 envelope、新用户激活材料和已有用户
无 secret 投影；配置测试证明普通 HTTP、带 query 的 URI 和非法重试边界失败关闭。网关 2xx 后或
请求取消时，Identity 会销毁 `protected_payload` 的可解密内容；`PUBLISHED` 只证明网关持久接收，
不代表邮件、短信或站内信最终送达。

本轮平台控制增量增加成对 write scope 的显式 cancellation，以及各自 read scope 的 tenant/user
安全分页。非 Docker 单元测试覆盖 tenantless SERVICE、operator 白名单、scope 拆分、分页边界和
取消指标；PostgreSQL 18.4 隔离 schema 重放全部 8 份 Identity migration，并实测 request/grant/
outbox 同时 `CANCELLED`、payload 销毁和取消审计。Testcontainers 用例还覆盖重复取消不重复审计、
新用户 grant 缺失时整笔回滚、已有用户无 grant、稳定排序/total 和 HTTP 响应不含凭据数据。

本轮终态回执增量增加独立 gateway client bootstrap、tenantless SERVICE + 专用 scope + 精确白名单
安全链、`DELIVERED|FAILED` 最小模型、单 notification 终态与 gateway event 幂等、抢先回执状态
冲突以及首次终态 Counter。10 个不依赖 Docker 的新增测试覆盖参数/未来时间、回放/冲突、Controller
安全投影、配置失败关闭和 bootstrap 策略。本机 PostgreSQL 18.4 隔离 schema 从空库重放全部 9 份
Identity migration，验证回执 UUIDv7、合法终态写入、重复 notification 唯一冲突和 `FAILED`
空失败码拒绝；该 smoke 实际发现并修正了 SQL 三值逻辑下 NULL 逃逸 check 的问题，schema 已清理。
随机端口 OAuth2/Bearer 与真实事务 Testcontainers 用例已写入并通过 test compilation，但当前无
Docker，尚未实际执行，不能计入 0-skipped 发布证据。

当前机器未运行 Docker。本轮干净执行 `mvn clean test` 时 14 个 Reactor 模块全部成功，
Surefire 共发现 61 个测试套件、256 个测试；其中 172 个实际执行并通过，0 failure、0 error，
84 个 Testcontainers 测试因 `disabledWithoutDocker=true` 跳过。因此上述本机 PostgreSQL smoke
是真实增量证据，但不是新的完整 0-skipped 发布快照。上方 221-test 结果仍是最近一次完整不跳过
基线，合并/发布前必须在 Colima/Testcontainers 可用环境重跑全量并更新数字。

本轮安全收口还验证：Passkey 恢复/enrollment 对目标 ACTIVE default membership 的跨 tenant guard 与
数据库复合外键；登录限流在 context path 下对 WebAuthn options 返回统一 429、
`Retry-After`/no-store 并记录 allow/deny；step-up 真实 HTTP 覆盖匿名 401、USER 成功、SERVICE/
缺因子/旧时间/越界未来时间 403。全量测试还暴露并修正了 AI 测试 JWT 缺必填 `actor_type` 的旧夹具。

累计 Phase D resource server step-up 证据：`RecentStrongAuthenticationFilter` 单元测试覆盖
`amr` 含必需因子且 `auth_time` 新鲜放行、密码 Token 缺 `mfa` 返回 403（错误体含特定错误码）、
`auth_time` 过期/缺失拒绝、缺 `amr` 拒绝、非 JWT 认证拒绝、非受保护路径不节流，以及 `StepUp`
配置校验拒绝空规则/空 `required-amr`/超 24 小时 `max-auth-age`。这是 resource server 第一次消费
Authorization Server 在 Phase A 签发的 `amr`/`auth_time`。filter 默认关闭，与在线校验 filter 同锚点。

累计 Phase C 限速与受控 enrollment 证据：限速器单元测试覆盖窗口内放行、超额拒绝、不同 key
独立计数、跨窗口复位与 `Retry-After` 取整；受控首次 enrollment 在真实 PostgreSQL 上验证
`require-invite` 模式——无授权的首枚 Passkey 登记被拒（`ENROLLMENT_GRANT_REQUIRED`），操作员建立
授权后首登成功且授权同事务置 `CONSUMED`，已有 ACTIVE Passkey 的 replacement 不受影响。限速明确为
node-local（全仓无 Redis），多实例需共享存储留待后续。限速 filter 的端到端 HTTP 429 已完成；
enrollment 服务控制面与真实登记拒绝路径已由 PostgreSQL 集成测试覆盖。

累计 Phase B Passkey 恢复证据：恢复码自助流程在真实 HTTP 会话与 PostgreSQL 上跑通——
真实 Passkey 登记后签发 8 枚高熵一次性恢复码（明文仅返回一次，库内只存 bcrypt 哈希），
密码登录本人用一枚恢复码赎回后，该账号全部 ACTIVE Passkey 被吊销并写 `SELF_RECOVERY`
安全操作审计，用户可重新 bootstrap。管理员双人恢复在 service 层用真实事务验证：申请者建立
`REQUESTED`，同服务批准被拒（`RECOVERY_APPROVER_MUST_DIFFER`），不同服务批准成功、吊销目标
全部 Passkey，`(operation_id, phase)` 偏唯一审计为 `[REQUESTED, EXECUTED]`，重复批准被拒。
恢复码失败尝试按 subject 累计并锁定；最后凭证保护在恢复上下文中被安全越过（不破坏普通自助
删除的最后凭证保护）。通知（含联系字段与可达通道）仍为已知缺口，未在本切片交付。

累计 Phase A Passkey 真实签名 ceremony 端到端证据：用 webauthn4j 虚拟 authenticator
驱动 `/webauthn/register`（真实 attestation）与 `/login/webauthn`（真实 assertion）闭环，
真实走通 Spring Security 7.1 的 `Webauthn4JRelyingPartyOperations` 签名校验代码路径；
Passkey 用户完成授权码流程后，access token 携带 `amr=pwd,mfa,pop`、`auth_time`、稳定
`sub`（subjectId UUID）、`tenant_id` 与 `roles`。这组测试同时揭露并修复了三个此前被合成
CredentialRecord 测试掩盖的真实缺陷：OAuth2 授权记录无法反序列化 `WebAuthnAuthentication`
主体（注册 `WebauthnJacksonModule`）、Passkey 用户 token 因 customizer 不认 WebAuthn
principal 而缺 Ainer claims（customizer 改为按 username 解析 `AinerUserDetails`），以及
凭证管理端点 `/webauthn/register/**` 因协议 filter 在授权 filter 之前短路而未被条件 MFA
门禁保护（新增 `AinerPasskeyCredentialManagementGateFilter`，锚定 `CsrfFilter` 之后、协议
filter 之前）。HTTP 层门禁测试覆盖：已登记账号在缺因子时 `/webauthn/register/options` 与
`DELETE /webauthn/register/{id}` 均 302 到 `/login`，凭证不被新增或删除；首次 bootstrap
（未登记账号）不受影响。本轮尚未覆盖真实设备/浏览器兼容矩阵。

累计 OAuth Client 生命周期证据：创建
managed client 后只保存 password hash，一次性 secret 可正常换取 tenant-bound JWT；未授权 scope
返回稳定 422，tenant-bound operator 返回 403。蓝绿轮换期间新旧 ID 并行可用，显式退役后旧
secret 换 Token 返回 401、历史 Token introspection inactive，而 Spring 官方 JDBC authorization
仍可重建历史记录。配置测试覆盖空 operator、平台/`.all` scope、过长 access token 和弱随机
secret 的启动失败；operator bootstrap 只创建无 tenant、`oauth.clients.manage`、一分钟 Token。
创建、轮换、退役审计表没有 secret 字段。

本轮新增 PKCE 证据：测试专用 public client 通过真实表单登录、cookie/CSRF 和 S256 challenge
取得 authorization code，并用正确 verifier 交换出包含人员 `sub`、`tenant_id`、`roles` 的
access token 和 OIDC ID token；响应不含 refresh token。authorization code 重放、错误 verifier、
缺失/`plain` challenge 均失败，未注册 redirect URI 不发生外部跳转。真实 JDBC 往返暴露并修复
了 Jackson 3 拒绝 Ainer 人员 principal 的问题；修复只增加精确类型白名单，并证明授权记录不含
密码或 password 字段。

本轮新增 Passkey 证据：Authorization Server 从空库执行七份 migration，创建 Spring 官方
WebAuthn 协议表和 Ainer 生命周期/审计表。配置门禁拒绝 IP 型 RP ID、越界 Origin、普通 HTTP、
重复 Origin、路径和过长 timeout；真实 HTTP registration options 强制 resident credential 与
`userVerification=required`。无凭证账号仍可用密码完成 PKCE，Token 含 `amr=pwd` 和
`auth_time`；登记合成 credential 后，仅密码不能取得 authorization code。JDBC 门禁验证登记、
计数器/last-used 更新、replacement、软撤销、审计和协议记录保留；并发撤销两个 ACTIVE
credential 时只允许一个成功，最后一个保持 ACTIVE。后续虚拟 authenticator 签名 ceremony 已完成，
但主流真实设备兼容矩阵仍未完成，因此不能把自动化证据表述为生产兼容性认证。

本轮新增指标安全证据：Resource Server 真实 HTTP 测试覆盖无 Token 401、USER/tenant-bound SERVICE/缺 scope 403、tenantless SERVICE 200，并使用自定义 management base path 证明路径配置不会绕过授权；路径 matcher 还覆盖 context path、尾斜杠和编码路径。业务 Resource Server 显式关闭时，真实 Prometheus endpoint 仍拒绝匿名并且不返回 JVM/process 指标。metrics bootstrap 测试证明只创建 Client Credentials、无 tenant、只有 `platform.metrics.read`、一分钟 Token、无 introspection 标记，重复运行不覆盖且弱 secret 失败关闭。Authorization Server 的真实 PostgreSQL 协议测试已实际验证专用/tenant-bound metrics Token 与 exporter 的 401/403/200，并同时覆盖 Client Credentials、OIDC discovery、专用 introspection、RFC 7009 与 Identity revocation epoch。

同日使用本机真实 PostgreSQL 18.4 从空库执行 Identity 四份、Workspace 八份全量 migration。除 M4.1 的 outbox 领取/撤销证据外，本轮实际执行了耗尽原事件双人重放事务、REVOKED OWNER 提升新 OWNER 事务、安全操作审计约束，以及授权审计归档 CTE、热冷统一查询和导出审计。原事件内容保持不变，旧 OWNER 保持 REVOKED，归档后热表 0/归档表 3；两个一次性数据库均已删除。loopback HTTP 测试实际验证 Client Credentials Token 获取/缓存和 Bearer 事件发布。

M4.3 另使用本机 PostgreSQL 18.4 从空库执行 Authorization Server 五份 migration 并实际启动发行物。协议 smoke 证明普通 client introspection 返回 401、专用 client 对新 Token 返回 `active=true`、RFC 7009 revocation 返回 200 且随后 `active=false`。真实 JDBC 往返同时暴露并修复了 Boot 4/Jackson 3 对 JDK 私有不可变 claim 集合的反序列化拒绝。对 5,000 条合成 access event 的 revocation epoch 查询使用 `idx_ainer_identity_access_event_subject` Index Only Scan，实测执行约 0.036 ms；旧/等于 epoch 的 Token inactive，事件后的 Token active，membership 禁用后当前身份 inactive。一次性数据库和 RSA 测试密钥目录均已删除。

该结果证明当前源码基线可构建，且全部自动化 PostgreSQL 集成组已在本地 Docker-compatible runtime 中实际执行；它仍不是独立发布候选环境的证据，也不证明生产容量、备份恢复或高可用。

## 4. 已知缺口

### 访问控制

- 选择性在线校验只覆盖配置的高风险 API；普通低风险自包含 JWT 仍有自然到期窗口；
- Authorization Server 已成为高风险 API 在线依赖；受保护 exporter 与独立 metrics/introspection 凭据创建基线已有代码，但尚未完成生产高可用、容量、旧凭据退役、真实 Prometheus、dashboard 与告警；
- 重放与 OWNER 恢复已做服务 `sub` 分离，但生产 IAM 仍需证明凭据由不同人员/职责保管；
- 授权审计归档仍位于同一数据库，没有 WORM、数字签名、法律保留和最终删除策略；
- SIEM 只有默认关闭的拉取 API，没有部署外部消费者、不可变副本或告警路由；
- Directory/relay/consumer 与 M4.2 控制面默认关闭，尚未在真实多节点环境完成容量、故障注入和滚动 30 天撤销 SLO 验证。

### Identity 与 OAuth

- tenant-bound Client Credentials 已有生命周期控制面，但 browser/OIDC、平台级
  metrics/introspection/operator、`.all` 与既有 bootstrap client 尚未纳管，也没有列表分页、审计
  导出、双人审批或 UI；
- Authorization Code + PKCE 与 Passkey 条件门禁、虚拟 authenticator 签名 ceremony、恢复、
  受控 enrollment 和 Resource Server step-up 已有自动化证据，但生产 browser/OIDC client 控制面、
  恢复通知、真实设备矩阵、共享限流、多节点会话和签名密钥轮换未完成；品牌登录合同 1.0.0
  明确不提供可见 Passkey 动作，因此需要人员 Passkey 登录的部署仍需等待 Studio 新合同；
- 平台级 tenant/user 控制面已有默认关闭的预配申请/查询、一次性激活核心、加密 notification
  outbox、OAuth2/HTTPS 通知网关 relay、已有用户本人接受、安全分页、显式取消与 provider-neutral
  终态回执接收；真实外部通知网关/供应商、供应商回执映射和最终送达证据、禁用/恢复、tenant
  ownership transfer 和成员管理 UI 尚未完成；
- tenant 成员 API 位于 Authorization Server，但其 step-up/在线校验接入要随生产 browser/OIDC
  client 策略单独完成；Ainer Admin access token 已强制 active gate，但不能把业务 Server 的默认
  step-up 规则误认为覆盖该端点；
- Ainer Admin 同源代理已有契约但尚未在选定生产 ingress 上完成 HTTPS、Cookie、重定向和缓存
  验收；`ainer-admin-dev` 与开发身份不能替代生产 browser client 生命周期和正式开户。

### AI 平台

- 限流仍是单进程基线，未形成集群级一致性；
- provider 凭据托管、指标、trace、输出策略、评测、RAG 与 Agent runtime 尚未完成；
- Run / Artifact 与 Knowledge 当前只有经 xq 现状复审后的 Proposed 数据模型，没有 Accepted ADR、
  migration 或运行代码；通用 Step、Feedback 和资源级 ACL 不得描述为已完成；
- 供应商兼容面仅覆盖当前 OpenAI-compatible 最小协议。

### 工程与运营

- PostgreSQL Native-First 目标已由 ADR-0020 和数据库规范 1.2 确立，但当前持久化实现仍大量使用
  UUIDv4，Workspace/AI `tenant_id` 仍为 `varchar(128)`；M4.8A 新增 request/grant/outbox/audit
  /receipt 及预留 tenant/subject 已率先使用 PostgreSQL `uuidv7()`，但统一 `RETURNING`、全域
  tenant UUID 化和 1.0 clean baseline 尚未实施；
- 没有正式 CI、制品签名、SBOM、发布仓库和自动部署；
- 没有具名模块维护者矩阵、`CODEOWNERS` 和正式审查责任分配；
- 没有生产备份恢复、容量测试、正式错误预算/告警路由和灾难恢复演练；
- 没有稳定版兼容政策、商业许可证文本和付费产品交付系统；
- Testcontainers 仍使用 `disabledWithoutDocker`；本机 Colima 已能完整执行，但正式 CI 尚未建立“数据库测试不得跳过”的自动门禁。

## 5. 下一里程碑

M4.8 已形成并接受
[ADR-0019](decisions/0019-identity-provisioning-tenant-context-and-ownership-governance.md)，
按依赖顺序拆为三个切片。M4.8A 已完成 tenantless SERVICE 预配、激活核心与真实 HTTPS gateway
transport、provider-neutral 终态回执接收，以及平台显式取消与 tenant/user 安全分页；下一步联调
真实外部通知网关/供应商和回执映射，并在 Docker 可用环境跑完随机端口 HTTP、并发、过期、回滚和
0-skipped PostgreSQL 门禁。完成这些
M4.8A 发布证据后再进入 Authorization Server 人员
多租户上下文选择，使 `is_default` 只承担首次登录落点；最后完成当前 OWNER 发起、目标 ACTIVE ADMIN
强认证接受的 Identity OWNER 专用转移。不能先做 OWNER 转移，因为现有人员 Token 仍只从默认
membership 取得 tenant，上下文选择是目标本人接受转移的协议前提。

M4.8 与商业 entitlement 保持正交：Identity `OWNER/ADMIN/MEMBER` 只表达授权角色，Community /
Pro / Enterprise、license、订阅和配额留给后续独立 entitlement 边界。生产并行工作仍包括真实
Prometheus 抓取、dashboard/告警、Authorization Server 多实例容量/故障证据、browser/OIDC client
生产控制面、真实设备/浏览器兼容矩阵、metrics/introspection/operator 旧凭据退役、Resource Server
灰度与安全降级审批，以及把 M4.2 操作审计接入生产级 IAM 职责分离和外部不可变存储。

完成条件包括：平台供应使用独立 tenantless SERVICE operator、最小 scope、幂等键、一次性激活
凭据与真实 PostgreSQL/HTTP 证据；tenant selection 支持并行会话且不能由请求参数伪造 claim；
OWNER 转移具备双方强认证、单一未完成请求、数据库唯一 ACTIVE OWNER、同事务审计和双方旧 Token
撤销证据；Passkey 通知、真实设备与多节点 session 完成验证；发布候选环境的 Testcontainers
测试不跳过；
高风险 online validation 在目标容量与故障注入下满足确定的延时/可用性目标；request/approve
与平台凭据在运营上真正分离并可轮换；外部审计副本可验证、可恢复；初始撤销 SLO 经多节点证据
修正后形成正式错误预算。随后继续补齐浏览器/平台 Client 控制面、签名密钥轮换与独立商业
entitlement 内核。

## 6. 更新规则

- 只写已经有代码、migration、测试或明确证据的完成项；
- 测试数量和版本变动后更新本页，不散落到长期规范；
- 缺口关闭时同时更新对应 ADR、专题文档和 Changelog；
- 发布版本形成后保留历史 Changelog，本页只保留最新状态。
