# Ainer 数据库与 Migration 手册

> 文档类型：长期规范 · 状态：生效 · 最近核对：2026-07-30 · 适用版本：`0.1.x`

本文负责数据库归属、当前表、Flyway 执行、安全演进和运行验证。新表/字段设计、命名、类型、
约束、索引、tenant 完整性和评审门禁以
[`database-design-standard.md`](database-design-standard.md) 为权威规范；发生冲突时不得默默选取，
应修复文档或通过 ADR 明确偏离。

## 1. 基线

Ainer 以 PostgreSQL 18.x 为唯一业务数据库基线，使用 Flyway 管理 schema，使用
MyBatis-Plus/MyBatis 实现业务持久化。MyBatis-Plus 只作为 infrastructure 的简单 CRUD 与分页
增强，不改变显式 SQL、Repository 端口、事务或数据所有权。项目不提供 MySQL、H2 或旧
PostgreSQL 方言兼容；禁止用 H2 compatibility mode 替代 PostgreSQL 行为验证。新 Ainer 持久化
ID 与 tenant 类型遵守
[ADR-0020](decisions/0020-postgresql-native-greenfield-baseline.md)：ID 默认 UUIDv7，
`tenant_id` 全链路统一 UUID。

UUID、时间、金额、约束和锁语义必须按 PostgreSQL 设计。SQL 参数必须绑定，不得拼接 tenant、subject、URL 或用户输入。

### 1.1 MyBatis-Plus 使用边界

- 统一入口是 `ainer-starter-persistence`，使用 Boot 4 专用 MyBatis-Plus starter；不维护原生与
  Plus 两套 starter。
- `BaseMapper`、Wrapper、Page 和 ORM 注解只允许在 infrastructure；application、domain 和 API
  不暴露这些类型，也不默认采用 `IService`、`ServiceImpl` 或 ActiveRecord。
- 现有 Mapper XML 继续有效。CTE、锁、`RETURNING`、advisory lock、outbox、审计归档和稳定游标
  继续使用显式 SQL。
- 全局 `IdType.AUTO` 让数据库 `DEFAULT uuidv7()` 生成并回填 ID；禁止 `ASSIGN_ID` /
  `ASSIGN_UUID`。不默认启用逻辑删除或 MetaObject 自动填充。
- tenant interceptor 当前不启用，所有租户查询仍显式传入并绑定可信 tenant。分页最大单页
  `100`，并位于 interceptor 链尾。

完整决策见 [ADR-0028](decisions/0028-mybatis-plus-infrastructure-baseline.md)。

## 2. 当前数据库归属

| 发行物 | 逻辑数据库 | 所属 migration | 当前数据 |
|---|---|---|---|
| `ainer-server` | 业务库 `ainer` | Workspace、AI runtime | workspace、成员、授权审计热/归档、OWNER 恢复审计、AI invocation |
| `ainer-authorization-server` | 身份库 `ainer_auth` | Identity、OAuth authorization server | tenant、user、membership/成员变更审计、access event/重放审计、client、authorization、consent、Passkey 协议/生命周期/恢复审计 |

数据库名只是本地示例。生产可以改名，但两个发行物不得通过共享表形成隐式模块调用。未来拆服务时，每个模块保留自己的数据所有权，跨边界通过契约或可靠事件同步。

## 3. 当前表前缀

| 前缀 | 所有者 |
|---|---|
| `ainer_workspace*` | Workspace |
| `ainer_ai_*` | AI runtime |
| `ainer_identity_*` | Identity |
| `oauth2_*` | Authorization Server 协议存储 |
| `ainer_oauth_*` | Authorization Server 的 Ainer-owned 生命周期与操作审计 |
| `user_entities` / `user_credentials` | Spring Security WebAuthn 官方 JDBC 协议存储 |
| `ainer_passkey_*` | Authorization Server 的 Passkey 生命周期与操作审计 |

