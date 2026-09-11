package dev.ainer.cache.autoconfigure;

import dev.ainer.cache.lock.DistributedLockPort;
import dev.ainer.cache.lock.RedisDistributedLockPort;
import dev.ainer.cache.ratelimit.RateLimitPort;
import dev.ainer.cache.ratelimit.RedisFixedWindowRateLimitPort;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Redis/Valkey 缓存装配（ADR-0039）。当 {@code ainer.cache.type=redis} 且 classpath 存在
 * Spring Data Redis 时激活，提供：
 * <ul>
 *   <li>{@link StringRedisTemplate}（缺失时补一个）；</li>
 *   <li>{@link RedisCacheManager}（带 TTL、key 前缀、JSON 序列化）；</li>
 *   <li>Redis 分布式锁（仅 {@code ainer.cache.lock.type=AUTO} 时；显式要求 PG/LOCAL 时让位）。</li>
 * </ul>
 *
 * <h2>缓存值序列化与「Redis 必须视为受信基础设施」的边界</h2>
 * 缓存值用 {@link GenericJacksonJsonRedisSerializer}（Jackson 3 / {@code tools.jackson}）写成
 * JSON，并开启<strong>受限</strong>多态类型（{@code @class} 类型标记），否则
 * {@code RedisCache} 在反序列化时拿不到目标类型，缓存读出的会是 {@code LinkedHashMap}
 * 而不是业务 records。类型标记的校验器只放行应用自身（{@code dev.ainer.}）与
 * {@code java.util.}/{@code java.time.}，不使用 {@code enableUnsafeDefaultTyping()}
 * （LaissezFaire 允许任意类型，Redis 一旦被写入恶意 JSON 就是反序列化攻击面）。
 *
 * <p>关于 secret：{@code ConfigEntry.encryptedValue} 里存的是 {@code ConfigEncryptionPort}
 * 加密后的<strong>密文</strong>，解密发生在 {@code ConfigApplicationService#getSecret}——
 * 即<strong>不被缓存</strong>的那个方法里。因此把 {@code Optional<ConfigEntry>} 放进 Redis
 * 不引入明文密钥泄露。但这不改变边界：Redis 中仍会存放业务配置数据（含密文），
 * 必须把 Redis 当作<strong>受信基础设施</strong>——启用认证、限制网络可达性、按环境隔离实例；
 * 需要跨信任域共享缓存时应改用不透明数据或自行更换序列化策略。
 */
@AutoConfiguration(after = AinerCacheAutoConfiguration.class)
@EnableConfigurationProperties(AinerCacheProperties.class)
@Conditional(AinerCacheConditions.OnCacheTypeRedis.class)
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnProperty(prefix = "ainer.cache", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AinerRedisCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StringRedisTemplate ainerRedisTemplate(
            ObjectProvider<RedisConnectionFactory> connectionFactories) {
        return new StringRedisTemplate(requireConnectionFactory(connectionFactories));
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public RedisCacheManager cacheManager(
            ObjectProvider<RedisConnectionFactory> connectionFactories, AinerCacheProperties properties) {
        RedisConnectionFactory connectionFactory = requireConnectionFactory(connectionFactories);
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.redis().timeToLive())
                .prefixCacheNameWith(properties.redis().keyPrefix())
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(redisCacheValueSerializer()));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .build();
    }

    /**
     * Redis 分布式锁：{@code lock.type=AUTO}（默认）时使用，兑现 ADR-0039「默认实现 Redis，
     * 无 Redis 时降级 PG advisory lock」的顺序。
     */
    @Bean
    @ConditionalOnMissingBean(DistributedLockPort.class)
    @Conditional(AinerCacheConditions.OnLockTypeAuto.class)
    public DistributedLockPort redisDistributedLockPort(StringRedisTemplate ainerRedisTemplate) {
        return new RedisDistributedLockPort(ainerRedisTemplate);
    }

    /**
     * Redis 固定窗口限流：{@code ainer.cache.type=redis} 时的 {@code RateLimitPort} 实现，
     * 兑现 ADR-0039 §1「默认实现 Redis，无 Redis 时降级 node-local」的顺序。
     *
     * <p>装配在这里而不是独立自动配置类，是因为它需要本类装配的 {@link StringRedisTemplate}，
     * 且必须与「Redis 后端可用」的判定（classpath + {@code ainer.cache.type}）保持一致；
     * 缺省/无 Redis 时由 {@link AinerRateLimitAutoConfiguration} 提供进程内实现并 WARN。
     */
    @Bean
    @ConditionalOnMissingBean(RateLimitPort.class)
    public RateLimitPort redisRateLimitPort(
            StringRedisTemplate ainerRedisTemplate, AinerCacheProperties properties) {
        return new RedisFixedWindowRateLimitPort(
                ainerRedisTemplate, properties.rateLimit().keyPrefix());
    }

    /**
     * {@code ainer.cache.type=redis} 必须有 Redis 连接：声明了 Redis 缓存却没有
     * {@link RedisConnectionFactory}，继续启动只会得到一个没有 {@code CacheManager} 的上下文和
     * 一句难以定位的报错。这里显式失败并给出可执行的修复建议。
     *
     * <p>用 {@link ObjectProvider} 而不是 {@code @ConditionalOnMissingBean} 是刻意的：本自动配置按
     * 类名字母序先于 Spring Boot 的 {@code RedisAutoConfiguration} 处理，条件求值阶段还看不到
     * Boot 即将注册的 {@code RedisConnectionFactory}——用条件会把「马上就有」误判成「缺失」。
     */
    private static RedisConnectionFactory requireConnectionFactory(
            ObjectProvider<RedisConnectionFactory> connectionFactories) {
        RedisConnectionFactory connectionFactory = connectionFactories.getIfUnique();
        if (connectionFactory == null) {
            throw new IllegalStateException("""
                    ainer.cache.type=redis 需要上下文存在 RedisConnectionFactory bean，但当前没有\
                    （或存在多个而无法判定）。请配置 spring.data.redis.*（或提供自定义 \
                    RedisConnectionFactory），或改回 ainer.cache.type=local 使用 Caffeine 本地缓存。""");
        }
        return connectionFactory;
    }

    /**
     * 缓存值序列化器：JSON + 受限多态类型标记，保证 records 往返。
     *
     * <p>读写钩子只做一件事：把<strong>根值</strong>归一化成能自描述的形状。原因是
     * {@code RedisCache} 反序列化时只知道目标类型是 {@code Object}，完全依赖 JSON 自带的
     * {@code @class} 类型标记，而两类真实缓存值无法自描述（已由
     * {@code AinerRedisCacheAutoConfigurationIntegrationTest} 实测）：
     * <ul>
     *   <li>{@code Optional<ConfigEntry>}（{@code ConfigApplicationService#getEntry} 的返回类型）：
     *       Jackson 3 把 Optional 当 reference type，类型标记会"穿透"到被包裹的值——读回来的是
     *       {@code ConfigEntry} 而不是 {@code Optional<ConfigEntry>}，调用方 {@code .filter(...)}
     *       会直接 ClassCastException；</li>
     *   <li>{@code List.of(...)} 等 JDK 不可变集合：它们是 final 类型，默认类型标记不写包装，
     *       反序列化直接抛 SerializationException。</li>
     * </ul>
     * 归一化只作用于根值；record 内部字段的类型由声明类型决定，不受影响。
     */
    static RedisSerializer<Object> redisCacheValueSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(cachePolymorphicTypeValidator())
                .writer(AinerRedisCacheAutoConfiguration::writeCacheValue)
                .reader(AinerRedisCacheAutoConfiguration::readCacheValue)
                .build();
    }

    private static byte[] writeCacheValue(ObjectMapper mapper, Object value) {
        return mapper.writeValueAsBytes(normalizeForCache(value));
    }

    private static Object readCacheValue(ObjectMapper mapper, byte[] bytes, JavaType type) {
        return restoreFromCache(mapper.readValue(bytes, 0, bytes.length, type));
    }

    /** 把根值换成可自描述的等价形状（仅类型包装，不改业务语义）。 */
    private static Object normalizeForCache(Object value) {
        if (value instanceof Optional<?> optional) {
            return new CachedOptional(optional.orElse(null));
        }
        if (value instanceof List<?> list && !(value instanceof ArrayList)) {
            return new ArrayList<>(list);
        }
        if (value instanceof Set<?> set && !(value instanceof LinkedHashSet)) {
            return new LinkedHashSet<>(set);
        }
        if (value instanceof Map<?, ?> map && !(value instanceof LinkedHashMap)) {
            return new LinkedHashMap<>(map);
        }
        return value;
    }

    private static Object restoreFromCache(Object value) {
        if (value instanceof CachedOptional cachedOptional) {
            return Optional.ofNullable(cachedOptional.value());
        }
        return value;
    }

    /** 只放行应用自身类型与常用 JDK 集合/时间类型的多态类型校验器。 */
    static PolymorphicTypeValidator cachePolymorphicTypeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("dev.ainer.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .build();
    }

    /**
     * {@code Optional} 的缓存载体。{@code value == null} 表示 {@code Optional.empty()}。
     *
     * <p>必须存在的原因见 {@link #redisCacheValueSerializer()}：Jackson 的类型标记无法描述
     * {@code Optional} 本身，只能用一个普通 record 承载它。
     */
    public record CachedOptional(@Nullable Object value) {
    }
}
