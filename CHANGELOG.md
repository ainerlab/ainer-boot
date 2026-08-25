# Changelog

Ainer Boot 的用户可见变化记录在此文件。格式参考 Keep a Changelog，版本遵循语义化版本。

## [Unreleased]

### Changed

- **采用 MIT 并公开仓库（ADR-0051）**：根目录 `LICENSE` / `NOTICE`，根 POM 声明 MIT。
  不授予 Ainer 商标权（ADR-0004）。供应链签名/SBOM/不可变 Release 门禁不变。

### Added

- **组织端点 `@AinerAuthorize` 粗门禁**：`/api/organization` 目录/任职/岗位消费
  `organization.read` / `organization.manage`；参考装配需对应 Binding。模块切片未装配拦截器时
  注解不生效。请求体/查询 `workspaceId` 不作为授权目标。仍不是按目录/任职 ID 的对象级合同。
- **知识端点 `@AinerAuthorize` 粗门禁**：`/api/knowledge` 对象/修订/发布消费
  `knowledge.read` / `knowledge.manage`；参考装配需对应 Binding。模块切片未装配拦截器时
  注解不生效。请求体/查询 `workspaceId` 不作为授权目标。仍不是按对象/修订 ID 的对象级合同。
- **任务端点 `@AinerAuthorize` 粗门禁**：`/api/tasks` 定义/作业消费 `task.read` /
  `task.manage` / `task.submit`；参考装配需对应 Binding。模块切片未装配拦截器时注解不生效。
  仍不是按作业 ID 的对象级授权合同。
- **字典端点 `@AinerAuthorize` 粗门禁**：`/api/dictionaries` 类型/项读写消费
  `dictionary.read` / `dictionary.manage`；参考装配需对应 Binding。模块切片未装配拦截器时
  注解不生效。仍不是按类型/项 ID 的对象级授权合同。
- **通知端点 `@AinerAuthorize` 粗门禁**：`/api/notifications` 模板/记录/提交消费
  `notification.read` / `notification.manage` / `notification.submit`；参考装配需对应 Binding。
  模块切片未装配拦截器时注解不生效。仍不是按模板/记录 ID 的对象级授权合同。
- **配置端点 `@AinerAuthorize` 粗门禁**：`/api/configs` 读写与 secret 写入消费
  `config.read` / `config.manage`；参考装配需对应 Binding。模块切片未装配拦截器时注解不生效。
  仍不是按 namespace/key 的对象级授权合同。
- **文件端点 `@AinerAuthorize` 粗门禁**：`/api/files` 读写消费 `file.read` / `file.write`；
  参考装配需对应 Binding。模块切片未装配拦截器时注解不生效。仍不是对象级授权合同。
- **Workspace 路径目标解析（参考装配）**：`ainer-server` 注册 `AuthorizationTargetResolver`，
  把 `/api/workspaces/{id}` 写入 `ResourceRef.workspaceId`（仍是 `resourceType=request`）。
  对该工作区的 WORKSPACE Binding 才能过路径门禁；创建/列表无路径 id 时仍是粗闸门。
  **不是** 1.x 资源级授权合同（所有权/成员仍由应用服务检查）。
- **通知 EMAIL SMTP 投递（默认关闭）**：启用 `ainer.notification.email.enabled` 后，EMAIL
  渠道用 `JavaMailSender` 发送纯文本邮件；必须配置 `from` 与 `spring.mail.host`。地址与
  主题拒绝控制字符（防头注入）；日志与错误消息不含收件人。SMS/Push 仍由产品实现。
- **M5' 参考装配接线**：Workspace 读写/审计与 AI 网关/Agent 管理端点消费 `@AinerAuthorize`；
  网关仅在请求带 `actingAgentId` 时 fail-closed 调用 `ActingGrant.check`。这是参考装配粗门禁，
  **不是** 1.x 资源级授权合同。
- **最小观测 Starter（`ainer-starter-observability`，ADR-0029 T1#6）**：默认桥接 Boot
  `ObservationRegistry`，把 `requestId` 写入 `traceId` MDC；`ainer.observability.otlp.enabled`
  默认关闭，开启只装配导出标记，不强制全链路 OTel，也不改写域 Micrometer counters。参考装配
  `ainer-server` 与 `ainer-offstate-app` 按需依赖。发布清单 28 个 project / 132 个主制品。
- **M2 延迟自提权 Alert（ADR-0050）**：入岗命中「任职主体曾创建且仍 ACTIVE 的岗位集合绑定」
  时写 `DELAYED_SELF_ELEVATION` 审计并递增 `ainer.organization.delayed_self_elevation`；不自动
  撤销、不阻断入岗。UNAVAILABLE 创建拒绝保持不变。
- **通知 WEBHOOK 真实投递（默认关闭）**：启用 `ainer.notification.webhook.enabled` 后，WEBHOOK
  渠道用 `RestClient` POST JSON `{title,body}`；host 白名单 + HTTPS（loopback HTTP 需显式允许）
  + 拒绝私网/链路本地/ULA 解析 + 不跟随重定向。日志与错误消息不含 URL/正文。未启用时仍是
  开发用日志 sender；SMS/Push 仍由产品实现 `ChannelSender`。
- **授权端点门禁增强（ADR-0037 后续切片首批）**：新增 `AuthorizationTargetResolver` SPI——产品注册
  bean 后 `@AinerAuthorize` 门禁按解析出的类型化 `ResourceRef` 决策（第一个非空结果胜出，
  resourceType 不匹配 fail-closed），不再局限于合成 request 资源；完整决策经请求属性
  `ainer.authorization.decision` 暴露给 controller 供其消费公开投影描述符。CHALLENGE 拒绝现在
  携带 RFC 9470 `WWW-Authenticate: Bearer error="insufficient_user_authentication"` 挑战头。
- 授权模块补齐 ADR-0037 §3 声明的 ArchUnit 包边界守护（domain/policy/catalog/application 零
  Spring Security/Servlet 依赖、spring/ 适配层无反向引用）。

### Changed

