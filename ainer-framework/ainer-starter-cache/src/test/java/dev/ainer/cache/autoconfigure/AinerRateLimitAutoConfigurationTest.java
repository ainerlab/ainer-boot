package dev.ainer.cache.autoconfigure;

import dev.ainer.cache.lock.DistributedLockPort;
import dev.ainer.cache.lock.LocalDistributedLockPort;
import dev.ainer.cache.lock.RedisDistributedLockPort;
import dev.ainer.cache.ratelimit.NodeLocalRateLimitPort;
import dev.ainer.cache.ratelimit.RateLimitDecision;
import dev.ainer.cache.ratelimit.RateLimitPort;
import dev.ainer.cache.ratelimit.RedisFixedWindowRateLimitPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0039 §1 第三层能力（分布式限流）的装配测试。要证明的三件事：
 * <ol>
 *   <li>实现跟随 {@code ainer.cache.type}：{@code REDIS} → Redis 固定窗口；缺省/{@code LOCAL} →
 *       进程内固定窗口；</li>
 *   <li>降级不是静默的：启动期 WARN + {@link AinerCacheCapabilities}
 *       的 {@code rateLimitClusterAccurate=false}；</li>
 *   <li>产品自定义 {@code RateLimitPort} 时让位（{@code @ConditionalOnMissingBean}）。</li>
 * </ol>
 */
@ExtendWith(OutputCaptureExtension.class)
class AinerRateLimitAutoConfigurationTest {

