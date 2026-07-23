# ADR-0003：AI Model Gateway 基线

- 状态：Accepted
- 日期：2026-07-22

## 背景

Aurora 的差异化不能只停留在“未来支持 AI”。在 M1 证明 PostgreSQL、事务和模块边界后，M2 需要用一个可运行的垂直切片证明：业务能够通过稳定端口调用模型，平台能够在出网前执行策略，并能对成功、失败和拒绝调用进行租户级用量与费用审计。

本决策只覆盖模型网关最小闭环，不把 Agent、RAG、工具调用、评测和完整 DLP 一次性塞进第一个模块。

## 决策

1. 创建 `aurora-module-ai-runtime`，模块内部采用 `api -> application -> domain` 与 `infrastructure -> application/domain` 的依赖方向。领域与应用端口不依赖任何模型厂商 SDK。
2. M2 提供一个 OpenAI-compatible adapter，使用 JDK `HttpClient` 与 Jackson 3，调用 `/v1/chat/completions`。不为单一供应商引入新的运行时 SDK。
3. 同时支持非流式和 SSE。流式请求明确发送 `stream_options.include_usage=true`，以最终 usage chunk 为计费依据；供应商不返回 usage 时才使用本地估算，并在审计中标记 `usageEstimated=true`。
4. 所有调用先执行模型白名单、提示长度、敏感凭据模式、租户每分钟限流和当日预算策略，再访问供应商。
5. 当日预算使用 PostgreSQL 审计表作为权威账本。预算预占与汇总在同一事务中，通过租户级 transaction advisory lock 串行化，防止并发请求共同穿透预算。最终以实际费用替换预估费用。
6. M2 每分钟限流是进程内、节点级防护，不宣称为集群级精确限流。多实例部署前必须替换为共享限流适配器或在受信任网关实施一致策略。
7. 审计表记录租户、主体、请求 ID、供应商、请求/解析模型、流式标志、状态、策略决策、Token、预估/实际费用、币种、耗时、供应商请求 ID、稳定错误码和时间。
8. 审计表不保存 prompt 或模型输出正文，只保存 prompt 的 SHA-256 指纹。指纹用于相关性调查，不用于还原内容；低熵输入仍可能被字典猜测，因此不对外返回指纹。
9. 供应商 API key 只允许从外部配置注入。默认要求 HTTPS，`allow-insecure-http` 仅用于本地测试。配置和异常不得输出 key 或供应商原始错误正文。
10. M2 的 `X-Aurora-Tenant-Id`、`X-Aurora-Subject-Id` 只是显式调用上下文，不是身份凭证。M3 必须由 Resource Server 的认证结果和受信任租户解析器生成上下文，届时外部请求不得自行声明租户身份。
11. AI runtime 默认关闭。缺少 provider URL、key、默认模型或合法价格/限额时，启用过程快速失败。
12. `workspace` 与 AI 成为第二个真实 PostgreSQL 消费者后，抽取 `aurora-starter-persistence`，只承载 MyBatis/Flyway/PostgreSQL/UUID 的公共装配；业务表、Mapper、Repository 和 transaction use case 继续留在所属模块。

## 协议边界

M2 对内暴露 Aurora 自己的命令、结果、Provider 端口和流观察者；OpenAI-compatible JSON 只存在于 infrastructure adapter。这样做允许未来增加不同协议供应商，又不会把供应商字段扩散到业务模块。

SSE 对外事件固定为：

- `delta`：增量文本与 invocation ID；
- `usage`：最终模型、finish reason、Token、费用与耗时；
- `done`：正常结束；
- `error`：稳定 Aurora 错误码，不含供应商响应正文。

M2 不承诺供应商工具调用、结构化输出、图像、多模态和 Responses API 的字段兼容。这些能力必须通过显式 Aurora 契约增量设计。

## 已验证行为

- JDK 25 + Spring Boot 4.1.0 Reactor 可以编译并打包包含 AI runtime 的可执行 JAR。
- 本地 PostgreSQL 18.4 空库可连续执行 workspace 与 AI 两条 Flyway migration。
- OpenAI-compatible 非流式请求正确传递 Bearer 认证、模型、消息、最大输出 Token 和温度，并解析实际 usage。
- SSE 能连续传递增量文本、最终 usage 和完成事件；供应商缺失 usage 时有显式估算标记。
- 敏感凭据模式在出网前被拒绝；超预算调用被 PostgreSQL 权威账本拒绝。
- 成功、流式成功、敏感拒绝、预算拒绝和供应商失败均形成审计记录。
- 供应商 503 的原始错误正文不会进入客户端错误；跨租户读取 invocation 返回 404。
- `aurora_ai_invocation` 没有 prompt、response 或 content 正文列，只有 `prompt_fingerprint`。
- Provider 合约测试覆盖非流式、SSE、usage fallback 和 429 脱敏；PostgreSQL Testcontainers 测试覆盖 migration、HTTP、策略、审计隔离与预算预占。

## 后果

正面：

- Aurora 第一次具备可直接被业务消费的 AI 平台能力，而不是厂商 SDK 的薄封装。
- 策略、费用和审计位于统一出网边界，可持续演进为企业治理与商业收费能力。
- 供应商协议被限制在 adapter 内，未来可以增加 provider 或独立发行物。
- 第二个数据消费者证明了 persistence starter 的真实共性。

代价与限制：

- 当前价格是运维配置，不是自动同步的供应商价格表；模型换价必须显式更新并审计配置变更。
- Node-local rate limiter 不适合多实例精确配额。
- 简单敏感模式不是完整 DLP，也不处理 prompt injection、输出泄露和工具越权。
- 当前上下文请求头没有密码学身份保证，不能直接作为公网生产认证方案。
- SSE 中断后的供应商取消、断点续传、幂等和计费对账仍需后续强化。

## 后续升级条件

M3 优先接入 Resource Server 与 Authorization Server，让租户/主体来自已认证上下文。AI 后续里程碑至少包括：共享限流、provider credential reference/KMS、指标与 trace、配置化模型目录、价格版本、输出策略、评测数据集、调用幂等、供应商降级和费用对账。

如果增加第二种非 OpenAI 协议 provider，再从已验证差异中抽取 provider capability 描述；不得预先构造“大一统”厂商接口。