- Maven 4 同 reactor BOM import 告警已用
  [ADR-0049](docs/decisions/0049-maven4-reactor-bom-import-warning.md)
  定性：**暂不消除，等待 Maven 4 GA**。不改 parentless BOM 消费合同。
- **ai-agent 分页拉齐 file 基准**：非法 `page`/`size` 返回 422 `AINER.AI_AGENT.INVALID_PAGE`，
  信封字段为 `items`/`page`/`size`/`total`，不再静默钳制。
- **ArchUnit 扩覆盖**：P3（file/dictionary/config/notification）、Incubating
  （organization/knowledge/task）、`ai.agent` 与 identity foundation（无 Web）按 workspace
  模板守住分层与无环。
- **`@AinerAuthorize` 拦截器装配**：适配器在 bean 创建时解析
  `AuthenticatedPrincipalResolver`（通常来自 security starter 自动装配），不再被用户
  `@Configuration` 阶段的 `@ConditionalOnBean` 误判为空操作。

## [1.1.0] - 2026-08-24

商业级代码评审首次制品发布（PR #24–#27），并纳入评审遗留收口：P4 任务调度引擎
（ADR-0047）、授权管理面硬化与 `trusted-managers` issuer 绑定匹配（PR #30/#31）。
原 2026-08-21 的 tag 因 GitHub Packages 存储配额（HTTP 402）从未部署成功，远端无任何
1.1.0 制品与消费者，发布前按当前 dev 头重新打 tag 并把已合入工作并入本版本。
**全部为加性变更与缺陷修复，无 schema 变化**；`1.0.x` 作为 LTS 补丁线继续受支持
（ADR-0045/0046）。27 modules。

### Added

- **任务调度模块 `ainer-module-task`（ADR-0047，Incubating）**：任务类型注册、延迟/周期
  执行、PostgreSQL SKIP LOCKED 领取（领取与 CLAIMED 审计单条 CTE 原子完成）、指数退避重试、
  按定义 `timeout_seconds` 的超时看门狗（at-least-once，handler 需幂等）、每轮询僵尸清扫与
  引擎侧生命周期审计；管理 API `/api/tasks/**`（`task.read`/`task.manage`/`task.submit`，
  错误码 `AINER.TASK.*`）。含真实 PostgreSQL 引擎集成测试。
- **授权引擎生产路径激活（M5）**：参考装配注册平台权限目录、scope 恒等天花板、
  BINDING_REQUIRED 领域策略与管理面白名单（缺省空 = fail-closed），引擎不再是全 deny 死链。
- **OpenAPI 运行时文档**：springdoc 3.1.0 装配于 `ainer-server`，`/v3/api-docs` 与 Swagger UI
  默认受资源服务器安全链保护。业务模块与消费者不强制依赖。
- Knowledge Revision 响应暴露 `payloadMarkdown`（K1 读语义闭环）。
- 契约修复九项：AI 网关 scope 403、`AiAgentErrorCode`、`ACTING_GRANT` 独立错误码、
  `SCOPE_` 前缀统一、分页 422 与回显钳制、`DUPLICATE_POSITION_CODE`、Knowledge sources/
  evidence 校验、readOnly 方法补齐、死代码清除。
- 注释语言统一中文（`src/main/java`）；成员响应域枚举 String 化等可读性拉齐。

### Security

- `ainer-starter-security` 在线校验默认 fail-closed（移除 `matchIfMissing`）。
- SubjectSet 成员解析按声明 workspace 过滤，关闭跨工作区提权。
- 授权决策审计接线到 MVC 授权管理器（`REQUIRES_NEW`，拒绝后审计仍存活）。
- PUBLIC 投影误拒修复；CHALLENGE 映射真实 401 并把 step-up 强认证事实接入授权上下文。
- 授权管理守卫拒绝时持久化为对 `authorization.manage` 的 DENY 决策审计
  （`REQUIRES_NEW`，审计失败阻断请求）。
- `ainer.authorization.trusted-managers` 白名单支持 issuer 绑定匹配：`<issuer>|<sub>`
  复合键精确采用；裸 `<sub>` 写法继续兼容，自动绑定本部署 resource server 的 issuer
  （匹配范围较仅裸 sub 明确收紧）。缺省空仍为拒绝一切。

### Changed

- 决策审计的 `AuditLevel.NONE` 过滤统一收口到 `AuthorizationDecisionAuditService`
  （此前由调用方自行判断，存在元数据静默失效隐患）；未注册权限码 fail-safe 照常记录。
- `AuthorizationManagementController` 的 scope 解析与保留 resourceType 白名单下沉应用层
  `ScopeRequests`；Binding/SetBinding/ActingGrant 创建服务新增服务端时钟加性重载（旧签名
  保留兼容），Controller 不再决定生效时间。

### Fixed

- 组织模块乐观锁 CAS 静默失效改为 409 且不写假审计；EXCLUDE 约束 23P01 竞态分流；
  Knowledge 版本号竞态重试；workspace 全部写方法的「已允许」审计移到业务成功之后。

## [1.0.0] - 2026-08-18

**1.0 产品合同定稿**：ADR-0040 Stable/Incubating 清单逐项核对通过（验收记录见 ADR-0040），
G0–G4 门禁全部关闭。自 `0.2.0` 起**零代码差异**——本版本是合同声明发布，使 ADR-0040 的
1.x 兼容承诺（HTTP API/错误码/SPI 签名/migration 只向前追加）正式生效。

### Process

- ADR-0045（版本策略/升级回滚窗口/patch 规则/兼容检查落地形态）+ ADR-0046（1.0 LTS 条款：
  `1.0.x` 首个 LTS 线，`1.1.0` 后再支持一个 minor 周期）
- G4 证据：双参考消费者（xq-platform-next `rc.2→rc.3→0.1.0→0.2.0` 含回滚；
  python-learning-service `0.1.0→0.2.0`）、兼容检查三形态（双消费者全绿/migration 重放/
  配置元数据契约）、`v0.2.0` 发布门禁全绿（122/122 验签、immutable）
- 发布后双消费者执行 `0.2.0 → 1.0.0` 升级验证（ADR-0045 窗口首个 major 升级证据）

### Incubating（1.0 可用、不承诺 API 稳定）

