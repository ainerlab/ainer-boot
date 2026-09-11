package dev.ainer.cache.lock;

import dev.ainer.core.uuid.Uuidv7;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内分布式锁实现——<strong>仅对单实例部署有效</strong>。多实例部署下不同 JVM 各有
 * 自己的 Map，互斥<strong>不成立</strong>；因此装配层在选择本实现时会打 WARN（ADR-0039
 * 「无 Redis 时降级」的最后一档，必须显式暴露而不能静默成功）。
 *
 * <p>TTL 语义：持有记录带绝对到期时间，{@link #tryLock} 遇到已到期条目按「无人持有」处理并
 * 原地接管；不再像旧实现那样每次获取都起一条虚拟线程 sleep 满 TTL（旧写法在长 TTL、
 * 高获取频率下会堆积无意义的休眠线程）。未到期条目只会在同 key 再次获取或
 * {@link #release} 时被清除，占用上限为「历史上出现过的不同 key 数」，对锁场景可忽略。
 */
public final class LocalDistributedLockPort implements DistributedLockPort {

    private final ConcurrentHashMap<String, Holder> locks = new ConcurrentHashMap<>();
    private final Clock clock;

    /** 使用系统 UTC 时钟。 */
    public LocalDistributedLockPort() {
        this(Clock.systemUTC());
    }

    /**
     * @param clock 用于 TTL 判定与测试可控时钟
     */
    public LocalDistributedLockPort(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<LockHandle> tryLock(String key, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ttl, "ttl");
        Holder candidate = new Holder(
                new LockHandle(key, Uuidv7.generate().toString()),
                this.clock.millis() + Math.max(ttl.toMillis(), 0));
        while (true) {
            Holder existing = this.locks.putIfAbsent(key, candidate);
            if (existing == null) {
                return Optional.of(candidate.handle());
            }
            if (existing.deadlineMillis() > this.clock.millis()) {
                return Optional.empty();
            }
            // 已到期：原地接管；失败说明有并发者先接管，重试
            if (this.locks.replace(key, existing, candidate)) {
                return Optional.of(candidate.handle());
            }
        }
    }

    @Override
    public void release(LockHandle handle) {
        Objects.requireNonNull(handle, "handle");
        Holder holder = this.locks.get(handle.key());
        if (holder != null && holder.handle().token().equals(handle.token())) {
            this.locks.remove(handle.key(), holder);
        }
    }

    @Override
    public boolean multiInstanceSafe() {
        return false;
    }

    /** 当前未过期的持有数（诊断/测试用）。 */
    public int heldCount() {
        long now = this.clock.millis();
        return (int) this.locks.values().stream()
                .filter(holder -> holder.deadlineMillis() > now)
                .count();
    }

    private record Holder(LockHandle handle, long deadlineMillis) {
    }
}
