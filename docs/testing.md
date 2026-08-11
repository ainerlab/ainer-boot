# Ainer 测试与质量门禁

> 文档类型：长期规范 · 状态：生效 · 最近核对：2026-08-07 · 适用版本：`0.1.x`

## 1. 目标

测试用于证明业务行为、依赖方向、协议语义和真实基础设施兼容性。覆盖率数字不能替代高风险拒绝路径、事务回滚和数据隔离验证。

## 2. 测试分层

| 层级 | 关注点 | 当前示例 |
|---|---|---|
| Domain | 值对象、状态机、计算和不变量 | `WorkspaceTest`、`AiGatewayPolicyTest` |
| Application | 用例、授权、幂等、端口交互 | `WorkspaceApplicationServiceAuthorizationTest`、`IdentitySecurityLifecycleTest` |
| Architecture | 包依赖、层间方向、循环依赖 | 各业务模块 `*ArchitectureTest` |
| Adapter contract | HTTP、JWT、Client Credentials、provider、序列化和错误脱敏 | Web/Security starter、Identity HTTP transport 与 OpenAI-compatible provider 测试 |
| PostgreSQL integration | Flyway、MyBatis-Plus/MyBatis、约束、锁和事务回滚 | 各模块 `*IntegrationTest` |
| Executable smoke | 发行物启动、健康端点和自动装配 | `AinerServerApplicationTest`、Authorization Server 集成测试 |

新增能力至少覆盖正常路径、边界输入、权限拒绝和基础设施失败。并发所有权、预算扣减、撤销 epoch 等事务敏感行为必须在真实 PostgreSQL 上验证。

## 3. 标准命令

完整验证：

```bash
./mvnw clean verify
```

模块验证：

```bash
./mvnw -pl ainer-module-workspace -am test
./mvnw -pl ainer-module-identity -am test
./mvnw -pl ainer-module-ai-runtime -am test
./mvnw -pl ainer-authorization-server -am test
```

单个测试：

```bash
./mvnw -pl ainer-module-workspace -am \
  -Dtest=WorkspaceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Surefire XML 报告位于各模块 `target/surefire-reports/`。`target/` 是构建产物，不提交。
生产者验证必须使用锁定 Maven 4.0.0-rc-6 preview 的 Wrapper；系统 Maven 3.9+ 只由
`scripts/verify-maven-consumers.sh` 用于下游兼容门禁。
发布质量检查还必须执行 `./scripts/check-surefire-results.sh`；该脚本在没有报告、没有执行测试、
存在 failure/error 或任何 skipped 测试时失败。

## 4. PostgreSQL 与 Testcontainers

当前集成测试固定使用 `postgres:18.3-alpine`。没有 Docker-compatible runtime 时，`@Testcontainers(disabledWithoutDocker = true)` 会明确跳过数据库测试，不会降级为 H2。

本地迭代允许跳过，但必须在结果中说明。发布候选必须在 Docker 可用环境运行，确认 PostgreSQL
集成测试实际执行。候选 GitHub Actions 工作流会运行
`scripts/check-surefire-results.sh` 强制 `skipped=0`；在该工作流首次成功并被设为分支必需检查前，
它仍是尚未闭环的自动化门禁。

macOS 使用 Colima 时，Docker CLI context 本身不会自动成为 Testcontainers 的 socket 配置。先启动运行时，再把实际 Colima socket 和容器内 Docker socket 显式传给 Maven；将示例中的 `your-name` 替换为本机短用户名：

```bash
colima start --cpu 4 --memory 8 --runtime docker

DOCKER_HOST=unix:///Users/your-name/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./mvnw clean verify
```

`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 不可省略：否则 Testcontainers 的 Ryuk 容器会尝试 bind-mount 裸 Colima
socket 路径，virtiofs 报 `operation not supported`，Ryuk 启动失败使 `DockerAvailableDetector` 误判无 Docker，
`disabledWithoutDocker` 会跳过全部集成测试。启动后可用 `colima status` 与 `docker context inspect colima` 核对
runtime 和 socket。最终仍以 Surefire 报告中的 `skipped=0` 为准，不能仅根据 Docker daemon 可访问就宣称数据库测试已执行。

修改 `ainer-starter-persistence`、MyBatis-Plus 或 JSqlParser 时，除完整 Reactor 外还必须执行
starter 的真实 PostgreSQL 兼容测试，覆盖 `BaseMapper`、数据库 UUIDv7 生成键回填、自定义 XML、
显式资源归属 SQL 和分页；既有锁、CTE、`RETURNING` 与审计 XML 也必须由所属模块集成
测试回归。H2 或只检查应用上下文都不能替代这些门禁。

出现 Testcontainers 失败时依次检查：

1. Docker daemon 是否可访问；
2. PostgreSQL 镜像是否能拉取；
3. 端口、磁盘和容器日志；
4. Flyway migration 是否可从空库顺序执行；
5. 测试是否依赖执行顺序或共享脏数据。

