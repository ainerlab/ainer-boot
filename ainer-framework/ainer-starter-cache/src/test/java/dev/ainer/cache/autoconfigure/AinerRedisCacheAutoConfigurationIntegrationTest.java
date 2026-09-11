package dev.ainer.cache.autoconfigure;

import dev.ainer.cache.lock.DistributedLockPort;
import dev.ainer.cache.lock.RedisDistributedLockPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Redis 后端集成测试（Testcontainers {@code redis:7-alpine}，ADR-0039 §2 明确 Redis 7.x 兼容）。
 *
 * <p>两件必须用真实 Redis 证明的事：
 * <ol>
 *   <li>{@link RedisCacheManager} 的 JSON 序列化能<strong>往返被缓存的真实类型形状</strong>——
 *       {@code List<record>} 与 {@code Optional<record>}。被缓存的生产类型是
 *       {@code ConfigEntry}（{@code ConfigApplicationService.getEntry} 返回 {@code Optional<ConfigEntry>}）、
 *       {@code DictionaryType}/{@code DictionaryItem}，全部是 records；缓存 starter 不能反向依赖
 *       业务模块，因此这里用同形状的 record 探针（UUID / 字符串 / 布尔 / 枚举 / Instant / 密文字段）。</li>
 *   <li>Redis 锁在两个独立实例之间互斥，且 TTL 真实生效（{@code SET NX EX}）。</li>
 * </ol>
 */
@Testcontainers(disabledWithoutDocker = true)
class AinerRedisCacheAutoConfigurationIntegrationTest {

    private static final String CACHE_KEY_PREFIX = "ainer:test:";

    private static final String TTL_LOCK_KEY = "integration:ttl-lock";

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static final AutoConfigurations AUTO_CONFIGURATIONS = AutoConfigurations.of(
            AinerCacheAutoConfiguration.class,
            AinerLocalCacheAutoConfiguration.class,
            AinerRedisCacheAutoConfiguration.class,
            AinerRedisCacheUnavailableAutoConfiguration.class,
            AinerCacheLockAutoConfiguration.class,
            AinerCacheCapabilitiesAutoConfiguration.class);

    private static RedisConnectionFactory newConnectionFactory() {
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        return factory;
    }

    private static StringRedisTemplate newTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    // ---- 缓存序列化往返 ----

