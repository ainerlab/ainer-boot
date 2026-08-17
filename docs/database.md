# Ainer 数据库与 Migration 手册

> 文档类型：长期规范 · 状态：生效 · 最近核对：2026-08-07 · 适用版本：`0.1.x`

本文负责数据库归属、当前表、Flyway 执行、安全演进和运行验证。新表/字段设计、命名、类型、
约束、索引、资源归属和评审门禁以
[`database-design-standard.md`](database-design-standard.md) 为权威规范；发生冲突时不得默默选取，
应修复文档或通过 ADR 明确偏离。

## 1. 基线

Ainer 以 PostgreSQL 18.x 为唯一业务数据库基线，使用 Flyway 管理 schema，使用
MyBatis-Plus/MyBatis 实现业务持久化。MyBatis-Plus 只作为 infrastructure 的简单 CRUD 与分页
增强，不改变显式 SQL、Repository 端口、事务或数据所有权。项目不提供 MySQL、H2 或旧
PostgreSQL 方言兼容；禁止用 H2 compatibility mode 替代 PostgreSQL 行为验证。新 Ainer 持久化
ID 遵守 [ADR-0020](decisions/0020-postgresql-native-greenfield-baseline.md)：ID 默认 UUIDv7，
所有身份与资源归属键都是 UUID（Greenfield 后不再有 `tenant_id` claim，见
[ADR-0033](architecture/0033-greenfield-atomic-cutover-execution-plan.md)）。

UUID、时间、金额、约束和锁语义必须按 PostgreSQL 设计。SQL 参数必须绑定，不得拼接
subject、URL 或用户输入。

### 1.1 MyBatis-Plus 使用边界

- 统一入口是 `ainer-starter-persistence`，使用 Boot 4 专用 MyBatis-Plus starter；不维护原生与
  Plus 两套 starter。
- `BaseMapper`、Wrapper、Page 和 ORM 注解只允许在 infrastructure；application、domain 和 API
  不暴露这些类型，也不默认采用 `IService`、`ServiceImpl` 或 ActiveRecord。
- 现有 Mapper XML 继续有效。CTE、锁、`RETURNING`、advisory lock、审计归档和稳定游标
  继续使用显式 SQL。
- 全局 `IdType.AUTO` 让数据库 `DEFAULT uuidv7()` 生成并回填 ID；禁止 `ASSIGN_ID` /
  `ASSIGN_UUID`。不默认启用逻辑删除或 MetaObject 自动填充。
- 无 tenant 多租户拦截器：所有资源查询显式携带并绑定可信 `workspace_id`/`account_id` 等归属
  键。分页最大单页 `100`，并位于 interceptor 链尾。

完整决策见 [ADR-0028](decisions/0028-mybatis-plus-infrastructure-baseline.md)。

## 2. 当前数据库归属

| 发行物 | 逻辑数据库 | 所属 migration | 当前数据 |
|---|---|---|---|
| `ainer-server` | 业务库 `ainer` | Workspace（`V202608070310`）、AI runtime（`V202608070320`） | workspace、成员、授权审计热/归档、OWNER 恢复审计、AI invocation |
| `ainer-authorization-server` | 身份库 `ainer_auth` | Identity（`V202608070300`）、OAuth authorization server（`V202608070330`） | HumanAccount、LoginIdentity、Credential、Profile、ServicePrincipal、client、authorization、consent、Passkey 协议/生命周期/恢复审计 |

数据库名只是本地示例。生产可以改名，但两个发行物不得通过共享表形成隐式模块调用。未来拆服务时，每个模块保留自己的数据所有权，跨边界通过契约或可靠事件同步。

## 3. 当前表前缀

| 前缀 | 所有者 |
|---|---|
| `ainer_workspace*` | Workspace |
| `ainer_ai_*` | AI runtime |
| `ainer_identity_*` | Identity |
| `ainer_authorization_*` | 通用授权（ADR-0030） |
| `oauth2_*` | Authorization Server 协议存储 |
| `ainer_oauth_*` | Authorization Server 的 Ainer-owned 生命周期与操作审计 |
| `user_entities` / `user_credentials` | Spring Security WebAuthn 官方 JDBC 协议存储 |
| `ainer_passkey_*` | Authorization Server 的 Passkey 生命周期与操作审计 |

