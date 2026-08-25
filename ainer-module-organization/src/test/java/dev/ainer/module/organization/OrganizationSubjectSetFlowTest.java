package dev.ainer.module.organization;

import com.nimbusds.jose.jwk.RSAKey;
import dev.ainer.authorization.AuthorizationModuleConfiguration;
import dev.ainer.authorization.AuthorizationService;
import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationOutcome;
import dev.ainer.authorization.domain.AuthorizationRequest;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.ainer.core.uuid.Uuidv7;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G3-O2 money test（ADR-0042 §3/§5）：岗位在岗者通过 SubjectSetBinding 获得受保护资源授权；
 * 终止其 Engagement 后，同一 subject 的决策立即翻转为 DENY——组织事实变化通过决策时实时
 * 解析传播，无事件、无缓存。真实 PostgreSQL + 真 JWT 管理链。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = OrganizationSubjectSetFlowTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.organization.enabled=true",
                "ainer.organization.trusted-issuer=https://auth.ainer.test",
                "ainer.organization.test-subject-set-flow=true",
                "ainer.authorization.enabled=true",
                "ainer.security.resource-server.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
@AutoConfigureTestRestTemplate
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class OrganizationSubjectSetFlowTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final UUID WORKSPACE_ID =
            UUID.fromString("019c4000-0000-7000-8000-0000000000aa");
    private static final UUID RESOURCE_ID =
            UUID.fromString("019c4000-0000-7000-8000-0000000000bb");
    private static final PermissionCode PROTECTED_WRITE =
            new PermissionCode("orgflow.resource.write");
    private static final ResourceType PROTECTED_RESOURCE =
            new ResourceType("orgflow.resource");
    private static final String PROTECTED_SCOPE = "orgflow.resources.write";
    private static final String WORKER_SUBJECT = "orgflow-worker";
    private static final RSAKey RSA_JWK = JwtTestSupport.generateRsaKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_org_set_flow_test")
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
    void cleanAndAuthenticateAsOps() {
        jdbcTemplate.execute("DELETE FROM ainer_authorization_decision_audit");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_change_audit");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_subject_set_binding");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_subject_binding");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role_permission");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_permission");
        jdbcTemplate.execute("DELETE FROM ainer_org_change_audit");
        jdbcTemplate.execute("DELETE FROM ainer_org_position_assignment");
        jdbcTemplate.execute("DELETE FROM ainer_org_position");
        jdbcTemplate.execute("DELETE FROM ainer_org_unit_assignment");
        jdbcTemplate.execute("DELETE FROM ainer_org_engagement");
        jdbcTemplate.execute("DELETE FROM ainer_org_unit_parent");
        jdbcTemplate.execute("DELETE FROM ainer_org_unit");
        jdbcTemplate.execute("DELETE FROM ainer_org_directory");
        jdbcTemplate.update("""
                INSERT INTO ainer_authorization_permission
                    (code, action, resource_type, risk_tier, audit_level, system_only,
                     agent_delegable, source_module, definition_version, created_at, updated_at)
                VALUES ('orgflow.resource.write', 'write', 'orgflow.resource', 'MEDIUM',
                        'ON_DECISION', false, false, 'orgflow-test', 1, now(), now())
                """);

        client = RestTestClient.forLocalServer(restTemplate, port);
        authenticate(JwtTestSupport.signServiceJwt(
                RSA_JWK, ISSUER, AUDIENCE, "xq-ops", "authorization.manage organization.read organization.manage"));
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

    private AuthorizationDecision decideAsWorker() {
        SubjectRef subject = new SubjectRef(ISSUER, WORKER_SUBJECT, SubjectType.USER);
        return authorizationService.authorize(new AuthorizationRequest(
                new Requester.Authenticated(
                        subject, Set.of(PROTECTED_SCOPE), Set.of(AUDIENCE), "orgflow-test"),
                AccessMode.AUTHENTICATED,
                PROTECTED_WRITE,
                new ResourceRef(WORKSPACE_ID, PROTECTED_RESOURCE, RESOURCE_ID),
                new AuthorizationContext(Instant.now(), AuthorizationContext.Assurance.NONE,
                        "orgflow-test", "req-orgflow", null)));
    }

    private String jsonPost(String path, String body) {
        RestResponse response = client.postJson(path, body);
        assertThat(response.status().value())
                .as("POST %s -> %s", path, response.body())
                .isEqualTo(201);
        return (String) response.jsonPath("$.data.id");
    }

    @org.junit.jupiter.api.Order(1)
    @Test
    void positionAssigneeGainsGrantAndTerminationRevokesItImmediately() {
        // 1) 组织事实：目录 + Unit + 岗位 + 任职 + 分配 + 岗位任职
        String directoryId = jsonPost("/api/organization/directories", """
                {"workspaceId": "%s", "code": "flow", "displayName": "流程目录"}
                """.formatted(WORKSPACE_ID));
        String rootUnitId = (String) client
                .get("/api/organization/directories/" + directoryId + "/units")
                .jsonPath("$.data[0].id");
        String unitId = jsonPost("/api/organization/directories/" + directoryId + "/units", """
                {"parentUnitId": "%s", "code": "ops", "displayName": "运营"}
                """.formatted(rootUnitId));
        Instant past = Instant.now().minusSeconds(3600);
        String engagementId = jsonPost("/api/organization/directories/" + directoryId
                + "/engagements", """
                {"subjectIssuer": "%s", "subjectId": "%s", "engagementType": "EMPLOYEE",
                 "validFrom": "%s"}
                """.formatted(ISSUER, WORKER_SUBJECT, past));
        String assignmentId = jsonPost("/api/organization/directories/" + directoryId
                + "/unit-assignments", """
                {"engagementId": "%s", "orgUnitId": "%s", "kind": "PRIMARY", "validFrom": "%s"}
                """.formatted(engagementId, unitId, past));
        String positionId = jsonPost("/api/organization/directories/" + directoryId + "/positions", """
                {"orgUnitId": "%s", "code": "operator", "displayName": "运营专员"}
                """.formatted(unitId));
        jsonPost("/api/organization/directories/" + directoryId + "/position-assignments", """
                {"positionId": "%s", "engagementId": "%s", "unitAssignmentId": "%s",
                 "kind": "PRIMARY", "validFrom": "%s"}
                """.formatted(positionId, engagementId, assignmentId, past));

        // 2) 授权：Role + SubjectSetBinding（workforce.position#assignee → WORKSPACE 范围）
        RestResponse roleCreated = client.postJson("/api/authorization/roles", """
                {"code": "orgflow-operator", "name": "Orgflow Operator",
                 "permissions": ["orgflow.resource.write"]}
                """);
        assertThat(roleCreated.status().value()).isEqualTo(201);
        String roleId = (String) roleCreated.jsonPath("$.data.id");
        RestResponse setBindingCreated = client.postJson("/api/authorization/set-bindings", """
                {"setObjectType": "workforce.position", "setObjectId": "%s",
                 "setRelation": "assignee", "setWorkspaceId": "%s", "setDirectoryId": "%s",
                 "roleId": "%s", "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(positionId, WORKSPACE_ID, directoryId, roleId, WORKSPACE_ID));
        assertThat(setBindingCreated.status().value()).isEqualTo(201);

        // 3) 在岗者：无任何直接 Binding，仅凭岗位成员资格获得 ALLOW
        assertThat(decideAsWorker().outcome()).isEqualTo(AuthorizationOutcome.ALLOW);

        // 4) 终止 Engagement（组织事实变化）——同一 subject 下一次决策立即 DENY
        RestResponse terminated = client.postJson("/api/organization/directories/" + directoryId
                + "/engagements/" + engagementId + "/terminations", "{}");
        assertThat(terminated.status().value()).isEqualTo(200);

        assertThat(decideAsWorker().outcome()).isEqualTo(AuthorizationOutcome.DENY);
    }

    @org.junit.jupiter.api.Order(2)
    @Test
    void crossWorkspaceSetDeclarationCannotEscalateMembership() {
        // 岗位事实属于 WORKSPACE_ID；恶意 SubjectSetBinding 声明 otherWorkspace + scope(otherWorkspace)。
        // 修复前：membership 只按 positionId+subject 查询 → 误判 MEMBER → 跨工作区提权。
        // 修复后：解析按声明 workspace 过滤目录事实 → NOT_MEMBER。
        var principal = new dev.ainer.security.token.AuthenticatedPrincipal(
                new dev.ainer.security.principal.HumanSubjectRef(
                        new dev.ainer.security.principal.IdentityAuthorityRef(ISSUER), "account:ops"),
                new dev.ainer.security.principal.IdentityAuthorityRef(ISSUER),
                dev.ainer.security.token.TokenProfile.USER_NEUTRAL_V1, "1",
                java.util.Set.of(AUDIENCE),
                java.util.Set.of("organization.read", "organization.manage"), "pwd", null, 0L);
        var directory = directoryService.createDirectory(
                principal, null, WORKSPACE_ID, "xws", "跨工作区目录");
        UUID rootUnitId = directoryService.unitTree(principal, directory.id()).get(0).id();
        var unit = directoryService.createUnit(principal, null, directory.id(), rootUnitId, "ops2", "运营2");
        Instant past = Instant.now().minusSeconds(3600);
        var engagement = workforceService.engage(principal, null, directory.id(),
                ISSUER, WORKER_SUBJECT, "EMPLOYEE", null, past, null);
        var assignment = workforceService.assignUnit(principal, null, directory.id(),
                engagement.id(), unit.id(),
                dev.ainer.module.organization.orgdir.domain.AssignmentKind.PRIMARY, past, null);
        var position = workforceService.createPosition(principal, null, directory.id(),
                unit.id(), "buyer2", "采购2");
        workforceService.assignPosition(principal, null, directory.id(), position.id(),
                engagement.id(), assignment.id(),
                dev.ainer.module.organization.orgdir.domain.AssignmentKind.PRIMARY, past, null);

        UUID otherWorkspace = UUID.fromString("019c4000-0000-7000-8000-0000000000ff");
        var membership = new dev.ainer.module.organization.orgdir.access.WorkforcePositionMembershipResolver(
                workforceRepositoryBean);
        var result = membership.resolve(
                new dev.ainer.authorization.domain.SubjectRef(
                        ISSUER, WORKER_SUBJECT, dev.ainer.authorization.domain.SubjectType.USER),
                new dev.ainer.authorization.domain.SubjectSetRef(
                        "workforce.position", position.id(), "assignee", otherWorkspace, null),
                Instant.now());
        org.assertj.core.api.Assertions.assertThat(result.isMember()).isFalse();
        // 同工作区声明仍解析为 MEMBER（正向对照）
        var sameWorkspace = membership.resolve(
                new dev.ainer.authorization.domain.SubjectRef(
                        ISSUER, WORKER_SUBJECT, dev.ainer.authorization.domain.SubjectType.USER),
                new dev.ainer.authorization.domain.SubjectSetRef(
                        "workforce.position", position.id(), "assignee", WORKSPACE_ID, null),
                Instant.now());
        org.assertj.core.api.Assertions.assertThat(sameWorkspace.isMember()).isTrue();
    }

    @org.junit.jupiter.api.Order(3)
    @Test
    void assignPositionAlertsWhenAssigneeCreatedTheSetBinding() {
        String directoryId = jsonPost("/api/organization/directories", """
                {"workspaceId": "%s", "code": "alert", "displayName": "告警目录"}
                """.formatted(WORKSPACE_ID));
        String rootUnitId = (String) client
                .get("/api/organization/directories/" + directoryId + "/units")
                .jsonPath("$.data[0].id");
        String unitId = jsonPost("/api/organization/directories/" + directoryId + "/units", """
                {"parentUnitId": "%s", "code": "sec", "displayName": "安全"}
                """.formatted(rootUnitId));
        Instant past = Instant.now().minusSeconds(3600);
        String engagementId = jsonPost("/api/organization/directories/" + directoryId
                + "/engagements", """
                {"subjectIssuer": "%s", "subjectId": "%s", "engagementType": "EMPLOYEE",
                 "validFrom": "%s"}
                """.formatted(ISSUER, WORKER_SUBJECT, past));
        String assignmentId = jsonPost("/api/organization/directories/" + directoryId
                + "/unit-assignments", """
                {"engagementId": "%s", "orgUnitId": "%s", "kind": "PRIMARY", "validFrom": "%s"}
                """.formatted(engagementId, unitId, past));
        String selfPositionId = jsonPost("/api/organization/directories/" + directoryId
                + "/positions", """
                {"orgUnitId": "%s", "code": "self-created", "displayName": "自建岗"}
                """.formatted(unitId));
        String otherPositionId = jsonPost("/api/organization/directories/" + directoryId
                + "/positions", """
                {"orgUnitId": "%s", "code": "other-created", "displayName": "他人岗"}
                """.formatted(unitId));

        RestResponse roleCreated = client.postJson("/api/authorization/roles", """
                {"code": "orgflow-alert", "name": "Orgflow Alert",
                 "permissions": ["orgflow.resource.write"]}
                """);
        assertThat(roleCreated.status().value()).isEqualTo(201);
        String roleId = (String) roleCreated.jsonPath("$.data.id");

        UUID selfBindingId = Uuidv7.generate();
        jdbcTemplate.update("""
                INSERT INTO ainer_authorization_subject_set_binding
                    (id, set_object_type, set_object_id, set_relation, set_workspace_id,
                     set_directory_id, role_id, scope_kind, workspace_id, valid_from, status,
                     version, created_at, updated_at)
                VALUES (?, 'workforce.position', ?, 'assignee', ?, ?, ?, 'WORKSPACE', ?, now(),
                        'ACTIVE', 1, now(), now())
                """, selfBindingId, UUID.fromString(selfPositionId), WORKSPACE_ID,
                UUID.fromString(directoryId), UUID.fromString(roleId), WORKSPACE_ID);
        jdbcTemplate.update("""
                INSERT INTO ainer_authorization_change_audit
                    (actor_issuer, actor_type, actor_id, target_type, target_id, action,
                     after_version, occurred_at)
                VALUES (?, 'USER', ?, 'SET_BINDING', ?, 'CREATE', 1, now())
                """, ISSUER, WORKER_SUBJECT, selfBindingId);

        RestResponse otherBinding = client.postJson("/api/authorization/set-bindings", """
                {"setObjectType": "workforce.position", "setObjectId": "%s",
                 "setRelation": "assignee", "setWorkspaceId": "%s", "setDirectoryId": "%s",
                 "roleId": "%s", "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(otherPositionId, WORKSPACE_ID, directoryId, roleId, WORKSPACE_ID));
        assertThat(otherBinding.status().value()).isEqualTo(201);

        jsonPost("/api/organization/directories/" + directoryId + "/position-assignments", """
                {"positionId": "%s", "engagementId": "%s", "unitAssignmentId": "%s",
                 "kind": "PRIMARY", "validFrom": "%s"}
                """.formatted(selfPositionId, engagementId, assignmentId, past));
        jsonPost("/api/organization/directories/" + directoryId + "/position-assignments", """
                {"positionId": "%s", "engagementId": "%s", "unitAssignmentId": "%s",
                 "kind": "SECONDARY", "validFrom": "%s"}
                """.formatted(otherPositionId, engagementId, assignmentId, past));

        Integer alerts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_org_change_audit "
                        + "WHERE operation = 'DELAYED_SELF_ELEVATION'",
                Integer.class);
        Integer assigned = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_org_change_audit "
                        + "WHERE entity_type = 'POSITION_ASSIGNMENT' AND operation = 'ASSIGNED'",
                Integer.class);
        assertThat(assigned).isEqualTo(2);
        assertThat(alerts).isEqualTo(1);
    }

    @org.springframework.beans.factory.annotation.Autowired
    dev.ainer.module.organization.orgdir.application.DirectoryApplicationService directoryService;

    @org.springframework.beans.factory.annotation.Autowired
    dev.ainer.module.organization.orgdir.application.WorkforceApplicationService workforceService;

    @org.springframework.beans.factory.annotation.Autowired
    dev.ainer.module.organization.orgdir.application.WorkforceRepository workforceRepositoryBean;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({OrganizationModuleConfiguration.class, AuthorizationModuleConfiguration.class})
    static class TestApplication {

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.organization.test-subject-set-flow", havingValue = "true")
        @Bean
        @Primary
        JwtDecoder orgflowJwtDecoder() {
            return JwtTestSupport.jwtDecoder(RSA_JWK, ISSUER, AUDIENCE);
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.organization.test-subject-set-flow", havingValue = "true")
        @Bean
        AuthenticatedPrincipalResolver orgflowPrincipalResolver() {
            return JwtTestSupport.principalResolver();
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.organization.test-subject-set-flow", havingValue = "true")
        @Bean
        dev.ainer.authorization.catalog.PermissionContributor orgflowPermissions() {
            ResourceType requestResource = new ResourceType("request");
            return () -> Set.of(
                    new Permission(
                            PROTECTED_WRITE, "write", PROTECTED_RESOURCE, RiskTier.MEDIUM,
                            AuditLevel.ON_DECISION, false, false),
                    new Permission(
                            new PermissionCode("organization.read"), "read", requestResource,
                            RiskTier.LOW, AuditLevel.ON_DECISION, false, true),
                    new Permission(
                            new PermissionCode("organization.manage"), "write", requestResource,
                            RiskTier.MEDIUM, AuditLevel.ON_DECISION, false, true));
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.organization.test-subject-set-flow", havingValue = "true")
        @Bean
        @Primary
        ScopePermissionCeiling orgflowScopeCeiling() {
            return (scope, permission) -> (PROTECTED_SCOPE.equals(scope)
                    && PROTECTED_WRITE.equals(permission))
                    || (scope.equals(permission.value())
                    && ("organization.read".equals(scope) || "organization.manage".equals(scope)));
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.organization.test-subject-set-flow", havingValue = "true")
        @Bean
        @Primary
        DomainAuthorizationPolicy orgflowDomainPolicy() {
            return new DomainAuthorizationPolicy() {
                @Override
                public GrantPath pathFor(PermissionCode permission) {
                    if (PROTECTED_WRITE.equals(permission)) {
                        return GrantPath.BINDING_REQUIRED;
                    }
                    // 本测试装配拦截器；组织管理面走关系路径，不要求 Binding。
                    if ("organization.read".equals(permission.value())
                            || "organization.manage".equals(permission.value())) {
                        return GrantPath.RELATION_DERIVED;
                    }
                    return null;
                }

                @Override
                public boolean relationGrants(Requester.Authenticated subject,
                        PermissionCode permission, ResourceRef resource,
                        AuthorizationContext context) {
                    return "organization.read".equals(permission.value())
                            || "organization.manage".equals(permission.value());
                }

                @Override
                public boolean resourceStateSatisfies(Requester.Authenticated subject,
                        PermissionCode permission, ResourceRef resource,
                        AuthorizationContext context) {
                    return true;
                }
            };
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.organization.test-subject-set-flow", havingValue = "true")
        @Bean
        GrantAdministrationPolicy orgflowGrantAdministrationPolicy() {
            return new GrantAdministrationPolicy() {
                @Override
                public String version() {
                    return "orgflow-administration-v1";
                }

                @Override
                public boolean isTrustedManager(AuthenticatedPrincipal actor) {
                    return actor.isService() && "xq-ops".equals(actor.subjectId());
                }

                @Override
                public boolean isPermissionAssignable(AuthenticatedPrincipal actor,
                        Permission permission) {
                    return PROTECTED_WRITE.equals(permission.code());
                }

                @Override
                public boolean isScopeAssignable(AuthenticatedPrincipal actor, Scope scope) {
                    return scope instanceof Scope.Workspace || scope instanceof Scope.Resource;
                }

                @Override
                public boolean isTargetAssignable(AuthenticatedPrincipal actor, SubjectRef target) {
                    return target.type() == SubjectType.USER;
                }
            };
        }
    }
}
