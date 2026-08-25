package dev.ainer.module.notification;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.notification.notification.application.NotificationApplicationService;
import dev.ainer.module.notification.notification.application.NotificationAuthorities;
import dev.ainer.module.notification.notification.application.NotificationDeliveryEngine;
import dev.ainer.module.notification.notification.application.NotificationErrorCode;
import dev.ainer.module.notification.notification.application.NotificationRecordRepository;
import dev.ainer.module.notification.notification.domain.NotificationChannel;
import dev.ainer.module.notification.notification.domain.NotificationIntent;
import dev.ainer.module.notification.notification.domain.NotificationRecord;
import dev.ainer.module.notification.notification.domain.NotificationStatus;
import dev.ainer.security.principal.HumanSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.security.token.TokenProfile;
import jakarta.mail.internet.MimeMessage;
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
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 启用 SMTP sender 后：提交期拒绝非法地址，投递期发出 MimeMessage 且记 SENT。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = SmtpMailChannelSenderIntegrationTest.TestApplication.class,
        properties = {
                "ainer.notification.enabled=true",
                "ainer.notification.poll-interval-ms=999999",
                "ainer.notification.email.enabled=true",
                "ainer.notification.email.from=noreply@example.test",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class SmtpMailChannelSenderIntegrationTest {

    private static final IdentityAuthorityRef AUTHORITY =
            new IdentityAuthorityRef("https://auth.ainer.test");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_notification_email_test")
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
    NotificationDeliveryEngine deliveryEngine;
    @Autowired
    NotificationRecordRepository recordRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    RecordingJavaMailSender mailSender;

    private final AuthenticatedPrincipal manager = principal(
            NotificationAuthorities.READ, NotificationAuthorities.MANAGE,
            NotificationAuthorities.SUBMIT);

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM ainer_notification_audit");
        jdbcTemplate.execute("DELETE FROM ainer_notification_record");
        jdbcTemplate.execute("DELETE FROM ainer_notification_template");
        mailSender.sent().clear();
        mailSender.failNext(false);
    }

    @Test
    void submitRejectsInjectedRecipient() {
        assertThatThrownBy(() -> service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.EMAIL, "ops@example.test\r\nBcc: evil@example.test",
                "Alert", "body", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(NotificationErrorCode.INVALID_REQUEST));
    }

    @Test
    void deliverBatchSendsMimeMessageAndMarksSent() throws Exception {
        UUID id = service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.EMAIL, "ops@example.test", "Alert", "Workspace renamed", null));

        deliveryEngine.deliverBatch();

        Optional<NotificationRecord> record = recordRepository.findById(id);
        assertThat(record).isPresent();
        assertThat(record.get().status()).isEqualTo(NotificationStatus.SENT);
        assertThat(mailSender.sent()).hasSize(1);
        MimeMessage message = mailSender.sent().getFirst();
        assertThat(message.getAllRecipients()[0].toString()).contains("ops@example.test");
        assertThat(message.getSubject()).isEqualTo("Alert");
        assertThat(message.getContent().toString()).contains("Workspace renamed");
    }

    @Test
    void mailFailureIsRetriedWithoutLeakingAddress() {
        mailSender.failNext(true);
        UUID id = service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.EMAIL, "secret-ops@example.test", "Alert", "body", null));

        deliveryEngine.deliverBatch();

        Optional<NotificationRecord> record = recordRepository.findById(id);
        assertThat(record).isPresent();
        assertThat(record.get().status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(record.get().retryCount()).isEqualTo(1);
        assertThat(record.get().errorMessage()).isEqualTo("Email delivery failed");
        assertThat(record.get().errorMessage()).doesNotContain("secret-ops");
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
    @Import(NotificationModuleConfiguration.class)
    static class TestApplication {
    }

    @TestConfiguration
    static class Fixtures {

        @Bean
        AuthenticatedPrincipalResolver emailIntegrationPrincipalResolver() {
            return () -> principal(NotificationAuthorities.READ, NotificationAuthorities.MANAGE,
                    NotificationAuthorities.SUBMIT);
        }

        @Bean
        @Primary
        RecordingJavaMailSender recordingJavaMailSender() {
            return new RecordingJavaMailSender();
        }
    }

    static final class RecordingJavaMailSender extends JavaMailSenderImpl {

        private final List<MimeMessage> sent = new CopyOnWriteArrayList<>();
        private volatile boolean failNext;

        List<MimeMessage> sent() {
            return sent;
        }

        void failNext(boolean failNext) {
            this.failNext = failNext;
        }

        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
            if (failNext) {
                throw new MailException("smtp unavailable") {
                };
            }
            sent.add(mimeMessage);
        }

        @Override
        public void send(MimeMessage... mimeMessages) throws MailException {
            for (MimeMessage message : mimeMessages) {
                send(message);
            }
        }
    }
}
