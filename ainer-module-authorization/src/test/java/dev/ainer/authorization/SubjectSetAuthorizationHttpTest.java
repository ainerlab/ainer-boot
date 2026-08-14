package dev.ainer.authorization;

import com.nimbusds.jose.jwk.RSAKey;
import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationOutcome;
import dev.ainer.authorization.domain.AuthorizationRequest;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectSetRef;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import dev.ainer.authorization.policy.GrantAdministrationPolicy;
import dev.ainer.authorization.policy.ScopePermissionCeiling;
import dev.ainer.authorization.policy.SubjectSetMembership;
import dev.ainer.authorization.policy.SubjectSetMembershipResolver;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.testsupport.jwt.JwtTestSupport;
import dev.ainer.testsupport.rest.RestResponse;
import dev.ainer.testsupport.rest.RestTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-JWT HTTP + decision tests for subject-set bindings (ADR-0042 O2): management endpoints,
 * the anti-escalation creation matrix, and the decision engine's set-membership grant path with a
 * test membership provider. Live PostgreSQL; membership is evaluated at decision time.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = SubjectSetAuthorizationHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.authorization.enabled=true",
                "ainer.authorization.test-set-binding=true",
                "ainer.security.resource-server.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
@AutoConfigureTestRestTemplate
class SubjectSetAuthorizationHttpTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final UUID WORKSPACE_ID =
            UUID.fromString("019c4000-0000-7000-8000-000000000001");
    private static final UUID OTHER_WORKSPACE_ID =
            UUID.fromString("019c4000-0000-7000-8000-000000000002");
    private static final UUID GROUP_ID =
            UUID.fromString("019c4000-0000-7000-8000-000000000003");
    private static final PermissionCode WRITE_PERMISSION =
            new PermissionCode("set.test.write");
    private static final PermissionCode HIGH_PERMISSION =
            new PermissionCode("set.test.high");
    private static final ResourceType TEST_RESOURCE = new ResourceType("set.test.resource");
    private static final String WRITE_SCOPE = "set.tests.write";
    private static final RSAKey RSA_JWK = JwtTestSupport.generateRsaKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_set_binding_test")
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
    AuthorizationService authorizationService;

    private RestTestClient client;

    @BeforeEach
    void cleanSeedAndAuthenticate() {
        jdbcTemplate.execute("DELETE FROM ainer_authorization_decision_audit");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_change_audit");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_subject_set_binding");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_subject_binding");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role_permission");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_permission");
        seedPermission("set.test.write", "write", "set.test.resource", "MEDIUM");
        seedPermission("set.test.high", "administer", "set.test.resource", "HIGH");

        client = RestTestClient.forLocalServer(restTemplate, port);
        authenticate(JwtTestSupport.signServiceJwt(
                RSA_JWK, ISSUER, AUDIENCE, "svc-management", "authorization.manage"));
    }

    private void seedPermission(String code, String action, String resourceType, String riskTier) {
        jdbcTemplate.update("""
                INSERT INTO ainer_authorization_permission
                    (code, action, resource_type, risk_tier, audit_level, system_only,
                     agent_delegable, source_module, definition_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ON_DECISION', false, false, 'set-test', 1, now(), now())
                """, code, action, resourceType, riskTier);
    }

    private void authenticate(String jwt) {
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
                request.getHeaders().setBearerAuth(jwt);
            }
            return execution.execute(request, body);
        });
    }

    private String createRole(String code, String permission) {
        RestResponse created = client.postJson("/api/authorization/roles", """
                {"code": "%s", "name": "%s", "permissions": ["%s"]}
                """.formatted(code, code, permission));
        assertThat(created.status().value()).isEqualTo(201);
        return (String) created.jsonPath("$.data.id");
    }

    private RestResponse createSetBinding(String roleId, String scopeKind, UUID scopeWorkspace,
            String setObjectType) {
        return client.postJson("/api/authorization/set-bindings", """
                {"setObjectType": "%s", "setObjectId": "%s", "setRelation": "member",
                 "setWorkspaceId": "%s", "roleId": "%s", "scopeKind": "%s", "workspaceId": "%s"}
                """.formatted(setObjectType, GROUP_ID, WORKSPACE_ID, roleId, scopeKind, scopeWorkspace));
    }

    // ------------------------------------------------------------ decision helpers

    private AuthorizationDecision decideAsUser(String subjectId, UUID resourceWorkspace) {
        SubjectRef subject = new SubjectRef(ISSUER, subjectId, SubjectType.USER);
        AuthorizationContext context = new AuthorizationContext(
                Instant.now(), AuthorizationContext.Assurance.NONE, "test-client", "req-set", null);
        return authorizationService.authorize(new AuthorizationRequest(
                new Requester.Authenticated(subject, Set.of(WRITE_SCOPE), Set.of(AUDIENCE), "test-client"),
                AccessMode.AUTHENTICATED,
                WRITE_PERMISSION,
                new ResourceRef(resourceWorkspace, TEST_RESOURCE, GROUP_ID),
                context));
    }

    // ------------------------------------------------------------------- tests

    @Test
    void memberIsAllowedThroughSetBindingAndRevocationDenies() {
        String roleId = createRole("set-writer", WRITE_PERMISSION.value());
        RestResponse created = createSetBinding(roleId, "WORKSPACE", WORKSPACE_ID, "test.group");
        assertThat(created.status().value()).isEqualTo(201);
        assertThat(created.jsonPath("$.data.status")).isEqualTo("ACTIVE");
        String bindingId = (String) created.jsonPath("$.data.id");

        assertThat(decideAsUser("user-set-member", WORKSPACE_ID).outcome())
                .isEqualTo(AuthorizationOutcome.ALLOW);
        assertThat(decideAsUser("user-outsider", WORKSPACE_ID).outcome())
                .isEqualTo(AuthorizationOutcome.DENY);

        RestResponse revoked = client.postJson(
                "/api/authorization/set-bindings/" + bindingId + "/revocations",
                "{\"reason\": \"rotation\"}");
        assertThat(revoked.status().value()).isEqualTo(200);
        assertThat(revoked.jsonPath("$.data.status")).isEqualTo("REVOKED");

        assertThat(decideAsUser("user-set-member", WORKSPACE_ID).outcome())
                .isEqualTo(AuthorizationOutcome.DENY);

        Integer audits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_authorization_change_audit WHERE target_type = 'SET_BINDING'",
                Integer.class);
        assertThat(audits).isEqualTo(2);
    }

    @Test
    void crossWorkspaceResourceIsNotCoveredBySetBindingScope() {
        String roleId = createRole("set-writer-x", WRITE_PERMISSION.value());
        RestResponse created = createSetBinding(roleId, "WORKSPACE", WORKSPACE_ID, "test.group");
        assertThat(created.status().value()).isEqualTo(201);

        assertThat(decideAsUser("user-set-member", OTHER_WORKSPACE_ID).outcome())
                .isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void globalScopeIsRejectedForSetBindings() {
        String roleId = createRole("set-global", WRITE_PERMISSION.value());
        RestResponse response = client.postJson("/api/authorization/set-bindings", """
                {"setObjectType": "test.group", "setObjectId": "%s", "setRelation": "member",
                 "setWorkspaceId": "%s", "roleId": "%s", "scopeKind": "GLOBAL"}
                """.formatted(GROUP_ID, WORKSPACE_ID, roleId));
        assertThat(response.status().value()).isEqualTo(422);
    }

    @Test
    void setWorkspaceMustMatchScopeWorkspace() {
        String roleId = createRole("set-mismatch", WRITE_PERMISSION.value());
        RestResponse response = createSetBinding(roleId, "WORKSPACE", OTHER_WORKSPACE_ID, "test.group");
        assertThat(response.status().value()).isEqualTo(422);
        assertThat(response.jsonPath("$.code"))
                .isEqualTo("AINER.AUTHORIZATION.SUBJECT_SET_SCOPE_MISMATCH");
    }

    @Test
    void unknownSetFamilyIsRejected() {
        String roleId = createRole("set-unknown", WRITE_PERMISSION.value());
        RestResponse response = createSetBinding(roleId, "WORKSPACE", WORKSPACE_ID, "no.such.family");
        assertThat(response.status().value()).isEqualTo(422);
        assertThat(response.jsonPath("$.code")).isEqualTo("AINER.AUTHORIZATION.UNKNOWN_SUBJECT_SET");
    }

    @Test
    void highRiskPermissionMayNotRideASetBinding() {
        String roleId = createRole("set-high", HIGH_PERMISSION.value());
        RestResponse response = createSetBinding(roleId, "WORKSPACE", WORKSPACE_ID, "test.group");
        assertThat(response.status().value()).isEqualTo(422);
        assertThat(response.jsonPath("$.code"))
                .isEqualTo("AINER.AUTHORIZATION.SUBJECT_SET_PERMISSION_FORBIDDEN");
    }

    @Test
    void requestWithoutTokenIsUnauthorized() {
        restTemplate.getRestTemplate().setInterceptors(java.util.List.of());
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/authorization/set-bindings/"
                        + UUID.randomUUID(),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(AuthorizationModuleConfiguration.class)
    static class TestApplication {

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.authorization.test-set-binding", havingValue = "true")
        @Bean
        @Primary
        JwtDecoder setTestJwtDecoder() {
            return JwtTestSupport.jwtDecoder(RSA_JWK, ISSUER, AUDIENCE);
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.authorization.test-set-binding", havingValue = "true")
        @Bean
        AuthenticatedPrincipalResolver setTestPrincipalResolver() {
            return JwtTestSupport.principalResolver();
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.authorization.test-set-binding", havingValue = "true")
        @Bean
        dev.ainer.authorization.catalog.PermissionContributor setTestPermissions() {
            return () -> Set.of(
                    new Permission(WRITE_PERMISSION, "write", TEST_RESOURCE, RiskTier.MEDIUM,
                            AuditLevel.ON_DECISION, false, false),
                    new Permission(HIGH_PERMISSION, "administer", TEST_RESOURCE, RiskTier.HIGH,
                            AuditLevel.ALWAYS, false, false));
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.authorization.test-set-binding", havingValue = "true")
        @Bean
        @Primary
        ScopePermissionCeiling setTestScopeCeiling() {
            return (scope, permission) -> WRITE_SCOPE.equals(scope)
                    && WRITE_PERMISSION.equals(permission);
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.authorization.test-set-binding", havingValue = "true")
        @Bean
        @Primary
        DomainAuthorizationPolicy setTestDomainPolicy() {
            return new DomainAuthorizationPolicy() {
                @Override
                public GrantPath pathFor(PermissionCode permission) {
                    return WRITE_PERMISSION.equals(permission) ? GrantPath.BINDING_REQUIRED : null;
                }

                @Override
                public boolean relationGrants(Requester.Authenticated subject,
                        PermissionCode permission, ResourceRef resource,
                        AuthorizationContext context) {
                    return false;
                }

                @Override
                public boolean resourceStateSatisfies(Requester.Authenticated subject,
                        PermissionCode permission, ResourceRef resource,
                        AuthorizationContext context) {
                    return true;
                }
            };
        }

        /** 测试集合族：test.group#member，只有 user-set-member 是成员。 */
        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.authorization.test-set-binding", havingValue = "true")
        @Bean
        SubjectSetMembershipResolver testGroupMembershipResolver() {
            return new SubjectSetMembershipResolver() {
                @Override
                public boolean supports(String objectType, String relation) {
                    return "test.group".equals(objectType) && "member".equals(relation);
                }

                @Override
                public SubjectSetMembership resolve(
                        dev.ainer.authorization.domain.SubjectRef requester,
                        SubjectSetRef set, Instant evaluationTime) {
                    if ("user-set-member".equals(requester.subjectId())) {
                        return new SubjectSetMembership(
                                SubjectSetMembership.Status.MEMBER, null, "test-v1", null, null);
                    }
                    return SubjectSetMembership.notMember();
                }
            };
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.authorization.test-set-binding", havingValue = "true")
        @Bean
        GrantAdministrationPolicy setTestGrantAdministrationPolicy() {
            return new GrantAdministrationPolicy() {
                @Override
                public String version() {
                    return "set-test-administration-v1";
                }

                @Override
                public boolean isTrustedManager(AuthenticatedPrincipal actor) {
                    return actor.isService() && "svc-management".equals(actor.subjectId());
                }

                @Override
                public boolean isPermissionAssignable(AuthenticatedPrincipal actor,
                        Permission permission) {
                    return true;
                }

                @Override
                public boolean isScopeAssignable(AuthenticatedPrincipal actor, Scope scope) {
                    return true;
                }

                @Override
                public boolean isTargetAssignable(AuthenticatedPrincipal actor,
                        dev.ainer.authorization.domain.SubjectRef target) {
                    return true;
                }
            };
        }
    }
}
