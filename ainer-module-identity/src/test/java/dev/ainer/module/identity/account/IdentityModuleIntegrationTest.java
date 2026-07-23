package dev.ainer.module.identity.account;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.identity.IdentityModuleConfiguration;
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
import dev.ainer.module.identity.account.application.ProvisionTenantOwnerCommand;
import dev.ainer.module.identity.account.application.ProvisionedIdentity;
import dev.ainer.module.identity.account.domain.IdentityAccessEvent;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import dev.ainer.module.identity.account.domain.IdentityTenant;
import dev.ainer.module.identity.account.domain.IdentityUser;
import dev.ainer.module.identity.account.domain.TenantMembership;
import dev.ainer.module.identity.account.domain.TenantRole;
import dev.ainer.module.identity.account.infrastructure.mybatis.MybatisIdentityRepository;
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
    private ControllableIdentityRepository identityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ainer_identity_security_operation_audit");
        jdbcTemplate.update("DELETE FROM ainer_identity_access_event_replay_request");
        jdbcTemplate.update("DELETE FROM ainer_identity_access_event");
        jdbcTemplate.update("DELETE FROM ainer_identity_tenant");
        jdbcTemplate.update("DELETE FROM ainer_identity_user");
        identityRepository.reset();
    }

    @Test
    void migrationCreatesIdentitySchema() {
        assertThat(flyway.info().applied()).hasSize(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('ainer_identity_tenant','ainer_identity_user',"
                        + "'ainer_identity_membership','ainer_identity_access_event',"
                        + "'ainer_identity_access_event_replay_request',"
                        + "'ainer_identity_security_operation_audit')",
                Integer.class)).isEqualTo(6);
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
                "UPDATE ainer_identity_membership SET status = 'DISABLED', updated_at = CURRENT_TIMESTAMP "
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
        public Optional<IdentityDirectoryEntry> findActiveDirectoryEntry(
                UUID tenantId, UUID subjectId) {
            return delegate.findActiveDirectoryEntry(tenantId, subjectId);
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
        public void insertAccessEvent(IdentityAccessEvent event) {
            if (failNextAccessEvent) {
                failNextAccessEvent = false;
                throw new IllegalStateException("simulated access event persistence failure");
            }
            delegate.insertAccessEvent(event);
        }
    }
}
