package dev.ainer.authorizationserver;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.module.identity.account.application.ProvisionTenantOwnerCommand;
import dev.ainer.module.identity.account.application.ProvisionedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
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
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AinerAuthorizationServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.security.authorization-server.issuer=https://auth.ainer.test",
                "ainer.security.authorization-server.passkey.enabled=true",
                "ainer.security.authorization-server.passkey.rp-id=localhost",
                "ainer.security.authorization-server.passkey.rp-name=Ainer Test",
                "ainer.security.authorization-server.passkey.allowed-origins=http://localhost",
                "ainer.security.authorization-server.passkey.allow-insecure-http=true",
                "spring.main.banner-mode=off"
        })
@Import(AinerAuthorizationCodePkceIntegrationTest.TestKeyConfiguration.class)
class AinerAuthorizationCodePkceIntegrationTest {

    private static final String CLIENT_ID = "ainer-browser-pkce-test";
    private static final String REDIRECT_URI = "https://client.ainer.test/callback";
    private static final String USERNAME = "pkce-user@example.com";
    private static final String PASSWORD = "strong-password-2026";
    private static final String VERIFIER =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-._~";
    private static final String WRONG_VERIFIER =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyz~_.-";
    private static final Pattern CSRF_INPUT = Pattern.compile(
            "<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"[^>]*>");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_pkce_test")
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
    private JwtDecoder jwtDecoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PublicKeyCredentialUserEntityRepository userEntityRepository;

    @Autowired
    private UserCredentialRepository userCredentialRepository;

