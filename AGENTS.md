# AGENTS.md

Ainer Boot 的 AI/人协作规则。新会话先读本文件，再按顺序阅读：

1. `README.md`
2. `docs/README.md`
3. `docs/project-status.md`
4. `docs/architecture.md`
5. `docs/conventions.md`
6. `docs/decisions/README.md`
7. `docs/decisions/0001-independent-architecture-baseline.md`
8. `docs/decisions/0004-ainer-brand-and-naming-baseline.md`
9. 与任务直接相关的开发手册、专题文档和 ADR

数据库任务必须读 `docs/database.md` 与 `docs/testing.md`；安全任务必须读 `docs/security.md` 和 ADR-0005 至 ADR-0011；AI 任务必须读 `docs/ai-gateway.md` 与 ADR-0003。

## 工程定位

Ainer 是 JDK 25 + Spring Boot 4.1 的 AI 原生企业应用平台底座。当前已经具备 framework、可信租户化 `workspace`、AI Model Gateway、Identity、JWT Resource Server 与独立 OAuth 2.1/OIDC Authorization Server 基线。

模块化单体是默认交付形态。服务化演进通过独立应用装配、稳定契约和基础设施适配器完成，不承诺同一个应用修改一行 YAML 就自动变成微服务。

## 品牌与命名

- 正式品牌是 `Ainer`，含义为 **AI-Native Extensible Runtime**；开源脚手架产品名是 `Ainer Boot`。
- 活动技术标识统一使用 `ainer-*`、`dev.ainer.*`、`ainer.*`、`AINER_*`、`X-Ainer-*` 和 `AINER.*`。
- 历史 ADR 中的旧名称仅用于保留决策语境，不得作为当前代码、配置或产品命名依据。
- 不得重新引入旧品牌前缀或建立双包名、双配置、双错误码兼容层，除非出现真实外部消费者并另立 ADR。
- 域名状态、商标前置条件和完整迁移记录以 ADR-0004 为准。

## 开始工作前

```bash
git status --short --branch
git log --oneline --decorate -12
mvn test
```

保持用户已有改动，不回滚、不覆盖、不把无关文件混入提交。

## 最高优先级规则

### 1. Clean-room 与许可证

- 禁止复制 BladeX、Snowy、Dante 或其他竞品的源码、注释、模板、包结构和专有命名。
- 竞品只用于验证需求、行为和取舍。实现必须基于官方标准、官方文档和 Ainer 自己的设计。
- 引入代码或依赖时记录来源、许可证和版本；许可证不清晰时停止引入。
- 不在文档中使用“可直接移植竞品代码”一类表述。

### 2. 依赖方向

```text
ainer-core <- ainer-spring <- starter <- application/module
```

- `ainer-core` 必须保持零 Spring、零 Servlet、零 ORM 依赖。
- framework 不依赖业务模块。
- 业务模块之间通过显式契约交互，禁止跨模块注入对方 Service 实现。
- 反向通知使用领域事件或可靠消息；事件不能承担同步请求/响应。
- `spring.main.allow-circular-references=false`，禁止用 `@Lazy` 掩盖设计环。

### 3. 装配与部署

- `ainer.runtime.mode=monolith|service` 只选择本地/远程适配器，不改变部署拓扑、数据库归属或事务边界。
- 单体与服务化必须有不同可执行应用或不同明确装配清单。
- 只有真实存在远程消费者时才增加 remote adapter；不提前创建空 Feign 接口。
- Starter 使用 `@AutoConfiguration` 和 `AutoConfiguration.imports`。

### 4. API 与错误

- HTTP status 是传输层权威语义，禁止“所有错误都返回 200”。
- 错误码使用稳定字符串：`AINER.<MODULE>.<ERROR>`，禁止 hash 分配和手工数字段位。
- 业务异常使用 `BusinessException(ErrorCode)`；未知异常不得向客户端泄露堆栈和内部消息。
- 所有响应携带请求追踪标识；敏感数据不得进入错误消息或日志。

### 5. 安全

- 采用 Spring Security 与标准 OAuth 2.1/OIDC；禁止引入 Sa-Token 或自造 OAuth2 Token 表。
- Resource Owner Password Credentials 不是支持目标。短信、微信等登录若需要接入，按标准扩展授权或登录编排单独设计。
- 内部请求头只能作为辅助信号，不能替代 mTLS、签名身份或受控网络边界。
- 密钥不得硬编码、XOR 混淆或使用固定 IV；字段加密必须使用 KMS/密钥信封和认证加密。

### 6. 数据与 AI

