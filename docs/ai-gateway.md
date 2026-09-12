# AI Model Gateway 使用与运维

> 适用版本：M3 foundation · 2026-07-22

## 1. 能力与边界

`ainer-module-ai-runtime` 提供 OpenAI-compatible chat completions 的统一入口：

- 非流式与 SSE 流式调用；
- 默认模型和模型白名单；
- 提示长度、常见密钥模式、subject 速率和当日预算策略；
- Token、费用、耗时、状态和策略审计；
- 供应商超时、限流、不可用和协议错误的稳定映射；
- 不保存 prompt 与输出正文。

M3 已接入 Resource Server 和可信认证上下文；主体分钟限流已接入 `RateLimitPort`（`ainer.cache.type=redis`
时跨实例共享计数），当前仍不包含完整 DLP、输出 guardrail、Agent/Tool/RAG 或自动价格同步。

Run / Artifact 与 Knowledge 的后续候选边界分别记录在
[`design/ai-runtime-data-model.md`](design/ai-runtime-data-model.md) 和
[`design/knowledge-data-model.md`](design/knowledge-data-model.md)。它们仍是 Proposed 设计，不是
当前 Model Gateway 已交付能力。

## 2. 启用配置

AI runtime 默认关闭。生产环境至少提供：

| 环境变量 | 含义 | 默认值 |
|---|---|---|
| `AINER_AI_ENABLED` | 是否装配 AI runtime | `false` |
| `AINER_AI_PROVIDER_NAME` | 审计中的 provider 名称 | `openai-compatible` |
| `AINER_AI_BASE_URL` | 供应商根 URL；自动追加 `/v1/chat/completions` | 无 |
| `AINER_AI_API_KEY` | Bearer key；必须由 secret manager 或环境注入 | 无 |
| `AINER_AI_DEFAULT_MODEL` | 请求未指定模型时使用 | 无 |
| `AINER_AI_ALLOWED_MODELS` | 逗号分隔的允许模型；必须包含默认模型 | 默认模型 |
| `AINER_AI_CONNECT_TIMEOUT` | 建连超时 | `5s` |
HEAD
| `AINER_AI_REQUEST_TIMEOUT` | 单次请求超时（**只覆盖到响应头**，见 §2.1） | `60s` |
| `AINER_AI_TOTAL_TIMEOUT` | 单次非流式调用总时长上限（覆盖响应体读取） | `120s` |
| `AINER_AI_STREAM_TOTAL_TIMEOUT` | 单次 SSE 流式调用总时长上限（覆盖响应体读取） | `600s` |
| `AINER_AI_SELF_HEAL_ENABLED` | 是否启用中间态定时自愈 | `true` |
| `AINER_AI_SELF_HEAL_STUCK_THRESHOLD` | 中间态允许停留的最长时间（必须大于流式总超时 + 1m） | `15m` |
| `AINER_AI_SELF_HEAL_SCAN_INTERVAL_MS` | 自愈扫描周期（毫秒，1000..3600000） | `60000` |
| `AINER_AI_SELF_HEAL_BATCH_SIZE` | 单次清扫每类中间态最多处理的行数 | `200` |
| `AINER_AI_REQUESTS_PER_MINUTE` | 每 subject 每分钟限流（`ainer.cache.type=redis` 时跨实例共享计数；默认 `local` 时仅每实例） | `60` |
| `AINER_AI_SUBJECT_DAILY_BUDGET` | 每 subject UTC 日预算 | `10.00` |
| `AINER_AI_MAX_PROMPT_CHARACTERS` | 所有消息内容字符总上限 | `100000` |
| `AINER_AI_CURRENCY` | 三位大写币种代码 | `USD` |
| `AINER_AI_INPUT_PER_MILLION_TOKENS` | 每百万输入 Token 单价 | `0` |
| `AINER_AI_OUTPUT_PER_MILLION_TOKENS` | 每百万输出 Token 单价 | `0` |

`AINER_AI_ALLOW_INSECURE_HTTP=true` 只允许本机测试。生产配置必须使用 HTTPS，URL 不允许 user-info、query 或 fragment。

价格是平台预算与审计的运维数据。不要盲目复制第三方示例价格；供应商、模型或账户价发生变化时，应由受控配置变更更新。

### 2.1 超时分层：为什么 `request-timeout` 不够

