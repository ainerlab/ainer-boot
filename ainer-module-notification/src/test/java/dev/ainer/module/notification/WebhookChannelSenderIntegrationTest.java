package dev.ainer.module.notification;

import com.sun.net.httpserver.HttpServer;
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
import org.junit.jupiter.api.AfterEach;
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 启用真实 webhook sender 后：提交期拒绝非法 URL，投递期 POST JSON 且 2xx 记 SENT。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = WebhookChannelSenderIntegrationTest.TestApplication.class,
        properties = {
                "ainer.notification.enabled=true",
                "ainer.notification.poll-interval-ms=999999",
                "ainer.notification.webhook.enabled=true",
                "ainer.notification.webhook.allowed-hosts=127.0.0.1",
                "ainer.notification.webhook.allow-insecure-http=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class WebhookChannelSenderIntegrationTest {

    private static final IdentityAuthorityRef AUTHORITY =
            new IdentityAuthorityRef("https://auth.ainer.test");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_notification_webhook_test")
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

    private final AuthenticatedPrincipal manager = principal(
            NotificationAuthorities.READ, NotificationAuthorities.MANAGE,
            NotificationAuthorities.SUBMIT);

    private HttpServer server;
    private final AtomicInteger statusToReturn = new AtomicInteger(204);
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void startReceiver() throws IOException {
        jdbcTemplate.execute("DELETE FROM ainer_notification_audit");
        jdbcTemplate.execute("DELETE FROM ainer_notification_record");
        jdbcTemplate.execute("DELETE FROM ainer_notification_template");
        receivedBody.set(null);
        hits.set(0);
        statusToReturn.set(204);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            hits.incrementAndGet();
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(statusToReturn.get(), -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopReceiver() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void submitRejectsDestinationOutsideAllowlist() {
        assertThatThrownBy(() -> service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.WEBHOOK, "https://8.8.8.8/hooks", "T", "B", null)))
                .isInstanceOfSatisfying(dev.ainer.core.error.BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(NotificationErrorCode.INVALID_REQUEST));
    }

    @Test
    void deliverBatchPostsJsonAndMarksSent() {
        String recipient = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
        UUID id = service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.WEBHOOK, recipient, "Alert", "Workspace renamed", null));

        deliveryEngine.deliverBatch();

        Optional<NotificationRecord> record = recordRepository.findById(id);
        assertThat(record).isPresent();
        assertThat(record.get().status()).isEqualTo(NotificationStatus.SENT);
        assertThat(hits.get()).isEqualTo(1);
        assertThat(receivedBody.get()).isEqualTo("{\"title\":\"Alert\",\"body\":\"Workspace renamed\"}");
        assertThat(record.get().errorMessage()).isNull();
    }

    @Test
    void nonSuccessStatusIsRetriedWithoutLeakingUrl() {
        statusToReturn.set(503);
        String recipient = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook?token=secret-value";
        UUID id = service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.WEBHOOK, recipient, "Alert", "body", null));

        deliveryEngine.deliverBatch();

        Optional<NotificationRecord> record = recordRepository.findById(id);
        assertThat(record).isPresent();
        assertThat(record.get().status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(record.get().retryCount()).isEqualTo(1);
        assertThat(record.get().errorMessage()).isEqualTo("Webhook delivery failed: HTTP 503");
        assertThat(record.get().errorMessage()).doesNotContain("secret-value");
        assertThat(record.get().errorMessage()).doesNotContain(recipient);
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
    static class PrincipalFixture {

        @Bean
        AuthenticatedPrincipalResolver webhookIntegrationPrincipalResolver() {
            return () -> principal(NotificationAuthorities.READ, NotificationAuthorities.MANAGE,
                    NotificationAuthorities.SUBMIT);
        }
    }
}
