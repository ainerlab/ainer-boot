package dev.ainer.cache.lock;

import dev.ainer.core.uuid.Uuidv7;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Redis/Valkey 分布式锁（ADR-0039 §4）：{@code SET key token NX EX ttl} 加锁，
 * Lua 脚本校验 token 后释放，避免调用方释放已不属于自己的锁（例如 TTL 过期后锁已被
 * 其他调用方重新获取）。
 *
 * <p>锁的 key 与 token 都是应用生成的普通字符串，不承载业务数据；但 key 会以明文出现在
 * Redis 中，命名时不要把秘密写进 key。
 */
public final class RedisDistributedLockPort implements DistributedLockPort {

    /** 仅当 key 上的 token 仍是本次持有的 token 时才删除，返回 1 表示释放成功。 */
    private static final String RELEASE_LUA = """
            if redis.call("get", KEYS[1]) == ARGV[1] then
                return redis.call("del", KEYS[1])
            else
                return 0
            end
            """;

    private static final RedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(RELEASE_LUA, Long.class);

    private final StringRedisTemplate redis;

    public RedisDistributedLockPort(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    @Override
    public Optional<LockHandle> tryLock(String key, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ttl, "ttl");
        String token = Uuidv7.generate().toString();
        Boolean acquired = this.redis.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired)
                ? Optional.of(new LockHandle(key, token))
                : Optional.empty();
    }

    @Override
    public void release(LockHandle handle) {
        Objects.requireNonNull(handle, "handle");
        // 通过 Lua 脚本原子地校验 token 后释放：GET+DEL 合并为一次 Redis 操作，
        // 避免其他调用方在我们的 GET 与 DEL 之间抢到锁的竞态。
        this.redis.execute(RELEASE_SCRIPT, List.of(handle.key()), handle.token());
    }
}
