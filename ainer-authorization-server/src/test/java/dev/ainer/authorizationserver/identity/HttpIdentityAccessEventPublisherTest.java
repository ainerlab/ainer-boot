package dev.ainer.authorizationserver.identity;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.ainer.module.identity.account.application.IdentityAccessEventPublicationException;
import dev.ainer.module.identity.account.domain.IdentityAccessEvent;
import dev.ainer.security.client.ClientCredentialsServiceTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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

class HttpIdentityAccessEventPublisherTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void publishesVersionedEventWithBearerTokenAndClassifiesAuthenticationFailure() throws Exception {
        AtomicInteger targetStatus = new AtomicInteger(204);
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> payload = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/token", exchange -> respond(exchange, 200, """
                {"access_token":"relay-token","token_type":"Bearer",\
                 "expires_in":300,"scope":"identity.access-events.publish"}
                """));
        server.createContext("/internal/identity/access-events", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, targetStatus.get(), "");
        });
        server.start();
        URI baseUri = URI.create("http://127.0.0.1:%d".formatted(server.getAddress().getPort()));
        ClientCredentialsServiceTokenProvider tokenProvider = new ClientCredentialsServiceTokenProvider(
                baseUri.resolve("/oauth2/token"),
                "ainer-identity-relay",
                "test-secret-with-24-characters",
                Set.of("identity.access-events.publish"),
                true);
        HttpIdentityAccessEventPublisher publisher =
                new HttpIdentityAccessEventPublisher(baseUri, tokenProvider);
        IdentityAccessEvent event = IdentityAccessEvent.membershipRevoked(
                UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-07-23T03:00:00Z"));

        publisher.publish(event);

        assertThat(authorization.get()).isEqualTo("Bearer relay-token");
        assertThat(payload.get())
                .contains("\"eventId\":\"" + event.id() + "\"")
                .contains("\"eventType\":\"IDENTITY_MEMBERSHIP_REVOKED\"")
                .contains("\"payloadVersion\":1");

        targetStatus.set(403);
        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOfSatisfying(IdentityAccessEventPublicationException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(HttpIdentityAccessEventPublisher.AUTHENTICATION_REJECTED));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (!body.isEmpty()) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } else {
            exchange.sendResponseHeaders(status, -1);
        }
        exchange.close();
    }
}
