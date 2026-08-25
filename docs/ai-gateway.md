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

M3 已接入 Resource Server 和可信认证上下文；当前仍不包含完整 DLP、输出 guardrail、Agent/Tool/RAG、集群级限流或自动价格同步。

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
| `AINER_AI_REQUEST_TIMEOUT` | 单次请求超时 | `60s` |
| `AINER_AI_REQUESTS_PER_MINUTE` | 每 subject、每节点分钟限流 | `60` |
| `AINER_AI_SUBJECT_DAILY_BUDGET` | 每 subject UTC 日预算 | `10.00` |
| `AINER_AI_MAX_PROMPT_CHARACTERS` | 所有消息内容字符总上限 | `100000` |
| `AINER_AI_CURRENCY` | 三位大写币种代码 | `USD` |
| `AINER_AI_INPUT_PER_MILLION_TOKENS` | 每百万输入 Token 单价 | `0` |
| `AINER_AI_OUTPUT_PER_MILLION_TOKENS` | 每百万输出 Token 单价 | `0` |

`AINER_AI_ALLOW_INSECURE_HTTP=true` 只允许本机测试。生产配置必须使用 HTTPS，URL 不允许 user-info、query 或 fragment。

价格是平台预算与审计的运维数据。不要盲目复制第三方示例价格；供应商、模型或账户价发生变化时，应由受控配置变更更新。

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
   -> 节点级 subject 分钟限流
   -> PostgreSQL subject 日预算预占
  -> Provider
  -> 实际 Token/费用回写
```

预算在调用前用输入估算与 `maxOutputTokens` 预占；成功后替换为供应商实际 usage 计算的费用。`STARTED`、`SUCCEEDED` 与 `FAILED` 的费用暴露都会纳入预算，避免并发或失败请求绕过上限。预算日界线使用 UTC。

## 5. 错误语义

| 错误码 | HTTP | 含义 |
|---|---:|---|
| `AINER.AI.INVALID_REQUEST` / `INVALID_CONTEXT` | 400 | 请求或调用上下文不合法 |
| `AINER.AI.INVALID_ACTING_CONTEXT` | 422 | 代行调用缺少 `actingAgentId` 或 `workspaceId` |
| `AINER.AI.PROMPT_TOO_LARGE` | 413 | 提示字符总量超限 |
| `AINER.AI.MODEL_NOT_ALLOWED` | 422 | 模型不在白名单 |
| `AINER.AI.SENSITIVE_DATA_REJECTED` | 422 | 命中禁止出网的敏感模式 |
| `AINER.AI.RATE_LIMITED` | 429 | 本节点账户分钟限流 |
| `AINER.AI.BUDGET_EXCEEDED` | 429 | PostgreSQL 权威日预算不足 |
| `AINER.AI.PROVIDER_PROTOCOL_ERROR` | 502 | 供应商响应不符合协议 |
| `AINER.AI.PROVIDER_RATE_LIMITED` / `PROVIDER_UNAVAILABLE` | 503 | 供应商限流或不可用 |
| `AINER.AI.PROVIDER_TIMEOUT` | 504 | 供应商调用超时 |

错误响应不包含 API key、prompt、供应商原始响应正文或堆栈。

## 6. 生产安全要求

- AI 身份只允许来自 Resource Server 验证后的 `USER_NEUTRAL_V1` typed `sub` 和 `ai.invoke` scope；不要在代理层重新发明身份请求头协议。
- API key 使用 Vault/KMS/平台 secret，不写入 Git、镜像、日志或普通配置中心明文。
- 默认敏感模式只拦截少量高风险 key/私钥格式，不能替代数据分类、DLP、prompt injection 防护和输出审查。
- 多实例部署不能把当前 node-local limiter 当成全局限额；预算因共享 PostgreSQL 和 subject advisory lock 是数据库范围内的权威控制。
- 监控 provider 超时、协议错误、estimated usage 比例、预算拒绝率与长时间 `STARTED` 记录。M2 尚未内置这些指标的完整 dashboard。
- 不在日志中增加请求/响应 body。问题定位使用 `requestId`、`invocationId` 和 provider request ID。

## 7. 验证

```bash
./mvnw -pl ainer-module-ai-runtime -am test
./mvnw clean verify
```

Provider 合约测试使用本地 JDK HTTP server；数据库集成测试使用 PostgreSQL Testcontainers。本机无 Docker 时数据库测试明确跳过，不会改用 H2。上线前还应对实际供应商沙箱执行合约验证，但不能把真实 key 或响应录入仓库。