    @Test
    void redisCacheManagerRoundTripsListAndOptionalOfRecords() {
        runWithRedis(context -> {
            CacheManager manager = context.getBean(CacheManager.class);
            assertThat(manager).isInstanceOf(RedisCacheManager.class);
            Cache cache = manager.getCache("probe:records");
            StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);

            CachedConfigEntry entry = new CachedConfigEntry(
                    UUID.randomUUID(), "app", "db.password", null, "STRING", true,
                    "AQIDBA==（AES-GCM 密文）", "DB password", 3L, Instant.parse("2026-09-11T00:00:00Z"));
            Optional<CachedConfigEntry> optionalEntry = Optional.of(entry);

            CachedDictionaryItem item = new CachedDictionaryItem(
                    UUID.randomUUID(), UUID.randomUUID(), "RED", "红", DictionaryStatus.ACTIVE, 1);
            // 覆盖 JDK 不可变集合（List.of）与可变集合两种真实来源
            List<CachedDictionaryItem> immutableItems = List.of(item);
            List<CachedDictionaryItem> mutableItems = new ArrayList<>(List.of(item));

            cache.put("optional", optionalEntry);
            cache.put("optional-empty", Optional.empty());
            cache.put("list-immutable", immutableItems);
            cache.put("list-mutable", mutableItems);

            Object optionalRoundTrip = cache.get("optional").get();
            assertThat(optionalRoundTrip).isInstanceOf(Optional.class);
            assertThat(((Optional<?>) optionalRoundTrip).orElseThrow())
                    .isInstanceOf(CachedConfigEntry.class)
                    .isEqualTo(entry);
            // 密文字段原样往返（解密在 ConfigApplicationService#getSecret，不经缓存）
            assertThat(((Optional<?>) optionalRoundTrip).map(CachedConfigEntry.class::cast)
                    .orElseThrow().encryptedValue()).isEqualTo("AQIDBA==（AES-GCM 密文）");
            // 空 Optional 也能往返（生产上 unless 条件已跳过空结果，这里守住载体语义）
            Object emptyRoundTrip = cache.get("optional-empty").get();
            assertThat(emptyRoundTrip).isInstanceOf(Optional.class);
            assertThat((Optional<?>) emptyRoundTrip).isEmpty();

            Object immutableRoundTrip = cache.get("list-immutable").get();
            assertThat(roundTrippedItems(immutableRoundTrip)).containsExactly(item);

            Object mutableRoundTrip = cache.get("list-mutable").get();
            assertThat(roundTrippedItems(mutableRoundTrip)).containsExactly(item);

            // key 前缀与 TTL 真实落到 Redis
            Set<String> keys = redis.keys(CACHE_KEY_PREFIX + "probe:records::*");
            assertThat(keys).contains(
                    CACHE_KEY_PREFIX + "probe:records::optional",
                    CACHE_KEY_PREFIX + "probe:records::optional-empty",
                    CACHE_KEY_PREFIX + "probe:records::list-immutable",
                    CACHE_KEY_PREFIX + "probe:records::list-mutable");
            Long ttlSeconds = redis.getExpire(CACHE_KEY_PREFIX + "probe:records::optional");
            assertThat(ttlSeconds).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(30).toSeconds());
            // 值确实是 JSON 且带类型标记（否则 RedisCache 读回来的是 LinkedHashMap）
            String rawListJson = redis.opsForValue().get(CACHE_KEY_PREFIX + "probe:records::list-mutable");
            assertThat(rawListJson).contains("@class").contains("CachedDictionaryItem");
            // Optional 经 CachedOptional 载体承载（Jackson 类型标记无法描述 Optional 本身）
            String rawOptionalJson = redis.opsForValue().get(CACHE_KEY_PREFIX + "probe:records::optional");
            assertThat(rawOptionalJson).contains("CachedOptional").contains("CachedConfigEntry");
        });
    }

    @Test
    void redisCacheTimeToLiveComesFromProperties() {
        runWithRedis(context -> {
            StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
            Cache cache = context.getBean(CacheManager.class).getCache("probe:ttl");
            cache.put("k", new CachedDictionaryItem(
                    UUID.randomUUID(), UUID.randomUUID(), "BLUE", "蓝", DictionaryStatus.INACTIVE, 2));
            Long ttlSeconds = redis.getExpire(CACHE_KEY_PREFIX + "probe:ttl::k");
            assertThat(ttlSeconds).isPositive().isLessThanOrEqualTo(90L);
        }, "ainer.cache.redis.time-to-live=90s");
    }

    // ---- Redis 锁 ----

    @Test
    void redisLockIsMutuallyExclusiveAcrossInstances() {
        runWithRedis(context -> {
            // 生产装配出来的锁 = 实例 A；再手工构造一个同参数端口 = 实例 B（模拟第二个 JVM）
            DistributedLockPort instanceA = context.getBean(DistributedLockPort.class);
            assertThat(instanceA).isInstanceOf(RedisDistributedLockPort.class);
            DistributedLockPort instanceB =
                    new RedisDistributedLockPort(context.getBean(StringRedisTemplate.class));

            DistributedLockPort.LockHandle heldByA =
                    instanceA.tryLock("integration:lock", Duration.ofSeconds(30)).orElseThrow();
            assertThat(instanceB.tryLock("integration:lock", Duration.ofSeconds(30))).isEmpty();
            // 不同 key 不受影响
            assertThat(instanceB.tryLock("integration:lock-other", Duration.ofSeconds(30))).isPresent();

            // 用不匹配的 token 释放是 no-op（不能释放别人的锁）
            instanceB.release(new DistributedLockPort.LockHandle("integration:lock", "not-the-token"));
            assertThat(instanceB.tryLock("integration:lock", Duration.ofSeconds(30))).isEmpty();

            instanceA.release(heldByA);
            assertThat(instanceB.tryLock("integration:lock", Duration.ofSeconds(30))).isPresent();
        });
    }

    @Test
    void redisLockHonoursTimeToLive() {
        RedisConnectionFactory factory = newConnectionFactory();
        try {
            StringRedisTemplate redis = newTemplate(factory);
            RedisDistributedLockPort first = new RedisDistributedLockPort(redis);
            RedisDistributedLockPort second = new RedisDistributedLockPort(redis);

            assertThat(first.tryLock("integration:ttl-lock", Duration.ofSeconds(1))).isPresent();
            assertThat(second.tryLock("integration:ttl-lock", Duration.ofSeconds(1))).isEmpty();

            // 只等待只读状态（锁键消失 = TTL 到期），断言在等待之后一次性执行；
            // 重试只覆盖「状态未就绪」，不覆盖断言失败。
            await().atMost(Duration.ofSeconds(10))
                    .until(() -> !Boolean.TRUE.equals(redis.hasKey(TTL_LOCK_KEY)));
            assertThat(second.tryLock(TTL_LOCK_KEY, Duration.ofSeconds(5))).isPresent();
        } finally {
            ((LettuceConnectionFactory) factory).destroy();
        }
    }

    // ---- 夹具 ----

    /** 断言缓存读回来的是 {@code List<CachedDictionaryItem>} 而不是 LinkedHashMap 列表。 */
    @SuppressWarnings("unchecked")
    private static List<CachedDictionaryItem> roundTrippedItems(Object value) {
        assertThat(value).isInstanceOf(List.class);
        assertThat(((List<Object>) value).get(0)).isInstanceOf(CachedDictionaryItem.class);
        return (List<CachedDictionaryItem>) value;
    }

    private static void runWithRedis(
            Consumer<AssertableApplicationContext> handler, String... extraProperties) {
        String[] properties = new String[extraProperties.length + 2];
        properties[0] = "ainer.cache.type=redis";
        properties[1] = "ainer.cache.redis.key-prefix=" + CACHE_KEY_PREFIX;
        System.arraycopy(extraProperties, 0, properties, 2, extraProperties.length);
        new ApplicationContextRunner()
                .withConfiguration(AUTO_CONFIGURATIONS)
                .withPropertyValues(properties)
                .withBean(RedisConnectionFactory.class,
                        AinerRedisCacheAutoConfigurationIntegrationTest::newConnectionFactory)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AinerCacheCapabilities capabilities = context.getBean(AinerCacheCapabilities.class);
                    assertThat(capabilities.cacheManagerClass()).contains("RedisCacheManager");
                    assertThat(capabilities.multiInstanceSafe()).isTrue();
                    handler.accept(context);
                });
    }

    /** 镜像 {@code ConfigEntry} 的形状（record + UUID + 布尔 + 密文字段 + 版本 + Instant）。 */
    public record CachedConfigEntry(
            UUID id,
            String namespace,
            String key,
            String value,
            String valueType,
            boolean secret,
            String encryptedValue,
            String description,
            long version,
            Instant updatedAt) {
    }

    /** 镜像 {@code DictionaryItem} 的形状（record + UUID + 枚举 + 排序）。 */
    public record CachedDictionaryItem(
            UUID id,
            UUID typeId,
            String code,
            String label,
            DictionaryStatus status,
            int sortIndex) {
    }

    /** 镜像 {@code DictionaryStatus} 枚举。 */
    public enum DictionaryStatus {
        ACTIVE,
        INACTIVE
    }
}
