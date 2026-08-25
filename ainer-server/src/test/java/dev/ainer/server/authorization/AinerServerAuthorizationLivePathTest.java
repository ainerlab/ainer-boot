package dev.ainer.server.authorization;

import com.nimbusds.jose.jwk.RSAKey;
import dev.ainer.authorization.AuthorizationService;
import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationOutcome;
import dev.ainer.authorization.domain.AuthorizationRequest;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.ainer.authorization.spring.AinerAuthorize;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 授权引擎生产路径验证（ADR-0037 参考装配）：平台权限目录同步、scope 恒等天花板、
 * BINDING_REQUIRED 策略与管理面白名单全部激活——引擎不再是全 deny 的死链。
 * 通过真实管理 API 创建 Role/Binding 后，AuthorizationService 对持有 Binding 的主体
 * 返回 ALLOW；撤销后立即 DENY。管理面 fail-closed：未配置白名单时管理操作拒绝。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AinerServerAuthorizationLivePathTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off",
                "ainer.authorization.trusted-managers=https://auth.ainer.test|platform-ops",
                "ainer.security.resource-server.enabled=true",
                "ainer.server.test-authz-live=true"
        })
@AutoConfigureTestRestTemplate
class AinerServerAuthorizationLivePathTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final UUID WORKSPACE_ID =
            UUID.fromString("019c7000-0000-7000-8000-000000000001");
    private static final UUID OTHER_WORKSPACE_ID =
            UUID.fromString("019c7000-0000-7000-8000-000000000099");
    private static final RSAKey RSA_JWK = JwtTestSupport.generateRsaKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_server_authz_live_test")
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
        jdbcTemplate.execute("DELETE FROM ainer_authorization_acting_grant_permission");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_acting_grant");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_subject_binding");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role_permission");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role");
        // 目录投影保留（启动同步写入），只清 Role/Binding
        client = RestTestClient.forLocalServer(restTemplate, port);
        authenticate(JwtTestSupport.signServiceJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-ops", "authorization.manage"));
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

    private AuthorizationDecision decideAsUser(String subjectId, String permissionCode) {
        SubjectRef subject = new SubjectRef(ISSUER, subjectId, SubjectType.USER);
        return authorizationService.authorize(new AuthorizationRequest(
                new Requester.Authenticated(
                        subject, Set.of("workspace.read", "workspace.write"),
                        Set.of(AUDIENCE), "authz-live-test"),
                AccessMode.AUTHENTICATED,
                new PermissionCode(permissionCode),
                new ResourceRef(WORKSPACE_ID, new ResourceType("request"),
                        UUID.nameUUIDFromBytes("/api/workspaces".getBytes())),
                new AuthorizationContext(
                        Instant.now(), AuthorizationContext.Assurance.NONE,
                        "authz-live-test", "req-authz-live", null)));
    }

    @Test
    void startupCatalogSyncPopulatesPlatformPermissions() {
        Integer synced = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_authorization_permission WHERE source_module = 'ainer-server'",
                Integer.class);
        assertThat(synced).isEqualTo(21);
    }

    @Test
    void bindingGrantsAllowAndRevocationDeniesImmediately() {
        // 引擎激活前：无 Binding → DENY
        assertThat(decideAsUser("platform-user-1", "workspace.read").outcome())
                .isEqualTo(AuthorizationOutcome.DENY);

        // 管理面：创建 Role + WORKSPACE Binding
        RestResponse role = client.postJson("/api/authorization/roles", """
                {"code": "workspace-viewer", "name": "Workspace Viewer",
                 "permissions": ["workspace.read"]}
                """);
        assertThat(role.status().value()).isEqualTo(201);
        String roleId = (String) role.jsonPath("$.data.id");

        RestResponse binding = client.postJson("/api/authorization/bindings", """
                {"issuer": "%s", "subjectType": "USER", "subjectId": "platform-user-1",
                 "roleId": "%s", "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(ISSUER, roleId, WORKSPACE_ID));
        assertThat(binding.status().value()).isEqualTo(201);
        String bindingId = (String) binding.jsonPath("$.data.id");

        // 有 Binding → ALLOW（scope 恒等天花板 + BINDING_REQUIRED 全链激活）
        assertThat(decideAsUser("platform-user-1", "workspace.read").outcome())
                .isEqualTo(AuthorizationOutcome.ALLOW);

        // 撤销 → 下一次决策立即 DENY（拉取式，无缓存）
        RestResponse revoked = client.postJson(
                "/api/authorization/bindings/" + bindingId + "/revocations",
                "{\"reason\": \"access removed\"}");
        assertThat(revoked.status().value()).isEqualTo(200);
        assertThat(decideAsUser("platform-user-1", "workspace.read").outcome())
                .isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void managementIsFailClosedWithoutTrustedManager() {
        // 白名单外 SERVICE（持 authorization.manage scope）→ 管理操作拒绝
        authenticate(JwtTestSupport.signServiceJwt(
                RSA_JWK, ISSUER, AUDIENCE, "rogue-manager", "authorization.manage"));
        RestResponse response = client.postJson("/api/authorization/roles", """
                {"code": "rogue", "name": "Rogue", "permissions": ["workspace.read"]}
                """);
        assertThat(response.status().value()).isEqualTo(403);
        assertThat(response.jsonPath("$.code"))
                .isEqualTo("AINER.AUTHORIZATION.GRANT_ADMINISTRATION_DENIED");

        // 拒绝本身持久化为对 authorization.manage 的 DENY 决策审计（可告警、可回溯）
        Integer denials = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_authorization_decision_audit "
                        + "WHERE permission_code = 'authorization.manage' "
                        + "AND outcome = 'DENY' AND requester_id = 'rogue-manager'",
                Integer.class);
        assertThat(denials).isEqualTo(1);
    }

    @Test
    void scopeCeilingDeniesUnregisteredPermission() {
        // 天花板拦截半边：仅持 workspace scope 的 USER 请求 organization.manage → DENY
        assertThat(decideAsUser("platform-user-2", "organization.manage").outcome())
                .isEqualTo(AuthorizationOutcome.DENY);
        // 同主体请求清单内但未授 Binding 的权限同样 DENY（BINDING_REQUIRED）
        assertThat(decideAsUser("platform-user-2", "workspace.read").outcome())
                .isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void annotatedProbeDeniesWithoutBindingAndAllowsWithBinding() {
        authenticate(JwtTestSupport.signUserJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-user-1", "workspace.read"));
        RestResponse denied = client.get("/api/authz-live-probe");
        assertThat(denied.status().value()).isEqualTo(403);

        authenticate(JwtTestSupport.signServiceJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-ops", "authorization.manage"));
        RestResponse role = client.postJson("/api/authorization/roles", """
                {"code": "workspace-reader-http", "name": "Workspace Reader HTTP",
                 "permissions": ["workspace.read"]}
                """);
        assertThat(role.status().value()).isEqualTo(201);
        String roleId = (String) role.jsonPath("$.data.id");
        RestResponse binding = client.postJson("/api/authorization/bindings", """
                {"issuer": "%s", "subjectType": "USER", "subjectId": "platform-user-1",
                 "roleId": "%s", "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(ISSUER, roleId, WORKSPACE_ID));
        assertThat(binding.status().value()).isEqualTo(201);

        authenticate(JwtTestSupport.signUserJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-user-1", "workspace.read"));
        RestResponse allowed = client.get("/api/authz-live-probe");
        assertThat(allowed.status().value()).isEqualTo(200);
    }

    @Test
    void workspacePathProbeRequiresBindingForThatWorkspace() {
        authenticate(JwtTestSupport.signServiceJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-ops", "authorization.manage"));
        RestResponse role = client.postJson("/api/authorization/roles", """
                {"code": "workspace-reader-scoped", "name": "Workspace Reader Scoped",
                 "permissions": ["workspace.read"]}
                """);
        assertThat(role.status().value()).isEqualTo(201);
        String roleId = (String) role.jsonPath("$.data.id");
        RestResponse binding = client.postJson("/api/authorization/bindings", """
                {"issuer": "%s", "subjectType": "USER", "subjectId": "platform-user-1",
                 "roleId": "%s", "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(ISSUER, roleId, WORKSPACE_ID));
        assertThat(binding.status().value()).isEqualTo(201);

        authenticate(JwtTestSupport.signUserJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-user-1", "workspace.read"));
        RestResponse allowed = client.get("/api/workspaces/" + WORKSPACE_ID + "/authz-live-probe");
        assertThat(allowed.status().value()).isEqualTo(200);
        RestResponse denied = client.get("/api/workspaces/" + OTHER_WORKSPACE_ID + "/authz-live-probe");
        assertThat(denied.status().value()).isEqualTo(403);
    }

    @Test
    void fileProbeDeniesWithoutBindingAndAllowsWithFileBinding() {
        authenticate(JwtTestSupport.signUserJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-user-1", "file.read"));
        RestResponse denied = client.get("/api/authz-file-probe");
        assertThat(denied.status().value()).isEqualTo(403);

        authenticate(JwtTestSupport.signServiceJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-ops", "authorization.manage"));
        RestResponse role = client.postJson("/api/authorization/roles", """
                {"code": "file-reader-http", "name": "File Reader HTTP",
                 "permissions": ["file.read"]}
                """);
        assertThat(role.status().value()).isEqualTo(201);
        String roleId = (String) role.jsonPath("$.data.id");
        RestResponse binding = client.postJson("/api/authorization/bindings", """
                {"issuer": "%s", "subjectType": "USER", "subjectId": "platform-user-1",
                 "roleId": "%s", "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(ISSUER, roleId, WORKSPACE_ID));
        assertThat(binding.status().value()).isEqualTo(201);

        authenticate(JwtTestSupport.signUserJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-user-1", "file.read"));
        RestResponse allowed = client.get("/api/authz-file-probe");
        assertThat(allowed.status().value()).isEqualTo(200);
    }

    @Test
    void configProbeDeniesWithoutBindingAndAllowsWithConfigBinding() {
        authenticate(JwtTestSupport.signUserJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-user-1", "config.read"));
        RestResponse denied = client.get("/api/authz-config-probe");
        assertThat(denied.status().value()).isEqualTo(403);

        authenticate(JwtTestSupport.signServiceJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-ops", "authorization.manage"));
        RestResponse role = client.postJson("/api/authorization/roles", """
                {"code": "config-reader-http", "name": "Config Reader HTTP",
                 "permissions": ["config.read"]}
                """);
        assertThat(role.status().value()).isEqualTo(201);
        String roleId = (String) role.jsonPath("$.data.id");
        RestResponse binding = client.postJson("/api/authorization/bindings", """
                {"issuer": "%s", "subjectType": "USER", "subjectId": "platform-user-1",
                 "roleId": "%s", "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(ISSUER, roleId, WORKSPACE_ID));
        assertThat(binding.status().value()).isEqualTo(201);

        authenticate(JwtTestSupport.signUserJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-user-1", "config.read"));
        RestResponse allowed = client.get("/api/authz-config-probe");
        assertThat(allowed.status().value()).isEqualTo(200);
    }

    @Test
    void notificationSubmitProbeDeniesWithoutBindingAndAllowsWithSubmitBinding() {
        authenticate(JwtTestSupport.signUserJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-user-1", "notification.submit"));
        RestResponse denied = client.get("/api/authz-notification-probe");
        assertThat(denied.status().value()).isEqualTo(403);

        authenticate(JwtTestSupport.signServiceJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-ops", "authorization.manage"));
        RestResponse role = client.postJson("/api/authorization/roles", """
                {"code": "notification-submitter-http", "name": "Notification Submitter HTTP",
                 "permissions": ["notification.submit"]}
                """);
        assertThat(role.status().value()).isEqualTo(201);
        String roleId = (String) role.jsonPath("$.data.id");
        RestResponse binding = client.postJson("/api/authorization/bindings", """
                {"issuer": "%s", "subjectType": "USER", "subjectId": "platform-user-1",
                 "roleId": "%s", "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(ISSUER, roleId, WORKSPACE_ID));
        assertThat(binding.status().value()).isEqualTo(201);

        authenticate(JwtTestSupport.signUserJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-user-1", "notification.submit"));
        RestResponse allowed = client.get("/api/authz-notification-probe");
        assertThat(allowed.status().value()).isEqualTo(200);
    }

    @Test
    void requestWithoutTokenIsUnauthorized() {
        restTemplate.getRestTemplate().setInterceptors(List.of());
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/authorization/permissions", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            AinerServerAuthorizationPolicyConfiguration.class,
            dev.ainer.authorization.AuthorizationModuleConfiguration.class,
            WorkspaceReadProbeController.class,
            WorkspaceScopedProbeController.class,
            FileReadProbeController.class,
            ConfigReadProbeController.class,
            NotificationSubmitProbeController.class
    })
    static class TestApplication {

        // Clock 由授权模块自带（authorizationClock），无需外部提供

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.server.test-authz-live", havingValue = "true")
        @Bean
        @Primary
        JwtDecoder authzLiveJwtDecoder() {
            return JwtTestSupport.jwtDecoder(RSA_JWK, ISSUER, AUDIENCE);
        }
    }

    @RestController
    @RequestMapping("/api/authz-live-probe")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "ainer.server.test-authz-live", havingValue = "true")
    static class WorkspaceReadProbeController {

        @GetMapping
        @AinerAuthorize(permission = "workspace.read")
        public ResponseEntity<Void> peek() {
            return ResponseEntity.ok().build();
        }
    }

    @RestController
    @RequestMapping("/api/workspaces")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "ainer.server.test-authz-live", havingValue = "true")
    static class WorkspaceScopedProbeController {

        @GetMapping("/{workspaceId}/authz-live-probe")
        @AinerAuthorize(permission = "workspace.read")
        public ResponseEntity<Void> peekWorkspace(@PathVariable UUID workspaceId) {
            return ResponseEntity.ok().build();
        }
    }

    @RestController
    @RequestMapping("/api/authz-file-probe")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "ainer.server.test-authz-live", havingValue = "true")
    static class FileReadProbeController {

        @GetMapping
        @AinerAuthorize(permission = "file.read")
        public ResponseEntity<Void> peekFile() {
            return ResponseEntity.ok().build();
        }
    }

    @RestController
    @RequestMapping("/api/authz-config-probe")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "ainer.server.test-authz-live", havingValue = "true")
    static class ConfigReadProbeController {

        @GetMapping
        @AinerAuthorize(permission = "config.read")
        public ResponseEntity<Void> peekConfig() {
            return ResponseEntity.ok().build();
        }
    }

    @RestController
    @RequestMapping("/api/authz-notification-probe")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "ainer.server.test-authz-live", havingValue = "true")
    static class NotificationSubmitProbeController {

        @GetMapping
        @AinerAuthorize(permission = "notification.submit")
        public ResponseEntity<Void> peekNotificationSubmit() {
            return ResponseEntity.ok().build();
        }
    }
}
