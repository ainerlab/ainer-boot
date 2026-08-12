package dev.ainer.module.notification;

import dev.ainer.module.notification.notification.application.NotificationApplicationService;
import dev.ainer.module.notification.notification.application.NotificationRecordRepository;
import dev.ainer.module.notification.notification.domain.NotificationChannel;
import dev.ainer.module.notification.notification.domain.NotificationIntent;
import dev.ainer.module.notification.notification.domain.NotificationRecord;
import dev.ainer.module.notification.notification.domain.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the notification module (ADR-0038). Validates PG 18 JSONB templates,
 * SKIP LOCKED queue claiming, template rendering via switch pattern matching, and retry scheduling.
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

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM ainer_notification_record");
        jdbcTemplate.execute("DELETE FROM ainer_notification_template");
    }

    @Test
    void createTemplateAndSubmitWithRendering() {
        service.createTemplate("welcome_email", NotificationChannel.EMAIL,
                "Welcome, {name}!", "Hello {name}, your account is ready.",
                Map.of("name", "string"));

        // JDK 25 sealed interface record: TemplateIntent
        java.util.UUID recordId = service.submit(new NotificationIntent.TemplateIntent(
                NotificationChannel.EMAIL, "user@test.com", "welcome_email",
                Map.of("name", "Alice")));

        Optional<NotificationRecord> record = recordRepository.findById(recordId);
        assertThat(record).isPresent();
        assertThat(record.get().title()).isEqualTo("Welcome, Alice!");
        assertThat(record.get().body()).isEqualTo("Hello Alice, your account is ready.");
        assertThat(record.get().status()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void submitDirectIntentWithoutTemplate() {
        java.util.UUID recordId = service.submit(new NotificationIntent.DirectIntent(
                NotificationChannel.SMS, "+8613800138000", "OTP", "Your code is 123456", null));

        Optional<NotificationRecord> record = recordRepository.findById(recordId);
        assertThat(record).isPresent();
        assertThat(record.get().title()).isEqualTo("OTP");
        assertThat(record.get().body()).isEqualTo("Your code is 123456");
    }

    @Test
    void templateChannelMismatchRejected() {
        service.createTemplate("sms_code", NotificationChannel.SMS, "Code", "Your code: {code}",
                Map.of("code", "string"));

        assertThatThrownBy(() -> service.submit(new NotificationIntent.TemplateIntent(
                NotificationChannel.EMAIL, "user@test.com", "sms_code", Map.of("code", "1234"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateTemplateCodeRejected() {
        service.createTemplate("dup", NotificationChannel.EMAIL, "T", "B", Map.of());
        assertThatThrownBy(() -> service.createTemplate("dup", NotificationChannel.EMAIL,
                "T2", "B2", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void skipLockedClaimingFlipsStatusToSending() {
        // 提交 3 条通知
        for (int i = 0; i < 3; i++) {
            service.submit(new NotificationIntent.DirectIntent(
                    NotificationChannel.SMS, "recip" + i, "T" + i, "B" + i, null));
        }

        // SKIP LOCKED 领取：状态翻转为 SENDING
        List<NotificationRecord> claimed = recordRepository.claimPending(10);
        assertThat(claimed).hasSize(3);
        assertThat(claimed).allSatisfy(r ->
                assertThat(r.status()).isEqualTo(NotificationStatus.SENDING));
    }

    @Test
    void markSentUpdatesStatusAndTimestamp() {
        java.util.UUID id = service.submit(new NotificationIntent.DirectIntent(
                NotificationChannel.WEBHOOK, "https://hook.test", "Title", "Body", null));
        recordRepository.claimPending(10); // 先领取
        recordRepository.markSent(id, java.time.Instant.now());

        Optional<NotificationRecord> record = recordRepository.findById(id);
        assertThat(record).isPresent();
        assertThat(record.get().status()).isEqualTo(NotificationStatus.SENT);
        assertThat(record.get().sentAt()).isNotNull();
    }

    @Test
    void markFailedWithRetrySchedulesNextRetryAndIncrementsCount() {
        java.util.UUID id = service.submit(new NotificationIntent.DirectIntent(
                NotificationChannel.SMS, "recip", "T", "B", null));
        recordRepository.claimPending(10);

        // 第一次失败：retry_count 0→1，状态回 PENDING（未超 max_retries=3）
        java.time.Instant nextRetry = java.time.Instant.now().plusSeconds(2);
        recordRepository.markFailed(id, "Connection refused", 0, 3, nextRetry);

        Optional<NotificationRecord> afterFirstFail = recordRepository.findById(id);
        assertThat(afterFirstFail).isPresent();
        assertThat(afterFirstFail.get().status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(afterFirstFail.get().retryCount()).isEqualTo(1);
        assertThat(afterFirstFail.get().errorMessage()).contains("Connection refused");
        assertThat(afterFirstFail.get().nextRetryAt()).isNotNull();

        // 第四次失败：retry_count 3→4，超过 max_retries=3 → 状态 FAILED
        recordRepository.markFailed(id, "Still failing", 3, 3, nextRetry);
        Optional<NotificationRecord> afterMaxFail = recordRepository.findById(id);
        assertThat(afterMaxFail).isPresent();
        assertThat(afterMaxFail.get().status()).isEqualTo(NotificationStatus.FAILED);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({NotificationModuleConfiguration.class})
    static class TestApplication {
    }
}