    private ProvisionedIdentity identity;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ainer_passkey_credential_audit");
        jdbcTemplate.update("DELETE FROM ainer_passkey_credential");
        jdbcTemplate.update("DELETE FROM user_credentials");
        jdbcTemplate.update("DELETE FROM user_entities");
        jdbcTemplate.update("DELETE FROM ainer_oauth_service_client_audit");
        jdbcTemplate.update("DELETE FROM ainer_oauth_service_client");
        jdbcTemplate.update("DELETE FROM oauth2_authorization_consent");
        jdbcTemplate.update("DELETE FROM oauth2_authorization");
        jdbcTemplate.update("DELETE FROM oauth2_registered_client");
        jdbcTemplate.update("DELETE FROM ainer_identity_access_event");
        jdbcTemplate.update("DELETE FROM ainer_identity_tenant");
        jdbcTemplate.update("DELETE FROM ainer_identity_user");
        identity = identityService.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "pkce-tenant",
                "PKCE Tenant",
                USERNAME,
                PASSWORD,
                "PKCE User"));
        registeredClientRepository.save(browserClient());
    }

    @Test
    void s256AuthorizationCodeLoginIssuesUserTokensAndCodeCannotBeReplayed() throws Exception {
        BrowserSession browser = newBrowser();
        String code = authorize(browser, VERIFIER, REDIRECT_URI);
        assertAuthorizationDoesNotPersistCredentials(code);

        HttpResponse<String> tokenResponse = exchange(code, VERIFIER, REDIRECT_URI);

        assertThat(tokenResponse.statusCode()).isEqualTo(200);
        JsonNode tokenBody = objectMapper.readTree(tokenResponse.body());
        assertThat(tokenBody.path("token_type").stringValue()).isEqualToIgnoringCase("Bearer");
        assertThat(tokenBody.path("scope").stringValue()).contains("openid", "workspace.read");
        assertThat(tokenBody.path("id_token").stringValue()).isNotBlank();
        assertThat(tokenBody.has("refresh_token")).isFalse();
        Jwt accessToken = jwtDecoder.decode(tokenBody.path("access_token").stringValue());
        assertThat(accessToken.getSubject()).isEqualTo(identity.subjectId().toString());
        assertThat(accessToken.getClaimAsString("actor_type")).isEqualTo("USER");
        assertThat(accessToken.getClaimAsString("tenant_id"))
                .isEqualTo(identity.tenantId().toString());
        assertThat(accessToken.getClaimAsStringList("roles")).contains("OWNER");
        assertThat(accessToken.getClaimAsStringList("amr")).containsExactly("pwd");
        assertThat(accessToken.getClaimAsInstant("auth_time")).isNotNull();

        HttpResponse<String> replay = exchange(code, VERIFIER, REDIRECT_URI);
        assertThat(replay.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(replay.body()).path("error").stringValue())
                .isEqualTo("invalid_grant");
    }

    @Test
    void wrongVerifierCannotExchangeAuthorizationCode() throws Exception {
        BrowserSession browser = newBrowser();
        String code = authorize(browser, VERIFIER, REDIRECT_URI);

        HttpResponse<String> response = exchange(code, WRONG_VERIFIER, REDIRECT_URI);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).path("error").stringValue())
                .isEqualTo("invalid_grant");
    }

    @Test
    void missingOrPlainChallengeAndUnregisteredRedirectAreRejected() throws Exception {
        BrowserSession browser = newBrowser();
        authorize(browser, VERIFIER, REDIRECT_URI);
        HttpClient client = browser.client();

        HttpResponse<String> missing = sendGet(client, authorizationUri(
                REDIRECT_URI, null, null, "missing-challenge"));
        assertTrustedAuthorizationError(missing, "invalid_request");

        HttpResponse<String> plain = sendGet(client, authorizationUri(
                REDIRECT_URI, VERIFIER, "plain", "plain-challenge"));
        assertTrustedAuthorizationError(plain, "invalid_request");

        String unregisteredRedirect = "https://evil.example/callback";
        HttpResponse<String> redirectMismatch = sendGet(client, authorizationUri(
                unregisteredRedirect, challenge(VERIFIER), "S256", "bad-redirect"));
        assertThat(redirectMismatch.statusCode()).isEqualTo(400);
        assertThat(redirectMismatch.headers().firstValue("Location"))
                .isEmpty();
        assertThat(redirectMismatch.body()).doesNotContain(unregisteredRedirect);
    }

    @Test
    void registrationOptionsRequireUserVerificationAndUseConfiguredRelyingParty()
            throws Exception {
        BrowserSession browser = newBrowser();
        String csrf = login(browser);

        HttpResponse<String> response = browser.client().send(
                HttpRequest.newBuilder()
                        .uri(localUri("/webauthn/register/options"))
                        .header("Content-Type", "application/json")
                        .header("X-CSRF-TOKEN", csrf)
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("rp").path("id").stringValue()).isEqualTo("localhost");
        assertThat(body.path("rp").path("name").stringValue()).isEqualTo("Ainer Test");
        assertThat(body.path("authenticatorSelection").path("residentKey").stringValue())
                .isEqualTo("required");
        assertThat(body.path("authenticatorSelection").path("userVerification").stringValue())
                .isEqualTo("required");
        assertThat(body.path("timeout").longValue()).isEqualTo(300_000L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM user_entities",
                String.class)).isEqualTo(USERNAME);
    }

    @Test
    void credentialLifecycleIsAuditedUpdatedAndSoftRevoked() {
        CredentialRecord credential = credential();
        CredentialRecord replacement = credential();

        userCredentialRepository.save(credential);
        userCredentialRepository.save(replacement);
        CredentialRecord updated = ImmutableCredentialRecord.fromCredentialRecord(credential)
                .signatureCount(7)
                .lastUsed(credential.getLastUsed().plusSeconds(30))
                .build();
        userCredentialRepository.save(updated);

        assertThat(userCredentialRepository.findByCredentialId(
                credential.getCredentialId()).getSignatureCount()).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_passkey_credential WHERE credential_id = ?",
                String.class,
                credential.getCredentialId().toBase64UrlString())).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_passkey_credential_audit",
                Integer.class)).isEqualTo(2);

        userCredentialRepository.delete(credential.getCredentialId());

        assertThat(userCredentialRepository.findByCredentialId(
                credential.getCredentialId())).isNull();
        assertThat(userCredentialRepository.findByUserId(
                credential.getUserEntityUserId()))
                .extracting(CredentialRecord::getCredentialId)
                .containsExactly(replacement.getCredentialId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_credentials",
                Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_passkey_credential WHERE credential_id = ?",
                String.class,
                credential.getCredentialId().toBase64UrlString())).isEqualTo("REVOKED");
        assertThat(jdbcTemplate.queryForList(
                """
                SELECT operation
                FROM ainer_passkey_credential_audit
                ORDER BY occurred_at, id
                """,
                String.class)).containsExactly("REGISTERED", "REGISTERED", "REVOKED");
        assertThatThrownBy(() -> userCredentialRepository.delete(
                replacement.getCredentialId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("last active passkey");
    }

    @Test
    void registeredCredentialRequiresPasskeyBeforeIssuingAuthorizationCode()
            throws Exception {
        userCredentialRepository.save(credential());
        BrowserSession browser = newBrowser();
        String state = UUID.randomUUID().toString();
        URI requestUri = authorizationUri(
                REDIRECT_URI, challenge(VERIFIER), "S256", state);
        HttpResponse<String> unauthenticated = sendGet(browser.client(), requestUri);
        URI loginUri = resolve(requestUri, unauthenticated.headers()
                .firstValue("Location").orElseThrow());
        HttpResponse<String> loginPage = sendGet(browser.client(), loginUri);
        Matcher csrf = CSRF_INPUT.matcher(loginPage.body());
        assertThat(csrf.find()).isTrue();
        HttpResponse<String> loggedIn = postLogin(browser, csrf.group(1));

        URI resumedAuthorization = resolve(loginUri, loggedIn.headers()
                .firstValue("Location").orElseThrow());
        HttpResponse<String> missingPasskey = sendGet(
                browser.client(), resumedAuthorization);

        assertThat(missingPasskey.statusCode()).isEqualTo(302);
        URI factorLogin = resolve(
                resumedAuthorization,
                missingPasskey.headers().firstValue("Location").orElseThrow());
        assertThat(factorLogin.getPath()).isEqualTo("/login");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth2_authorization",
                Integer.class)).isZero();
    }

    @Test
    void concurrentRemovalCannotRevokeBothActiveCredentials() throws Exception {
        CredentialRecord first = credential();
        CredentialRecord second = credential();
        userCredentialRepository.save(first);
        userCredentialRepository.save(second);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstRemoval = executor.submit(() ->
                    removeAfterBarrier(first.getCredentialId(), ready, start));
            var secondRemoval = executor.submit(() ->
                    removeAfterBarrier(second.getCredentialId(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    firstRemoval.get(5, TimeUnit.SECONDS),
                    secondRemoval.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ainer_passkey_credential
                WHERE status = 'ACTIVE'
                """,
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ainer_passkey_credential
                WHERE status = 'REVOKED'
                """,
                Integer.class)).isEqualTo(1);
    }

    private RegisteredClient browserClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(CLIENT_ID)
                .clientName("Ainer browser PKCE integration test")
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

    private String login(BrowserSession browser) throws Exception {
        HttpResponse<String> loginPage = sendGet(browser.client(), localUri("/login"));
        assertThat(loginPage.statusCode()).isEqualTo(200);
        Matcher csrf = CSRF_INPUT.matcher(loginPage.body());
        assertThat(csrf.find()).isTrue();
        HttpResponse<String> loggedIn = postLogin(browser, csrf.group(1));
        assertThat(loggedIn.statusCode()).isEqualTo(302);
        HttpResponse<String> authenticatedPage =
                sendGet(browser.client(), localUri("/login"));
        Matcher authenticatedCsrf = CSRF_INPUT.matcher(authenticatedPage.body());
        assertThat(authenticatedCsrf.find()).isTrue();
        return authenticatedCsrf.group(1);
    }

    private HttpResponse<String> postLogin(BrowserSession browser, String csrf)
            throws Exception {
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

    private String authorize(BrowserSession browser, String verifier, String redirectUri)
            throws Exception {
        String state = UUID.randomUUID().toString();
        URI requestUri = authorizationUri(
                redirectUri, challenge(verifier), "S256", state);
        HttpResponse<String> unauthenticated = sendGet(browser.client(), requestUri);
        assertThat(unauthenticated.statusCode()).isEqualTo(302);
        URI loginUri = resolve(requestUri, unauthenticated.headers()
                .firstValue("Location").orElseThrow());
        assertThat(loginUri.getPath()).isEqualTo("/login");

        HttpResponse<String> loginPage = sendGet(browser.client(), loginUri);
        assertThat(loginPage.statusCode()).isEqualTo(200);
        Matcher csrf = CSRF_INPUT.matcher(loginPage.body());
        assertThat(csrf.find()).isTrue();

        HttpResponse<String> loggedIn = postLogin(browser, csrf.group(1));
        assertThat(loggedIn.statusCode()).isEqualTo(302);

        URI resumedAuthorization = resolve(loginUri, loggedIn.headers()
                .firstValue("Location").orElseThrow());
        HttpResponse<String> authorized = sendGet(browser.client(), resumedAuthorization);
        assertThat(authorized.statusCode()).isEqualTo(302);
        URI callback = URI.create(authorized.headers().firstValue("Location").orElseThrow());
        assertThat(callback.getScheme()).isEqualTo("https");
        assertThat(callback.getHost()).isEqualTo("client.ainer.test");
        assertThat(callback.getPath()).isEqualTo("/callback");
        Map<String, String> callbackParameters = queryParameters(callback);
        assertThat(callbackParameters.get("state")).isEqualTo(state);
        assertThat(callbackParameters).containsKey("code").doesNotContainKey("error");
        return callbackParameters.get("code");
    }

    private HttpResponse<String> exchange(String code, String verifier, String redirectUri)
            throws Exception {
        String body = form(Map.of(
                "grant_type", "authorization_code",
                "client_id", CLIENT_ID,
                "redirect_uri", redirectUri,
                "code", code,
                "code_verifier", verifier));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(localUri("/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertAuthorizationDoesNotPersistCredentials(String code) {
        String attributes = jdbcTemplate.queryForObject(
                """
                SELECT attributes
                FROM oauth2_authorization
                WHERE authorization_code_value = ?
                """,
                String.class,
                code);
        assertThat(attributes)
                .doesNotContain(PASSWORD)
                .doesNotContain("\"password\"");
    }

    private URI authorizationUri(
            String redirectUri,
            String codeChallenge,
            String codeChallengeMethod,
            String state) {
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("response_type", "code");
        parameters.put("client_id", CLIENT_ID);
        parameters.put("redirect_uri", redirectUri);
        parameters.put("scope", "openid profile workspace.read");
        parameters.put("state", state);
        parameters.put("nonce", UUID.randomUUID().toString());
        if (codeChallenge != null) {
            parameters.put("code_challenge", codeChallenge);
        }
        if (codeChallengeMethod != null) {
            parameters.put("code_challenge_method", codeChallengeMethod);
        }
        return localUri("/oauth2/authorize?" + form(parameters));
    }

    private void assertTrustedAuthorizationError(
            HttpResponse<String> response,
            String expectedError) {
        assertThat(response.statusCode()).isEqualTo(302);
        URI callback = URI.create(response.headers().firstValue("Location").orElseThrow());
        assertThat(callback.toString()).startsWith(REDIRECT_URI);
        assertThat(queryParameters(callback).get("error")).isEqualTo(expectedError);
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
            String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2
                    ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                    : "";
            parameters.put(name, value);
        }
        return parameters;
    }

    private CredentialRecord credential() {
        var existingUserEntity = userEntityRepository.findByUsername(USERNAME);
        Bytes userEntityId;
        if (existingUserEntity == null) {
            userEntityId = Bytes.random();
            userEntityRepository.save(ImmutablePublicKeyCredentialUserEntity.builder()
                    .id(userEntityId)
                    .name(USERNAME)
                    .displayName("PKCE User")
                    .build());
        } else {
            userEntityId = existingUserEntity.getId();
        }
        return ImmutableCredentialRecord.builder()
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .credentialId(Bytes.random())
                .userEntityUserId(userEntityId)
                .publicKey(new ImmutablePublicKeyCose(new byte[] {1, 2, 3, 4}))
                .signatureCount(0)
                .uvInitialized(true)
                .transports(Set.of(AuthenticatorTransport.INTERNAL))
                .backupEligible(false)
                .backupState(false)
                .attestationObject(new Bytes(new byte[] {5, 6, 7}))
                .attestationClientDataJSON(new Bytes(new byte[] {8, 9}))
                .label("test-passkey")
                .build();
    }

    private boolean removeAfterBarrier(
            Bytes credentialId,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Passkey removal barrier timed out");
        }
        try {
            userCredentialRepository.delete(credentialId);
            return true;
        } catch (AccessDeniedException exception) {
            return false;
        }
    }

    private record BrowserSession(HttpClient client) {
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
                    .keyID("pkce-test-key")
                    .build();
            return new ImmutableJWKSet<>(new JWKSet(rsaKey));
        }
    }
}
