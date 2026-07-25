# ADR-0015：Passkey 恢复（恢复码 + 管理员双人恢复）

- 状态：Accepted
- 日期：2026-07-25
- 决策者：待指定
- 取代：无
- 被取代：无

## 背景

ADR-0014 已建立 Passkey 优先认证基线：已登记账号在 `/oauth2/authorize` 与凭证管理端点
必须完成 WebAuthn 因子；最后一个 ACTIVE Passkey 不允许自助删除。Phase A 又用真实签名
ceremony 端到端验证了该门禁。

这带来一个必须解决的可用性死锁：**用户设过 Passkey、设备丢失或被盗**时，凭证在
`ainer_passkey_credential` 仍是 `ACTIVE`，密码登录被条件门禁拦截（`registered=true`），
而唯一凭证的自助删除又被最后凭证保护拒绝。账号在没有专用恢复流程前会被完全锁死。

当前事实：Ainer 尚无 Passkey 恢复码、管理员恢复、恢复通知、登录限速或多节点会话。
`AinerJdbcPasskeyCredentialRepository.revoke` 在删除最后一个 ACTIVE 凭证时抛出
`AccessDeniedException("...before recovery policy is configured")`，是 ADR-0014 预留的
恢复接缝。本 ADR 决定的就是填补这条接缝的第一条可运行恢复基线，不是“生产账号全渠道
通知与风控已交付”。

## 决策驱动因素

- 丢失唯一 Passkey 的合法用户必须能在可审计、可失败关闭的流程里恢复访问；
- 恢复流程不能让攻击者凭密码或暴力枚举绕过条件 MFA；
- 复用 Ainer 已验证的双人审批模式（ADR-0010、Workspace owner recovery、Identity event replay），
  不自造审批状态机；
- 私钥永不离开 authenticator；恢复码是高熵、一次性、只保存哈希的服务端凭证；
- 恢复是高风险操作，必须写独立高风险审计；账号所有者通知在 ADR-0014 中要求，
  但 Ainer 当前无联系字段与通知基础设施，需要明确取舍；
- 受控首次 enrollment（高权限账号的邀请/审批 gate）不属于本切片，留待后续 ADR。

## 备选方案

### 方案 A：只保留密码 bootstrap，不做恢复

实现最简，但已登记账号的密码 bootstrap 被条件门禁关闭；失去唯一 Passkey 的用户被永久锁死，
只能手工停用账号。可用性不可接受，拒绝。

### 方案 B：自助“忘记 Passkey”邮件链接

业界常见，但 Ainer Identity 账号当前没有 email/手机字段，也没有邮件/SMS 通道、模板、退订
与可达性审计。在本切片新建整条通知链路超出恢复本身范围，且邮件链接本身可钓鱼，与 Passkey
抗钓鱼方向相悖。保留为后续部署选项，不在本 ADR 落地。

### 方案 C：恢复码 + 管理员双人恢复（采用）

恢复码：首次登记 Passkey 时签发一组高熵一次性码，只保存哈希；用户在密码登录后用一枚恢复码
触发自助恢复，吊销全部 ACTIVE Passkey 并允许重新 bootstrap。
管理员双人恢复：用户连恢复码也丢失时，由两个不同服务身份走 request/approve 两阶段强制吊销。
两者都写独立高风险审计，复用 ADR-0010 的双人审批骨架。采用。

### 方案 D：TOTP 作为恢复 fallback

ADR-0014 已把 TOTP 排除在抗钓鱼主因子之外，作为受限恢复 fallback 仍可评估。但 TOTP 种子
同样需要安全分发与恢复，不解决根问题，且增加第二个需保护的秘密。本 ADR 不引入 TOTP，
保留为未来候选。

## 决策

### 1. 恢复码（自助）

1. 首次成功登记 Passkey 后，服务端用密码学随机数生成 **8 枚**单次使用恢复码，每枚至少
   **20 字节**熵，按固定分段格式呈现给用户一次；明码不落库、不进日志、不进审计。
2. 数据库只保存每枚恢复码的**哈希**（复用 Identity 现有 `DelegatingPasswordEncoder`/bcrypt），
   以及 `status`（`ACTIVE`/`USED`）、`used_at`、`attempt_count`、`subject_id` 与审计元数据。
   哈希算法不随密码策略隐式漂移：恢复码记录显式携带算法前缀。
3. 恢复码签发与刷新记 `REGISTERED`/`ROTATED` 审计；明码仅在签发/刷新响应里返回一次，
   与 OAuth client secret 同样“丢失即重发、不可找回”。
