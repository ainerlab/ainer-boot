package dev.ainer.authorization;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.ainer.authorization.application.AuthorizationDecisionAuditService;
import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationRequest;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.authorization.spring.AinerAuthorize;
import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.testsupport.rest.RestResponse;
import dev.ainer.testsupport.rest.RestTestClient;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP integration test for the authorization management API (ADR-0030 S2) with a <strong>real signed
 * JWT security chain</strong>. Exercises the full SecurityFilterChain → NimbusJwtDecoder (real RSA
 * signature verification) → JwtToVerifiedJwtClaims → ReferenceTokenProfileResolver →
 * SecurityContextAuthenticatedPrincipalResolver → controller path, over real HTTP against a
 * PostgreSQL 18.3 Testcontainers instance.
 *
 * <p>This replaces the earlier stub {@code AuthenticatedPrincipalResolver} (defect #9): the JWT is
 * signed with a test RSA key and the resource server verifies it with the matching public key. The
 * same fixture also proves that a protected product write re-evaluates a USER binding on every
 * request: after revocation, the exact same still-valid JWT is denied before the product effect.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AuthorizationManagementHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.authorization.enabled=true",
                "ainer.authorization.test-administration-policy=http",
                "ainer.authorization.test-protected-write=true",
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
    private static final PermissionCode PROTECTED_WRITE_PERMISSION =
            new PermissionCode("consumer.resource.write");
    private static final String PROTECTED_WRITE_SCOPE = "consumer.resources.write";
    private static final ResourceType PROTECTED_RESOURCE =
            new ResourceType("consumer.resource");
    private static final PermissionCode ENDPOINT_PERMISSION =
            new PermissionCode("consumer.endpoint.read");
    private static final String ENDPOINT_SCOPE = "consumer.endpoints.read";
    private static final ResourceType ENDPOINT_RESOURCE = new ResourceType("request");
    private static final UUID PROTECTED_WORKSPACE_ID =
            UUID.fromString("019c1100-0000-7000-8000-000000000001");
    private static final UUID PROTECTED_RESOURCE_ID =
            UUID.fromString("019c1100-0000-7000-8000-000000000002");

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
    @Autowired
    ProtectedBusinessWriteController protectedBusinessWriteController;

    private RestTestClient client;
    private String managementJwt;

    @BeforeEach
    void cleanSeedAndAuthenticate() {
        protectedBusinessWriteController.resetAdapterInvocations();
        client = RestTestClient.forLocalServer(restTemplate, port);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS consumer_protected_resource (
                    id UUID NOT NULL,
                    workspace_id UUID NOT NULL,
                    title VARCHAR(100) NOT NULL,
                    CONSTRAINT pk_consumer_protected_resource PRIMARY KEY (id),
                    CONSTRAINT uq_consumer_protected_resource_workspace_id
                        UNIQUE (workspace_id, id),
                    CONSTRAINT ck_consumer_protected_resource_id_version
                        CHECK (uuid_extract_version(id) = 7)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS consumer_protected_write_event (
                    id UUID NOT NULL DEFAULT uuidv7(),
                    workspace_id UUID NOT NULL,
                    resource_id UUID NOT NULL,
                    value VARCHAR(100) NOT NULL,
                    requester_issuer VARCHAR(256) NOT NULL,
                    requester_id VARCHAR(128) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL,
                    CONSTRAINT pk_consumer_protected_write_event PRIMARY KEY (id),
                    CONSTRAINT fk_consumer_protected_write_event_resource
                        FOREIGN KEY (workspace_id, resource_id)
                        REFERENCES consumer_protected_resource (workspace_id, id),
                    CONSTRAINT ck_consumer_protected_write_event_id_version
                        CHECK (uuid_extract_version(id) = 7),
                    CONSTRAINT ck_consumer_protected_write_event_value
                        CHECK (btrim(value) <> '')
                )
                """);
        jdbcTemplate.execute(
                "TRUNCATE TABLE consumer_protected_write_event, consumer_protected_resource");
        jdbcTemplate.update("""
                INSERT INTO consumer_protected_resource (id, workspace_id, title)
                VALUES (?, ?, 'Protected fixture resource')
                """, PROTECTED_RESOURCE_ID, PROTECTED_WORKSPACE_ID);
        jdbcTemplate.execute("DELETE FROM ainer_authorization_change_audit");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_decision_audit");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_subject_binding");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role_permission");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_permission");
        seedPermission("mgmt.test.read", "read", "mgmt.test", "LOW");
        seedPermission("mgmt.test.write", "write", "mgmt.test", "MEDIUM");
        seedPermission(PROTECTED_WRITE_PERMISSION.value(), "write", PROTECTED_RESOURCE.value(), "MEDIUM");

        // 签发真实 SERVICE_V1 JWT，注入 Bearer header 到所有请求
        managementJwt = signServiceJwt("svc-management", "authorization.manage");
        authenticateWith(managementJwt);
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

    private void authenticateWith(String jwt) {
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add(bearerInterceptor(jwt));
    }

    /**
     * Sign a SERVICE_V1 JWT matching the claim contract expected by
     * {@link dev.ainer.security.token.ReferenceTokenProfileResolver}.
     */
    private static String signServiceJwt(String subjectId, String scope) {
        return signServiceJwtWithIssuer(subjectId, scope, ISSUER);
    }

    private static String signServiceJwtWithIssuer(String subjectId, String scope, String issuer) {
        return signJwt(subjectId, scope, issuer, "SERVICE_V1", "SERVICE", "client_credentials");
    }

    private static String signUserJwt(String subjectId, String scope) {
        return signJwt(subjectId, scope, ISSUER, "USER_NEUTRAL_V1", "USER", "pwd");
    }

    private static String signJwt(
            String subjectId,
            String scope,
            String issuer,
            String tokenProfile,
            String actorType,
            String assurance) {
        try {
            JWSSigner signer = new RSASSASigner((RSAPrivateKey) RSA_KEY_PAIR.getPrivate());
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-kid").build(),
                    new JWTClaimsSet.Builder()
                            .issuer(issuer)
                            .audience(AUDIENCE)
                            .subject(subjectId)
                            .claim("token_profile", tokenProfile)
                            .claim("claim_contract_version", "1")
                            .claim("actor_type", actorType)
                            .claim("scope", scope)
                            .claim("amr", assurance)
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
    void revokedBindingImmediatelyBlocksTheSameJwtFromAProtectedBusinessWrite() {
        UUID roleId = createRole("consumer-writer", PROTECTED_WRITE_PERMISSION.value());
        RestResponse created = client.postJson("/api/authorization/bindings", """
                {"issuer": "%s", "subjectType": "USER", "subjectId": "user-writer",
                 "roleId": "%s", "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(ISSUER, roleId, PROTECTED_WORKSPACE_ID));
        assertThat(created.status().value()).isEqualTo(201);
        String bindingId = (String) created.jsonPath("$.data.id");

        String originalUserJwt = signUserJwt("user-writer", PROTECTED_WRITE_SCOPE);
        authenticateWith(originalUserJwt);
        String writePath = "/test/consumer-resources/%s/writes"
                .formatted(PROTECTED_RESOURCE_ID);

        RestResponse allowed = client.postJson(writePath, """
                {"value": "first authorized write"}
                """);
        assertThat(allowed.status().value()).isEqualTo(201);
        assertThat(allowed.jsonPath("$.code")).isEqualTo("AINER.COMMON.OK");
        assertThat(protectedWriteCount()).isEqualTo(1L);

        authenticateWith(managementJwt);
        RestResponse revoked = client.postJson(
                "/api/authorization/bindings/" + bindingId + "/revocations",
                """
                {"reason": "protected write access removed"}
                """);
        assertThat(revoked.status().value()).isEqualTo(200);
        assertThat(revoked.jsonPath("$.data.status")).isEqualTo("REVOKED");

        // Reuse the exact serialized JWT: only the database Binding changed between requests.
        authenticateWith(originalUserJwt);
        RestResponse denied = client.postJson(writePath, """
                {"value": "write after revocation"}
                """);
        assertThat(denied.status().value()).isEqualTo(403);
        assertThat(denied.jsonPath("$.code")).isEqualTo("AINER.COMMON.FORBIDDEN");
        assertThat(protectedWriteCount()).isEqualTo(1L);

        assertThat(decisionAuditCount("ALLOW", "AUTHORIZED")).isEqualTo(1L);
        assertThat(decisionAuditCount("DENY", "NO_BINDING")).isEqualTo(1L);
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
        assertThat(response.jsonPath("$.data.length()")).isEqualTo(3);
    }

    @Test
    void requestWithoutBearerTokenIsRejected() {
        // 移除 Bearer interceptor → 无凭证请求应被 SecurityFilterChain 拒绝（401）
        restTemplate.getRestTemplate().getInterceptors().clear();
        RestResponse response = client.get("/api/authorization/permissions");
        assertThat(response.status().value()).isEqualTo(401);
    }

    @Test
    void annotatedEndpointExecutesTheAuthorizationManagerBeforeControllerInvocation() {
        authenticateWith(signServiceJwt("svc-adapter", ENDPOINT_SCOPE));

        RestResponse allowed = client.get("/test/consumer-resources/adapter-gate");

        assertThat(allowed.status().value()).isEqualTo(200);
        assertThat(allowed.jsonPath("$.data")).isEqualTo(1);
        assertThat(protectedBusinessWriteController.adapterInvocationCount()).isEqualTo(1);

        authenticateWith(signServiceJwt("svc-adapter", "some.other.scope"));
        RestResponse denied = client.get("/test/consumer-resources/adapter-gate");

        assertThat(denied.status().value()).isEqualTo(403);
        assertThat(denied.jsonPath("$.code")).isEqualTo("AINER.COMMON.FORBIDDEN");
        assertThat(protectedBusinessWriteController.adapterInvocationCount()).isEqualTo(1);
    }

    @Test
    void annotatedEndpointWithoutBearerTokenIsRejectedBeforeControllerInvocation() {
        restTemplate.getRestTemplate().getInterceptors().clear();

        RestResponse denied = client.get("/test/consumer-resources/adapter-gate");

        assertThat(denied.status().value()).isEqualTo(401);
        assertThat(protectedBusinessWriteController.adapterInvocationCount()).isZero();
    }

    @Test
    void requestWithServiceJwtLackingManagementScopeIsForbidden() {
        // 签发缺少 authorization.manage scope 的 SERVICE JWT → 应被 Controller requireManagement 拒绝（403）
        String noScopeJwt = signServiceJwt("svc-other", "some.other.scope");
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add(bearerInterceptor(noScopeJwt));
        RestResponse response = client.get("/api/authorization/permissions");
        assertThat(response.status().value()).isEqualTo(403);
        assertThat(response.jsonPath("$.code"))
                .isEqualTo("AINER.AUTHORIZATION.GRANT_ADMINISTRATION_DENIED");
    }

    @Test
    void managementScopeDoesNotTrustAnArbitraryServicePrincipal() {
        String untrustedJwt = signServiceJwt("svc-other", "authorization.manage");
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add(bearerInterceptor(untrustedJwt));

        RestResponse response = client.get("/api/authorization/permissions");

        assertThat(response.status().value()).isEqualTo(403);
        assertThat(response.jsonPath("$.code"))
                .isEqualTo("AINER.AUTHORIZATION.GRANT_ADMINISTRATION_DENIED");
    }

    @Test
    void unassignableAndSystemOnlyPermissionsAreRejected() {
        RestResponse unassignable = client.postJson("/api/authorization/roles", """
                {"code": "unassignable", "name": "Unassignable", "permissions": ["mgmt.test.unassignable"]}
                """);
        assertThat(unassignable.status().value()).isEqualTo(422);
        assertThat(unassignable.jsonPath("$.code"))
                .isEqualTo("AINER.AUTHORIZATION.PERMISSION_NOT_ASSIGNABLE");

        RestResponse systemOnly = client.postJson("/api/authorization/roles", """
                {"code": "system", "name": "System", "permissions": ["mgmt.test.system"]}
                """);
        assertThat(systemOnly.status().value()).isEqualTo(422);
        assertThat(systemOnly.jsonPath("$.code"))
                .isEqualTo("AINER.AUTHORIZATION.PERMISSION_NOT_ASSIGNABLE");
    }

    @Test
    void globalScopeAndIneligibleTargetAreRejected() {
        UUID roleId = createRole("editor", "mgmt.test.read");

        RestResponse global = client.postJson("/api/authorization/bindings", """
                {"issuer": "ainer-test", "subjectType": "USER", "subjectId": "user-global",
                 "roleId": "%s", "scopeKind": "GLOBAL"}
                """.formatted(roleId));
        assertThat(global.status().value()).isEqualTo(422);
        assertThat(global.jsonPath("$.code"))
                .isEqualTo("AINER.AUTHORIZATION.SCOPE_NOT_ASSIGNABLE");

        RestResponse ineligibleTarget = client.postJson("/api/authorization/bindings", """
                {"issuer": "other-authority", "subjectType": "USER", "subjectId": "user-other",
                 "roleId": "%s", "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(roleId, UUID.randomUUID()));
        assertThat(ineligibleTarget.status().value()).isEqualTo(403);
        assertThat(ineligibleTarget.jsonPath("$.code"))
                .isEqualTo("AINER.AUTHORIZATION.GRANT_ADMINISTRATION_DENIED");
    }

    @Test
    void genericManagementApiRejectsSelfBindingAndOwnRoleMutation() {
        UUID roleId = createRole("editor", "mgmt.test.read");
        UUID workspaceId = UUID.randomUUID();

        RestResponse selfBinding = client.postJson("/api/authorization/bindings", """
                {"issuer": "%s", "subjectType": "SERVICE", "subjectId": "svc-management",
                 "roleId": "%s", "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(ISSUER, roleId, workspaceId));
        assertThat(selfBinding.status().value()).isEqualTo(403);
        assertThat(selfBinding.jsonPath("$.code"))
                .isEqualTo("AINER.AUTHORIZATION.SELF_GRANT_FORBIDDEN");

        // Simulate a binding established by a separate trusted bootstrap/onboarding path. Once the
        // role grants the manager access, the generic API must not let that manager enlarge it.
        UUID ownBindingId = jdbcTemplate.queryForObject("""
                INSERT INTO ainer_authorization_subject_binding (
                    issuer, subject_type, subject_id, role_id, scope_kind, workspace_id,
                    valid_from, status, version, created_at, updated_at
                ) VALUES (?, 'SERVICE', ?, ?, 'WORKSPACE', ?, now(), 'ACTIVE', 0, now(), now())
                RETURNING id
                """, UUID.class, ISSUER, "svc-management", roleId, workspaceId);

        RestResponse ownRoleMutation = client.putJson(
                "/api/authorization/roles/" + roleId + "/permissions",
                """
                {"permissions": ["mgmt.test.read", "mgmt.test.write"]}
                """);
        assertThat(ownRoleMutation.status().value()).isEqualTo(403);
        assertThat(ownRoleMutation.jsonPath("$.code"))
                .isEqualTo("AINER.AUTHORIZATION.SELF_GRANT_FORBIDDEN");

        RestResponse ownBindingRevocation = client.postJson(
                "/api/authorization/bindings/" + ownBindingId + "/revocations",
                """
                {"reason": "self change"}
                """);
        assertThat(ownBindingRevocation.status().value()).isEqualTo(403);
        assertThat(ownBindingRevocation.jsonPath("$.code"))
                .isEqualTo("AINER.AUTHORIZATION.SELF_GRANT_FORBIDDEN");
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

    private long protectedWriteCount() {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM consumer_protected_write_event", Long.class));
    }

    private long decisionAuditCount(String outcome, String reasonCode) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM ainer_authorization_decision_audit
                WHERE requester_issuer = ?
                  AND requester_type = 'USER'
                  AND requester_id = 'user-writer'
                  AND permission_code = ?
                  AND resource_type = ?
                  AND resource_id = ?
                  AND outcome = ?
                  AND reason_code = ?
                """, Long.class,
                ISSUER,
                PROTECTED_WRITE_PERMISSION.value(),
                PROTECTED_RESOURCE.value(),
                PROTECTED_RESOURCE_ID,
                outcome,
                reasonCode));
    }

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
                            false, false),
                    new dev.ainer.authorization.domain.Permission(
                            PROTECTED_WRITE_PERMISSION,
                            "write",
                            PROTECTED_RESOURCE,
                            dev.ainer.authorization.domain.RiskTier.MEDIUM,
                            dev.ainer.authorization.domain.AuditLevel.ON_DECISION,
                            false, false),
                    new dev.ainer.authorization.domain.Permission(
                            ENDPOINT_PERMISSION,
                            "read",
                            ENDPOINT_RESOURCE,
                            dev.ainer.authorization.domain.RiskTier.LOW,
                            dev.ainer.authorization.domain.AuditLevel.NONE,
                            false, false),
                    new dev.ainer.authorization.domain.Permission(
                            new dev.ainer.authorization.domain.PermissionCode("mgmt.test.unassignable"),
                            "assign",
                            new dev.ainer.authorization.domain.ResourceType("mgmt.test"),
                            dev.ainer.authorization.domain.RiskTier.HIGH,
                            dev.ainer.authorization.domain.AuditLevel.ALWAYS,
                            false, false),
                    new dev.ainer.authorization.domain.Permission(
                            new dev.ainer.authorization.domain.PermissionCode("mgmt.test.system"),
                            "administer",
                            new dev.ainer.authorization.domain.ResourceType("mgmt.test"),
                            dev.ainer.authorization.domain.RiskTier.HIGH,
                            dev.ainer.authorization.domain.AuditLevel.ALWAYS,
                            true, false));
        }

        @Bean
        dev.ainer.authorization.policy.ScopePermissionCeiling protectedWriteScopeCeiling() {
            return (scope, permission) ->
                    (PROTECTED_WRITE_SCOPE.equals(scope) && PROTECTED_WRITE_PERMISSION.equals(permission))
                            || (ENDPOINT_SCOPE.equals(scope) && ENDPOINT_PERMISSION.equals(permission));
        }

        @Bean
        dev.ainer.authorization.policy.DomainAuthorizationPolicy protectedWriteDomainPolicy() {
            return new dev.ainer.authorization.policy.DomainAuthorizationPolicy() {
                @Override
                public dev.ainer.authorization.domain.GrantPath pathFor(PermissionCode permission) {
                    if (PROTECTED_WRITE_PERMISSION.equals(permission)) {
                        return dev.ainer.authorization.domain.GrantPath.BINDING_REQUIRED;
                    }
                    return ENDPOINT_PERMISSION.equals(permission)
                            ? dev.ainer.authorization.domain.GrantPath.RELATION_DERIVED
                            : null;
                }

                @Override
                public boolean relationGrants(
                        Requester.Authenticated subject,
                        PermissionCode permission,
                        ResourceRef resource,
                        AuthorizationContext context) {
                    return ENDPOINT_PERMISSION.equals(permission)
                            && ENDPOINT_RESOURCE.equals(resource.resourceType());
                }

                @Override
                public boolean resourceStateSatisfies(
                        Requester.Authenticated subject,
                        PermissionCode permission,
                        ResourceRef resource,
                        AuthorizationContext context) {
                    return (PROTECTED_WRITE_PERMISSION.equals(permission)
                                    && PROTECTED_RESOURCE.equals(resource.resourceType()))
                            || (ENDPOINT_PERMISSION.equals(permission)
                                    && ENDPOINT_RESOURCE.equals(resource.resourceType()));
                }
            };
        }

        @Bean
        ProtectedBusinessWriteService protectedBusinessWriteService(
                AuthorizationService authorizationService,
                AuthorizationDecisionAuditService decisionAuditService,
                AuthenticatedPrincipalResolver principalResolver,
                JdbcTemplate jdbcTemplate,
                Clock clock) {
            return new ProtectedBusinessWriteService(
                    authorizationService,
                    decisionAuditService,
                    principalResolver,
                    jdbcTemplate,
                    clock);
        }

        @Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.authorization.test-administration-policy", havingValue = "http")
        dev.ainer.authorization.policy.GrantAdministrationPolicy httpTestGrantAdministrationPolicy() {
            return new dev.ainer.authorization.policy.GrantAdministrationPolicy() {
                @Override
                public String version() {
                    return "http-test-administration-v1";
                }

                @Override
                public boolean isTrustedManager(
                        dev.ainer.security.token.AuthenticatedPrincipal actor) {
                    return actor.isService()
                            && ISSUER.equals(actor.authority().issuer())
                            && "svc-management".equals(actor.subjectId());
                }

                @Override
                public boolean isPermissionAssignable(
                        dev.ainer.security.token.AuthenticatedPrincipal actor,
                        dev.ainer.authorization.domain.Permission permission) {
                    return java.util.Set.of(
                                    "mgmt.test.read",
                                    "mgmt.test.write",
                                    PROTECTED_WRITE_PERMISSION.value())
                            .contains(permission.code().value());
                }

                @Override
                public boolean isScopeAssignable(
                        dev.ainer.security.token.AuthenticatedPrincipal actor,
                        dev.ainer.authorization.domain.Scope scope) {
                    return scope instanceof dev.ainer.authorization.domain.Scope.Workspace
                            || scope instanceof dev.ainer.authorization.domain.Scope.Resource;
                }

                @Override
                public boolean isTargetAssignable(
                        dev.ainer.security.token.AuthenticatedPrincipal actor,
                        dev.ainer.authorization.domain.SubjectRef target) {
                    return target.type() == dev.ainer.authorization.domain.SubjectType.USER
                            && java.util.Set.of("ainer-test", ISSUER)
                                    .contains(target.issuerNamespace());
                }
            };
        }
    }

    @RestController
    @RequestMapping("/test/consumer-resources")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "ainer.authorization.test-protected-write", havingValue = "true")
    static class ProtectedBusinessWriteController {

        private final ProtectedBusinessWriteService writeService;
        private final AtomicInteger adapterInvocations = new AtomicInteger();

        ProtectedBusinessWriteController(ProtectedBusinessWriteService writeService) {
            this.writeService = writeService;
        }

        @GetMapping("/adapter-gate")
        @AinerAuthorize(permission = "consumer.endpoint.read")
        ApiResponse<Integer> adapterGate(HttpServletRequest request) {
            int invocation = adapterInvocations.incrementAndGet();
            return ApiResponse.success(invocation, RequestIds.currentOrCreate(request));
        }

        int adapterInvocationCount() {
            return adapterInvocations.get();
        }

        void resetAdapterInvocations() {
            adapterInvocations.set(0);
        }

        @PostMapping("/{resourceId}/writes")
        ResponseEntity<ApiResponse<ProtectedWriteResponse>> write(
                @PathVariable UUID resourceId,
                @RequestBody ProtectedWriteRequest body,
                HttpServletRequest request) {
            String requestId = RequestIds.currentOrCreate(request);
            ProtectedWriteResponse response =
                    writeService.write(resourceId, body.value(), requestId, null);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response, requestId));
        }
    }

    static class ProtectedBusinessWriteService {

        private final AuthorizationService authorizationService;
        private final AuthorizationDecisionAuditService decisionAuditService;
        private final AuthenticatedPrincipalResolver principalResolver;
        private final JdbcTemplate jdbcTemplate;
        private final Clock clock;

        ProtectedBusinessWriteService(
                AuthorizationService authorizationService,
                AuthorizationDecisionAuditService decisionAuditService,
                AuthenticatedPrincipalResolver principalResolver,
                JdbcTemplate jdbcTemplate,
                Clock clock) {
            this.authorizationService = authorizationService;
            this.decisionAuditService = decisionAuditService;
            this.principalResolver = principalResolver;
            this.jdbcTemplate = jdbcTemplate;
            this.clock = clock;
        }

        @Transactional
        public ProtectedWriteResponse write(
                UUID resourceId, String value, String requestId, String traceId) {
            String normalizedValue = value == null ? "" : value.trim();
            if (normalizedValue.isEmpty() || normalizedValue.length() > 100) {
                throw new BusinessException(StandardErrorCode.INVALID_REQUEST);
            }

            UUID workspaceId = findWorkspaceId(resourceId);
            AuthenticatedPrincipal principal = principalResolver.requireCurrent();
            SubjectType subjectType;
            if (principal.isHuman()) {
                subjectType = SubjectType.USER;
            } else if (principal.isService()) {
                subjectType = SubjectType.SERVICE;
            } else {
                throw new BusinessException(StandardErrorCode.FORBIDDEN);
            }
            SubjectRef subject = new SubjectRef(
                    principal.authority().issuer(),
                    principal.subjectId(),
                    subjectType);
            AuthorizationContext context = new AuthorizationContext(
                    Instant.now(clock),
                    AuthorizationContext.Assurance.NONE,
                    principal.clientId(),
                    requestId,
                    traceId);
            AuthorizationRequest authorizationRequest = new AuthorizationRequest(
                    new Requester.Authenticated(
                            subject,
                            principal.scopes(),
                            principal.audiences(),
                            principal.clientId()),
                    AccessMode.AUTHENTICATED,
                    PROTECTED_WRITE_PERMISSION,
                    new ResourceRef(workspaceId, PROTECTED_RESOURCE, resourceId),
                    context);
            AuthorizationDecision decision = authorizationService.authorize(authorizationRequest);

            // Audit must succeed before the protected effect is allowed to reach the product table.
            decisionAuditService.recordIfApplicable(
                    authorizationRequest, decision, requestId, traceId);
            if (!decision.isAllowed() || !decision.obligations().isEmpty()) {
                throw new BusinessException(StandardErrorCode.FORBIDDEN);
            }

            UUID writeId = Objects.requireNonNull(jdbcTemplate.queryForObject("""
                    INSERT INTO consumer_protected_write_event (
                        workspace_id, resource_id, value,
                        requester_issuer, requester_id, created_at
                    ) VALUES (?, ?, ?, ?, ?, now())
                    RETURNING id
                    """, UUID.class,
                    workspaceId,
                    resourceId,
                    normalizedValue,
                    principal.authority().issuer(),
                    principal.subjectId()));
            return new ProtectedWriteResponse(writeId, resourceId);
        }

        private UUID findWorkspaceId(UUID resourceId) {
            List<UUID> matches = jdbcTemplate.query(
                    "SELECT workspace_id FROM consumer_protected_resource WHERE id = ?",
                    (rows, rowNumber) -> rows.getObject("workspace_id", UUID.class),
                    resourceId);
            if (matches.isEmpty()) {
                throw new BusinessException(StandardErrorCode.NOT_FOUND);
            }
            return matches.getFirst();
        }
    }

    record ProtectedWriteRequest(String value) {
    }

    record ProtectedWriteResponse(UUID writeId, UUID resourceId) {
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({AuthorizationModuleConfiguration.class, ManagementPrincipalConfiguration.class})
    static class TestApplication {
    }
}
