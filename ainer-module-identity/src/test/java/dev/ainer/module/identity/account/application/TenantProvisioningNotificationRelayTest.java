package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.infrastructure.security.AesGcmTenantProvisioningNotificationPayloadProtector;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TenantProvisioningNotificationRelayTest {

    @Test
    void retriesAProviderFailureAndPublishesOnlyAfterAcknowledgement() {
        Instant initialAttemptAt = Instant.parse("2026-07-26T08:00:00Z");
        var protector = new AesGcmTenantProvisioningNotificationPayloadProtector(
                "test-v1",
                Map.of("test-v1", new byte[32]),
                new SecureRandom());
        TenantProvisioningNotification notification = new TenantProvisioningNotification(
                TenantProvisioningNotificationType.NEW_USER_ACTIVATION,
                UUID.fromString("019c0000-0000-7000-8000-000000000011"),
                UUID.fromString("019c0000-0000-7000-8000-000000000012"),
                UUID.fromString("019c0000-0000-7000-8000-000000000013"),
                "EMAIL",
                "relay@example.com",
                UUID.fromString("019c0000-0000-7000-8000-000000000014"),
                "0123456789012345678901234567890123456789012",
                initialAttemptAt.plus(Duration.ofHours(1)));
        TenantProvisioningNotificationOutboxEntry entry =
                new TenantProvisioningNotificationOutboxEntry(
                        UUID.fromString("019c0000-0000-7000-8000-000000000015"),
                        notification.provisioningRequestId(),
                        notification.tenantId(),
                        notification.subjectId(),
                        notification.type(),
                        1,
                        protector.protect(notification),
                        0,
                        initialAttemptAt.minusSeconds(1));
        InMemoryOutboxRepository repository =
                new InMemoryOutboxRepository(entry, initialAttemptAt);
        TenantProvisioningNotificationOutboxService outboxService =
                new TenantProvisioningNotificationOutboxService(repository);
        AtomicInteger providerCalls = new AtomicInteger();
        TenantProvisioningNotificationPublisher publisher = delivery -> {
            assertThat(delivery.notificationId()).isEqualTo(entry.id());
            assertThat(delivery.templateVersion()).isEqualTo(1);
            assertThat(delivery.notification()).isEqualTo(notification);
            if (providerCalls.incrementAndGet() == 1) {
                throw new TenantProvisioningNotificationPublicationException(
                        "AINER.IDENTITY.EMAIL_TEMPORARILY_UNAVAILABLE",
                        "simulated provider failure");
            }
        };

        TenantProvisioningNotificationRelayResult first =
                relayAt(initialAttemptAt, outboxService, protector, publisher)
                        .relay(
                                "relay:test",
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(5),
                                3,
                                10);
        assertThat(first).isEqualTo(
                new TenantProvisioningNotificationRelayResult(1, 0, 1));
        assertThat(repository.status).isEqualTo("FAILED");
        assertThat(repository.attemptCount).isEqualTo(1);
        assertThat(repository.lastErrorCode)
                .isEqualTo("AINER.IDENTITY.EMAIL_TEMPORARILY_UNAVAILABLE");

        TenantProvisioningNotificationRelayResult beforeRetry =
                relayAt(
                        initialAttemptAt.plusSeconds(4),
                        outboxService,
                        protector,
                        publisher)
                        .relay(
                                "relay:test",
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(5),
                                3,
                                10);
        assertThat(beforeRetry).isEqualTo(
                new TenantProvisioningNotificationRelayResult(0, 0, 0));

        TenantProvisioningNotificationRelayResult retried =
                relayAt(
                        initialAttemptAt.plusSeconds(5),
                        outboxService,
                        protector,
                        publisher)
                        .relay(
                                "relay:test",
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(5),
                                3,
                                10);
        assertThat(retried).isEqualTo(
                new TenantProvisioningNotificationRelayResult(1, 1, 0));
        assertThat(providerCalls).hasValue(2);
        assertThat(repository.status).isEqualTo("PUBLISHED");
        assertThat(repository.attemptCount).isEqualTo(2);
        assertThat(repository.publishedAt).isEqualTo(initialAttemptAt.plusSeconds(5));
    }

    private static TenantProvisioningNotificationRelay relayAt(
            Instant instant,
            TenantProvisioningNotificationOutboxService outboxService,
            TenantProvisioningNotificationPayloadProtector protector,
            TenantProvisioningNotificationPublisher publisher) {
        return new TenantProvisioningNotificationRelay(
                outboxService,
                protector,
                publisher,
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static final class InMemoryOutboxRepository
            implements TenantProvisioningNotificationOutboxRepository {

        private final TenantProvisioningNotificationOutboxEntry original;
        private Instant availableAt;
        private Instant leaseUntil;
        private String leaseOwner;
        private String status = "PENDING";
        private String lastErrorCode;
        private Instant publishedAt;
        private int attemptCount;

        private InMemoryOutboxRepository(
                TenantProvisioningNotificationOutboxEntry original,
                Instant availableAt) {
            this.original = original;
            this.availableAt = availableAt;
        }

        @Override
        public List<TenantProvisioningNotificationOutboxEntry> claimBatch(
                String candidateLeaseOwner,
                Instant now,
                Instant candidateLeaseUntil,
                int maxAttempts,
                int limit) {
            if ("PUBLISHED".equals(status)
                    || attemptCount >= maxAttempts
                    || availableAt.isAfter(now)
                    || (leaseUntil != null && leaseUntil.isAfter(now))) {
                return List.of();
            }
            leaseOwner = candidateLeaseOwner;
            leaseUntil = candidateLeaseUntil;
            attemptCount++;
            return List.of(new TenantProvisioningNotificationOutboxEntry(
                    original.id(),
                    original.provisioningRequestId(),
                    original.tenantId(),
                    original.subjectId(),
                    original.type(),
                    original.templateVersion(),
                    original.protectedNotification(),
                    attemptCount,
                    original.createdAt()));
        }

        @Override
        public boolean markPublished(
                UUID notificationId,
                String candidateLeaseOwner,
                Instant candidatePublishedAt) {
            if (!original.id().equals(notificationId)
                    || !candidateLeaseOwner.equals(leaseOwner)) {
                return false;
            }
            status = "PUBLISHED";
            publishedAt = candidatePublishedAt;
            leaseOwner = null;
            leaseUntil = null;
            lastErrorCode = null;
            return true;
        }

        @Override
        public boolean markFailed(
                UUID notificationId,
                String candidateLeaseOwner,
                Instant candidateAvailableAt,
                String errorCode) {
            if (!original.id().equals(notificationId)
                    || !candidateLeaseOwner.equals(leaseOwner)) {
                return false;
            }
            status = "FAILED";
            availableAt = candidateAvailableAt;
            leaseOwner = null;
            leaseUntil = null;
            lastErrorCode = errorCode;
            return true;
        }

        @Override
        public TenantProvisioningNotificationOutboxStatus status(int maxAttempts) {
            boolean exhausted = ("PENDING".equals(status) || "FAILED".equals(status))
                    && attemptCount >= maxAttempts;
            long pending = "PENDING".equals(status) && !exhausted ? 1 : 0;
            long failed = "FAILED".equals(status) && !exhausted ? 1 : 0;
            return new TenantProvisioningNotificationOutboxStatus(
                    pending,
                    failed,
                    exhausted ? 1 : 0,
                    "PUBLISHED".equals(status) ? 1 : 0,
                    "CANCELLED".equals(status) ? 1 : 0,
                    pending + failed == 0 ? null : availableAt);
        }
    }
}
