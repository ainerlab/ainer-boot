package dev.ainer.security.autoconfigure;

import dev.ainer.core.web.ApiResponse;
import dev.ainer.security.actor.AuthenticatedActor;
import dev.ainer.security.actor.AuthenticatedActorResolver;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = AinerStepUpIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.security.resource-server.enabled=true",
                "ainer.security.resource-server.step-up.enabled=true",
                "ainer.security.resource-server.step-up.max-auth-age=15m",
                "ainer.security.resource-server.step-up.clock-skew=30s",
                "ainer.security.resource-server.step-up.always-protected-paths=/api/security/step-up",
                "spring.main.banner-mode=off"
        })
class AinerStepUpIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-26T03:00:00Z");

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void recentStrongUserTokenIsAllowed() throws Exception {
        assertThat(post("strong-user").statusCode()).isEqualTo(200);
    }

    @Test
    void missingOrInsufficientAuthenticationKeepsDistinct401And403Semantics() throws Exception {
        HttpResponse<String> anonymous = post(null);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.body()).contains("AINER.COMMON.UNAUTHENTICATED");

        HttpResponse<String> passwordOnly = post("password-user");
        assertThat(passwordOnly.statusCode()).isEqualTo(403);
        assertThat(passwordOnly.body()).contains("AINER.SECURITY.RECENT_STRONG_AUTHENTICATION_REQUIRED");
    }

    @Test
    void serviceTokensAndUntrustedFutureAuthTimeAreRejected() throws Exception {
        assertThat(post("service-with-amr").statusCode()).isEqualTo(403);
        assertThat(post("future-user").statusCode()).isEqualTo(403);
        assertThat(post("stale-user").statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> post(String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d/api/security/step-up".formatted(port)))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({StepUpEndpoint.class, SecurityTestConfiguration.class})
    static class TestApplication {
    }

    @RestController
    static class StepUpEndpoint {

        private final AuthenticatedActorResolver actorResolver;

        StepUpEndpoint(AuthenticatedActorResolver actorResolver) {
            this.actorResolver = actorResolver;
        }

        @PostMapping("/api/security/step-up")
        ApiResponse<AuthenticatedActor> stepUp(HttpServletRequest request) {
            return ApiResponse.success(actorResolver.requireCurrent(), RequestIds.currentOrCreate(request));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityTestConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                boolean service = "service-with-amr".equals(token);
                Instant authTime = switch (token) {
                    case "future-user" -> NOW.plusSeconds(31);
                    case "stale-user" -> NOW.minusSeconds(16 * 60);
                    default -> NOW.minusSeconds(30);
                };
                List<String> amr = "password-user".equals(token)
                        ? List.of("pwd")
                        : List.of("pwd", "mfa", "pop");
                return Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject(service ? "service:test" : "subject:user-1")
                        .claim("tenant_id", "tenant:trusted")
                        .claim("actor_type", service ? "SERVICE" : "USER")
                        .claim("amr", amr)
                        .claim("auth_time", authTime)
                        .issuedAt(NOW.minusSeconds(60))
                        .expiresAt(NOW.plusSeconds(300))
                        .build();
            };
        }
    }
}
