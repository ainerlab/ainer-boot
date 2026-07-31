package dev.ainer.module.identity.account;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.identity.IdentityModuleConfiguration;
import dev.ainer.module.identity.account.application.AddTenantMemberCommand;
import dev.ainer.module.identity.account.application.CreateTenantProvisioningCommand;
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
import dev.ainer.module.identity.account.application.NotificationGatewayActor;
import dev.ainer.module.identity.account.application.PlatformIdentityQueryService;
import dev.ainer.module.identity.account.application.PlatformProvisioningActor;
import dev.ainer.module.identity.account.application.ProvisionTenantOwnerCommand;
import dev.ainer.module.identity.account.application.ProvisionedIdentity;
import dev.ainer.module.identity.account.application.TenantMemberManagementService;
import dev.ainer.module.identity.account.application.TenantContextEntry;
import dev.ainer.module.identity.account.application.OwnershipTransfer;
import dev.ainer.module.identity.account.application.OwnershipRecovery;
import dev.ainer.module.identity.account.application.TenantProvisioningCancellationResult;
import dev.ainer.module.identity.account.application.TenantProvisioningCompletion;
import dev.ainer.module.identity.account.application.TenantProvisioningNotification;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationDeliveryStatus;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationOutboxEntry;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationOutboxService;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationPayloadProtector;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationPublicationException;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationPublisher;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationReceiptCommand;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationReceiptService;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationRelay;
import dev.ainer.module.identity.account.application.TenantOwnerBootstrapResult;
import dev.ainer.module.identity.account.application.TenantProvisioningRequest;
import dev.ainer.module.identity.account.application.TenantProvisioningResult;
import dev.ainer.module.identity.account.application.TenantProvisioningPolicy;
import dev.ainer.module.identity.account.application.TenantProvisioningService;
import dev.ainer.module.identity.account.domain.IdentityAccessEvent;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import dev.ainer.module.identity.account.domain.OwnershipTransferStatus;
import dev.ainer.module.identity.account.domain.IdentityTenant;
import dev.ainer.module.identity.account.domain.IdentityUser;
import dev.ainer.module.identity.account.domain.TenantMembership;
import dev.ainer.module.identity.account.domain.TenantRole;
import dev.ainer.module.identity.account.infrastructure.mybatis.MybatisIdentityRepository;
import dev.ainer.module.identity.account.infrastructure.security.AesGcmTenantProvisioningNotificationPayloadProtector;
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
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = IdentityModuleIntegrationTest.TestApplication.class,
        properties = {
                "ainer.identity.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.profiles.active=identity-module-integration-test",
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
    private TenantProvisioningService provisioningService;

    @Autowired
    private PlatformIdentityQueryService platformIdentityQueryService;

    @Autowired
    private TenantProvisioningNotificationOutboxService provisioningNotificationOutboxService;

    @Autowired
    private TenantProvisioningNotificationPayloadProtector provisioningNotificationProtector;

    @Autowired
    private TenantProvisioningNotificationReceiptService provisioningNotificationReceiptService;

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
        jdbcTemplate.update("DELETE FROM ainer_identity_ownership_recovery");
        jdbcTemplate.update("DELETE FROM ainer_identity_ownership_transfer");
        jdbcTemplate.update("DELETE FROM ainer_identity_platform_operation_audit");
        jdbcTemplate.update(
                "DELETE FROM ainer_identity_notification_delivery_receipt");
        jdbcTemplate.update("DELETE FROM ainer_identity_notification_outbox");
        jdbcTemplate.update("DELETE FROM ainer_identity_activation_grant");
        jdbcTemplate.update("DELETE FROM ainer_identity_tenant_provisioning_request");
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
        assertThat(flyway.info().applied()).hasSize(12);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('ainer_identity_tenant','ainer_identity_user',"
                        + "'ainer_identity_membership','ainer_identity_access_event',"
                        + "'ainer_identity_access_event_replay_request',"
                        + "'ainer_identity_security_operation_audit',"
                        + "'ainer_identity_member_audit',"
                        + "'ainer_identity_tenant_provisioning_request',"
                        + "'ainer_identity_platform_operation_audit',"
                        + "'ainer_identity_activation_grant',"
                        + "'ainer_identity_notification_outbox',"
                        + "'ainer_identity_notification_delivery_receipt',"
                        + "'ainer_identity_ownership_transfer',"
                        + "'ainer_identity_ownership_recovery')",
                Integer.class)).isEqualTo(14);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void tenantProvisioningReservesIdentityIdempotentlyWithoutPollutingCoreTables() {
        PlatformProvisioningActor actor =
                new PlatformProvisioningActor("platform-operator", null, "req-provision-1");
        CreateTenantProvisioningCommand command = new CreateTenantProvisioningCommand(
                "ACME-NEXT",
                " Acme Next ",
                "OWNER@ACME-NEXT.DEV",
                " Acme Owner ",
                "EMAIL",
                "owner@acme-next.dev",
                "idem-acme-next",
                "ORDER-2026-001");

        TenantProvisioningResult created =
                provisioningService.create(command, actor, provisioningPolicy(Duration.ofDays(7)));
        TenantProvisioningResult replayed = provisioningService.create(
                command,
                new PlatformProvisioningActor(
                        "platform-operator", null, "req-provision-retry"),
                provisioningPolicy(Duration.ofDays(7)));

        assertThat(created.created()).isTrue();
        assertThat(replayed.created()).isFalse();
        assertThat(replayed.request()).isEqualTo(created.request());
        assertThat(created.request())
                .extracting(
                        TenantProvisioningRequest::tenantCode,
                        TenantProvisioningRequest::tenantName,
                        TenantProvisioningRequest::ownerUsername,
                        TenantProvisioningRequest::ownerDisplayName,
                        TenantProvisioningRequest::ownerUserExists,
                        TenantProvisioningRequest::status)
                .containsExactly(
                        "acme-next",
                        "Acme Next",
                        "owner@acme-next.dev",
                        "Acme Owner",
                        false,
                        "REQUESTED");
        assertThat(created.request().requestFingerprint()).matches("[a-f0-9]{64}");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_tenant", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_user", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_membership", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_tenant_provisioning_request",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_activation_grant",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_notification_outbox",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_tenant_provisioning_request "
                        + "WHERE uuid_extract_version(id) = 7 "
                        + "AND uuid_extract_version(tenant_id) = 7 "
                        + "AND uuid_extract_version(owner_subject_id) = 7",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_platform_operation_audit "
                        + "WHERE operation_id = ? AND phase = 'REQUESTED'",
                Integer.class,
                created.request().id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name IN ("
                        + "'ainer_identity_tenant_provisioning_request',"
                        + "'ainer_identity_platform_operation_audit',"
                        + "'ainer_identity_activation_grant',"
                        + "'ainer_identity_notification_outbox') "
                        + "AND (column_name LIKE '%password%' "
                        + "OR column_name = 'activation_secret' "
                        + "OR column_name = 'delivery_address' "
                        + "OR column_name = 'recipient_reference' "
                        + "OR column_name LIKE '%token%')",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'ainer_identity_activation_grant' "
                        + "AND column_name LIKE '%secret%'",
                String.class)).containsExactly("secret_hash");
        assertIdentityError(
                () -> service.ensureTenantOwner(new ProvisionTenantOwnerCommand(
                        "acme-next",
                        "Bootstrap Collision",
                        "bootstrap-owner@example.com",
                        "bootstrap-password-2026",
                        "Bootstrap Owner")),
                IdentityErrorCode.TENANT_BOOTSTRAP_STATE_CONFLICT);
    }

    @Test
    void platformCancellationClosesGrantDestroysNotificationAndAuditsOnce() {
        TenantProvisioningResult created = createProvisioning(
                "cancel-next",
                "Cancel Next",
                "cancel-owner@example.com",
                "Cancel Owner",
                "idem-cancel-next",
                "req-cancel-create",
                provisioningPolicy(Duration.ofDays(7)));
        PlatformProvisioningActor actor =
                new PlatformProvisioningActor(
                        "platform-operator",
                        null,
                        "req-cancel-command");

        TenantProvisioningCancellationResult cancelled = provisioningService.cancel(
                created.request().id(),
                "ORDER-CANCEL-2026-001",
                actor);
        TenantProvisioningCancellationResult replayed = provisioningService.cancel(
                created.request().id(),
                "ORDER-CANCEL-2026-REPLAY",
                new PlatformProvisioningActor(
                        "platform-operator",
                        null,
                        "req-cancel-replay"));

        assertThat(cancelled.cancelled()).isTrue();
        assertThat(cancelled.request().status()).isEqualTo("CANCELLED");
        assertThat(replayed.cancelled()).isFalse();
        assertThat(replayed.request().status()).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_identity_activation_grant "
                        + "WHERE provisioning_request_id = ?",
                String.class,
                created.request().id())).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForMap(
                "SELECT publication_status, payload_key_version, "
                        + "octet_length(protected_payload) AS payload_length, "
                        + "payload_destroyed_at IS NOT NULL AS payload_destroyed "
                        + "FROM ainer_identity_notification_outbox "
                        + "WHERE provisioning_request_id = ?",
                created.request().id()))
                .containsEntry("publication_status", "CANCELLED")
                .containsEntry("payload_key_version", "destroyed")
                .containsEntry("payload_length", 32)
                .containsEntry("payload_destroyed", true);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT actor_type, actor_id, request_id, change_reference "
                        + "FROM ainer_identity_platform_operation_audit "
                        + "WHERE operation_id = ? AND phase = 'CANCELLED'",
                created.request().id()))
                .containsEntry("actor_type", "SERVICE")
                .containsEntry("actor_id", "platform-operator")
                .containsEntry("request_id", "req-cancel-command")
                .containsEntry("change_reference", "ORDER-CANCEL-2026-001");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_platform_operation_audit "
                        + "WHERE operation_id = ? AND phase = 'CANCELLED'",
                Integer.class,
                created.request().id())).isEqualTo(1);

        TenantProvisioningResult corrupted = createProvisioning(
                "cancel-corrupted",
                "Cancel Corrupted",
                "cancel-corrupted@example.com",
                "Cancel Corrupted Owner",
                "idem-cancel-corrupted",
                "req-cancel-corrupted",
                provisioningPolicy(Duration.ofDays(7)));
        jdbcTemplate.update(
                "DELETE FROM ainer_identity_activation_grant "
                        + "WHERE provisioning_request_id = ?",
                corrupted.request().id());

        assertIdentityError(
                () -> provisioningService.cancel(
                        corrupted.request().id(),
                        "ORDER-CANCEL-CORRUPTED",
                        actor),
                IdentityErrorCode.TENANT_PROVISIONING_STATE_CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_identity_tenant_provisioning_request "
                        + "WHERE id = ?",
                String.class,
                corrupted.request().id())).isEqualTo("REQUESTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT publication_status FROM ainer_identity_notification_outbox "
                        + "WHERE provisioning_request_id = ?",
                String.class,
                corrupted.request().id())).isEqualTo("PENDING");
    }

    @Test
    void platformQueryReturnsBoundedSafeTenantAndUserProjections() {
        provision(
                "directory-alpha",
                "Directory Alpha",
                "alpha-owner@example.com",
                "Alpha Owner");
        provision(
                "directory-beta",
                "Directory Beta",
                "beta-owner@example.com",
                "Beta Owner");
        PlatformProvisioningActor actor =
                new PlatformProvisioningActor(
                        "platform-operator",
                        null,
                        "req-platform-directory");

        var firstTenantPage = platformIdentityQueryService.tenants(actor, 1, 1);
        var secondTenantPage = platformIdentityQueryService.tenants(actor, 2, 1);
        var userPage = platformIdentityQueryService.users(actor, 1, 20);

        assertThat(firstTenantPage.total()).isEqualTo(2);
        assertThat(firstTenantPage.items()).singleElement()
                .satisfies(tenant -> {
                    assertThat(tenant.code()).isEqualTo("directory-alpha");
                    assertThat(tenant.status()).isEqualTo(IdentityStatus.ACTIVE);
                });
        assertThat(secondTenantPage.items()).singleElement()
                .extracting(tenant -> tenant.code())
                .isEqualTo("directory-beta");
        assertThat(userPage.total()).isEqualTo(2);
        assertThat(userPage.items())
                .extracting(user -> user.username())
                .containsExactly(
                        "alpha-owner@example.com",
                        "beta-owner@example.com");
    }

    @Test
    void tenantProvisioningRejectsChangedIdempotencyAndReleasesExpiredReservations() {
        PlatformProvisioningActor actor =
                new PlatformProvisioningActor("platform-operator", null, "req-expiry-1");
        CreateTenantProvisioningCommand original = new CreateTenantProvisioningCommand(
                "reserved-code",
                "Reserved Tenant",
                "reserved-owner@example.com",
                "Reserved Owner",
                "EMAIL",
                "reserved-owner@example.com",
                "idem-reserved",
                "ORDER-2026-002");
        TenantProvisioningRequest first = provisioningService
                .create(original, actor, provisioningPolicy(Duration.ofMinutes(15)))
                .request();

        assertIdentityError(
                () -> provisioningService.create(
                        new CreateTenantProvisioningCommand(
                                "reserved-code",
                                "Changed Tenant",
                                "reserved-owner@example.com",
                                "Reserved Owner",
                                "EMAIL",
                                "reserved-owner@example.com",
                                "idem-reserved",
                                "ORDER-2026-002"),
                        actor,
                        provisioningPolicy(Duration.ofMinutes(15))),
                IdentityErrorCode.TENANT_PROVISIONING_IDEMPOTENCY_CONFLICT);
        assertIdentityError(
                () -> provisioningService.create(
                        new CreateTenantProvisioningCommand(
                                "reserved-code",
                                "Reserved Tenant",
                                "reserved-owner@example.com",
                                "Reserved Owner",
                                "EMAIL",
                                "reserved-owner@example.com",
                                "idem-reserved",
                                "ORDER-2026-CHANGED"),
                        actor,
                        provisioningPolicy(Duration.ofMinutes(15))),
                IdentityErrorCode.TENANT_PROVISIONING_IDEMPOTENCY_CONFLICT);
        assertIdentityError(
                () -> provisioningService.create(
                        new CreateTenantProvisioningCommand(
                                "reserved-code",
                                "Reserved Tenant",
                                "other-owner@example.com",
                                "Other Owner",
                                "EMAIL",
                                "other-owner@example.com",
                                "idem-other",
                                "ORDER-2026-003"),
                        actor,
                        provisioningPolicy(Duration.ofMinutes(15))),
                IdentityErrorCode.TENANT_PROVISIONING_CONFLICT);

        Instant now = Instant.now();
        jdbcTemplate.update(
                "UPDATE ainer_identity_tenant_provisioning_request "
                        + "SET requested_at = ?, expires_at = ? WHERE id = ?",
                databaseTimestamp(now.minus(Duration.ofHours(1))),
                databaseTimestamp(now.minus(Duration.ofMinutes(1))),
                first.id());

        TenantProvisioningRequest expired = provisioningService.find(
                first.id(),
                new PlatformProvisioningActor(
                        "platform-operator", null, "req-expiry-read"));
        TenantProvisioningResult replacement = provisioningService.create(
                new CreateTenantProvisioningCommand(
                        "reserved-code",
                        "Replacement Tenant",
                        "replacement-owner@example.com",
                        "Replacement Owner",
                        "EMAIL",
                        "replacement-owner@example.com",
                        "idem-replacement",
                        "ORDER-2026-004"),
                new PlatformProvisioningActor(
                        "platform-operator", null, "req-expiry-replacement"),
                provisioningPolicy(Duration.ofDays(1)));

        assertThat(expired.status()).isEqualTo("EXPIRED");
        assertThat(expired.completedAt()).isNotNull();
        assertThat(replacement.created()).isTrue();
        assertThat(jdbcTemplate.queryForList(
                "SELECT phase, actor_type, actor_id "
                        + "FROM ainer_identity_platform_operation_audit "
                        + "WHERE operation_id = ? ORDER BY occurred_at, phase",
                first.id()))
                .extracting(
                        row -> row.get("phase"),
                        row -> row.get("actor_type"),
                        row -> row.get("actor_id"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "REQUESTED", "SERVICE", "platform-operator"),
                        org.assertj.core.groups.Tuple.tuple(
                                "EXPIRED", "SYSTEM", "system:expiry"));
    }

    @Test
    void tenantProvisioningReusesOnlyActiveExistingUser() {
        ProvisionedIdentity existing = provision(
                "existing-home",
                "Existing Home",
                "existing-owner@example.com",
                "Authoritative Display");

        TenantProvisioningRequest request = provisioningService.create(
                new CreateTenantProvisioningCommand(
                        "existing-next",
                        "Existing Next",
                        "EXISTING-OWNER@EXAMPLE.COM",
                        "Caller Supplied Display",
                        "EMAIL",
                        "existing-owner@example.com",
                        "idem-existing",
                        "ORDER-2026-005"),
                new PlatformProvisioningActor(
                        "platform-operator", null, "req-existing"),
                provisioningPolicy(Duration.ofDays(7))).request();

        assertThat(request.ownerUserExists()).isTrue();
        assertThat(request.ownerSubjectId()).isEqualTo(existing.subjectId());
        assertThat(request.ownerDisplayName()).isEqualTo("Authoritative Display");
        TenantProvisioningCancellationResult cancelledExistingUser =
                provisioningService.cancel(
                        request.id(),
                        "ORDER-CANCEL-EXISTING",
                        new PlatformProvisioningActor(
                                "platform-operator",
                                null,
                                "req-cancel-existing"));
        assertThat(cancelledExistingUser.cancelled()).isTrue();
        assertThat(cancelledExistingUser.request().status()).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_activation_grant "
                        + "WHERE provisioning_request_id = ?",
                Integer.class,
                request.id())).isZero();

        jdbcTemplate.update(
                "UPDATE ainer_identity_user SET status = 'LOCKED' WHERE id = ?",
                existing.subjectId());
        assertIdentityError(
                () -> provisioningService.create(
                        new CreateTenantProvisioningCommand(
                                "locked-next",
                                "Locked Next",
                                "existing-owner@example.com",
                                "Ignored Display",
                                "EMAIL",
                                "existing-owner@example.com",
                                "idem-locked",
                                "ORDER-2026-006"),
                        new PlatformProvisioningActor(
                                "platform-operator", null, "req-locked"),
                        provisioningPolicy(Duration.ofDays(7))),
                IdentityErrorCode.TENANT_PROVISIONING_USER_CONFLICT);
    }

    @Test
    void newUserActivationConsumesSecretOnceAndCreatesTheReservedIdentityAtomically() {
        TenantProvisioningResult result = createProvisioning(
                "activation-new",
                "Activation New",
                "activation-new@example.com",
                "Activation Owner",
                "idem-activation-new",
                "req-activation-new",
                new TenantProvisioningPolicy(
                        Duration.ofDays(7), Duration.ofHours(24), 5));
        TenantProvisioningNotificationOutboxEntry delivery =
                claimProvisioningNotification("relay:new-user");
        TenantProvisioningNotification notification =
                provisioningNotificationProtector.unprotect(
                        delivery.protectedNotification());

        assertThat(notification.provisioningRequestId()).isEqualTo(result.request().id());
        assertThat(notification.activationGrantId()).isNotNull();
        assertThat(notification.activationSecret()).hasSize(43);
        assertThat(notification.deliveryChannel()).isEqualTo("EMAIL");
        assertThat(notification.recipientReference())
                .isEqualTo("activation-new@example.com");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT secret_hash <> ? AND char_length(secret_hash) = 64 "
                        + "FROM ainer_identity_activation_grant WHERE id = ?",
                Boolean.class,
                notification.activationSecret(),
                notification.activationGrantId())).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT position(convert_to(?, 'UTF8') in protected_payload) = 0 "
                        + "FROM ainer_identity_notification_outbox WHERE id = ?",
                Boolean.class,
                notification.activationSecret(),
                delivery.id())).isTrue();
        provisioningNotificationOutboxService.markPublished(
                delivery.id(), "relay:new-user", Instant.now());
        assertThat(jdbcTemplate.queryForMap(
                "SELECT publication_status, payload_key_version, "
                        + "payload_destroyed_at IS NOT NULL AS payload_destroyed "
                        + "FROM ainer_identity_notification_outbox WHERE id = ?",
                delivery.id()))
                .containsEntry("publication_status", "PUBLISHED")
                .containsEntry("payload_key_version", "destroyed")
                .containsEntry("payload_destroyed", true);

        TenantProvisioningCompletion completion = provisioningService.activateNewUser(
                notification.activationGrantId(),
                notification.activationSecret(),
                "activation-password-2026",
                "req-activate-new");

        assertThat(completion.activated()).isTrue();
        assertThat(completion.request().status()).isEqualTo("ACTIVATED");
        assertThat(completion.identity().tenantId()).isEqualTo(result.request().tenantId());
        assertThat(completion.identity().subjectId())
                .isEqualTo(result.request().ownerSubjectId());
        IdentityAccount account = service
                .findAccountByUsername("activation-new@example.com")
                .orElseThrow();
        assertThat(passwordEncoder.matches(
                "activation-password-2026", account.passwordHash())).isTrue();
        assertThat(account.roles()).containsExactly("ROLE_OWNER");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT is_default FROM ainer_identity_membership "
                        + "WHERE tenant_id = ? AND user_id = ?",
                Boolean.class,
                completion.identity().tenantId(),
                completion.identity().subjectId())).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_identity_activation_grant WHERE id = ?",
                String.class,
                notification.activationGrantId())).isEqualTo("CONSUMED");
        assertThat(jdbcTemplate.queryForList(
                "SELECT phase, actor_type FROM ainer_identity_platform_operation_audit "
                        + "WHERE operation_id = ? ORDER BY occurred_at, phase",
                result.request().id()))
                .extracting(row -> row.get("phase"), row -> row.get("actor_type"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("REQUESTED", "SERVICE"),
                        org.assertj.core.groups.Tuple.tuple(
                                "ACTIVATED", "ACTIVATION_GRANT"));
        assertIdentityError(
                () -> provisioningService.activateNewUser(
                        notification.activationGrantId(),
                        notification.activationSecret(),
                        "another-password-2026",
                        "req-activate-replay"),
                IdentityErrorCode.TENANT_ACTIVATION_CREDENTIAL_INVALID);
    }

    @Test
    void activationFailuresPersistAttemptsThenLockWithoutCreatingCoreIdentity() {
        TenantProvisioningResult result = createProvisioning(
                "activation-lock",
                "Activation Lock",
                "activation-lock@example.com",
                "Activation Lock Owner",
                "idem-activation-lock",
                "req-activation-lock",
                new TenantProvisioningPolicy(
                        Duration.ofDays(1), Duration.ofHours(1), 2));
        TenantProvisioningNotification notification =
                provisioningNotificationProtector.unprotect(
                        claimProvisioningNotification("relay:lock")
                                .protectedNotification());
        String wrongSecret = (notification.activationSecret().startsWith("A") ? "B" : "A")
                + notification.activationSecret().substring(1);

        TenantProvisioningCompletion firstFailure =
                provisioningService.activateNewUser(
                        notification.activationGrantId(),
                        wrongSecret,
                        "activation-password-2026",
                        "req-activation-wrong-1");
        TenantProvisioningCompletion secondFailure =
                provisioningService.activateNewUser(
                        notification.activationGrantId(),
                        wrongSecret,
                        "activation-password-2026",
                        "req-activation-wrong-2");

        assertThat(firstFailure.activated()).isFalse();
        assertThat(firstFailure.request().status()).isEqualTo("REQUESTED");
        assertThat(secondFailure.activated()).isFalse();
        assertThat(secondFailure.request().status()).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForMap(
                "SELECT status, attempt_count FROM ainer_identity_activation_grant "
                        + "WHERE id = ?",
                notification.activationGrantId()))
                .containsEntry("status", "LOCKED")
                .containsEntry("attempt_count", 2);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT publication_status, payload_key_version, "
                        + "payload_destroyed_at IS NOT NULL AS payload_destroyed "
                        + "FROM ainer_identity_notification_outbox "
                        + "WHERE provisioning_request_id = ?",
                result.request().id()))
                .containsEntry("publication_status", "CANCELLED")
                .containsEntry("payload_key_version", "destroyed")
                .containsEntry("payload_destroyed", true);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_tenant", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_user", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_membership", Integer.class)).isZero();
        assertIdentityError(
                () -> provisioningService.activateNewUser(
                        notification.activationGrantId(),
                        notification.activationSecret(),
                        "activation-password-2026",
                        "req-activation-after-lock"),
                IdentityErrorCode.TENANT_ACTIVATION_CREDENTIAL_INVALID);
    }

    @Test
    void existingUserMustAcceptAsTheReservedSubjectAndKeepsTheOriginalDefaultTenant() {
        ProvisionedIdentity existing = provision(
                "accept-home",
                "Accept Home",
                "accept-owner@example.com",
                "Accept Owner");
        TenantProvisioningResult result = createProvisioning(
                "accept-next",
                "Accept Next",
                "accept-owner@example.com",
                "Ignored Caller Display",
                "idem-accept-next",
                "req-accept-next",
                new TenantProvisioningPolicy(
                        Duration.ofDays(7), Duration.ofHours(24), 5));
        TenantProvisioningNotification notification =
                provisioningNotificationProtector.unprotect(
                        claimProvisioningNotification("relay:existing-user")
                                .protectedNotification());

        assertThat(notification.deliveryChannel()).isEqualTo("IDENTITY_SUBJECT");
        assertThat(notification.recipientReference())
                .isEqualTo(existing.subjectId().toString());
        assertThat(notification.activationGrantId()).isNull();
        assertThat(notification.activationSecret()).isNull();
        assertIdentityError(
                () -> provisioningService.acceptExistingUser(
                        result.request().id(),
                        UUID.randomUUID(),
                        "req-accept-wrong-user"),
                IdentityErrorCode.TENANT_PROVISIONING_ACCEPTANCE_FORBIDDEN);

        TenantProvisioningCompletion completion =
                provisioningService.acceptExistingUser(
                        result.request().id(),
                        existing.subjectId(),
                        "req-accept-existing");

        assertThat(completion.activated()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_user WHERE id = ?",
                Integer.class,
                existing.subjectId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT is_default FROM ainer_identity_membership "
                        + "WHERE tenant_id = ? AND user_id = ?",
                Boolean.class,
                result.request().tenantId(),
                existing.subjectId())).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM ainer_identity_membership "
                        + "WHERE user_id = ? AND is_default = true",
                UUID.class,
                existing.subjectId())).isEqualTo(existing.tenantId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT actor_type FROM ainer_identity_platform_operation_audit "
                        + "WHERE operation_id = ? AND phase = 'ACTIVATED'",
                String.class,
                result.request().id())).isEqualTo("USER");
    }

    @Test
    void activationRollsBackTenantUserAndMembershipWhenPersistenceFails() {
        TenantProvisioningResult result = createProvisioning(
                "activation-rollback",
                "Activation Rollback",
                "activation-rollback@example.com",
                "Activation Rollback Owner",
                "idem-activation-rollback",
                "req-activation-rollback",
                new TenantProvisioningPolicy(
                        Duration.ofDays(1), Duration.ofHours(1), 5));
        TenantProvisioningNotification notification =
                provisioningNotificationProtector.unprotect(
                        claimProvisioningNotification("relay:rollback")
                                .protectedNotification());
        identityRepository.failNextMembership();

        assertThatThrownBy(() -> provisioningService.activateNewUser(
                notification.activationGrantId(),
                notification.activationSecret(),
                "activation-password-2026",
                "req-activate-rollback"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated membership persistence failure");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_tenant WHERE id = ?",
                Integer.class,
                result.request().tenantId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_user WHERE id = ?",
                Integer.class,
                result.request().ownerSubjectId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_membership WHERE tenant_id = ?",
                Integer.class,
                result.request().tenantId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_identity_activation_grant WHERE id = ?",
                String.class,
                notification.activationGrantId())).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_identity_tenant_provisioning_request WHERE id = ?",
                String.class,
                result.request().id())).isEqualTo("REQUESTED");
    }

    @Test
    void notificationRelayRetriesAProviderFailureAndPublishesProtectedPayload() {
        createProvisioning(
                "notification-retry",
                "Notification Retry",
                "notification-retry@example.com",
                "Notification Retry Owner",
                "idem-notification-retry",
                "req-notification-retry",
                new TenantProvisioningPolicy(
                        Duration.ofDays(1), Duration.ofHours(1), 5));
        Instant firstAttemptAt = Instant.now().plusSeconds(1);
        AtomicInteger attempts = new AtomicInteger();
        TenantProvisioningNotificationPublisher publisher = delivery -> {
            if (attempts.incrementAndGet() == 1) {
                throw new TenantProvisioningNotificationPublicationException(
                        "AINER.IDENTITY.EMAIL_TEMPORARILY_UNAVAILABLE",
                        "simulated provider failure");
            }
        };
        TenantProvisioningNotificationRelay firstRelay =
                new TenantProvisioningNotificationRelay(
                        provisioningNotificationOutboxService,
                        provisioningNotificationProtector,
                        publisher,
                        Clock.fixed(firstAttemptAt, ZoneOffset.UTC));

        assertThat(firstRelay.relay(
                "relay:retry",
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                5,
                10))
                .extracting("claimed", "published", "failed")
                .containsExactly(1, 0, 1);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT publication_status, attempt_count, last_error_code "
                        + "FROM ainer_identity_notification_outbox"))
                .containsEntry("publication_status", "FAILED")
                .containsEntry("attempt_count", 1)
                .containsEntry(
                        "last_error_code",
                        "AINER.IDENTITY.EMAIL_TEMPORARILY_UNAVAILABLE");

        TenantProvisioningNotificationRelay retryRelay =
                new TenantProvisioningNotificationRelay(
                        provisioningNotificationOutboxService,
                        provisioningNotificationProtector,
                        publisher,
                        Clock.fixed(firstAttemptAt.plusSeconds(2), ZoneOffset.UTC));
        assertThat(retryRelay.relay(
                "relay:retry",
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                5,
                10))
                .extracting("claimed", "published", "failed")
                .containsExactly(1, 1, 0);
        assertThat(attempts).hasValue(2);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT publication_status, attempt_count, published_at "
                        + "FROM ainer_identity_notification_outbox"))
                .containsEntry("publication_status", "PUBLISHED")
                .containsEntry("attempt_count", 2);
        assertThat(provisioningNotificationOutboxService.status(5))
                .extracting("pending", "failed", "exhausted", "published")
                .containsExactly(0L, 0L, 0L, 1L);
    }

    @Test
    void notificationReceiptRecordsOneTerminalFactOnlyAfterPublication() {
        createProvisioning(
                "notification-receipt",
                "Notification Receipt",
                "notification-receipt@example.com",
                "Notification Receipt Owner",
                "idem-notification-receipt",
                "req-notification-receipt",
                new TenantProvisioningPolicy(
                        Duration.ofDays(1), Duration.ofHours(1), 5));
        TenantProvisioningNotificationOutboxEntry delivery =
                claimProvisioningNotification("relay:receipt");
        Instant publishedAt = Instant.now().minusSeconds(2);
        provisioningNotificationOutboxService.markPublished(
                delivery.id(), "relay:receipt", publishedAt);
        NotificationGatewayActor actor = new NotificationGatewayActor(
                "notification-gateway", null, "req-receipt-db-1");
        Instant occurredAt = Instant.now().minusSeconds(1);
        TenantProvisioningNotificationReceiptCommand command =
                new TenantProvisioningNotificationReceiptCommand(
                        "gateway-event-db-1",
                        delivery.id(),
                        TenantProvisioningNotificationDeliveryStatus.DELIVERED,
                        occurredAt,
                        null);

        var created = provisioningNotificationReceiptService.record(command, actor);
        var replayed = provisioningNotificationReceiptService.record(command, actor);
        var duplicateEvent = provisioningNotificationReceiptService.record(
                new TenantProvisioningNotificationReceiptCommand(
                        "gateway-event-db-duplicate",
                        delivery.id(),
                        TenantProvisioningNotificationDeliveryStatus.DELIVERED,
                        occurredAt,
                        null),
                actor);

        assertThat(created.created()).isTrue();
        assertThat(replayed.created()).isFalse();
        assertThat(duplicateEvent.created()).isFalse();
        assertThat(jdbcTemplate.queryForMap(
                "SELECT uuid_extract_version(id)::integer AS id_version, "
                        + "gateway_client_id, gateway_event_id, delivery_status, "
                        + "failure_code, request_id "
                        + "FROM ainer_identity_notification_delivery_receipt "
                        + "WHERE notification_id = ?",
                delivery.id()))
                .containsEntry("id_version", 7)
                .containsEntry("gateway_client_id", "notification-gateway")
                .containsEntry("gateway_event_id", "gateway-event-db-1")
                .containsEntry("delivery_status", "DELIVERED")
                .containsEntry("failure_code", null)
                .containsEntry("request_id", "req-receipt-db-1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) "
                        + "FROM ainer_identity_notification_delivery_receipt "
                        + "WHERE notification_id = ?",
                Integer.class,
                delivery.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = "
                        + "'ainer_identity_notification_delivery_receipt' "
                        + "AND (column_name LIKE '%secret%' "
                        + "OR column_name LIKE '%token%' "
                        + "OR column_name LIKE '%address%' "
                        + "OR column_name LIKE '%payload%' "
                        + "OR column_name LIKE '%body%')",
                Integer.class)).isZero();

        assertIdentityError(
                () -> provisioningNotificationReceiptService.record(
                        new TenantProvisioningNotificationReceiptCommand(
                                "gateway-event-db-1",
                                delivery.id(),
                                TenantProvisioningNotificationDeliveryStatus.FAILED,
                                occurredAt,
                                "BOUNCED"),
                        actor),
                IdentityErrorCode
                        .NOTIFICATION_RECEIPT_IDEMPOTENCY_CONFLICT);

        createProvisioning(
                "notification-receipt-pending",
                "Notification Receipt Pending",
                "notification-receipt-pending@example.com",
                "Notification Receipt Pending Owner",
                "idem-notification-receipt-pending",
                "req-notification-receipt-pending",
                new TenantProvisioningPolicy(
                        Duration.ofDays(1), Duration.ofHours(1), 5));
        UUID pendingNotificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM ainer_identity_notification_outbox "
                        + "WHERE publication_status = 'PENDING'",
                UUID.class);
        assertIdentityError(
                () -> provisioningNotificationReceiptService.record(
                        new TenantProvisioningNotificationReceiptCommand(
                                "gateway-event-db-pending",
                                pendingNotificationId,
                                TenantProvisioningNotificationDeliveryStatus.DELIVERED,
                                Instant.now(),
                                null),
                        actor),
                IdentityErrorCode.NOTIFICATION_RECEIPT_STATE_CONFLICT);
    }

    @Test
    void concurrentTenantProvisioningAllowsOnlyOneOpenCodeReservation()
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> first = executor.submit(() -> concurrentProvisioning(
                    "concurrent-owner-a@example.com",
                    "Concurrent Owner A",
                    "idem-concurrent-a",
                    "req-concurrent-a",
                    ready,
                    start));
            Future<Object> second = executor.submit(() -> concurrentProvisioning(
                    "concurrent-owner-b@example.com",
                    "Concurrent Owner B",
                    "idem-concurrent-b",
                    "req-concurrent-b",
                    ready,
                    start));
            ready.await();
            start.countDown();
            List<Object> outcomes = List.of(first.get(), second.get());

            assertThat(outcomes)
                    .filteredOn(TenantProvisioningResult.class::isInstance)
                    .hasSize(1);
            assertThat(outcomes)
                    .filteredOn(
                            IdentityErrorCode.TENANT_PROVISIONING_CONFLICT::equals)
                    .hasSize(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) "
                            + "FROM ainer_identity_tenant_provisioning_request "
                            + "WHERE tenant_code = 'concurrent-code' "
                            + "AND status = 'REQUESTED'",
                    Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Object concurrentProvisioning(
            String ownerUsername,
            String ownerDisplayName,
            String idempotencyKey,
            String requestId,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            return provisioningService.create(
                    new CreateTenantProvisioningCommand(
                            "concurrent-code",
                            "Concurrent Tenant",
                            ownerUsername,
                            ownerDisplayName,
                            "EMAIL",
                            ownerUsername,
                            idempotencyKey,
                            "ORDER-CONCURRENT"),
                    new PlatformProvisioningActor(
                            "platform-operator", null, requestId),
                    provisioningPolicy(Duration.ofDays(7)));
        } catch (BusinessException exception) {
            return exception.errorCode();
        }
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
    void findActiveMembershipsReturnsAllActiveWithContextForMultiTenantUser() {
        ProvisionedIdentity ownerA = provision(
                "alpha", "Alpha Tenant", "owner@alpha.dev", "Alpha Owner");
        ProvisionedIdentity ownerB = provision(
                "beta", "Beta Tenant", "owner@beta.dev", "Beta Owner");
        AuthenticatedActor ownerBActor = managerActor(ownerB);

        // ownerA 的用户被加入 Beta Tenant 作为 MEMBER
        memberService.addMember(
                ownerBActor,
                ownerB.tenantId(),
                new AddTenantMemberCommand(
                        "OWNER@ALPHA.DEV", null, TenantRole.MEMBER, "cross-tenant"),
                "req-cross-1");

        List<TenantContextEntry> memberships = service.findActiveMemberships(ownerA.subjectId());

        assertThat(memberships).hasSize(2);
        // 默认租户排在首位
        TenantContextEntry defaultEntry = memberships.get(0);
        assertThat(defaultEntry.tenantId()).isEqualTo(ownerA.tenantId());
        assertThat(defaultEntry.tenantCode()).isEqualTo("alpha");
        assertThat(defaultEntry.role()).isEqualTo(TenantRole.OWNER);
        assertThat(defaultEntry.defaultTenant()).isTrue();
        // 第二个 membership
        TenantContextEntry betaEntry = memberships.get(1);
        assertThat(betaEntry.tenantId()).isEqualTo(ownerB.tenantId());
        assertThat(betaEntry.tenantCode()).isEqualTo("beta");
        assertThat(betaEntry.role()).isEqualTo(TenantRole.MEMBER);
        assertThat(betaEntry.defaultTenant()).isFalse();
    }

    @Test
    void findActiveMembershipValidatesRealtimeRelationship() {
        ProvisionedIdentity owner = provision(
                "gamma", "Gamma Tenant", "owner@gamma.dev", "Gamma Owner");
        ProvisionedIdentity other = provision(
                "delta", "Delta Tenant", "owner@delta.dev", "Delta Owner");
        AuthenticatedActor ownerActor = managerActor(owner);

        // other 用户加入 gamma tenant
        memberService.addMember(
                ownerActor,
                owner.tenantId(),
                new AddTenantMemberCommand(
                        null, other.subjectId(), TenantRole.ADMIN, "joined"),
                "req-join-1");

        // 实时校验：存在的 ACTIVE membership
        Optional<IdentityDirectoryEntry> active = service.findActiveMembership(
                owner.tenantId(), other.subjectId());
        assertThat(active).isPresent();
        assertThat(active.get().role()).isEqualTo(TenantRole.ADMIN);

        // 实时校验：非成员关系返回空
        assertThat(service.findActiveMembership(
                other.tenantId(), owner.subjectId())).isEmpty();

        // 撤销后实时校验也返回空
        memberService.removeMember(
                ownerActor, owner.tenantId(), other.subjectId(), "revoked", "req-remove-1");
        assertThat(service.findActiveMembership(
                owner.tenantId(), other.subjectId())).isEmpty();
        // 但该用户在原 tenant 仍有一个 membership
        assertThat(service.findActiveMemberships(other.subjectId()))
                .extracting(TenantContextEntry::tenantCode)
                .containsExactly("delta");
    }

    @Test
    void disabledMembershipNotListedInActiveMemberships() {
        ProvisionedIdentity owner = provision(
                "epsilon", "Epsilon Tenant", "owner@epsilon.dev", "Epsilon Owner");
        ProvisionedIdentity member = provision(
                "zeta", "Zeta Tenant", "zeta-user@zeta.dev", "Zeta Owner");
        AuthenticatedActor ownerActor = managerActor(owner);

        memberService.addMember(
                ownerActor,
                owner.tenantId(),
                new AddTenantMemberCommand(
                        null, member.subjectId(), TenantRole.MEMBER, "joined"),
                "req-join-2");

        // 加入后有两个 ACTIVE membership
        assertThat(service.findActiveMemberships(member.subjectId())).hasSize(2);

        // 撤销后只剩一个
        memberService.removeMember(
                ownerActor, owner.tenantId(), member.subjectId(), "offboarded", "req-remove-2");
        List<TenantContextEntry> remaining = service.findActiveMemberships(member.subjectId());
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).tenantCode()).isEqualTo("zeta");
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

    private static TenantProvisioningPolicy provisioningPolicy(Duration requestTtl) {
        Duration activationTtl = requestTtl.compareTo(Duration.ofHours(24)) < 0
                ? requestTtl
                : Duration.ofHours(24);
        return new TenantProvisioningPolicy(requestTtl, activationTtl, 5);
    }

    private TenantProvisioningResult createProvisioning(
            String tenantCode,
            String tenantName,
            String ownerUsername,
            String ownerDisplayName,
            String idempotencyKey,
            String requestId,
            TenantProvisioningPolicy policy) {
        return provisioningService.create(
                new CreateTenantProvisioningCommand(
                        tenantCode,
                        tenantName,
                        ownerUsername,
                        ownerDisplayName,
                        "EMAIL",
                        ownerUsername,
                        idempotencyKey,
                        "ORDER-" + tenantCode.toUpperCase()),
                new PlatformProvisioningActor(
                        "platform-operator", null, requestId),
                policy);
    }

    private TenantProvisioningNotificationOutboxEntry claimProvisioningNotification(
            String leaseOwner) {
        return provisioningNotificationOutboxService.claimBatch(
                        leaseOwner,
                        Instant.now().plusSeconds(1),
                        Duration.ofMinutes(1),
                        5,
                        1)
                .getFirst();
    }

    @Test
    void ownershipTransferSwapsOwnerAndAdminAtomicallyWithAuditAndRevocationEvents() {
        ProvisionedIdentity owner = provision(
                "owner-tx", "Owner TX", "owner@tx.dev", "TX Owner");
        ProvisionedIdentity admin = provision(
                "admin-home", "Admin Home", "admin@tx.dev", "TX Admin");
        AuthenticatedActor ownerActor = transferActor(owner);
        // 给 owner tenant 添加 admin 用户为 ADMIN
        memberService.addMember(
                ownerActor, owner.tenantId(),
                new AddTenantMemberCommand("ADMIN@TX.DEV", null, TenantRole.ADMIN, "onboarding"),
                "req-add-admin");

        // 发起转移
        OwnershipTransfer transfer = transferService.initiateTransfer(
                ownerActor, owner.tenantId(), admin.subjectId(), "succession", "req-init-1");
        assertThat(transfer.status()).isEqualTo(OwnershipTransferStatus.REQUESTED);

        // admin（目标）接受转移
        AuthenticatedActor adminActor = transferActorFor(admin, owner.tenantId());
        OwnershipTransfer executed = transferService.acceptTransfer(
                adminActor, owner.tenantId(), transfer.id(), "accepted", "req-accept-1");
        assertThat(executed.status()).isEqualTo(OwnershipTransferStatus.EXECUTED);
        assertThat(executed.executedBySubjectId()).isEqualTo(admin.subjectId());

        // 角色：原 owner 降为 ADMIN，admin 升为 OWNER
        IdentityDirectoryEntry demotedOwner = directoryService.findActiveMember(
                owner.tenantId(), owner.subjectId()).orElseThrow();
        assertThat(demotedOwner.role()).isEqualTo(TenantRole.ADMIN);
        IdentityDirectoryEntry promotedAdmin = directoryService.findActiveMember(
                owner.tenantId(), admin.subjectId()).orElseThrow();
        assertThat(promotedAdmin.role()).isEqualTo(TenantRole.OWNER);

        // 审计记录
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_member_audit "
                        + "WHERE tenant_id = ? AND operation = 'OWNERSHIP_TRANSFERRED'",
                Integer.class, owner.tenantId())).isEqualTo(1);

        // 双方 access event（撤销链路）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_access_event "
                        + "WHERE tenant_id = ? AND event_type = 'IDENTITY_MEMBERSHIP_ROLE_CHANGED' "
                        + "AND subject_id IN (?, ?)",
                Integer.class, owner.tenantId(), owner.subjectId(), admin.subjectId())).isEqualTo(2);
    }

    @Test
    void ownershipTransferEnforcesInitiatorOwnerAndTargetAdminAndAtMostOneOutstanding() {
        ProvisionedIdentity owner = provision(
                "ot-1", "OT One", "owner@ot1.dev", "OT Owner");
        ProvisionedIdentity admin = provision(
                "ot-2", "OT Two", "admin@ot2.dev", "OT Admin");
        ProvisionedIdentity member = provision(
                "ot-3", "OT Three", "member@ot3.dev", "OT Member");
        AuthenticatedActor ownerActor = transferActor(owner);
        memberService.addMember(ownerActor, owner.tenantId(),
                new AddTenantMemberCommand("ADMIN@OT2.DEV", null, TenantRole.ADMIN, "add"),
                "req-1");
        memberService.addMember(ownerActor, owner.tenantId(),
                new AddTenantMemberCommand("MEMBER@OT3.DEV", null, TenantRole.MEMBER, "add"),
                "req-2");

        // 非 OWNER 不能发起
        AuthenticatedActor adminActor = transferActorFor(admin, owner.tenantId());
        assertStandardError(
                () -> transferService.initiateTransfer(
                        adminActor, owner.tenantId(), member.subjectId(), "try", "req-no-owner"),
                StandardErrorCode.FORBIDDEN);

        // 目标必须是 ADMIN，不能是 MEMBER
        assertIdentityError(
                () -> transferService.initiateTransfer(
                        ownerActor, owner.tenantId(), member.subjectId(), "try-member", "req-no-admin"),
                IdentityErrorCode.OWNERSHIP_TRANSFER_TARGET_INELIGIBLE);

        // 正常发起
        OwnershipTransfer transfer = transferService.initiateTransfer(
                ownerActor, owner.tenantId(), admin.subjectId(), "init", "req-ok-1");

        // 同 tenant 同时只能一个未完成转移
        assertIdentityError(
                () -> transferService.initiateTransfer(
                        ownerActor, owner.tenantId(), admin.subjectId(), "dup", "req-ok-2"),
                IdentityErrorCode.OWNERSHIP_TRANSFER_OUTSTANDING_CONFLICT);

        // 发起者（OWNER）不能接受自己的转移（角色不是 ADMIN）
        assertStandardError(
                () -> transferService.acceptTransfer(
                        ownerActor, owner.tenantId(), transfer.id(), "hijack", "req-hijack"),
                StandardErrorCode.FORBIDDEN);

        // 发起者取消
        OwnershipTransfer cancelled = transferService.cancelTransfer(
                ownerActor, owner.tenantId(), transfer.id(), "cancelled", "req-cancel-1");
        assertThat(cancelled.status()).isEqualTo(OwnershipTransferStatus.CANCELLED);

        // 取消后可以再次发起
        assertThat(transferService.initiateTransfer(
                ownerActor, owner.tenantId(), admin.subjectId(), "retry", "req-ok-3").status())
                .isEqualTo(OwnershipTransferStatus.REQUESTED);
    }

    @Test
    void concurrentOwnershipTransferAcceptOnlyOneSucceeds() throws Exception {
        ProvisionedIdentity owner = provision(
                "cc-1", "Concurrent One", "owner@cc1.dev", "CC Owner");
        ProvisionedIdentity admin = provision(
                "cc-2", "Concurrent Two", "admin@cc2.dev", "CC Admin");
        AuthenticatedActor ownerActor = transferActor(owner);
        memberService.addMember(ownerActor, owner.tenantId(),
                new AddTenantMemberCommand("ADMIN@CC2.DEV", null, TenantRole.ADMIN, "add"),
                "req-cc-add");
        OwnershipTransfer transfer = transferService.initiateTransfer(
                ownerActor, owner.tenantId(), admin.subjectId(), "concurrent", "req-cc-init");
        AuthenticatedActor adminActor = transferActorFor(admin, owner.tenantId());

        int threads = 4;
        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successes = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger();

        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final String requestId = "req-cc-accept-" + i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("barrier timeout");
                    }
                    transferService.acceptTransfer(
                            adminActor, owner.tenantId(), transfer.id(),
                            "concurrent-accept", requestId);
                    successes.incrementAndGet();
                } catch (BusinessException exception) {
                    failures.incrementAndGet();
                } catch (Exception exception) {
                    // unexpected
                }
                return null;
            }));
        }
        ready.await(5, java.util.concurrent.TimeUnit.SECONDS);
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(threads - 1);
        // 最终只有一个 ACTIVE OWNER
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_membership "
                        + "WHERE tenant_id = ? AND role = 'OWNER' AND status = 'ACTIVE'",
                Integer.class, owner.tenantId())).isEqualTo(1);
    }

    @Test
    void expiredOwnershipTransferCannotBeAccepted() {
        ProvisionedIdentity owner = provision(
                "exp-1", "Expired One", "owner@exp1.dev", "EXP Owner");
        ProvisionedIdentity admin = provision(
                "exp-2", "Expired Two", "admin@exp2.dev", "EXP Admin");
        AuthenticatedActor ownerActor = transferActor(owner);
        memberService.addMember(ownerActor, owner.tenantId(),
                new AddTenantMemberCommand("ADMIN@EXP2.DEV", null, TenantRole.ADMIN, "add"),
                "req-exp-add");
        OwnershipTransfer transfer = transferService.initiateTransfer(
                ownerActor, owner.tenantId(), admin.subjectId(), "will-expire", "req-exp-init");

        // 手动把 expires_at 设为过去，模拟过期
        jdbcTemplate.update(
                "UPDATE ainer_identity_ownership_transfer SET expires_at = NOW() - INTERVAL '1 minute' "
                        + "WHERE id = ?", transfer.id());

        AuthenticatedActor adminActor = transferActorFor(admin, owner.tenantId());
        assertIdentityError(
                () -> transferService.acceptTransfer(
                        adminActor, owner.tenantId(), transfer.id(), "late", "req-exp-accept"),
                IdentityErrorCode.OWNERSHIP_TRANSFER_STATE_CONFLICT);

        // 过期的转移仍可取消
        OwnershipTransfer cancelled = transferService.cancelTransfer(
                ownerActor, owner.tenantId(), transfer.id(), "expired-cancel", "req-exp-cancel");
        assertThat(cancelled.status()).isEqualTo(OwnershipTransferStatus.CANCELLED);
    }

    @Autowired
    private dev.ainer.module.identity.account.application.OwnershipTransferService transferService;

    @Autowired
    private dev.ainer.module.identity.account.application.OwnershipRecoveryService ownershipRecoveryService;

    @Test
    void ownershipRecoveryPromotesAdminAndDemotesOldOwnerWithTwoServiceApproval() {
        ProvisionedIdentity owner = provision(
                "rec-t", "Recovery Tenant", "owner@rec.dev", "Rec Owner");
        ProvisionedIdentity admin = provision(
                "rec-a", "Recovery Admin Home", "admin@rec.dev", "Rec Admin");
        AuthenticatedActor ownerActor = transferActor(owner);
        memberService.addMember(ownerActor, owner.tenantId(),
                new AddTenantMemberCommand("ADMIN@REC.DEV", null, TenantRole.ADMIN, "add"),
                "req-rec-add");

        // SERVICE-1 发起恢复
        OwnershipRecovery recovery = ownershipRecoveryService.requestRecovery(
                "recovery-requester", owner.tenantId(), admin.subjectId(), "INC-2026-001");
        assertThat(recovery.status()).isEqualTo(OwnershipTransferStatus.REQUESTED);

        // 同一 SERVICE 不能批准
        assertIdentityError(
                () -> ownershipRecoveryService.approveAndExecute(
                        "recovery-requester", owner.tenantId(), recovery.id()),
                IdentityErrorCode.OWNERSHIP_RECOVERY_APPROVER_MUST_DIFFER);

        // SERVICE-2 批准并执行
        OwnershipRecovery executed = ownershipRecoveryService.approveAndExecute(
                "recovery-approver", owner.tenantId(), recovery.id());
        assertThat(executed.status()).isEqualTo(OwnershipTransferStatus.EXECUTED);
        assertThat(executed.approverServiceId()).isEqualTo("recovery-approver");

        // 原 OWNER 降为 ADMIN，admin 升为 OWNER
        assertThat(directoryService.findActiveMember(owner.tenantId(), owner.subjectId())
                .orElseThrow().role()).isEqualTo(TenantRole.ADMIN);
        assertThat(directoryService.findActiveMember(owner.tenantId(), admin.subjectId())
                .orElseThrow().role()).isEqualTo(TenantRole.OWNER);

        // 只有一个 ACTIVE OWNER
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_membership "
                        + "WHERE tenant_id = ? AND role = 'OWNER' AND status = 'ACTIVE'",
                Integer.class, owner.tenantId())).isEqualTo(1);

        // 安全操作审计：REQUESTED + EXECUTED
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_security_operation_audit "
                        + "WHERE operation_id = ? AND operation_type = 'OWNERSHIP_RECOVERY'",
                Integer.class, recovery.id())).isEqualTo(2);
    }

    @Test
    void ownershipRecoveryRejectsNonAdminTarget() {
        ProvisionedIdentity owner = provision(
                "rec-n", "Rec N", "owner@recn.dev", "Rec N Owner");
        ProvisionedIdentity member = provision(
                "rec-m", "Rec M Home", "member@recm.dev", "Rec M Member");
        AuthenticatedActor ownerActor = transferActor(owner);
        memberService.addMember(ownerActor, owner.tenantId(),
                new AddTenantMemberCommand("MEMBER@RECM.DEV", null, TenantRole.MEMBER, "add"),
                "req-recn-add");

        assertIdentityError(
                () -> ownershipRecoveryService.requestRecovery(
                        "recovery-requester", owner.tenantId(), member.subjectId(), "INC-2026-002"),
                IdentityErrorCode.OWNERSHIP_RECOVERY_TARGET_INELIGIBLE);
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

    private static AuthenticatedActor transferActor(ProvisionedIdentity identity) {
        java.util.Set<String> authorities = new java.util.HashSet<>(managerAuthorities());
        authorities.add("SCOPE_" + AinerSecurityScopes.TENANT_OWNERSHIP_TRANSFER);
        return new AuthenticatedActor(
                identity.subjectId().toString(),
                identity.tenantId().toString(),
                AuthenticatedActor.USER,
                authorities);
    }

    private static AuthenticatedActor transferActorFor(ProvisionedIdentity identity, UUID tenantId) {
        return new AuthenticatedActor(
                identity.subjectId().toString(),
                tenantId.toString(),
                AuthenticatedActor.USER,
                java.util.Set.of("SCOPE_" + AinerSecurityScopes.TENANT_OWNERSHIP_TRANSFER));
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
    @Profile("identity-module-integration-test")
    static class FailureProbeConfiguration {

        @Bean
        @Primary
        ControllableIdentityRepository controllableIdentityRepository(
                MybatisIdentityRepository delegate) {
            return new ControllableIdentityRepository(delegate);
        }

        @Bean
        TenantProvisioningNotificationPayloadProtector
                tenantProvisioningNotificationPayloadProtector() {
            return new AesGcmTenantProvisioningNotificationPayloadProtector(
                    "test-v1",
                    Map.of("test-v1", new byte[32]),
                    new SecureRandom());
        }
    }

    static final class ControllableIdentityRepository implements IdentityRepository {

        private final IdentityRepository delegate;
        private boolean failNextAccessEvent;
        private boolean failNextMembership;

        ControllableIdentityRepository(IdentityRepository delegate) {
            this.delegate = delegate;
        }

        void failNextAccessEvent() {
            failNextAccessEvent = true;
        }

        void failNextMembership() {
            failNextMembership = true;
        }

        void reset() {
            failNextAccessEvent = false;
            failNextMembership = false;
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
            if (failNextMembership) {
                failNextMembership = false;
                throw new IllegalStateException(
                        "simulated membership persistence failure");
            }
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
        public List<TenantContextEntry> findActiveMembershipsBySubject(UUID subjectId) {
            return delegate.findActiveMembershipsBySubject(subjectId);
        }

        @Override
        public void insertOwnershipTransfer(OwnershipTransfer transfer) {
            delegate.insertOwnershipTransfer(transfer);
        }

        @Override
        public Optional<OwnershipTransfer> findOwnershipTransfer(UUID id) {
            return delegate.findOwnershipTransfer(id);
        }

        @Override
        public Optional<OwnershipTransfer> findOwnershipTransferForUpdate(UUID id) {
            return delegate.findOwnershipTransferForUpdate(id);
        }

        @Override
        public boolean completeOwnershipTransfer(
                UUID id, UUID tenantId, UUID executedBySubjectId,
                Instant executedAt, Instant updatedAt) {
            return delegate.completeOwnershipTransfer(id, tenantId, executedBySubjectId, executedAt, updatedAt);
        }

        @Override
        public boolean cancelOwnershipTransfer(UUID id, UUID tenantId, Instant updatedAt) {
            return delegate.cancelOwnershipTransfer(id, tenantId, updatedAt);
        }

        @Override
        public void insertOwnershipRecovery(OwnershipRecovery recovery) {
            delegate.insertOwnershipRecovery(recovery);
        }

        @Override
        public Optional<OwnershipRecovery> findOwnershipRecovery(UUID id) {
            return delegate.findOwnershipRecovery(id);
        }

        @Override
        public Optional<OwnershipRecovery> findOwnershipRecoveryForUpdate(UUID id) {
            return delegate.findOwnershipRecoveryForUpdate(id);
        }

        @Override
        public boolean executeOwnershipRecovery(
                UUID id, UUID tenantId, String approverServiceId,
                Instant executedAt, Instant updatedAt) {
            return delegate.executeOwnershipRecovery(id, tenantId, approverServiceId, executedAt, updatedAt);
        }

        @Override
        public boolean cancelOwnershipRecovery(UUID id, UUID tenantId, Instant updatedAt) {
            return delegate.cancelOwnershipRecovery(id, tenantId, updatedAt);
        }

        @Override
        public void insertSecurityOperationAudit(
                UUID operationId, UUID tenantId, UUID targetId, String operationType,
                String phase, String actorServiceId, String incidentReference, Instant occurredAt) {
            delegate.insertSecurityOperationAudit(
                    operationId, tenantId, targetId, operationType,
                    phase, actorServiceId, incidentReference, occurredAt);
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
        public void acquireIdentityLock(String lockKey) {
            delegate.acquireIdentityLock(lockKey);
        }

        @Override
        public boolean openProvisioningReservationExists(
                String tenantCode,
                String normalizedUsername) {
            return delegate.openProvisioningReservationExists(
                    tenantCode, normalizedUsername);
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