- 组织目录（ADR-0042：O1/O2 已交付）、Agent 代行（ADR-0043：A1 已交付）、
  Knowledge Foundation（ADR-0044：K1/K2 已交付）；任务调度（P4）未建设。

## [0.2.0] - 2026-08-17

ADR-0040 G3（产品核心闭环）四个切片全部落地后的次版本。**全部为加性变更**：已发布 API、
SPI 签名与 migration 不变（旧构造器保留、`BindingResolver.liveSetBindings` 为 default 方法、
migration 只追加），是对 1.x 兼容承诺的首次真实验证。

### Added

- **组织目录模块 `ainer-module-organization`（第 25 个模块）**：ADR-0042 取代 ADR-0032
  （Workspace 锚点 + 决策时实时解析撤销语义）。O1：Unit/Engagement/Assignment/Position
  基线（btree_gist 任职期不重叠、复合 FK、调岗单事务）+ 管理 API `/api/organization/**`；
  O2：`workforce.position#assignee` 成员解析器——**撤岗即失权**（岗位集合绑定 + 终止任职
  后下一次决策立即 DENY）。
- **SubjectSet 授权（ADR-0042 O2）**：决策引擎集合授予路径（`SubjectSetMembershipRegistry`
  端口，产品提供解析器）；`ainer_authorization_subject_set_binding` 加性 migration；管理 API
  `/api/authorization/set-bindings/**`；创建防提权矩阵（GLOBAL/system-only/HIGH 拒绝、
  set↔scope Workspace 一致、自成员拒绝）。
- **Agent 代行 A1（ADR-0043 取代 ADR-0031）**：`ActingGrant` 一层委托（签发防扩权：
  agentDelegable ∧ principal live 子集 ∧ scope 覆盖）+ 委托检查点实时解析（Agent 退役/
  权限收缩/撤委托下一次检查即拒）+ 管理 API `/api/authorization/acting-grants/**`；
  ai-runtime 独立 `AiAgentModuleConfiguration` + Agent 定义注册表 `/api/ai/agents`。
- **Knowledge Foundation K1/K2（ADR-0044，第 26 个模块）**：不可变 Revision + SUPERSEDES
  lineage + asOf 精确解析（未发布不可见）+ 人工发布门禁（SERVICE/AI 可提案、发布一律 403）
  + append-only 生命周期事件；管理 API `/api/knowledge/**`。
- 决策审计新增 agent/grant 可空关联列（加性 ALTER）。

### Fixed

- **timestamptz 微秒精度缺陷（组织模块）**：Linux JDK 纳秒时钟 + PostgreSQL 微秒截断使
  客户端纳秒时间戳在子分配包含性比较中漂移 <1µs 触发 422；全部时间入口统一
  `truncatedTo(MICROS)`（macOS 本地时钟无法复现，仅 CI 暴露）。

### 边界

- 授权 A2–A4（Capability catalog/Context 授权/Tool 检查点/Token Exchange）与 Knowledge
  Phase 2–4（索引/Context Assembly/OKF）按需推进；本版本不含。制品规模 26 projects /
  122 primary。

## [0.1.0] - 2026-08-14

第一个稳定 `0.1` 基线：包含 ADR-0040 G1 全部硬化与 G2 消费者证据闭环。相对
`0.1.0-rc.3` 的变化如下；`rc.2`/`rc.3` 保持不可变，作为升级/回滚链的已发布起点。

### Added

- **文件存储模块 `ainer-module-file`（第 24 个 reactor 模块）**：`ainer_file_object`/
  `ainer_file_audit` migration（UUIDv7 CHECK）、`/api/files` 上传/下载/删除与分页管理
  API（`file.read`/`file.write` scope，413/415 真实状态码）、大小/类型限制、SHA-256 校验、
  上传失败补偿与同事务变更审计；发布链同步（BOM、release-artifacts 24 projects/112
  primary、consumer 24 POM）。
- **P3 服务端管理 API（dictionary/config/notification）**：三模块稳定错误码
  （`AINER.<MODULE>.*`）、`*.read`/`*.manage`/`*.submit` scope 在应用服务内强制、管理
  REST API（乐观锁部分更新、动作名词状态变更端点、分页 ≤100）与同事务变更审计
  （dictionary/notification 新增 append-only 审计表）；写入面补齐（字典类型/项更新与启停、
  通知模板更新/启停/分页、投递记录状态分页且不回显渲染内容）。
- **`ainer-test-support` 新增 `JwtTestSupport`**：共享真 JWT fixture（RSA 3072 签发
  USER_NEUTRAL_V1/SERVICE_V1 + 真 NimbusJwtDecoder + @Primary resolver 工厂）。
- **首个外部消费者 G2 证据**：`xq-platform-next` 从远端 GitHub Packages 冷仓消费
  `v0.1.0-rc.3`，完成 `rc.2 → rc.3 → rc.2 → rc.3` 升级/回滚演练（每步 4 tests 零跳过）、
  JWT 安全链 / Ainer 资源授权（撤销 Binding 后同一 Token 立即 403）/ migration replay /
  真实 HTTP 错误 / OpenAPI→TypeScript SDK 门禁的产品纵向切片（14 tests 零跳过）。

### Changed

- **持久化身份全域 UUIDv7（ADR-0040 G1）**：Workspace、AI Runtime 与 Authorization
  Server 的 20 处持久化主键/审计/恢复 ID 从 UUIDv4 统一迁移到应用层 `Uuidv7.generate()`，
  持久化路径零 `UUID.randomUUID()`；集成测试新增 `id().version() == 7` 断言。数据库
  schema 零改动。

### 边界

- `0.1.0` 是稳定 `0.1` 基线与 ADR-0040 G2 的收口版本，不是公开发行版、生产就绪或 1.0
  声明；许可状态仍为私有/专有。OpenAPI 运行时文档未引入（Boot 4.1 springdoc 兼容性
  待验证）。

## [0.1.0-rc.3] - 2026-08-13

### Fixed