4. 自助恢复端点要求已通过密码认证的主体本人提交一枚明文恢复码。校验流程在同一事务：
   按 `subject_id` 锁定其全部恢复码记录（`FOR UPDATE`），找到匹配哈希且 `ACTIVE` 的记录，
   否则递增 `attempt_count`；`attempt_count` 超过配置上限（默认 5）后该组恢复码锁定，
   只能走管理员双人恢复。
5. 校验通过的事务：把该枚恢复码置 `USED`，**吊销该 subject 全部 `ACTIVE` Passkey**
   （复用 `AinerJdbcPasskeyCredentialRepository`，但走一条允许越过最后凭证保护的新内部路径），
   写 `RECOVERED` 审计，提交。提交后 `registered=false`，用户可走正常密码 bootstrap 重新登记。
6. 恢复码吊销 Passkey 不复用自助 `DELETE /webauthn/register/{id}`；它是一个独立的、
   服务端在已验证恢复码后批量软撤销的内部操作，不受条件 MFA 门禁约束。

### 2. 管理员双人恢复（他助）

7. 新增默认关闭的控制面 `/internal/passkey-recovery/tenants/{tenantId}/...`，复刻
   `WorkspaceOwnerRecoveryController` 结构：`@ConditionalOnProperty`、controller 本地
   `passkey.recovery.request[.all]` / `passkey.recovery.approve[.all]` scope 常量、
   `requireTenantAccess` 助手、`actor_type=SERVICE` 由安全链强制。
8. `PasskeyRecoveryService.requestRecovery(requesterServiceId, tenantId, subjectId,
   incidentReference, approvalTtl)` 与 `approveAndExecute(approverServiceId, tenantId, requestId)`
   均为 `@Transactional`，逐字复刻 Workspace owner recovery 的不变量：
   - `REQUESTED`/`EXECUTED`/`EXPIRED` 状态机；
   - `expireOpenRequests` 先清扫过期请求；
   - approve 时 `SELECT … FOR UPDATE` 锁请求、`requested_by <> approver` 双重校验（Java + SQL + CHECK）、
     15 分钟默认 TTL（≤1 天）、CAS `markExecuted`；
   - approve 副作用：吊销目标 subject 的全部 `ACTIVE` Passkey（同样越过最后凭证保护），
     写 `RECOVERED` 审计；不重新激活任何已 `REVOKED` 凭证。
9. `incidentReference` 只接受 `^[A-Za-z0-9._:@/-]{1,128}$`，不接受自由文本、Token 或客户数据。

### 3. 审计与数据

10. 新增 `ainer_passkey_security_operation_audit`（或在 `ainer_passkey_credential_audit` 的
    operation CHECK 增加 `RECOVERED`/`ROTATED`，二选一由实现核对迁移代价后定）。恢复码签发/刷新、
    自助恢复、管理员恢复的 `REQUESTED`/`EXECUTED` 阶段都写一行，`(operation_id, phase)` 偏唯一。
    审计不含恢复码明文、私钥或密码。
11. 全部新表与 CHECK 放在新 Flyway migration（`ainer-authorization-server`，版本 > `V202607231100`）。
    已发布 migration 不修改；若需放宽 `ainer_passkey_credential_audit` 的 operation CHECK，
    用新 migration `ALTER … DROP CONSTRAINT … ADD CONSTRAINT`。

### 4. 通知与限速（本切片的明确边界）

12. **通知暂不在本切片交付**。ADR-0014 要求“因子替换必须通知账号所有者”，但 Ainer 当前
    无联系字段、无通知通道。本切片只满足其中“写独立高风险审计”；明码可达通知（含联系字段
    设计、模板、可达性审计、退订）整体推迟到通知切片（原计划 Phase D），并在 `project-status.md`
    显式记录为已知缺口。生产启用恢复前必须评估该缺口。
13. **限速**：恢复码校验自带 per-subject `attempt_count` + 锁定（决策 4），这是恢复码防暴力的
    最小必要门禁，不依赖通用限速框架。通用限速（options/登录端点）仍由后续限速切片交付，
    不在本 ADR 宣称完成。

## 后果

### 正面

- 失去唯一 Passkey 的合法用户有可审计的自助恢复路径，不再被永久锁死；
- 恢复码只存哈希、一次性、限速锁定，暴力枚举与数据库泄露都不直接暴露可用秘密；
- 管理员恢复复用已验证的双人审批骨架，单点凭据被盗无法独立完成恢复；
- 恢复事件全部进入高风险审计，为未来通知与风控提供事实来源。

### 负面与风险