Identity 与 Workspace 之间不共享表，跨模块关系通过显式服务契约而非共享表实现；Workspace 不得
直接查询 `ainer_identity_*`。canonical Workspace 只拥有 `workspace_id`、Human membership、
授权审计与本地安全操作审计。

### 2.1 业务库 `ainer`（`ainer-server`）

Workspace foundation baseline（`V202608070310`）：

| 表 | 用途 |
|---|---|
| `ainer_workspace` | 唯一业务资源；只有 `workspace_id`、名称、状态与时间，不存 tenant |
| `ainer_workspace_member` | Human membership（subject、ACTIVE/REVOKED 与角色），授权依据 |
| `ainer_workspace_authorization_audit` | 允许/拒绝决策热审计，独立事务 |
| `ainer_workspace_authorization_audit_archive` | 同库归档，保留原 audit ID 与调查字段 |
| `ainer_workspace_owner_recovery_request` | 无 ACTIVE OWNER 时的双人恢复申请 |
| `ainer_workspace_security_operation_audit` | OWNER 恢复与 SIEM 导出操作审计 |

AI runtime foundation baseline（`V202608070320`）：

| 表 | 用途 |
|---|---|
| `ainer_ai_invocation` / `_result` / `_feedback` | 调用审计、结果与反馈 |
| `ainer_ai_task` / `_task_run` / `_context_snapshot` | 任务与运行、上下文快照 |

通用授权 foundation baseline（`V202608070340`，ADR-0030 S1）：

| 表 | 用途 |
|---|---|
| `ainer_authorization_permission` | 权限目录投影（code PK，action/resource_type/risk_tier/audit_level/system_only/agent_delegable） |
| `ainer_authorization_role` | 角色聚合（UUIDv7 PK，code/name/system_role/status/version 乐观锁） |
| `ainer_authorization_role_permission` | 角色-权限关联（复合 PK role_id+permission_code，FK RESTRICT） |
| `ainer_authorization_subject_binding` | 绑定生命周期（UUIDv7 PK，subject_ref/role_id/scope_kind+scope 列/valid_from/until/status/version/revoked_*） |
| `ainer_authorization_change_audit` | 变更审计 append-only（actor/target/action/before-after version，no Token/body） |
| `ainer_authorization_decision_audit` | 决策审计 append-only（decision_id/requester/permission/resource/outcome/reason/evaluated_at） |

`scope_kind` CHECK 适配 Greenfield Workspace 语义：`GLOBAL`（workspace/resource 全 NULL）、
`WORKSPACE`（workspace_id 非空）、`RESOURCE`（workspace_id+resource_type+resource_id 全非空）。
GLOBAL binding 仅 SERVICE subject 持有（决策器强制）。

文件存储 baseline（`V202608140100`，ADR-0040）：

