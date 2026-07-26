package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.application.TenantProvisioningNotificationOutboxService;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationRelay;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationRelayResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

final class TenantProvisioningNotificationRelayRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    TenantProvisioningNotificationRelayRunner.class);

    private final TenantProvisioningNotificationRelay relay;
    private final TenantProvisioningNotificationOutboxService outboxService;
    private final TenantProvisioningNotificationRelayProperties properties;
    private final Clock clock;
    private final String leaseOwner =
            "ainer-auth/provisioning-notification/" + UUID.randomUUID();
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Counter cycleFailedCounter;
    private final AtomicLong pendingGauge = new AtomicLong();
    private final AtomicLong failedGauge = new AtomicLong();
    private final AtomicLong exhaustedGauge = new AtomicLong();
    private final AtomicLong cancelledGauge = new AtomicLong();
    private final AtomicLong oldestReadyAgeSecondsGauge = new AtomicLong();

    TenantProvisioningNotificationRelayRunner(
            TenantProvisioningNotificationRelay relay,
            TenantProvisioningNotificationOutboxService outboxService,
            TenantProvisioningNotificationRelayProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.relay = relay;
        this.outboxService = outboxService;
        this.properties = properties;
        this.clock = clock;
        publishedCounter = Counter.builder(
                        "ainer.identity.provisioning.notifications.published")
                .register(meterRegistry);
        failedCounter = Counter.builder(
                        "ainer.identity.provisioning.notifications.failed")
                .register(meterRegistry);
        cycleFailedCounter = Counter.builder(
                        "ainer.identity.provisioning.notifications.relay.cycle.failed")
                .register(meterRegistry);
        Gauge.builder(
                        "ainer.identity.provisioning.notifications.pending",
                        pendingGauge,
                        AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder(
                        "ainer.identity.provisioning.notifications.failed.current",
                        failedGauge,
                        AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder(
                        "ainer.identity.provisioning.notifications.exhausted",
                        exhaustedGauge,
                        AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder(
                        "ainer.identity.provisioning.notifications.cancelled",
                        cancelledGauge,
                        AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder(
                        "ainer.identity.provisioning.notifications."
                                + "oldest.ready.age.seconds",
                        oldestReadyAgeSecondsGauge,
                        AtomicLong::get)
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString =
                    "${ainer.identity.provisioning-notification-relay."
                            + "fixed-delay:5s}",
            initialDelayString =
                    "${ainer.identity.provisioning-notification-relay."
                            + "fixed-delay:5s}")
    void relayOnce() {
        try {
            TenantProvisioningNotificationRelayResult result = relay.relay(
                    leaseOwner,
                    properties.getLeaseDuration(),
                    properties.getRetryDelay(),
                    properties.getMaxAttempts(),
                    properties.getBatchSize());
            publishedCounter.increment(result.published());
            failedCounter.increment(result.failed());
            var status = outboxService.status(properties.getMaxAttempts());
            pendingGauge.set(status.pending());
            failedGauge.set(status.failed());
            exhaustedGauge.set(status.exhausted());
            cancelledGauge.set(status.cancelled());
            oldestReadyAgeSecondsGauge.set(status.oldestReadyAt() == null
                    ? 0
                    : Math.max(
                            0,
                            Duration.between(
                                            status.oldestReadyAt(),
                                            clock.instant())
                                    .toSeconds()));
        } catch (RuntimeException exception) {
            cycleFailedCounter.increment();
            LOGGER.error(
                    "Tenant provisioning notification relay cycle failed");
        }
    }
}
