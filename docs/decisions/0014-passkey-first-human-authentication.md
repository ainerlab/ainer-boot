# ADR-0014：Passkey 优先的人员认证与条件 MFA 基线

- 状态：Proposed
- 日期：2026-07-23
- 决策者：待指定
- 取代：无
- 被取代：无

## 背景

Ainer 已完成 Authorization Code + PKCE、人员密码登录、标准 JWT、Identity 状态撤销和受审计
服务 Client 生命周期，但此前没有抗钓鱼的人员认证因子、凭证生命周期或恢复策略。工业级平台
不能只把 TOTP 输入框接到登录页就宣称 MFA 完成，也不能把 WebAuthn 私钥、人体特征或自研
challenge 协议放进业务数据库。

Spring Security 7.1 已提供 WebAuthn/Passkey 协议实现、认证因子 authority 与条件 MFA 组合能力。
NIST SP 800-63B-4 明确要求高保证场景使用抗钓鱼的密码学认证器；手工输入的 OTP 不具备抗钓鱼
能力。WebAuthn Level 3 则定义了 RP ID、Origin、challenge、用户验证和凭证签名等协议边界。

当前事实是：Ainer 尚无公网注册、受控 enrollment 邀请、恢复码、管理员恢复、恢复通知、真实
浏览器 authenticator 兼容矩阵或多节点会话证据。因此本 ADR 决定的是第一条可运行安全基线，
不是“生产 MFA 已全部交付”。

## 决策驱动因素

- 优先抵抗凭证钓鱼、重放和数据库密码材料泄露；
- 复用标准 WebAuthn/OIDC 行为，不自造 challenge、签名或 Token claim；
- 允许现有密码账号渐进迁移，同时避免已登记账号静默降级回密码；
- 私钥和生物识别数据留在 authenticator；服务端只保存公钥协议材料和治理元数据；
- 凭证登记、使用、轮换和撤销必须可审计、可失败关闭；
- Client Credentials、内部 API 和指标安全链不能被人员 MFA 规则误伤。

## 备选方案

### 方案 A：继续只用密码

实现和迁移成本最低，但不能抵抗钓鱼、撞库和密码数据库泄露，不满足长期方向，拒绝。

### 方案 B：密码 + TOTP 作为默认 MFA

生态成熟、离线可用，但 TOTP 是可转发的手工 OTP，不抗实时钓鱼。它可在后续作为受限恢复
fallback 评估，不能成为 Ainer 的首选强认证，拒绝作为主方案。

### 方案 C：Passkey/WebAuthn 优先，按账号渐进启用

浏览器和 authenticator 执行标准密码学 ceremony，服务端保存公钥；启用用户验证的 Passkey
可以同时证明 authenticator 持有和本地用户验证。需要严格 RP/Origin 配置、恢复设计和设备兼容
验证，采用。

### 方案 D：只委托外部企业 IdP

生产部署应允许企业 OIDC IdP 替换 Ainer 的交互式登录，但只依赖外部 IdP 会让独立发行版缺少
可验证的安全基线。保留为部署选项，不替代本 ADR。

## 决策

1. `ainer-authorization-server` 使用 Spring Security 7.1 官方 WebAuthn 实现，不复制或修改
   WebAuthn4J 验证算法。
2. Passkey 默认关闭。开启时必须配置小写 RP ID、RP name、至少一个精确 Origin 和 ceremony
   timeout；生产 Origin 只允许 HTTPS。HTTP 例外必须显式开启且只接受 `localhost` 自动化测试。
   Origin host 必须等于 RP ID 或位于其子域范围，错误配置启动失败。
3. registration 和 authentication options 都强制
   `userVerification=required`；使用 resident/discoverable credential，attestation 保持 `none`。
   Ainer 当前不基于设备厂商或 attestation 建立信任等级。
4. 条件迁移规则：
   - 没有 ACTIVE Passkey 的账号可用密码完成一次 bootstrap，并登记首个 Passkey；
   - 存在 ACTIVE Passkey 后，`/oauth2/authorize` 与凭证登记/删除管理要求
     `FACTOR_WEBAUTHN`；
   - 该要求不应用于 Client Credentials、`/internal/**` 或 Prometheus 无状态安全链。
5. 服务端使用 Spring 官方 `user_entities` / `user_credentials` 作为协议存储，并增加
   `ainer_passkey_credential` 生命周期和 `ainer_passkey_credential_audit`。登记、协议记录和
   `REGISTERED` 审计在同一事务提交；删除转为 `REVOKED` 软撤销并保留官方协议记录和审计证据。
   已撤销或缺失生命周期的凭证在认证查询中失败关闭。
6. 最后一个 ACTIVE Passkey 不能通过自助端点删除。轮换必须先登记 replacement，再撤销旧
   credential，避免条件策略静默降级到密码。账号丢失最后一个 authenticator 时，当前只能走
   账号停用和未来的专用恢复流程，不能绕过数据库状态直接删记录。
7. 人员 Token 使用 IANA 已登记 AMR 值：
   - 密码因子写 `pwd`；
   - `userVerification=required` 的 WebAuthn 写 `mfa` 与 `pop`；
   - `auth_time` 取本次已完成认证因子的最新签发时间。
   当前不自造 `webauthn`/`passkey` AMR 值，也不为 Client Credentials 添加人员 AMR。
