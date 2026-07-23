package dev.ainer.security.autoconfigure;

import dev.ainer.core.web.ApiResponse;
import dev.ainer.security.actor.AuthenticatedActor;
import dev.ainer.security.actor.AuthenticatedActorResolver;
import dev.ainer.web.request.RequestIds;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = AinerOnlineTokenValidationIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.security.resource-server.enabled=true",
                "ainer.security.resource-server.online-validation.enabled=true",
                "ainer.security.resource-server.online-validation.introspection-uri=http://127.0.0.1/introspect",
                "ainer.security.resource-server.online-validation.client-id=test-resource-server",
                "ainer.security.resource-server.online-validation.client-secret=test-only-introspection-secret",
                "ainer.security.resource-server.online-validation.allow-insecure-http=true",
                "spring.main.banner-mode=off"
        })
class AinerOnlineTokenValidationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestIntrospector introspector;

    @Autowired
    private SimpleMeterRegistry meters;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void lowRiskReadDoesNotCallIntrospection() throws Exception {
        int before = introspector.invocations();

        HttpResponse<String> response = request("GET", "/api/security/me", "active");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(introspector.invocations()).isEqualTo(before);
    }

    @Test
    void highRiskMutationIsCheckedForEveryRequestWithoutPositiveCache() throws Exception {
        int before = introspector.invocations();
        double allowedBefore = counter("ainer.security.online.validation.allowed");
        long durationBefore = timerCount();

        HttpResponse<String> first = request("POST", "/api/workspaces/workspace-1/members", "active");
        HttpResponse<String> second = request("POST", "/api/workspaces/workspace-1/members", "active");

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(introspector.invocations()).isEqualTo(before + 2);
        assertThat(counter("ainer.security.online.validation.allowed")).isEqualTo(allowedBefore + 2);
        assertThat(timerCount()).isEqualTo(durationBefore + 2);
    }

    @Test
    void inactiveHighRiskTokenUsesGenericUnauthenticatedContract() throws Exception {
        double inactiveBefore = counter("ainer.security.online.validation.inactive");

        HttpResponse<String> response = request(
                "GET", "/api/workspaces/workspace-1/authorization-audits", "inactive");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body())
                .contains("AINER.COMMON.UNAUTHENTICATED")
                .doesNotContain("revoked", "inactive");
        assertThat(counter("ainer.security.online.validation.inactive")).isEqualTo(inactiveBefore + 1);
    }

    @Test
    void introspectionDependencyFailureFailsClosedWithServiceUnavailable() throws Exception {
        double failedBefore = counter("ainer.security.online.validation.failed");

        HttpResponse<String> response = request(
                "POST", "/api/ai/invocations", "unavailable");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body())
                .contains("AINER.SECURITY.ONLINE_VALIDATION_UNAVAILABLE")
                .doesNotContain("introspection dependency failed");
        assertThat(counter("ainer.security.online.validation.failed")).isEqualTo(failedBefore + 1);
    }

    @Test
    void protectedRuleCannotBeBypassedWithoutBearerAuthentication() throws Exception {
        HttpResponse<String> response = request(
                "POST", "/api/workspaces/workspace-1/members", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("AINER.COMMON.UNAUTHENTICATED");
    }

    private HttpResponse<String> request(String method, String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d%s".formatted(port, path)))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private double counter(String name) {
        return meters.get(name).counter().count();
    }

    private long timerCount() {
        return meters.get("ainer.security.online.validation.duration").timer().count();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({TestEndpoint.class, SecurityTestConfiguration.class})
    static class TestApplication {
    }

    @RestController
    static class TestEndpoint {

        private final AuthenticatedActorResolver actorResolver;

        TestEndpoint(AuthenticatedActorResolver actorResolver) {
            this.actorResolver = actorResolver;
        }

        @GetMapping("/api/security/me")
        ApiResponse<AuthenticatedActor> current(HttpServletRequest request) {
            return response(request);
        }

        @PostMapping("/api/workspaces/{workspaceId}/members")
        ApiResponse<AuthenticatedActor> mutateWorkspace(
                @PathVariable String workspaceId, HttpServletRequest request) {
            return response(request);
        }

        @GetMapping("/api/workspaces/{workspaceId}/authorization-audits")
        ApiResponse<AuthenticatedActor> readAudits(
                @PathVariable String workspaceId, HttpServletRequest request) {
            return response(request);
        }

        @PostMapping("/api/ai/invocations")
        ApiResponse<AuthenticatedActor> invokeAi(HttpServletRequest request) {
            return response(request);
        }

        private ApiResponse<AuthenticatedActor> response(HttpServletRequest request) {
            return ApiResponse.success(actorResolver.requireCurrent(), RequestIds.currentOrCreate(request));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityTestConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject("subject:user-1")
                    .claim("scope", "ai.invoke")
                    .claim("tenant_id", "tenant:trusted")
                    .issuedAt(Instant.now().minusSeconds(5))
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        }

        @Bean
        TestIntrospector opaqueTokenIntrospector() {
            return new TestIntrospector();
        }

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    static final class TestIntrospector implements OpaqueTokenIntrospector {

        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public DefaultOAuth2AuthenticatedPrincipal introspect(String token) {
            invocations.incrementAndGet();
            if ("inactive".equals(token)) {
                throw new BadOpaqueTokenException("revoked token");
            }
            if ("unavailable".equals(token)) {
                throw new OAuth2IntrospectionException("introspection dependency failed");
            }
            return new DefaultOAuth2AuthenticatedPrincipal(
                    Map.of("active", true, "sub", "ignored-online-principal"), List.of());
        }

        int invocations() {
            return invocations.get();
        }
    }
}