- **Initializer 独立构建合同补正（2026-08-13）**：修复 Project Initializer 的生成项目 README
  要求执行 `./mvnw`，实际生成树却缺少 Maven Wrapper 的合同缺口。生成项目现在自带 Apache Maven
  Wrapper 3.3.4，固定 Maven 3.9.16 的 Maven Central 地址与 SHA-256；POSIX 写入保留 `mvnw`
  执行位，`diff` 同时检测字节与执行位漂移。Initializer consumer、TTFR 与 TTCRUD 门禁改为使用
  生成项目自己的 Wrapper，不再借用 Ainer 生产者的 Maven 4 Wrapper。该缺口由首个产品消费者
  `xq-platform-next` 复核发现；`v0.1.0-rc.2` 保持不可变，本修复归入新的不可变候选
  `v0.1.0-rc.3`。release run `31675920731` 已完成 338 tests 零失败/错误/跳过、107/107 远端
  制品读回验签、Maven 3/Maven 4 空仓消费、远端 Initializer 三通道和 immutable GitHub Release。

## [0.1.0-rc.2] - 2026-08-13

> **破坏性变更（Greenfield S8，不可逆）**：按 ADR-0033 完成去 tenant 化原子切换。删除
> tenant/多租户上下文、Identity access-event outbox/relay/消费、跨运行时 Directory、平台
> 预配与通知回执、tenant 服务 client 控制面及 OWNER 专用转移/丢失恢复；重建为
> HumanAccount/ServicePrincipal/LoginIdentity/Credential foundation 与
> `SERVICE_V1`/`USER_NEUTRAL_V1` typed token profile，撤销通过 `sec_epoch` 在线比对。
> 下文历史条目中的 tenant/access-event/relay/预配描述已在当前基线中移除，不再适用。
> Ainer Admin JSON 契约同步收敛为 v1.1.0：TenantMembers 管理 API、`/api/me/tenants` 与
> tenant selector 代理/选择页一并删除，`ainer-admin-v1.yaml` 只保留
> `POST /api/me/access-token-revocations` 当前会话撤销。

### Fixed

- **`0.1` 发布列车失败关闭加固（2026-08-13）**：修复虚拟线程矩阵在 Ubuntu 上先从 `PATH`
  发现 `ab`、执行时却硬编码 `/usr/sbin/ab` 的失败，并修正 `AINER_VERSION` 拼写。发布 workflow
  同时要求 ApacheBench 完成全部请求，拒绝 Non-2xx、Connect、Receive 与 Exceptions，只允许动态
  响应产生的 Length 差异，避免 `|| true` 把真实压测失败伪装为绿色。发布 workflow
  现在要求 annotated tag/source 一致、目标 package 版本不存在、GitHub Immutable Releases 已启用；
  GPG 恢复 best-practices + passphrase 环境变量模式，拒绝无口令私钥、非预期 fingerprint 和 Maven
  CLI 口令。发布后按与 reactor POM 对照的唯一清单逐一读回 107 个主制品与 107 个 `.asc` 验证精确
  fingerprint，再从两个空仓执行远端 Maven 3/4 Golden Consumer 并远端获取 Initializer CLI。
  `0.1.0-rc.1` 因制品源码/tag 不一致且证据不完整标记为
  withdrawn/non-qualifying，禁止覆盖或消费，下一候选使用 `rc.2+`。
- **`0.1` 端点授权真实装配（2026-08-11）**：修正 `HandlerInterceptor` 与 servlet security filter
  顺序假设。`AuthorizationModuleConfiguration` 现在在 Servlet Web + verified principal resolver
  存在时注册 manager、interceptor 与 MVC wiring；MVC 解析 `HandlerMethod` 后，由 interceptor 在
  controller 执行前调用 `AinerRequestAuthorizationManager`。真实签名 JWT + HTTP + PostgreSQL 测试
  覆盖匹配 scope 放行、缺 scope 统一 403 且 controller effect 不发生、无 Token 401；未认证
  `PUBLIC_PROJECTION` 会以 Anonymous requester 进入公共策略，但未执行 obligation 仍失败关闭；
  principal resolver 的非认证业务异常之外的运行时故障不再降级成匿名访问。
- **`0.1` 签名发布门禁（2026-08-11）**：tag workflow 现在强制语义化非 SNAPSHOT 版本、Docker、
  锁定 Maven 3.9.16、Maven 3/4 Golden Consumer、Initializer consumer、完整 clean deploy 与零跳过
  检查。所有 tag 发布必须配置签名开关和 GPG key/passphrase；passphrase 改由环境变量进入 GPG
  Plugin best-practices 模式，parentless BOM 增加独立 POM 签名 profile，禁止静默未签名发布。
  non-SNAPSHOT 可重复性彩排的两次构建统一显式跳过正式签名，避免 `gpg.skip` 进入 consumer POM
  后产生伪差异；真实 tag 的签名门禁不受该彩排参数影响。

### Added

- **私有 RC 签名证据与不可变 Release（2026-08-13）**：接受 ADR-0041；release workflow 生成
  CycloneDX SBOM、远端 Maven SHA-256/SHA-512 清单、记录精确 source/tag/run/107 个 artifact digest
  的项目签名 provenance、签名制品清单、公钥/fingerprint 与证据签名，全部门禁通过后才创建
  GitHub Release。
  GitHub Attestations 改为显式可选的附加能力；启用后失败关闭，不再用 `continue-on-error` 把计费
  限制伪装成来源门禁通过。项目签名 provenance 不宣称 GitHub Attestation 或 SLSA 等级认证。
- **撤权后原 Token 受保护写失效验证（2026-08-11）**：真实签名 `USER_NEUTRAL_V1` JWT
  先通过产品所有的 test-scope HTTP 写路径；管理 SERVICE 随后经真实管理 API 撤销 PostgreSQL
  Binding，复用完全相同且仍在有效期内的 JWT 再写返回 403，业务写事件保持不变。ALLOW 与
  `NO_BINDING` DENY 均在产品 effect 前写入决策审计。该结果证明模块内无 ALLOW 缓存的请求时
  重评估链路，不代表外部消费者或生产部署的授权失效 SLA 已验收。

