package dev.ainer.security.autoconfigure;

import dev.ainer.core.web.ApiResponse;
import dev.ainer.security.actor.AuthenticatedActor;
import dev.ainer.security.actor.AuthenticatedActorResolver;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.security.token.TokenProfile;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = AinerResourceServerIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.security.resource-server.enabled=true",
                "spring.main.banner-mode=off"
        })
class AinerResourceServerIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void unauthenticatedRequestUsesAinerFailureContract() throws Exception {
        HttpResponse<String> response = get(null, null, null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue(RequestIds.HEADER)).isPresent();
        assertThat(response.body())
                .contains("AINER.COMMON.UNAUTHENTICATED")
                .contains("\"requestId\"");
    }

    @Test
    void actorComesFromVerifiedJwtAndIgnoresIdentityHeaders() throws Exception {
        HttpResponse<String> response = get("valid", "forged-tenant", "forged-subject");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"subjectId\":\"subject:user-1\"")
                .contains("\"tenantId\":\"tenant:trusted\"")
                .contains("SCOPE_ai.invoke")
                .doesNotContain("forged-tenant", "forged-subject");
    }

    @Test
    void authenticatedJwtWithoutTenantContextIsForbidden() throws Exception {
        HttpResponse<String> response = get("missing-tenant", null, null);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("AINER.COMMON.FORBIDDEN");
    }

    @Test
    void authenticatedJwtWithoutRequiredScopeIsForbidden() throws Exception {
        HttpResponse<String> response = get("missing-scope", null, null);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("AINER.COMMON.FORBIDDEN");
    }

    @Test
    void newHumanProfileResolvesToTypedPrincipalWithEpoch() throws Exception {
        HttpResponse<String> response = get(
                "/api/security/principal", "new-human", null, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"profile\":\"USER_NEUTRAL_V1\"")
                .contains("\"human\":true")
                .contains("\"service\":false")
                .contains("\"subjectId\":\"account-1\"")
                .contains("\"securityEpoch\":7");
    }

    @Test
    void newServiceProfileResolvesToTypedServicePrincipal() throws Exception {
        HttpResponse<String> response = get(
                "/api/security/principal", "new-service", null, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"profile\":\"SERVICE_V1\"")
                .contains("\"human\":false")
                .contains("\"service\":true")
                .contains("\"subjectId\":\"service-1\"")
                .contains("\"securityEpoch\":3");
    }

    @Test
    void malformedNewProfileClaimsFailClosedWith401() throws Exception {
        for (String token : List.of("valid", "unknown-profile", "mismatched-profile")) {
            HttpResponse<String> response = get(
                    "/api/security/principal", token, null, null);

            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).contains("AINER.COMMON.UNAUTHENTICATED");
        }
    }

    private HttpResponse<String> get(String token, String tenantHeader, String subjectHeader) throws Exception {
        return get("/api/security/me", token, tenantHeader, subjectHeader);
    }

    private HttpResponse<String> get(
            String path,
            String token,
            String tenantHeader,
            String subjectHeader) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d%s".formatted(port, path)))
                .GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (tenantHeader != null) {
            request.header("X-Ainer-Tenant-Id", tenantHeader);
        }
        if (subjectHeader != null) {
            request.header("X-Ainer-Subject-Id", subjectHeader);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({TestEndpoint.class, JwtTestConfiguration.class})
    static class TestApplication {
    }

    @RestController
    static class TestEndpoint {

        private final AuthenticatedActorResolver actorResolver;
        private final AuthenticatedPrincipalResolver principalResolver;

        TestEndpoint(
                AuthenticatedActorResolver actorResolver,
                AuthenticatedPrincipalResolver principalResolver) {
            this.actorResolver = actorResolver;
            this.principalResolver = principalResolver;
        }

        @GetMapping("/api/security/me")
        ApiResponse<AuthenticatedActor> current(HttpServletRequest request) {
            AuthenticatedActor actor = actorResolver.requireCurrent();
            actor.requireAuthority("SCOPE_ai.invoke");
            return ApiResponse.success(actor, RequestIds.currentOrCreate(request));
        }

        @GetMapping("/api/security/principal")
        ApiResponse<PrincipalView> principal(HttpServletRequest request) {
            AuthenticatedPrincipal principal = principalResolver.requireCurrent();
            return ApiResponse.success(
                    new PrincipalView(
                            principal.tokenProfile().claimValue(),
                            principal.isHuman(),
                            principal.isService(),
                            principal.principalSubjectRef().subjectId(),
                            principal.securityEpoch()),
                    RequestIds.currentOrCreate(request));
        }

        record PrincipalView(
                String profile,
                boolean human,
                boolean service,
                String subjectId,
                Long securityEpoch) {
        }

    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                Jwt.Builder jwt = Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject("subject:user-1")
                        .issuedAt(Instant.now().minusSeconds(5))
                        .expiresAt(Instant.now().plusSeconds(300))
                        .claim("actor_type", "USER");
                if ("new-human".equals(token)) {
                    return jwt.issuer("https://ainer.example/auth")
                            .audience(List.of("ainer-api"))
                            .subject("account-1")
                            .claim(TokenProfile.PROFILE_CLAIM, TokenProfile.USER_NEUTRAL_V1.claimValue())
                            .claim(TokenProfile.CONTRACT_VERSION_CLAIM, TokenProfile.CURRENT_CONTRACT_VERSION)
                            .claim("actor_type", "USER")
                            .claim("scope", "account.read")
                            .claim("amr", "pwd")
                            .claim("sec_epoch", 7L)
                            .build();
                }
                if ("new-service".equals(token)) {
                    return jwt.issuer("https://ainer.example/auth")
                            .audience(List.of("ainer-api"))
                            .subject("service-1")
                            .claim(TokenProfile.PROFILE_CLAIM, TokenProfile.SERVICE_V1.claimValue())
                            .claim(TokenProfile.CONTRACT_VERSION_CLAIM, TokenProfile.CURRENT_CONTRACT_VERSION)
                            .claim("actor_type", "SERVICE")
                            .claim("scope", "account.read")
                            .claim("sec_epoch", 3L)
                            .build();
                }
                if ("unknown-profile".equals(token)) {
                    return jwt.issuer("https://ainer.example/auth")
                            .audience(List.of("ainer-api"))
                            .claim(TokenProfile.PROFILE_CLAIM, "UNKNOWN_V1")
                            .claim(TokenProfile.CONTRACT_VERSION_CLAIM, TokenProfile.CURRENT_CONTRACT_VERSION)
                            .claim("actor_type", "USER")
                            .claim("amr", "pwd")
                            .build();
                }
                if ("mismatched-profile".equals(token)) {
                    return jwt.issuer("https://ainer.example/auth")
                            .audience(List.of("ainer-api"))
                            .claim(TokenProfile.PROFILE_CLAIM, TokenProfile.SERVICE_V1.claimValue())
                            .claim(TokenProfile.CONTRACT_VERSION_CLAIM, TokenProfile.CURRENT_CONTRACT_VERSION)
                            .claim("actor_type", "USER")
                            .claim("amr", "pwd")
                            .build();
                }
                if (!"missing-scope".equals(token)) {
                    jwt.claim("scope", "ai.invoke");
                }
                if (!"missing-tenant".equals(token)) {
                    jwt.claim("tenant_id", "tenant:trusted");
                }
                return jwt.build();
            };
        }
    }
}
