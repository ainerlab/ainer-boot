package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.application.TenantProvisioningNotification;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationDelivery;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationPublicationException;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationPublisher;
import dev.ainer.security.client.ClientCredentialsServiceTokenProvider;
import dev.ainer.security.client.ServiceTokenException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

final class HttpTenantProvisioningNotificationPublisher
        implements TenantProvisioningNotificationPublisher {

    static final String AUTHENTICATION_REJECTED =
            "AINER.IDENTITY.PROVISIONING_NOTIFICATION_AUTHENTICATION_REJECTED";
    static final String DELIVERY_REJECTED =
            "AINER.IDENTITY.PROVISIONING_NOTIFICATION_DELIVERY_REJECTED";
    static final String TARGET_UNAVAILABLE =
            "AINER.IDENTITY.PROVISIONING_NOTIFICATION_TARGET_UNAVAILABLE";

    private final RestClient restClient = RestClient.create();
    private final URI gatewayUri;
    private final ClientCredentialsServiceTokenProvider tokenProvider;

    HttpTenantProvisioningNotificationPublisher(
            URI gatewayUri,
            ClientCredentialsServiceTokenProvider tokenProvider) {
        this.gatewayUri = gatewayUri;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void publish(TenantProvisioningNotificationDelivery delivery) {
        try {
            restClient.post()
                    .uri(gatewayUri)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + tokenProvider.accessToken())
                    .header("Idempotency-Key", delivery.notificationId().toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(TenantProvisioningNotificationRequest.from(delivery))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Unauthorized
                | HttpClientErrorException.Forbidden exception) {
            throw new TenantProvisioningNotificationPublicationException(
                    AUTHENTICATION_REJECTED, exception);
        } catch (HttpClientErrorException exception) {
            throw new TenantProvisioningNotificationPublicationException(
                    DELIVERY_REJECTED, exception);
        } catch (RestClientException | ServiceTokenException exception) {
            throw new TenantProvisioningNotificationPublicationException(
                    TARGET_UNAVAILABLE, exception);
        }
    }

    private record TenantProvisioningNotificationRequest(
            UUID notificationId,
            String notificationType,
            int templateVersion,
            UUID provisioningRequestId,
            UUID tenantId,
            UUID subjectId,
            DeliveryTarget deliveryTarget,
            ActivationMaterial activation,
            Instant expiresAt) {

        private static TenantProvisioningNotificationRequest from(
                TenantProvisioningNotificationDelivery delivery) {
            TenantProvisioningNotification notification =
                    delivery.notification();
            ActivationMaterial activation = notification.activationGrantId() == null
                    ? null
                    : new ActivationMaterial(
                            notification.activationGrantId(),
                            notification.activationSecret());
            return new TenantProvisioningNotificationRequest(
                    delivery.notificationId(),
                    notification.type().name(),
                    delivery.templateVersion(),
                    notification.provisioningRequestId(),
                    notification.tenantId(),
                    notification.subjectId(),
                    new DeliveryTarget(
                            notification.deliveryChannel(),
                            notification.recipientReference()),
                    activation,
                    notification.expiresAt());
        }
    }

    private record DeliveryTarget(String channel, String recipientReference) {
    }

    private record ActivationMaterial(UUID grantId, String secret) {
    }
}
