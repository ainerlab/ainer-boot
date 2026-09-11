package dev.ainer.cache.autoconfigure;

import com.github.benmanes.caffeine.cache.Caffeine;
import dev.ainer.cache.lock.DistributedLockPort;
import dev.ainer.cache.lock.LocalDistributedLockPort;
import dev.ainer.cache.lock.PostgresDistributedLockPort;
import dev.ainer.cache.lock.RedisDistributedLockPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0039 缓存与分布式协调装配测试（ApplicationContextRunner，仓库约定：默认装配、显式开启/关闭、
 * Redis 分支、锁策略与非法配置都要覆盖）。
 *
 * <p>核心回归点：打开缓存之前 {@code @Cacheable} 是死注解——本测试用「方法体真实执行次数」
 * 证明缓存注解确实被拦截，而不是只看 bean 是否存在。
 */
@ExtendWith(OutputCaptureExtension.class)
class AinerCacheAutoConfigurationTest {

    private static final AutoConfigurations AUTO_CONFIGURATIONS = AutoConfigurations.of(
            AinerCacheAutoConfiguration.class,
            AinerLocalCacheAutoConfiguration.class,
            AinerRedisCacheAutoConfiguration.class,
            AinerRedisCacheUnavailableAutoConfiguration.class,
            AinerCacheLockAutoConfiguration.class,
            AinerCacheCapabilitiesAutoConfiguration.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AUTO_CONFIGURATIONS)
            .withUserConfiguration(ProbeConfiguration.class);

    // ---- 默认装配 ----

