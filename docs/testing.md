# Ainer 测试与质量门禁

> 文档类型：长期规范 · 状态：生效 · 最近核对：2026-07-23 · 适用版本：`0.1.x`

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

发布前还必须确认：数据库测试未因 Docker 缺失而跳过、两个可执行发行物均能启动、Flyway 从空库成功、升级 migration 在备份副本成功、关键鉴权与健康检查通过。M4.2 还要在可运行 Testcontainers 的环境执行双人审批、锁定重检、归档回滚和游标边界集成测试。M4.3 还要在真实 PostgreSQL 上执行 Authorization Server 协议 smoke，证明专用/普通 introspection client 隔离、active、RFC 7009 撤销和 Identity epoch，并用接近真实规模数据检查 epoch 查询计划。手工 PostgreSQL 与 loopback 证据是补充，不取代发布候选环境中不跳过的自动门禁。当前验证快照见 [`project-status.md`](project-status.md)。
