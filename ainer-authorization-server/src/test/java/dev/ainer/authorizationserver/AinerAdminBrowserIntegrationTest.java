package dev.ainer.authorizationserver;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AinerAuthorizationServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=dev",
                "spring.main.banner-mode=off",
                "ainer.security.authorization-server.issuer=https://auth.ainer.test",
                "ainer.admin.browser-client.enabled=true",
                "ainer.admin.browser-client.redirect-uri="
                        + "http://127.0.0.1:5173/ainer-admin/auth/callback",
                "ainer.admin.browser-client.post-logout-redirect-uri="
                        + "http://127.0.0.1:5173/ainer-admin/auth/logged-out",
                "ainer.admin.dev-bootstrap.enabled=true",
                "ainer.admin.dev-bootstrap.owner-username=admin-owner@example.com",
                "ainer.admin.dev-bootstrap.owner-password=ainer-admin-owner-2026",
                "ainer.admin.dev-bootstrap.owner-display-name=Admin Owner",
                "ainer.admin.dev-bootstrap.member-username=admin-member@example.com",
                "ainer.admin.dev-bootstrap.member-password=ainer-admin-member-2026",
                "ainer.admin.dev-bootstrap.member-display-name=Admin Member"
        })
@Import(AinerAdminBrowserIntegrationTest.TestKeyConfiguration.class)
class AinerAdminBrowserIntegrationTest {