| 表 | 用途 |
|---|---|
| `ainer_file_object` | 存储对象元数据（storage_key 唯一、namespace、SHA-256、上传者三元组；UUIDv7 CHECK） |
| `ainer_file_audit` | 变更审计 append-only（UPLOADED/DELETED；file_id FK ON DELETE SET NULL，文件删除后审计保留） |
| `ainer_org_directory` | 组织目录容器（ADR-0042：Workspace 锚点，workspace+code 唯一） |
| `ainer_org_unit` | 组织单元（ROOT 每 Directory 唯一部分索引；复合 FK 阻止跨目录引用） |
| `ainer_org_unit_parent` | Unit 父关系（开放父关系每子唯一；递归 CTE 查询祖先） |
| `ainer_org_engagement` | 任职关系（btree_gist + tstzrange EXCLUDE：同目录同 Subject 非 REVOKED 有效期不重叠；employeeNumber 目录内唯一不复用） |
| `ainer_org_unit_assignment` | 任职分配（开放 PRIMARY 每 Engagement 唯一；复合 FK 到 engagement/unit） |
| `ainer_org_position` | Unit 内岗位（orgUnit 不可变；directory+unit+code 唯一） |
| `ainer_org_position_assignment` | 岗位任职（5 列复合 FK 锚定同 Engagement 同 Unit 的 UnitAssignment） |
| `ainer_org_change_audit` | 组织变更审计 append-only（DIRECTORY/UNIT/ENGAGEMENT/…实体类型枚举） |
| `ainer_authorization_subject_set_binding` | 集合绑定（ADR-0042 O2：set 三元组 + workspace 锚定；CHECK 禁 GLOBAL 且 scope.workspace = set.workspace；加性 migration V202608141000） |
| `ainer_authorization_acting_grant` + `_permission` | 一层委托（ADR-0043 A1：principal→agent；permission 子表 FK 目录；GLOBAL 不可表达；decision audit 增 agent/grant 关联列；V202608150100） |
| `ainer_ai_agent_definition` | Agent 定义注册表（ADR-0043 A1：code+version 唯一、ACTIVE/RETIRED；ai-runtime 所有，V202608150200） |
| `ainer_knowledge_object` / `_revision` / `_revision_lineage` / `_source` / `_evidence` / `_lifecycle_event` | Knowledge Foundation（ADR-0044 K1/K2：不可变 Revision + SUPERSEDES lineage + asOf 精确解析 + append-only 生命周期；V202608150300） |

Dictionary 审计 baseline（`V202608140200`，ADR-0040 管理面加固）：

| 表 | 用途 |
|---|---|
| `ainer_dictionary_audit` | 类型/项变更审计 append-only（operation/target_kind/target_id/actor；UUIDv7 CHECK） |

Notification 审计 baseline（`V202608140300`，ADR-0040 管理面加固）：

| 表 | 用途 |
|---|---|
| `ainer_notification_audit` | 模板变更审计 append-only（TEMPLATE_CREATED/UPDATED/STATUS_CHANGED；FK ON DELETE RESTRICT） |

### 2.2 身份库 `ainer_auth`（`ainer-authorization-server`）

Identity foundation baseline（`V202608070300`）：

| 表 | 用途 |
|---|---|
| `ainer_identity_human_account` | 人员账号、状态与单调递增 `security_epoch` 撤销版本；人员 `sub` 是 HumanAccount UUID |
| `ainer_identity_human_profile` | display name 等公开投影 |
| `ainer_identity_login_identity` | 登录标识到账号的映射 |
| `ainer_identity_credential` | 凭据密码状态投影 |
| `ainer_identity_oauth_client_binding` | 账号与 OAuth client 绑定关系 |
| `ainer_identity_service_principal` | SERVICE 主体，同样携带 `security_epoch`；服务 `sub` 是 ServicePrincipal UUID |

人员 Token 在线状态通过 `sec_epoch` claim 与账号/主体的 `security_epoch` 比较实现
（`RevocationAwareOAuth2AuthorizationService`）：`findByToken` 时账号已禁用或 epoch 不匹配即视为
inactive，不需要 access-event outbox，也不创建自研 Token 表。RFC 7009 仍修改 Spring Security
官方 `oauth2_authorization` 元数据。Identity 不再保存 tenant/membership；`sub` 与 `sec_epoch`
之外的 claim 不参与授权。

Authorization Server foundation baseline（`V202608070330`）：

| 表 | 用途 |
|---|---|
| `oauth2_registered_client` / `oauth2_authorization` / `oauth2_authorization_consent` | Spring Security 官方 JDBC 协议存储 |
| `user_entities` / `user_credentials` | Spring Security WebAuthn 官方协议存储 |
| `ainer_passkey_credential` / `_audit` | Passkey 生命周期与审计，绑定 `account_id` |
| `ainer_passkey_recovery_code` / `_lockout` / `_request` / `_security_operation_audit` / `_enrollment_grant` | 恢复码、失败锁定、双人恢复申请与 `require-invite` 首枚 Passkey 授权 |
| `ainer_oauth_browser_client` / `_audit` | browser client 生命周期与 `CREATED/ROTATED/RETIRED` 审计；不复制 secret |