Identity Directory 是安全查询契约，不授权 Workspace 直接查询 `ainer_identity_*`。access-event outbox 的事实归 Identity 所有，下游消费状态不能反写 Identity 业务表。Workspace 只拥有 `ainer_workspace_identity_event_receipt` 与本地 membership 的 `REVOKED` 状态。

M4.2 新增表仍归各模块独立所有：

| 表 | 所有者 | 用途 |
|---|---|---|
| `ainer_identity_access_event_replay_request` | Identity | 耗尽事件的短时双人重放申请 |
| `ainer_identity_security_operation_audit` | Identity | 重放申请与成功执行阶段审计 |
| `ainer_workspace_owner_recovery_request` | Workspace | 无 ACTIVE OWNER 时的双人恢复申请 |
| `ainer_workspace_security_operation_audit` | Workspace | OWNER 恢复与 SIEM 导出操作审计 |
| `ainer_workspace_authorization_audit_archive` | Workspace | 授权审计同库归档，保留原 audit ID 和调查字段 |

M4.3 不新增表或 migration。人员 Token 在线状态先按主键检查 tenant、user、membership 当前状态，再按 `(tenant_id, subject_id, occurred_at DESC, id DESC)` 从 `ainer_identity_access_event` 读取最新事件时间。该查询复用 `idx_ainer_identity_access_event_subject`；`issuedAt` 早于或等于最新事件时 inactive。不得为此复制 OAuth Token 正文、建立第二张自研 Token 表或绕过 Identity 的事务事实。RFC 7009 仍修改 Spring Authorization Server 官方 `oauth2_authorization` 元数据。

后续 tenant 服务 client 生命周期切片新增：

| 表 | 所有者 | 用途 |
|---|---|---|
| `ainer_oauth_service_client` | Authorization Server | managed client 与官方 registered client 的关联、tenant、ACTIVE/RETIRED、replacement 和乐观版本 |
| `ainer_oauth_service_client_audit` | Authorization Server | CREATED/ROTATED/RETIRED 操作、operator、request/change reference 和时间 |

官方 `oauth2_registered_client` 仍是协议配置与 secret hash 的唯一存储；Ainer 表不复制 hash、
Token、grant JSON 或 redirect URI。生命周期表以 `registered_client_id` 外键关联官方表，退役不
删除协议记录。按 `client_id` 的认证查找会拒绝 RETIRED；按内部 ID 的 authorization 历史重建仍
允许读取，再由 authorization service 把该 client 的 Token 在线视为 inactive。审计表故意没有
secret 字段。

M4.6 Passkey 切片新增：

| 表 | 所有者 | 用途 |
|---|---|---|
| `user_entities` | Spring Security WebAuthn adapter | username 与 WebAuthn opaque user handle |
| `user_credentials` | Spring Security WebAuthn adapter | credential 公钥、计数器、transport、backup/attestation 协议材料 |
| `ainer_passkey_credential` | Authorization Server | credential 与稳定 Identity subject/account 的 ACTIVE/REVOKED 生命周期 |
| `ainer_passkey_credential_audit` | Authorization Server | REGISTERED/REVOKED、request ID 与发生时间 |
| `ainer_passkey_recovery_code` / `_lockout` | Authorization Server | 恢复码 bcrypt hash 与按 subject/account 的失败锁定 |
| `ainer_passkey_recovery_request` | Authorization Server | 管理员双人恢复申请 |
| `ainer_passkey_security_operation_audit` | Authorization Server | 恢复申请与执行审计 |
| `ainer_passkey_enrollment_grant` | Authorization Server | `require-invite` 首枚 Passkey 的短时授权 |

`user_credentials` 不保存 authenticator 私钥或生物识别模板。S4 共存迁移后，Passkey 生命周期与安全
记录的 foundation 行使用 `account_id`，legacy 行继续使用 `(tenant_id, subject_id)`；foundation recovery/
enrollment 行不携带 tenant。Ainer 生命周期登记与官方协议记录在
同一事务提交；认证更新时间不重复写生命周期审计。撤销不物理删除官方记录，读取只返回 ACTIVE
credential。最后一个 ACTIVE credential 的自助撤销被拒绝；replacement 与旧 credential 通过
同一 user entity 串行保护，避免并发删除把账号降级到零因子。

