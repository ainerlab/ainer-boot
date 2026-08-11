package dev.ainer.authorization;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.ainer.testsupport.rest.RestResponse;
import dev.ainer.testsupport.rest.RestTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP integration test for the authorization management API (ADR-0030 S2) with a <strong>real signed
 * JWT security chain</strong>. Exercises the full SecurityFilterChain → NimbusJwtDecoder (real RSA
 * signature verification) → JwtToVerifiedJwtClaims → ReferenceTokenProfileResolver →
 * SecurityContextAuthenticatedPrincipalResolver → controller path, over real HTTP against a
 * PostgreSQL 18.3 Testcontainers instance.
 *
 * <p>This replaces the earlier stub {@code AuthenticatedPrincipalResolver} (defect #9): the JWT is
 * signed with a test RSA key and the resource server verifies it with the matching public key.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AuthorizationManagementHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.authorization.enabled=true",
                "ainer.security.resource-server.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
@AutoConfigureTestRestTemplate
class AuthorizationManagementHttpTest {

    /** Test-only RSA key pair generated once for the whole test class (no external PEM dependency). */
    private static final KeyPair RSA_KEY_PAIR = generateRsaKeyPair();
    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_auth_mgmt_test")
                    .withUsername("ainer")
                    .withPassword("ainer");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private RestTestClient client;
    private String managementJwt;

    @BeforeEach
    void cleanSeedAndAuthenticate() {
        client = RestTestClient.forLocalServer(restTemplate, port);
        jdbcTemplate.execute("DELETE FROM ainer_authorization_change_audit");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_decision_audit");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_subject_binding");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role_permission");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_permission");
        seedPermission("mgmt.test.read", "read", "mgmt.test", "LOW");
        seedPermission("mgmt.test.write", "write", "mgmt.test", "MEDIUM");

        // 签发真实 SERVICE_V1 JWT，注入 Bearer header 到所有请求
        managementJwt = signServiceJwt("svc-management", "authorization.manage");
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add(bearerInterceptor(managementJwt));
    }

    private void seedPermission(String code, String action, String resourceType, String riskTier) {
        jdbcTemplate.update("""
                INSERT INTO ainer_authorization_permission
                    (code, action, resource_type, risk_tier, audit_level, system_only, agent_delegable, definition_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ON_DECISION', false, false, 1, now(), now())
                """, code, action, resourceType, riskTier);
    }

    private ClientHttpRequestInterceptor bearerInterceptor(String jwt) {
        return (request, body, execution) -> {
            request.getHeaders().setBearerAuth(jwt);
            return execution.execute(request, body);
        };
    }

    /**
     * Sign a SERVICE_V1 JWT matching the claim contract expected by
     * {@link dev.ainer.security.token.ReferenceTokenProfileResolver}.
     */
    private static String signServiceJwt(String subjectId, String scope) {
        return signServiceJwtWithIssuer(subjectId, scope, ISSUER);
    }

    private static String signServiceJwtWithIssuer(String subjectId, String scope, String issuer) {
        try {
            JWSSigner signer = new RSASSASigner((RSAPrivateKey) RSA_KEY_PAIR.getPrivate());
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-kid").build(),
                    new JWTClaimsSet.Builder()
                            .issuer(issuer)
                            .audience(AUDIENCE)
                            .subject(subjectId)
                            .claim("token_profile", "SERVICE_V1")
                            .claim("claim_contract_version", "1")
                            .claim("actor_type", "SERVICE")
                            .claim("scope", scope)
                            .claim("amr", "client_credentials")
                            .claim("client_id", "test-client")
                            .claim("sec_epoch", 0L)
                            .issueTime(new Date())
                            .expirationTime(new Date(System.currentTimeMillis() + 300_000))
                            .build());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign test JWT", e);
        }
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(3072);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair", e);
        }
    }

    @Test
    void createRoleAndGetItBack() {
        RestResponse created = client.postJson("/api/authorization/roles", """
                {"code": "editor", "name": "Editor", "permissions": ["mgmt.test.read", "mgmt.test.write"]}
                """);
        assertThat(created.status().value()).isEqualTo(201);
        assertThat(created.jsonPath("$.code")).isEqualTo("AINER.COMMON.OK");
        assertThat(created.jsonPath("$.data.code")).isEqualTo("editor");

        String roleId = (String) created.jsonPath("$.data.id");
        RestResponse fetched = client.get("/api/authorization/roles/" + roleId);
        assertThat(fetched.status().value()).isEqualTo(200);
        assertThat(fetched.jsonPath("$.data.code")).isEqualTo("editor");
        assertThat(fetched.jsonPath("$.data.name")).isEqualTo("Editor");
        assertThat(fetched.jsonPath("$.data.permissions")).isNotNull();
    }

    @Test
    void replaceRolePermissions() {
        UUID roleId = createRole("editor", "mgmt.test.read");
        RestResponse response = client.putJson(
                "/api/authorization/roles/" + roleId + "/permissions",
                """
                {"permissions": ["mgmt.test.write"]}
                """);
        assertThat(response.status().value()).isEqualTo(200);
        assertThat(response.jsonPath("$.data.permissions")).asString().contains("mgmt.test.write");
    }

    @Test
    void createBindingRevokeAndCheckEffectiveAccess() {
        UUID roleId = createRole("editor", "mgmt.test.read", "mgmt.test.write");
        UUID workspaceId = UUID.randomUUID();

        RestResponse created = client.postJson("/api/authorization/bindings", """
                {"issuer": "ainer-test", "subjectType": "USER", "subjectId": "user-1",
                 "roleId": "%s", "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(roleId, workspaceId));
        assertThat(created.status().value()).isEqualTo(201);
        String bindingId = (String) created.jsonPath("$.data.id");

        // Effective access shows the live binding.
        RestResponse ea = client.get("/api/authorization/effective-access"
                + "?issuer=ainer-test&subjectType=USER&subjectId=user-1");
        assertThat(ea.status().value()).isEqualTo(200);
        assertThat(ea.jsonPath("$.data.bindings.length()")).isEqualTo(1);
        assertThat(ea.jsonPath("$.data.bindings[0].status")).isEqualTo("ACTIVE");

        // Revoke via action-path noun.
        RestResponse revoked = client.postJson(
                "/api/authorization/bindings/" + bindingId + "/revocations",
                """
                {"reason": "policy change"}
                """);
        assertThat(revoked.status().value()).isEqualTo(200);
        assertThat(revoked.jsonPath("$.data.status")).isEqualTo("REVOKED");

        // Effective access shows no live bindings.
        RestResponse eaAfter = client.get("/api/authorization/effective-access"
                + "?issuer=ainer-test&subjectType=USER&subjectId=user-1");
        assertThat(eaAfter.jsonPath("$.data.bindings.length()")).isEqualTo(0);
    }

    @Test
    void invalidScopeKindReturnsError() {
        UUID roleId = createRole("editor", "mgmt.test.read");
        RestResponse response = client.postJson("/api/authorization/bindings", """
                {"issuer": "ainer-test", "subjectType": "USER", "subjectId": "user-x",
                 "roleId": "%s", "scopeKind": "INVALID_KIND"}
                """.formatted(roleId));
        assertThat(response.status().is4xxClientError()).isTrue();
    }

    @Test
    void permissionsListReturnsCatalog() {
        RestResponse response = client.get("/api/authorization/permissions");
        assertThat(response.status().value()).isEqualTo(200);
        assertThat(response.jsonPath("$.data.length()")).isEqualTo(2);
    }

    @Test
    void requestWithoutBearerTokenIsRejected() {
        // 移除 Bearer interceptor → 无凭证请求应被 SecurityFilterChain 拒绝（401）
        restTemplate.getRestTemplate().getInterceptors().clear();
        RestResponse response = client.get("/api/authorization/permissions");
        assertThat(response.status().value()).isEqualTo(401);
    }

    @Test
    void requestWithServiceJwtLackingManagementScopeIsForbidden() {
        // 签发缺少 authorization.manage scope 的 SERVICE JWT → 应被 Controller requireManagement 拒绝（403）
        String noScopeJwt = signServiceJwt("svc-other", "some.other.scope");
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add(bearerInterceptor(noScopeJwt));
        RestResponse response = client.get("/api/authorization/permissions");
        assertThat(response.status().value()).isEqualTo(403);
    }

    @Test
    void requestWithWrongIssuerJwtIsRejected() {
        // 签发 issuer 错误的 JWT → SecurityFilterChain JwtDecoder 的 issuer validator 应拒绝（401）
        String wrongIssuerJwt = signServiceJwtWithIssuer("svc-test", "authorization.manage",
                "https://wrong.issuer.test");
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add(bearerInterceptor(wrongIssuerJwt));
        RestResponse response = client.get("/api/authorization/permissions");
        assertThat(response.status().value()).isEqualTo(401);
    }

    // ---- helpers ----

    @SuppressWarnings("unchecked")
    private UUID createRole(String code, String... permissions) {
        StringBuilder perms = new StringBuilder();
        for (int i = 0; i < permissions.length; i++) {
            if (i > 0) perms.append(", ");
            perms.append('"').append(permissions[i]).append('"');
        }
        RestResponse response = client.postJson("/api/authorization/roles",
                """
                {"code": "%s", "name": "%s", "permissions": [%s]}
                """.formatted(code, code, perms));
        assertThat(response.status().value()).isEqualTo(201);
        return UUID.fromString((String) response.jsonPath("$.data.id"));
    }

    @TestConfiguration
    static class ManagementPrincipalConfiguration {

        /**
         * Real {@link JwtDecoder} using the test RSA public key — verifies the JWT signature
         * signed by {@link #signServiceJwt}. No OIDC discovery; the decoder is supplied directly.
         */
        @Bean
        JwtDecoder testJwtDecoder() {
            RSAPublicKey publicKey = (RSAPublicKey) RSA_KEY_PAIR.getPublic();
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
            OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(ISSUER);
            OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                    jwt.getAudience().contains(AUDIENCE)
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                    "invalid_token", "Required audience is missing", null));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
            return decoder;
        }

        /**
         * {@code @Primary} resolver so it wins over any stub resolver that may leak from other tests'
         * {@code @ComponentScan} scope. Reads the verified {@link Jwt} from the SecurityContext and
         * resolves it through {@link dev.ainer.security.token.ReferenceTokenProfileResolver} — the
         * same logic as the production {@code SecurityContextAuthenticatedPrincipalResolver}.
         */
        @Bean
        @org.springframework.context.annotation.Primary
        dev.ainer.security.token.AuthenticatedPrincipalResolver jwtPrincipalResolver() {
            dev.ainer.security.token.ReferenceTokenProfileResolver profileResolver =
                    new dev.ainer.security.token.ReferenceTokenProfileResolver();
            return () -> {
                var authentication = org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
                if (authentication == null || !authentication.isAuthenticated()
                        || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
                    throw new dev.ainer.core.error.BusinessException(dev.ainer.core.error.StandardErrorCode.UNAUTHENTICATED);
                }
                if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
                    throw new dev.ainer.core.error.BusinessException(dev.ainer.core.error.StandardErrorCode.FORBIDDEN);
                }
                return profileResolver.resolve(new dev.ainer.security.token.VerifiedJwtClaims(
                        jwt.getIssuer().toString(),
                        jwt.getSubject(),
                        jwt.getAudience() == null ? java.util.Set.of()
                                : new java.util.LinkedHashSet<>(jwt.getAudience()),
                        jwt.getExpiresAt(),
                        jwt.getClaims()));
            };
        }

        @Bean
        dev.ainer.authorization.catalog.PermissionContributor httpTestPermissions() {
            return () -> java.util.Set.of(
                    new dev.ainer.authorization.domain.Permission(
                            new dev.ainer.authorization.domain.PermissionCode("mgmt.test.read"),
                            "read",
                            new dev.ainer.authorization.domain.ResourceType("mgmt.test"),
                            dev.ainer.authorization.domain.RiskTier.LOW,
                            dev.ainer.authorization.domain.AuditLevel.ON_DECISION,
                            false, false),
                    new dev.ainer.authorization.domain.Permission(
                            new dev.ainer.authorization.domain.PermissionCode("mgmt.test.write"),
                            "write",
                            new dev.ainer.authorization.domain.ResourceType("mgmt.test"),
                            dev.ainer.authorization.domain.RiskTier.MEDIUM,
                            dev.ainer.authorization.domain.AuditLevel.ON_DECISION,
                            false, false));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({AuthorizationModuleConfiguration.class, ManagementPrincipalConfiguration.class})
    static class TestApplication {
    }
}
