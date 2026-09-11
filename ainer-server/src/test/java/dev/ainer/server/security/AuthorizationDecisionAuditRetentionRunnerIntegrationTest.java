package dev.ainer.server.security;

import dev.ainer.authorization.AuthorizationModuleConfiguration;
import dev.ainer.authorization.application.AuthorizationDecisionAudit;
import dev.ainer.authorization.application.AuthorizationDecisionAuditCursor;
import dev.ainer.authorization.application.AuthorizationDecisionAuditLifecycleRepository;
import dev.ainer.authorization.application.AuthorizationDecisionAuditLifecycleService;
import dev.ainer.authorization.application.AuthorizationDecisionAuditOperationalStatus;
import dev.ainer.authorization.application.AuthorizationDecisionAuditRepository;
import dev.ainer.authorization.domain.AuthorizationOutcome;
import dev.ainer.core.uuid.Uuidv7;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 决策审计保留任务（装配层）的行为与指标验证：真实 PostgreSQL 18.3 + 真实归档 SQL。
 *
 * <p>任务实例按测试自己的配置构造（保留期、批次上限、告警窗口各不相同的用例互不干扰），
 * 因此这里验证的是装配层真正拥有的语义：单周期批次上限、Micrometer 指标、最久未归档告警日志、
 * 失败计数与「失败不丢行」，以及启动期配置校验与 {@code enabled} 条件装配。
 *
 * <p>{@code @Scheduled} 的首次执行延迟由同包的
 * {@code AuthorizationDecisionAuditRetentionSchedulingContractTest} 以反射契约固化。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AuthorizationDecisionAuditRetentionRunnerIntegrationTest.TestApplication.class,
        properties = {
                "ainer.authorization.enabled=true",
                // 归档任务与 HTTP 鉴权无关：关掉 resource server，避免为 JwtDecoder 造测试替身。
                "ainer.security.resource-server.enabled=false",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class AuthorizationDecisionAuditRetentionRunnerIntegrationTest {

    private static final String HOT_TABLE = "ainer_authorization_decision_audit";
    private static final String ARCHIVE_TABLE = "ainer_authorization_decision_audit_archive";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_server_decision_audit_retention_test")
                    .withUsername("ainer")
                    .withPassword("ainer");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    AuthorizationDecisionAuditLifecycleService lifecycleService;

    @Autowired
    AuthorizationDecisionAuditRepository auditRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        jdbcTemplate.execute("DELETE FROM " + ARCHIVE_TABLE);
        jdbcTemplate.execute("DELETE FROM " + HOT_TABLE);
    }

    @Test
    void retentionCycleArchivesInBoundedBatchesAndPublishesMetrics() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        insertExpiredRows(250, now.minus(Duration.ofHours(2)), 1);
        insertExpiredRows(10, now.minus(Duration.ofMinutes(5)), 1);
        var runner = runnerWith(Duration.ofHours(1), Duration.ofDays(91), 100, 2);

        runner.runOnce();

        assertThat(archivedCounter()).as("单周期最多搬运 batch-size × max-batches-per-cycle 行").isEqualTo(200);
        assertThat(hotGauge()).isEqualTo(60);
        assertThat(archiveGauge()).isEqualTo(200);
        assertThat(oldestHotAgeGauge()).as("最久热行年龄应接近 2 小时").isGreaterThan(7000);

        runner.runOnce();

        assertThat(archivedCounter()).as("第二个周期把剩余到期行搬完（不足一批即结束）").isEqualTo(250);
        assertThat(hotGauge()).isEqualTo(10);
        assertThat(archiveGauge()).isEqualTo(250);
        assertThat(oldestHotAgeGauge()).as("到期行搬完后最旧热行年龄回到 5 分钟级").isLessThan(600);

        runner.runOnce();

        assertThat(archivedCounter()).as("无到期行时计数不再增长").isEqualTo(250);
        assertThat(hotGauge()).isEqualTo(10);
        assertThat(archiveGauge()).isEqualTo(250);
        assertThat(failedCounter()).as("正常周期不产生失败计数").isZero();
    }

    @Test
    void retentionCycleWarnsWhenOldestHotRowExceedsWarnWindow() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        insertExpiredRows(2, now.minus(Duration.ofHours(3)), 1);
        insertExpiredRows(1, now.minus(Duration.ofMinutes(1)), 1);
        // 单周期只搬一行：剩下的最旧热行仍然超过告警窗口，正是「归档跟不上写入」的信号。
        var runner = runnerWith(Duration.ofHours(1), Duration.ofMinutes(90), 1, 1);

        var warnings = captureLogs(runner);

        assertThat(archivedCounter()).isEqualTo(1);
        assertThat(hotGauge()).isEqualTo(2);
        assertThat(oldestHotAgeGauge())
                .as("最旧热行年龄应约为 3 小时")
                .isGreaterThan(Duration.ofMinutes(90).toSeconds());
        assertThat(warnings)
                .as("最旧热行超过告警窗口必须留下 WARN 日志")
                .anySatisfy(message -> assertThat(message).contains("oldest hot row"));
    }

    @Test
    void failedCycleIncrementsFailureCounterAndKeepsEveryHotRow() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        insertExpiredRows(3, now.minus(Duration.ofHours(2)), 1);
        var runner = runnerWith(Duration.ofHours(1), Duration.ofDays(91), 100, 2);

        jdbcTemplate.execute("ALTER TABLE " + ARCHIVE_TABLE + " RENAME TO " + ARCHIVE_TABLE + "_hidden");
        try {
            var errors = captureErrors(runner);
            assertThat(failedCounter()).as("归档失败必须计入失败计数").isEqualTo(1);
            assertThat(errors)
                    .as("归档失败必须留下 ERROR 日志（否则失败是静默的）")
                    .anySatisfy(message -> assertThat(message).contains("retention cycle failed"));
            assertThat(hotCount()).as("失败周期不得删除任何热行").isEqualTo(3);
        } finally {
            jdbcTemplate.execute(
                    "ALTER TABLE " + ARCHIVE_TABLE + "_hidden RENAME TO " + ARCHIVE_TABLE);
        }

        runner.runOnce();

        assertThat(failedCounter()).as("恢复后不再累计失败").isEqualTo(1);
        assertThat(archivedCounter()).as("恢复后积压照常搬完").isEqualTo(3);
        assertThat(hotCount()).isZero();
        assertThat(archiveCount()).isEqualTo(3);
    }

    @Test
    void startupValidationRejectsUnusableRetentionSettings() {
        var configuration = new AuthorizationDecisionAuditRetentionConfiguration();
        var clock = Clock.systemUTC();

        assertThatThrownBy(() -> configuration.authorizationDecisionAuditRetentionRunner(
                lifecycleService,
                new AuthorizationDecisionAuditRetentionProperties(
                        true, Duration.ofDays(90), Duration.ofMinutes(5), Duration.ofMinutes(5),
                        Duration.ofDays(90), 500, 20),
                clock, meterRegistry))
                .as("告警窗口必须严格长于热保留期，否则告警恒亮")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("oldest-hot-warn-window");

        assertThatThrownBy(() -> configuration.authorizationDecisionAuditRetentionRunner(
                lifecycleService,
                new AuthorizationDecisionAuditRetentionProperties(
                        true, Duration.ofDays(90), Duration.ofMinutes(5), Duration.ofMinutes(5),
                        Duration.ofDays(91), 0, 20),
                clock, meterRegistry))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> configuration.authorizationDecisionAuditRetentionRunner(
                lifecycleService,
                new AuthorizationDecisionAuditRetentionProperties(
                        true, Duration.ofDays(90), Duration.ofMinutes(5), Duration.ofMinutes(5),
                        Duration.ofDays(91), 500, 0),
                clock, meterRegistry))
                .isInstanceOf(IllegalStateException.class);

        assertThat(configuration.authorizationDecisionAuditRetentionRunner(
                lifecycleService,
                new AuthorizationDecisionAuditRetentionProperties(
                        true, Duration.ofDays(90), Duration.ofMinutes(5), Duration.ofMinutes(5),
                        Duration.ofDays(91), 500, 20),
                clock, meterRegistry))
                .as("合法配置应能装配")
                .isNotNull();
    }

    @Test
    void runnerBeanIsRegisteredOnlyWhenRetentionIsExplicitlyEnabled() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(AuthorizationDecisionAuditRetentionConfiguration.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(AuthorizationDecisionAuditLifecycleService.class,
                        () -> new AuthorizationDecisionAuditLifecycleService(
                                new NoOpLifecycleRepository(), Clock.systemUTC()));

        contextRunner
                .withPropertyValues("ainer.authorization.decision-audit-retention.enabled=false")
                .run(context -> assertThat(context)
                        .as("默认关闭时不得注册归档任务")
                        .doesNotHaveBean(AuthorizationDecisionAuditRetentionRunner.class));

        contextRunner
                .withPropertyValues("ainer.authorization.decision-audit-retention.enabled=true")
                .run(context -> assertThat(context)
                        .as("显式开启时注册归档任务")
                        .hasSingleBean(AuthorizationDecisionAuditRetentionRunner.class));
    }

    // ---------------------------------------------------------------- 辅助方法

    private AuthorizationDecisionAuditRetentionRunner runnerWith(
            Duration hotRetention, Duration oldestHotWarnWindow, int batchSize, int maxBatchesPerCycle) {
        return new AuthorizationDecisionAuditRetentionRunner(
                lifecycleService,
                new AuthorizationDecisionAuditRetentionProperties(
                        true, hotRetention, Duration.ofMinutes(5), Duration.ofMinutes(5),
                        oldestHotWarnWindow, batchSize, maxBatchesPerCycle),
                Clock.systemUTC(),
                meterRegistry);
    }

    private List<String> captureLogs(AuthorizationDecisionAuditRetentionRunner runner) {
        return capture(runner, ch.qos.logback.classic.Level.WARN);
    }

    private List<String> captureErrors(AuthorizationDecisionAuditRetentionRunner runner) {
        return capture(runner, ch.qos.logback.classic.Level.ERROR);
    }

    private List<String> capture(AuthorizationDecisionAuditRetentionRunner runner, ch.qos.logback.classic.Level level) {
        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                AuthorizationDecisionAuditRetentionRunner.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            runner.runOnce();
            return appender.list.stream()
                    .filter(event -> event.getLevel() == level)
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .toList();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private double archivedCounter() {
        return meterRegistry.get("ainer.authorization.decision.audit.archived").counter().count();
    }

    private double failedCounter() {
        return meterRegistry.get("ainer.authorization.decision.audit.archive.failed").counter().count();
    }

    private double hotGauge() {
        return meterRegistry.get("ainer.authorization.decision.audit.hot").gauge().value();
    }

    private double archiveGauge() {
        return meterRegistry.get("ainer.authorization.decision.audit.archive.current").gauge().value();
    }

    private double oldestHotAgeGauge() {
        return meterRegistry.get("ainer.authorization.decision.audit.oldest.hot.age.seconds").gauge().value();
    }

    private void insertExpiredRows(int count, Instant from, int secondsBetween) {
        for (int index = 0; index < count; index++) {
            auditRepository.insert(new AuthorizationDecisionAudit(
                    Uuidv7.generate(),
                    null,
                    "https://auth.ainer.test",
                    "USER",
                    "account:retention-runner-test",
                    "test.resource.read",
                    "workspace",
                    null,
                    AuthorizationOutcome.ALLOW,
                    "AINER.AUTHORIZATION.ALLOWED",
                    "retention-runner-test-policy",
                    "req-retention-runner",
                    "trace-retention-runner",
                    from.minusSeconds((long) index * secondsBetween)));
        }
    }

    private long hotCount() {
        return countOf(HOT_TABLE);
    }

    private long archiveCount() {
        return countOf(ARCHIVE_TABLE);
    }

    private long countOf(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? -1 : count;
    }

    /** 条件装配测试用的空实现（不是 Mock 框架，只是把端口实现成无副作用）。 */
    private static final class NoOpLifecycleRepository implements AuthorizationDecisionAuditLifecycleRepository {

        @Override
        public int archiveBefore(Instant cutoff, Instant archivedAt, int batchSize) {
            return 0;
        }

        @Override
        public List<AuthorizationDecisionAudit> findPage(
                UUID workspaceId, AuthorizationDecisionAuditCursor cursor, int limit) {
            return List.of();
        }

        @Override
        public AuthorizationDecisionAuditOperationalStatus operationalStatus() {
            return new AuthorizationDecisionAuditOperationalStatus(0, 0, null);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(AuthorizationModuleConfiguration.class)
    static class TestApplication {

        /**
         * 授权模块的管理 API 需要一个主体解析器；本测试不经过 HTTP 鉴权（resource server 关闭），
         * 只在容器装配阶段满足依赖，被调用即失败。
         */
        @org.springframework.context.annotation.Bean
        dev.ainer.security.token.AuthenticatedPrincipalResolver testPrincipalResolver() {
            return () -> {
                throw new UnsupportedOperationException(
                        "决策审计归档测试不经过 HTTP 主体解析");
            };
        }
    }
}
