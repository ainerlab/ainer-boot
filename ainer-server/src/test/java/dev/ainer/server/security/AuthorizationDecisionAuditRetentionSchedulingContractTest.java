package dev.ainer.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 决策审计保留任务的调度契约（回归防护）。
 *
 * <p>与 {@code NotificationDeliverySchedulingContractTest} 同一教训（2026-09-11 CI 实测）：
 * {@code @Scheduled} 不声明 {@code initialDelay} 时，Spring 会在上下文刷新后<strong>立即执行
 * 一次</strong>。归档任务若在启动瞬间抢跑，会与启动期负载争抢行锁，也会与"测试里手动驱动
 * 归档"的路径互相干扰（同一区间的候选行被另一个执行者锁走，断言看到 0 或少于预期）。
 *
 * <p>因此本测试把两件事固化成契约：轮询间隔与首次延迟都必须可配置，且首次延迟不得缺失。
 */
class AuthorizationDecisionAuditRetentionSchedulingContractTest {

    @Test
    void runOnceDeclaresInitialDelayToAvoidRunningBeforeStartupCompletes() throws Exception {
        Scheduled scheduled = AuthorizationDecisionAuditRetentionRunner.class
                .getDeclaredMethod("runOnce")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).as("@Scheduled 注解必须存在（否则归档任务不再运行）").isNotNull();
        assertThat(scheduled.fixedDelayString())
                .as("轮询间隔必须可配置")
                .contains("ainer.authorization.decision-audit-retention.fixed-delay");
        assertThat(scheduled.initialDelayString())
                .as("必须声明首次执行延迟：无 initialDelay 会在启动瞬间抢跑并争抢归档区间")
                .contains("ainer.authorization.decision-audit-retention.initial-delay");
    }
}