- **Golden Consumer 参数化 PostgreSQL 查询验证（2026-08-11）**：新增 test-scope 产品 listing
  JDBC adapter，在真实 PostgreSQL 18.3 中把 `DefaultQueryAuthorizationPlanner` 生成的类型化 `Q`
  下推为 `varchar[]`/`uuid[]` PreparedStatement。验证未授权 Workspace row 不进入 JVM、注入形态
  status 不扩大结果、ALLOW 一次查询、DENY 零查询，以及 20,003 行合成夹具命中授权查询索引。
  planner 同步拒绝错主体、过期、USER GLOBAL 和错 resourceType Binding。该结果是 Golden Consumer
  工程验证，不代表已有生产产品 Repository 或生产容量结论。

- **外部授权 Golden Consumer 制品门禁（2026-08-11）**：`verify-maven-consumers.sh` 不再只编译
  `PermissionCode` smoke；独立临时项目只通过 BOM 与隔离仓库已安装制品，自行定义产品
  Permission/Role/Binding/policy/query constraint，并实际调用 `AuthorizationService` 与
  `DefaultQueryAuthorizationPlanner`。Maven 3.9+、Maven 4 各执行 1 项 JUnit，均为零
  failure/error/skipped。该结果证明本地 `0.1.0-SNAPSHOT` 的公开契约可被外部 Maven 项目消费，
  不代表正式制品已发布，也不替代完整产品关系、参数化 SQL 与 row/字段投影验收。

- **通用授权管理防提权矩阵（2026-08-11）**：新增代码注册、版本化
  `GrantAdministrationPolicy` 与不可绕过的 `GrantAdministrationGuard`。仅有 SERVICE JWT 和
  `authorization.manage` scope 不再足够；宿主必须精确登记可信主体及 assignable
  Permission/Scope/target，未登记时默认 deny-all。Controller 与事务应用服务双层拒绝
  system-only/策略外 Permission、GLOBAL/策略外 Scope、越界目标、自 Binding 与修改自己的 ACTIVE
  Binding 所引用 Role。真实签名 JWT + PostgreSQL 18.3 补齐任意持 scope SERVICE、目录外授权、
  GLOBAL、越界 target 和自我提权负向矩阵。Greenfield 后生产 bootstrap 与 Ainer Admin 集成仍待
  取代 ADR 定义。

- **通用授权 S3 查询计划与 Golden Consumer 验证（2026-08-11）**：ADR-0030 S3 落地。
  新增集合查询授权契约：`QueryAuthorizationRequest<I>`（产品定义 query intent）、
  `AuthorizedQueryPlan<Q>`（Allowed 携带类型化约束 / Denied）、`QueryAuthorizationPlanner<I,Q>` 端口、
  `QueryConstraintBuilder<Q>`（产品约束累积器）。`DefaultQueryAuthorizationPlanner` 复用 scope ceiling
  与 binding resolver 生成产品类型化 `Q`——Ainer 不输出 SQL，未授权 row 在数据库层排除。
  6 项 Golden Consumer 查询验证测试覆盖 Workspace/Resource/Global binding、撤销、scope 与
  customer deny。ADR-0030 S0+S1+S2+S3 全部 Accepted，§13.4 创建门禁 8 的单资源与集合查询维度通过。
  > **接手复核（2026-08-11）撤销该结论**：ADR-0030 回退为 Proposed，S1–S3 为原型未达验收，
  > §13.4 门禁 8 未关闭。详见 `project-status.md` §3 差距清单。
- **通用授权 S2 管理 REST API（2026-08-11）**：ADR-0030 S2 落地。新增 `/api/authorization/**`
  管理 REST API：Permission 目录只读、Role CRUD + 权限替换、Binding 创建/撤销（action-path noun）、
  Effective Access 查询。所有端点要求 SERVICE principal + `authorization.manage` scope。5 项 HTTP
  集成测试全绿（TestRestTemplate + 真实 PostgreSQL 18.3），ADR-0030 S0+S1+S2 Accepted。
  > **接手复核（2026-08-11）撤销该结论**：S2 HTTP 测试用 stub Principal 绕过真实 JWT，
  > 管理 API 缺防提权矩阵；ADR-0030 回退为 Proposed。
- **通用授权 S1 PostgreSQL 持久化（2026-08-11）**：ADR-0030 S1 落地。
  `ainer-module-authorization` 从纯域模块升级为可持久化模块（新增 `ainer-starter-persistence` 依赖）。
  新增 Flyway baseline（6 张表：permission/role/role_permission/subject_binding/change_audit/
  decision_audit），scope_kind CHECK 适配 Greenfield Workspace 语义。
  新增 application 层（Role/Binding 服务 + 端口 + 错误码）与 infrastructure 层（MyBatis Row/Mapper/
  Repository 适配器 + PostgreSQL BindingResolver 实现）。
  撤销绑定后 liveBindings 立即不返回——无 ALLOW 缓存，仍有效的 JWT 不能恢复已撤销授权。
  9 项 Testcontainers 集成测试（真实 PostgreSQL 18.3）全绿，ADR-0030 从 Proposed 转 Accepted。
  > **接手复核（2026-08-11）撤销该结论**：S1 存在 RESOURCE scope CHECK 冲突、change/decision
  > audit 零写入、`Role.name` 死参数等缺陷；ADR-0030 回退为 Proposed。
- **Docker Compose 开发环境（2026-08-10）**：新增 `docker-compose.yml`、`Dockerfile`
  （多阶段构建，`AINER_MODULE` build arg 选择模块，容器内使用 Maven Wrapper）、
  `docker/init-db.sh`（PostgreSQL 双库双用户：`ainer`/`ainer_auth`）、
  `scripts/generate-dev-keys.sh`（幂等生成 RSA 3072 PKCS#8 PEM 签名密钥）、
  `.env.example`（完整环境变量模板）。默认 profile 只启动 postgres，`--profile full`
  额外启动 Authorization Server + 业务 Server。`development.md` 新增 §3 Docker Compose
  快速启动小节。
