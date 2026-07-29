# Ainer 测试与质量门禁

> 文档类型：长期规范 · 状态：生效 · 最近核对：2026-07-26 · 适用版本：`0.1.x`

## 1. 目标

测试用于证明业务行为、依赖方向、协议语义和真实基础设施兼容性。覆盖率数字不能替代高风险拒绝路径、事务回滚和数据隔离验证。

## 2. 测试分层

| 层级 | 关注点 | 当前示例 |
|---|---|---|
| Domain | 值对象、状态机、计算和不变量 | `WorkspaceTest`、`AiGatewayPolicyTest` |
| Application | 用例、授权、幂等、端口交互 | `WorkspaceApplicationServiceAuthorizationTest`、`IdentitySecurityLifecycleTest` |
| Architecture | 包依赖、层间方向、循环依赖 | 各业务模块 `*ArchitectureTest` |
| Adapter contract | HTTP、JWT、Client Credentials、provider、序列化和错误脱敏 | Web/Security starter、Identity HTTP transport 与 OpenAI-compatible provider 测试 |
| PostgreSQL integration | Flyway、MyBatis、约束、锁和事务回滚 | 各模块 `*IntegrationTest` |
| Executable smoke | 发行物启动、健康端点和自动装配 | `AinerServerApplicationTest`、Authorization Server 集成测试 |

新增能力至少覆盖正常路径、边界输入、权限拒绝和基础设施失败。并发所有权、预算扣减、outbox 等事务敏感行为必须在真实 PostgreSQL 上验证。

## 3. 标准命令

完整验证：

```bash
mvn clean test
```

模块验证：

```bash
mvn -pl ainer-module-workspace -am test
mvn -pl ainer-module-identity -am test
mvn -pl ainer-module-ai-runtime -am test
mvn -pl ainer-authorization-server -am test
```

单个测试：

```bash
mvn -pl ainer-module-workspace -am \
  -Dtest=WorkspaceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Surefire XML 报告位于各模块 `target/surefire-reports/`。`target/` 是构建产物，不提交。

## 4. PostgreSQL 与 Testcontainers

当前集成测试固定使用 `postgres:18.3-alpine`。没有 Docker-compatible runtime 时，`@Testcontainers(disabledWithoutDocker = true)` 会明确跳过数据库测试，不会降级为 H2。

本地迭代允许跳过，但必须在结果中说明。发布候选必须在 Docker 可用环境运行，确认 PostgreSQL 集成测试实际执行；在 CI 尚未自动阻止跳过前，这是人工发布门禁。

macOS 使用 Colima 时，Docker CLI context 本身不会自动成为 Testcontainers 的 socket 配置。先启动运行时，再把实际 Colima socket 和容器内 Docker socket 显式传给 Maven；将示例中的 `your-name` 替换为本机短用户名：

```bash
colima start --cpu 4 --memory 8 --runtime docker

