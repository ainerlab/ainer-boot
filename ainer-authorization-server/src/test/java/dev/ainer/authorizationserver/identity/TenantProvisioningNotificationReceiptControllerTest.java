package dev.ainer.authorizationserver.identity;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationDeliveryStatus;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationPublication;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationReceipt;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationReceiptRepository;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationReceiptService;
import dev.ainer.security.AinerSecurityScopes;
import dev.ainer.security.service.AuthenticatedService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantProvisioningNotificationReceiptControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
    private static final UUID NOTIFICATION_ID =
            UUID.fromString("019c0000-0000-7000-8000-000000000031");
    private static final String RECEIPT_AUTHORITY =
            "SCOPE_"
                    + AinerSecurityScopes
                            .IDENTITY_PROVISIONING_NOTIFICATION_RECEIPTS_WRITE;

    @Test
    void recordsOnceAndCountsOnlyCreatedReceipt() {
        StubRepository repository = new StubRepository();
        repository.publication = new TenantProvisioningNotificationPublication(
                NOTIFICATION_ID, "PUBLISHED", NOW.minusSeconds(10));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TenantProvisioningNotificationReceiptController controller = controller(
                repository, meterRegistry);
        var body = new TenantProvisioningNotificationReceiptController.ReceiptRequest(
                "gateway-event-1",
                NOTIFICATION_ID,
                TenantProvisioningNotificationDeliveryStatus.DELIVERED,
                NOW.minusSeconds(1),
                null);

        var created = controller.record(
                body,
                authentication("notification-gateway", null, RECEIPT_AUTHORITY),
                request("req-receipt-controller-1"));
        var replayed = controller.record(
                body,
                authentication("notification-gateway", null, RECEIPT_AUTHORITY),
                request("req-receipt-controller-2"));

        assertThat(created.data().created()).isTrue();
        assertThat(replayed.data().created()).isFalse();
        assertThat(created.data().status()).isEqualTo("DELIVERED");
        assertThat(meterRegistry
                .counter(
                        "ainer.identity.tenant.provisioning.notification.delivered")
                .count()).isEqualTo(1);
        assertThat(meterRegistry
                .counter("ainer.identity.tenant.provisioning.notification.failed")
                .count()).isZero();
    }

    @Test
    void rejectsMissingScopeTenantBoundAndUnknownGateway() {
        TenantProvisioningNotificationReceiptController controller = controller(
                new StubRepository(), new SimpleMeterRegistry());
        var body = new TenantProvisioningNotificationReceiptController.ReceiptRequest(
                "gateway-event-auth",
                NOTIFICATION_ID,
                TenantProvisioningNotificationDeliveryStatus.DELIVERED,
                NOW,
                null);

        assertForbidden(() -> controller.record(
                body,
                authentication("notification-gateway", null),
                request("req-missing-scope")));
        assertForbidden(() -> controller.record(
                body,
                authentication(
                        "notification-gateway",
                        UUID.randomUUID().toString(),
                        RECEIPT_AUTHORITY),
                request("req-tenant-bound")));
        assertForbidden(() -> controller.record(
                body,
                authentication("unknown-gateway", null, RECEIPT_AUTHORITY),
                request("req-unknown-gateway")));
    }

    private TenantProvisioningNotificationReceiptController controller(
            StubRepository repository,
            SimpleMeterRegistry meterRegistry) {
        var settings = new TenantProvisioningNotificationReceiptSettings(
                Set.of("notification-gateway"));
        return new TenantProvisioningNotificationReceiptController(
                new TenantProvisioningNotificationReceiptService(
                        repository, Clock.fixed(NOW, ZoneOffset.UTC)),
                new NotificationGatewayActorResolver(settings),
                meterRegistry);
    }

    private MockHttpServletRequest request(String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", requestId);
        return request;
    }

    private Authentication authentication(
            String subject,
            String tenantId,
            String... authorities) {
        Jwt.Builder jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .claim(
                        AuthenticatedService.ACTOR_TYPE_CLAIM,
                        AuthenticatedService.SERVICE_ACTOR_TYPE);
        if (tenantId != null) {
            jwt.claim("tenant_id", tenantId);
        }
        return new JwtAuthenticationToken(
                jwt.build(),
                List.of(authorities).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList());
    }

    private void assertForbidden(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(
                                StandardErrorCode.FORBIDDEN));
    }

    private static final class StubRepository
            implements TenantProvisioningNotificationReceiptRepository {

        private final Map<String, TenantProvisioningNotificationReceipt>
                byEvent = new LinkedHashMap<>();
        private TenantProvisioningNotificationReceipt receipt;
        private TenantProvisioningNotificationPublication publication;

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
                    byEvent.get(gatewayClientId + '\u001f' + eventId));
        }

        @Override
        public Optional<TenantProvisioningNotificationReceipt> findByNotification(
                UUID notificationId) {
            return receipt != null && receipt.notificationId().equals(notificationId)
                    ? Optional.of(receipt)
                    : Optional.empty();
        }

        @Override
        public Optional<TenantProvisioningNotificationPublication>
                findPublicationForUpdate(UUID notificationId) {
            return publication != null
                            && publication.notificationId().equals(notificationId)
                    ? Optional.of(publication)
                    : Optional.empty();
        }

        @Override
        public UUID nextUuidV7() {
            return UUID.fromString("019c0000-0000-7000-8000-000000000032");
        }

        @Override
        public void insert(TenantProvisioningNotificationReceipt candidate) {
            receipt = candidate;
            byEvent.put(
                    candidate.gatewayClientId()
                            + '\u001f'
                            + candidate.gatewayEventId(),
                    candidate);
        }
    }
}
