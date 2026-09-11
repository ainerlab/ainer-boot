package dev.ainer.spring.scheduling;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AinerSchedulingAutoConfiguration} 的装配契约：默认（无配置）时 {@code @Scheduled}
 * 方法必须真的被周期执行；只有显式 {@code ainer.scheduling.enabled=false} 才关闭。
 *
 * <p>这条测试就是 2026-09-11 通知投递缺陷的框架级护栏：当时 {@code @EnableScheduling} 只挂在
 * 一个默认关闭的业务条件配置上，任何「装配存在但 @Scheduled 不跑」的回归都会在这里失败。
 */
class AinerSchedulingAutoConfigurationTest {

    private static final Duration AWAIT = Duration.ofSeconds(10);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AinerSchedulingAutoConfiguration.class))
            .withUserConfiguration(TickingConfiguration.class);

    @Test
    void scheduledMethodRunsWithoutAnyPropertyConfigured() {
        contextRunner.run(context -> {
            AtomicInteger ticks = context.getBean(TickingTask.class).ticks();
            awaitTicks(ticks);
            assertThat(ticks.get()).isGreaterThanOrEqualTo(1);
        });
    }

    @Test
    void scheduledMethodRunsWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("ainer.scheduling.enabled=true")
                .run(context -> {
                    AtomicInteger ticks = context.getBean(TickingTask.class).ticks();
                    awaitTicks(ticks);
                    assertThat(ticks.get()).isGreaterThanOrEqualTo(1);
                });
    }

    @Test
    void scheduledMethodStaysIdleWhenSchedulingDisabled() {
        contextRunner
                .withPropertyValues("ainer.scheduling.enabled=false")
                .run(context -> {
                    AtomicInteger ticks = context.getBean(TickingTask.class).ticks();
                    sleep(Duration.ofMillis(500));
                    assertThat(ticks.get()).isZero();
                });
    }

    private static void awaitTicks(AtomicInteger ticks) {
        long deadline = System.nanoTime() + AWAIT.toNanos();
        while (ticks.get() < 1 && System.nanoTime() < deadline) {
            sleep(Duration.ofMillis(20));
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TickingConfiguration {

        @Bean
        TickingTask tickingTask() {
            return new TickingTask();
        }
    }

    /** 统计被调度次数的探针任务。 */
    static class TickingTask {

        private final AtomicInteger ticks = new AtomicInteger();

        AtomicInteger ticks() {
            return ticks;
        }

        @Scheduled(fixedDelay = 50L)
        void tick() {
            ticks.incrementAndGet();
        }
    }
}