DOCKER_HOST=unix:///Users/your-name/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn clean test
```

启动后可用 `colima status` 与 `docker context inspect colima` 核对 runtime 和 socket。最终仍以 Surefire 报告中的 `skipped=0` 为准，不能仅根据 Docker daemon 可访问就宣称数据库测试已执行。

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

### 安全与租户

- 无 Token、无效签名、issuer/audience 不匹配；
- 缺失 `sub` 或 `tenant_id`；
- scope 允许但资源成员关系拒绝；
- 跨 tenant 读取、更新和审计查询被拒绝；
- OWNER、PENDING/ACTIVE/REVOKED、禁用和撤销状态转换；
- 服务与人员 `actor_type` 隔离、tenant-bound 与平台级 Directory scope；
- outbox lease owner/过期、重试上限、发布确认和耗尽指标；
- 事件重复、乱序、跨 tenant、后建 membership 保护和消费事务回滚。
- 重放与 OWNER 恢复的 request/approve scope 分离、不同 SERVICE `sub`、tenant 二次绑定、过期和重复审批拒绝；
- OWNER 恢复必须保留原 REVOKED OWNER，存在 ACTIVE OWNER 或目标非 ACTIVE 时失败关闭；
- 内部控制面默认关闭，SERVICE/USER、最小 scope 和精确 trusted subject 拒绝路径。
- 低风险请求不得调用 introspection；每次高风险请求都必须在线校验且不缓存 active；
- inactive 返回 401，在线依赖失败返回 503，二者都不能泄漏 Token 或原始响应；
- introspection client 必须显式受信、只有 `token.introspect`，携带 tenant、业务 scope 或普通 client 身份时拒绝；
- RFC 7009 撤销后 inactive；Identity user/tenant/membership 非 ACTIVE 与 `issuedAt <= latestRevokedAt` 均 inactive，事件后新 Token 可恢复。
- `/actuator/prometheus` 无 Token 401，USER、tenant-bound SERVICE 与缺 `platform.metrics.read` 403，只有 tenantless SERVICE 成功；业务 Resource Server 显式关闭时也不得公开指标。
- metrics bootstrap 只能创建无 tenant、单一 `platform.metrics.read`、一分钟 Token 的 Client Credentials client，不得带 introspection 标记，并验证幂等与弱 secret 失败关闭。
- Authorization Code public client 必须强制 PKCE S256，覆盖 cookie/CSRF 登录、授权码单次交换、
  错误 verifier、缺失/`plain` challenge 和未注册 redirect URI；
- public client 不得出现 refresh token；人员 access token 必须包含稳定 `sub`、`tenant_id` 与
  `roles`，JDBC authorization 不得保存 password 属性或凭证。
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
- 恢复/enrollment 管理端必须把目标 `(tenant_id, subject_id)` 绑定到 ACTIVE default Identity membership，
  跨 tenant 目标即使 subject 存在也必须拒绝；登录限流 HTTP 429 使用统一 envelope、`Retry-After`
  与 no-store，并且只匹配配置的 POST 路径。
- step-up 对匿名请求保留 Resource Server 401，对 SERVICE、缺/旧 `auth_time`、缺强因子和超出
  clock skew 的未来时间返回稳定 403；边界时间使用可注入 `Clock`。
- tenant 成员管理 HTTP/应用/真实 PostgreSQL 测试必须覆盖 USER scope + ACTIVE OWNER/ADMIN、
  SERVICE/MEMBER/跨 tenant 拒绝、OWNER 不可由通用接口修改、重激活和同事务审计。
- 平台 Identity 预配必须覆盖 tenantless SERVICE、tenant/user 成对 read/write scope、精确
  operator 白名单、缺失 `Idempotency-Key`、相同摘要重放与不同摘要冲突、tenant code 并发预留、
  已有 ACTIVE/非 ACTIVE 用户、惰性过期、与首租户 bootstrap 的共享锁，以及核心
  tenant/user/membership 零污染。
- 预配 HTTP 成功与错误响应必须 `no-store`，不得返回 request fingerprint、幂等键、密码、激活
  Token 或 secret；request 与 phase audit 必须同事务，数据库 schema 也不得出现认证明文列。
- 新用户激活必须覆盖：outbox 密文可由正确 key version 解密、数据库只存 secret 摘要、错误 secret
  尝试跨事务持久化、达到上限锁定、过期、成功单次消费、重放 401、用户本人设置的密码哈希，以及
  tenant/user/默认 OWNER/状态/审计任一步失败时整体回滚。
- 已有用户接受必须覆盖：通知不含激活材料、匿名 401、缺 scope/SERVICE/错误 subject 403、目标
  ACTIVE USER 成功、不会复制 user、不会覆盖原默认 tenant，新 OWNER membership 为非默认。
- notification relay 必须覆盖租约领取、provider 失败延迟重试、正确 key rotation 读取、
  tamper/未知 key 拒绝、成功确认后 `PUBLISHED`、payload 销毁和最大尝试后不再自动领取；HTTP
  合约还必须覆盖独立 Client Credentials Bearer、稳定 `Idempotency-Key`、版本化 envelope、
  已有用户无 secret 投影、HTTPS/URI 失败关闭和下游鉴权拒绝。测试 publisher 不得把明文打印到
  日志。
- notification receipt 必须覆盖独立 tenantless SERVICE credential、专用 write scope、精确 gateway
  client 白名单、默认关闭与空白名单失败关闭；HTTP 负向矩阵包含匿名、错误 scope、tenant-bound
  SERVICE 和未知 gateway client。只有 `PUBLISHED` notification 可登记，`DELIVERED` 不得有
  failure code，`FAILED` 必须有受限稳定码，未来时间只允许五分钟 clock skew。
- receipt 幂等测试必须覆盖相同 gateway event 重放、同 notification 的不同 event 但相同终态、
  相同事件不同内容和矛盾终态；前两者返回原事实且不重复计数，后两者返回 409。数据库 schema
  必须证明不存在正文、联系地址、secret、Token 或供应商原始 body 列。
- 平台显式取消必须覆盖成对 write scope、精确 operator、`REQUESTED -> CANCELLED`、重复调用
  幂等、新用户 grant 同步取消、已有用户无 grant、预期 grant 缺失时整笔回滚、通知 payload
  销毁，以及取消 `changeReference` 只进入唯一阶段审计。
- 平台 tenant/user 分页必须覆盖各自独立 read scope、tenantless SERVICE、白名单、`page >= 1`、
  `size <= 100`、稳定排序和正确 total；HTTP 响应不得出现 password hash、OAuth secret、激活
  材料或通知目标，尚未激活的 reservation 不得混入核心列表。

### 数据与事务

- migration 从空库执行；
- 唯一、外键、check constraint 和索引支持预期行为；
- 事务中的第二步失败时完整回滚；
- 重复请求或重复事件具有定义好的幂等结果；
- tenant 条件存在于查询与更新，而不只存在于 Controller。
- 耗尽重放保留原 event ID/内容/发生时间，仅重置可重试状态，且原 receipt 幂等不失效；
- 归档的插入与热表删除在一事务中，反复执行不丢数、统一查询不重数；
- SIEM 以 `(occurredAt, id)` 稳定升序续传，跨热/归档边界不漏读，消费者可按 audit ID 去重；
- 撤销传播 Timer 只记录首次成功消费，重复 receipt 不重复计时，负时钟偏差按 0 处理。

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

合并前：受影响模块测试、完整 `mvn clean test`、`git diff --check`。

发布前还必须确认：数据库测试未因 Docker 缺失而跳过、两个可执行发行物均能启动、Flyway 从空库成功、升级 migration 在备份副本成功、关键鉴权与健康检查通过。M4.2 还要在可运行 Testcontainers 的环境执行双人审批、锁定重检、归档回滚和游标边界集成测试。M4.3 还要在真实 PostgreSQL 上执行 Authorization Server 协议 smoke，证明专用/普通 introspection client 隔离、active、RFC 7009 撤销和 Identity epoch，并用接近真实规模数据检查 epoch 查询计划；M4.5 还要执行真实浏览器 HTTP 会话的 PKCE S256 正反门禁，并检查 JDBC authorization 不落凭证。M4.6 当前还必须执行 Passkey options、条件门禁、虚拟 authenticator 签名 ceremony、恢复/enrollment、登录限流和 step-up 门禁；M6 品牌登录发布候选还必须用真实 Chromium 验证四种合同状态的桌面/移动布局、axe-core、CSRF/SavedRequest、通用错误语义和精确静态代理。在宣称生产 MFA 前，必须另补主流真实设备的 registration/authentication、丢失/被盗/同步凭证、恢复通知和多节点 session 证据。M4.7 还要执行 tenant 成员管理的真实 PostgreSQL + Bearer HTTP 正反门禁，并确认 API 与 migration 只存在于 Identity 权威运行时。M4.8A 必须执行平台预配与激活的真实 PostgreSQL 并发、Bearer HTTP 正反门禁、默认关闭/错误配置、operator bootstrap、通知重试、终态回执幂等/冲突、过期/回放和无孤儿 ACTIVE tenant 测试；本机临时 schema smoke 只能补充 DDL/事务证据，不能替代发布候选环境中 0 skipped 的完整 Testcontainers 门禁。真实送达声明还必须使用真实外部网关与供应商沙箱或正式通道，覆盖 credential、供应商事件映射、重放和失败演练；本地 stub、数据库回执或合成 `DELIVERED` 都不能替代。生产可观测性切片还要用独立 metrics client 抓取两个真实 exporter，并验证多节点、Token endpoint/数据库故障和告警路由。当前验证快照见 [`project-status.md`](project-status.md)。