    private static final String CLIENT_ID = "ainer-admin-dev";
    private static final String CALLBACK =
            "http://127.0.0.1:5173/ainer-admin/auth/callback";
    private static final String LOGGED_OUT =
            "http://127.0.0.1:5173/ainer-admin/auth/logged-out";
    private static final String OWNER_USERNAME = "admin-owner@example.com";
    private static final String OWNER_PASSWORD = "ainer-admin-owner-2026";
    private static final String MEMBER_USERNAME = "admin-member@example.com";
    private static final String VERIFIER =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-._~";
    private static final String SCOPES =
            "openid profile tenant.members.read tenant.members.write";
    private static final Pattern CSRF_INPUT = Pattern.compile(
            "<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"[^>]*>");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_admin_browser_test")
            .withUsername("ainer")
            .withPassword("ainer");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void browserClientCompletesPkceMemberLifecycleRevocationAndLogout() throws Exception {
        assertBootstrappedBrowserClient();
        UUID tenantId = fixtureTenantId();
        UUID ownerSubjectId = fixtureSubjectId(OWNER_USERNAME);
        UUID memberSubjectId = fixtureSubjectId(MEMBER_USERNAME);

        BrowserSession browser = newBrowser();
        AuthorizationResult authorization = authorize(browser);
        TokenSet tokens = exchange(browser, authorization.code());

        Jwt accessToken = jwtDecoder.decode(tokens.accessToken());
        assertThat(accessToken.getSubject()).isEqualTo(ownerSubjectId.toString());
        assertThat(accessToken.getClaimAsString("actor_type")).isEqualTo("USER");
        assertThat(accessToken.getClaimAsString("tenant_id")).isEqualTo(tenantId.toString());
        assertThat(accessToken.getClaimAsStringList("roles")).containsExactly("OWNER");
        assertThat(tokens.scope()).contains(
                "openid", "profile", "tenant.members.read", "tenant.members.write");
        assertThat(tokens.refreshToken()).isNull();

        JsonNode initialMembers = body(api(
                browser,
                "GET",
                "/api/tenants/" + tenantId + "/members",
                tokens.accessToken(),
                null,
                "admin-e2e-list"));
        assertThat(initialMembers.path("data").path("total").intValue()).isEqualTo(1);
        assertThat(initialMembers.path("data").path("members").get(0).path("role").stringValue())
                .isEqualTo("OWNER");

        JsonNode added = body(api(
                browser,
                "POST",
                "/api/tenants/" + tenantId + "/members",
                tokens.accessToken(),
                Map.of(
                        "username", MEMBER_USERNAME,
                        "role", "MEMBER",
                        "reasonCode", "admin-e2e-add"),
                "admin-e2e-add"));
        assertMember(added, memberSubjectId, "MEMBER", "admin-e2e-add");

        JsonNode promoted = body(api(
                browser,
                "PATCH",
                "/api/tenants/" + tenantId + "/members/" + memberSubjectId,
                tokens.accessToken(),
                Map.of("role", "ADMIN", "reasonCode", "admin-e2e-promote"),
                "admin-e2e-promote"));
        assertMember(promoted, memberSubjectId, "ADMIN", "admin-e2e-promote");

        JsonNode restored = body(api(
                browser,
                "PATCH",
                "/api/tenants/" + tenantId + "/members/" + memberSubjectId,
                tokens.accessToken(),
                Map.of("role", "MEMBER", "reasonCode", "admin-e2e-restore"),
                "admin-e2e-restore"));
        assertMember(restored, memberSubjectId, "MEMBER", "admin-e2e-restore");

        HttpResponse<String> removed = api(
                browser,
                "DELETE",
                "/api/tenants/" + tenantId + "/members/" + memberSubjectId
                        + "?reasonCode=admin-e2e-remove",
                tokens.accessToken(),
                null,
                "admin-e2e-remove");
        assertThat(removed.statusCode()).isEqualTo(200);
        assertThat(body(removed).path("data").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM ainer_identity_membership
                WHERE tenant_id = ? AND user_id = ?
                """,
                String.class,
                tenantId,
                memberSubjectId)).isEqualTo("DISABLED");
        assertThat(jdbcTemplate.queryForList(
                """
                SELECT operation
                FROM ainer_identity_member_audit
                WHERE tenant_id = ? AND target_subject_id = ?
                ORDER BY occurred_at, id
                """,
                String.class,
                tenantId,
                memberSubjectId)).containsExactly(
                        "ADDED", "ROLE_CHANGED", "ROLE_CHANGED", "REMOVED");

        JsonNode revoked = body(api(
                browser,
                "POST",
                "/api/me/access-token-revocations",
                tokens.accessToken(),
                Map.of(),
                "admin-e2e-revoke"));
        assertThat(revoked.path("data").path("revoked").booleanValue()).isTrue();

        HttpResponse<String> rejected = api(
                browser,
                "GET",
                "/api/tenants/" + tenantId + "/members",
                tokens.accessToken(),
                null,
                "admin-e2e-after-revoke");
        assertThat(rejected.statusCode()).isEqualTo(401);
        assertThat(objectMapper.readTree(rejected.body()).path("code").stringValue())
                .isEqualTo("AINER.COMMON.UNAUTHENTICATED");

        OAuth2Authorization logoutAuthorization = authorizationService.findByToken(
                tokens.idToken(),
                new OAuth2TokenType("id_token"));
        assertThat(logoutAuthorization).isNotNull();
        assertThat(logoutAuthorization.getToken(
                org.springframework.security.oauth2.core.oidc.OidcIdToken.class).isActive())
                .isTrue();
        logout(browser, tokens.idToken());
        assertBrowserSessionLoggedOut(browser);
    }

    private void assertBootstrappedBrowserClient() {
        RegisteredClient client = registeredClientRepository.findByClientId(CLIENT_ID);
        assertThat(client).isNotNull();
        assertThat(client.getClientSecret()).isNull();
        assertThat(client.getRedirectUris()).containsExactly(CALLBACK);
        assertThat(client.getPostLogoutRedirectUris()).containsExactly(LOGGED_OUT);
        assertThat(client.getScopes()).containsExactlyInAnyOrderElementsOf(
                Set.of("openid", "profile", "tenant.members.read", "tenant.members.write"));
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
    }

    private UUID fixtureTenantId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM ainer_identity_tenant WHERE code = 'ainer-admin-dev'",
                UUID.class);
    }

    private UUID fixtureSubjectId(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM ainer_identity_user WHERE username = ?",
                UUID.class,
                username);
    }

    private AuthorizationResult authorize(BrowserSession browser) throws Exception {
        String state = UUID.randomUUID().toString();
        String nonce = UUID.randomUUID().toString();
        URI authorizationUri = authorizationUri(state, nonce);
        HttpResponse<String> unauthenticated = htmlGet(browser.client(), authorizationUri);
        assertThat(unauthenticated.statusCode()).isEqualTo(302);
        URI loginUri = authorizationUri.resolve(
                unauthenticated.headers().firstValue("Location").orElseThrow());
        assertThat(loginUri.getPath()).isEqualTo("/login");

        HttpResponse<String> loginPage = htmlGet(browser.client(), loginUri);
        assertThat(loginPage.statusCode()).isEqualTo(200);
        Matcher csrf = CSRF_INPUT.matcher(loginPage.body());
        assertThat(csrf.find()).isTrue();

        HttpResponse<String> loggedIn = browser.client().send(
                HttpRequest.newBuilder()
                        .uri(localUri("/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form(Map.of(
                                "username", OWNER_USERNAME,
                                "password", OWNER_PASSWORD,
                                "_csrf", csrf.group(1)))))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(loggedIn.statusCode()).isEqualTo(302);

        URI resumedAuthorization = loginUri.resolve(
                loggedIn.headers().firstValue("Location").orElseThrow());
        HttpResponse<String> authorized = htmlGet(browser.client(), resumedAuthorization);
        assertThat(authorized.statusCode()).isEqualTo(302);
        URI callback = URI.create(authorized.headers().firstValue("Location").orElseThrow());
        assertThat(callback.getScheme()).isEqualTo("http");
        assertThat(callback.getHost()).isEqualTo("127.0.0.1");
        assertThat(callback.getPort()).isEqualTo(5173);
        assertThat(callback.getPath()).isEqualTo("/ainer-admin/auth/callback");
        Map<String, String> callbackParameters = queryParameters(callback);
        assertThat(callbackParameters.get("state")).isEqualTo(state);
        assertThat(callbackParameters).containsKey("code").doesNotContainKey("error");
        return new AuthorizationResult(callbackParameters.get("code"));
    }

    private TokenSet exchange(BrowserSession browser, String code) throws Exception {
        HttpResponse<String> response = browser.client().send(
                HttpRequest.newBuilder()
                        .uri(localUri("/oauth2/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form(Map.of(
                                "grant_type", "authorization_code",
                                "client_id", CLIENT_ID,
                                "redirect_uri", CALLBACK,
                                "code", code,
                                "code_verifier", VERIFIER))))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("token_type").stringValue()).isEqualToIgnoringCase("Bearer");
        assertThat(body.path("id_token").stringValue()).isNotBlank();
        assertThat(body.has("refresh_token")).isFalse();
        return new TokenSet(
                body.path("access_token").stringValue(),
                body.path("id_token").stringValue(),
                body.path("scope").stringValue(),
                body.has("refresh_token") ? body.path("refresh_token").stringValue() : null);
    }

    private HttpResponse<String> api(
            BrowserSession browser,
            String method,
            String path,
            String accessToken,
            Object requestBody,
            String requestId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(localUri(path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Request-Id", requestId);
        if (requestBody == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(
                            method,
                            HttpRequest.BodyPublishers.ofString(
                                    objectMapper.writeValueAsString(requestBody)));
        }
        return browser.client().send(
                request.build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private void assertMember(
            JsonNode response,
            UUID subjectId,
            String role,
            String requestId) {
        assertThat(response.path("requestId").stringValue()).isEqualTo(requestId);
        assertThat(response.path("data").path("subjectId").stringValue())
                .isEqualTo(subjectId.toString());
        assertThat(response.path("data").path("role").stringValue()).isEqualTo(role);
    }

    private void logout(BrowserSession browser, String idToken) throws Exception {
        String state = UUID.randomUUID().toString();
        URI logoutUri = localUri("/connect/logout?" + form(Map.of(
                "id_token_hint", idToken,
                "post_logout_redirect_uri", LOGGED_OUT,
                "state", state)));
        HttpResponse<String> response = htmlGet(browser.client(), logoutUri);
        assertThat(response.statusCode())
                .withFailMessage(
                        "OIDC logout failed: headers=%s body=%s",
                        response.headers().map(),
                        response.body())
                .isEqualTo(302);
        URI callback = URI.create(response.headers().firstValue("Location").orElseThrow());
        assertThat(callback.getScheme()).isEqualTo("http");
        assertThat(callback.getHost()).isEqualTo("127.0.0.1");
        assertThat(callback.getPort()).isEqualTo(5173);
        assertThat(callback.getPath()).isEqualTo("/ainer-admin/auth/logged-out");
        assertThat(queryParameters(callback).get("state")).isEqualTo(state);
    }

    private void assertBrowserSessionLoggedOut(BrowserSession browser) throws Exception {
        HttpResponse<String> response = htmlGet(
                browser.client(),
                authorizationUri(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        assertThat(response.statusCode()).isEqualTo(302);
        URI redirected = localUri("/oauth2/authorize").resolve(
                response.headers().firstValue("Location").orElseThrow());
        assertThat(redirected.getPath()).isEqualTo("/login");
    }

    private URI authorizationUri(String state, String nonce) throws Exception {
        return localUri("/oauth2/authorize?" + form(Map.of(
                "response_type", "code",
                "client_id", CLIENT_ID,
                "redirect_uri", CALLBACK,
                "scope", SCOPES,
                "state", state,
                "nonce", nonce,
                "code_challenge", challenge(VERIFIER),
                "code_challenge_method", "S256")));
    }

    private HttpResponse<String> htmlGet(HttpClient client, URI uri) throws Exception {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(uri)
                        .header("Accept", "text/html,application/xhtml+xml")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private BrowserSession newBrowser() {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        return new BrowserSession(HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    private URI localUri(String path) {
        return URI.create("http://127.0.0.1:%d%s".formatted(port, path));
    }

    private String challenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private Map<String, String> queryParameters(URI uri) {
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        if (uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
            return parameters;
        }
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            parameters.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length == 2
                            ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                            : "");
        }
        return parameters;
    }

    private record BrowserSession(HttpClient client) {
    }

    private record AuthorizationResult(String code) {
    }

    private record TokenSet(
            String accessToken,
            String idToken,
            String scope,
            String refreshToken) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestKeyConfiguration {

        @Bean
        JWKSource<SecurityContext> testJwkSource() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID("ainer-admin-browser-test-key")
                    .build();
            return new ImmutableJWKSet<>(new JWKSet(rsaKey));
        }
    }
}
