package dev.ainer.authorizationserver.identity;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.ainer.module.identity.account.application.TenantProvisioningNotification;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationDelivery;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationPublicationException;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationType;
import dev.ainer.security.AinerSecurityScopes;
import dev.ainer.security.client.ClientCredentialsServiceTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpTenantProvisioningNotificationPublisherTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void publishesVersionedIdempotentNotificationAndClassifiesAuthenticationFailure()
            throws Exception {
        AtomicInteger targetStatus = new AtomicInteger(202);
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> idempotencyKey = new AtomicReference<>();
        AtomicReference<String> cacheControl = new AtomicReference<>();
        AtomicReference<String> payload = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/token", exchange -> respond(exchange, 200, """
                {"access_token":"notification-relay-token","token_type":"Bearer",\
                 "expires_in":300,"scope":"identity.provisioning-notifications.publish"}
                """));
        server.createContext("/internal/identity/tenant-provisioning-notifications", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            cacheControl.set(exchange.getRequestHeaders().getFirst("Cache-Control"));
            payload.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            respond(exchange, targetStatus.get(), "");
        });
        server.start();
        URI baseUri = URI.create(
                "http://127.0.0.1:%d".formatted(server.getAddress().getPort()));
        ClientCredentialsServiceTokenProvider tokenProvider =
                new ClientCredentialsServiceTokenProvider(
                        baseUri.resolve("/oauth2/token"),
                        "ainer-provisioning-notification-relay",
                        "test-secret-with-24-characters",
                        Set.of(AinerSecurityScopes
                                .IDENTITY_PROVISIONING_NOTIFICATIONS_PUBLISH),
                        true);
        HttpTenantProvisioningNotificationPublisher publisher =
                new HttpTenantProvisioningNotificationPublisher(
                        RestClient.builder().build(),
                        baseUri.resolve(
                                "/internal/identity/"
                                        + "tenant-provisioning-notifications"),
                        tokenProvider);
        UUID notificationId =
                UUID.fromString("019c1000-0000-7000-8000-000000000001");
        TenantProvisioningNotification notification =
                new TenantProvisioningNotification(
                        TenantProvisioningNotificationType.NEW_USER_ACTIVATION,
                        UUID.fromString("019c1000-0000-7000-8000-000000000002"),
                        UUID.fromString("019c1000-0000-7000-8000-000000000003"),
                        UUID.fromString("019c1000-0000-7000-8000-000000000004"),
                        "EMAIL",
                        "new-owner@example.com",
                        UUID.fromString("019c1000-0000-7000-8000-000000000005"),
                        "0123456789012345678901234567890123456789012",
                        Instant.parse("2026-07-27T00:00:00Z"));
        TenantProvisioningNotificationDelivery delivery =
                new TenantProvisioningNotificationDelivery(
                        notificationId, 1, notification);

        publisher.publish(delivery);

        assertThat(authorization.get())
                .isEqualTo("Bearer notification-relay-token");
        assertThat(idempotencyKey.get()).isEqualTo(notificationId.toString());
        assertThat(cacheControl.get()).isEqualTo("no-store");
        assertThat(payload.get())
                .contains("\"notificationId\":\"" + notificationId + "\"")
                .contains("\"notificationType\":\"NEW_USER_ACTIVATION\"")
                .contains("\"templateVersion\":1")
                .contains("\"channel\":\"EMAIL\"")
                .contains("\"recipientReference\":\"new-owner@example.com\"")
                .contains("\"secret\":\""
                        + notification.activationSecret() + "\"");

        TenantProvisioningNotification existingUserNotification =
                new TenantProvisioningNotification(
                        TenantProvisioningNotificationType
                                .EXISTING_USER_ACCEPTANCE,
                        UUID.fromString("019c1000-0000-7000-8000-000000000012"),
                        UUID.fromString("019c1000-0000-7000-8000-000000000013"),
                        UUID.fromString("019c1000-0000-7000-8000-000000000014"),
                        "IDENTITY_SUBJECT",
                        "019c1000-0000-7000-8000-000000000014",
                        null,
                        null,
                        Instant.parse("2026-08-02T00:00:00Z"));
        publisher.publish(new TenantProvisioningNotificationDelivery(
                UUID.fromString("019c1000-0000-7000-8000-000000000011"),
                1,
                existingUserNotification));
        assertThat(payload.get())
                .contains("\"notificationType\":\"EXISTING_USER_ACCEPTANCE\"")
                .contains("\"channel\":\"IDENTITY_SUBJECT\"")
                .contains("\"activation\":null")
                .doesNotContain("\"secret\"");

        targetStatus.set(400);
        assertThatThrownBy(() -> publisher.publish(delivery))
                .isInstanceOfSatisfying(
                        TenantProvisioningNotificationPublicationException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(
                                        HttpTenantProvisioningNotificationPublisher
                                                .DELIVERY_REJECTED));
        targetStatus.set(503);
        assertThatThrownBy(() -> publisher.publish(delivery))
                .isInstanceOfSatisfying(
                        TenantProvisioningNotificationPublicationException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(
                                        HttpTenantProvisioningNotificationPublisher
                                                .TARGET_UNAVAILABLE));
        targetStatus.set(403);
        assertThatThrownBy(() -> publisher.publish(delivery))
                .isInstanceOfSatisfying(
                        TenantProvisioningNotificationPublicationException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(
                                        HttpTenantProvisioningNotificationPublisher
                                                .AUTHENTICATION_REJECTED));
    }

    private void respond(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (!body.isEmpty()) {
            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } else {
            exchange.sendResponseHeaders(status, -1);
        }
        exchange.close();
    }
}
