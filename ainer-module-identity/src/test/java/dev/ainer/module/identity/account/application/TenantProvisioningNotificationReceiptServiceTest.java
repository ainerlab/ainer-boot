package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantProvisioningNotificationReceiptServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-26T09:00:00Z");
    private static final UUID NOTIFICATION_ID =
            UUID.fromString("019c0000-0000-7000-8000-000000000021");
    private static final NotificationGatewayActor ACTOR =
            new NotificationGatewayActor(
                    "notification-gateway", null, "req-receipt-1");

    private StubRepository repository;
    private TenantProvisioningNotificationReceiptService service;

    @BeforeEach
    void setUp() {
        repository = new StubRepository();
        service = new TenantProvisioningNotificationReceiptService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recordsDeliveredReceiptForPublishedNotification() {
        repository.publications.put(
                NOTIFICATION_ID,
                new TenantProvisioningNotificationPublication(
                        NOTIFICATION_ID, "PUBLISHED", NOW.minusSeconds(10)));

        TenantProvisioningNotificationReceiptResult result = service.record(
                command(
                        "gateway-event-1",
                        TenantProvisioningNotificationDeliveryStatus.DELIVERED,
                        NOW.minusSeconds(2),
                        null),
                ACTOR);

        assertThat(result.created()).isTrue();
        assertThat(result.receipt().id().version()).isEqualTo(7);
        assertThat(result.receipt().gatewayClientId())
                .isEqualTo("notification-gateway");
        assertThat(result.receipt().requestId()).isEqualTo("req-receipt-1");
        assertThat(repository.insertCount).isEqualTo(1);
    }

    @Test
    void normalizesFailureCodeAndReplaysWithoutSecondInsert() {
        repository.publications.put(
                NOTIFICATION_ID,
                new TenantProvisioningNotificationPublication(
                        NOTIFICATION_ID, "PUBLISHED", NOW.minusSeconds(10)));
        var command = command(
                "gateway-event-failed",
                TenantProvisioningNotificationDeliveryStatus.FAILED,
                NOW.minusSeconds(1),
                "mailbox_unavailable");

        TenantProvisioningNotificationReceiptResult created =
                service.record(command, ACTOR);
        TenantProvisioningNotificationReceiptResult replayed =
                service.record(command, ACTOR);
        TenantProvisioningNotificationReceiptResult duplicateEvent =
                service.record(
                        command(
                                "gateway-event-failed-duplicate",
                                TenantProvisioningNotificationDeliveryStatus.FAILED,
                                NOW.minusSeconds(1),
                                "MAILBOX_UNAVAILABLE"),
                        ACTOR);

        assertThat(created.receipt().failureCode())
                .isEqualTo("MAILBOX_UNAVAILABLE");
        assertThat(replayed.created()).isFalse();
        assertThat(duplicateEvent.created()).isFalse();
        assertThat(duplicateEvent.receipt().gatewayEventId())
                .isEqualTo("gateway-event-failed");
        assertThat(repository.insertCount).isEqualTo(1);
    }

    @Test
    void rejectsConflictingReplayAndNonPublishedNotification() {
        repository.publications.put(
                NOTIFICATION_ID,
                new TenantProvisioningNotificationPublication(
                        NOTIFICATION_ID, "PUBLISHED", NOW.minusSeconds(10)));
        service.record(
                command(
                        "gateway-event-1",
                        TenantProvisioningNotificationDeliveryStatus.DELIVERED,
                        NOW.minusSeconds(2),
                        null),
                ACTOR);

        assertIdentityError(
                () -> service.record(
                        command(
                                "gateway-event-1",
                                TenantProvisioningNotificationDeliveryStatus.FAILED,
                                NOW.minusSeconds(2),
                                "BOUNCED"),
                        ACTOR),
                IdentityErrorCode.NOTIFICATION_RECEIPT_IDEMPOTENCY_CONFLICT);

        UUID pendingId =
                UUID.fromString("019c0000-0000-7000-8000-000000000022");
        repository.publications.put(
                pendingId,
                new TenantProvisioningNotificationPublication(
                        pendingId, "PENDING", null));
        assertIdentityError(
                () -> service.record(
                        new TenantProvisioningNotificationReceiptCommand(
                                "gateway-event-pending",
                                pendingId,
                                TenantProvisioningNotificationDeliveryStatus.DELIVERED,
                                NOW,
                                null),
                        ACTOR),
                IdentityErrorCode.NOTIFICATION_RECEIPT_STATE_CONFLICT);
    }

    @Test
    void rejectsUnknownInvalidFutureAndTenantBoundReceipts() {
        assertIdentityError(
                () -> service.record(
                        command(
                                "gateway-event-missing",
                                TenantProvisioningNotificationDeliveryStatus.DELIVERED,
                                NOW,
                                null),
                        ACTOR),
                IdentityErrorCode.NOTIFICATION_RECEIPT_NOT_FOUND);
        assertIdentityError(
                () -> service.record(
                        command(
                                "gateway-event-future",
                                TenantProvisioningNotificationDeliveryStatus.DELIVERED,
                                NOW.plusSeconds(301),
                                null),
                        ACTOR),
                IdentityErrorCode.INVALID_NOTIFICATION_RECEIPT);
        assertIdentityError(
                () -> service.record(
                        command(
                                "gateway-event-no-code",
                                TenantProvisioningNotificationDeliveryStatus.FAILED,
                                NOW,
                                null),
                        ACTOR),
                IdentityErrorCode.INVALID_NOTIFICATION_RECEIPT);
        assertStandardError(
                () -> service.record(
                        command(
                                "gateway-event-tenant",
                                TenantProvisioningNotificationDeliveryStatus.DELIVERED,
                                NOW,
                                null),
                        new NotificationGatewayActor(
                                "notification-gateway",
                                UUID.randomUUID().toString(),
                                "req-receipt-tenant")),
                StandardErrorCode.FORBIDDEN);
    }

    private TenantProvisioningNotificationReceiptCommand command(
            String eventId,
            TenantProvisioningNotificationDeliveryStatus status,
            Instant occurredAt,
            String failureCode) {
        return new TenantProvisioningNotificationReceiptCommand(
                eventId, NOTIFICATION_ID, status, occurredAt, failureCode);
    }

    private void assertIdentityError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            IdentityErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(expected));
    }

    private void assertStandardError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            StandardErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(expected));
    }

    private static final class StubRepository
            implements TenantProvisioningNotificationReceiptRepository {

        private final Map<UUID, TenantProvisioningNotificationPublication>
                publications = new LinkedHashMap<>();
        private final Map<String, TenantProvisioningNotificationReceipt>
                receiptsByEvent = new LinkedHashMap<>();
        private final Map<UUID, TenantProvisioningNotificationReceipt>
                receiptsByNotification = new LinkedHashMap<>();
        private int insertCount;

        @Override
        public void acquireLocks(
                String gatewayClientId,
                String eventId,
                UUID notificationId) {
        }

        @Override
        public Optional<TenantProvisioningNotificationReceipt> findByGatewayEvent(
                String gatewayClientId,
                String eventId) {
            return Optional.ofNullable(
                    receiptsByEvent.get(gatewayClientId + '\u001f' + eventId));
        }

        @Override
        public Optional<TenantProvisioningNotificationReceipt> findByNotification(
                UUID notificationId) {
            return Optional.ofNullable(receiptsByNotification.get(notificationId));
        }

        @Override
        public Optional<TenantProvisioningNotificationPublication>
                findPublicationForUpdate(UUID notificationId) {
            return Optional.ofNullable(publications.get(notificationId));
        }

        @Override
        public UUID nextUuidV7() {
            return UUID.fromString("019c0000-0000-7000-8000-000000000023");
        }

        @Override
        public void insert(TenantProvisioningNotificationReceipt receipt) {
            insertCount++;
            receiptsByEvent.put(
                    receipt.gatewayClientId()
                            + '\u001f'
                            + receipt.gatewayEventId(),
                    receipt);
            receiptsByNotification.put(receipt.notificationId(), receipt);
        }
    }
}
