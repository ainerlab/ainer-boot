package dev.ainer.module.ai.agent;

import com.nimbusds.jose.jwk.RSAKey;
import dev.ainer.authorization.AuthorizationModuleConfiguration;
import dev.ainer.authorization.application.ActingGrantApplicationService;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import dev.ainer.authorization.policy.GrantAdministrationPolicy;
import dev.ainer.authorization.policy.ScopePermissionCeiling;
import dev.ainer.module.ai.agent.AiAgentModuleConfiguration;
import dev.ainer.module.ai.gateway.application.AiGatewayActingGrantGuard;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.ainer.core.uuid.Uuidv7;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G3-A1 委托检查点端到端（ADR-0043）：Agent 注册 → 委托签发（子集校验）→ 检查 ALLOW →
 * Agent 退役/principal Binding 撤销/grant 撤销任一发生后，同一检查点立即拒绝（拉取式，
 * 无缓存）。真实 PostgreSQL + 真 JWT 管理链。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AgentDelegationFlowTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.authorization.enabled=true",
                "ainer.security.resource-server.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off",
                "ainer.ai.test-agent-flow=true"
        })
@AutoConfigureTestRestTemplate
class AgentDelegationFlowTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final UUID WORKSPACE_ID =
            UUID.fromString("019c5000-0000-7000-8000-000000000001");
    private static final UUID RESOURCE_ID =
            UUID.fromString("019c5000-0000-7000-8000-000000000002");
    private static final PermissionCode DELEGABLE_WRITE =
            new PermissionCode("agentflow.resource.write");
    private static final PermissionCode NON_DELEGABLE_WRITE =
            new PermissionCode("agentflow.nondelegable.write");
    private static final PermissionCode AGENTS_MANAGE = new PermissionCode("ai.agents.manage");
    private static final PermissionCode AI_INVOKE = new PermissionCode("ai.invoke");
    private static final ResourceType FLOW_RESOURCE = new ResourceType("agentflow.resource");
    private static final ResourceType REQUEST_RESOURCE = new ResourceType("request");
    private static final String WORKER = "agentflow-worker";
    private static final RSAKey RSA_JWK = JwtTestSupport.generateRsaKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_agent_flow_test")
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
    ActingGrantApplicationService actingGrants;

    private RestTestClient client;

    @BeforeEach
    void cleanSeedAndAuthenticate() {
        jdbcTemplate.execute("DELETE FROM ainer_authorization_acting_grant_permission");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_acting_grant");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_decision_audit");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_change_audit");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_subject_set_binding");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_subject_binding");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role_permission");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_permission");
        jdbcTemplate.execute("DELETE FROM ainer_ai_agent_definition");
        seedPermission("agentflow.resource.write", "agentflow.resource", true, "MEDIUM");
        seedPermission("agentflow.nondelegable.write", "agentflow.resource", false, "MEDIUM");
        seedPermission("ai.agents.manage", "request", false, "MEDIUM");
        seedPermission("ai.invoke", "request", true, "MEDIUM");
        client = RestTestClient.forLocalServer(restTemplate, port);
        authenticate(JwtTestSupport.signServiceJwt(RSA_JWK, ISSUER, AUDIENCE, "xq-ops",
                "authorization.manage ai.agents.manage"));
        bindServiceOps(AGENTS_MANAGE.value());
    }

    private void seedPermission(String code, String resourceType, boolean delegable, String riskTier) {
        jdbcTemplate.update("""
                INSERT INTO ainer_authorization_permission
                    (code, action, resource_type, risk_tier, audit_level, system_only,
                     agent_delegable, source_module, definition_version, created_at, updated_at)
                VALUES (?, 'write', ?, ?, 'ON_DECISION', false, ?,
                        'agentflow-test', 1, now(), now())
                """, code, resourceType, riskTier, delegable);
    }

    private void bindServiceOps(String permission) {
        RestResponse role = client.postJson("/api/authorization/roles", """
                {"code": "flow-ops-%s", "name": "Flow Ops", "permissions": ["%s"]}
                """.formatted(permission, permission));
        assertThat(role.status().value()).isEqualTo(201);
        String roleId = (String) role.jsonPath("$.data.id");
        jdbcTemplate.update("""
                INSERT INTO ainer_authorization_subject_binding
                    (id, issuer, subject_type, subject_id, role_id, scope_kind, workspace_id,
                     valid_from, status, version, created_at, updated_at)
                VALUES (?, ?, 'SERVICE', 'xq-ops', ?, 'WORKSPACE', ?, now(), 'ACTIVE', 1, now(), now())
                """, Uuidv7.generate(), ISSUER, UUID.fromString(roleId), WORKSPACE_ID);
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

    private String registerAgent(String version) {
        RestResponse created = client.postJson("/api/ai/agents", """
                {"code": "flow-agent", "version": "%s", "purpose": "流程测试 Agent"}
                """.formatted(version));
        assertThat(created.status().value()).isEqualTo(201);
        return (String) created.jsonPath("$.data.id");
    }

    private String createRoleAndBindWorker(String permission) {
        RestResponse role = client.postJson("/api/authorization/roles", """
                {"code": "flow-worker-%s", "name": "Flow Worker", "permissions": ["%s"]}
                """.formatted(permission, permission));
        assertThat(role.status().value()).isEqualTo(201);
        String roleId = (String) role.jsonPath("$.data.id");
        RestResponse binding = client.postJson("/api/authorization/bindings", """
                {"issuer": "%s", "subjectType": "USER", "subjectId": "%s", "roleId": "%s",
                 "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(ISSUER, WORKER, roleId, WORKSPACE_ID));
        assertThat(binding.status().value()).isEqualTo(201);
        return (String) binding.jsonPath("$.data.id");
    }

    private String issueGrant(String agentId, String permission) {
        RestResponse grant = client.postJson("/api/authorization/acting-grants", """
                {"principalIssuer": "%s", "principalSubjectId": "%s", "principalSubjectType": "USER",
                 "agentId": "%s", "agentVersion": "v1", "permissions": ["%s"],
                 "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(ISSUER, WORKER, agentId, permission, WORKSPACE_ID));
        assertThat(grant.status().value()).isEqualTo(201);
        return (String) grant.jsonPath("$.data.id");
    }

    private ActingGrantApplicationService.DelegationCheck check(String agentId) {
        return actingGrants.check(
                new SubjectRef(ISSUER, WORKER, SubjectType.USER),
                UUID.fromString(agentId),
                DELEGABLE_WRITE,
                new ResourceRef(WORKSPACE_ID, FLOW_RESOURCE, RESOURCE_ID),
                "agentflow-test");
    }

    @Test
    void pageRejectsInvalidSize() {
        RestResponse response = client.get("/api/ai/agents?page=1&size=101");
        assertThat(response.status().value()).isEqualTo(422);
        assertThat(response.jsonPath("$.code")).isEqualTo("AINER.AI_AGENT.INVALID_PAGE");
    }

    @Test
    void gatewayActingGrantCheckAllowsThenDeniesOnRetire() {
        String agentId = registerAgent("v-acting");
        createRoleAndBindWorker(AI_INVOKE.value());
        issueGrant(agentId, AI_INVOKE.value());

        authenticate(JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE, WORKER, "ai.invoke"));
        RestResponse personnel = client.postJson("/api/ai/acting-probe", "{}");
        assertThat(personnel.status().value()).isEqualTo(200);

        RestResponse missingWorkspace = client.postJson("/api/ai/acting-probe", """
                {"actingAgentId": "%s"}
                """.formatted(agentId));
        assertThat(missingWorkspace.status().value()).isEqualTo(422);
        assertThat(missingWorkspace.jsonPath("$.code")).isEqualTo("AINER.AI.INVALID_ACTING_CONTEXT");

        RestResponse allowed = client.postJson("/api/ai/acting-probe", """
                {"actingAgentId": "%s", "workspaceId": "%s"}
                """.formatted(agentId, WORKSPACE_ID));
        assertThat(allowed.status().value()).isEqualTo(200);

        authenticate(JwtTestSupport.signServiceJwt(RSA_JWK, ISSUER, AUDIENCE, "xq-ops",
                "authorization.manage ai.agents.manage"));
        RestResponse retired = client.postJson("/api/ai/agents/" + agentId + "/retirements", "{}");
        assertThat(retired.status().value()).isEqualTo(200);

        authenticate(JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE, WORKER, "ai.invoke"));
        RestResponse denied = client.postJson("/api/ai/acting-probe", """
                {"actingAgentId": "%s", "workspaceId": "%s"}
                """.formatted(agentId, WORKSPACE_ID));
        assertThat(denied.status().value()).isEqualTo(403);
    }

    @Test
    void delegationLifecycleAllowsThenDeniesOnRetireShrinkAndRevoke() {
        String agentId = registerAgent("v1");
        createRoleAndBindWorker(DELEGABLE_WRITE.value());
        issueGrant(agentId, DELEGABLE_WRITE.value());

        assertThat(check(agentId).allowed()).isTrue();

        // Agent 退役 → 检查点立即拒绝
        RestResponse retired = client.postJson("/api/ai/agents/" + agentId + "/retirements", "{}");
        assertThat(retired.status().value()).isEqualTo(200);
        ActingGrantApplicationService.DelegationCheck afterRetire = check(agentId);
        assertThat(afterRetire.allowed()).isFalse();
        assertThat(afterRetire.reason()).isEqualTo("AGENT_RETIRED");
    }

    @Test
    void principalBindingRevocationShrinksTheGrantImmediately() {
        String agentId = registerAgent("v2");
        String bindingId = createRoleAndBindWorker(DELEGABLE_WRITE.value());
        issueGrant(agentId, DELEGABLE_WRITE.value());
        assertThat(check(agentId).allowed()).isTrue();

        client.postJson("/api/authorization/bindings/" + bindingId + "/revocations",
                "{\"reason\": \"role removed\"}");
        ActingGrantApplicationService.DelegationCheck shrunk = check(agentId);
        assertThat(shrunk.allowed()).isFalse();
        assertThat(shrunk.reason()).isEqualTo("PRINCIPAL_SHRUNK");
    }

    @Test
    void grantRevocationDeniesNextCheckpoint() {
        String agentId = registerAgent("v3");
        createRoleAndBindWorker(DELEGABLE_WRITE.value());
        String grantId = issueGrant(agentId, DELEGABLE_WRITE.value());
        assertThat(check(agentId).allowed()).isTrue();

        RestResponse revoked = client.postJson(
                "/api/authorization/acting-grants/" + grantId + "/revocations",
                "{\"reason\": \"delegation ended\"}");
        assertThat(revoked.status().value()).isEqualTo(200);
        assertThat(check(agentId).allowed()).isFalse();
    }

    @Test
    void issueEnforcesDelegabilityAndSubset() {
        String agentId = registerAgent("v4");
        createRoleAndBindWorker(DELEGABLE_WRITE.value());

        // 不可委托权限 → 422
        RestResponse nonDelegable = client.postJson("/api/authorization/acting-grants", """
                {"principalIssuer": "%s", "principalSubjectId": "%s", "principalSubjectType": "USER",
                 "agentId": "%s", "agentVersion": "v1", "permissions": ["agentflow.nondelegable.write"],
                 "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(ISSUER, WORKER, agentId, WORKSPACE_ID));
        assertThat(nonDelegable.status().value()).isEqualTo(422);

        // principal 没有的权限（未绑定）→ 422：超集拒绝
        jdbcTemplate.update("""
                INSERT INTO ainer_authorization_permission
                    (code, action, resource_type, risk_tier, audit_level, system_only,
                     agent_delegable, source_module, definition_version, created_at, updated_at)
                VALUES ('agentflow.other.write', 'write', 'agentflow.resource', 'MEDIUM',
                        'ON_DECISION', false, true, 'agentflow-test', 1, now(), now())
                """);
        RestResponse superset = client.postJson("/api/authorization/acting-grants", """
                {"principalIssuer": "%s", "principalSubjectId": "%s", "principalSubjectType": "USER",
                 "agentId": "%s", "agentVersion": "v1", "permissions": ["agentflow.other.write"],
                 "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(ISSUER, WORKER, agentId, WORKSPACE_ID));
        assertThat(superset.status().value()).isEqualTo(422);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({AiAgentModuleConfiguration.class, AuthorizationModuleConfiguration.class})
    static class TestApplication {

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.ai.test-agent-flow", havingValue = "true")
        @Bean
        @Primary
        JwtDecoder agentFlowJwtDecoder() {
            return JwtTestSupport.jwtDecoder(RSA_JWK, ISSUER, AUDIENCE);
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.ai.test-agent-flow", havingValue = "true")
        @Bean
        AuthenticatedPrincipalResolver agentFlowPrincipalResolver() {
            return JwtTestSupport.principalResolver();
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.ai.test-agent-flow", havingValue = "true")
        @Bean
        dev.ainer.authorization.catalog.PermissionContributor agentFlowPermissions() {
            return () -> Set.of(
                    new Permission(DELEGABLE_WRITE, "write", FLOW_RESOURCE, RiskTier.MEDIUM,
                            AuditLevel.ON_DECISION, false, true),
                    new Permission(NON_DELEGABLE_WRITE, "write", FLOW_RESOURCE, RiskTier.MEDIUM,
                            AuditLevel.ON_DECISION, false, false),
                    new Permission(AGENTS_MANAGE, "write", REQUEST_RESOURCE, RiskTier.MEDIUM,
                            AuditLevel.ON_DECISION, false, false),
                    new Permission(AI_INVOKE, "write", REQUEST_RESOURCE, RiskTier.MEDIUM,
                            AuditLevel.ON_DECISION, false, true));
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.ai.test-agent-flow", havingValue = "true")
        @Bean
        @Primary
        ScopePermissionCeiling agentFlowScopeCeiling() {
            return (scope, permission) ->
                    (AGENTS_MANAGE.value().equals(scope) && AGENTS_MANAGE.equals(permission))
                    || (AI_INVOKE.value().equals(scope) && AI_INVOKE.equals(permission));
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.ai.test-agent-flow", havingValue = "true")
        @Bean
        @Primary
        DomainAuthorizationPolicy agentFlowDomainPolicy() {
            return new DomainAuthorizationPolicy() {
                @Override
                public GrantPath pathFor(PermissionCode permission) {
                    return AGENTS_MANAGE.equals(permission) || AI_INVOKE.equals(permission)
                            ? GrantPath.BINDING_REQUIRED : null;
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
                    return AGENTS_MANAGE.equals(permission) || AI_INVOKE.equals(permission);
                }
            };
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.ai.test-agent-flow", havingValue = "true")
        @Bean
        AiGatewayActingGrantGuard agentFlowActingGrantGuard(
                ObjectProvider<ActingGrantApplicationService> grants) {
            return new AiGatewayActingGrantGuard(grants);
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.ai.test-agent-flow", havingValue = "true")
        @Bean
        GrantAdministrationPolicy agentFlowGrantPolicy() {
            return new GrantAdministrationPolicy() {
                @Override
                public String version() {
                    return "agentflow-v1";
                }

                @Override
                public boolean isTrustedManager(AuthenticatedPrincipal actor) {
                    return actor.isService() && "xq-ops".equals(actor.subjectId());
                }

                @Override
                public boolean isPermissionAssignable(AuthenticatedPrincipal actor,
                        Permission permission) {
                    return true;
                }

                @Override
                public boolean isScopeAssignable(AuthenticatedPrincipal actor, Scope scope) {
                    return scope instanceof Scope.Workspace || scope instanceof Scope.Resource;
                }

                @Override
                public boolean isTargetAssignable(AuthenticatedPrincipal actor, SubjectRef target) {
                    return target.type() == SubjectType.USER
                            || target.type() == SubjectType.SERVICE && "xq-ops".equals(target.subjectId());
                }
            };
        }
    }

    @RestController
    @RequestMapping("/api/ai/acting-probe")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "ainer.ai.test-agent-flow", havingValue = "true")
    static class ActingGrantProbeController {

        private final AuthenticatedPrincipalResolver principalResolver;
        private final AiGatewayActingGrantGuard guard;

        public ActingGrantProbeController(
                AuthenticatedPrincipalResolver principalResolver,
                AiGatewayActingGrantGuard guard) {
            this.principalResolver = principalResolver;
            this.guard = guard;
        }

        @PostMapping
        ResponseEntity<Void> probe(@RequestBody(required = false) ActingProbeRequest body,
                HttpServletRequest request) {
            ActingProbeRequest payload = body == null ? new ActingProbeRequest(null, null) : body;
            guard.requireIfPresent(
                    principalResolver.requireCurrent(),
                    payload.actingAgentId(),
                    payload.workspaceId(),
                    request.getRequestURI());
            return ResponseEntity.ok().build();
        }
    }

    record ActingProbeRequest(UUID actingAgentId, UUID workspaceId) {
    }
}
