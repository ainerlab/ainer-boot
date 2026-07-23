package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.application.IdentityAccessEventOutboxService;
import dev.ainer.module.identity.account.application.IdentityAccessEventRelay;
import dev.ainer.module.identity.account.application.IdentityAccessEventRelayResult;
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

final class IdentityAccessEventRelayRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityAccessEventRelayRunner.class);

    private final IdentityAccessEventRelay relay;
    private final IdentityAccessEventOutboxService outboxService;
    private final IdentityAccessEventRelayProperties properties;
    private final Clock clock;
    private final String leaseOwner = "ainer-auth/" + UUID.randomUUID();
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Counter cycleFailedCounter;
    private final AtomicLong pendingGauge = new AtomicLong();
    private final AtomicLong failedGauge = new AtomicLong();
    private final AtomicLong exhaustedGauge = new AtomicLong();
    private final AtomicLong oldestReadyAgeSecondsGauge = new AtomicLong();

    IdentityAccessEventRelayRunner(
            IdentityAccessEventRelay relay,
            IdentityAccessEventOutboxService outboxService,
            IdentityAccessEventRelayProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.relay = relay;
        this.outboxService = outboxService;
        this.properties = properties;
        this.clock = clock;
        publishedCounter = Counter.builder("ainer.identity.access.events.published").register(meterRegistry);
        failedCounter = Counter.builder("ainer.identity.access.events.failed").register(meterRegistry);
        cycleFailedCounter = Counter.builder("ainer.identity.access.events.relay.cycle.failed")
                .register(meterRegistry);
        Gauge.builder("ainer.identity.access.events.pending", pendingGauge, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("ainer.identity.access.events.failed.current", failedGauge, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("ainer.identity.access.events.exhausted", exhaustedGauge, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder(
                        "ainer.identity.access.events.oldest.ready.age.seconds",
                        oldestReadyAgeSecondsGauge,
                        AtomicLong::get)
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${ainer.identity.access-event-relay.fixed-delay:5s}",
            initialDelayString = "${ainer.identity.access-event-relay.fixed-delay:5s}")
    void relayOnce() {
        try {
            IdentityAccessEventRelayResult result = relay.relayBatch(
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
            oldestReadyAgeSecondsGauge.set(status.oldestReadyAt() == null
                    ? 0
                    : Math.max(0, Duration.between(status.oldestReadyAt(), clock.instant()).toSeconds()));
        } catch (RuntimeException exception) {
            cycleFailedCounter.increment();
            LOGGER.error("Identity access event relay cycle failed");
        }
    }
}