- **`ainer-test-support` 测试基座（2026-08-10）**：ADR-0029 T1 第 7 项落地。新模块提供
  `RestTestClient`/`RestResponse`（Boot 4.1 `TestRestTemplate` JSON 集成测试便捷）与
  `AinerPostgresContainer`（固定 `postgres:18.3-alpine`，配合 `@ServiceConnection` 自动装配
  DataSource，替代 `@DynamicPropertySource` 样板）。Initializer v1 生成的 SmokeTest 与
  CRUD 集成测试模板已切换为 test-support，pom 增加 `ainer-test-support` test 依赖；
  模块自身含 RANDOM_PORT + 真实 PostgreSQL Testcontainers 集成测试。
- **初始生成项目默认开启虚拟线程（2026-08-10）**：按 ADR-0029 决策 5，双模式压测矩阵
  闭环（等待型 80ms×400 并发虚拟线程 p50 减半、吞吐 +77%，JDBC 场景同级无回归）后，
  Initializer v1 模板新增 `spring.threads.virtual.enabled=true` 默认开启；新增生成
  默认开关断言测试。`scripts/measure-virtual-threads.sh` 增加 `/api/wait` 等待型场景，
  `xq-platform-next` 在默认虚拟线程下 4 tests 0 skipped。
- **首个外部消费者 `xq-platform-next` 生成（2026-08-09）**：Initializer 在独立仓库生成
  `platformApp` CRUD 全栈并通过独立 `mvn verify`（JDK 25 + 真实 PostgreSQL 18.3
  Testcontainers，4 tests 0 skipped）。修复生成器缺陷：CRUD 测试示例值
  `字段名-created/updated` 超过 `string(N)` 上限时按 `size` 截断（新增
  `paddedSample()` 与 string(8) 边界单测）。
- **第二个外部消费者 `python-learning-service` 登记（2026-08-09）**：在 scaffold 设计
  §13.5 登记 Python 课程、学习进度、练习、Tutor 后台为第二个消费者，接入方式固定为
  “版本化制品升级”（非 SNAPSHOT BOM/Starter 固定版本），拒绝源码副本与开发分支依赖；
  领域模型可先行开发，后台适配层保持隔离，等 P1 发布门槛后正式接入。
- **P2 Create & Generate 收口（2026-08-09）**：P2 四项退出门禁逐项闭环——生成确定性
  （同 manifest 两轮 diff=0）、生成安全（preview 不写盘、非空目标拒绝覆盖、生成器不连接
  或写入数据库、不改菜单）、量化时间目标（TTFR 实测 100s/门禁 600s、TTCRUD 实测
  124s/门禁 1800s，均已接入 CI）与 PostgreSQL/golden consumer 门禁。组织/行业模板与
  受控策略包按 ADR-0035 决策 7 归入 Studio/Enterprise 扩展（P3+），不阻塞 P2 收口。
- 增加 Initializer CRUD v1 生成（ADR-0036 Accepted）：Manifest v1 可选顶层 `entities`
  （字段名/类型词汇表 `string(n)`/`int`/`long`/`decimal`/`boolean`/`instant`/`uuid`/`text`、
  `nullable`/`unique`/`comment`，未知键、重复字段、模板字面量与 `id` 保留字 fail-fast），
  仅允许与 `database: postgresql` 组合；每实体生成 6 个文件：Flyway migration
  （`id uuid primary key default uuidv7()` 符合 ADR-0020、命名唯一约束与 `COMMENT ON`）、
  MyBatis-Plus `Entity`/`Mapper`（自定义 `INSERT ... RETURNING id` 绑定参数、无 `${}`）、
  `ApplicationService`（create/get/page/update/delete，缺失抛 `StandardErrorCode.NOT_FOUND`）、
  `Controller`（`/api/<复数>` 五个端点，全部 `ApiResponse` envelope + `X-Request-Id`，
  不含 `@PreAuthorize`，安全装配由消费者决定）与 Testcontainers CRUD 全链路集成测试
  （create→get→update→list→delete→404，0 skipped）；`verify-initializer-consumer.sh`
  增加第三通道（确定性 diff、无源码复制、无 `mybatis-plus-generator` 依赖、真实 PostgreSQL
  集成测试 0 skipped）。
- 增加 TTCRUD 量化门禁：新增 `scripts/measure-ttcrud.sh` 并接入 CI，从含 `entities` 的
  manifest 到含 migration、API 与 Testcontainers 集成测试全绿的纵向 CRUD 实测 124 秒
  （设计文档 §12.1 目标 ≤30 分钟），含 reactor install、确定性生成、独立编译与真实
  PostgreSQL 运行全流程。
- 增加 P2 Project Initializer v1 切片：`ainner-initializer`（Manifest v1 解析/校验 +
  零 Spring 确定性生成内核，ADR-0035 Accepted）与 `ainner-initializer-cli`（`preview`/`init`/`diff`
  离线命令）；同版本同 manifest 两轮生成字节级一致、preview 不落盘、非空目标拒绝覆盖
  （`--force` 才允许且不删除外部文件）、生成物只引用已发布制品不含 Ainer 源码副本；
  新增 `scripts/verify-initializer-consumer.sh` 并接入 CI，验证确定性、无源码复制与生成项目
  独立编译。
- 增加 Initializer postgres 变体：`database: postgresql` 生成 `ainner-starter-persistence`、
  runtime PostgreSQL 驱动与 Testcontainers 测试依赖（版本由 BOM 管理），测试资源生成
  `@Testcontainers` + `postgres:18.3-alpine` + `@DynamicPropertySource` 集成测试（真实
  `SELECT 1` 连通断言 + ping 契约），`consumer` 门禁同时验证普通与 postgres 变体、双变体
  真实测试 0 skipped；Boot 4.1 拆分后 `TestRestTemplate`/`AutoConfigureTestRestTemplate`
  使用 `org.springframework.boot.resttestclient` 新包。
- 增加 TTFR 量化门禁：生成项目隐含 `spring-boot-starter-actuator`（management 只暴露
  health、`show-details: never`），smoke 测试断言 `/actuator/health` UP；新增
  `scripts/measure-ttfr.sh` 并接入 CI，从空目录到 `/actuator/health=UP` 实测 100 秒
  （设计文档 §12.1 目标 ≤10 分钟），含 reactor install、确定性生成与真实启动全流程。
