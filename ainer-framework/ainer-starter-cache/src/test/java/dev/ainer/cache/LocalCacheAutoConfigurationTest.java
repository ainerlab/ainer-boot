package dev.ainer.cache;

import dev.ainer.cache.autoconfigure.AinerLocalCacheAutoConfiguration;
import dev.ainer.cache.lock.DistributedLockPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link AinerLocalCacheAutoConfiguration}: Caffeine cache manager + in-memory lock port
 * assemble by default (no Redis needed).
 */
class LocalCacheAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AinerLocalCacheAutoConfiguration.class));

    @Test
    void cacheManagerAndLockPortAssembleByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(CacheManager.class);
            assertThat(context).hasSingleBean(DistributedLockPort.class);
        });
    }

    @Test
    void lockAcquireAndRelease() {
        runner.run(context -> {
            DistributedLockPort lock = context.getBean(DistributedLockPort.class);
            Optional<DistributedLockPort.LockHandle> acquired = lock.tryLock("test-key", Duration.ofSeconds(10));
            assertThat(acquired).isPresent();

            // Second acquire fails (already locked)
            Optional<DistributedLockPort.LockHandle> second = lock.tryLock("test-key", Duration.ofSeconds(10));
            assertThat(second).isEmpty();

            // Release and re-acquire succeeds
            lock.release(acquired.get());
            Optional<DistributedLockPort.LockHandle> reacquired = lock.tryLock("test-key", Duration.ofSeconds(10));
            assertThat(reacquired).isPresent();
        });
    }

    @Test
    void cacheManagerIsCaffeineByDefault() {
        runner.run(context -> {
            CacheManager cm = context.getBean(CacheManager.class);
            assertThat(cm.getClass().getName()).contains("Caffeine");
        });
    }
}
