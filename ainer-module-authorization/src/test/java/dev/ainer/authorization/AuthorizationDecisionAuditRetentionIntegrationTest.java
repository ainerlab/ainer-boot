package dev.ainer.authorization;

import dev.ainer.authorization.application.AuthorizationDecisionAudit;
import dev.ainer.authorization.application.AuthorizationDecisionAuditCursor;
import dev.ainer.authorization.application.AuthorizationDecisionAuditLifecycleService;
import dev.ainer.authorization.application.AuthorizationDecisionAuditOperationalStatus;
import dev.ainer.authorization.application.AuthorizationDecisionAuditPage;
import dev.ainer.authorization.application.AuthorizationDecisionAuditRepository;
import dev.ainer.authorization.domain.AuthorizationOutcome;
import dev.ainer.core.uuid.Uuidv7;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 决策审计保留策略的真实 PostgreSQL 18.3 验证（ADR-0037 §12.4）。
 *
 * <p>覆盖四类不变量：
 * <ol>
 *   <li><strong>不丢数据</strong>：归档把热行整行搬到冷表，热+冷总行数与每一行的列值都不变；
 *       归档表写入失败时热行一条都不删（先归档后删除）；重复执行幂等。</li>
 *   <li><strong>并发安全</strong>：两个归档实例真并发跑同一区间时不重复、不丢行；另一实例
 *       持有的行锁会被 {@code FOR UPDATE SKIP LOCKED} 跳过而不是阻塞等待。</li>
 *   <li><strong>保留期边界</strong>：未到期的行不归档，到期（严格早于 cutoff）的行归档。</li>
 *   <li><strong>读路径一致性</strong>：热+冷并集的稳定游标分页在归档前后返回同一条序列，
 *       扫描中途发生归档也不会出现空洞或重复。</li>
 * </ol>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AuthorizationDecisionAuditRetentionIntegrationTest.TestApplication.class,
        properties = {
                "ainer.authorization.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class AuthorizationDecisionAuditRetentionIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_authorization_retention_test")
                    .withUsername("ainer")
                    .withPassword("ainer");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** 热表与归档表共享的 16 列（归档表只多一列 archived_at），用于逐列比对搬运结果。 */
    private static final String SHARED_COLUMNS = """
            decision_id, workspace_id, requester_issuer, requester_type, requester_id,
            permission_code, resource_type, resource_id, outcome, reason_code,
            policy_version, request_id, trace_id, evaluated_at, agent_id, acting_grant_id
            """;

    private static final String HOT_TABLE = "ainer_authorization_decision_audit";
    private static final String ARCHIVE_TABLE = "ainer_authorization_decision_audit_archive";

    @Autowired
    AuthorizationDecisionAuditRepository auditRepository;

    @Autowired
    AuthorizationDecisionAuditLifecycleService lifecycleService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanAuditTables() {
        jdbcTemplate.execute("DELETE FROM " + ARCHIVE_TABLE);
        jdbcTemplate.execute("DELETE FROM " + HOT_TABLE);
    }

    // ---------------------------------------------------------------- 不丢数据

    @Test
    void archiveMovesExpiredRowsToArchiveTableWithoutLosingAnyRow() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant cutoff = base.minus(Duration.ofHours(1));
        List<UUID> expired = insertExpiredRows(60, cutoff, 1);
        List<UUID> fresh = insertRowsInWindow(60, cutoff, base);

        Map<UUID, Map<String, Object>> hotBefore = snapshots(HOT_TABLE, expired);

        int archived = lifecycleService.archiveBefore(cutoff, 100);

        assertThat(archived).as("到期热行应被搬走").isEqualTo(60);
        assertThat(hotCount()).as("热表只剩未到期行").isEqualTo(60);
        assertThat(archiveCount()).as("归档表行数等于搬走的行数").isEqualTo(60);
        assertThat(hotCount() + archiveCount())
                .as("归档前后热+冷总行数必须不变（不丢数据）")
                .isEqualTo(120);

        assertThat(idsIn(HOT_TABLE))
                .as("热表保留的正是未到期行")
                .containsExactlyInAnyOrderElementsOf(fresh);
        assertThat(idsIn(ARCHIVE_TABLE))
                .as("归档表持有的正是到期行")
                .containsExactlyInAnyOrderElementsOf(expired);
        for (UUID id : expired) {
            assertThat(rowSnapshot(ARCHIVE_TABLE, id))
                    .as("归档行 %s 的每一列都必须与热行一致", id)
                    .isEqualTo(hotBefore.get(id));
        }
    }

    @Test
    void repeatedArchiveIsIdempotent() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant cutoff = base.minus(Duration.ofHours(1));
        insertExpiredRows(30, cutoff, 1);

        assertThat(lifecycleService.archiveBefore(cutoff, 10)).isEqualTo(10);
        assertThat(lifecycleService.archiveBefore(cutoff, 10)).isEqualTo(10);
        assertThat(lifecycleService.archiveBefore(cutoff, 10)).isEqualTo(10);
        assertThat(lifecycleService.archiveBefore(cutoff, 10)).as("已无到期行时返回 0").isZero();
        assertThat(lifecycleService.archiveBefore(base, 10)).as("放宽 cutoff 也无可归档行").isZero();

        assertThat(hotCount()).isZero();
        assertThat(archiveCount()).isEqualTo(30);
        assertThat(distinctArchiveIds()).as("归档表内 decision_id 不得重复").isEqualTo(30);
    }

    @Test
    void failedArchiveInsertKeepsEveryHotRow() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant cutoff = base.minus(Duration.ofHours(1));
        insertExpiredRows(2, cutoff, 1);
        insertAudit(Uuidv7.generate(), null, cutoff.minusSeconds(30), "test.archive.fail");

        jdbcTemplate.execute("""
                CREATE FUNCTION ainer_test_fail_archive() RETURNS trigger AS $$
                BEGIN
                  IF NEW.permission_code = 'test.archive.fail' THEN
                    RAISE EXCEPTION 'forced archive failure for test';
                  END IF;
                  RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_ainer_test_fail_archive
                BEFORE INSERT ON ainer_authorization_decision_audit_archive
                FOR EACH ROW EXECUTE FUNCTION ainer_test_fail_archive()
                """);
        try {
            assertThatThrownBy(() -> lifecycleService.archiveBefore(cutoff, 100))
                    .as("归档表写入失败必须让整批失败")
                    .isInstanceOf(DataAccessException.class);
            assertThat(hotCount())
                    .as("归档行没有落库时，热行一条都不能删（先归档后删除）")
                    .isEqualTo(3);
            assertThat(archiveCount()).as("失败批次不留半截归档").isZero();
        } finally {
            jdbcTemplate.execute(
                    "DROP TRIGGER IF EXISTS trg_ainer_test_fail_archive ON " + ARCHIVE_TABLE);
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS ainer_test_fail_archive()");
        }

        assertThat(lifecycleService.archiveBefore(cutoff, 100))
                .as("故障排除后同一个 cutoff 仍能完整归档，没有行被卡死")
                .isEqualTo(3);
        assertThat(hotCount()).isZero();
        assertThat(archiveCount()).isEqualTo(3);
    }

    // ---------------------------------------------------------------- 并发安全

    @Test
    void concurrentArchiveInstancesNeitherDuplicateNorLoseRows() throws Exception {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant cutoff = base.minus(Duration.ofHours(1));
        List<UUID> expired = insertExpiredRows(400, cutoff, 1);
        int batchSize = 25;

        CyclicBarrier startTogether = new CyclicBarrier(2);
        AtomicInteger firstInstance = new AtomicInteger(-1);
        AtomicInteger secondInstance = new AtomicInteger(-1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread instanceA = Thread.ofVirtual().name("archive-instance-a").start(
                () -> runDrain(startTogether, cutoff, batchSize, firstInstance, failure));
        Thread instanceB = Thread.ofVirtual().name("archive-instance-b").start(
                () -> runDrain(startTogether, cutoff, batchSize, secondInstance, failure));

        awaitTermination(instanceA);
        awaitTermination(instanceB);
        assertThat(failure.get()).as("并发归档实例不得抛异常").isNull();

        assertThat(firstInstance.get())
                .as("第一个实例必须真的搬走了一部分行（否则不是并发归档）")
                .isPositive();
        assertThat(secondInstance.get())
                .as("第二个实例必须真的搬走了一部分行（否则不是并发归档）")
                .isPositive();
        assertThat(firstInstance.get() + secondInstance.get())
                .as("两个实例搬走的行数之和必须等于到期行总数")
                .isEqualTo(400);
        assertThat(hotCount()).as("到期行必须全部离开热表").isZero();
        assertThat(archiveCount()).as("归档行数必须恰好等于到期行总数（不重复）").isEqualTo(400);
        assertThat(distinctArchiveIds()).as("归档表内 decision_id 不得重复").isEqualTo(400);
        assertThat(idsIn(ARCHIVE_TABLE))
                .as("归档内容必须与原始到期行一一对应（不丢行、不错行）")
                .containsExactlyInAnyOrderElementsOf(expired);
        assertThat(intersectionCount())
                .as("同一 decision_id 不得同时留在热表与归档表")
                .isZero();
        assertThat(lifecycleService.archiveBefore(cutoff, batchSize))
                .as("并发跑完之后再跑一次仍幂等")
                .isZero();
    }

    @Test
    void archiveSkipsRowsLockedByAnotherInstanceInsteadOfBlocking() throws Exception {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant cutoff = base.minus(Duration.ofHours(1));
        insertExpiredRows(100, cutoff, 1);

        CountDownLatch locksHeld = new CountDownLatch(1);
        CountDownLatch releaseLocks = new CountDownLatch(1);
        CountDownLatch archiveReturned = new CountDownLatch(1);
        AtomicReference<Throwable> holderFailure = new AtomicReference<>();
        AtomicInteger archivedRows = new AtomicInteger(-1);
        List<UUID> lockedIds = new ArrayList<>();

        Thread lockHolder = Thread.ofVirtual().name("archive-lock-holder").start(() -> {
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    lockedIds.addAll(jdbcTemplate.queryForList(
                            "SELECT decision_id FROM " + HOT_TABLE
                                    + " WHERE evaluated_at < ? ORDER BY evaluated_at, decision_id "
                                    + "LIMIT 50 FOR UPDATE",
                            UUID.class, OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC)));
                    locksHeld.countDown();
                    awaitQuietly(releaseLocks);
                });
            } catch (Throwable throwable) {
                holderFailure.set(throwable);
                locksHeld.countDown();
            }
        });

        assertThat(locksHeld.await(30, TimeUnit.SECONDS)).as("竞争实例必须已经持锁").isTrue();
        assertThat(holderFailure.get()).as("竞争实例持锁失败").isNull();
        assertThat(lockedIds).as("竞争实例锁住了 50 行").hasSize(50);

        Thread archiver = Thread.ofVirtual().name("archive-instance").start(() -> {
            try {
                archivedRows.set(lifecycleService.archiveBefore(cutoff, 100));
            } catch (Throwable throwable) {
                holderFailure.compareAndSet(null, throwable);
            } finally {
                archiveReturned.countDown();
            }
        });

        // 没有 SKIP LOCKED 的实现会在这里一直等竞争实例释放行锁，直到下面的等待超时。
        boolean finishedWhileRowsWereLocked = archiveReturned.await(20, TimeUnit.SECONDS);
        releaseLocks.countDown();
        assertThat(finishedWhileRowsWereLocked)
                .as("归档必须在行仍被其它实例锁住时就返回（FOR UPDATE SKIP LOCKED，不是阻塞等待）")
                .isTrue();
        awaitTermination(archiver);
        awaitTermination(lockHolder);
        assertThat(holderFailure.get()).as("竞争实例与归档实例都不得抛异常").isNull();

        assertThat(archivedRows.get())
                .as("只搬走未被其它实例锁住的 50 行，被锁住的 50 行跳过而不是阻塞或重复搬运")
                .isEqualTo(50);
        assertThat(hotCount()).as("被跳过的行留在热表等待下一个周期").isEqualTo(50);
        assertThat(idsIn(HOT_TABLE)).containsExactlyInAnyOrderElementsOf(lockedIds);
        assertThat(lifecycleService.archiveBefore(cutoff, 100))
                .as("锁释放后剩余行照常归档")
                .isEqualTo(50);
        assertThat(hotCount()).isZero();
        assertThat(archiveCount()).isEqualTo(100);
    }

    // ---------------------------------------------------------------- 保留期边界

    @Test
    void retentionCutoffArchivesOnlyRowsStrictlyOlderThanTheWindow() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant cutoff = base.minus(Duration.ofHours(1));

        insertExpiredRows(10, cutoff, 1);
        UUID exactlyAtCutoff = insertAudit(Uuidv7.generate(), null, cutoff, "test.resource.read");
        UUID justInsideWindow = insertAudit(
                Uuidv7.generate(), null, cutoff.plusMillis(1), "test.resource.read");
        UUID fresh = insertAudit(Uuidv7.generate(), null, base.minusSeconds(5), "test.resource.read");

        assertThat(lifecycleService.archiveBefore(cutoff, 100)).isEqualTo(10);
        assertThat(idsIn(HOT_TABLE))
                .as("边界时刻及之后的行仍在热表")
                .containsExactlyInAnyOrder(exactlyAtCutoff, justInsideWindow, fresh);
        assertThat(archiveCount()).isEqualTo(10);

        assertThat(lifecycleService.archiveBefore(cutoff.plusMillis(1), 100))
                .as("边界被跨过之后该行才归档")
                .isEqualTo(1);
        assertThat(idsIn(HOT_TABLE)).containsExactlyInAnyOrder(justInsideWindow, fresh);
    }

    // ---------------------------------------------------------------- 读路径一致性

    @Test
    void historyReadsHotAndArchiveUnionWithStableCursor() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant cutoff = base.minus(Duration.ofHours(1));
        UUID workspaceId = UUID.randomUUID();
        UUID otherWorkspaceId = UUID.randomUUID();

        List<UUID> expired = insertWorkspaceWindow(
                workspaceId, 12, cutoff.minusSeconds(720), cutoff.minusSeconds(60));
        List<UUID> fresh = insertWorkspaceWindow(
                workspaceId, 8, cutoff.plusSeconds(60), base);
        // 另一个 workspace：1 行到期 + 4 行未到期。归档按决策时间全局执行（不按 workspace），
        // 读路径才按 workspace 过滤——这条数据用于同时证明这两点。
        List<UUID> otherExpired = insertWorkspaceWindow(
                otherWorkspaceId, 1, cutoff.minusSeconds(720), cutoff.minusSeconds(60));
        List<UUID> otherFresh = insertWorkspaceWindow(
                otherWorkspaceId, 4, cutoff.plusSeconds(60), base);

        List<UUID> before = pageThrough(workspaceId, 7).stream()
                .map(AuthorizationDecisionAudit::decisionId).toList();
        assertThat(before).as("归档前读到本 workspace 的全部 20 行").hasSize(20);
        assertThat(before).containsAll(expired).containsAll(fresh);
        assertThat(before).doesNotContainAnyElementsOf(otherExpired).doesNotContainAnyElementsOf(otherFresh);
        assertThat(archiveCount()).isZero();

        assertThat(lifecycleService.archiveBefore(cutoff, 100))
                .as("归档按决策时间选择候选行，覆盖所有 workspace 的到期行")
                .isEqualTo(13);
        assertThat(archiveCount()).isEqualTo(13);
        assertThat(hotCount()).isEqualTo(12);

        List<UUID> after = pageThrough(workspaceId, 7).stream()
                .map(AuthorizationDecisionAudit::decisionId).toList();
        assertThat(after)
                .as("热+冷并集读路径在归档前后返回完全一致的序列（无空洞、无重复）")
                .containsExactlyElementsOf(before);
        assertThat(new HashSet<>(after)).as("分页结果不得出现重复行").hasSize(20);
        assertThat(idsIn(ARCHIVE_TABLE))
                .as("归档表持有两个 workspace 的到期行")
                .containsExactlyInAnyOrderElementsOf(
                        java.util.stream.Stream.concat(expired.stream(), otherExpired.stream()).toList());
        assertThat(pageThrough(otherWorkspaceId, 3))
                .as("读路径按 workspace 过滤：另一个 workspace 归档前后都只读到自己的 5 行")
                .hasSize(5);

        assertDescendingByEvaluatedAtThenDecisionId(pageThrough(workspaceId, 7));
    }

    @Test
    void historyPaginationStaysStableWhenArchiveRunsMidScan() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID workspaceId = UUID.randomUUID();
        UUID controlWorkspaceId = UUID.randomUUID();

        List<Instant> timeline = new ArrayList<>();
        for (int index = 1; index <= 24; index++) {
            timeline.add(base.minus(Duration.ofSeconds(60L * index)));
        }
        for (Instant evaluatedAt : timeline) {
            insertAudit(Uuidv7.generate(), workspaceId, evaluatedAt, "test.resource.read");
            insertAudit(Uuidv7.generate(), controlWorkspaceId, evaluatedAt, "test.resource.read");
        }
        List<Instant> controlOrder = pageThrough(controlWorkspaceId, 5).stream()
                .map(AuthorizationDecisionAudit::evaluatedAt).toList();

        // cutoff 落在第 12 行与第 13 行之间：翻页读到一半时两个 workspace 各 12 行被搬进归档表。
        Instant cutoff = base.minus(Duration.ofSeconds(60L * 12 + 30));
        List<AuthorizationDecisionAudit> scanned = new ArrayList<>();
        AuthorizationDecisionAuditCursor cursor = null;
        for (int pageIndex = 1; pageIndex <= 20; pageIndex++) {
            AuthorizationDecisionAuditPage page = lifecycleService.history(workspaceId, cursor, 5);
            scanned.addAll(page.items());
            if (pageIndex == 1) {
                assertThat(lifecycleService.archiveBefore(cutoff, 100))
                        .as("扫描中途归档两个 workspace 的到期行（归档按决策时间全局执行）")
                        .isEqualTo(24);
                assertThat(archiveCount()).isEqualTo(24);
            }
            if (!page.hasMore()) {
                break;
            }
            cursor = page.nextCursor();
        }

        assertThat(scanned).as("中途归档不得造成行丢失或重复").hasSize(24);
        assertThat(scanned.stream().map(AuthorizationDecisionAudit::evaluatedAt).toList())
                .as("中途归档不得造成空洞或乱序")
                .containsExactlyElementsOf(controlOrder);
    }

    @Test
    void operationalStatusReportsHotArchiveAndOldestHotRow() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant cutoff = base.minus(Duration.ofHours(1));
        insertExpiredRows(4, cutoff, 1);
        insertAudit(Uuidv7.generate(), null, base.minusSeconds(30), "test.resource.read");

        AuthorizationDecisionAuditOperationalStatus before = lifecycleService.status();
        assertThat(before.hot()).isEqualTo(5);
        assertThat(before.archived()).isZero();
        assertThat(before.oldestHotAt()).isEqualTo(cutoff.minusSeconds(4));

        lifecycleService.archiveBefore(cutoff, 100);

        AuthorizationDecisionAuditOperationalStatus after = lifecycleService.status();
        assertThat(after.hot()).isEqualTo(1);
        assertThat(after.archived()).isEqualTo(4);
        assertThat(after.oldestHotAt()).isEqualTo(base.minusSeconds(30));
    }

    // ---------------------------------------------------------------- 辅助方法

    private void runDrain(
            CyclicBarrier startTogether, Instant cutoff, int batchSize,
            AtomicInteger archivedRows, AtomicReference<Throwable> failure) {
        try {
            startTogether.await(30, TimeUnit.SECONDS);
            int total = 0;
            while (true) {
                int archived = lifecycleService.archiveBefore(cutoff, batchSize);
                total += archived;
                if (archived < batchSize) {
                    break;
                }
            }
            archivedRows.set(total);
        } catch (Throwable throwable) {
            failure.compareAndSet(null, throwable);
        }
    }

    private static void awaitTermination(Thread thread) throws InterruptedException {
        thread.join(Duration.ofSeconds(30));
        if (thread.isAlive()) {
            thread.interrupt();
            throw new AssertionError("归档线程未在 30 秒内结束：" + thread.getName());
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待释放行锁超时");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static void assertDescendingByEvaluatedAtThenDecisionId(List<AuthorizationDecisionAudit> rows) {
        for (int index = 1; index < rows.size(); index++) {
            AuthorizationDecisionAudit previous = rows.get(index - 1);
            AuthorizationDecisionAudit current = rows.get(index);
            int byTime = current.evaluatedAt().compareTo(previous.evaluatedAt());
            assertThat(byTime <= 0)
                    .as("第 %d 行的 evaluated_at 不得大于前一行", index)
                    .isTrue();
            if (byTime == 0) {
                assertThat(current.decisionId().compareTo(previous.decisionId()) <= 0)
                        .as("同一 evaluated_at 下 decision_id 必须递减", index)
                        .isTrue();
            }
        }
    }

    private List<AuthorizationDecisionAudit> pageThrough(UUID workspaceId, int limit) {
        List<AuthorizationDecisionAudit> all = new ArrayList<>();
        AuthorizationDecisionAuditCursor cursor = null;
        for (int page = 0; page < 50; page++) {
            AuthorizationDecisionAuditPage current = lifecycleService.history(workspaceId, cursor, limit);
            assertThat(current.items().size()).as("单页不得超过 limit").isLessThanOrEqualTo(limit);
            all.addAll(current.items());
            if (!current.hasMore()) {
                assertThat(current.nextCursor()).as("没有下一页时游标必须为空").isNull();
                return all;
            }
            assertThat(current.nextCursor()).as("还有下一页时必须给出游标").isNotNull();
            cursor = current.nextCursor();
        }
        throw new AssertionError("分页未在 50 页内结束");
    }

    private List<UUID> insertExpiredRows(int count, Instant cutoff, int secondsBetween) {
        List<UUID> ids = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            ids.add(insertAudit(Uuidv7.generate(), null,
                    cutoff.minusSeconds((long) index * secondsBetween), "test.resource.read"));
        }
        return ids;
    }

    private List<UUID> insertRowsInWindow(int count, Instant from, Instant to) {
        List<UUID> ids = new ArrayList<>();
        long stepSeconds = Math.max(1, Duration.between(from, to).toSeconds() / (count + 1));
        for (int index = 1; index <= count; index++) {
            ids.add(insertAudit(Uuidv7.generate(), null,
                    from.plusSeconds(stepSeconds * index), "test.resource.read"));
        }
        return ids;
    }

    private List<UUID> insertWorkspaceWindow(UUID workspaceId, int count, Instant from, Instant to) {
        List<UUID> ids = new ArrayList<>();
        long span = Math.max(1, Duration.between(from, to).toSeconds());
        for (int index = 1; index <= count; index++) {
            ids.add(insertAudit(Uuidv7.generate(), workspaceId,
                    from.plusSeconds(span * index / (count + 1)), "workspace.read"));
        }
        return ids;
    }

    private UUID insertAudit(UUID decisionId, UUID workspaceId, Instant evaluatedAt, String permissionCode) {
        auditRepository.insert(new AuthorizationDecisionAudit(
                decisionId,
                workspaceId,
                "https://auth.ainer.test",
                "USER",
                "account:retention-test",
                permissionCode,
                "workspace",
                workspaceId,
                AuthorizationOutcome.ALLOW,
                "AINER.AUTHORIZATION.ALLOWED",
                "retention-test-policy",
                "req-retention",
                "trace-retention",
                evaluatedAt));
        return decisionId;
    }

    private Map<String, Object> rowSnapshot(String table, UUID decisionId) {
        return jdbcTemplate.queryForMap(
                "SELECT " + SHARED_COLUMNS + " FROM " + table + " WHERE decision_id = ?", decisionId);
    }

    private Map<UUID, Map<String, Object>> snapshots(String table, List<UUID> ids) {
        Map<UUID, Map<String, Object>> result = new LinkedHashMap<>();
        for (UUID id : ids) {
            result.put(id, rowSnapshot(table, id));
        }
        return result;
    }

    private Set<UUID> idsIn(String table) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
                "SELECT decision_id FROM " + table, UUID.class));
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

    private long distinctArchiveIds() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT decision_id) FROM " + ARCHIVE_TABLE, Long.class);
        return count == null ? -1 : count;
    }

    private long intersectionCount() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM ainer_authorization_decision_audit hot
                JOIN ainer_authorization_decision_audit_archive archive
                  ON archive.decision_id = hot.decision_id
                """, Long.class);
        return count == null ? -1 : count;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(dev.ainer.authorization.AuthorizationModuleConfiguration.class)
    static class TestApplication {
    }
}
