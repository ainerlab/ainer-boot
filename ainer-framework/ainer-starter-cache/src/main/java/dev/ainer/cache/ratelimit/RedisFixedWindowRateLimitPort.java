package dev.ainer.cache.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 固定窗口限流（ADR-0039 §1 的默认实现）。多实例共享同一 Redis 计数，因此
 * <strong>集群总阈值不放大</strong>：两个实例并发打同一个 key，放行总量等于 {@code limit}。
 *
 * <h2>原子性</h2>
 * <p>「读当前值 → 判断是否超限 → 自增 → 维护 TTL」四步由单个 Lua 脚本完成，Redis 单线程执行脚本，
 * 因此不存在「两个实例同时读到 59 再各自自增」的穿透。刻意不用 {@code INCR} + {@code EXPIRE} 的
 * 两步组合：那是两个 RTT 之间可被打断的窗口（第一次 INCR 之后进程崩溃，键就永远不会过期）。
 *
 * <h2>窗口边界与 key 过期</h2>
 * <ul>
 *   <li>key 里带窗口序号（{@code <前缀><业务 key>:<窗口序号>}），窗口推进即换键，无需读改写重置；</li>
 *   <li>key 的 TTL 精确到本窗口结束（{@code GET/PEXPIRE} 在同一脚本内完成），旧窗口的键自然消失，
 *       不需要额外的清理任务；</li>
 *   <li>{@code retryAfter} 由本地时钟算出「距窗口结束」的时长（恒为正），可直接映射
 *       {@code Retry-After}。</li>
 * </ul>
 *
 * <h2>时钟与集群一致性</h2>
 * <p>窗口序号由<strong>本地时钟</strong>计算而不是 Redis 的 {@code TIME}：Redis Cluster 要求脚本只能访问
 * 显式传入的 KEYS，在脚本里动态拼键会破坏 key slot 路由，所以「用哪个键」必须在调用方决定。
 * 代价是实例间时钟偏移：偏移量小于窗口长度时只影响跨越边界的窗口（两个实例可能各算一个窗口，
 * 该边界窗口的放行量最多放大一倍），因此部署要求实例 NTP 同步。这与固定窗口本身的
 * 「边界双倍速率」性质同源，不额外放大风险。
 *
 * <h2>Redis 不可用时：失败关闭</h2>
 * <p>连接失败、命令超时或返回不可解析的结果时，本实现<strong>不抛异常、也不放行</strong>，返回
 * {@link RateLimitDecision.Outcome#BACKEND_UNAVAILABLE}。理由：
 * <ol>
 *   <li>本端口的用途是保护下游（AI 供应商配额、出站调用预算）。Redis 抖动时放行等于在最不可预测的
 *       时刻取消保护，且完全不可见；</li>
 *   <li>静默降级为进程内计数会制造「声明了集群精确、实际每实例独立」的假象——这正是 ADR-0039
 *       落地补齐要消除的缺陷形态；</li>
 *   <li>失败的形态是可观测的：判定结果区分 {@code BACKEND_UNAVAILABLE}，并按下述节流打 WARN，
 *       运维能立刻看到「限流后端在抖动」而不是靠猜。</li>
 * </ol>
 * 代价是可用性：Redis 全程不可用意味着被限流的入口（AI 调用）在故障期间一律 429。这是显式取舍，
 * 不是遗漏；需要「Redis 抖动也不拒绝」的产品应自行在调用方决定是否放行（例如按
 * {@link RateLimitDecision#outcome()} 走自己的降级分支），本端口不提供静默放行开关。
 *
 * <p>失败告警按 {@value #FAILURE_LOG_INTERVAL_MILLIS} 毫秒节流，并统计被抑制的条数：Redis 长时间
 * 不可用时不会按请求量刷爆日志，同时保留「抑制了多少条」的量级信息。
 */
public final class RedisFixedWindowRateLimitPort implements RateLimitPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisFixedWindowRateLimitPort.class);

    /** 后端失败告警的节流间隔（毫秒）。 */
    static final long FAILURE_LOG_INTERVAL_MILLIS = 30_000L;

    /**
     * 固定窗口的原子计数脚本。
     *
     * <p>KEYS[1] = 窗口键；ARGV[1] = 本次申请的配额数；ARGV[2] = 窗口内配额上限；
     * ARGV[3] = TTL（毫秒，精确到窗口结束）。返回 {@code {是否放行(0/1), 剩余配额}}。
     *
     * <p>被拒绝时不写计数（先判断后自增，判断与自增在同一脚本内，仍然原子）：拒绝不应该消耗配额，
     * 否则并发重试会把窗口提前打满。
     */
    private static final String ACQUIRE_LUA = """
            local used = tonumber(redis.call('GET', KEYS[1]) or '0')
            local permits = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])
            if used + permits > limit then
                local remaining = limit - used
                if remaining < 0 then remaining = 0 end
                return {0, remaining}
            end
            local updated = redis.call('INCRBY', KEYS[1], permits)
            local currentTtl = redis.call('PTTL', KEYS[1])
            if currentTtl < 0 or currentTtl > ttl then
                redis.call('PEXPIRE', KEYS[1], ttl)
            end
            return {1, limit - updated}
            """;

    private static final RedisScript<List> ACQUIRE_SCRIPT =
            new DefaultRedisScript<>(ACQUIRE_LUA, List.class);

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final Clock clock;
    private final AtomicLong nextFailureLogMillis = new AtomicLong();
    private final AtomicLong suppressedFailures = new AtomicLong();

    /** 使用系统 UTC 时钟。 */
    public RedisFixedWindowRateLimitPort(StringRedisTemplate redis, String keyPrefix) {
        this(redis, keyPrefix, Clock.systemUTC());
    }

    /**
     * @param redis     Redis 客户端（项目统一使用 {@code StringRedisTemplate}）
     * @param keyPrefix 基础设施 key 前缀，见 {@link RateLimitPort} 命名空间约定
     * @param clock     窗口计算时钟（测试可注入可控时钟；生产用系统 UTC 时钟）
     */
    public RedisFixedWindowRateLimitPort(StringRedisTemplate redis, String keyPrefix, Clock clock) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public RateLimitDecision tryAcquire(String key, int permits, int limit, Duration window) {
        FixedWindow.requireValidArguments(key, permits, limit, window);
        long windowMillis = window.toMillis();
        long now = this.clock.millis();
        long windowIndex = FixedWindow.index(now, windowMillis);
        long retryAfterMillis = FixedWindow.endMillis(windowIndex, windowMillis) - now;
        String storageKey = FixedWindow.storageKey(this.keyPrefix, key, windowIndex);
        Object reply;
        try {
            // ARGV 一律以字符串传入：StringRedisTemplate 的值序列化器只接受 String，
            // 直接传 Integer/Long 会在序列化阶段抛 ClassCastException（脚本里的 tonumber 负责转换）。
            reply = this.redis.execute(
                    ACQUIRE_SCRIPT,
                    List.of(storageKey),
                    Integer.toString(permits),
                    Integer.toString(limit),
                    Long.toString(retryAfterMillis));
        } catch (RuntimeException failure) {
            // 任何后端异常（连接失败、超时、序列化）都按失败关闭处理，绝不冒泡给业务、也绝不放行。
            warnBackendUnavailable(storageKey, failure);
            return RateLimitDecision.backendUnavailable();
        }
        if (!(reply instanceof List<?> values) || values.size() < 2) {
            warnUnexpectedReply(storageKey);
            return RateLimitDecision.backendUnavailable();
        }
        Long allowed = toLong(values.get(0));
        Long remaining = toLong(values.get(1));
        if (allowed == null || remaining == null) {
            warnUnexpectedReply(storageKey);
            return RateLimitDecision.backendUnavailable();
        }
        return allowed == 1L
                ? RateLimitDecision.allowed(remaining)
                : RateLimitDecision.limitExceeded(remaining, Duration.ofMillis(retryAfterMillis));
    }

    /**
     * 脚本返回值的宽松解析：Lettuce 把 Lua 的整数表解码成 {@code Long}，但若将来换了序列化器
     * （例如按字符串反序列化）会变成 {@code String}——两种形状都接受，形状不认识时按后端异常
     * 失败关闭，绝不猜成「放行」或「超限」。
     */
    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "RedisFixedWindowRateLimitPort{keyPrefix=" + this.keyPrefix + "}";
    }

    private void warnBackendUnavailable(String storageKey, RuntimeException failure) {
        long now = this.clock.millis();
        long next = this.nextFailureLogMillis.get();
        if (now < next) {
            this.suppressedFailures.incrementAndGet();
            return;
        }
        if (this.nextFailureLogMillis.compareAndSet(next, now + FAILURE_LOG_INTERVAL_MILLIS)) {
            long suppressed = this.suppressedFailures.getAndSet(0);
            LOGGER.warn("""
                    [ainer-cache] 限流后端 Redis 不可用，按失败关闭拒绝请求（key={}，此前 {} 毫秒内 {} 条同类\
                    告警被抑制）：{}。限流入口在此期间一律拒绝——这是显式取舍，不是放行。""",
                    storageKey, FAILURE_LOG_INTERVAL_MILLIS, suppressed, failure.toString());
        }
    }

    private void warnUnexpectedReply(String storageKey) {
        LOGGER.warn("[ainer-cache] 限流脚本返回了不可解析的结果，按失败关闭拒绝请求（key={}）", storageKey);
    }
}
