package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.domain.IdentityAccessEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityAccessEventRelayTest {

    private static final Instant NOW = Instant.parse("2026-07-23T01:00:00Z");

    @Test
    void publishesSuccessfulEventsAndSchedulesStableFailureForRetry() {
        IdentityAccessEvent successful = IdentityAccessEvent.userDisabled(
                UUID.randomUUID(), UUID.randomUUID(), NOW.minusSeconds(1));
        IdentityAccessEvent failed = IdentityAccessEvent.membershipRevoked(
                UUID.randomUUID(), UUID.randomUUID(), NOW.minusSeconds(1));
        FakeOutboxRepository repository = new FakeOutboxRepository(List.of(
                new IdentityAccessEventDelivery(successful, 1),
                new IdentityAccessEventDelivery(failed, 1)));
        IdentityAccessEventOutboxService outboxService = new IdentityAccessEventOutboxService(repository);
        IdentityAccessEventRelay relay = new IdentityAccessEventRelay(
                outboxService,
                event -> {
                    if (event.id().equals(failed.id())) {
                        throw new IdentityAccessEventPublicationException(
                                "AINER.IDENTITY.TEST_TARGET_UNAVAILABLE");
                    }
                },
                Clock.fixed(NOW, ZoneOffset.UTC));

        IdentityAccessEventRelayResult result = relay.relayBatch(
                "relay:test", Duration.ofSeconds(30), Duration.ofSeconds(45), 5, 10);

        assertThat(result).isEqualTo(new IdentityAccessEventRelayResult(2, 1, 1));
        assertThat(repository.published).containsExactly(successful.id());
        assertThat(repository.failed).containsExactly(new FailedDelivery(
                failed.id(),
                NOW.plusSeconds(45),
                "AINER.IDENTITY.TEST_TARGET_UNAVAILABLE"));
    }

    @Test
    void convertsUnexpectedPublisherFailureToNonSensitiveStableCode() {
        IdentityAccessEvent event = IdentityAccessEvent.userDisabled(
                UUID.randomUUID(), UUID.randomUUID(), NOW.minusSeconds(1));
        FakeOutboxRepository repository = new FakeOutboxRepository(List.of(
                new IdentityAccessEventDelivery(event, 1)));
        IdentityAccessEventRelay relay = new IdentityAccessEventRelay(
                new IdentityAccessEventOutboxService(repository),
                ignored -> {
                    throw new IllegalStateException("vendor response with sensitive details");
                },
                Clock.fixed(NOW, ZoneOffset.UTC));

        IdentityAccessEventRelayResult result = relay.relayBatch(
                "relay:test", Duration.ofSeconds(30), Duration.ofSeconds(10), 3, 1);

        assertThat(result).isEqualTo(new IdentityAccessEventRelayResult(1, 0, 1));
        assertThat(repository.failed).containsExactly(new FailedDelivery(
                event.id(),
                NOW.plusSeconds(10),
                IdentityAccessEventRelay.UNEXPECTED_DELIVERY_ERROR));
    }

    private static final class FakeOutboxRepository implements IdentityAccessEventOutboxRepository {

        private final List<IdentityAccessEventDelivery> deliveries;
        private final List<UUID> published = new ArrayList<>();
        private final List<FailedDelivery> failed = new ArrayList<>();

        private FakeOutboxRepository(List<IdentityAccessEventDelivery> deliveries) {
            this.deliveries = deliveries;
        }

        @Override
        public List<IdentityAccessEventDelivery> claimBatch(
                String leaseOwner,
                Instant now,
                Instant leaseUntil,
                int maxAttempts,
                int limit) {
            return deliveries;
        }

        @Override
        public boolean markPublished(UUID eventId, String leaseOwner, Instant publishedAt) {
            published.add(eventId);
            return true;
        }

        @Override
        public boolean markFailed(
                UUID eventId,
                String leaseOwner,
                Instant availableAt,
                String errorCode) {
            failed.add(new FailedDelivery(eventId, availableAt, errorCode));
            return true;
        }

        @Override
        public IdentityAccessEventOutboxStatus status(int maxAttempts) {
            return new IdentityAccessEventOutboxStatus(0, 0, 0, published.size(), null);
        }
    }

    private record FailedDelivery(UUID eventId, Instant availableAt, String errorCode) {
    }
}
