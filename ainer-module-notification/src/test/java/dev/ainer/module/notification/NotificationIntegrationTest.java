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

    private static final String LEASE_OWNER = "test-engine";

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

    /** 领取租约：测试里给足额租约，只有显式 {@link #expireLease} 才让记录可被重新领取。 */
    private static java.time.Instant leaseDeadline() {
        return java.time.Instant.now().plusSeconds(60);
    }

    /** 把租约改成已过期，模拟「发送超过租约时长 / 实例崩溃」，用于验证重新领取路径。 */
    private void expireLease(UUID id) {
        jdbcTemplate.update(
                "UPDATE ainer_notification_record SET lease_expires_at = now() - interval '1 second' "
                        + "WHERE id = ?", id);
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
        assertThat(recordRepository.claimPending(1, LEASE_OWNER, leaseDeadline())).first()
                .satisfies(claimed -> assertThat(claimed.id()).isEqualTo(sent));
        recordRepository.markSent(sent, LEASE_OWNER, java.time.Instant.now());

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

        List<NotificationRecord> claimed = recordRepository.claimPending(10, LEASE_OWNER, leaseDeadline());
        assertThat(claimed).hasSize(3);
        assertThat(claimed).allSatisfy(r ->
                assertThat(r.status()).isEqualTo(NotificationStatus.SENDING));
    }

    @Test
    void sendingRecordWithActiveLeaseIsNotReclaimable() {
        UUID id = service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.SMS, "recip", "T", "B", null));

        List<NotificationRecord> firstClaim =
                recordRepository.claimPending(10, LEASE_OWNER, leaseDeadline());
        assertThat(firstClaim).extracting(NotificationRecord::id).containsExactly(id);

        // 租约未过期：其他消费者（或下一轮轮询）不得再次领取，否则慢发送会被重复投递
        assertThat(recordRepository.claimPending(10, "other-engine", leaseDeadline())).isEmpty();

        // 租约过期：允许重新领取（实例崩溃 / 发送线程卡死的自愈路径）
        expireLease(id);
        List<NotificationRecord> reclaimed =
                recordRepository.claimPending(10, "other-engine", leaseDeadline());
        assertThat(reclaimed).extracting(NotificationRecord::id).containsExactly(id);
    }

    @Test
    void staleLeaseOwnerCannotOverwriteResultOfNewClaimant() {
        UUID id = service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.SMS, "recip", "T", "B", null));
        recordRepository.claimPending(10, LEASE_OWNER, leaseDeadline());
        // 租约到期后被另一个消费者接管
        expireLease(id);
        recordRepository.claimPending(10, "other-engine", leaseDeadline());

        // 旧领取者的迟到写回必须是空操作，不得把记录改成 SENT / 覆盖新领取者的结果
        recordRepository.markSent(id, LEASE_OWNER, java.time.Instant.now());
        assertThat(recordRepository.findById(id)).get()
                .extracting(NotificationRecord::status).isEqualTo(NotificationStatus.SENDING);

        recordRepository.markSent(id, "other-engine", java.time.Instant.now());
        assertThat(recordRepository.findById(id)).get()
                .extracting(NotificationRecord::status).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void markFailedWithRetrySchedulesNextRetryAndIncrementsCount() {
        UUID id = service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.SMS, "recip", "T", "B", null));
        recordRepository.claimPending(10, LEASE_OWNER, leaseDeadline());

        java.time.Instant nextRetry = java.time.Instant.now().plusSeconds(30)
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        recordRepository.markFailed(id, LEASE_OWNER, "Connection refused", 0, 3, nextRetry);

        Optional<NotificationRecord> afterFirstFail = recordRepository.findById(id);
        assertThat(afterFirstFail).isPresent();
        assertThat(afterFirstFail.get().status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(afterFirstFail.get().retryCount()).isEqualTo(1);
        assertThat(afterFirstFail.get().nextRetryAt()).isEqualTo(nextRetry);
        // 退避未到期：不可领取
        assertThat(recordRepository.claimPending(10, LEASE_OWNER, leaseDeadline())).isEmpty();
    }

    @Test
    void timestampsWithNanosecondPrecisionAreReadBackAtMicrosecondPrecision() {
        // 回归（CI 2026-09-11 暴露）：PostgreSQL timestamptz 是微秒精度，而 Instant 是纳秒精度。
        // 持久化边界不截断时，「内存里的时间」与「读回来的时间」不相等；本地纳秒末位恰好为 0
        // 时不会暴露，CI 上必然失败。本用例固定断言该精度契约。
        UUID id = service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.SMS, "recip", "T", "B", null));
        recordRepository.claimPending(10, LEASE_OWNER, leaseDeadline());

        java.time.Instant withNanos = java.time.Instant.now().plusSeconds(30).plusNanos(789);
        java.time.Instant expected = withNanos.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        assertThat(withNanos).isNotEqualTo(expected);
        recordRepository.markFailed(id, LEASE_OWNER, "Connection refused", 0, 3, withNanos);

        assertThat(recordRepository.findById(id)).get()
                .extracting(NotificationRecord::nextRetryAt)
                .isEqualTo(expected);
    }

    @Test
    void retryFailureAtMaxRetriesReachesFailedTerminal() {
        UUID id = service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.SMS, "recip", "T", "B", null));

        // 复刻引擎的重试序列：每次都先领取（写租约），失败时传入当前 retryCount，达到上限即终态
        java.time.Instant due = java.time.Instant.now().minusSeconds(1);
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(recordRepository.claimPending(10, LEASE_OWNER, leaseDeadline()))
                    .extracting(NotificationRecord::id).containsExactly(id);
            recordRepository.markFailed(id, LEASE_OWNER, "Still failing", attempt, 3, due);
        }

        assertThat(recordRepository.findById(id)).get().satisfies(record -> {
            assertThat(record.status()).isEqualTo(NotificationStatus.FAILED);
            assertThat(record.retryCount()).isEqualTo(3);
        });
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
