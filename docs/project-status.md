# Ainer 项目状态

> 文档类型：时间敏感快照 · 状态：持续更新 · 核对时间：2026-07-25 · 工程版本：`0.1.0-SNAPSHOT`

本文只记录当前事实和验证证据，不替代架构规范与 ADR。每个里程碑结束、发布候选形成或主要风险变化时更新核对时间。

## 1. 当前阶段

Foundation M4.6 Passkey 协议、条件人员认证基线、Phase A 真实签名 ceremony、Phase B 恢复码与
管理员双人恢复，以及 Phase C 登录限速与受控首次 enrollment 已完成。下一阶段进入账号通知与
step-up policy。项目已经从纯设计文档进入可编译、可运行、可测试的 Spring Boot 4.1 多模块工程，
但尚未达到生产或商业发行就绪。

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
- ADR-0001 至 ADR-0011 已接受，ADR-0012 至 ADR-0014 处于 Proposed；架构、HTTP API、安全、数据、测试、运行与发布基础文档已建立。

## 3. 最近验证证据

2026-07-25 在 macOS Colima、Testcontainers 2.0.5 与 `postgres:18.3-alpine` 环境执行完整
`mvn test`：14 个 Reactor 模块成功，188 个测试全部实际执行通过，0 failure、0 error、
0 skipped。

本轮 Phase C 新增限速与受控 enrollment 证据：限速器单元测试覆盖窗口内放行、超额拒绝、不同 key
独立计数、跨窗口复位与 `Retry-After` 取整；受控首次 enrollment 在真实 PostgreSQL 上验证
`require-invite` 模式——无授权的首枚 Passkey 登记被拒（`ENROLLMENT_GRANT_REQUIRED`），操作员建立
授权后首登成功且授权同事务置 `CONSUMED`，已有 ACTIVE Passkey 的 replacement 不受影响。限速明确为
node-local（全仓无 Redis），多实例需共享存储留待后续。限速 filter 的端到端 HTTP 429 与 enrollment
控制面 HTTP 级拒绝路径仍是后续门禁。

本轮 Phase B 新增 Passkey 恢复证据：恢复码自助流程在真实 HTTP 会话与 PostgreSQL 上跑通——
真实 Passkey 登记后签发 8 枚高熵一次性恢复码（明文仅返回一次，库内只存 bcrypt 哈希），
密码登录本人用一枚恢复码赎回后，该账号全部 ACTIVE Passkey 被吊销并写 `SELF_RECOVERY`
安全操作审计，用户可重新 bootstrap。管理员双人恢复在 service 层用真实事务验证：申请者建立
`REQUESTED`，同服务批准被拒（`RECOVERY_APPROVER_MUST_DIFFER`），不同服务批准成功、吊销目标
全部 Passkey，`(operation_id, phase)` 偏唯一审计为 `[REQUESTED, EXECUTED]`，重复批准被拒。
恢复码失败尝试按 subject 累计并锁定；最后凭证保护在恢复上下文中被安全越过（不破坏普通自助
删除的最后凭证保护）。通知（含联系字段与可达通道）仍为已知缺口，未在本切片交付。

2026-07-25 在 macOS Colima、Testcontainers 2.0.5 与 `postgres:18.3-alpine` 环境执行完整
`mvn test`：14 个 Reactor 模块成功，182 个测试全部实际执行通过，0 failure、0 error、
0 skipped。

本轮 Phase A 新增 Passkey 真实签名 ceremony 端到端证据：用 webauthn4j 虚拟 authenticator
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

2026-07-23 在 macOS Colima 0.10.3、Docker 29.5.2 与 Testcontainers 2.0.5 环境执行完整
`mvn test`：14 个 Reactor 模块成功，180 个测试全部实际执行通过，0 failure、0 error、
0 skipped。PostgreSQL 集成组使用 `postgres:18.3-alpine` 从空库启动并执行 Flyway，覆盖
Identity、Workspace、AI runtime 与 Authorization Server。

本轮新增 OAuth Client 生命周期证据：Authorization Server 从空库执行六份 migration；创建
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
credential 时只允许一个成功，最后一个保持 ACTIVE。本轮尚未完成 authenticator 的真实签名
ceremony，因此不能把这组证据表述为完整 Passkey 兼容性或生产 MFA。

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
- Authorization Code + PKCE 与 Passkey 条件门禁已有自动化协议证据，但生产 browser/OIDC
  client 控制面、品牌登录 UI、完整 authenticator ceremony、受控 enrollment、恢复/通知、
  会话治理、step-up policy 和签名密钥轮换未完成；
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
- Testcontainers 仍使用 `disabledWithoutDocker`；本机 Colima 已能完整执行，但正式 CI 尚未建立“数据库测试不得跳过”的自动门禁。

## 5. 下一里程碑

M4.6 已完成 Passkey 协议配置、条件人员门禁、JDBC credential 生命周期基线、Phase A 真实签名
ceremony 端到端门禁与凭证管理条件 MFA 门禁、Phase B 恢复码与管理员双人恢复，以及 Phase C 登录
限速与受控首次 enrollment。虚拟 authenticator 的 registration/authentication 签名 ceremony 已用
webauthn4j 在自动化测试中真实跑通；真实设备/浏览器兼容矩阵仍待补。下一工程切片优先建立账号通知
（含联系字段与可达通道）与 resource server 端 step-up policy，随后继续 browser/OIDC client 的
生产注册、redirect URI 变更和退役流程。

生产并行工作仍包括真实 Prometheus 抓取、dashboard/告警、Authorization Server 多实例容量/故障
证据、metrics/introspection/operator 旧凭据退役、Resource Server 灰度与安全降级审批，以及把
M4.2 操作审计接入生产级 IAM 职责分离和外部不可变存储。

完成条件包括：受控 enrollment、完整 ceremony、凭证丢失/被盗、恢复、通知和高风险操作
step-up 边界形成 ADR 与真实数据库/浏览器门禁；发布候选环境的 Testcontainers 测试不跳过；
高风险 online validation 在目标容量与故障注入下满足确定的延时/可用性目标；request/approve
与平台凭据在运营上真正分离并可轮换；外部审计副本可验证、可恢复；初始撤销 SLO 经多节点证据
修正后形成正式错误预算。随后继续补齐 tenant ownership transfer、浏览器/平台 Client 控制面与
签名密钥轮换。

## 6. 更新规则

- 只写已经有代码、migration、测试或明确证据的完成项；
- 测试数量和版本变动后更新本页，不散落到长期规范；
- 缺口关闭时同时更新对应 ADR、专题文档和 Changelog；
- 发布版本形成后保留历史 Changelog，本页只保留最新状态。
