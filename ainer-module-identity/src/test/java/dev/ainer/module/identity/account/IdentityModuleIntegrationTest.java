package dev.ainer.module.identity.account;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.identity.IdentityModuleConfiguration;
import dev.ainer.module.identity.account.application.AddTenantMemberCommand;
import dev.ainer.module.identity.account.application.IdentityAccessLifecycleService;
import dev.ainer.module.identity.account.application.IdentityAccessEventDelivery;
import dev.ainer.module.identity.account.application.IdentityAccessEventOutboxService;
import dev.ainer.module.identity.account.application.IdentityAccessEventOutboxStatus;
import dev.ainer.module.identity.account.application.IdentityAccessEventRecoveryService;
import dev.ainer.module.identity.account.application.IdentityAccessEventReplayRequest;
import dev.ainer.module.identity.account.application.IdentityAccount;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.module.identity.account.application.IdentityDirectoryEntry;
import dev.ainer.module.identity.account.application.IdentityDirectoryService;
import dev.ainer.module.identity.account.application.IdentityErrorCode;
import dev.ainer.module.identity.account.application.IdentityRepository;
import dev.ainer.module.identity.account.application.IdentityTokenStatusService;
import dev.ainer.module.identity.account.application.MemberPage;
import dev.ainer.module.identity.account.application.MemberSummary;
import dev.ainer.module.identity.account.application.ProvisionTenantOwnerCommand;
import dev.ainer.module.identity.account.application.ProvisionedIdentity;
import dev.ainer.module.identity.account.application.TenantMemberManagementService;
import dev.ainer.module.identity.account.application.TenantOwnerBootstrapResult;
import dev.ainer.module.identity.account.domain.IdentityAccessEvent;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import dev.ainer.module.identity.account.domain.IdentityTenant;
import dev.ainer.module.identity.account.domain.IdentityUser;
import dev.ainer.module.identity.account.domain.TenantMembership;
import dev.ainer.module.identity.account.domain.TenantRole;
import dev.ainer.module.identity.account.infrastructure.mybatis.MybatisIdentityRepository;
import dev.ainer.security.AinerSecurityScopes;
import dev.ainer.security.actor.AuthenticatedActor;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = IdentityModuleIntegrationTest.TestApplication.class,
        properties = {
                "ainer.identity.enabled=true",
                "mybatis.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class IdentityModuleIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_identity_test")
            .withUsername("ainer")
            .withPassword("ainer");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private IdentityApplicationService service;

    @Autowired
    private IdentityDirectoryService directoryService;

    @Autowired
    private IdentityAccessLifecycleService lifecycleService;

    @Autowired
    private IdentityAccessEventOutboxService outboxService;

    @Autowired
    private IdentityAccessEventRecoveryService recoveryService;

    @Autowired
    private IdentityTokenStatusService tokenStatusService;

    @Autowired
    private TenantMemberManagementService memberService;

    @Autowired
    private ControllableIdentityRepository identityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ainer_identity_member_audit");
        jdbcTemplate.update("DELETE FROM ainer_identity_security_operation_audit");
        jdbcTemplate.update("DELETE FROM ainer_identity_access_event_replay_request");
        jdbcTemplate.update("DELETE FROM ainer_identity_access_event");
        jdbcTemplate.update("DELETE FROM ainer_identity_tenant");
        jdbcTemplate.update("DELETE FROM ainer_identity_user");
        identityRepository.reset();
    }

    @Test
    void migrationCreatesIdentitySchema() {
        assertThat(flyway.info().applied()).hasSize(6);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('ainer_identity_tenant','ainer_identity_user',"
                        + "'ainer_identity_membership','ainer_identity_access_event',"
                        + "'ainer_identity_access_event_replay_request',"
                        + "'ainer_identity_security_operation_audit',"
                        + "'ainer_identity_member_audit')",
                Integer.class)).isEqualTo(7);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void tokenStatusUsesCurrentIdentityStateAndLatestAccessEventEpoch() {
        ProvisionedIdentity identity = service.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "token-status", "Token Status", "token-status@example.com",
                "strong-password-2026", "Token Status"));
        Instant revokedAt = Instant.parse("2026-07-23T02:00:00Z");

        assertThat(tokenStatusService.isAccessTokenActive(
                identity.tenantId(), identity.subjectId(), revokedAt.minusSeconds(60))).isTrue();

        jdbcTemplate.update(
                "INSERT INTO ainer_identity_access_event "
                        + "(id, event_type, tenant_id, subject_id, payload_version, occurred_at, "
                        + "publication_status, attempt_count, available_at) "
                        + "VALUES (?, 'IDENTITY_MEMBERSHIP_REVOKED', ?, ?, 1, ?, 'PENDING', 0, ?)",
                UUID.randomUUID(), identity.tenantId(), identity.subjectId(),
                databaseTimestamp(revokedAt), databaseTimestamp(revokedAt));

        assertThat(tokenStatusService.isAccessTokenActive(
                identity.tenantId(), identity.subjectId(), revokedAt.minusNanos(1))).isFalse();
        assertThat(tokenStatusService.isAccessTokenActive(
                identity.tenantId(), identity.subjectId(), revokedAt)).isFalse();
        assertThat(tokenStatusService.isAccessTokenActive(
                identity.tenantId(), identity.subjectId(), revokedAt.plusNanos(1))).isTrue();

        jdbcTemplate.update(
                "UPDATE ainer_identity_membership "
                        + "SET status = 'DISABLED', "
                        + "updated_at = GREATEST(updated_at, CURRENT_TIMESTAMP) "
                        + "WHERE tenant_id = ? AND user_id = ?",
                identity.tenantId(), identity.subjectId());
        assertThat(tokenStatusService.isAccessTokenActive(
                identity.tenantId(), identity.subjectId(), revokedAt.plusSeconds(2))).isFalse();
    }

    @Test
    void exhaustedEventReplayRequiresTwoServicesAndPreservesTheOriginalFact() {
        ProvisionedIdentity identity = service.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "replay-event", "Replay Event", "replay@example.com",
                "strong-password-2026", "Replay User"));
        lifecycleService.disableUser(identity.subjectId());
        Instant occurredAt = jdbcTemplate.queryForObject(
                "SELECT occurred_at FROM ainer_identity_access_event WHERE subject_id = ?",
                Instant.class,
                identity.subjectId());
        IdentityAccessEventDelivery delivery = outboxService.claimBatch(
                "relay:exhaust", occurredAt.plusSeconds(1), Duration.ofSeconds(30), 1, 1)
                .getFirst();
        outboxService.markFailed(
                delivery.event().id(), "relay:exhaust", occurredAt.plusSeconds(60),
                "AINER.IDENTITY.TEST_DELIVERY_FAILED");

        IdentityAccessEventReplayRequest request = recoveryService.requestReplay(
                "operator:request", identity.tenantId(), delivery.event().id(),
                "INC-REPLAY-001", Duration.ofMinutes(15), 1);

        assertThatThrownBy(() -> recoveryService.approveAndExecute(
                "operator:request", identity.tenantId(), request.id(), 1))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(IdentityErrorCode.REPLAY_APPROVER_MUST_DIFFER));

        recoveryService.approveAndExecute(
                "operator:approve", identity.tenantId(), request.id(), 1);

        assertThat(jdbcTemplate.queryForMap(
                "SELECT id, event_type, tenant_id, subject_id, payload_version, occurred_at, "
                        + "publication_status, attempt_count, last_error_code "
                        + "FROM ainer_identity_access_event WHERE id = ?",
                delivery.event().id()))
                .containsEntry("id", delivery.event().id())
                .containsEntry("event_type", delivery.event().type().name())
                .containsEntry("tenant_id", identity.tenantId())
                .containsEntry("subject_id", identity.subjectId())
                .containsEntry("publication_status", "PENDING")
                .containsEntry("attempt_count", 0)
                .containsEntry("last_error_code", null);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payload_version FROM ainer_identity_access_event WHERE id = ?",
                Integer.class,
                delivery.event().id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT occurred_at = ? FROM ainer_identity_access_event WHERE id = ?",
                Boolean.class,
                databaseTimestamp(occurredAt),
                delivery.event().id())).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_security_operation_audit "
                        + "WHERE operation_id = ?",
                Integer.class,
                request.id())).isEqualTo(2);
    }

    @Test
    void outboxLeaseExpiresRetriesAndStopsAtMaximumAttempts() {
        ProvisionedIdentity identity = service.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "leased-event", "Leased Event", "leased@example.com",
                "strong-password-2026", "Leased User"));
        lifecycleService.disableUser(identity.subjectId());
        Instant occurredAt = jdbcTemplate.queryForObject(
                "SELECT occurred_at FROM ainer_identity_access_event WHERE subject_id = ?",
                Instant.class,
                identity.subjectId());
        Instant firstClaimAt = occurredAt.plusSeconds(1);

        List<IdentityAccessEventDelivery> first = outboxService.claimBatch(
                "relay:first", firstClaimAt, Duration.ofSeconds(30), 2, 10);
        List<IdentityAccessEventDelivery> concurrent = outboxService.claimBatch(
                "relay:second", firstClaimAt.plusSeconds(1), Duration.ofSeconds(30), 2, 10);
        List<IdentityAccessEventDelivery> afterExpiry = outboxService.claimBatch(
                "relay:second", firstClaimAt.plusSeconds(31), Duration.ofSeconds(30), 2, 10);

        assertThat(first).singleElement().satisfies(delivery ->
                assertThat(delivery.attemptCount()).isEqualTo(1));
        assertThat(concurrent).isEmpty();
        assertThat(afterExpiry).singleElement().satisfies(delivery ->
                assertThat(delivery.attemptCount()).isEqualTo(2));

        Instant retryAt = firstClaimAt.plusSeconds(90);
        outboxService.markFailed(
                afterExpiry.getFirst().event().id(),
                "relay:second",
                retryAt,
                "AINER.IDENTITY.TEST_DELIVERY_FAILED");

        assertThat(outboxService.claimBatch(
                "relay:third", retryAt.minusSeconds(1), Duration.ofSeconds(30), 2, 10)).isEmpty();
        assertThat(outboxService.claimBatch(
                "relay:third", retryAt, Duration.ofSeconds(30), 2, 10)).isEmpty();
        IdentityAccessEventOutboxStatus status = outboxService.status(2);
        assertThat(status.failed()).isZero();
        assertThat(status.exhausted()).isEqualTo(1);
        assertThat(status.published()).isZero();
    }

    @Test
    void outboxPublicationRequiresLeaseOwnerAndUpdatesStatus() {
        ProvisionedIdentity identity = service.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "published-event", "Published Event", "published@example.com",
                "strong-password-2026", "Published User"));
        lifecycleService.disableUser(identity.subjectId());
        Instant occurredAt = jdbcTemplate.queryForObject(
                "SELECT occurred_at FROM ainer_identity_access_event WHERE subject_id = ?",
                Instant.class,
                identity.subjectId());
        IdentityAccessEventDelivery claimed = outboxService.claimBatch(
                "relay:publisher", occurredAt.plusSeconds(1), Duration.ofSeconds(30), 3, 1)
                .getFirst();

        assertThatThrownBy(() -> outboxService.markPublished(
                claimed.event().id(), "relay:other", occurredAt.plusSeconds(2)))
                .isInstanceOf(BusinessException.class);

        outboxService.markPublished(
                claimed.event().id(), "relay:publisher", occurredAt.plusSeconds(2));

        IdentityAccessEventOutboxStatus status = outboxService.status(3);
        assertThat(status.pending()).isZero();
        assertThat(status.failed()).isZero();
        assertThat(status.exhausted()).isZero();
        assertThat(status.published()).isEqualTo(1);
    }

    @Test
    void provisionsTenantOwnerWithDelegatingPasswordHashAndDefaultTenant() {
        ProvisionedIdentity identity = service.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "acme-ai", "Acme AI", "OWNER@ACME.COM", "strong-password-2026", "Acme Owner"));

        IdentityAccount account = service.findAccountByUsername("owner@acme.com").orElseThrow();
        assertThat(account.subjectId()).isEqualTo(identity.subjectId());
        assertThat(account.tenantId()).isEqualTo(identity.tenantId());
        assertThat(account.roles()).containsExactly("ROLE_OWNER");
        assertThat(account.enabled()).isTrue();
        assertThat(account.accountNonLocked()).isTrue();
        assertThat(account.passwordHash()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches("strong-password-2026", account.passwordHash())).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_hash = 'strong-password-2026' FROM ainer_identity_user",
                Boolean.class)).isFalse();
    }

    @Test
    void duplicateUsernameRollsBackNewTenant() {
        service.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "tenant-one", "Tenant One", "owner@example.com", "strong-password-2026", "Owner One"));

        assertThatThrownBy(() -> service.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "tenant-two", "Tenant Two", "OWNER@EXAMPLE.COM", "another-password-2026", "Owner Two")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(IdentityErrorCode.ALREADY_EXISTS));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ainer_identity_tenant", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ainer_identity_user", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void bootstrapIsIdempotentOnlyForTheExactActiveDefaultOwner() {
        ProvisionTenantOwnerCommand command = new ProvisionTenantOwnerCommand(
                "platform", "Ainer Platform", "PLATFORM@AINER.DEV",
                "bootstrap-password-2026", "Platform Owner");

        TenantOwnerBootstrapResult created = service.ensureTenantOwner(command);
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM ainer_identity_user WHERE id = ?",
                String.class,
                created.identity().subjectId());
        TenantOwnerBootstrapResult existing = service.ensureTenantOwner(new ProvisionTenantOwnerCommand(
                "PLATFORM", "Ignored Name", "platform@ainer.dev",
                "different-password-2026", "Ignored Display Name"));

        assertThat(created.created()).isTrue();
        assertThat(existing.created()).isFalse();
        assertThat(existing.identity()).isEqualTo(created.identity());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_hash FROM ainer_identity_user WHERE id = ?",
                String.class,
                created.identity().subjectId())).isEqualTo(passwordHash);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_tenant", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_user", Integer.class)).isEqualTo(1);
    }

    @Test
    void bootstrapRejectsPartiallyOccupiedIdentityState() {
        service.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "occupied", "Occupied Tenant", "occupied@example.com",
                "strong-password-2026", "Occupied Owner"));

        assertIdentityError(
                () -> service.ensureTenantOwner(new ProvisionTenantOwnerCommand(
                        "occupied", "Other Tenant", "other@example.com",
                        "other-password-2026", "Other Owner")),
                IdentityErrorCode.TENANT_BOOTSTRAP_STATE_CONFLICT);
        assertIdentityError(
                () -> service.ensureTenantOwner(new ProvisionTenantOwnerCommand(
                        "other-tenant", "Other Tenant", "occupied@example.com",
                        "other-password-2026", "Other Owner")),
                IdentityErrorCode.TENANT_BOOTSTRAP_STATE_CONFLICT);
    }

    @Test
    void tenantManagersCanManageMembersWithAuditedLifecycle() {
        ProvisionedIdentity owner = provision(
                "managed", "Managed Tenant", "owner@managed.dev", "Managed Owner");
        ProvisionedIdentity firstTarget = provision(
                "first-home", "First Home", "first@managed.dev", "First Member");
        ProvisionedIdentity secondTarget = provision(
                "second-home", "Second Home", "second@managed.dev", "Second Member");
        AuthenticatedActor ownerActor = managerActor(owner);

        MemberSummary first = memberService.addMember(
                ownerActor,
                owner.tenantId(),
                new AddTenantMemberCommand(
                        "FIRST@MANAGED.DEV", null, TenantRole.MEMBER, "onboarding"),
                "req-member-add-1");
        MemberSummary second = memberService.addMember(
                ownerActor,
                owner.tenantId(),
                new AddTenantMemberCommand(
                        null, secondTarget.subjectId(), TenantRole.ADMIN, "admin-onboarding"),
                "req-member-add-2");
        MemberPage page = memberService.listMembers(ownerActor, owner.tenantId(), 1, 20);

        assertThat(first)
                .extracting(MemberSummary::subjectId, MemberSummary::username, MemberSummary::role)
                .containsExactly(firstTarget.subjectId(), "first@managed.dev", TenantRole.MEMBER);
        assertThat(second.role()).isEqualTo(TenantRole.ADMIN);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.members())
                .extracting(MemberSummary::subjectId)
                .containsExactlyInAnyOrder(
                        owner.subjectId(), firstTarget.subjectId(), secondTarget.subjectId());

        assertThat(memberService.changeMemberRole(
                ownerActor,
                owner.tenantId(),
                firstTarget.subjectId(),
                TenantRole.ADMIN,
                "promoted",
                "req-member-role").role()).isEqualTo(TenantRole.ADMIN);
        memberService.removeMember(
                ownerActor,
                owner.tenantId(),
                firstTarget.subjectId(),
                "offboarded",
                "req-member-remove");
        assertThat(memberService.listMembers(ownerActor, owner.tenantId(), 1, 20).total()).isEqualTo(2);

        assertThat(memberService.addMember(
                ownerActor,
                owner.tenantId(),
                new AddTenantMemberCommand(
                        null, firstTarget.subjectId(), TenantRole.MEMBER, "returned"),
                "req-member-reactivate").role()).isEqualTo(TenantRole.MEMBER);

        assertThat(jdbcTemplate.queryForList(
                "SELECT operation, role, reason_code, request_id "
                        + "FROM ainer_identity_member_audit "
                        + "WHERE tenant_id = ? ORDER BY occurred_at, request_id",
                owner.tenantId()))
                .extracting(
                        row -> row.get("operation"),
                        row -> row.get("reason_code"),
                        row -> row.get("request_id"))
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("ADDED", "onboarding", "req-member-add-1"),
                        org.assertj.core.groups.Tuple.tuple("ADDED", "admin-onboarding", "req-member-add-2"),
                        org.assertj.core.groups.Tuple.tuple("ROLE_CHANGED", "promoted", "req-member-role"),
                        org.assertj.core.groups.Tuple.tuple("REMOVED", "offboarded", "req-member-remove"),
                        org.assertj.core.groups.Tuple.tuple(
                                "REACTIVATED", "returned", "req-member-reactivate"));
    }

    @Test
    void memberManagementEnforcesActorTypeScopeTenantAndLiveManagerRole() {
        ProvisionedIdentity owner = provision(
                "secure", "Secure Tenant", "owner@secure.dev", "Secure Owner");
        ProvisionedIdentity target = provision(
                "target-home", "Target Home", "target@secure.dev", "Target Member");
        ProvisionedIdentity outsider = provision(
                "outsider", "Outsider Tenant", "owner@outsider.dev", "Outsider Owner");
        AddTenantMemberCommand command = new AddTenantMemberCommand(
                null, target.subjectId(), TenantRole.MEMBER, "onboarding");

        assertStandardError(
                () -> memberService.addMember(
                        new AuthenticatedActor(
                                owner.subjectId().toString(),
                                owner.tenantId().toString(),
                                AuthenticatedActor.USER,
                                Set.of()),
                        owner.tenantId(),
                        command,
                        "req-no-scope"),
                StandardErrorCode.FORBIDDEN);
        assertStandardError(
                () -> memberService.addMember(
                        new AuthenticatedActor(
                                owner.subjectId().toString(),
                                owner.tenantId().toString(),
                                AuthenticatedActor.SERVICE,
                                managerAuthorities()),
                        owner.tenantId(),
                        command,
                        "req-service"),
                StandardErrorCode.FORBIDDEN);
        assertStandardError(
                () -> memberService.listMembers(
                        managerActor(outsider),
                        owner.tenantId(),
                        1,
                        20),
                StandardErrorCode.FORBIDDEN);

        MemberSummary member = memberService.addMember(
                managerActor(owner),
                owner.tenantId(),
                command,
                "req-add-target");
        AuthenticatedActor memberActor = new AuthenticatedActor(
                member.subjectId().toString(),
                owner.tenantId().toString(),
                AuthenticatedActor.USER,
                managerAuthorities());
        assertStandardError(
                () -> memberService.listMembers(memberActor, owner.tenantId(), 1, 20),
                StandardErrorCode.FORBIDDEN);
        assertIdentityError(
                () -> memberService.changeMemberRole(
                        managerActor(owner),
                        owner.tenantId(),
                        owner.subjectId(),
                        TenantRole.ADMIN,
                        "owner-change",
                        "req-owner-change"),
                IdentityErrorCode.CANNOT_MODIFY_OWNER);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_member_audit WHERE tenant_id = ?",
                Integer.class,
                owner.tenantId())).isEqualTo(1);
    }

    @Test
    void directoryReturnsOnlySafeActiveTenantProjection() {
        ProvisionedIdentity identity = service.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "directory", "Directory Tenant", "Owner@Directory.COM",
                "strong-password-2026", "Directory Owner"));

        IdentityDirectoryEntry exact = directoryService.findActiveMember(
                identity.tenantId(), identity.subjectId()).orElseThrow();
        List<IdentityDirectoryEntry> search = directoryService.searchActiveMembers(
                identity.tenantId(), "directory owner", 10);

        assertThat(exact)
                .extracting(
                        IdentityDirectoryEntry::username,
                        IdentityDirectoryEntry::displayName,
                        IdentityDirectoryEntry::role)
                .containsExactly("owner@directory.com", "Directory Owner", TenantRole.OWNER);
        assertThat(search).containsExactly(exact);
        assertThat(directoryService.searchActiveMembers(identity.tenantId(), "%%", 10)).isEmpty();
    }

    @Test
    void disablingUserHidesDirectoryAndWritesOneOutboxEvent() {
        ProvisionedIdentity identity = service.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "disabled-user", "Disabled User", "disabled@example.com",
                "strong-password-2026", "Disabled User"));

        assertThat(lifecycleService.disableUser(identity.subjectId())).isEqualTo(1);

        assertThat(service.findAccountByUsername(identity.username()).orElseThrow().enabled()).isFalse();
        assertThat(directoryService.findActiveMember(identity.tenantId(), identity.subjectId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_access_event "
                        + "WHERE tenant_id = ? AND subject_id = ? "
                        + "AND event_type = 'IDENTITY_USER_DISABLED' "
                        + "AND publication_status = 'PENDING'",
                Integer.class,
                identity.tenantId(),
                identity.subjectId())).isEqualTo(1);
        assertThat(lifecycleService.disableUser(identity.subjectId())).isZero();
    }

    @Test
    void revokingMembershipDisablesAccessButProtectsOwner() {
        ProvisionedIdentity owner = service.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "membership", "Membership Tenant", "membership-owner@example.com",
                "strong-password-2026", "Membership Owner"));
        UUID memberId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-23T00:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO ainer_identity_user "
                        + "(id, username, password_hash, display_name, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                memberId, "member@example.com", passwordEncoder.encode("strong-password-2026"),
                "Member", databaseTimestamp(now), databaseTimestamp(now));
        jdbcTemplate.update(
                "INSERT INTO ainer_identity_membership "
                        + "(tenant_id, user_id, role, is_default, status, joined_at, updated_at) "
                        + "VALUES (?, ?, 'MEMBER', true, 'ACTIVE', ?, ?)",
                owner.tenantId(), memberId, databaseTimestamp(now), databaseTimestamp(now));

        assertThat(lifecycleService.revokeMembership(owner.tenantId(), memberId)).isTrue();
        assertThat(directoryService.findActiveMember(owner.tenantId(), memberId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_type FROM ainer_identity_access_event WHERE subject_id = ?",
                String.class,
                memberId)).isEqualTo("IDENTITY_MEMBERSHIP_REVOKED");
        assertThatThrownBy(() -> lifecycleService.revokeMembership(
                owner.tenantId(), owner.subjectId()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(IdentityErrorCode.OWNER_REVOCATION_REQUIRES_TRANSFER));
    }

    @Test
    void accessEventFailureRollsBackUserDisable() {
        ProvisionedIdentity identity = service.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                "rollback-event", "Rollback Event", "rollback@example.com",
                "strong-password-2026", "Rollback User"));
        identityRepository.failNextAccessEvent();

        assertThatThrownBy(() -> lifecycleService.disableUser(identity.subjectId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated access event persistence failure");

        assertThat(service.findAccountByUsername(identity.username()).orElseThrow().enabled()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_access_event", Integer.class)).isZero();
    }

    private static OffsetDateTime databaseTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private ProvisionedIdentity provision(
            String tenantCode,
            String tenantName,
            String username,
            String displayName) {
        return service.provisionTenantOwner(new ProvisionTenantOwnerCommand(
                tenantCode, tenantName, username, "strong-password-2026", displayName));
    }

    private static AuthenticatedActor managerActor(ProvisionedIdentity identity) {
        return new AuthenticatedActor(
                identity.subjectId().toString(),
                identity.tenantId().toString(),
                AuthenticatedActor.USER,
                managerAuthorities());
    }

    private static Set<String> managerAuthorities() {
        return Set.of(
                "SCOPE_" + AinerSecurityScopes.TENANT_MEMBERS_READ,
                "SCOPE_" + AinerSecurityScopes.TENANT_MEMBERS_WRITE);
    }

    private static void assertIdentityError(Runnable invocation, IdentityErrorCode expected) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(expected));
    }

    private static void assertStandardError(Runnable invocation, StandardErrorCode expected) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(expected));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({IdentityModuleConfiguration.class, FailureProbeConfiguration.class})
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureProbeConfiguration {

        @Bean
        @Primary
        ControllableIdentityRepository controllableIdentityRepository(
                MybatisIdentityRepository delegate) {
            return new ControllableIdentityRepository(delegate);
        }
    }

    static final class ControllableIdentityRepository implements IdentityRepository {

        private final IdentityRepository delegate;
        private boolean failNextAccessEvent;

        ControllableIdentityRepository(IdentityRepository delegate) {
            this.delegate = delegate;
        }

        void failNextAccessEvent() {
            failNextAccessEvent = true;
        }

        void reset() {
            failNextAccessEvent = false;
        }

        @Override
        public void insertTenant(IdentityTenant tenant) {
            delegate.insertTenant(tenant);
        }

        @Override
        public void insertUser(IdentityUser user) {
            delegate.insertUser(user);
        }

        @Override
        public void insertMembership(TenantMembership membership) {
            delegate.insertMembership(membership);
        }

        @Override
        public Optional<IdentityAccount> findAccountByUsername(String normalizedUsername) {
            return delegate.findAccountByUsername(normalizedUsername);
        }

        @Override
        public Optional<IdentityAccount> findAccountBySubjectId(UUID subjectId) {
            return delegate.findAccountBySubjectId(subjectId);
        }

        @Override
        public Optional<IdentityDirectoryEntry> findActiveDirectoryEntry(
                UUID tenantId, UUID subjectId) {
            return delegate.findActiveDirectoryEntry(tenantId, subjectId);
        }

        @Override
        public Optional<IdentityDirectoryEntry> findActiveDirectoryEntryForUpdate(
                UUID tenantId, UUID subjectId) {
            return delegate.findActiveDirectoryEntryForUpdate(tenantId, subjectId);
        }

        @Override
        public List<IdentityDirectoryEntry> searchActiveDirectory(
                UUID tenantId, String likePattern, int limit) {
            return delegate.searchActiveDirectory(tenantId, likePattern, limit);
        }

        @Override
        public Optional<IdentityStatus> findUserStatusForUpdate(UUID subjectId) {
            return delegate.findUserStatusForUpdate(subjectId);
        }

        @Override
        public List<UUID> findActiveMembershipTenantIds(UUID subjectId) {
            return delegate.findActiveMembershipTenantIds(subjectId);
        }

        @Override
        public boolean updateUserStatus(
                UUID subjectId,
                IdentityStatus expectedStatus,
                IdentityStatus newStatus,
                Instant updatedAt) {
            return delegate.updateUserStatus(subjectId, expectedStatus, newStatus, updatedAt);
        }

        @Override
        public Optional<TenantMembership> findMembershipForUpdate(UUID tenantId, UUID subjectId) {
            return delegate.findMembershipForUpdate(tenantId, subjectId);
        }

        @Override
        public boolean updateMembershipStatus(
                UUID tenantId,
                UUID subjectId,
                IdentityStatus expectedStatus,
                IdentityStatus newStatus,
                Instant updatedAt) {
            return delegate.updateMembershipStatus(
                    tenantId, subjectId, expectedStatus, newStatus, updatedAt);
        }

        @Override
        public List<IdentityDirectoryEntry> listMembersByTenant(UUID tenantId, int offset, int limit) {
            return delegate.listMembersByTenant(tenantId, offset, limit);
        }

        @Override
        public int countMembersByTenant(UUID tenantId) {
            return delegate.countMembersByTenant(tenantId);
        }

        @Override
        public boolean updateMembershipRole(
                UUID tenantId, UUID subjectId, String newRole, Instant updatedAt) {
            return delegate.updateMembershipRole(tenantId, subjectId, newRole, updatedAt);
        }

        @Override
        public boolean reactivateMembership(
                UUID tenantId,
                UUID subjectId,
                IdentityStatus expectedStatus,
                String newRole,
                Instant updatedAt) {
            return delegate.reactivateMembership(
                    tenantId, subjectId, expectedStatus, newRole, updatedAt);
        }

        @Override
        public void insertMemberAudit(
                UUID tenantId,
                UUID actorSubjectId,
                UUID targetSubjectId,
                String operation,
                String role,
                String reasonCode,
                String requestId,
                Instant occurredAt) {
            delegate.insertMemberAudit(
                    tenantId, actorSubjectId, targetSubjectId,
                    operation, role, reasonCode, requestId, occurredAt);
        }

        @Override
        public Optional<IdentityDirectoryEntry> findActiveDefaultOwner(
                String tenantCode, String normalizedUsername) {
            return delegate.findActiveDefaultOwner(tenantCode, normalizedUsername);
        }

        @Override
        public boolean tenantExistsByCode(String tenantCode) {
            return delegate.tenantExistsByCode(tenantCode);
        }

        @Override
        public boolean userExistsByUsername(String normalizedUsername) {
            return delegate.userExistsByUsername(normalizedUsername);
        }

        @Override
        public void acquireTenantBootstrapLock(String tenantCode, String normalizedUsername) {
            delegate.acquireTenantBootstrapLock(tenantCode, normalizedUsername);
        }

        @Override
        public void insertAccessEvent(IdentityAccessEvent event) {
            if (failNextAccessEvent) {
                failNextAccessEvent = false;
                throw new IllegalStateException("simulated access event persistence failure");
            }
            delegate.insertAccessEvent(event);
        }
    }
}
