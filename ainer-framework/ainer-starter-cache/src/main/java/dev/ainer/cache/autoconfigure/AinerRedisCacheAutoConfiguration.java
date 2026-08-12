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
 * Redis/Valkey cache configuration (ADR-0039). Active when {@code ainer.cache.type=redis} and
 * Spring Data Redis is on the classpath. Provides a Redis-backed {@link DistributedLockPort} using
 * {@code SET NX EX} + Lua-script release.
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
     * Redis SET NX EX + token-checked release via Lua script. Prevents a caller from releasing
     * a lock it no longer owns (e.g. after TTL expiry and re-acquisition by another caller).
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
            // Atomic token-checked release via Lua script: GET+DEL in a single Redis operation
            // prevents race where another caller acquires the lock between our GET and DEL.
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