- 增加默认关闭的 M4.8B 租户上下文选择：`GET /api/me/tenants` 返回当前 USER 的 ACTIVE
  membership 安全投影（tenant ID、code、name、role、是否默认），LOCKED/DISABLED 不返回；
  Authorization Code + PKCE 人员流程在认证后增加 tenant selection 步骤，多 ACTIVE membership
  用户被重定向到品牌选择页，选择结果绑定当前 AS 会话与 authorization request；
  JWT token customizer 在签发人员 access token 前实时重查 Identity membership 校验选定 tenant
  仍然 ACTIVE 并取得当前角色，principal 或客户端提交的 tenant 只作为候选。
- 增加默认关闭的 M4.8C OWNER 专用转移：双自然人确认状态机（REQUESTED → EXECUTED / CANCELLED /
  EXPIRED），当前 ACTIVE OWNER 发起、目标 ACTIVE ADMIN 接受后原子角色交换（OWNER↔ADMIN），
  同事务写入操作审计与双方 `IDENTITY_MEMBERSHIP_ROLE_CHANGED` access event/outbox 使旧角色
  Token 进入撤销链路；数据库部分唯一索引保证每 tenant 最多一个 ACTIVE OWNER 和一个未完成转移；
  暴露 initiate/get/accept/cancel 四个端点，要求 `tenant.ownership.transfer` scope、可信
  tenant claim 与实时角色门禁。
- 增加默认关闭的 M4.8C OWNER 丢失恢复：双 tenantless SERVICE request/approve（不同 service
  subject），只能提升现有 ACTIVE ADMIN 为 OWNER 并降原 OWNER 为 ADMIN，不恢复被禁用主体；
  独立表、端点（`/internal/identity/ownership-recovery/**`）与 scope
  （`identity.ownership-recovery.request|approve`），与正常转移不共用授权规则。
- 增加 ownership-transfer step-up 门禁：默认关闭，启用后要求人员 Token 的 `amr` 含强因子且
  `auth_time` 在 `maxAuthAge` 内才能执行所有权转移。
- 增加由 Ainer Boot 服务端承载的品牌 `/login`：固定消费 Ainer Studio 视觉合同 1.0.0，
  保持 CSRF、SavedRequest、PKCE/OIDC 与条件 MFA 语义，统一凭据错误，提供 HTML 429 和明确
  基础设施异常 503 状态，并只通过两个精确 CSS 路径交付资源。
- `ainer-dev.xiaoqu99.com` 可复现 dev 环境已真实上线：独立 PostgreSQL 18.3、loopback
  systemd Authorization Server、版本化 JAR/Studio/Admin release、原子切换/校验回滚、
  Let's Encrypt 与精确同源 Nginx 路由全部启用；远程 Chromium 已跑通 PKCE、成员治理、
  revoke、OIDC logout 和退出后重新登录；登录页样式资源使用精确代理且根 favicon 不再产生
  404，并建立跨 Boot/Studio session 的开发环境交接手册。
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
- 增加默认关闭的 M4.8A 平台 tenant 预配与激活核心：独立 tenantless SERVICE operator、
  tenant/user 成对 capability、operator ID 白名单、`Idempotency-Key` 请求摘要、tenant code/
  新 username 并发预留、惰性过期和同事务平台操作审计；新用户使用短时限次、只存摘要的一次性
  grant 与 AES-256-GCM 保护的 notification outbox，已有 ACTIVE 用户必须以本人 USER Token
  接受。成功消费在单一事务中创建 ACTIVE tenant/user（若需要）和唯一 OWNER membership。
- 增加默认关闭的预配通知 relay：以独立 tenantless Client Credentials client、最小
  `identity.provisioning-notifications.publish` scope 和 HTTPS 向可配置通知网关投递；稳定
  UUIDv7 notification ID 同时作为 `Idempotency-Key`，并暴露 pending/failed/exhausted/cancelled
  指标。网关持久接收或请求取消后，Identity 主动销毁 outbox 中可解密 payload。
- 增加默认关闭的预配通知终态回执 API：外部网关使用独立 tenantless Client Credentials client、
  精确 client ID 白名单和 `identity.provisioning-notifications.receipts.write` scope 回传
  `DELIVERED|FAILED`；Identity 使用 PostgreSQL UUIDv7、gateway event 与 notification 双重唯一键
  保存最小终态事实，重复回放不重复写入或计数。
- 增加平台 tenant/user 安全分页与未完成预配显式取消：列表分别使用最小 read scope、最大单页
  100 且不返回凭据数据；取消使用成对 write scope，并把 request、预期 grant、未发布 payload
  销毁和唯一 `CANCELLED` 审计放入同一事务，重复调用保持幂等。
- 建立面向 PostgreSQL 18 的数据库设计规范 1.2，统一 Ainer 自有表的命名、类型、结构模板、
  完整性、tenant 隔离、索引、AI 数据、在线演进与设计评审门禁；以最小“单一写入所有者”规则
  约束跨模块写入，并为不可变 Domain Snapshot 划定 JSONB 边界。
- 增加 Proposed 的 AI Runtime 与 Knowledge 数据模型提案：基于 Ainer 当前 Invocation 和 xq
  现有模型复审 Run、Artifact、revision、chunk、索引代际与业务事实边界；不将候选表写成已交付。
- 增加 `docs/00-overview.md` 作为人和 AI agent 的统一文档入口，按任务组织阅读路径和完整文档地图；
  `docs/README.md` 收敛为代码托管平台自动展示的极简门面。
- 接受 ADR-0020，将数据基线调整为 PostgreSQL 18 Native-First：新 Ainer 持久化 ID 默认
  UUIDv7、`tenant_id` 全链路统一 UUID，现有 UUIDv4/字符串 tenant 和早期 migration 只作为
  1.0 前待清理实现债；PostgreSQL 19 Beta 被规划为前向测试目标而非生产承诺。
