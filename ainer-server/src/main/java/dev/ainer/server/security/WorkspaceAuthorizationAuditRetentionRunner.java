package dev.ainer.server.security;

import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditLifecycleService;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditOperationalStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

final class WorkspaceAuthorizationAuditRetentionRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            WorkspaceAuthorizationAuditRetentionRunner.class);

    private final WorkspaceAuthorizationAuditLifecycleService lifecycleService;
    private final WorkspaceAuthorizationAuditRetentionProperties properties;
    private final Clock clock;
    private final Counter archivedCounter;
    private final Counter failedCounter;
    private final AtomicLong hotGauge = new AtomicLong();
    private final AtomicLong archivedGauge = new AtomicLong();
    private final AtomicLong deniedWindowGauge = new AtomicLong();
    private final AtomicLong ownerlessGauge = new AtomicLong();
    private final AtomicLong oldestHotAgeGauge = new AtomicLong();

    WorkspaceAuthorizationAuditRetentionRunner(
            WorkspaceAuthorizationAuditLifecycleService lifecycleService,
            WorkspaceAuthorizationAuditRetentionProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.lifecycleService = lifecycleService;
        this.properties = properties;
        this.clock = clock;
        archivedCounter = meterRegistry.counter("ainer.workspace.authorization.audit.archived");
        failedCounter = meterRegistry.counter("ainer.workspace.authorization.audit.archive.failed");
        Gauge.builder("ainer.workspace.authorization.audit.hot", hotGauge, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("ainer.workspace.authorization.audit.archive.current", archivedGauge, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("ainer.workspace.authorization.audit.denied.window", deniedWindowGauge, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("ainer.workspace.ownerless", ownerlessGauge, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("ainer.workspace.authorization.audit.oldest.hot.age.seconds", oldestHotAgeGauge, AtomicLong::get)
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${ainer.workspace.authorization-audit-retention.fixed-delay:5m}",
            initialDelayString = "${ainer.workspace.authorization-audit-retention.fixed-delay:5m}")
    void runOnce() {
        try {
            var now = clock.instant();
            int archived = lifecycleService.archiveBefore(
                    now.minus(properties.getHotRetention()), properties.getBatchSize());
            archivedCounter.increment(archived);
            WorkspaceAuthorizationAuditOperationalStatus status = lifecycleService.status(
                    now.minus(properties.getDeniedWindow()));
            hotGauge.set(status.hot());
            archivedGauge.set(status.archived());
            deniedWindowGauge.set(status.deniedInWindow());
            ownerlessGauge.set(status.ownerlessWorkspaces());
            oldestHotAgeGauge.set(status.oldestHotAt() == null
                    ? 0
                    : Math.max(0, Duration.between(status.oldestHotAt(), now).toSeconds()));
        } catch (RuntimeException exception) {
            failedCounter.increment();
            LOGGER.error("Workspace authorization audit retention cycle failed");
        }
    }
}