JDK 侧的事实（已核对官方 issue）：

- [JDK-8258397](https://bugs.openjdk.org/browse/JDK-8258397)「HttpClient: Investigate supporting
  timeout for reading body bytes」（调查单，Closed）；
- [JDK-8208693](https://bugs.openjdk.org/browse/JDK-8208693)「HttpClient: Extend the request
  timeout's scope to cover the response body」——描述明确写着 *the timeout set by the current
  `HttpRequest.Builder::timeout` stops when the response headers are received*；
  修复版本 **JDK 26**（Resolved 2025-12-04），回移到 25-pool 的
  [JDK-8383521](https://bugs.openjdk.org/browse/JDK-8383521) 仍是 Open/Unresolved。

因此在本仓库的基线 JDK 25 上：`HttpRequest.timeout` **在读到响应头之后就失效，不覆盖响应体读取**。
上游只要先把响应头（或第一个 SSE 分片）刷出来，再静默不发正文，客户端就会永远阻塞在
`readLine()` / `readNBytes()` 上——这不是异常，`try/catch` 永远等不到，调用线程会被永久挂住。

实测（JDK 25.0.2、`OpenAiCompatibleModelProviderTest`、本地 `HttpServer` 桩）：

| 桩行为 | `HttpRequest.timeout` 是否生效 |
|---|---|
| 只发响应头、零字节正文 | ✅ 生效（804ms 抛 `HttpTimeoutException`；客户端根本拿不到响应头） |
| 发响应头 + 一小段正文后静默 | ❌ 不生效（实测 800ms 的 request timeout 下读取仍阻塞 > 3s） |

即使将来升到含该修复的 JDK，本节的总超时与审计终态语义仍必须保留：上游「慢速但不断流」不会触发
读超时，而调用必须始终被推进到终态。

因此 provider 对**整次调用**（发送 + 读完响应体）加了总时长上限：

1. 调用（含 body 读取）提交到专用虚拟线程执行器 `aiProviderCallExecutor`；
2. 调用线程用 `Future.get(totalTimeout)` 等待，超时即 `cancel(true)` 并关闭响应体，
   唤醒阻塞在 `read()` 的读取线程；
3. 抛出 `ProviderFailure(Kind.TIMEOUT)` →
   `AINER.AI.PROVIDER_TIMEOUT`（HTTP 504），调用进入 FAILED 终态，不留中间态；
4. 流式读取循环另有一层截止时间检查：上游慢速滴流时由读取循环自己在截止点失败。

`total-timeout` 管非流式，`stream-total-timeout` 管流式（默认更宽，SSE 可能长时间持续输出）。
`AiGatewayController` 的 `SseEmitter` 存活时间取 `stream-total-timeout + 5s`，
保证「provider 超时 → 写 FAILED 审计 → 推 error 事件」这条终态路径先于通道超时发生。

超时失败的调用**释放当日预算预占**（`actual_cost = 0`）；其他供应商失败（限流/不可用/协议错误）
仍按既有口径以预估成本占用预算，见 §4。

## 3. HTTP API

### 非流式

```http
POST /api/ai/chat/completions
Authorization: Bearer <access-token-with-ai.invoke-scope>
Content-Type: application/json

{
  "model": "your/model",
  "messages": [
    {"role": "SYSTEM", "content": "回答要简洁"},
    {"role": "USER", "content": "介绍 Ainer"}
  ],
  "maxOutputTokens": 512,
  "temperature": 0.2
}
```

`model`、`maxOutputTokens` 和 `temperature` 可省略，默认最大输出为 1024、温度为 0.7。角色支持 `SYSTEM`、`USER`、`ASSISTANT`。

成功响应的 `data` 包含：

```json
{
  "invocationId": "uuid",
  "providerRequestId": "provider-id",
  "model": "resolved/model",
  "content": "...",
  "finishReason": "stop",
  "usage": {
    "inputTokens": 10,
    "outputTokens": 8,
    "totalTokens": 18,
    "estimated": false
  },
  "cost": {"amount": 0.00002600, "currency": "USD"},
  "latencyMillis": 19
}
```

### SSE

```http
POST /api/ai/chat/completions/stream
Accept: text/event-stream
```

请求体与非流式相同。正常事件顺序为多个 `delta`、一个 `usage`、一个 `done`。业务失败通过 `error` 事件发送稳定错误码；HTTP 已经开始写流后，调用方不能只依赖初始 HTTP status 判断最终结果。

### 审计读取

```http
GET /api/ai/invocations/{invocationId}
Authorization: Bearer <access-token-with-ai.invoke-scope>
```

查询按已验证 typed principal 的 `sub` 隔离。不存在或属于其他 subject 都返回 404，不返回 prompt fingerprint 与正文。客户端自报的 subject 请求头会被忽略。

## 4. 策略顺序

调用按以下顺序执行：

```text
模型白名单
  -> 提示字符上限
  -> 敏感凭据模式
   -> subject 分钟限流（RateLimitPort：Redis 固定窗口 / 进程内降级）
   -> PostgreSQL subject 日预算预占
  -> Provider
  -> 实际 Token/费用回写
```

预算在调用前用输入估算与 `maxOutputTokens` 预占；成功后替换为供应商实际 usage 计算的费用。`STARTED`、`SUCCEEDED` 与 `FAILED` 的费用暴露都会纳入预算，避免并发或失败请求绕过上限。预算日界线使用 UTC。

**唯一例外（刻意设计，2026-09-11）**：超时/中断失败与定时自愈把 `actual_cost` 置 0，释放预占。
理由是这类调用永远不会再回到终态、也没有可用的 usage——若继续按预估值占用，一次上游静默就会把
该 subject 的当日预算锁死到 UTC 跨日（可用性故障）。`estimated_cost` 仍保留在审计行里，
所以「当初预占了多少」不会丢。普通供应商失败**不释放**，仍按预估值占用。

## 4.1 中间态自愈（`@Scheduled`）

进程被 kill、线程卡死、客户端断开导致终态回写丢失时，记录会永久停在中间态，
而当日预算按 `STARTED` 统计 → 该 subject 的预算被永久占用。因此 `AiIntermediateStateSweeper`
按 `ainer.ai.self-heal.scan-interval-ms` 周期清扫三类中间态：

| 表 | 中间态 | 年龄基准 | 终态 |
|---|---|---|---|
| `ainer_ai_invocation` | `STARTED` | `started_at` | `FAILED` + `AINER.AI.INVOCATION_SELF_HEALED` + `actual_cost = 0` |
| `ainer_ai_task_run` | `RUNNING` | `started_at` | `FAILED` + `completed_at` |
| `ainer_ai_task` | `RUNNING` | `updated_at`（任务表没有 `started_at`） | `FAILED` + `updated_at` |

- **阈值**：超过 `stuck-threshold`（默认 15m）才动；配置校验强制它比
  `stream-total-timeout` 至少大 1 分钟，因此清扫不会把仍在正常进行的调用判死。
- **幂等**：条件 `UPDATE ... WHERE status = '中间态'`，重复执行同一批数据是无操作。
- **并发安全**：候选行用 `FOR UPDATE SKIP LOCKED` 领取，多实例同时扫，同一行只会被处理一次。
- **指标与告警**：见 [`operations.md` §8](operations.md)。
- 状态回写一律带期望态（CAS）：`updateTaskStatus` / `updateTaskRunStatus` 都要求期望态，
  自愈写入终态后，迟到的成功/失败回写是空操作并打 WARN，不会把终态覆盖回中间态。

## 4.2 审计回写失败不得掩盖原始失败

`AiInvocationAuditService.fail/failReleasingReservation` 在没有 STARTED 行时会抛
`IllegalStateException`（例如自愈已把该行推进终态、或审计行被清理）。网关**不允许**让这个二次失败
替换掉调用方真正需要看到的结论：审计失败挂到被抛出异常的 suppressed 上，同时打 WARN 日志，
响应仍是原始的 `AINER.AI.PROVIDER_TIMEOUT`（而不是 500），SSE 客户端也照常收到 `error` 事件。

## 5. 错误语义

| 错误码 | HTTP | 含义 |
|---|---:|---|
| `AINER.AI.INVALID_REQUEST` / `INVALID_CONTEXT` | 400 | 请求或调用上下文不合法 |
| `AINER.AI.INVALID_ACTING_CONTEXT` | 422 | 代行调用缺少 `actingAgentId` 或 `workspaceId` |
| `AINER.AI.PROMPT_TOO_LARGE` | 413 | 提示字符总量超限 |
| `AINER.AI.MODEL_NOT_ALLOWED` | 422 | 模型不在白名单 |
| `AINER.AI.SENSITIVE_DATA_REJECTED` | 422 | 命中禁止出网的敏感模式 |
| `AINER.AI.RATE_LIMITED` | 429 | 本 subject 分钟配额超限（含限流后端不可用时的失败关闭，语义见 [operations.md](operations.md) §9.4） |
| `AINER.AI.BUDGET_EXCEEDED` | 429 | PostgreSQL 权威日预算不足 |
| `AINER.AI.PROVIDER_PROTOCOL_ERROR` | 502 | 供应商响应不符合协议 |
| `AINER.AI.PROVIDER_RATE_LIMITED` / `PROVIDER_UNAVAILABLE` | 503 | 供应商限流或不可用 |
| `AINER.AI.PROVIDER_TIMEOUT` | 504 | 供应商调用超时（含覆盖响应体读取的总超时） |
| `AINER.AI.INVOCATION_SELF_HEALED` | — | 定时自愈写入的失败原因，只出现在审计行 `error_code` 上，不作为 HTTP 响应返回 |

错误响应不包含 API key、prompt、供应商原始响应正文或堆栈。

## 6. 生产安全要求

- AI 身份只允许来自 Resource Server 验证后的 `USER_NEUTRAL_V1` typed `sub` 和 `ai.invoke` scope；不要在代理层重新发明身份请求头协议。
- API key 使用 Vault/KMS/平台 secret，不写入 Git、镜像、日志或普通配置中心明文。
- 默认敏感模式只拦截少量高风险 key/私钥格式，不能替代数据分类、DLP、prompt injection 防护和输出审查。
- 多实例部署下主体分钟限流是否集群精确取决于 `ainer.cache.type`：`redis` 时两实例共享同一计数（总阈值不放大），默认 `local` 时每实例独立、总阈值放大 N 倍（启动期 WARN + `AinerCacheCapabilities.rateLimitClusterAccurate=false`），此时不能把它当成全局限额；预算因共享 PostgreSQL 和 subject advisory lock 始终是数据库范围内的权威控制。
- 限流后端 Redis 不可用时按失败关闭拒绝（不静默放行），入口表现为 429；取舍见 [operations.md](operations.md) §9.4。
- 监控 provider 超时、协议错误、estimated usage 比例、预算拒绝率与长时间 `STARTED` 记录。
  自愈指标（`ainer.ai.intermediate_state.*`）已内置，dashboard 与告警条件见 [`operations.md` §8](operations.md)。
- 不在日志中增加请求/响应 body。问题定位使用 `requestId`、`invocationId` 和 provider request ID。

## 7. 验证

```bash
./mvnw -pl ainer-module-ai-runtime -am test
./mvnw clean verify
```

Provider 合约测试使用本地 JDK HTTP server；数据库集成测试使用 PostgreSQL Testcontainers。本机无 Docker 时数据库测试明确跳过，不会改用 H2。上线前还应对实际供应商沙箱执行合约验证，但不能把真实 key 或响应录入仓库。

韧性相关测试（`AiRuntimeResilienceIntegrationTest`，真实 PostgreSQL 18.3 + 本地 `HttpServer` 桩）：

| 断言 | 覆盖的交付物 |
|---|---|
| 桩「发完响应头 + 一小段正文后静默」时，非流式/流式调用都在有界时间内失败 | 有界总超时覆盖 body 读取 |
| 同一桩下 invocation 进入 `FAILED:AINER.AI.PROVIDER_TIMEOUT`，且该 subject 的当日暴露回到 0、后续调用仍为 200 | 终态 + 预算释放 |
| 反复调用（400ms 请求头超时 / 1200ms 总超时）在 1000..5000ms 区间失败 | 证明兜底的是总超时而不是请求头超时 |
| 超期 `STARTED` 被清扫为 `FAILED` 并释放预算；未超期行不受影响；重复清扫零副作用 | 自愈正确性与幂等 |
| 两个清扫实例并发处理 24 行，自愈计数恰好等于行数 | 并发只处理一次 |
| 不手动触发、只等 `@Scheduled`：超期行在 30s 内自动变终态 | 调度真的生效 |
| 在途时删掉 STARTED 行 → 响应仍是 504 `AINER.AI.PROVIDER_TIMEOUT` | 审计失败不掩盖原始错误 |
