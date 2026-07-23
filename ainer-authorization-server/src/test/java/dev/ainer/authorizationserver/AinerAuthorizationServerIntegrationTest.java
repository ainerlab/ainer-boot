package dev.ainer.authorizationserver;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.ainer.authorizationserver.config.AinerAuthorizationServerConfiguration;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.module.identity.account.application.IdentityAccessLifecycleService;
import dev.ainer.module.identity.account.application.ProvisionTenantOwnerCommand;
import dev.ainer.module.identity.account.application.ProvisionedIdentity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AinerAuthorizationServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.security.authorization-server.issuer=https://auth.ainer.test",
                "ainer.identity.directory-api.enabled=true",
                "spring.main.banner-mode=off"
        })
@Import(AinerAuthorizationServerIntegrationTest.TestKeyConfiguration.class)
class AinerAuthorizationServerIntegrationTest {

    private static final String CLIENT_ID = "ainer-machine-test";
    private static final String CLIENT_SECRET = "machine-secret-2026";
    private static final String TENANT_ID = "tenant:machine-test";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_authorization_test")
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private IdentityApplicationService identityService;

    @Autowired
    private IdentityAccessLifecycleService identityAccessLifecycleService;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM oauth2_authorization_consent");
        jdbcTemplate.update("DELETE FROM oauth2_authorization");
        jdbcTemplate.update("DELETE FROM oauth2_registered_client");
        jdbcTemplate.update("DELETE FROM ainer_identity_access_event");
        jdbcTemplate.update("DELETE FROM ainer_identity_tenant");
        jdbcTemplate.update("DELETE FROM ainer_identity_user");
        registeredClientRepository.save(machineClient());
    }

    @Test
    void migratesIdentityAndOfficialJdbcProtocolStores() {
        assertThat(flyway.info().applied()).hasSize(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('oauth2_registered_client','oauth2_authorization',"
                        + "'oauth2_authorization_consent')",
                Integer.class)).isEqualTo(3);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void clientCredentialsIssuesTenantScopedJwtAndPersistsAuthorization() throws Exception {
        HttpResponse<String> response = token(CLIENT_SECRET);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("token_type").stringValue()).isEqualToIgnoringCase("Bearer");
        assertThat(body.path("scope").stringValue()).contains("ai.invoke");

        Jwt jwt = jwtDecoder.decode(body.path("access_token").stringValue());
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://auth.ainer.test");
        assertThat(jwt.getAudience()).containsExactly("ainer-api");
        assertThat(jwt.getSubject()).isEqualTo(CLIENT_ID);
        assertThat(jwt.getClaimAsString("actor_type")).isEqualTo("SERVICE");
        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo(TENANT_ID);
        assertThat(String.valueOf(jwt.getClaim("scope"))).contains("ai.invoke");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oauth2_authorization", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void invalidClientSecretCannotReceiveToken() throws Exception {
        HttpResponse<String> response = token("wrong-secret");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oauth2_authorization", Integer.class))
                .isZero();
    }

    @Test
    void onlyDedicatedClientCanIntrospectAndRfc7009RevocationMakesTokenInactive() throws Exception {
        String introspectionClientId = "ainer-introspection-test";
        registeredClientRepository.save(machineClient(
                introspectionClientId, null, true, "token.introspect"));
        String accessToken = accessToken(CLIENT_ID, "ai.invoke");

        HttpResponse<String> ordinaryClient = introspect(accessToken, CLIENT_ID);
        HttpResponse<String> active = introspect(accessToken, introspectionClientId);
        HttpResponse<String> revoked = revoke(accessToken, CLIENT_ID);
        HttpResponse<String> inactive = introspect(accessToken, introspectionClientId);

        assertThat(ordinaryClient.statusCode()).isEqualTo(401);
        assertThat(active.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(active.body()).path("active").booleanValue()).isTrue();
        assertThat(revoked.statusCode()).isEqualTo(200);
        assertThat(inactive.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(inactive.body()).path("active").booleanValue()).isFalse();
    }

    @Test
    void identityDisableMakesPreviouslyIssuedUserAuthorizationInactive() throws Exception {
        String introspectionClientId = "ainer-user-introspection-test";
        registeredClientRepository.save(machineClient(
                introspectionClientId, null, true, "token.introspect"));
        ProvisionedIdentity identity = identityService.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "introspection-user", "Introspection User", "introspection-user@example.com",
                "strong-password-2026", "Introspection User"));
        RegisteredClient tokenClient = machineClient(
                "ainer-user-token-test", identity.tenantId().toString(), false, "workspace.write");
        registeredClientRepository.save(tokenClient);
        Instant issuedAt = Instant.now().minusSeconds(5);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "user-access-token-" + UUID.randomUUID(),
                issuedAt,
                issuedAt.plus(Duration.ofMinutes(5)),
                Set.of("workspace.write"));
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(tokenClient)
                .principalName(identity.subjectId().toString())
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizedScopes(Set.of("workspace.write"))
                .token(accessToken, metadata -> metadata.put(
                        OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                        Map.of(
                                "actor_type", "USER",
                                "sub", identity.subjectId().toString(),
                                "tenant_id", identity.tenantId().toString())))
                .build();
        authorizationService.save(authorization);

        HttpResponse<String> active = introspect(accessToken.getTokenValue(), introspectionClientId);
        identityAccessLifecycleService.disableUser(identity.subjectId());
        HttpResponse<String> inactive = introspect(accessToken.getTokenValue(), introspectionClientId);

        assertThat(objectMapper.readTree(active.body()).path("active").booleanValue()).isTrue();
        assertThat(objectMapper.readTree(inactive.body()).path("active").booleanValue()).isFalse();
    }

    @Test
    void oidcDiscoveryDoesNotAdvertisePasswordGrant() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:%d/.well-known/openid-configuration".formatted(port)))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("https://auth.ainer.test")
                .doesNotContain("\"password\"");
    }

    @Test
    void directoryEnforcesServiceActorScopeAndTenantBoundary() throws Exception {
        ProvisionedIdentity first = identityService.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "directory-first", "Directory First", "first@example.com",
                "strong-password-2026", "First Owner"));
        ProvisionedIdentity second = identityService.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "directory-second", "Directory Second", "second@example.com",
                "strong-password-2026", "Second Owner"));
        String boundClientId = "directory-bound-test";
        registeredClientRepository.save(machineClient(
                boundClientId, first.tenantId().toString(), "identity.directory.read"));
        String boundToken = accessToken(boundClientId, "identity.directory.read");

        HttpResponse<String> ownTenant = directoryMember(
                first.tenantId(), first.subjectId(), boundToken);
        HttpResponse<String> otherTenant = directoryMember(
                second.tenantId(), second.subjectId(), boundToken);

        assertThat(ownTenant.statusCode()).isEqualTo(200);
        assertThat(ownTenant.body())
                .contains("\"username\":\"first@example.com\"")
                .contains("\"displayName\":\"First Owner\"")
                .doesNotContain("passwordHash")
                .doesNotContain("clientSecret");
        assertThat(otherTenant.statusCode()).isEqualTo(403);

        String platformClientId = "directory-platform-test";
        registeredClientRepository.save(machineClient(
                platformClientId, null, "identity.directory.read.all"));
        String platformToken = accessToken(platformClientId, "identity.directory.read.all");
        assertThat(directoryMember(second.tenantId(), second.subjectId(), platformToken).statusCode())
                .isEqualTo(200);

        String userToken = userToken(first);
        assertThat(directoryMember(first.tenantId(), first.subjectId(), userToken).statusCode())
                .isEqualTo(403);
    }

    private RegisteredClient machineClient() {
        return machineClient(CLIENT_ID, TENANT_ID, "ai.invoke");
    }

    private RegisteredClient machineClient(String clientId, String tenantId, String... scopes) {
        return machineClient(clientId, tenantId, false, scopes);
    }

    private RegisteredClient machineClient(
            String clientId, String tenantId, boolean introspectionAllowed, String... scopes) {
        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(CLIENT_SECRET))
                .clientName("Ainer machine integration test")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build());
        for (String scope : scopes) {
            builder.scope(scope);
        }
        ClientSettings.Builder settings = ClientSettings.builder();
        if (tenantId != null) {
            settings.setting(AinerAuthorizationServerConfiguration.CLIENT_TENANT_SETTING, tenantId);
        }
        settings.setting(
                AinerAuthorizationServerConfiguration.CLIENT_INTROSPECTION_ALLOWED_SETTING,
                introspectionAllowed);
        return builder.clientSettings(settings.build()).build();
    }

    private HttpResponse<String> token(String secret) throws Exception {
        return token(CLIENT_ID, secret, "ai.invoke");
    }

    private HttpResponse<String> token(String clientId, String secret, String scope) throws Exception {
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        String form = "grant_type=client_credentials&scope="
                + URLEncoder.encode(scope, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d/oauth2/token".formatted(port)))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String accessToken(String clientId, String scope) throws Exception {
        HttpResponse<String> response = token(clientId, CLIENT_SECRET, scope);
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body()).path("access_token").stringValue();
    }

    private HttpResponse<String> introspect(String accessToken, String clientId) throws Exception {
        return protocolPost(
                "/oauth2/introspect",
                "token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8),
                clientId);
    }

    private HttpResponse<String> revoke(String accessToken, String clientId) throws Exception {
        return protocolPost(
                "/oauth2/revoke",
                "token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8),
                clientId);
    }

    private HttpResponse<String> protocolPost(String path, String form, String clientId) throws Exception {
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d%s".formatted(port, path)))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> directoryMember(
            UUID tenantId,
            UUID subjectId,
            String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:%d/internal/identity/directory/tenants/%s/members/%s"
                        .formatted(port, tenantId, subjectId)))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String userToken(ProvisionedIdentity identity) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://auth.ainer.test")
                .subject(identity.subjectId().toString())
                .audience(List.of("ainer-api"))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(5)))
                .claim("actor_type", "USER")
                .claim("tenant_id", identity.tenantId().toString())
                .claim("scope", "identity.directory.read")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
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
                    .keyID("test-key")
                    .build();
            return new ImmutableJWKSet<>(new JWKSet(rsaKey));
        }
    }
}