M4.7 管理面新增：

| 表 | 所有者 | 用途 |
|---|---|---|
| `ainer_identity_member_audit` | Identity | tenant 成员 ADDED/REACTIVATED/ROLE_CHANGED/REMOVED 的同事务安全审计 |

成员管理 API 和 migration 只装配在 Identity 权威运行时 `ainer-authorization-server`，业务
`ainer-server` 不创建 `ainer_identity_*` 表。Passkey 恢复码、锁定、恢复申请、安全操作审计与
enrollment grant 都通过 `(tenant_id, subject_id)` 复合外键绑定
`ainer_identity_membership(tenant_id, user_id)`；应用层还要求目标为 ACTIVE default membership，防止
只凭全局 subject 对另一个 tenant 建立恢复或 enrollment 安全记录。

M4.8A 预配、激活与控制面新增：

| 表 | 所有者 | 用途 |
|---|---|---|
| `ainer_identity_tenant_provisioning_request` | Identity | tenant/OWNER 标识预留、operator 级幂等、状态与 TTL；不是可授权核心身份 |
| `ainer_identity_activation_grant` | Identity | 新用户高熵 secret 的 SHA-256 摘要、短 TTL、失败次数与单次消费状态 |
| `ainer_identity_notification_outbox` | Identity | 带 key version 的 AES-GCM 密文、租约、重试和网关发布状态；不保存可查询联系地址/正文，发布或取消后销毁 payload |
| `ainer_identity_notification_delivery_receipt` | Identity | 外部网关归一化的唯一 `DELIVERED|FAILED` 终态；不保存正文、联系地址或供应商原始 body |
| `ainer_identity_platform_operation_audit` | Identity | `REQUESTED/ACTIVATED/CANCELLED/EXPIRED` 的同事务审计与 SERVICE/USER/SYSTEM/grant actor 类型 |

预配 request 以 `(requested_by_service_id, idempotency_key)` 唯一约束保存稳定幂等结果，并只对
`REQUESTED` 状态建立 tenant code 部分唯一索引；不存在核心用户时，open request 还对规范化
username 建立部分唯一索引。过期记录保留历史，不复用其预生成 UUID；新请求使用新幂等键和新 ID。
`owner_user_exists=true` 允许同一 ACTIVE 用户成为多个待接受 tenant 的候选 OWNER；接受事务仍会
锁定并重新检查实时用户状态与目标 subject。request、grant、notification 和平台 audit 的独立 ID
均使用 PostgreSQL 18 `uuidv7()` 并有版本 check；预留给新 tenant/subject 的 ID 也由同一数据库
生成器取得。

平台预配与首租户 bootstrap 使用相同的
`identity:tenant-code:<code>` / `identity:username:<username>` PostgreSQL transaction advisory
lock 命名空间，避免两个入口并发绕过彼此。申请事务只写 request、grant（仅新用户）、加密 outbox
与 audit，不写 `ainer_identity_tenant`、`ainer_identity_user` 或
`ainer_identity_membership`。grant 只有 `secret_hash`，outbox 只有 `protected_payload BYTEA`
与 key version；联系地址、正文和激活明文没有可查询列。

通知网关返回 2xx 后，outbox 在 lease owner 条件下进入 `PUBLISHED`，记录 `published_at`，把
`payload_key_version` 改为 `destroyed`、覆盖 `protected_payload` 并记录
`payload_destroyed_at`；请求在投递前取消时使用同一销毁规则。数据库状态因此只证明通知网关已
持久接收，不证明最终邮件/短信送达。覆盖不能清除已经进入 WAL 或备份的数据，密钥轮换、备份访问
和通知域保留策略仍是必要控制。