- 建立架构决策、HTTP API、开发、测试、数据库、配置、运行和发布文档体系。
- 增加只读权限的候选 GitHub Actions 质量门禁：锁定 JDK 25，验证 Maven 4 Wrapper 与 Docker，
  执行完整 Reactor、强制 Surefire `skipped=0`、验证 Maven 3/4 外部消费者，并生成短期
  CycloneDX SBOM 工作流制品；正式发布、签名和 provenance 仍保持阻断。
- 增加 P1 发布能力：根 POM `release` profile 为全部 JAR 制品附加 `-sources.jar` 与
  `-javadoc.jar`（JDK 25、UTF-8、确定性时间戳）并用 `maven-gpg-plugin` 逐制品生成 `.asc`
  签名；`release.yml` 支持仓库变量+secrets 驱动的 GPG 密钥导入（缺失 fail-closed）与
  `actions/attest-build-provenance` 接入可选 GitHub Attestation；当前强制 provenance 与失败策略
  由 ADR-0041 取代；consumer 门禁改为
  `-Prelease` 安装并断言八个 library 制品的 sources/javadoc 伴随文件存在。

### Changed

- `ainer-starter-persistence` 切换为 Spring Boot 4 专用
  `mybatis-plus-spring-boot4-starter:3.5.17`，并显式引入
  `mybatis-plus-jsqlparser:3.5.17`；MyBatis-Plus 仅用于 infrastructure 的简单 CRUD 和最大
  100 条分页，现有复杂 XML 与 PostgreSQL 原生 SQL 保持显式。全局 `IdType.AUTO` 保留数据库
  `DEFAULT uuidv7()` 生成与回填，不启用 tenant interceptor、逻辑删除、自动填充或代码生成器。
- 明确保留 JDK 25 LTS、`--release 25` 与 Enforcer `[25,26)` 作为生产基线；JDK 27 EA 仅作为
  未来候选，待 GA、Spring Boot 官方兼容范围和 ArchUnit major 71 支持就绪后另行评审。
- 生产者构建基线切换为 Maven 4.0.0-rc-6 preview，并选择 Maven Wrapper 3.3.4 固定构建工具；
  clean、resources、compiler、surefire、jar、install、deploy 与 artifact 等构建插件显式锁定，
  根 POM 与 parentless BOM 都通过 Enforcer 拒绝旧 Maven，避免失败前写入 Ainer 制品。
- 移除 Flatten Maven Plugin，改由 Maven 4 内建 Consumer POM 处理 `${revision}`，并固定
  `maven.consumer.pom.flatten=false`；Maven 3.9+ 只保留为已安装或已发布制品的下游消费兼容门禁。
- JDK 23+ 注解处理器改为 Compiler Plugin 显式输入；Spring Boot 配置属性模块增加配置元数据
  产物断言，不再依赖 classpath 自动扫描。

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
- 平台 Identity 预配只接受无 tenant 的 SERVICE、精确 operator client ID，并同时要求 tenant 与
  user 对应的 read/write scope；独立一分钟 operator bootstrap 不覆盖策略不匹配的既有 client。
  平台响应不含密码、激活 secret 或请求摘要；grant 只保存高熵 secret 摘要，联系目标与唯一明文
  只存在于带密钥版本的 AES-GCM outbox 密文。已有用户接受只允许目标 USER subject 并要求
  `identity.provisioning.accept`。
- 预配通知 relay client 与平台 operator 分离且无 tenant，只能持有通知发布 scope；生产默认强制
  HTTPS。网关 2xx 只表示已持久、幂等接收，不伪装成最终邮件/短信送达，错误正文不会进入日志或
  outbox。
- 预配通知回执 client 还必须与 relay、operator、metrics、introspection 分离，只能持有回执写
  scope 并命中精确白名单；回调不接收正文、联系地址、供应商原始 body、Token 或 secret。
- AI 审计默认不保存 prompt、模型输出、API key 或供应商错误正文。

### Fixed

- 修复已认证请求缺少必填 HTTP header 时落入 500 的通用 Web 错误映射；现在
  `MissingRequestHeaderException` 返回统一 400 `AINER.COMMON.INVALID_REQUEST`，平台预配缺少
  `Idempotency-Key` 已有真实 HTTP 回归门禁。
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
- 修复预配通知 `FAILED` 回执数据库 check 在 SQL 三值逻辑下允许空 `failure_code` 的问题；现以
  显式 `IS NOT NULL` 约束，并由 PostgreSQL 18.4 smoke 验证拒绝。

### Known limitations

- `0.1.0-rc.2` 是私有、受控的 release candidate，不是稳定版、公开发行版或生产就绪声明；
  `0.1.0-rc.1` 已撤回且禁止消费。
- Maven 4.0.0-rc-6 仍是 preview；Wrapper 已锁定 Maven Central 正式同步的发行包与 SHA-512，
  Maven 3.9+ 只作为下游消费者门禁。稳定 producer toolchain 仍需在 `1.0` 前重新决策。
- 文件元数据持久化以及字典、配置、通知、文件的服务端管理 API/OpenAPI 尚未完成，P3/G1 未关闭。
- 授权当前只有端点粗粒度门禁；资源 target resolver、obligation executor、方法级 AOP 与 RFC 9470
  challenge 仍未交付。
- Organization/Workforce、Agent/Tool/Context/Evaluation、Knowledge/RAG 与任务调度仍为 Incubating，
  不属于本 RC 的 Stable 契约。
- 生产高可用、备份恢复、灾难演练、多实例容量、正式 SLO/告警路由、外部不可变审计和商业
  entitlement 尚未完成。
- 私有免费仓库不能启用 required review/branch protection，当前依赖 Draft PR、完整 CI 与 tag
  只能指向默认分支头的补偿控制。
- 稳定版仍要求 `xq-platform-next` 从远端 RC 完成真实纵向切片、migration replay、升级与回滚；
  本仓库测试和 RC 发布不能替代产品证据。

[Unreleased]: https://github.com/ainerlab/ainer-boot/compare/v0.1.0-rc.3...HEAD
[0.1.0-rc.3]: https://github.com/ainerlab/ainer-boot/releases/tag/v0.1.0-rc.3
[0.1.0-rc.2]: https://github.com/ainerlab/ainer-boot/releases/tag/v0.1.0-rc.2
