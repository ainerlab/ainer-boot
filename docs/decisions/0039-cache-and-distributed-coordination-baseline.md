# ADR-0039：缓存与分布式协调基础设施基线

- 状态：Accepted
- 日期：2026-08-12
- 决策者：Ainer 项目维护者
- 取代：无（局部修订 ADR-0016 登录限流、ADR-0011 在线校验、ADR-0030 授权缓存中关于「不引入 Redis」的阶段性结论）
- 被取代：无

## 背景

P3 阶段之前（P0–P2 foundation），多个 ADR 明确拒绝引入 Redis：

- ADR-0016（登录限流）：「ainer-boot 当前无 Redis，且本阶段不新增中间件依赖」→ node-local 固定窗口限流。
- ADR-0011（在线 Token 校验）：「需要新的共享状态……会把发行物绑定到 Redis」→ 拒绝 Redis deny-list。
- ADR-0030（授权）：「首版不缓存 ALLOW」→ 每次决策读 DB。

这些决策在 foundation 阶段正确——不在没有消费者时引入中间件。但进入 P3「商业级企业基座」后，
以下问题不再是「可以推迟」：

1. **多实例缓存一致性**：字典（`ConcurrentHashMap`）和配置（`ConcurrentHashMap`）在单实例下正常，
   但多实例部署时一个实例写入，其他实例缓存不失效。
2. **分布式锁缺失**：ADR-0038 的 `IdempotencyPort` 依赖分布式锁；PG advisory lock 可用但不适合
   跨资源锁（如限流、并发上传）。
3. **限流仍为 node-local**：ADR-0016 的固定窗口限流在多实例下总阈值翻倍（每实例独立计数）。
4. **通知队列轮询延迟**：PG SKIP LOCKED 队列靠 5s 轮询，高并发下延迟和 PG 负载成为瓶颈。

## 决策驱动因素

- 商业级脚手架必须支持多实例部署，缓存一致性、分布式锁和跨实例限流是基座能力；
- Spring Cache 抽象允许缓存实现可替换（Caffeine 本地 → Redis 分布式），产品按部署拓扑选择；
- 技术选型优先兼容 Redis 协议但许可证更友好的实现（Valkey，BSD）。

## 决策

### 1. 引入 Valkey/Redis 作为可选缓存与分布式协调基础设施

新增 `ainer-starter-cache`，提供三层能力：

| 能力 | SPI 端口 | 默认实现 | 无 Redis 时降级 |
|---|---|---|---|
| **缓存** | Spring Cache（`@Cacheable`/`@CacheEvict`） | Redis（Valkey） | Caffeine 本地缓存 |
| **分布式锁** | `DistributedLockPort`（tryLock/release） | Redis SET NX EX + Lua 释放 | PG advisory lock |
| **分布式限流** | `RateLimitPort`（tryAcquire） | Redis 固定窗口/令牌桶 | node-local（ADR-0016 现状） |

**装配策略**：
- `@ConditionalOnProperty(prefix = "ainer.cache", name = "type", havingValue = "redis", matchIfMissing = false)`
  — 默认不启用 Redis，产品显式配置 `ainer.cache.type=redis`。
- `@ConditionalOnProperty(prefix = "ainer.cache", name = "type", havingValue = "local")`
  — 默认 Caffeine 本地缓存（单体/开发环境）。
- 不强制任何应用依赖 Redis——`ainer-starter-cache` 的 Redis 依赖是 optional。

### 2. 技术选型：Valkey 优先，Redis 兼容

- 客户端：Spring Data Redis + Lettuce（Spring Boot 默认，netty-based，虚拟线程友好）。
- 服务端：推荐 Valkey 8.x（Linux Foundation，BSD 许可证）；Redis 7.x 也兼容（RSAL/SSPL 许可证
  不影响客户端使用，但发行时需注意）。
- 连接池：Lettuce 原生（非 commons-pool2），利用 netty EventLoop。

### 3. 缓存改造：从 ConcurrentHashMap 到 Spring Cache

现有手写缓存迁移到 Spring Cache 注解：

| 模块 | 现状 | 改造后 |
|---|---|---|
| 字典 | `ConcurrentHashMap` + 手写 clear | `@Cacheable("dict:items:{typeCode}")` + `@CacheEvict` |
| 配置 | `ConcurrentHashMap` + 手写 clear | `@Cacheable("config:{namespace}:{key}")` + `@CacheEvict` |

改造后缓存实现可替换：开发用 Caffeine（零外部依赖），生产用 Redis（跨实例一致）。

### 4. 分布式锁 SPI

```java
public interface DistributedLockPort {
    Optional<LockHandle> tryLock(String key, Duration ttl);
    void release(LockHandle handle);
    record LockHandle(String key, String token) {}
}
```

Redis 实现：`SET key token NX EX ttl` + Lua 脚本安全释放（token 校验防误删）。
PG 实现（降级）：`pg_try_advisory_lock(hash(key))`。

### 5. 局部修订既有 ADR

以下 ADR 中「不引入 Redis」的阶段性结论修订为「foundation 阶段不引入，P3 起作为可选基础设施引入」：

- ADR-0016：限流从「永久 node-local」修订为「默认 node-local，Redis 可用时升级为分布式」。
- ADR-0011：在线校验从「不引入 Redis deny-list」修订为「默认不引入，Redis 可用时可选」。
- ADR-0030/0037：授权从「首版不缓存 ALLOW」保持不变（授权决策不缓存，缓存仅用于字典/配置等
  低频变更数据），但 BindingResolver 的 live bindings 可选缓存（另立 ADR）。

## 非目标

- 不在授权决策路径引入缓存（ADR-0030 §12 的安全约束仍有效）；
- 不引入 Kafka/RocketMQ/RabbitMQ（ADR-0024 的服务拆分触发条件未满足）；
- 不强制所有应用依赖 Redis（optional + ConditionalOnProperty）；
- 不在本轮实现分布式限流的完整令牌桶（首版固定窗口，令牌桶后续）。

## 后果

### 正面

- 多实例部署的缓存一致性、分布式锁、跨实例限流从「不支持」变为「可选启用」；
- 字典/配置从手写缓存迁移到标准 Spring Cache，实现可替换；
- 为幂等（`IdempotencyPort`）、通知实时推送（Redis pub/sub 替代 5s 轮询）奠定基础。

### 负面与风险

- 新增 `ainer-starter-cache` 模块 + Redis 依赖（optional），BOM 需管理版本；
- 缓存一致性从「单实例 clear」变为「跨实例 pub/sub 失效」，需验证 Redis 可用性降级；
- Valkey/Redis 的运维成本（部署、监控、持久化）由产品承担。

## 运维与迁移

1. `ainer-starter-cache` 作为 framework 层新制品，纳入 BOM 和 reactor；
2. 默认 `ainer.cache.type=local`（Caffeine），不改变现有行为；
3. 产品配置 `ainer.cache.type=redis` + Redis 连接信息后自动切换；
4. 字典/配置改造为 Spring Cache 注解，与缓存类型无关（声明式）。

## 参考

- [ADR-0016 登录限流](0016-login-rate-limit-and-controlled-enrollment.md)
- [ADR-0011 选择性在线 Token 校验](0011-selective-online-token-validation.md)
- [ADR-0024 演进式模块化平台架构](0024-evolutionary-modular-platform-architecture.md)
- [ADR-0025 公共制品与仓库边界](0025-public-artifacts-utilities-and-repository-boundary.md)
- [ADR-0038 P4 范围精简与企业基建前置](0038-p4-scope-refinement-and-enterprise-base.md)
- [Valkey](https://valkey.io/)
- [Spring Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
