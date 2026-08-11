package dev.ainer.authorization;

import dev.ainer.security.principal.ServiceSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.security.token.TokenProfile;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP integration test for the authorization management API (ADR-0030 S2). Exercises the full
 * controller → service → persistence path over real HTTP against a PostgreSQL 18.3 Testcontainers
 * instance. The security context is stubbed by a test {@link AuthenticatedPrincipalResolver} —
 * the real JWT validation chain is tested elsewhere; here we verify the API contract and the
 * service/scope guard logic.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AuthorizationManagementHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.authorization.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
@AutoConfigureTestRestTemplate
class AuthorizationManagementHttpTest {

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

    @BeforeEach
    void cleanAndSeed() {
        client = RestTestClient.forLocalServer(restTemplate, port);
        jdbcTemplate.execute("DELETE FROM ainer_authorization_subject_binding");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role_permission");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_permission");
        seedPermission("mgmt.test.read", "read", "mgmt.test", "LOW");
        seedPermission("mgmt.test.write", "write", "mgmt.test", "MEDIUM");
    }

    private void seedPermission(String code, String action, String resourceType, String riskTier) {
        jdbcTemplate.update("""
                INSERT INTO ainer_authorization_permission
                    (code, action, resource_type, risk_tier, audit_level, system_only, agent_delegable, definition_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ON_DECISION', false, false, 1, now(), now())
                """, code, action, resourceType, riskTier);
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
        @Bean
        AuthenticatedPrincipalResolver testPrincipalResolver() {
            return new TestPrincipalResolver();
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

    /**
     * Stub resolver that returns a SERVICE principal with the {@code authorization.manage} scope.
     * This bypasses real JWT validation — the integration target is the API/service/persistence
     * contract, not the resource-server security chain.
     */
    static class TestPrincipalResolver implements AuthenticatedPrincipalResolver {
        private static final AuthenticatedPrincipal MANAGEMENT_PRINCIPAL = new AuthenticatedPrincipal(
                new ServiceSubjectRef(new IdentityAuthorityRef("https://auth.ainer.test"), "svc-management"),
                new IdentityAuthorityRef("https://auth.ainer.test"),
                TokenProfile.SERVICE_V1,
                "1",
                Set.of("ainer-api"),
                Set.of("authorization.manage"),
                "client_credentials",
                "test-client");

        @Override
        public AuthenticatedPrincipal requireCurrent() {
            return MANAGEMENT_PRINCIPAL;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({AuthorizationModuleConfiguration.class, ManagementPrincipalConfiguration.class})
    static class TestApplication {
    }
}