- PostgreSQL 是首选业务数据库；集成测试使用真实 PostgreSQL Testcontainers，不用 H2 模拟 PostgreSQL。
- SQL 参数必须绑定，禁止拼接用户 ID、URL、组织 ID 等权限条件。
- AI 调用必须经过模型网关并记录租户、模型、Token/费用、耗时、结果状态和策略决策。
- AI 审计默认不保存 prompt 和输出正文；新增正文存储必须先完成数据分类、保留期、加密、访问审计和删除机制设计。
- 租户/主体只允许来自已验证 JWT 的 `tenant_id` / `sub`；禁止重新引入外部身份请求头。
- 业务资源查询和更新必须显式携带可信 tenant 条件；scope 只表示能力范围，不能替代资源成员关系或所有权检查。
- Workspace 邀请在受邀主体以同 tenant 的可信 JWT 接受前只能是 `PENDING`；只有 `ACTIVE` 成员参与资源授权。
- OWNER 只能通过锁定 Workspace 的专用事务转移；通用成员接口不得授予、降级或移除 OWNER。
- 高价值授权决策必须持久化审计；受保护写操作不得在审计失败时静默继续。
- Identity Directory 只能返回 ACTIVE tenant/user/membership 的安全投影，不得暴露密码哈希或 OAuth 协议数据。
- 账号禁用与成员撤销必须和 access-event outbox 在同一事务提交；进程内异步事件不能充当可靠撤销通知。
- 内部 Directory/事件接口必须要求 `actor_type=SERVICE` 与最小 scope；事件 tenant 只允许来自精确可信 publisher subject。
- outbox 网络投递不得发生在数据库领取事务中；领取、确认、失败必须使用 event ID + lease owner，消费者必须用 receipt 幂等。
- Identity 撤销只把事件发生前已有的同 tenant/subject membership 置为 `REVOKED`；不得因 OWNER 不变量保留已禁用账号访问权，也不得让旧事件撤销后建 membership。
- 耗尽事件重放与 OWNER 恢复必须使用不同 SERVICE `sub` 的 request/approve 两阶段流程；两阶段都要重新绑定 tenant，不得把两类 scope 授予同一生产 client。
- 耗尽重放必须复用原 event ID/内容/发生时间，不得创建新事件绕过 receipt 幂等。OWNER 恢复不得重新激活或降级原 REVOKED OWNER。
- 授权审计从热表删除前必须在同一事务中存在同 ID 归档记录；归档表默认不删除。同库归档不得被宣称为 WORM 或法律不可抵赖存储。
- SIEM 导出使用 `(occurredAt, id)` 稳定游标，服务主体必须匹配精确 trusted exporter；消费方必须按 audit ID 去重并持久化 checkpoint。
- 高风险 API 在线校验必须失败关闭：inactive 返回 401，introspection 依赖失败返回 503，不得回退到仅验证 JWT 或缓存 active 结果。
- introspection client 必须独立、无 tenant、无业务 scope且显式受信；人员 Token 在线状态必须同时检查 Identity 当前状态与 revocation epoch。
- 供应商错误正文和 API key 禁止进入客户端、数据库或日志。
- Prompt、工具调用、知识检索、模型输出都是不可信输入，必须设计权限、审计与数据泄露防护。

## 编码规则

- 使用构造器注入；禁止字段注入。
- 优先不可变对象和 record；持久化实体按 ORM 约束使用普通类。
- API 模型显式设计。MapStruct 只生成映射实现，不会生成 DTO 类。
- 包结构按 feature 聚合，避免六层以上目录和全局 `util` 垃圾桶。
- `jakarta.*` 替代旧 Java EE `javax.*`；JDK 自带 `javax.annotation.processing` 等除外。
- 子模块依赖版本由 BOM 管理，业务 POM 不写依赖版本。

## 验证纪律

每个改动至少执行与风险匹配的测试。合并前必须通过：

```bash
mvn test
```

新增 Starter 时至少包含自动装配测试；新增 HTTP 错误时验证真实状态码和响应体；新增数据模块时包含 PostgreSQL 集成测试和 migration 重放测试；新增 AI provider 时包含非流式、SSE、usage、超时/限流和错误脱敏合约测试。

## 文档纪律

- `docs/README.md` 是文档导航和维护协议；新增文档必须接入该索引，不建立孤岛文档。
- 长期规范、时间敏感状态、研究记录和 ADR 必须分开；不得把计划能力写成已经实现。
- HTTP、行为、配置、数据库、运行或里程碑变化时，按 `docs/README.md` 的映射在同一变更更新文档。
- 已接受 ADR 不改写结论。设计改变时新增 ADR，并把旧 ADR 标记为被取代。
- 动态测试数量、当前缺口和下一里程碑只维护在 `docs/project-status.md`，发布变化维护在 `CHANGELOG.md`。
- 文档示例禁止使用真实密码、Token、私钥、API key、客户数据、prompt 或供应商正文。
- 文档也要通过 `git diff --check`；命令、链接与版本必须对照当前代码核实。

## 当前优先级

1. 把 M4.3 选择性在线撤销接入生产 Authorization Server 高可用、容量、专用凭据轮换、指标 exporter/dashboard/告警和多节点故障验证。
2. 把 M4.2 控制面接入生产 IAM 职责分离、外部不可变审计副本和正式 SLO/错误预算。
3. 为 M3 补齐 tenant ownership transfer、人员账号/Client 控制面、Authorization Code + PKCE 端到端测试、MFA 与密钥轮换。
4. 建立 AI 集群级限流、凭据托管、指标追踪、输出策略与评测基线。
5. 再进入代码生成器、其他业务资源的数据权限和服务化发行物。

## Git

提交格式：`type(scope): 中文描述`，例如：

```text
feat(ainer-starter-web): 增加真实 HTTP 错误响应
test(ainer-spring): 验证运行模式条件装配
docs(architecture): 修正服务化装配边界
```

全程使用中文与用户沟通。