终态回执表以 `(gateway_client_id,gateway_event_id)` 和 `notification_id` 两个唯一约束同时限制
上游事件重放与单 notification 终态；回执自身 ID、notification ID 都有 UUIDv7 check。
`DELIVERED` 必须没有 `failure_code`，`FAILED` 必须具有受限大写稳定码，供应商发生时间不能超过
Ainer 接收时间五分钟以上。外键只能证明 notification 存在；“仅 `PUBLISHED` 可登记”需要锁定
outbox 后由应用事务验证，因为跨表 check constraint 不能可靠表达该规则。

新用户成功消费 grant 时，tenant、user、默认 ACTIVE OWNER membership、grant `CONSUMED`、
request `ACTIVATED` 与阶段审计在同一事务提交；已有用户本人接受时不创建 user，新增 OWNER
membership 的 `is_default=false`，不覆盖原登录落点。错误次数达到上限会把 grant 置 `LOCKED`、
request 置 `CANCELLED` 并取消未投递 outbox；到期会置 `EXPIRED`。任何核心写入失败都会回滚，
因此失败或过期请求不能产生孤儿 ACTIVE tenant。

平台显式取消锁定 provisioning request，只允许 `REQUESTED -> CANCELLED`。新用户申请必须恰好把
一条 ACTIVE grant 迁移为 `CANCELLED`；已有用户申请必须没有 grant，实际影响行数与该不变量不符
时整笔事务失败关闭。PENDING/FAILED notification 同事务转为 `CANCELLED` 并执行 payload 销毁，
随后写入唯一 `(operation_id, phase='CANCELLED')` 审计；重复取消不新增审计。已 `PUBLISHED`
notification 不能召回，但其可解密 payload 已在发布确认时销毁，关联 grant 仍会被取消。

平台 tenant/user 分页直接读取核心表的安全列，分别按唯一业务键 `code,id` 和 `username,id`
排序，使用受限 `LIMIT/OFFSET`（`page >= 1`、`size <= 100`）并返回总数。查询不得选择
`password_hash`，也不连接 OAuth、notification 或 activation 表；provisioning reservation 在
真正激活进入核心表前不会出现在列表中。

## 4. Migration 命名与不可变性

文件位于所属模块：

```text
src/main/resources/db/migration/VYYYYMMDDHHMM__lower_snake_description.sql
```

规则：

- 已在任何共享环境执行的 migration 永不修改、重命名或重新排序；
- 修复通过新 migration 完成；
- 一个 migration 表达一个可审查目的；
- DDL 必须明确 `NOT NULL`、默认值、check/unique/foreign key 与索引意图；
- 大表回填、锁表 DDL 和并发索引必须单独设计发布步骤，不能直接照搬小表写法；
- 不在 migration 中写环境特定账号、真实 tenant 或秘密；
- 新模块时间戳必须全仓唯一，避免多个 classpath migration 冲突。

新增前先检查：

```bash
find . -path '*/src/main/resources/db/migration/*.sql' -type f -print | sort
```

## 5. 安全变更模式

对已有非空表增加必填列时，优先采用：增加可空列或安全默认值、分批回填、验证数据、增加约束、最后移除临时默认值。是否拆成多个版本取决于数据量和发布是否允许锁表。

删除或重命名列使用 expand-contract：先增加新结构并支持双读/受控双写，再迁移数据和消费者，经过兼容窗口后用后续版本删除。破坏性 migration 必须有 ADR 或发布说明。

## 6. tenant 与完整性

- 所有租户业务查询和更新显式携带可信 tenant 条件；
- 复合外键用于证明子记录与父资源属于同一 tenant；
- 应用授权不能替代数据库唯一约束和 check constraint；
- 更新所有权、预算或 outbox 状态时使用可解释的锁或条件更新；
- Repository 方法签名应暴露 tenant，而不是从线程本地或请求头隐式读取。

## 7. 事务与 outbox

事务边界位于 application use case。数据库写入与其必须可靠发布的事件在同一事务提交。outbox relay 采用至少一次投递时，消费者必须按事件 ID 幂等。

