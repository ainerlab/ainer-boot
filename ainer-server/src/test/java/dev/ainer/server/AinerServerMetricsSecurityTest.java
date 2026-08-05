package dev.ainer.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.workspace.enabled=false",
                "ainer.ai.enabled=false",
                "ainer.security.resource-server.enabled=true",
                "management.endpoints.web.base-path=/management",
                "spring.flyway.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        })
@Import(AinerServerMetricsSecurityTest.JwtTestConfiguration.class)
class AinerServerMetricsSecurityTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void prometheusUsesEndpointIdentityAndDedicatedTenantlessServiceAuthorization() throws Exception {
        assertThat(metrics(null).statusCode()).isEqualTo(401);
        assertThat(metrics("metrics-user").statusCode()).isEqualTo(403);
        assertThat(metrics("metrics-tenant-service").statusCode()).isEqualTo(403);
        assertThat(metrics("metrics-missing-scope").statusCode()).isEqualTo(403);

        HttpResponse<String> allowed = metrics("metrics-service");
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.body()).contains("# HELP").contains("jvm_");
    }

    private HttpResponse<String> metrics(String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d/management/prometheus".formatted(port)))
                .GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                Jwt.Builder jwt = Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject("metrics-client")
                        .issuedAt(Instant.now().minusSeconds(5))
                        .expiresAt(Instant.now().plusSeconds(300))
                        .claim("actor_type", "metrics-user".equals(token) ? "USER" : "SERVICE")
                        .claim("scope", "metrics-missing-scope".equals(token)
                                ? "ai.invoke"
                                : "platform.metrics.read");
                if ("metrics-tenant-service".equals(token)) {
                    jwt.claim("tenant_id", "tenant:forbidden");
                }
                return jwt.build();
            };
        }
    }
}