    private static final AutoConfigurations AUTO_CONFIGURATIONS = AutoConfigurations.of(
            AinerCacheAutoConfiguration.class,
            AinerLocalCacheAutoConfiguration.class,
            AinerRedisCacheAutoConfiguration.class,
            AinerRedisCacheUnavailableAutoConfiguration.class,
            AinerCacheLockAutoConfiguration.class,
            AinerRateLimitAutoConfiguration.class,
            AinerCacheCapabilitiesAutoConfiguration.class);

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AUTO_CONFIGURATIONS);

    @Test
    void defaultAssemblyFallsBackToNodeLocalWithExplicitWarning(CapturedOutput output) {
        this.runner.run(context -> {
            assertThat(context).hasNotFailed();
            RateLimitPort port = context.getBean(RateLimitPort.class);
            assertThat(port).isInstanceOf(NodeLocalRateLimitPort.class);
            assertThat(port.clusterAccurate()).isFalse();

            AinerCacheCapabilities capabilities = context.getBean(AinerCacheCapabilities.class);
            assertThat(capabilities.rateLimitImplementationClass())
                    .isEqualTo(NodeLocalRateLimitPort.class.getName());
            assertThat(capabilities.rateLimitClusterAccurate()).isFalse();
            assertThat(capabilities.describe())
                    .contains("rateLimit{declared=LOCAL")
                    .contains("effective=" + NodeLocalRateLimitPort.class.getName())
                    .contains("clusterAccurate=false");
        });
        assertThat(output.getAll())
                .as("降级必须在启动期可见，而不是静默成立")
                .contains("限流为进程内固定窗口")
                .contains("clusterAccurate=false")
                .contains("总阈值会被放大到 N 倍");
    }

    @Test
    void redisTypeAssemblesClusterAccurateRedisImplementation() {
        this.runner.withPropertyValues("ainer.cache.type=redis")
                .withBean(RedisConnectionFactory.class, AinerRateLimitAutoConfigurationTest::unconnectedRedis)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RateLimitPort port = context.getBean(RateLimitPort.class);
                    assertThat(port).isInstanceOf(RedisFixedWindowRateLimitPort.class);
                    assertThat(port.clusterAccurate()).isTrue();

                    AinerCacheCapabilities capabilities = context.getBean(AinerCacheCapabilities.class);
                    assertThat(capabilities.rateLimitImplementationClass())
                            .isEqualTo(RedisFixedWindowRateLimitPort.class.getName());
                    assertThat(capabilities.rateLimitClusterAccurate()).isTrue();
                    assertThat(capabilities.describe())
                            .contains("rateLimit{declared=REDIS")
                            .contains("clusterAccurate=true");
                });
    }

    @Test
    void keyPrefixComesFromProperties() {
        this.runner.withPropertyValues("ainer.cache.rate-limit.key-prefix=ainer:custom:")
                .run(context -> assertThat(context.getBean(RateLimitPort.class).toString())
                        .contains("ainer:custom:"));
    }

    @Test
    void blankKeyPrefixFallsBackToDefault() {
        this.runner.withPropertyValues("ainer.cache.rate-limit.key-prefix=")
                .run(context -> assertThat(context.getBean(RateLimitPort.class).toString())
                        .contains("ainer:ratelimit:"));
    }

    @Test
    void quotaIsNotPartOfInfrastructureConfiguration() {
        // 配额（limit/window）由调用方传入；这里用一个具体调用证明端口可用且语义与文档一致
        this.runner.run(context -> {
            RateLimitPort port = context.getBean(RateLimitPort.class);
            assertThat(port.tryAcquire("probe:key", 1, 2, Duration.ofMinutes(1)).allowed()).isTrue();
            assertThat(port.tryAcquire("probe:key", 1, 2, Duration.ofMinutes(1)).allowed()).isTrue();
            assertThat(port.tryAcquire("probe:key", 1, 2, Duration.ofMinutes(1)).allowed()).isFalse();
        });
    }

    @Test
    void userProvidedRateLimitPortWins() {
        this.runner.withUserConfiguration(CustomPortConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RateLimitPort.class)).isSameAs(CustomPortConfiguration.PORT);
            AinerCacheCapabilities capabilities = context.getBean(AinerCacheCapabilities.class);
            assertThat(capabilities.rateLimitImplementationClass())
                    .isEqualTo(RecordingRateLimitPort.class.getName());
            // 自定义实现自报 clusterAccurate=false 时，能力报告照样如实反映
            assertThat(capabilities.rateLimitClusterAccurate()).isFalse();
        });
    }

    @Test
    void redisBackendWithoutRedisRateLimitImplementationWarnsAboutDegradation(CapturedOutput output) {
        // type=redis 但缓存整体关闭：Redis 限流实现不装配，退化为进程内实现
        this.runner.withPropertyValues("ainer.cache.type=redis", "ainer.cache.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RateLimitPort.class)).isInstanceOf(NodeLocalRateLimitPort.class);
                });
        assertThat(output.getAll())
                .contains("没有可用的 Redis 限流实现")
                .contains("clusterAccurate=false");
    }

    /** 只为装配存在性：不建立真实连接（限流实现构造不需要 Redis 在线）。 */
    private static RedisConnectionFactory unconnectedRedis() {
        return new LettuceConnectionFactory("127.0.0.1", 6379);
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomPortConfiguration {

        static final RateLimitPort PORT = new RecordingRateLimitPort();

        @Bean
        RateLimitPort customRateLimitPort() {
            return PORT;
        }
    }

    /** 极简自定义实现：记录调用并自报「非集群精确」。 */
    static final class RecordingRateLimitPort implements RateLimitPort {

        private final List<String> keys = new ArrayList<>();

        @Override
        public RateLimitDecision tryAcquire(String key, int permits, int limit, Duration window) {
            this.keys.add(key);
            return RateLimitDecision.allowed(limit - permits);
        }

        @Override
        public boolean clusterAccurate() {
            return false;
        }
    }

    /** 限流装配不得影响锁与缓存的既有选择（防止 @Conditional 写宽导致锁让位）。 */
    @Test
    void rateLimitAssemblyDoesNotDisturbLockOrCacheSelection() {
        this.runner.run(context -> {
            assertThat(context.getBean(DistributedLockPort.class))
                    .isInstanceOf(LocalDistributedLockPort.class);
            assertThat(context.getBean(CacheManager.class)).isNotNull();
        });
        this.runner.withPropertyValues("ainer.cache.type=redis")
                .withBean(RedisConnectionFactory.class, AinerRateLimitAutoConfigurationTest::unconnectedRedis)
                .run(context -> assertThat(context.getBean(DistributedLockPort.class))
                        .isInstanceOf(RedisDistributedLockPort.class));
    }
}