Identity relay 使用以下状态机：

- 新事件为 `PENDING`，`available_at=occurred_at`，无 lease；
- 领取事务按 `available_at, occurred_at, id` 排序，以 `FOR UPDATE SKIP LOCKED` 选择小批量记录，写入 `lease_owner` / `lease_until` 并增加 `attempt_count`；
- 网络调用发生在领取事务提交之后；成功按 event ID + lease owner 置为 `PUBLISHED`，失败置为 `FAILED` 并推进 `available_at`；
- lease 到期后其他实例可以重新领取；达到 `max-attempts` 后 PENDING/FAILED 都归入 exhausted 指标，不再自动领取，数据仍保留供告警和人工处置。

Workspace 消费事务执行：`INSERT receipt ... ON CONFLICT DO NOTHING`，新事件才批量更新 membership，最后记录实际影响数。更新必须同时绑定 tenant、subject、`status IN ('PENDING','ACTIVE')` 与 `created_at <= occurred_at`。`REVOKED` 永不参与资源授权；重复、旧事件和跨 tenant 事件不能扩大影响范围。

耗尽事件重放审批事务会锁定申请与原事件，重新验证 tenant、耗尽状态、lease 和过期时间后，只重置事件的投递状态，不更换 event ID 或事件内容。OWNER 恢复审批事务锁定 Workspace，重新验证无 ACTIVE OWNER、存在 REVOKED OWNER 和目标 ACTIVE 成员，然后仅提升目标成员。两类请求都由部分唯一索引阻止同一目标存在多个开放申请。

Workspace 授权审计归档使用小批量 CTE：以 `FOR UPDATE SKIP LOCKED` 选择过期热记录，`INSERT ... ON CONFLICT` 写入归档，仅在同 ID 归档已存在时删除热记录。统一查询和 SIEM 导出读取热/归档并集。归档表本身不自动删除；最终删除、法律保留和外部不可变存储需要另立策略。

禁止：事务提交后才临时补写 outbox、用进程内异步事件代替可靠事实、在一个本地事务中假装覆盖另一个服务数据库。

## 8. 本地运行与验证

业务应用和 Authorization Server 使用不同空库：

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/ainer
export SPRING_DATASOURCE_USERNAME=ainer
export SPRING_DATASOURCE_PASSWORD='local-only-password'
```

应用启动时 Flyway 自动执行。自动化验证由 Testcontainers 从空库执行全部 migration：

```bash
./mvnw -pl ainer-module-workspace -am test
./mvnw -pl ainer-module-identity -am test
./mvnw -pl ainer-module-ai-runtime -am test
./mvnw -pl ainer-authorization-server -am test
```

2026-07-26 的 PostgreSQL 18.4 隔离 schema smoke 从空库重放 Identity 全部 9 份 migration，并
验证回执 ID 为 UUIDv7、合法终态可写、重复 notification 被唯一约束拒绝。该 smoke 还揭示 SQL
三值逻辑会让仅写 `failure_code ~ pattern` 的 check 在 NULL 时得到 UNKNOWN 并放行，因此最终
migration 对 `FAILED` 显式增加 `failure_code IS NOT NULL`；修正后 NULL 失败码被数据库拒绝。
隔离 schema 已删除。该实测结果补充 DDL 真实性验证，但不替代发布候选环境的完整 Testcontainers/HTTP
门禁。

发布前还必须在备份恢复出的接近真实规模数据库上验证升级耗时、锁等待和回滚方案。当前项目尚未建立生产备份恢复自动化，不能把空库测试等同于生产升级演练。

## 9. 回滚原则

应用回滚不等于 schema 回滚。优先让 migration 向前兼容上一版应用；数据库故障通过停止发布、恢复已验证备份或新增修复 migration 处理。任何手工生产 DDL/DML 都必须有审批、审计和执行记录，完成后补回版本化 migration，禁止环境永久漂移。
