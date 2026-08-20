package dev.ainer.cache.autoconfigure;

import dev.ainer.cache.lock.DistributedLockPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis/Valkey 缓存装配（ADR-0039）。当 {@code ainer.cache.type=redis} 且 classpath
 * 存在 Spring Data Redis 时激活。提供基于 Redis 的 {@link DistributedLockPort}：
 * {@code SET NX EX} 加锁 + Lua 脚本释放。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ainer.cache", name = "type", havingValue = "redis")
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
public class AinerRedisCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StringRedisTemplate ainerRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    @ConditionalOnMissingBean(DistributedLockPort.class)
    public DistributedLockPort redisDistributedLockPort(StringRedisTemplate redisTemplate) {
        return new RedisDistributedLockPort(redisTemplate);
    }

    /**
     * Redis SET NX EX 加锁 + Lua 脚本校验 token 后释放。防止调用方释放已不属于自己的锁
     * （例如 TTL 过期后锁已被其他调用方重新获取）。
     */
    static final class RedisDistributedLockPort implements DistributedLockPort {

        private static final String RELEASE_LUA = """
                if redis.call("get", KEYS[1]) == ARGV[1] then
                    return redis.call("del", KEYS[1])
                else
                    return 0
                end
                """;

        private final StringRedisTemplate redis;

        RedisDistributedLockPort(StringRedisTemplate redis) {
            this.redis = redis;
        }

        @Override
        public Optional<LockHandle> tryLock(String key, Duration ttl) {
            String token = dev.ainer.core.uuid.Uuidv7.generate().toString();
            Boolean acquired = redis.opsForValue()
                    .setIfAbsent(key, token, ttl);
            return Boolean.TRUE.equals(acquired)
                    ? Optional.of(new LockHandle(key, token))
                    : Optional.empty();
        }

        @Override
        public void release(LockHandle handle) {
            // 通过 Lua 脚本原子地校验 token 后释放：GET+DEL 合并为一次 Redis 操作，
            // 避免其他调用方在我们的 GET 与 DEL 之间抢到锁的竞态。
            redis.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> {
                byte[] script = """
                        if redis.call("get", KEYS[1]) == ARGV[1] then
                            return redis.call("del", KEYS[1])
                        else
                            return 0
                        end
                        """.getBytes();
                byte[] keyBytes = handle.key().getBytes();
                byte[] tokenBytes = handle.token().getBytes();
                return connection.scriptingCommands().eval(
                        script,
                        org.springframework.data.redis.connection.ReturnType.INTEGER,
                        1,
                        keyBytes,
                        tokenBytes);
            });
        }
    }
}
