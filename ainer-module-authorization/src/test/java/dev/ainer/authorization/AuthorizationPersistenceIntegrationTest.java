package dev.ainer.authorization;

import dev.ainer.authorization.application.RoleApplicationService;
import dev.ainer.authorization.application.RoleRepository;
import dev.ainer.authorization.application.SubjectBindingApplicationService;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.authorization.infrastructure.PostgresBindingResolver;
import dev.ainer.authorization.policy.BindingResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the authorization persistence slice (ADR-0030 S1). Runs against a real
 * PostgreSQL 18.3 Testcontainers instance, exercises the full MyBatis→domain mapping path, and
 * verifies revocation semantics and scope CHECK constraints.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AuthorizationPersistenceIntegrationTest.TestApplication.class, properties = {
        "ainer.authorization.enabled=true",
        "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
        "spring.main.banner-mode=off"
})
class AuthorizationPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_authorization_test")
                    .withUsername("ainer")
                    .withPassword("ainer");

    @org.springframework.test.context.DynamicPropertySource
    static void datasource(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    RoleApplicationService roleService;
    @Autowired
    SubjectBindingApplicationService bindingService;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    BindingResolver bindingResolver;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final PermissionCode READ = new PermissionCode("test.resource.read");
    private static final PermissionCode WRITE = new PermissionCode("test.resource.write");
    private static final SubjectRef USER_A =
            new SubjectRef("ainer-test", "user-a", SubjectType.USER);
    private static final SubjectRef SERVICE_X =
            new SubjectRef("ainer-test", "svc-x", SubjectType.SERVICE);

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("DELETE FROM ainer_authorization_subject_binding");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role_permission");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_permission");
        seedPermissions();
    }

    private void seedPermissions() {
        jdbcTemplate.update("""
                INSERT INTO ainer_authorization_permission
                    (code, action, resource_type, risk_tier, audit_level, system_only, agent_delegable, definition_version, created_at, updated_at)
                VALUES (?, 'read', 'test.resource', 'LOW', 'ON_DECISION', false, false, 1, now(), now())
                """, READ.value());
        jdbcTemplate.update("""
                INSERT INTO ainer_authorization_permission
                    (code, action, resource_type, risk_tier, audit_level, system_only, agent_delegable, definition_version, created_at, updated_at)
                VALUES (?, 'write', 'test.resource', 'MEDIUM', 'ON_DECISION', false, false, 1, now(), now())
                """, WRITE.value());
    }

    @Test
    void flywayMigrationCreatedAllSixTables() {
        for (String table : new String[]{
                "ainer_authorization_permission",
                "ainer_authorization_role",
                "ainer_authorization_role_permission",
                "ainer_authorization_subject_binding",
                "ainer_authorization_change_audit",
                "ainer_authorization_decision_audit"}) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT to_regclass(?)", String.class, "public." + table))
                    .as("table %s should exist", table)
                    .isNotNull();
        }
    }

    @Test
    void roleLifecycleCreateFindReplacePermissions() {
        UUID roleId = roleService.createRole("editor", "Editor", Set.of(READ));

        RoleRepository.RoleRecord loaded = roleService.getRole(roleId);
        assertThat(loaded.role().code()).isEqualTo("editor");
        assertThat(loaded.role().name()).isEqualTo("Editor");
        assertThat(loaded.role().permissions()).containsExactly(READ);
        assertThat(loaded.createdAt()).isNotNull();
        assertThat(loaded.updatedAt()).isNotNull();

        roleService.replacePermissions(roleId, Set.of(READ, WRITE), loaded.version());

        RoleRepository.RoleRecord reloaded = roleService.getRole(roleId);
        assertThat(reloaded.role().permissions()).containsExactlyInAnyOrder(READ, WRITE);
        assertThat(reloaded.version()).isEqualTo(loaded.version() + 1);
        // createdAt 不变，updatedAt 应在 replacePermissions 后刷新
        assertThat(reloaded.createdAt()).isEqualTo(loaded.createdAt());
        assertThat(reloaded.updatedAt()).isAfterOrEqualTo(loaded.updatedAt());
    }

    @Test
    void duplicateRoleCodeFailsClosed() {
        roleService.createRole("admin", "Admin", Set.of(READ));
        assertThatThrownBy(() -> roleService.createRole("admin", "Other", Set.of(WRITE)))
                .isInstanceOf(dev.ainer.core.error.BusinessException.class);
    }

    @Test
    void unregisteredPermissionCannotBeAssignedToRole() {
        PermissionCode unknown = new PermissionCode("test.resource.delete");
        assertThatThrownBy(() -> roleService.createRole("killer", "Killer", Set.of(unknown)))
                .isInstanceOf(dev.ainer.core.error.BusinessException.class);
    }

    @Test
    void bindingCreateRevokeAndResolverReflectsImmediately() {
        UUID roleId = roleService.createRole("editor", "Editor", Set.of(READ, WRITE));
        UUID workspaceId = UUID.randomUUID();
        Scope scope = new Scope.Workspace(workspaceId);

        UUID bindingId = bindingService.createBinding(
                USER_A, roleId, scope, Instant.now().minusSeconds(60), null);

        // Live binding is resolvable immediately.
        assertThat(bindingResolver.liveBindings(USER_A))
                .as("live binding should be resolvable before revocation")
                .hasSize(1);

        // Revoke — still-valid JWT cannot restore the grant.
        bindingService.revokeBinding(bindingId, "policy changed");

        assertThat(bindingResolver.liveBindings(USER_A))
                .as("revoked binding must not appear in live bindings")
                .isEmpty();
    }

    @Test
    void expiredBindingExcludedFromLiveBindings() {
        UUID roleId = roleService.createRole("editor", "Editor", Set.of(READ));
        UUID workspaceId = UUID.randomUUID();

        bindingService.createBinding(
                USER_A, roleId, new Scope.Workspace(workspaceId),
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));

        assertThat(bindingResolver.liveBindings(USER_A))
                .as("expired binding must not appear in live bindings")
                .isEmpty();
    }

    @Test
    void scopeCheckConstraintRejectsInvalidWorkspaceCombination() {
        UUID roleId = roleService.createRole("editor", "Editor", Set.of(READ));
        UUID workspaceId = UUID.randomUUID();

        // WORKSPACE scope with resource columns populated should violate the CHECK constraint.
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO ainer_authorization_subject_binding (
                    issuer, subject_type, subject_id, role_id, scope_kind,
                    workspace_id, resource_type, resource_id,
                    valid_from, status, version, created_at, updated_at
                ) VALUES (?, 'USER', ?, ?, 'WORKSPACE', ?, 'test.resource', ?,
                          now(), 'ACTIVE', 0, now(), now())
                """, "ainer-test", "user-b", roleId, workspaceId, workspaceId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void scopeCheckConstraintRejectsGlobalWithWorkspace() {
        UUID roleId = roleService.createRole("editor", "Editor", Set.of(READ));

        // GLOBAL scope with workspace_id populated should violate the CHECK constraint.
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO ainer_authorization_subject_binding (
                    issuer, subject_type, subject_id, role_id, scope_kind,
                    workspace_id, resource_type, resource_id,
                    valid_from, status, version, created_at, updated_at
                ) VALUES (?, 'SERVICE', ?, ?, 'GLOBAL', ?,
                          NULL, NULL, now(), 'ACTIVE', 0, now(), now())
                """, "ainer-test", "svc-y", roleId, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void postgresBindingResolverProducesDomainSubjectBinding() {
        UUID roleId = roleService.createRole("editor", "Editor", Set.of(READ, WRITE));
        UUID workspaceId = UUID.randomUUID();

        bindingService.createBinding(
                USER_A, roleId, new Scope.Workspace(workspaceId),
                Instant.now().minusSeconds(60), null);

        var live = bindingResolver.liveBindings(USER_A);
        assertThat(live).hasSize(1);
        var binding = live.iterator().next();
        assertThat(binding.subject()).isEqualTo(USER_A);
        assertThat(binding.role().code()).isEqualTo("editor");
        assertThat(binding.role().grants(READ)).isTrue();
        assertThat(binding.role().grants(WRITE)).isTrue();
        assertThat(binding.scope()).isInstanceOf(Scope.Workspace.class);
    }

    @TestConfiguration
    static class TestPermissionContributor {
        @org.springframework.context.annotation.Bean
        dev.ainer.authorization.catalog.PermissionContributor persistenceTestPermissions() {
            return () -> java.util.Set.of(
                    new dev.ainer.authorization.domain.Permission(
                            READ, "read", new ResourceType("test.resource"),
                            RiskTier.LOW, AuditLevel.ON_DECISION, false, false),
                    new dev.ainer.authorization.domain.Permission(
                            WRITE, "write", new ResourceType("test.resource"),
                            RiskTier.MEDIUM, AuditLevel.ON_DECISION, false, false));
        }
    }

    @org.springframework.boot.SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({AuthorizationModuleConfiguration.class, TestPermissionContributor.class})
    static class TestApplication {
    }
}