- 恢复成功后用户回到“仅密码”状态（`registered=false`，门禁不再要求 WebAuthn），直到重新登记。
  恢复窗口内账号退化为密码级保障；本 ADR 不强制恢复后立即重新登记，留待 step-up/assurance policy；
- 通知缺失意味着恢复发生时账号所有者不会被主动告知；生产启用前必须接受该风险或先做通知切片；
- 恢复码在签发时只返回一次，用户丢失即需管理员恢复，与 OAuth client secret 同样的运维纪律；
- 自助恢复吊销“全部”ACTIVE Passkey 是一刀切，多设备用户会连带吊销其他仍可用的凭证——
  这是“安全优先于便利”的取舍，未来可评估更细粒度吊销。

## 安全、数据与隐私

恢复码明文永不入库、入日志、入审计或错误正文；只保存 bcrypt 哈希与算法前缀。`attempt_count`
与 `used_at` 是账号安全相关元数据，按 Identity 安全数据同等限制读取。恢复端点要求密码认证
本人 + 明文恢复码双因素；管理员恢复要求两个不同 SERVICE 身份、tenant 二次绑定、受限
`incidentReference`。审计只保存稳定 subject、operation、phase、request id 与时间。

恢复吊销凭证复用既有 `ainer_passkey_credential` 软撤销（`status='REVOKED'`、`version+1`、
保留协议记录），不物理删除；`findByCredentialId`/`findByUserId` 继续对 REVOKED 失败关闭。

## 运维与迁移

发布顺序：

1. 发布 schema（恢复码表、恢复请求表、审计调整）与代码，保持 `passkey.recovery.enabled=false`
   与管理员控制面默认关闭；
2. 在受控初始化窗口建立彼此独立的 request/approve SERVICE client，把相应 scope 加入 operator
   白名单与 `client-control.allowed-scopes`；不得把 request 与 approve scope 授予同一 client；
3. 决定自助恢复是否默认开启（`passkey.recovery.self-service.enabled`）；生产环境与条件门禁、
   审计、（未来的）通知一并评估；
4. 启用后监控：恢复码签发/刷新/校验/锁定计数、管理员恢复 request/execute 计数、自助恢复
   后未重新登记的账号停留时长。

回滚优先关闭控制面与自助恢复开关，保留新表、恢复码哈希与审计记录，不删除已形成的恢复证据。
已执行 migration 不得修改，修复使用新版本。

## 验收证据

2026-07-25 已完成（全 Reactor `mvn test` 通过，PostgreSQL Testcontainers 实际执行，0 跳过）：

- 恢复码自助流程在真实 HTTP 会话与 PostgreSQL 上跑通：真实 Passkey 登记后签发 8 枚高熵一次性
  码（明文仅返回一次，库内只存 bcrypt 哈希）；密码登录本人用一枚明码赎回，该账号全部 ACTIVE
  Passkey 被吊销并写 `SELF_RECOVERY` 安全操作审计，恢复码置 `USED`；
- 管理员双人恢复在 service 层用真实事务验证：申请者建立 `REQUESTED`，同服务批准被拒
  （`RECOVERY_APPROVER_MUST_DIFFER`），不同服务批准成功、吊销目标全部 Passkey，`(operation_id,
  phase)` 偏唯一审计为 `[REQUESTED, EXECUTED]`，重复批准被拒（`RECOVERY_REQUEST_CONFLICT`）；
- 恢复吊销复用 `ainer_passkey_credential` 软撤销（`status='REVOKED'`、`version+1`、保留协议记录），
  普通自助删除的最后凭证保护不受影响；
- Flyway 从空库执行八份 Authorization Server migration，含恢复码、锁定计数、恢复请求与安全
  操作审计四张新表与全部 CHECK/偏唯一约束。

尚未完成：

- 控制器层 SERVICE/tenant/scope 拒绝路径与限速边界（通用限速框架）的 HTTP 级自动化测试；
- 账号所有者可达通知（含 Identity 联系字段与通知通道），仍为已知缺口；
- 真实设备/浏览器兼容矩阵、step-up assurance policy 与多节点 session。

## 参考

- [ADR-0010：安全运维双人审批与授权审计生命周期](0010-security-operations-and-audit-lifecycle.md)
- [ADR-0014：Passkey 优先的人员认证与条件 MFA 基线](0014-passkey-first-human-authentication.md)
- [NIST SP 800-63B-4：Authenticator Recovery](https://pages.nist.gov/800-63-4/sp800-63b.html)
- [OWASP：Authentication Cheat Sheet — Recovery](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
