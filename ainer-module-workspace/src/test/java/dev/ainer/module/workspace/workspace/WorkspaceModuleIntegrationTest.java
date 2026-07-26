package dev.ainer.module.workspace.workspace;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.workspace.WorkspaceModuleConfiguration;
import dev.ainer.module.workspace.workspace.application.AddWorkspaceMemberCommand;
import dev.ainer.module.workspace.workspace.application.CreateWorkspaceCommand;
import dev.ainer.module.workspace.workspace.application.ChangeWorkspaceMemberRoleCommand;
import dev.ainer.module.workspace.workspace.application.RemoveWorkspaceMemberCommand;
import dev.ainer.module.workspace.workspace.application.TransferWorkspaceOwnershipCommand;
import dev.ainer.module.workspace.workspace.application.WorkspaceApplicationService;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditExportBatch;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditLifecycleService;
import dev.ainer.module.workspace.workspace.application.WorkspaceErrorCode;
import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEvent;
import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEventConsumer;
import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEventRepository;
import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEventResult;
import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEventType;
import dev.ainer.module.workspace.workspace.application.WorkspaceMemberRepository;
import dev.ainer.module.workspace.workspace.application.WorkspaceOwnerRecoveryRequest;
import dev.ainer.module.workspace.workspace.application.WorkspaceOwnerRecoveryService;
import dev.ainer.module.workspace.workspace.application.WorkspaceRepository;
import dev.ainer.module.workspace.workspace.domain.Workspace;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMember;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMemberStatus;
import dev.ainer.module.workspace.workspace.domain.WorkspaceName;
import dev.ainer.module.workspace.workspace.domain.WorkspaceRole;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.TenantId;
import dev.ainer.module.workspace.workspace.infrastructure.mybatis.MybatisWorkspaceMemberRepository;
import dev.ainer.module.workspace.workspace.infrastructure.mybatis.MybatisWorkspaceIdentityAccessEventRepository;
import dev.ainer.security.actor.AuthenticatedActor;
import dev.ainer.security.actor.AuthenticatedActorResolver;
import dev.ainer.web.request.RequestIdFilter;
import dev.ainer.web.request.RequestIds;
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
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = WorkspaceModuleIntegrationTest.TestApplication.class,
        properties = {
                "ainer.workspace.enabled=true",
                "mybatis.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class WorkspaceModuleIntegrationTest {

    private static final String TENANT_ID = "tenant:workspace-test";
    private static final AuthenticatedActor OWNER = actor(
            "subject:owner-1", TENANT_ID,
            "SCOPE_workspace.read", "SCOPE_workspace.write", "SCOPE_workspace.audit.read");

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
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ControllableMemberRepository memberRepository;

    @Autowired
    private WorkspaceIdentityAccessEventConsumer accessEventConsumer;

    @Autowired
    private ControllableIdentityAccessEventRepository accessEventRepository;

    @Autowired
    private WorkspaceOwnerRecoveryService ownerRecoveryService;

    @Autowired
    private WorkspaceAuthorizationAuditLifecycleService auditLifecycleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private RequestIdFilter requestIdFilter;

    @Autowired
    private TestActorResolver actorResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ainer_workspace_security_operation_audit");
        jdbcTemplate.update("DELETE FROM ainer_workspace_owner_recovery_request");
        jdbcTemplate.update("DELETE FROM ainer_workspace_authorization_audit_archive");
        jdbcTemplate.update("DELETE FROM ainer_workspace_identity_event_receipt");
        jdbcTemplate.update("DELETE FROM ainer_workspace_authorization_audit");
        jdbcTemplate.update("DELETE FROM ainer_workspace");
        memberRepository.reset();
        accessEventRepository.reset();
        actorResolver.use(OWNER);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilter(requestIdFilter)
                .build();
    }

    @Test
    void migrationCreatesSchemaAndValidatesCleanly() {
        assertThat(flyway.info().applied()).hasSize(8);
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name IN "
                        + "('ainer_workspace', 'ainer_workspace_member', "
                        + "'ainer_workspace_authorization_audit', "
                        + "'ainer_workspace_identity_event_receipt', "
                        + "'ainer_workspace_owner_recovery_request', "
                        + "'ainer_workspace_security_operation_audit', "
                        + "'ainer_workspace_authorization_audit_archive')",
                Integer.class);
        assertThat(tableCount).isEqualTo(7);
        Integer tenantColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND column_name = 'tenant_id' "
                        + "AND table_name IN ('ainer_workspace', 'ainer_workspace_member')",
                Integer.class);
        assertThat(tenantColumnCount).isEqualTo(2);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void revokedOwnerRecoveryRequiresTwoServicesAndPromotesAnActiveMember() {
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        AuthenticatedActor owner = actor(
                ownerId.toString(), tenantId.toString(),
                "SCOPE_workspace.read", "SCOPE_workspace.write");
        AuthenticatedActor target = actor(
                targetId.toString(), tenantId.toString(),
                "SCOPE_workspace.read", "SCOPE_workspace.write");
        Workspace workspace = service.create(owner, new CreateWorkspaceCommand("OWNER恢复空间"));
        service.addMember(owner, workspace.id(),
                new AddWorkspaceMemberCommand(target.subjectId(), WorkspaceRole.ADMIN));
        service.acceptInvitation(target, workspace.id());
        accessEventConsumer.consume(new WorkspaceIdentityAccessEvent(
                UUID.randomUUID(), WorkspaceIdentityAccessEventType.IDENTITY_USER_DISABLED,
                tenantId, ownerId, 1, Instant.now().plusSeconds(1)));

        WorkspaceOwnerRecoveryRequest request = ownerRecoveryService.requestRecovery(
                "operator:request", new TenantId(tenantId.toString()), workspace.id(),
                new SubjectId(targetId.toString()), "INC-OWNER-RECOVERY",
                Duration.ofMinutes(15));

        assertThatThrownBy(() -> ownerRecoveryService.approveAndExecute(
                "operator:request", new TenantId(tenantId.toString()), request.id()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(WorkspaceErrorCode.OWNER_RECOVERY_APPROVER_MUST_DIFFER));

        ownerRecoveryService.approveAndExecute(
                "operator:approve", new TenantId(tenantId.toString()), request.id());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_workspace_member "
                        + "WHERE tenant_id = ? AND workspace_id = ? "
                        + "AND role = 'OWNER' AND status = 'ACTIVE'",
                Integer.class,
                tenantId.toString(),
                workspace.id())).isEqualTo(1);
        assertThat(memberStatus(tenantId, workspace.id(), ownerId)).isEqualTo("REVOKED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM ainer_workspace_member "
                        + "WHERE tenant_id = ? AND workspace_id = ? AND subject_id = ?",
                String.class,
                tenantId.toString(),
                workspace.id(),
                targetId.toString())).isEqualTo("OWNER");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_workspace_security_operation_audit "
                        + "WHERE operation_id = ?",
                Integer.class,
                request.id())).isEqualTo(2);
    }

    @Test
    void authorizationAuditArchivePreservesQueriesAndStableExport() {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("审计归档空间"));
        service.rename(OWNER, workspace.id(), "审计归档空间-2");
        long before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_workspace_authorization_audit WHERE workspace_id = ?",
                Long.class,
                workspace.id());

        int archived = auditLifecycleService.archiveBefore(Instant.now().plusSeconds(1), 1000);
        WorkspaceAuthorizationAuditExportBatch batch = auditLifecycleService.export(
                "siem:exporter", new TenantId(TENANT_ID), null, 1000);

        assertThat(archived).isGreaterThanOrEqualTo(Math.toIntExact(before));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_workspace_authorization_audit WHERE workspace_id = ?",
                Long.class,
                workspace.id())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_workspace_authorization_audit_archive WHERE workspace_id = ?",
                Long.class,
                workspace.id())).isEqualTo(before);
        assertThat(batch.items()).filteredOn(audit -> workspace.id().equals(audit.workspaceId()))
                .hasSize(Math.toIntExact(before));
        assertThat(service.authorizationAudits(OWNER, workspace.id(), 1, 100).total())
                .isGreaterThanOrEqualTo(before);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_workspace_security_operation_audit "
                        + "WHERE operation_type = 'AUTHORIZATION_AUDIT_EXPORT'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void createPersistsWorkspaceAndOwnerInOneUseCase() {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("研发空间"));

        assertThat(service.get(OWNER, workspace.id()).name().value()).isEqualTo("研发空间");
        assertThat(workspace.tenantId().value()).isEqualTo(TENANT_ID);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM ainer_workspace_member "
                        + "WHERE tenant_id = ? AND workspace_id = ? AND subject_id = ?",
                String.class,
                TENANT_ID,
                workspace.id(),
                "subject:owner-1")).isEqualTo("OWNER");
    }

    @Test
    void createRollsBackWorkspaceWhenOwnerWriteFails() {
        memberRepository.failNextInsert();

        assertThatThrownBy(() -> service.create(
                OWNER, new CreateWorkspaceCommand("回滚空间")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated member persistence failure");

        assertThat(service.page(OWNER, 1, 20).total()).isZero();
    }

    @Test
    void staleVersionCannotOverwriteANewerWorkspace() {
        Workspace stale = service.create(OWNER, new CreateWorkspaceCommand("并发空间"));
        Workspace current = service.rename(OWNER, stale.id(), "并发交付空间");
        Workspace conflicting = stale.rename(
                new WorkspaceName("过期修改空间"), current.updatedAt().plusSeconds(1));

        assertThat(workspaceRepository.update(conflicting, stale.version())).isFalse();
        assertThat(service.get(OWNER, stale.id()))
                .extracting(workspace -> workspace.name().value(), Workspace::version)
                .containsExactly("并发交付空间", 1L);
    }

    @Test
    void duplicateMemberReturnsStableConflict() throws Exception {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("成员空间"));

        mockMvc.perform(post("/api/workspaces/{id}/members", workspace.id())
                        .contentType("application/json")
                        .content("""
                                {"subjectId":"subject:owner-1","role":"MEMBER"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(WorkspaceErrorCode.MEMBER_ALREADY_EXISTS.code()))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void httpContractCreatesRenamesGetsAndPagesWorkspace() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/workspaces")
                        .header(RequestIds.HEADER, "workspace-contract-1")
                        .contentType("application/json")
                        .content("""
                                {"name":"API空间"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(RequestIds.HEADER, "workspace-contract-1"))
                .andExpect(jsonPath("$.code").value("AINER.COMMON.OK"))
                .andExpect(jsonPath("$.data.name").value("API空间"))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn();

        String body = createResult.getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.data.id");

        mockMvc.perform(patch("/api/workspaces/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {"name":"API交付空间"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("API交付空间"))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(get("/api/workspaces/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.name").value("API交付空间"));

        mockMvc.perform(get("/api/workspaces").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id));
    }

    @Test
    void rejectsInvalidRequestBeforeEnteringUseCase() throws Exception {
        mockMvc.perform(post("/api/workspaces")
                        .contentType("application/json")
                        .content("""
                                {"name":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AINER.COMMON.INVALID_REQUEST"));
    }

    @Test
    void applicationRejectsDuplicateMemberOutsideHttpToo() {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("应用空间"));

        assertThatThrownBy(() -> service.addMember(
                OWNER,
                workspace.id(),
                new AddWorkspaceMemberCommand("subject:owner-1", WorkspaceRole.ADMIN)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(WorkspaceErrorCode.MEMBER_ALREADY_EXISTS));
    }

    @Test
    void tenantAndMembershipBoundariesHideInaccessibleWorkspace() {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("隔离空间"));
        AuthenticatedActor otherTenant = actor(
                "subject:owner-1", "tenant:other", "SCOPE_workspace.read", "SCOPE_workspace.write");
        AuthenticatedActor nonMember = actor(
                "subject:outsider", TENANT_ID, "SCOPE_workspace.read", "SCOPE_workspace.write");

        assertThatThrownBy(() -> service.get(otherTenant, workspace.id()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(WorkspaceErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.get(nonMember, workspace.id()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(WorkspaceErrorCode.NOT_FOUND));
        assertThat(service.page(otherTenant, 1, 20).total()).isZero();
        assertThat(service.page(nonMember, 1, 20).total()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_workspace_authorization_audit "
                        + "WHERE workspace_id = ? AND decision = 'DENIED'",
                Integer.class,
                workspace.id())).isEqualTo(2);
    }

    @Test
    void memberCanReadButCannotManageWorkspace() {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("角色空间"));
        service.addMember(OWNER, workspace.id(),
                new AddWorkspaceMemberCommand("subject:member", WorkspaceRole.MEMBER));
        AuthenticatedActor member = actor(
                "subject:member", TENANT_ID, "SCOPE_workspace.read", "SCOPE_workspace.write");
        service.acceptInvitation(member, workspace.id());

        assertThat(service.get(member, workspace.id()).id()).isEqualTo(workspace.id());
        assertThatThrownBy(() -> service.rename(member, workspace.id(), "越权改名"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(WorkspaceErrorCode.ACCESS_DENIED));
    }

    @Test
    void ownerRoleRequiresDedicatedTransferFlow() throws Exception {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("归属空间"));

        mockMvc.perform(post("/api/workspaces/{id}/members", workspace.id())
                        .contentType("application/json")
                        .content("""
                                {"subjectId":"subject:second-owner","role":"OWNER"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(WorkspaceErrorCode.ROLE_NOT_ASSIGNABLE.code()));
    }

    @Test
    void pendingInvitationRequiresTheTrustedTargetSubjectToAccept() throws Exception {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("邀请契约空间"));

        mockMvc.perform(post("/api/workspaces/{id}/members", workspace.id())
                        .contentType("application/json")
                        .content("""
                                {"subjectId":"subject:invited","role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subjectId").value("subject:invited"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.activatedAt").doesNotExist());

        actorResolver.use(actor(
                "subject:other", TENANT_ID, "SCOPE_workspace.read", "SCOPE_workspace.write"));
        mockMvc.perform(post("/api/workspaces/{id}/membership-acceptances", workspace.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(WorkspaceErrorCode.INVITATION_NOT_FOUND.code()));

        actorResolver.use(actor(
                "subject:invited", TENANT_ID, "SCOPE_workspace.read", "SCOPE_workspace.write"));
        mockMvc.perform(get("/api/workspaces/{id}", workspace.id()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/workspaces/{id}/membership-acceptances", workspace.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.activatedAt").isNotEmpty());
        mockMvc.perform(get("/api/workspaces/{id}", workspace.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(workspace.id().toString()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_workspace_authorization_audit "
                        + "WHERE workspace_id = ? AND action = 'MEMBERSHIP_ACCEPT' "
                        + "AND decision = 'DENIED'",
                Integer.class,
                workspace.id())).isEqualTo(1);
    }

    @Test
    void roleChangeAndRemovalApplyImmediately() throws Exception {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("成员管理空间"));
        AuthenticatedActor member = actor(
                "subject:managed", TENANT_ID, "SCOPE_workspace.read", "SCOPE_workspace.write");
        service.addMember(OWNER, workspace.id(),
                new AddWorkspaceMemberCommand(member.subjectId(), WorkspaceRole.MEMBER));
        service.acceptInvitation(member, workspace.id());

        mockMvc.perform(post("/api/workspaces/{id}/member-role-changes", workspace.id())
                        .contentType("application/json")
                        .content("""
                                {"subjectId":"subject:managed","role":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/workspaces/{id}/member-removals", workspace.id())
                        .contentType("application/json")
                        .content("""
                                {"subjectId":"subject:managed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AINER.COMMON.OK"));

        actorResolver.use(member);
        mockMvc.perform(get("/api/workspaces/{id}", workspace.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownershipTransferIsAtomicAndLeavesOneActiveOwner() throws Exception {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("转移空间"));
        AuthenticatedActor nextOwner = actor(
                "subject:next-owner", TENANT_ID, "SCOPE_workspace.read", "SCOPE_workspace.write");
        service.addMember(OWNER, workspace.id(),
                new AddWorkspaceMemberCommand(nextOwner.subjectId(), WorkspaceRole.ADMIN));
        service.acceptInvitation(nextOwner, workspace.id());

        mockMvc.perform(post("/api/workspaces/{id}/ownership-transfers", workspace.id())
                        .contentType("application/json")
                        .content("""
                                {"newOwnerSubjectId":"subject:next-owner"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjectId").value("subject:next-owner"))
                .andExpect(jsonPath("$.data.role").value("OWNER"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_workspace_member "
                        + "WHERE tenant_id = ? AND workspace_id = ? "
                        + "AND role = 'OWNER' AND status = 'ACTIVE'",
                Integer.class,
                TENANT_ID,
                workspace.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM ainer_workspace_member "
                        + "WHERE tenant_id = ? AND workspace_id = ? AND subject_id = ?",
                String.class,
                TENANT_ID,
                workspace.id(),
                OWNER.subjectId())).isEqualTo("ADMIN");

        mockMvc.perform(post("/api/workspaces/{id}/ownership-transfers", workspace.id())
                        .contentType("application/json")
                        .content("""
                                {"newOwnerSubjectId":"subject:owner-1"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(WorkspaceErrorCode.ACCESS_DENIED.code()));
    }

    @Test
    void auditEndpointRequiresManagerAndAuditScope() throws Exception {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("审计查询空间"));
        service.rename(OWNER, workspace.id(), "审计查询交付空间");

        mockMvc.perform(get("/api/workspaces/{id}/authorization-audits", workspace.id())
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].action").value("AUTHORIZATION_AUDIT_READ"))
                .andExpect(jsonPath("$.data.items[0].workspaceId").value(workspace.id().toString()));

        actorResolver.use(actor(
                OWNER.subjectId(), TENANT_ID, "SCOPE_workspace.read", "SCOPE_workspace.write"));
        mockMvc.perform(get("/api/workspaces/{id}/authorization-audits", workspace.id()))
                .andExpect(status().isForbidden());
    }

    @Test
    void identityAccessEventIsTenantScopedIdempotentAndCanRevokeOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        AuthenticatedActor targetOwner = actor(
                subjectId.toString(), tenantId.toString(),
                "SCOPE_workspace.read", "SCOPE_workspace.write");
        AuthenticatedActor otherOwner = actor(
                subjectId.toString(), otherTenantId.toString(),
                "SCOPE_workspace.read", "SCOPE_workspace.write");
        Workspace target = service.create(targetOwner, new CreateWorkspaceCommand("待撤销空间"));
        Workspace other = service.create(otherOwner, new CreateWorkspaceCommand("其他租户空间"));
        WorkspaceIdentityAccessEvent event = new WorkspaceIdentityAccessEvent(
                UUID.randomUUID(),
                WorkspaceIdentityAccessEventType.IDENTITY_USER_DISABLED,
                tenantId,
                subjectId,
                1,
                Instant.now().plusSeconds(1));

        WorkspaceIdentityAccessEventResult first = accessEventConsumer.consume(event);
        WorkspaceIdentityAccessEventResult duplicate = accessEventConsumer.consume(event);

        assertThat(first).isEqualTo(new WorkspaceIdentityAccessEventResult(false, 1));
        assertThat(duplicate).isEqualTo(new WorkspaceIdentityAccessEventResult(true, 1));
        assertThat(memberStatus(tenantId, target.id(), subjectId)).isEqualTo("REVOKED");
        assertThat(memberStatus(otherTenantId, other.id(), subjectId)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_workspace_identity_event_receipt WHERE event_id = ?",
                Integer.class,
                event.eventId())).isEqualTo(1);
    }

    @Test
    void oldIdentityAccessEventDoesNotRevokeMembershipCreatedLater() {
        UUID tenantId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        WorkspaceIdentityAccessEvent oldEvent = new WorkspaceIdentityAccessEvent(
                UUID.randomUUID(),
                WorkspaceIdentityAccessEventType.IDENTITY_MEMBERSHIP_REVOKED,
                tenantId,
                subjectId,
                1,
                Instant.now().minusSeconds(60));
        AuthenticatedActor owner = actor(
                subjectId.toString(), tenantId.toString(),
                "SCOPE_workspace.read", "SCOPE_workspace.write");
        Workspace workspace = service.create(owner, new CreateWorkspaceCommand("后建空间"));

        WorkspaceIdentityAccessEventResult result = accessEventConsumer.consume(oldEvent);

        assertThat(result).isEqualTo(new WorkspaceIdentityAccessEventResult(false, 0));
        assertThat(memberStatus(tenantId, workspace.id(), subjectId)).isEqualTo("ACTIVE");
    }

    @Test
    void identityAccessEventRollsBackReceiptAndRevocationTogether() {
        UUID tenantId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        AuthenticatedActor owner = actor(
                subjectId.toString(), tenantId.toString(),
                "SCOPE_workspace.read", "SCOPE_workspace.write");
        Workspace workspace = service.create(owner, new CreateWorkspaceCommand("撤销回滚空间"));
        WorkspaceIdentityAccessEvent event = new WorkspaceIdentityAccessEvent(
                UUID.randomUUID(),
                WorkspaceIdentityAccessEventType.IDENTITY_MEMBERSHIP_REVOKED,
                tenantId,
                subjectId,
                1,
                Instant.now().plusSeconds(1));
        accessEventRepository.failNextFinalize();

        assertThatThrownBy(() -> accessEventConsumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated identity access event finalization failure");

        assertThat(memberStatus(tenantId, workspace.id(), subjectId)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_workspace_identity_event_receipt WHERE event_id = ?",
                Integer.class,
                event.eventId())).isZero();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({WorkspaceModuleConfiguration.class, FailureProbeConfiguration.class})
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureProbeConfiguration {

        @Bean
        TestActorResolver testActorResolver() {
            return new TestActorResolver(OWNER);
        }

        @Bean
        @Primary
        ControllableMemberRepository controllableMemberRepository(
                MybatisWorkspaceMemberRepository delegate) {
            return new ControllableMemberRepository(delegate);
        }

        @Bean
        @Primary
        ControllableIdentityAccessEventRepository controllableIdentityAccessEventRepository(
                MybatisWorkspaceIdentityAccessEventRepository delegate) {
            return new ControllableIdentityAccessEventRepository(delegate);
        }
    }

    static final class ControllableIdentityAccessEventRepository
            implements WorkspaceIdentityAccessEventRepository {

        private final WorkspaceIdentityAccessEventRepository delegate;
        private boolean failNextFinalize;

        ControllableIdentityAccessEventRepository(WorkspaceIdentityAccessEventRepository delegate) {
            this.delegate = delegate;
        }

        void failNextFinalize() {
            failNextFinalize = true;
        }

        void reset() {
            failNextFinalize = false;
        }

        @Override
        public boolean insertReceipt(WorkspaceIdentityAccessEvent event, Instant receivedAt) {
            return delegate.insertReceipt(event, receivedAt);
        }

        @Override
        public int revokeExistingMemberships(
                WorkspaceIdentityAccessEvent event, Instant receivedAt) {
            return delegate.revokeExistingMemberships(event, receivedAt);
        }

        @Override
        public void recordAffectedMemberships(UUID eventId, int affectedMemberships) {
            if (failNextFinalize) {
                failNextFinalize = false;
                throw new IllegalStateException(
                        "simulated identity access event finalization failure");
            }
            delegate.recordAffectedMemberships(eventId, affectedMemberships);
        }

        @Override
        public int findAffectedMemberships(UUID eventId) {
            return delegate.findAffectedMemberships(eventId);
        }
    }

    static final class ControllableMemberRepository implements WorkspaceMemberRepository {

        private final WorkspaceMemberRepository delegate;
        private boolean failNext;

        ControllableMemberRepository(WorkspaceMemberRepository delegate) {
            this.delegate = delegate;
        }

        void failNextInsert() {
            failNext = true;
        }

        void reset() {
            failNext = false;
        }

        @Override
        public void insert(WorkspaceMember member) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("simulated member persistence failure");
            }
            delegate.insert(member);
        }

        @Override
        public Optional<WorkspaceMember> findByWorkspaceAndSubject(
                TenantId tenantId, UUID workspaceId, SubjectId subjectId) {
            return delegate.findByWorkspaceAndSubject(tenantId, workspaceId, subjectId);
        }

        @Override
        public boolean activatePending(
                TenantId tenantId, UUID workspaceId, SubjectId subjectId, Instant activatedAt) {
            return delegate.activatePending(tenantId, workspaceId, subjectId, activatedAt);
        }

        @Override
        public boolean updateRole(
                TenantId tenantId,
                UUID workspaceId,
                SubjectId subjectId,
                WorkspaceRole expectedRole,
                WorkspaceRole newRole,
                Instant updatedAt) {
            return delegate.updateRole(
                    tenantId, workspaceId, subjectId, expectedRole, newRole, updatedAt);
        }

        @Override
        public boolean deleteNonOwner(TenantId tenantId, UUID workspaceId, SubjectId subjectId) {
            return delegate.deleteNonOwner(tenantId, workspaceId, subjectId);
        }

        @Override
        public boolean demoteOwner(
                TenantId tenantId, UUID workspaceId, SubjectId ownerSubjectId, Instant updatedAt) {
            return delegate.demoteOwner(tenantId, workspaceId, ownerSubjectId, updatedAt);
        }

        @Override
        public boolean promoteActiveMemberToOwner(
                TenantId tenantId,
                UUID workspaceId,
                SubjectId subjectId,
                WorkspaceRole expectedRole,
                Instant updatedAt) {
            return delegate.promoteActiveMemberToOwner(
                    tenantId, workspaceId, subjectId, expectedRole, updatedAt);
        }

        @Override
        public boolean hasActiveOwner(TenantId tenantId, UUID workspaceId) {
            return delegate.hasActiveOwner(tenantId, workspaceId);
        }

        @Override
        public boolean hasRevokedOwner(TenantId tenantId, UUID workspaceId) {
            return delegate.hasRevokedOwner(tenantId, workspaceId);
        }
    }

    static final class TestActorResolver implements AuthenticatedActorResolver {

        private AuthenticatedActor actor;

        TestActorResolver(AuthenticatedActor actor) {
            this.actor = actor;
        }

        void use(AuthenticatedActor actor) {
            this.actor = actor;
        }

        @Override
        public AuthenticatedActor requireCurrent() {
            return actor;
        }
    }

    private static AuthenticatedActor actor(String subjectId, String tenantId, String... authorities) {
        return new AuthenticatedActor(subjectId, tenantId, AuthenticatedActor.USER, Set.of(authorities));
    }

    private String memberStatus(UUID tenantId, UUID workspaceId, UUID subjectId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_workspace_member "
                        + "WHERE tenant_id = ? AND workspace_id = ? AND subject_id = ?",
                String.class,
                tenantId.toString(),
                workspaceId,
                subjectId.toString());
    }
}
