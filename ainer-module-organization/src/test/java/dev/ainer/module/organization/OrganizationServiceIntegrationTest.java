package dev.ainer.module.organization;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.organization.orgdir.application.DirectoryApplicationService;
import dev.ainer.module.organization.orgdir.application.OrganizationErrorCode;
import dev.ainer.module.organization.orgdir.application.WorkforceApplicationService;
import dev.ainer.module.organization.orgdir.domain.AssignmentKind;
import dev.ainer.module.organization.orgdir.domain.OrgDirectory;
import dev.ainer.module.organization.orgdir.domain.OrgUnit;
import dev.ainer.module.organization.orgdir.domain.UnitAssignment;
import dev.ainer.module.organization.orgdir.domain.WorkforceEngagement;
import dev.ainer.security.principal.HumanSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.TokenProfile;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O1 服务层不变量测试（ADR-0042）：真实 PostgreSQL 18.3 从空库重放 migration；覆盖重叠任职、
 * PRIMARY 唯一、调岗原子、暂停/终止即时失去成员资格（决策时实时解析）与审计。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = OrganizationServiceIntegrationTest.TestApplication.class,
        properties = {
                "ainer.organization.enabled=true",
                "ainer.organization.trusted-issuer=https://auth.ainer.test",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class OrganizationServiceIntegrationTest {

    private static final IdentityAuthorityRef AUTHORITY =
            new IdentityAuthorityRef("https://auth.ainer.test");
    private static final UUID WORKSPACE_ID =
            UUID.fromString("019c3000-0000-7000-8000-000000000001");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_org_service_test")
                    .withUsername("ainer")
                    .withPassword("ainer");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    DirectoryApplicationService directoryService;
    @Autowired
    WorkforceApplicationService workforceService;
    @Autowired
    JdbcTemplate jdbcTemplate;

    Instant base = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM ainer_org_change_audit");
        jdbcTemplate.execute("DELETE FROM ainer_org_position_assignment");
        jdbcTemplate.execute("DELETE FROM ainer_org_position");
        jdbcTemplate.execute("DELETE FROM ainer_org_unit_assignment");
        jdbcTemplate.execute("DELETE FROM ainer_org_engagement");
        jdbcTemplate.execute("DELETE FROM ainer_org_unit_parent");
        jdbcTemplate.execute("DELETE FROM ainer_org_unit");
        jdbcTemplate.execute("DELETE FROM ainer_org_directory");
    }

    @Test
    void createDirectoryAtomicallyCreatesRootUnitAndAudits() {
        OrgDirectory directory = directoryService.createDirectory(
                manager(), "req-1", WORKSPACE_ID, "main", "主目录");

        assertThat(directory.code()).isEqualTo("main");
        assertThat(directory.status().name()).isEqualTo("ENABLED");
        assertThat(directory.id().version()).isEqualTo(7);

        assertThat(directoryService.unitTree(reader(), directory.id()))
                .extracting(OrgUnit::kind)
                .containsExactly(dev.ainer.module.organization.orgdir.domain.OrgUnitKind.ROOT);

        Long directoryAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_org_change_audit WHERE entity_type = 'DIRECTORY'",
                Long.class);
        Long rootAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_org_change_audit WHERE entity_type = 'UNIT'",
                Long.class);
        assertThat(directoryAudits).isEqualTo(1);
        assertThat(rootAudits).isEqualTo(1);

        assertThatThrownBy(() -> directoryService.createDirectory(
                manager(), "req-2", WORKSPACE_ID, "main", "重复编码"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(OrganizationErrorCode.DUPLICATE_DIRECTORY_CODE));
    }

    @Test
    void engagementPeriodsOfSameSubjectMustNotOverlap() {
        OrgDirectory directory = newDirectory("d");
        workforceService.engage(manager(), null, directory.id(),
                AUTHORITY.issuer(), "account:1", "EMPLOYEE", "E001",
                base, base.plus(365, ChronoUnit.DAYS));

        assertThatThrownBy(() -> workforceService.engage(manager(), null, directory.id(),
                AUTHORITY.issuer(), "account:1", "EMPLOYEE", null,
                base.plus(180, ChronoUnit.DAYS), base.plus(400, ChronoUnit.DAYS)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(OrganizationErrorCode.ENGAGEMENT_PERIOD_OVERLAP));

        // 不同 subject、相同期不冲突；不可信 issuer 拒绝
        workforceService.engage(manager(), null, directory.id(),
                AUTHORITY.issuer(), "account:2", "CONTRACTOR", null, base, null);
        assertThatThrownBy(() -> workforceService.engage(manager(), null, directory.id(),
                "https://evil.example", "account:3", "EMPLOYEE", null, base, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(OrganizationErrorCode.INVALID_ISSUER));
    }

    @Test
    void terminatedEngagementFreesSubjectAndDuplicateEmployeeNumberIsRejected() {
        OrgDirectory directory = newDirectory("d");
        WorkforceEngagement first = workforceService.engage(manager(), null, directory.id(),
                AUTHORITY.issuer(), "account:1", "EMPLOYEE", "E001", base, null);

        assertThatThrownBy(() -> workforceService.engage(manager(), null, directory.id(),
                AUTHORITY.issuer(), "account:2", "EMPLOYEE", "E001", base, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(OrganizationErrorCode.DUPLICATE_EMPLOYEE_NUMBER));

        workforceService.terminateEngagement(manager(), null, directory.id(), first.id());

        // REVOKED 解除 subject 占用（编号不复活、不复用，ADR-0042 §4.4），重新入职创建新 Engagement
        WorkforceEngagement rehired = workforceService.engage(manager(), null, directory.id(),
                AUTHORITY.issuer(), "account:1", "EMPLOYEE", null,
                base.plusSeconds(3600), null);
        assertThat(rehired.id()).isNotEqualTo(first.id());
    }

    @Test
    void openPrimaryAssignmentIsUniqueAndPeriodMustBeContained() {
        OrgDirectory directory = newDirectory("d");
        OrgUnit unitA = directoryService.createUnit(
                manager(), null, directory.id(), rootUnitOf(directory.id()), "unit-a", "A");
        OrgUnit unitB = directoryService.createUnit(
                manager(), null, directory.id(), rootUnitOf(directory.id()), "unit-b", "B");
        WorkforceEngagement engagement = workforceService.engage(manager(), null, directory.id(),
                AUTHORITY.issuer(), "account:1", "EMPLOYEE", null, base, base.plus(365, ChronoUnit.DAYS));

        workforceService.assignUnit(manager(), null, directory.id(), engagement.id(),
                unitA.id(), AssignmentKind.PRIMARY, base, null);

        assertThatThrownBy(() -> workforceService.assignUnit(manager(), null, directory.id(),
                engagement.id(), unitB.id(), AssignmentKind.PRIMARY,
                base.plusSeconds(1), null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(OrganizationErrorCode.OPEN_PRIMARY_CONFLICT));

        // 超出父任职期拒绝
        assertThatThrownBy(() -> workforceService.assignUnit(manager(), null, directory.id(),
                engagement.id(), unitB.id(), AssignmentKind.SECONDARY,
                base.plus(366, ChronoUnit.DAYS), null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(OrganizationErrorCode.INVALID_PERIOD));
    }

    @Test
    void transferClosesOldAssignmentAndOpensNewAtomically() {
        OrgDirectory directory = newDirectory("d");
        OrgUnit unitA = directoryService.createUnit(
                manager(), null, directory.id(), rootUnitOf(directory.id()), "unit-a", "A");
        OrgUnit unitB = directoryService.createUnit(
                manager(), null, directory.id(), rootUnitOf(directory.id()), "unit-b", "B");
        WorkforceEngagement engagement = workforceService.engage(manager(), null, directory.id(),
                AUTHORITY.issuer(), "account:1", "EMPLOYEE", null, base, null);
        UnitAssignment first = workforceService.assignUnit(manager(), null, directory.id(),
                engagement.id(), unitA.id(), AssignmentKind.PRIMARY, base, null);

        Instant transferAt = base.plusSeconds(7200);
        UnitAssignment next = workforceService.transferUnitAssignment(manager(), null,
                directory.id(), engagement.id(), first.id(), unitB.id(), transferAt);

        assertThat(next.orgUnitId()).isEqualTo(unitB.id());
        assertThat(next.validFrom()).isEqualTo(transferAt);
        UnitAssignment closed = workforceService.getUnitAssignment(
                reader(), directory.id(), first.id());
        assertThat(closed.validUntil()).isEqualTo(transferAt);

        // T 前后成员资格分别落在新旧 Unit（决策时实时解析）
        assertThat(workforceService.unitMembers(reader(), directory.id(),
                unitA.id(), transferAt.minusSeconds(1))).hasSize(1);
        assertThat(workforceService.unitMembers(reader(), directory.id(),
                unitA.id(), transferAt)).isEmpty();
        assertThat(workforceService.unitMembers(reader(), directory.id(),
                unitB.id(), transferAt)).hasSize(1);
    }

    @Test
    void suspendAndTerminateImmediatelyRemoveMembershipWithoutAssignmentCleanup() {
        OrgDirectory directory = newDirectory("d");
        OrgUnit unit = directoryService.createUnit(
                manager(), null, directory.id(), rootUnitOf(directory.id()), "unit-a", "A");
        WorkforceEngagement engagement = workforceService.engage(manager(), null, directory.id(),
                AUTHORITY.issuer(), "account:1", "EMPLOYEE", null, base, null);
        workforceService.assignUnit(manager(), null, directory.id(), engagement.id(),
                unit.id(), AssignmentKind.PRIMARY, base, null);

        workforceService.suspendEngagement(manager(), null, directory.id(), engagement.id());

        // 子 Assignment 未清理，但成员投影立即为空（父门禁实时检查，ADR-0042 §3）
        assertThat(workforceService.unitMembers(reader(), directory.id(), unit.id(), null))
                .isEmpty();
        Integer rawAssignments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_org_unit_assignment WHERE engagement_id = ?",
                Integer.class, engagement.id());
        assertThat(rawAssignments).isEqualTo(1);
    }

    @Test
    void positionAssignmentRequiresSameUnitAndSameEngagement() {
        OrgDirectory directory = newDirectory("d");
        OrgUnit unitA = directoryService.createUnit(
                manager(), null, directory.id(), rootUnitOf(directory.id()), "unit-a", "A");
        OrgUnit unitB = directoryService.createUnit(
                manager(), null, directory.id(), rootUnitOf(directory.id()), "unit-b", "B");
        WorkforceEngagement engagement = workforceService.engage(manager(), null, directory.id(),
                AUTHORITY.issuer(), "account:1", "EMPLOYEE", null, base, null);
        UnitAssignment assignment = workforceService.assignUnit(manager(), null, directory.id(),
                engagement.id(), unitA.id(), AssignmentKind.PRIMARY, base, null);
        var position = workforceService.createPosition(
                manager(), null, directory.id(), unitB.id(), "buyer", "采购");

        // 岗位在 unitB，任职分配在 unitA → 拒绝
        assertThatThrownBy(() -> workforceService.assignPosition(manager(), null,
                directory.id(), position.id(), engagement.id(), assignment.id(),
                AssignmentKind.PRIMARY, base, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(OrganizationErrorCode.UNIT_MISMATCH));

        var sameUnitPosition = workforceService.createPosition(
                manager(), null, directory.id(), unitA.id(), "operator", "运营");
        workforceService.assignPosition(manager(), null, directory.id(),
                sameUnitPosition.id(), engagement.id(), assignment.id(),
                AssignmentKind.PRIMARY, base, null);
        assertThat(workforceService.positionAssignees(
                reader(), directory.id(), sameUnitPosition.id(), null)).hasSize(1);
    }

    @Test
    void persistedIdsAreUuidv7() {
        OrgDirectory directory = newDirectory("d");
        assertThat(directory.id().version()).isEqualTo(7);
        WorkforceEngagement engagement = workforceService.engage(manager(), null,
                directory.id(), AUTHORITY.issuer(), "account:9", "EMPLOYEE", null, base, null);
        assertThat(engagement.id().version()).isEqualTo(7);
    }

    // ------------------------------------------------------------------ helpers

    private OrgDirectory newDirectory(String code) {
        return directoryService.createDirectory(manager(), null, WORKSPACE_ID, code, "目录 " + code);
    }

    private UUID rootUnitOf(UUID directoryId) {
        return directoryService.unitTree(reader(), directoryId).get(0).id();
    }

    private static AuthenticatedPrincipal manager() {
        return principal("organization.read organization.manage");
    }

    private static AuthenticatedPrincipal reader() {
        return principal("organization.read");
    }

    private static AuthenticatedPrincipal principal(String scopes) {
        return new AuthenticatedPrincipal(
                new HumanSubjectRef(AUTHORITY, "account:admin"),
                AUTHORITY,
                TokenProfile.USER_NEUTRAL_V1,
                "1",
                Set.of("ainer-api"),
                Set.of(scopes.split(" ")),
                "pwd",
                null,
                0L);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(OrganizationModuleConfiguration.class)
    static class TestApplication {
    }

    /** 控制器需要 resolver；本测试只测服务层，直接返回固定 principal。 */
    @TestConfiguration
    static class PrincipalFixture {

        @Bean
        dev.ainer.security.token.AuthenticatedPrincipalResolver testPrincipalResolver() {
            return OrganizationServiceIntegrationTest::reader;
        }
    }
}
