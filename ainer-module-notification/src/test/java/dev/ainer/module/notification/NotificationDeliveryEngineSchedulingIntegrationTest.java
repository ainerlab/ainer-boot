package dev.ainer.module.notification;

import com.sun.net.httpserver.HttpServer;
import dev.ainer.module.notification.notification.application.NotificationApplicationService;
import dev.ainer.module.notification.notification.application.NotificationAuthorities;
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
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通知投递引擎的<b>引擎级集成测试</b>（docs/conventions.md §11：后台线程/调度器/队列消费者必须
 * 有真实 PostgreSQL + 端到端生命周期验证）。
 *
 * <p>与模块内其他测试的关键区别：这里<b>不做任何手动触发</b>——不调用
 * {@code deliveryEngine.deliverBatch()}，只提交通知，然后等待由 {@code @Scheduled} 驱动的
 * 调度器自己完成「领取 → 投递 → 回写终态」。2026-09-11 的缺陷正是：全仓 {@code @EnableScheduling}
 * 只挂在一个默认关闭的业务开关上，默认部署下调度器根本不注册，API 返回 201、审计有记录，
 * 记录却永远停在 PENDING，而所有既有测试都手动直调 {@code deliverBatch()} 所以全绿。
 *
 * <p>覆盖三个引擎级行为：
 * <ol>
 *   <li>调度驱动的投递真的发生（本地 {@code HttpServer} 桩收到请求，记录进入 SENT）；</li>
 *   <li>发送慢于 {@code poll-interval-ms} 时不会因为 {@code SENDING} 被重复领取而重复投递
 *       （租约）；</li>
 *   <li>发送超过 {@code send-timeout} 时按失败处理并继续走重试，调度线程不被卡死。</li>
 * </ol>
 * 所有等待都有超时上限，不会挂死构建。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = NotificationDeliveryEngineSchedulingIntegrationTest.TestApplication.class,
        properties = {
                "ainer.notification.enabled=true",
                // 真实调度：250ms 轮询一次，测试不手动触发投递
                "ainer.notification.poll-interval-ms=250",
                "ainer.notification.delivery.send-timeout=5s",
                "ainer.notification.delivery.lease-duration=20s",
                "ainer.notification.webhook.enabled=true",
                "ainer.notification.webhook.allowed-hosts=127.0.0.1",
                "ainer.notification.webhook.allow-insecure-http=true",
                // 读超时放宽到 60s，确保「超时」来自引擎的 send-timeout 而不是 HTTP 客户端
                "ainer.notification.webhook.read-timeout=60s",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class NotificationDeliveryEngineSchedulingIntegrationTest {

    private static final IdentityAuthorityRef AUTHORITY =
            new IdentityAuthorityRef("https://auth.ainer.test");

    /** 等待记录进入终态的硬上限，避免测试挂死。 */
    private static final Duration AWAIT_TERMINAL = Duration.ofSeconds(30);

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_notification_delivery_test")
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

    private HttpServer server;
    private ExecutorService serverExecutor;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger statusToReturn = new AtomicInteger(204);
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicInteger responseDelayMillis = new AtomicInteger();
    private final CountDownLatch releaseFirstResponse = new CountDownLatch(1);
    private volatile boolean blockUntilReleased;

    @BeforeEach
    void startReceiver() throws IOException {
        jdbcTemplate.execute("DELETE FROM ainer_notification_audit");
        jdbcTemplate.execute("DELETE FROM ainer_notification_record");
        jdbcTemplate.execute("DELETE FROM ainer_notification_template");
        hits.set(0);
        statusToReturn.set(204);
        receivedBody.set(null);
        responseDelayMillis.set(0);
        blockUntilReleased = false;
        serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(serverExecutor);
        server.createContext("/hook", exchange -> {
            hits.incrementAndGet();
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            awaitReleaseIfBlocked();
            sleepQuietly(responseDelayMillis.get());
            exchange.sendResponseHeaders(statusToReturn.get(), -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopReceiver() {
        releaseFirstResponse.countDown();
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    /**
     * 提交后不做任何手动触发：调度器必须自己把记录推进到 SENT，并且 HTTP 桩真的收到投递。
     */
    @Test
    void scheduledPollingDeliversWithoutManualTrigger() {
        UUID id = service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.WEBHOOK, hookUrl(), "Alert", "Workspace renamed", null));

        awaitStatus(id, NotificationStatus.SENT);

        assertThat(hits.get()).isEqualTo(1);
        assertThat(receivedBody.get()).contains("Alert").contains("Workspace renamed");
    }

    /**
     * 发送（1.5s）远慢于轮询间隔（250ms）：租约必须挡住重复领取，桩只能收到一次投递。
     * 修复前 {@code status IN ('PENDING','SENDING')} 会让同一行被下一轮再次领取并重复发送。
     */
    @Test
    void slowSendIsDeliveredOnceWhileLeaseHeld() {
        responseDelayMillis.set(1500);
        UUID id = service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.WEBHOOK, hookUrl(), "Slow", "slow body", null));

        awaitStatus(id, NotificationStatus.SENT);

        assertThat(hits.get()).isEqualTo(1);
    }

    /**
     * 发送卡死超过 send-timeout：按失败处理（进入重试），调度线程不被挂住——放行桩之后，
     * 下一轮轮询仍能把记录推进到终态，而不是永久停在 SENDING。
     */
    @Test
    void stuckSendTimesOutThenRetryStillReachesTerminalState() {
        blockUntilReleased = true;
        UUID id = service.submit(manager, null, new NotificationIntent.DirectIntent(
                NotificationChannel.WEBHOOK, hookUrl(), "Stuck", "stuck body", null));

        // 超时按失败处理：错误信息明确、记录回到可重试状态
        awaitCondition(() -> {
            Optional<NotificationRecord> record = recordRepository.findById(id);
            return record.isPresent()
                    && record.get().status() == NotificationStatus.PENDING
                    && record.get().retryCount() == 1
                    && record.get().errorMessage() != null
                    && record.get().errorMessage().contains("timed out");
        }, AWAIT_TERMINAL, "记录未在 send-timeout 后按失败进入重试");

        // 调度线程没有被卡死的发送挂住：放行桩后重试继续推进到终态
        blockUntilReleased = false;
        releaseFirstResponse.countDown();
        awaitStatus(id, NotificationStatus.SENT);
    }

    private String hookUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    private void awaitStatus(UUID id, NotificationStatus expected) {
        awaitCondition(() -> recordRepository.findById(id)
                        .map(record -> record.status() == expected)
                        .orElse(false),
                AWAIT_TERMINAL, "记录 " + id + " 未在超时前进入 " + expected + "：" + describe(id));
    }

    private void awaitCondition(BooleanSupplier condition, Duration timeout, String failureMessage) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleepQuietly(100);
        }
        throw new AssertionError(failureMessage);
    }

    private String describe(UUID id) {
        return recordRepository.findById(id)
                .map(record -> "status=" + record.status()
                        + ", retryCount=" + record.retryCount()
                        + ", errorMessage=" + record.errorMessage())
                .orElse("record missing");
    }

    private void awaitReleaseIfBlocked() {
        if (!blockUntilReleased) {
            return;
        }
        try {
            releaseFirstResponse.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
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

    /** Satisfies the controller's resolver dependency without enabling the resource-server chain. */
    @TestConfiguration
    static class PrincipalFixture {

        @Bean
        AuthenticatedPrincipalResolver notificationSchedulingPrincipalResolver() {
            return () -> principal(NotificationAuthorities.READ, NotificationAuthorities.MANAGE,
                    NotificationAuthorities.SUBMIT);
        }
    }
}
