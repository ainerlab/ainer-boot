package dev.ainer.module.notification;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.notification.notification.application.NotificationApplicationService;
import dev.ainer.module.notification.notification.application.NotificationAuthorities;
import dev.ainer.module.notification.notification.application.NotificationErrorCode;
import dev.ainer.module.notification.notification.application.NotificationRecordRepository;
import dev.ainer.module.notification.notification.domain.NotificationChannel;
import dev.ainer.module.notification.notification.domain.NotificationIntent;
import dev.ainer.module.notification.notification.domain.NotificationRecord;
import dev.ainer.module.notification.notification.domain.NotificationStatus;
import dev.ainer.module.notification.notification.domain.NotificationTemplate;
import dev.ainer.security.principal.HumanSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the notification module (ADR-0040 management hardening). Validates PG 18
 * JSONB templates, SKIP LOCKED queue claiming, template rendering, retry scheduling, plus the
 * managed template lifecycle (optimistic-locked update/status/page), record pagination, audit
 * and scope enforcement.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = NotificationIntegrationTest.TestApplication.class,
        properties = {
                "ainer.notification.enabled=true",
                "ainer.notification.poll-interval-ms=999999",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class NotificationIntegrationTest {

    private static final IdentityAuthorityRef AUTHORITY =
            new IdentityAuthorityRef("https://auth.ainer.test");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_notification_test")
                    .withUsername("ainer")
                    .withPassword("ainer");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    NotificationApplicationService service;
    @Autowired
    NotificationRecordRepository recordRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private final AuthenticatedPrincipal manager = principal(
            NotificationAuthorities.READ, NotificationAuthorities.MANAGE,
            NotificationAuthorities.SUBMIT);
    private final AuthenticatedPrincipal reader = principal(NotificationAuthorities.READ);

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM ainer_notification_audit");
        jdbcTemplate.execute("DELETE FROM ainer_notification_record");
        jdbcTemplate.execute("DELETE FROM ainer_notification_template");
    }

    @Test
    void createTemplateWritesAuditAndSubmitsWithRendering() {
        UUID templateId = service.createTemplate(manager, "req-1", "welcome_email",
                NotificationChannel.EMAIL,
                "Welcome, {name}!", "Hello {name}, your account is ready.",
                Map.of("name", "string"));
        assertThat(templateId.version()).isEqualTo(7);

        UUID recordId = service.submit(manager, null, new NotificationIntent.TemplateIntent(
                NotificationChannel.EMAIL, "user@test.com", "welcome_email",
                Map.of("name", "Alice")));

        Optional<NotificationRecord> record = recordRepository.findById(recordId);
        assertThat(record).isPresent();
        assertThat(record.get().title()).isEqualTo("Welcome, Alice!");
        assertThat(record.get().body()).isEqualTo("Hello Alice, your account is ready.");
        assertThat(record.get().status()).isEqualTo(NotificationStatus.PENDING);

        Integer audits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_notification_audit WHERE operation = 'TEMPLATE_CREATED'",
                Integer.class);
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void submitDirectIntentWithoutTemplate() {
        UUID recordId = service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.SMS, "+8613800138000", "OTP", "Your code is 123456", null));

        Optional<NotificationRecord> record = recordRepository.findById(recordId);
        assertThat(record).isPresent();
        assertThat(record.get().title()).isEqualTo("OTP");
        assertThat(record.get().body()).isEqualTo("Your code is 123456");
    }

    @Test
    void templateChannelMismatchRejected() {
        service.createTemplate(manager, null, "sms_code", NotificationChannel.SMS,
                "Code", "Your code: {code}", Map.of("code", "string"));

        assertThatThrownBy(() -> service.submit(manager, null, new NotificationIntent.TemplateIntent(
                NotificationChannel.EMAIL, "user@test.com", "sms_code", Map.of("code", "1234"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(NotificationErrorCode.CHANNEL_MISMATCH));
    }

    @Test
    void duplicateTemplateCodeRejected() {
        service.createTemplate(manager, null, "dup", NotificationChannel.EMAIL, "T", "B", Map.of());
        assertThatThrownBy(() -> service.createTemplate(manager, null, "dup",
                NotificationChannel.EMAIL, "T2", "B2", Map.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(NotificationErrorCode.TEMPLATE_ALREADY_EXISTS));
    }

    @Test
    void templateLifecycleUsesOptimisticLockStatusAndPagination() {
        UUID id = service.createTemplate(manager, null, "otp_sms", NotificationChannel.SMS,
                "OTP", "Code: {code}", Map.of("code", "string"));

        NotificationTemplate updated = service.updateTemplate(manager, null, id,
                "新标题", null, null, 0);
        assertThat(updated.titleTemplate()).isEqualTo("新标题");
        assertThat(updated.version()).isEqualTo(1L);

        assertThatThrownBy(() -> service.updateTemplate(manager, null, id,
                "再改", null, null, 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(NotificationErrorCode.CONCURRENT_MODIFICATION));

        NotificationTemplate disabled = service.changeTemplateStatus(manager, null, id,
                NotificationTemplate.NotificationTemplateStatus.DISABLED, 1);
        assertThat(disabled.status()).isEqualTo(NotificationTemplate.NotificationTemplateStatus.DISABLED);

        var activePage = service.pageTemplates(manager, "ACTIVE", 1, 20);
        assertThat(activePage.total()).isZero();
        var allPage = service.pageTemplates(manager, null, 1, 20);
        assertThat(allPage.total()).isEqualTo(1);

        Integer statusAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_notification_audit "
                        + "WHERE operation = 'TEMPLATE_STATUS_CHANGED'", Integer.class);
        assertThat(statusAudits).isEqualTo(1);
    }

    @Test
    void recordPaginationFiltersByStatus() {
        // submit the to-be-sent record first: claiming is ordered by created_at, id
        UUID sent = service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.SMS, "b@x", "T", "B", null));
        service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.SMS, "a@x", "T", "B", null));
        assertThat(recordRepository.claimPending(1)).first()
                .satisfies(claimed -> assertThat(claimed.id()).isEqualTo(sent));
        recordRepository.markSent(sent, java.time.Instant.now());

        var pending = service.pageRecords(manager, "PENDING", 1, 20);
        assertThat(pending.total()).isEqualTo(1);

        var sentPage = service.pageRecords(manager, "SENT", 1, 20);
        assertThat(sentPage.total()).isEqualTo(1);

        var all = service.pageRecords(manager, null, 1, 20);
        assertThat(all.total()).isEqualTo(2);
    }

    @Test
    void skipLockedClaimingFlipsStatusToSending() {
        for (int i = 0; i < 3; i++) {
            service.submit(manager, null, new NotificationIntent.DirectIntent(
                    NotificationChannel.SMS, "recip" + i, "T" + i, "B" + i, null));
        }

        List<NotificationRecord> claimed = recordRepository.claimPending(10);
        assertThat(claimed).hasSize(3);
        assertThat(claimed).allSatisfy(r ->
                assertThat(r.status()).isEqualTo(NotificationStatus.SENDING));
    }

    @Test
    void markFailedWithRetrySchedulesNextRetryAndIncrementsCount() {
        UUID id = service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.SMS, "recip", "T", "B", null));
        recordRepository.claimPending(10);

        java.time.Instant nextRetry = java.time.Instant.now().plusSeconds(2);
        recordRepository.markFailed(id, "Connection refused", 0, 3, nextRetry);

        Optional<NotificationRecord> afterFirstFail = recordRepository.findById(id);
        assertThat(afterFirstFail).isPresent();
        assertThat(afterFirstFail.get().status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(afterFirstFail.get().retryCount()).isEqualTo(1);

        recordRepository.markFailed(id, "Still failing", 3, 3, nextRetry);
        Optional<NotificationRecord> afterMaxFail = recordRepository.findById(id);
        assertThat(afterMaxFail.get().status()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void writeAndSubmitWithoutScopeAreForbidden() {
        assertThatThrownBy(() -> service.createTemplate(reader, null, "x",
                NotificationChannel.EMAIL, "T", "B", Map.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(StandardErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.submit(reader, null, new NotificationIntent.DirectIntent(
                NotificationChannel.SMS, "r", "T", "B", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(StandardErrorCode.FORBIDDEN));
        service.pageTemplates(reader, null, 1, 20); // read scope is sufficient
    }

    private static AuthenticatedPrincipal principal(String... scopes) {
        return new AuthenticatedPrincipal(
                new HumanSubjectRef(AUTHORITY, "account:1"),
                AUTHORITY,
                TokenProfile.USER_NEUTRAL_V1,
                "1",
                Set.of("ainer-api"),
                Set.of(scopes),
                "pwd",
                null,
                0L);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({NotificationModuleConfiguration.class})
    static class TestApplication {
    }

    /** Satisfies the controller's resolver dependency without enabling the resource-server chain. */
    @TestConfiguration
    static class PrincipalFixture {

        @Bean
        AuthenticatedPrincipalResolver notificationIntegrationPrincipalResolver() {
            return () -> principal(NotificationAuthorities.READ, NotificationAuthorities.MANAGE,
                    NotificationAuthorities.SUBMIT);
        }
    }
}