`user_credentials` 不保存 authenticator 私钥或生物识别模板。Passkey 生命周期与安全记录全部使用
`account_id` 绑定 HumanAccount。Ainer 生命周期登记与官方协议记录在同一事务提交；认证更新时间不
重复写生命周期审计。撤销不物理删除官方记录，读取只返回 ACTIVE credential；最后一个 ACTIVE
credential 的自助撤销被拒绝，replacement 与旧 credential 通过同一 user entity 串行保护。

官方 `oauth2_registered_client` 仍是协议配置与 secret hash 的唯一存储；Ainer 表不复制 hash、
Token、grant JSON 或 redirect URI。browser client 生命周期表以 `client_id` 关联官方表，退役不
删除协议记录。按 `client_id` 的认证查找会拒绝 RETIRED；按内部 ID 的 authorization 历史重建仍
允许读取，再由 authorization service 把该 client 的 Token 在线视为 inactive。审计表故意没有
secret 字段。

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

## 6. 资源与完整性

- 所有业务资源查询和更新显式携带可信资源条件（Workspace 使用 `workspace_id`，Identity 使用
  `account_id`/`principal_id`），不从请求头或线程本地隐式读取身份条件；
- 复合外键用于证明子记录与父资源属于同一归属（如 Passkey 生命周期绑定 HumanAccount）；
- 应用授权不能替代数据库唯一约束和 check constraint；
- 更新所有权、预算或状态时使用可解释的锁或条件更新；
- Repository 方法签名应显式暴露资源归属键，而不是从线程本地或请求头隐式读取。

## 7. 事务与可靠发布

事务边界位于 application use case。数据库写入与其必须可靠发布的通知在同一事务提交；跨运行时
通知采用 outbox 且至少一次投递时，消费者必须按事件 ID 幂等。

Identity 在线撤销不再依赖 outbox：人员 Token 的 `sec_epoch` claim 与
`ainer_identity_human_account.security_epoch` 逐请求比较（`RevocationAwareOAuth2AuthorizationService`），
账号禁用即时生效，不需要跨运行时事件。若未来引入需异步投递的业务通知，仍必须使用带 lease 的
outbox：`PENDING` -> `PUBLISHED`/`FAILED` -> exhausted，网络调用在领取事务提交之后，失败按
`available_at` 推进，消费者按 event ID + lease owner 幂等确认。

Workspace OWNER 恢复审批事务按 workspace 锁定并重新验证无 ACTIVE OWNER、存在 REVOKED OWNER
和目标 ACTIVE 成员，然后仅提升目标成员；部分唯一索引阻止同一 Workspace 存在多个开放申请。

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

Greenfield 之后，4 个 foundation baseline（identity `V202608070300`、workspace `V202608070310`、
AI runtime `V202608070320`、authorization server `V202608070330`）在 Testcontainers 空库上全新
重放（每模块刚好 1 份应用 migration），验证 4 组合成完整性：Identity 的 6 张表、
Authorization Server 的 OAuth/Passkey/浏览器 client 表、Workspace 成员与审计、AI 调用审计。
旧库不能原地升级到 4 baseline：迁移从空库重建。该实测结果补充 DDL 真实性验证，但不替代发布
候选环境的完整 Testcontainers/HTTP 门禁。

发布前还必须在备份恢复出的接近真实规模数据库上验证升级耗时、锁等待和回滚方案。当前项目尚未建立生产备份恢复自动化，不能把空库测试等同于生产升级演练。

## 9. 回滚原则

应用回滚不等于 schema 回滚。优先让 migration 向前兼容上一版应用；数据库故障通过停止发布、恢复已验证备份或新增修复 migration 处理。任何手工生产 DDL/DML 都必须有审批、审计和执行记录，完成后补回版本化 migration，禁止环境永久漂移。
