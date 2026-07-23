# Ainer 数据库与 Migration 手册

> 文档类型：长期规范 · 状态：生效 · 最近核对：2026-07-23 · 适用版本：`0.1.x`

## 1. 基线

Ainer 以 PostgreSQL 18.x 为业务数据库，使用 Flyway 管理 schema，使用 MyBatis 实现业务持久化。禁止用 H2 的兼容模式替代 PostgreSQL 行为验证。

UUID、时间、金额、约束和锁语义必须按 PostgreSQL 设计。SQL 参数必须绑定，不得拼接 tenant、subject、URL 或用户输入。

## 2. 当前数据库归属

| 发行物 | 逻辑数据库 | 所属 migration | 当前数据 |
|---|---|---|---|
| `ainer-server` | 业务库 `ainer` | Workspace、AI runtime | workspace、成员、授权审计热/归档、OWNER 恢复审计、AI invocation |
| `ainer-authorization-server` | 身份库 `ainer_auth` | Identity、OAuth authorization server | tenant、user、membership、access event/重放审计、client、authorization、consent、Passkey 协议/生命周期审计 |

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
| `ainer_passkey_credential` | Authorization Server | credential 与稳定 Identity subject 的 ACTIVE/REVOKED 生命周期 |
| `ainer_passkey_credential_audit` | Authorization Server | REGISTERED/REVOKED、request ID 与发生时间 |

`user_credentials` 不保存 authenticator 私钥或生物识别模板。Ainer 生命周期登记与官方协议记录在
同一事务提交；认证更新时间不重复写生命周期审计。撤销不物理删除官方记录，读取只返回 ACTIVE
credential。最后一个 ACTIVE credential 的自助撤销被拒绝；replacement 与旧 credential 通过
同一 user entity 串行保护，避免并发删除把账号降级到零因子。

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
mvn -pl ainer-module-workspace -am test
mvn -pl ainer-module-identity -am test
mvn -pl ainer-module-ai-runtime -am test
mvn -pl ainer-authorization-server -am test
```

发布前还必须在备份恢复出的接近真实规模数据库上验证升级耗时、锁等待和回滚方案。当前项目尚未建立生产备份恢复自动化，不能把空库测试等同于生产升级演练。

## 9. 回滚原则

应用回滚不等于 schema 回滚。优先让 migration 向前兼容上一版应用；数据库故障通过停止发布、恢复已验证备份或新增修复 migration 处理。任何手工生产 DDL/DML 都必须有审批、审计和执行记录，完成后补回版本化 migration，禁止环境永久漂移。