    @Test
    void defaultsAssembleCaffeineCacheAndLocalLock() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CacheManager.class);
            assertThat(context.getBean(CacheManager.class)).isInstanceOf(CaffeineCacheManager.class);

            DistributedLockPort lock = context.getBean(DistributedLockPort.class);
            assertThat(lock).isInstanceOf(LocalDistributedLockPort.class);
            assertThat(lock.multiInstanceSafe()).isFalse();

            AinerCacheCapabilities capabilities = context.getBean(AinerCacheCapabilities.class);
            assertThat(capabilities.declaredCacheType()).isEqualTo(AinerCacheProperties.CacheType.LOCAL);
            assertThat(capabilities.cacheManagerClass()).contains("CaffeineCacheManager");
            assertThat(capabilities.cacheTimeToLive()).isEqualTo(Duration.ofMinutes(30));
            assertThat(capabilities.declaredLockType()).isEqualTo(AinerCacheProperties.LockType.AUTO);
            assertThat(capabilities.lockImplementationClass())
                    .isEqualTo(LocalDistributedLockPort.class.getName());
            assertThat(capabilities.multiInstanceSafe()).isFalse();
        });
    }

    @Test
    void cacheableAnnotationIsInterceptedByDefault() {
        runner.run(context -> {
            CacheProbe probe = context.getBean(CacheProbe.class);
            assertThat(probe.value("alpha")).isEqualTo("value-alpha");
            assertThat(probe.value("alpha")).isEqualTo("value-alpha");
            // 命中缓存：第二次调用不再进入方法体
            assertThat(probe.invocations()).isEqualTo(1);

            assertThat(probe.value("beta")).isEqualTo("value-beta");
            assertThat(probe.invocations()).isEqualTo(2);
        });
    }

    @Test
    void localCacheParametersComeFromProperties() {
        runner.withPropertyValues(
                        "ainer.cache.local.time-to-live=45s",
                        "ainer.cache.local.maximum-size=123")
                .run(context -> {
                    AinerCacheCapabilities capabilities = context.getBean(AinerCacheCapabilities.class);
                    assertThat(capabilities.cacheTimeToLive()).isEqualTo(Duration.ofSeconds(45));

                    CaffeineCacheManager manager = context.getBean(CaffeineCacheManager.class);
                    manager.getCache("probe");
                    com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                            (com.github.benmanes.caffeine.cache.Cache<Object, Object>)
                                    manager.getCache("probe").getNativeCache();
                    assertThat(nativeCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter())
                            .isEqualTo(Duration.ofSeconds(45));
                    assertThat(nativeCache.policy().eviction().orElseThrow().getMaximum())
                            .isEqualTo(123L);
                });
    }

    @Test
    void lockAcquireReleaseAndTtlExpiryWorkInProcess() {
        runner.run(context -> {
            DistributedLockPort lock = context.getBean(DistributedLockPort.class);
            DistributedLockPort.LockHandle first =
                    lock.tryLock("probe-key", Duration.ofSeconds(30)).orElseThrow();
            assertThat(lock.tryLock("probe-key", Duration.ofSeconds(30))).isEmpty();

            lock.release(first);
            assertThat(lock.tryLock("probe-key", Duration.ofSeconds(30))).isPresent();

            // TTL 到期后无需后台线程即可重新获取（旧实现每次获取都起一条休眠虚拟线程）
            assertThat(lock.tryLock("expiring", Duration.ofMillis(50))).isPresent();
            Thread.sleep(120);
            assertThat(lock.tryLock("expiring", Duration.ofSeconds(30))).isPresent();
        });
    }

    // ---- 显式关闭 ----

    @Test
    void cacheIsNotEnabledWhenExplicitlyDisabled() {
        runner.withPropertyValues("ainer.cache.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CacheManager.class);
            assertThat(context.getBean(AinerCacheCapabilities.class).cacheManagerClass()).isNull();

            // 关闭后注解不再被拦截：方法体每次真实执行
            CacheProbe probe = context.getBean(CacheProbe.class);
            probe.value("alpha");
            probe.value("alpha");
            assertThat(probe.invocations()).isEqualTo(2);
        });
    }

    // ---- Redis 分支 ----

    @Test
    void redisTypeAssemblesRedisCacheManagerWithoutRedisServer() {
        runner.withPropertyValues("ainer.cache.type=redis")
                .withBean(RedisConnectionFactory.class, AinerCacheAutoConfigurationTest::unconnectedRedis)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context.getBean(CacheManager.class)).isInstanceOf(RedisCacheManager.class);
                    // AUTO：Redis 缓存后端可用时锁也用 Redis（ADR-0039 §4 的默认实现）
                    assertThat(context.getBean(DistributedLockPort.class))
                            .isInstanceOf(RedisDistributedLockPort.class);

                    AinerCacheCapabilities capabilities = context.getBean(AinerCacheCapabilities.class);
                    assertThat(capabilities.cacheManagerClass()).contains("RedisCacheManager");
                    assertThat(capabilities.cacheTimeToLive()).isEqualTo(Duration.ofMinutes(30));
                    assertThat(capabilities.multiInstanceSafe()).isTrue();
                });
    }

    @Test
    void redisTypeAcceptsUppercaseEnumSpelling() {
        runner.withPropertyValues("ainer.cache.type=REDIS")
                .withBean(RedisConnectionFactory.class, AinerCacheAutoConfigurationTest::unconnectedRedis)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CacheManager.class)).isInstanceOf(RedisCacheManager.class);
                });
    }

    @Test
    void redisTypeWithoutRedisClientFailsWithActionableMessage() {
        // 不带探针 bean：FilteredClassLoader 会为代理类另建类加载器，跨加载器代理一个包私有类是
        // IllegalAccessError，与本次要验证的失败原因无关。
        new ApplicationContextRunner()
                .withConfiguration(AUTO_CONFIGURATIONS)
                .withClassLoader(new FilteredClassLoader("org.springframework.data.redis"))
                .withPropertyValues("ainer.cache.type=redis")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(failureChain(context)).contains("spring-boot-starter-data-redis");
                });
    }

    @Test
    void redisTypeWithoutConnectionFactoryFailsWithActionableMessage() {
        runner.withPropertyValues("ainer.cache.type=redis").run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureChain(context)).contains("RedisConnectionFactory");
        });
    }

    // ---- 锁策略 ----

    @Test
    void postgresLockIsSelectedWhenExplicitlyRequested() {
        runner.withPropertyValues("ainer.cache.lock.type=POSTGRES")
                .withBean(DataSource.class, AinerCacheAutoConfigurationTest::unreachableDataSource)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(DistributedLockPort.class))
                            .isInstanceOf(PostgresDistributedLockPort.class);
                    assertThat(context.getBean(AinerCacheCapabilities.class).multiInstanceSafe()).isTrue();
                });
    }

    @Test
    void autoLockPrefersPostgresWhenDataSourcePresent() {
        runner.withBean(DataSource.class, AinerCacheAutoConfigurationTest::unreachableDataSource)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AinerCacheCapabilities.class).declaredLockType())
                            .isEqualTo(AinerCacheProperties.LockType.AUTO);
                    assertThat(context.getBean(DistributedLockPort.class))
                            .isInstanceOf(PostgresDistributedLockPort.class);
                    assertThat(context.getBean(AinerCacheCapabilities.class).multiInstanceSafe()).isTrue();
                });
    }

    @Test
    void postgresLockWithoutDataSourceFailsWithActionableMessage() {
        runner.withPropertyValues("ainer.cache.lock.type=POSTGRES").run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureChain(context))
                    .contains("ainer.cache.lock.type=POSTGRES")
                    .contains("DataSource");
        });
    }

    @Test
    void localLockIsSelectedWhenExplicitlyRequestedEvenWithDataSource() {
        runner.withPropertyValues("ainer.cache.lock.type=LOCAL")
                .withBean(DataSource.class, AinerCacheAutoConfigurationTest::unreachableDataSource)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    DistributedLockPort lock = context.getBean(DistributedLockPort.class);
                    assertThat(lock).isInstanceOf(LocalDistributedLockPort.class);
                    assertThat(context.getBean(AinerCacheCapabilities.class).multiInstanceSafe()).isFalse();
                });
    }

    @Test
    void degradedLockLogsExplicitWarning(CapturedOutput output) {
        runner.run(context -> assertThat(context).hasNotFailed());
        assertThat(output.getAll())
                .contains("分布式锁退化为进程内实现")
                .contains("多实例部署下锁互斥不成立");
    }

    // ---- 非法配置 ----

    @Test
    void invalidLockTypeFailsFast() {
        runner.withPropertyValues("ainer.cache.lock.type=REDIS_SENTINEL").run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureChain(context)).contains("ainer.cache.lock.type");
        });
    }

    @Test
    void invalidCacheTypeFailsFast() {
        runner.withPropertyValues("ainer.cache.type=memcached").run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureChain(context)).contains("ainer.cache.type");
        });
    }

    @Test
    void invalidLocalParametersFailFast() {
        runner.withPropertyValues("ainer.cache.local.maximum-size=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureChain(context)).contains("maximum-size");
        });
    }

    // ---- 测试夹具 ----

    private static String failureChain(AssertableApplicationContext context) {
        StringBuilder chain = new StringBuilder();
        for (Throwable failure = context.getStartupFailure(); failure != null; failure = failure.getCause()) {
            chain.append(failure).append('\n');
        }
        return chain.toString();
    }

    /** 只为装配存在性：不建立真实连接，RedisCacheManager 构造不需要 Redis 服务在线。 */
    private static RedisConnectionFactory unconnectedRedis() {
        return new LettuceConnectionFactory("127.0.0.1", 6379);
    }

    /** 只为装配存在性：PostgresDistributedLockPort 构造不建立连接。 */
    private static DataSource unreachableDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl("jdbc:postgresql://127.0.0.1:1/ainer");
        dataSource.setUser("ainer");
        return dataSource;
    }

    @Configuration(proxyBeanMethods = false)
    static class ProbeConfiguration {

        @Bean
        CacheProbe cacheProbe() {
            return new CacheProbe();
        }
    }

    /** 记录方法体真实执行次数的缓存探针（CGLIB 代理，非 final 类）。 */
    static class CacheProbe {

        private final AtomicInteger invocations = new AtomicInteger();

        @Cacheable("probe")
        public String value(String key) {
            invocations.incrementAndGet();
            return "value-" + key;
        }

        int invocations() {
            return invocations.get();
        }
    }
}
