package dev.ainer.module.workspace.workspace;

import dev.ainer.module.workspace.WorkspaceModuleConfiguration;
import dev.ainer.module.workspace.workspace.application.CreateWorkspaceCommand;
import dev.ainer.core.error.BusinessException;
import dev.ainer.module.workspace.workspace.application.WorkspaceErrorCode;
import dev.ainer.module.workspace.workspace.application.WorkspaceApplicationService;
import dev.ainer.module.workspace.workspace.domain.Workspace;
import dev.ainer.security.principal.HumanSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.security.token.TokenProfile;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = WorkspaceModuleIntegrationTest.TestApplication.class,
        properties = {
                "ainer.workspace.enabled=true",
                "ainer.workspace.test-module-integration=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class WorkspaceModuleIntegrationTest {

    private static final IdentityAuthorityRef AUTHORITY = new IdentityAuthorityRef("https://auth.ainer.test");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_workspace_test")
            .withUsername("ainer")
            .withPassword("ainer");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WorkspaceApplicationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ainer_workspace_security_operation_audit");
        jdbcTemplate.update("DELETE FROM ainer_workspace_owner_recovery_request");
        jdbcTemplate.update("DELETE FROM ainer_workspace_authorization_audit_archive");
        jdbcTemplate.update("DELETE FROM ainer_workspace_authorization_audit");
        jdbcTemplate.update("DELETE FROM ainer_workspace_member");
        jdbcTemplate.update("DELETE FROM ainer_workspace");
    }

    @Test
    void migrationAndTablesAreStandalone() {
        assertThat(flyway.info().applied()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name LIKE 'ainer_workspace%'
                  AND column_name = 'tenant_id'
                """,
                Integer.class)).isZero();
    }

    @Test
    void realPostgresWorkspaceAuthorizationUsesMembershipAndRejectsCrossWorkspaceAccess() {
        AuthenticatedPrincipal first = human("account:first");
        AuthenticatedPrincipal second = human("account:second");
        Workspace firstWorkspace = service.create(first, new CreateWorkspaceCommand("第一空间"));
        Workspace secondWorkspace = service.create(second, new CreateWorkspaceCommand("第二空间"));

        assertThat(firstWorkspace.id().version()).isEqualTo(7);
        assertThat(service.get(first, firstWorkspace.id()).id()).isEqualTo(firstWorkspace.id());
        assertThatThrownBy(() -> service.get(first, secondWorkspace.id()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(WorkspaceErrorCode.NOT_FOUND));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_workspace_member WHERE workspace_id = ? AND role = 'OWNER'",
                Integer.class, firstWorkspace.id())).isEqualTo(1);
    }

    private static AuthenticatedPrincipal human(String accountId) {
        return new AuthenticatedPrincipal(
                new HumanSubjectRef(AUTHORITY, accountId), AUTHORITY,
                TokenProfile.USER_NEUTRAL_V1, "1", Set.of("ainer-api"),
                Set.of("workspace.read", "workspace.write"), "pwd", null, 0L);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({WorkspaceModuleConfiguration.class, TestSecurityConfiguration.class})
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "ainer.workspace.test-module-integration", havingValue = "true")
    static class TestSecurityConfiguration {
        @Bean
        AuthenticatedPrincipalResolver authenticatedPrincipalResolver() {
            return () -> human("account:test");
        }
    }
}
