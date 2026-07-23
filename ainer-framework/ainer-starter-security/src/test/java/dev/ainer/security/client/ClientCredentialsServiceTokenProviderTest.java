package dev.ainer.security.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ClientCredentialsServiceTokenProviderTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void obtainsAndCachesClientCredentialsToken() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> form = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/token", exchange -> {
            requests.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            form.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"access_token":"service-token","token_type":"Bearer",\
                     "expires_in":300,"scope":"identity.directory.read"}
                    """);
        });
        server.start();
        URI tokenUri = URI.create("http://127.0.0.1:%d/oauth2/token"
                .formatted(server.getAddress().getPort()));
        ClientCredentialsServiceTokenProvider provider = new ClientCredentialsServiceTokenProvider(
                tokenUri,
                "directory-client",
                "test-secret-with-24-characters",
                Set.of("identity.directory.read"),
                true);

        assertThat(provider.accessToken()).isEqualTo("service-token");
        assertThat(provider.accessToken()).isEqualTo("service-token");

        assertThat(requests).hasValue(1);
        assertThat(authorization.get()).startsWith("Basic ");
        assertThat(form.get())
                .contains("grant_type=client_credentials")
                .contains("scope=identity.directory.read");
    }

    @Test
    void rejectsInsecureTokenUriUnlessExplicitlyAllowedForTests() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                new ClientCredentialsServiceTokenProvider(
                        URI.create("http://127.0.0.1/oauth2/token"),
                        "directory-client",
                        "test-secret-with-24-characters",
                        Set.of("identity.directory.read"),
                        false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Service token URI must use HTTPS");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