8. TOTP、恢复码、管理员恢复、恢复通知、step-up 有效期和 assurance policy 暂不包含在本
   切片。未来恢复码必须高熵、一次性、只保存哈希并限速；恢复和因子替换必须通知账号所有者并
   写独立高风险审计。

## 后果

### 正面

- 首选人员认证获得标准的抗钓鱼密码学方向，数据库泄露不直接暴露可用于登录的私钥；
- 现有账号可渐进登记，已登记账号的 OAuth 授权和凭证管理不会静默回落为密码；
- 协议表与 Ainer 生命周期表职责分离，既复用官方实现又保留软撤销和审计证据；
- `amr` / `auth_time` 为后续资源服务器 step-up policy 提供标准输入。

### 负面与风险

- 在首个 Passkey 登记前，密码被窃取的攻击者仍可能抢先 enrollment；高权限账号生产启用前
  必须增加受控邀请或现有强身份复核。
- 丢失唯一 Passkey 时尚无自助恢复，阻止删除最后凭证会牺牲可用性以避免安全降级。
- 同步型 Passkey 的云同步账户和恢复机制成为外部信任依赖；Ainer 当前没有设备/备份状态策略。
- WebAuthn ceremony 使用 HTTP session 保存短时 options；多节点部署需要粘性会话或经 ADR
  设计的共享会话，当前没有 HA 证据。
- 本轮没有完成虚拟 authenticator 或真实设备上的完整签名 ceremony 自动化，不能据此宣布
  浏览器/设备兼容矩阵已通过。

## 安全、数据与隐私

Authenticator 私钥和本地生物识别模板永不发送到 Ainer。`public_key`、credential ID、
attestation object、transport、backup flags、sign counter 和 label 虽不是登录 secret，仍是
账号安全与可关联元数据，应按 Identity 安全数据限制读取、导出和保留。审计只保存 credential
标识、稳定 subject、操作、request ID 与时间，不保存密码、私钥、恢复码或原始认证响应正文。

WebAuthn challenge 保存在短时 HTTP session 中，registration/authentication 请求继续受 Origin、
RP ID、challenge、用户存在和用户验证检查；登记端点受登录 session 与 CSRF 保护。credential
ID 仍按可能泄漏个人可关联信息处理，不应进入普通日志或错误正文。

## 运维与迁移

Flyway 无条件创建协议和治理表，功能保持默认关闭。生产启用顺序：

1. 发布 schema 与代码但保持 `passkey.enabled=false`；
2. 配置最终 HTTPS 域名、RP ID、精确 Origin 和会话路由；
3. 在隔离环境完成真实浏览器/设备 registration、authentication、轮换与失窃模拟；
4. 为高权限账号建立受控 enrollment 和恢复值班流程；
5. 小范围启用并监控登记、认证失败、恢复请求和数据库错误，再扩大范围。

回滚优先关闭 Passkey 功能并保留表与审计，不删除凭证记录。对已要求 Passkey 的生产账号关闭
功能属于认证降级，必须经过安全审批和用户通知。已执行 migration 不得修改，修复使用新版本。

## 验收证据

当前已完成：

- 配置测试覆盖 HTTPS、RP scope、重复 Origin、非法路径、timeout 和仅 localhost HTTP 例外；
- PostgreSQL 18.3 空库执行官方协议表与 Ainer 生命周期/审计 migration；
- 真实 HTTP session 验证 registration options 强制 UV/resident key、使用配置 RP；
- 真实 Authorization Code + PKCE 流程验证无凭证密码 bootstrap、`amr=pwd`、`auth_time`；
- 已登记账号仅凭密码不能取得 authorization code；
- JDBC 测试验证登记/计数更新时间不重复审计、replacement 后软撤销、协议记录保留和最后凭证
  删除拒绝；并发撤销两个 ACTIVE credential 时只允许一个成功。

尚未完成：

- 虚拟 authenticator 与主流真实设备的完整 registration/authentication 签名 ceremony；
- Passkey 登录后 Token 的 `amr=mfa,pop` 端到端证据；
- 受控首因子 enrollment、恢复码/管理员双人恢复、通知、限速和风控；
- 同步/非同步 Passkey、backup state、克隆信号和多节点 session 的容量/故障验证。

## 参考

- [Spring Security 7.1 Multi-Factor Authentication](https://docs.spring.io/spring-security/reference/7.1/servlet/authentication/mfa.html)
- [Spring Security 7.1 Passkeys](https://docs.spring.io/spring-security/reference/7.1/servlet/authentication/passkeys.html)
- [W3C Web Authentication Level 3](https://www.w3.org/TR/webauthn-3/)
- [NIST SP 800-63B-4](https://pages.nist.gov/800-63-4/sp800-63b.html)
- [IANA Authentication Method Reference Values](https://www.iana.org/assignments/authentication-method-reference-values/authentication-method-reference-values.xhtml)
- [ADR-0005：Identity 与 OAuth 2.1 安全基线](0005-identity-and-oauth2-security-baseline.md)