## 5. 必测行为

### HTTP 与错误

- 成功和失败的真实 HTTP status；
- 稳定 `AINER.<MODULE>.<ERROR>` 错误码；
- `X-Request-Id` 与响应 `requestId`；
- 未知异常不泄露堆栈、SQL、密钥或供应商正文。

### 安全与资源

- 无 Token、无效签名、issuer/audience 不匹配；
- 缺失 `sub`、错误 `token_profile` 或错误 `claim_contract_version`；
- scope 允许但资源成员关系拒绝；
- 跨 Workspace 读取、更新和审计查询被拒绝；
- OWNER、PENDING/ACTIVE/REVOKED、禁用和撤销状态转换；
- 服务与人员 `token_profile` 隔离、精确 scope 与 claim 版本约束；
- 重放与 OWNER 恢复的 request/approve scope 分离、不同 SERVICE `sub`、过期和重复审批拒绝；
- OWNER 恢复必须保留原 REVOKED OWNER，存在 ACTIVE OWNER 或目标非 ACTIVE 时失败关闭；
- 内部控制面默认关闭，SERVICE/USER、最小 scope 和精确 trusted subject 拒绝路径。
- 低风险请求不得调用 introspection；每次高风险请求都必须在线校验且不缓存 active；
- inactive 返回 401，在线依赖失败返回 503，二者都不能泄漏 Token 或原始响应；
- introspection client 必须显式受信、只有 `token.introspect`，携带业务 scope 或普通 client 身份时拒绝；
- RFC 7009 撤销后 inactive；账号/主体禁用或 `sec_epoch` 不匹配均 inactive；
- `/actuator/prometheus` 无 Token 401，USER、缺 `platform.metrics.read` 403，只有最小 scope 的
  SERVICE 成功；业务 Resource Server 显式关闭时也不得公开指标。
- metrics bootstrap 只能创建无业务 scope、单一 `platform.metrics.read`、一分钟 Token 的 Client
  Credentials client，不得带 introspection 标记，并验证幂等与弱 secret 失败关闭。
- Authorization Code public client 必须强制 PKCE S256，覆盖 cookie/CSRF 登录、授权码单次交换、
  错误 verifier、缺失/`plain` challenge 和未注册 redirect URI；
- public client 不得出现 refresh token；人员 access token 必须包含稳定 `sub`（HumanAccount UUID）、
  `token_profile=USER_NEUTRAL_V1`、`claim_contract_version=1` 与 `roles`，JDBC authorization
  不得保存 password 属性或凭证。
- 品牌 `GET /login` 必须由服务端生成 CSRF、保持 SavedRequest，并覆盖 normal、凭据错误、
  HTTP 429 和认证基础设施 HTTP 503 四种状态；普通认证失败不得区分未知账号与错误密码，
  不得回显用户名、密码或底层异常。
- HTML `POST /login` 限速必须返回同一品牌页面、`Retry-After` 与 no-store；API/WebAuthn
  限速仍返回统一 JSON envelope。品牌页桌面与移动视口均须截图复核，并对四种状态执行
  axe-core，不能用新增生产预览端点绕过真实模板与样式。
- Passkey 启用配置必须对 RP/Origin/HTTPS/timeout 失败关闭；registration 与 authentication
  options 必须为 `userVerification=required`；
- 无 ACTIVE Passkey 时密码 bootstrap 可以完成 PKCE，Token 含 `amr=pwd` 与 `auth_time`；存在
  ACTIVE Passkey 时仅密码不得取得 authorization code；
- Passkey 协议记录、ACTIVE 生命周期和 REGISTERED 审计同事务；计数器/last-used 更新不产生
  重复登记审计；replacement 后旧凭证软撤销，并发撤销不能移除最后一个 ACTIVE 凭证。
- Passkey 恢复/enrollment 管理端必须把目标 `account_id` 绑定到当前 ACTIVE HumanAccount，不能借
  外部 subject 头推断归属；登录限流 HTTP 429 使用统一 envelope、`Retry-After`
  与 no-store，并且只匹配配置的 POST 路径。
- step-up 对匿名请求保留 Resource Server 401，对 SERVICE、缺/旧 `auth_time`、缺强因子和超出
  clock skew 的未来时间返回稳定 403；边界时间使用可注入 `Clock`。
- Workspace HTTP/应用/真实 PostgreSQL 测试必须覆盖 scope + ACTIVE OWNER/ADMIN、USER/MEMBER/
  跨 Workspace 拒绝、OWNER 不可由通用接口修改、邀请接受只认 `sub` 和同事务审计。
- Identity foundation 测试必须覆盖 HumanAccount/ServicePrincipal 的状态与 `security_epoch`
  单调约束、`sec_epoch` claim 与账号状态联合判定（禁用即时 401），以及 SERVICE 门禁对
  `actor_type`/`token_profile`/`claim_contract_version` 的失败关闭。
