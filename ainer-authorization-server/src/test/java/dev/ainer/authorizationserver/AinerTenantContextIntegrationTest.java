package dev.ainer.authorizationserver;

import dev.ainer.module.identity.account.application.AddTenantMemberCommand;
import dev.ainer.module.identity.account.application.TenantMemberManagementService;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.module.identity.account.application.ProvisionTenantOwnerCommand;
import dev.ainer.module.identity.account.application.ProvisionedIdentity;
import dev.ainer.module.identity.account.domain.TenantRole;
import dev.ainer.security.actor.AuthenticatedActor;
import dev.ainer.security.AinerSecurityScopes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4.8B 租户上下文选择集成测试：{@code GET /api/me/tenants} 与多租户 authorization 选择流程。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AinerAuthorizationServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.security.authorization-server.issuer=https://auth.ainer.test",
                "spring.main.banner-mode=off"
        })
@Import(AinerAuthorizationCodePkceIntegrationTest.TestKeyConfiguration.class)
class AinerTenantContextIntegrationTest {

    private static final String CLIENT_ID = "ainer-tenant-context-test";
    private static final String REDIRECT_URI = "https://client.ainer.test/callback";
    private static final String USERNAME = "multi@example.com";
    private static final String PASSWORD = "strong-password-2026";
    private static final String VERIFIER =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-._~";
    private static final Pattern CSRF_INPUT = Pattern.compile(
            "<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"[^>]*>");
    private static final Pattern TENANT_RADIO = Pattern.compile(
            "<input[^>]*name=\"tenantId\"[^>]*value=\"([^\"]+)\"([^>]*)>");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_tenant_context_test")
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
    private IdentityApplicationService identityService;

    @Autowired
    private TenantMemberManagementService memberService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private ProvisionedIdentity primaryIdentity;
    private ProvisionedIdentity secondaryTenant;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM oauth2_authorization_consent");
        jdbcTemplate.update("DELETE FROM oauth2_authorization");
        jdbcTemplate.update("DELETE FROM oauth2_registered_client");
        jdbcTemplate.update("DELETE FROM ainer_identity_member_audit");
        jdbcTemplate.update("DELETE FROM ainer_identity_access_event");
        jdbcTemplate.update("DELETE FROM ainer_identity_tenant");
        jdbcTemplate.update("DELETE FROM ainer_identity_user");

        primaryIdentity = identityService.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "primary", "Primary Tenant", USERNAME, PASSWORD, "Multi User"));
        secondaryTenant = identityService.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "secondary", "Secondary Tenant",
                "secondary-owner@example.com", PASSWORD, "Secondary Owner"));
        registeredClientRepository.save(browserClient());
    }

    @Test
    void getMyTenantsReturnsActiveMembershipsForSingleTenantUser() throws Exception {
        BrowserSession browser = newBrowser();
        String accessToken = loginAndAuthorize(browser, VERIFIER);

        HttpResponse<String> response = browser.client().send(
                HttpRequest.newBuilder()
                        .uri(localUri("/api/me/tenants"))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept", "application/json")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = new ObjectMapper().readTree(response.body());
        JsonNode data = body.path("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data).hasSize(1);
        assertThat(data.get(0).path("tenantCode").asString()).isEqualTo("primary");
        assertThat(data.get(0).path("role").asString()).isEqualTo("OWNER");
        assertThat(data.get(0).path("defaultTenant").asBoolean()).isTrue();
    }

    @Test
    void getMyTenantsReturnsMultipleMembershipsForMultiTenantUser() throws Exception {
        addPrimaryUserToSecondaryTenant();

        BrowserSession browser = newBrowser();
        String accessToken = loginAndSelectTenant(browser, primaryIdentity.tenantId());

        HttpResponse<String> response = browser.client().send(
                HttpRequest.newBuilder()
                        .uri(localUri("/api/me/tenants"))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept", "application/json")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = new ObjectMapper().readTree(response.body());
        JsonNode data = body.path("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data).hasSize(2);
        assertThat(data.get(0).path("tenantCode").asString()).isEqualTo("primary");
        assertThat(data.get(1).path("tenantCode").asString()).isEqualTo("secondary");
    }

    @Test
    void multiTenantUserSelectsSecondaryTenantAndTokenCarriesCorrectTenant() throws Exception {
        addPrimaryUserToSecondaryTenant();

        BrowserSession browser = newBrowser();
        String accessToken = loginAndSelectTenant(browser, secondaryTenant.tenantId());

        org.springframework.security.oauth2.jwt.Jwt decoded = jwtDecoder.decode(accessToken);

        assertThat(decoded.getClaimAsString("tenant_id")).isEqualTo(
                secondaryTenant.tenantId().toString());
        assertThat(decoded.getClaimAsString("actor_type")).isEqualTo("USER");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("MEMBER");
    }

    @Test
    void multiTenantUserDefaultsToPrimaryWhenSelectingDefaultTenant() throws Exception {
        addPrimaryUserToSecondaryTenant();

        BrowserSession browser = newBrowser();
        String accessToken = loginAndSelectTenant(browser, primaryIdentity.tenantId());

        org.springframework.security.oauth2.jwt.Jwt decoded = jwtDecoder.decode(accessToken);
        assertThat(decoded.getClaimAsString("tenant_id")).isEqualTo(
                primaryIdentity.tenantId().toString());
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("OWNER");
    }

    @Test
    void getMyTenantsRejectsClientCredentialsToken() throws Exception {
        RegisteredClient ccClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("cc-test-client")
                .clientSecret(passwordEncoder.encode("test-secret-value"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("workspace.read")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .build())
                .build();
        registeredClientRepository.save(ccClient);

        String token = clientCredentialsToken("cc-test-client", "test-secret-value");

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(localUri("/api/me/tenants"))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
    }

    private void addPrimaryUserToSecondaryTenant() {
        AuthenticatedActor secondaryOwner = new AuthenticatedActor(
                secondaryTenant.subjectId().toString(),
                secondaryTenant.tenantId().toString(),
                AuthenticatedActor.USER,
                java.util.Set.of(
                        "SCOPE_" + AinerSecurityScopes.TENANT_MEMBERS_READ,
                        "SCOPE_" + AinerSecurityScopes.TENANT_MEMBERS_WRITE));
        memberService.addMember(secondaryOwner, secondaryTenant.tenantId(),
                new AddTenantMemberCommand(
                        USERNAME, null, TenantRole.MEMBER, "cross-tenant"),
                "req-cross-1");
    }

    private String loginAndAuthorize(BrowserSession browser, String verifier) throws Exception {
        String state = UUID.randomUUID().toString();
        URI requestUri = authorizationUri(challenge(verifier), "S256", state);
        HttpResponse<String> unauthenticated = sendGet(browser.client(), requestUri);
        assertThat(unauthenticated.statusCode()).isEqualTo(302);
        URI loginUri = resolve(requestUri, unauthenticated.headers()
                .firstValue("Location").orElseThrow());
        HttpResponse<String> loginPage = sendGet(browser.client(), loginUri);
        Matcher csrf = CSRF_INPUT.matcher(loginPage.body());
        assertThat(csrf.find()).isTrue();
        HttpResponse<String> loggedIn = postLogin(browser, csrf.group(1));
        URI resumed = resolve(loginUri, loggedIn.headers()
                .firstValue("Location").orElseThrow());
        HttpResponse<String> authorized = sendGet(browser.client(), resumed);
        assertThat(authorized.statusCode()).isEqualTo(302);
        URI callback = URI.create(authorized.headers().firstValue("Location").orElseThrow());
        String code = queryParameters(callback).get("code");
        return exchangeCode(code, verifier);
    }

    private String loginAndSelectTenant(BrowserSession browser, UUID selectedTenantId)
            throws Exception {
        String state = UUID.randomUUID().toString();
        URI requestUri = authorizationUri(challenge(VERIFIER), "S256", state);
        HttpResponse<String> unauthenticated = sendGet(browser.client(), requestUri);
        URI loginUri = resolve(requestUri, unauthenticated.headers()
                .firstValue("Location").orElseThrow());
        sendGet(browser.client(), loginUri);
        Matcher csrf = CSRF_INPUT.matcher(sendGet(browser.client(), loginUri).body());
        assertThat(csrf.find()).isTrue();
        postLogin(browser, csrf.group(1));

        // After login, the authorize request should redirect to /select-tenant
        HttpResponse<String> authorizeRedirect = sendGet(browser.client(), requestUri);
        assertThat(authorizeRedirect.statusCode()).isEqualTo(302);
        URI selectUri = resolve(requestUri, authorizeRedirect.headers()
                .firstValue("Location").orElseThrow());
        assertThat(selectUri.getPath()).isEqualTo("/select-tenant");

        // Load the selection page
        HttpResponse<String> selectPage = sendGet(browser.client(), selectUri);
        assertThat(selectPage.statusCode()).isEqualTo(200);
        Matcher selectCsrf = CSRF_INPUT.matcher(selectPage.body());
        assertThat(selectCsrf.find()).isTrue();
        Matcher tenantRadio = TENANT_RADIO.matcher(selectPage.body());
        assertThat(tenantRadio.find()).isTrue();

        // Submit the selection
        String selectionForm = form(Map.of(
                "tenantId", selectedTenantId.toString(),
                "_csrf", selectCsrf.group(1)));
        HttpResponse<String> selectionResponse = browser.client().send(
                HttpRequest.newBuilder()
                        .uri(localUri("/select-tenant"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(selectionForm))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(selectionResponse.statusCode()).isEqualTo(302);

        // After selection, resume the authorization flow
        URI resumedAuth = resolve(selectUri, selectionResponse.headers()
                .firstValue("Location").orElseThrow());
        HttpResponse<String> authorized = sendGet(browser.client(), resumedAuth);
        assertThat(authorized.statusCode()).isEqualTo(302);
        URI callback = URI.create(authorized.headers().firstValue("Location").orElseThrow());
        String code = queryParameters(callback).get("code");
        return exchangeCode(code, VERIFIER);
    }

    private HttpResponse<String> exchangeForToken(BrowserSession browser, String accessToken) {
        return null;
    }

    private String exchangeCode(String code, String verifier) throws Exception {
        String body = form(Map.of(
                "grant_type", "authorization_code",
                "client_id", CLIENT_ID,
                "redirect_uri", REDIRECT_URI,
                "code", code,
                "code_verifier", verifier));
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(localUri("/oauth2/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return new ObjectMapper().readTree(response.body()).path("access_token").asString();
    }

    private String clientCredentialsToken(String clientId, String secret) throws Exception {
        String auth = clientId + ":" + secret;
        String encoded = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        String body = form(Map.of("grant_type", "client_credentials"));
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(localUri("/oauth2/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Authorization", "Basic " + encoded)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return new ObjectMapper().readTree(response.body()).path("access_token").asString();
    }

    private RegisteredClient browserClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(CLIENT_ID)
                .clientName("Ainer tenant context integration test")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI)
                .scope("openid")
                .scope("profile")
                .scope("workspace.read")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build())
                .build();
    }

    private BrowserSession newBrowser() {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new BrowserSession(client);
    }

    private HttpResponse<String> postLogin(BrowserSession browser, String csrf) throws Exception {
        String loginForm = form(Map.of(
                "username", USERNAME,
                "password", PASSWORD,
                "_csrf", csrf));
        return browser.client().send(
                HttpRequest.newBuilder()
                        .uri(localUri("/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(loginForm))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendGet(HttpClient client, URI uri) throws Exception {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(uri)
                        .header("Accept", "text/html,application/xhtml+xml")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI authorizationUri(String codeChallenge, String codeChallengeMethod, String state) {
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("response_type", "code");
        parameters.put("client_id", CLIENT_ID);
        parameters.put("redirect_uri", REDIRECT_URI);
        parameters.put("scope", "openid profile workspace.read");
        parameters.put("state", state);
        parameters.put("nonce", UUID.randomUUID().toString());
        parameters.put("code_challenge", codeChallenge);
        parameters.put("code_challenge_method", codeChallengeMethod);
        return localUri("/oauth2/authorize?" + form(parameters));
    }

    private URI localUri(String path) {
        return URI.create("http://127.0.0.1:%d%s".formatted(port, path));
    }

    private URI resolve(URI base, String location) {
        return base.resolve(location);
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
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return parameters;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String name = java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2
                    ? java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                    : "";
            parameters.put(name, value);
        }
        return parameters;
    }

    private record BrowserSession(HttpClient client) {
    }
}
