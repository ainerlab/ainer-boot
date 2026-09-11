package dev.ainer.module.notification;

import dev.ainer.module.notification.notification.application.NotificationDeliveryEngine;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 投递引擎的调度契约（回归防护）。
 *
 * <p>CI 实测教训（2026-09-11）：{@code @Scheduled} 不声明 {@code initialDelay} 时，Spring 会在上下文
 * 刷新后**立即执行一次**。那次"启动即投递"会在应用尚未就绪时抢先领取记录，并与测试中手动调用
 * {@link NotificationDeliveryEngine#deliverBatch()} 的执行路径争抢同一批记录（表现为
 * {@code expected: SENT but was: SENDING}——记录被另一个租约持有者领走，手动投递再也领不到）。
 *
 * <p>因此本测试把"必须声明首次执行延迟"固化成契约：删掉 {@code initialDelayString} 即失败。
 * 与之配套的行为验证是同包的 {@code NotificationDeliveryEngineSchedulingIntegrationTest}
 * （短间隔下仍能自动投递，证明加延迟没有削弱功能）。
 */
class NotificationDeliverySchedulingContractTest {

    @Test
    void deliverBatchDeclaresInitialDelayToAvoidRunningBeforeStartupCompletes() throws Exception {
        Scheduled scheduled = NotificationDeliveryEngine.class
                .getMethod("deliverBatch")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).as("@Scheduled 注解必须存在（否则投递不再自动运行）").isNotNull();
        assertThat(scheduled.fixedDelayString()).as("轮询间隔必须可配置").isNotBlank();
        assertThat(scheduled.initialDelayString())
                .as("必须声明首次执行延迟：无 initialDelay 会在启动瞬间抢跑并与其他消费者争抢记录")
                .isNotBlank();
    }
}
