package dev.ainer.server.security;

import dev.ainer.authorization.application.AuthorizationDecisionAuditLifecycleService;
import dev.ainer.authorization.application.AuthorizationDecisionAuditOperationalStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 决策审计保留任务：周期性把超过热保留期的决策审计搬进归档表，并刷新运维指标。
 *
 * <p>放在装配层（{@code ainer-server}）而不是 {@code ainer-module-authorization}：归档本身是
 * 模块能力（{@code AuthorizationDecisionAuditLifecycleService} 持有事务与 SQL 语义），而
 * 「谁在什么时候跑、批次多大、往哪个 MeterRegistry 报指标」是宿主部署决策。模块因此不引入
 * 定时/监控绑定，非 web 或使用别的调度器（如任务模块）的消费者可以自己装配等价任务。
 *
 * <p>首次执行声明 {@code initialDelayString}：{@code @Scheduled} 不声明首次延迟时 Spring 会在
 * 上下文刷新后立即执行一次，与启动期负载和其它消费者抢同一批行锁。该契约由
 * {@code AuthorizationDecisionAuditRetentionSchedulingContractTest} 固化。
 *
 * <p>单周期按批循环搬运，最多 {@code max-batches-per-cycle} 批：单批限制持锁时间与 WAL，
 * 循环上限保证一个周期不会无限占用连接；剩余积压由下一个周期继续搬运。
 */
final class AuthorizationDecisionAuditRetentionRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            AuthorizationDecisionAuditRetentionRunner.class);

    private final AuthorizationDecisionAuditLifecycleService lifecycleService;
    private final AuthorizationDecisionAuditRetentionProperties properties;
    private final Clock clock;
    private final Counter archivedCounter;
    private final Counter failedCounter;
    private final AtomicLong hotGauge = new AtomicLong();
    private final AtomicLong archivedGauge = new AtomicLong();
    private final AtomicLong oldestHotAgeGauge = new AtomicLong();

    AuthorizationDecisionAuditRetentionRunner(
            AuthorizationDecisionAuditLifecycleService lifecycleService,
            AuthorizationDecisionAuditRetentionProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.lifecycleService = lifecycleService;
        this.properties = properties;
        this.clock = clock;
        archivedCounter = meterRegistry.counter("ainer.authorization.decision.audit.archived");
        failedCounter = meterRegistry.counter("ainer.authorization.decision.audit.archive.failed");
        Gauge.builder("ainer.authorization.decision.audit.hot", hotGauge, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("ainer.authorization.decision.audit.archive.current", archivedGauge, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("ainer.authorization.decision.audit.oldest.hot.age.seconds", oldestHotAgeGauge, AtomicLong::get)
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${ainer.authorization.decision-audit-retention.fixed-delay:5m}",
            initialDelayString = "${ainer.authorization.decision-audit-retention.initial-delay:5m}")
    void runOnce() {
        try {
            var now = clock.instant();
            int archived = archiveExpired(now);
            archivedCounter.increment(archived);
            AuthorizationDecisionAuditOperationalStatus status = lifecycleService.status();
            hotGauge.set(status.hot());
            archivedGauge.set(status.archived());
            long oldestHotAgeSeconds = status.oldestHotAt() == null
                    ? 0
                    : Math.max(0, Duration.between(status.oldestHotAt(), now).toSeconds());
            oldestHotAgeGauge.set(oldestHotAgeSeconds);
            if (archived > 0) {
                LOGGER.info("Authorization decision audit retention archived {} rows (hot={}, archived={})",
                        archived, status.hot(), status.archived());
            }
            warnWhenOldestHotRowExceedsWindow(oldestHotAgeSeconds);
        } catch (RuntimeException exception) {
            failedCounter.increment();
            LOGGER.error("Authorization decision audit retention cycle failed", exception);
        }
    }

    /**
     * 连续搬运直到某批不足 {@code batchSize}（说明已无过期行）或达到单周期批次上限。
     */
    private int archiveExpired(Instant now) {
        var cutoff = now.minus(properties.getHotRetention());
        int archivedTotal = 0;
        for (int batch = 0; batch < properties.getMaxBatchesPerCycle(); batch++) {
            int archived = lifecycleService.archiveBefore(cutoff, properties.getBatchSize());
            archivedTotal += archived;
            if (archived < properties.getBatchSize()) {
                break;
            }
        }
        return archivedTotal;
    }

    /**
     * 最旧热行年龄超过告警窗口时 WARN：它意味着热保留期已不成立——要么归档任务没有在跑，
     * 要么单周期搬运速度持续落后于写入速度。这是本缺陷（审计表无限增长）最早可见的信号。
     */
    private void warnWhenOldestHotRowExceedsWindow(long oldestHotAgeSeconds) {
        long warnWindowSeconds = properties.getOldestHotWarnWindow().toSeconds();
        if (oldestHotAgeSeconds > warnWindowSeconds) {
            LOGGER.warn(
                    "Authorization decision audit oldest hot row is {}s old, beyond the {}s warn window; "
                            + "retention is not keeping up",
                    oldestHotAgeSeconds, warnWindowSeconds);
        }
    }
}