- browser client 控制面测试必须覆盖 SERVICE operator 白名单、`oauth.browser-clients.manage`
  最小 scope、PKCE 强制、无 secret 投影、蓝绿轮换、退役后新 Token 401 与
  `CREATED/ROTATED/RETIRED` 同事务审计。

### 数据与事务

- migration 从空库执行；
- 唯一、外键、check constraint 和索引支持预期行为；
- 事务中的第二步失败时完整回滚；
- 重复请求或重复事件具有定义好的幂等结果；
- 资源归属条件存在于查询与更新，而不只存在于 Controller。
- OWNER 恢复申请保留原 REVOKED OWNER、原申请 ID/发生时间，重复审批不重复提升；
- 归档的插入与热表删除在一事务中，反复执行不丢数、统一查询不重数；
- SIEM 以 `(occurredAt, id)` 稳定升序续传，跨热/归档边界不漏读，消费者可按 audit ID 去重；
- `findByToken` 在线检查在账号禁用或 `sec_epoch` 不匹配时立即 inactive，撤销不依赖进程内事件。

### AI provider

- 非流式、SSE、usage 和费用；
- 超时、限流、预算、模型白名单；
- prompt fingerprint 而非正文审计；
- API key 和供应商错误正文脱敏。

## 6. 测试数据规则

- 只使用生成的 UUID、虚构账号和占位密钥；
- 禁止复制生产数据、真实 Token、prompt 或客户信息；
- 时间与随机值应可注入或可断言，避免依赖机器时区和执行顺序；
- 测试不得依赖外部商业 API，provider 使用本地合约服务或受控 stub。

## 7. 质量门禁

合并前：受影响模块测试、完整 `./mvnw clean verify`、`git diff --check`。

发布前还必须执行 Maven Artifact Plugin 的构建计划与两次构建比较，并运行独立消费者门禁：

```bash
./mvnw artifact:check-buildplan
AINER_REPRO_REPOSITORY="$(mktemp -d)"
./mvnw -Dmaven.repo.local="$AINER_REPRO_REPOSITORY" clean install
./mvnw -Dmaven.repo.local="$AINER_REPRO_REPOSITORY" clean verify artifact:compare
./scripts/verify-maven-consumers.sh
```

两次构建使用同一个隔离本地仓库，避免既有缓存成为参考。consumer 脚本必须证明 Maven 4 与
系统 Maven 3.9+ 外部项目都能只通过 BOM 和公开坐标完成构建；同时检查 19 个标准 Consumer
POM 中的 `${revision}` 都有当前安装版本属性可解析，并检查 `ainer-spring` JAR 含
`META-INF/spring-configuration-metadata.json`。脚本中的独立授权 Golden Consumer 还必须在两套 Maven
下真实执行 JUnit：由外部项目定义产品 Permission/policy/query constraint，调用
`AuthorizationService` 与 `DefaultQueryAuthorizationPlanner`，并强制 Surefire 报告为 1 test、零
failure/error/skipped。这些门禁是本地/自动化工程要求；消费本地 SNAPSHOT 不表示已经存在正式制品
仓库发布流程，也不替代真实产品的参数化 SQL、row/字段投影与关系矩阵。候选 CI 已编排上述命令，但在 Maven 4 RC6 官方持久发行包可下载并首次
完整成功前，不能称为生效的正式 CI。脚本默认读取根 POM 的 `revision`；发布过程通过
`AINER_VERSION=<目标版本>` 覆盖时，该值也会作为 `-Drevision` 传给两次生产者构建和两个
consumer。

此外还必须确认：数据库测试未因 Docker 缺失而跳过、两个可执行发行物均能启动、Flyway 从空库成功、升级 migration 在备份副本成功、关键鉴权与健康检查通过。M4.2 还要在可运行 Testcontainers 的环境执行双人审批、锁定重检、归档回滚和游标边界集成测试。M4.3 还要在真实 PostgreSQL 上执行 Authorization Server 协议 smoke，证明专用/普通 introspection client 隔离、active、RFC 7009 撤销和 Identity `sec_epoch`，并用接近真实规模数据检查 epoch 查询计划；M4.5 还要执行真实浏览器 HTTP 会话的 PKCE S256 正反门禁，并检查 JDBC authorization 不落凭证。M4.6 当前还必须执行 Passkey options、条件门禁、虚拟 authenticator 签名 ceremony、恢复/enrollment、登录限流和 step-up 门禁；M6 品牌登录发布候选还必须用真实 Chromium 验证四种合同状态的桌面/移动布局、axe-core、CSRF/SavedRequest、通用错误语义和精确静态代理。在宣称生产 MFA 前，必须另补主流真实设备的 registration/authentication、丢失/被盗/同步凭证、恢复通知和多节点 session 验证。生产可观测性切片还要用独立 metrics client 抓取两个真实 exporter，并验证多节点、Token endpoint/数据库故障和告警路由。当前验证快照见 [`project-status.md`](project-status.md)。
