package dev.ainer.module.config;

import dev.ainer.cache.autoconfigure.AinerCacheCapabilities;
import dev.ainer.module.config.config.application.ConfigApplicationService;
import dev.ainer.module.config.config.domain.ConfigEntry;
import dev.ainer.module.config.config.domain.ConfigValueType;
import dev.ainer.testfixture.config.CountingConfigEntryRepository;
import dev.ainer.testfixture.config.CountingRepositoryFixture;
import dev.ainer.testfixture.config.RedisFailFastFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0039 的 Redis 缓存端到端验证：用<strong>真实被缓存类型</strong>
 * {@code Optional<ConfigEntry>}（{@code ConfigApplicationService#getEntry} 的返回类型）走一遍
 * PostgreSQL → MyBatis → Spring Cache → Redis → 反序列化 → 调用方 的完整链路。
 *
 * <p>覆盖三件事：
 * <ol>
 *   <li>缓存命中返回的仍是 {@code Optional<ConfigEntry>}（Jackson 会丢 Optional 包装，
 *       由缓存值序列化器归一化兜住），且值确实来自 Redis 而不是数据库；</li>
 *   <li><strong>主读路径真的接通缓存</strong>：用计数仓储替身断言 {@code getValue}/{@code getSecret}
 *       在缓存命中时不再访问数据库（自调用绕过代理的路径已消除）；</li>
 *   <li>secret 字段在缓存里是密文实体，明文只在内存中解密。</li>
 * </ol>
 *
 * <p><strong>测试环境策略</strong>：本类通过 {@code RedisFailFastFixture} 让 Lettuce 在断连时直接失败
 * （{@code REJECT_COMMANDS} + {@code autoReconnect(false)}）。原因是一次实测发现：本机 Colima 偶发
 * Redis 连接抖动时，Lettuce 默认会缓冲命令并在重连后重放，「写 → evict → 读」因此可能乱序落地，
 * 缓存里静默留下过期值。禁掉重放后，连接问题以明确的连接异常暴露，而不是以顺序错乱暴露；
 * 读写时序相关的等待统一走 {@link #awaitRedis}（有界、只容忍状态未就绪与连接/超时类瞬时异常，
 * 断言一次性执行）。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = {ConfigIntegrationTest.TestApplication.class, CountingRepositoryFixture.class,
                RedisFailFastFixture.class},
        properties = {
                "ainer.config.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off",
                "ainer.cache.type=redis",
                "ainer.cache.redis.key-prefix=ainer:it:config:"
        })
class ConfigCacheRedisIntegrationTest {

    private static final String ENTRY_CACHE_KEY = "ainer:it:config:config:entry::app:site.name";

    /**
     * 等待 Redis 只读状态就绪的上限与探测间隔。重试只用于容器/连接预热这类瞬时故障，
     * 不用于吞掉断言失败（详见 {@link #awaitRedis}）。
     */
    private static final Duration RETRY_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration RETRY_INTERVAL = Duration.ofMillis(100);

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_config_cache_test")
                    .withUsername("ainer")
                    .withPassword("ainer");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    ConfigApplicationService service;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    CacheManager cacheManager;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    AinerCacheCapabilities capabilities;
    @Autowired
    CountingConfigEntryRepository countingRepository;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM ainer_config_history");
        jdbcTemplate.execute("DELETE FROM ainer_config_entry");
        cacheManager.getCache(ConfigApplicationService.CACHE_CONFIG_ENTRY).clear();
        countingRepository.resetFindCalls();
    }

    @Test
    void cacheReallyServesOptionalOfRealConfigEntryFromRedis() {
        service.setValue("app", "site.name", "Ainer Boot", ConfigValueType.STRING, "Site name", null);

        Optional<ConfigEntry> first = service.getEntry("app", "site.name");
        assertThat(first).isPresent();
        assertThat(first.orElseThrow().value()).isEqualTo("Ainer Boot");

        // 绕过 service 直接改库（不触发 @CacheEvict）：若缓存生效，第二次读到的仍是旧值
        jdbcTemplate.update(
                "UPDATE ainer_config_entry SET config_value = ? WHERE namespace = ? AND config_key = ?",
                "tampered", "app", "site.name");
        Optional<ConfigEntry> servedFromCache = service.getEntry("app", "site.name");
        // 读回来必须仍是 Optional<ConfigEntry>（丢了 Optional 包装这里就会 ClassCastException）
        assertThat(servedFromCache).isPresent();
        assertThat(servedFromCache.orElseThrow().value()).isEqualTo("Ainer Boot");

        // Redis 里是带类型标记的 JSON：Spring Cache 已把 Optional 拆包，缓存的是 ConfigEntry 本体
        String cachedJson = awaitRedis("缓存值 " + ENTRY_CACHE_KEY,
                () -> redis.opsForValue().get(ENTRY_CACHE_KEY), Objects::nonNull);
        assertThat(cachedJson).contains("ConfigEntry");

        Long ttlSeconds = awaitRedis("TTL " + ENTRY_CACHE_KEY,
                () -> redis.getExpire(ENTRY_CACHE_KEY), ttl -> ttl != null && ttl > 0);
        assertThat(ttlSeconds).isLessThanOrEqualTo(Duration.ofMinutes(30).toSeconds());

        // service 写入触发 @CacheEvict → 缓存被真实删除，数据库新值可见。
        // 等待 evict 在缓存上可观察（同一套有界策略：只容忍连接/超时类瞬时故障与状态未就绪），
        // 断言在等待之后一次性执行；evict 若始终不发生会以明确的超时错误失败，不被重试掩盖。
        service.setValue("app", "site.name", "Ainer Boot v2", ConfigValueType.STRING, "Site name", null);
        awaitRedis("evict " + ENTRY_CACHE_KEY,
                () -> cacheManager.getCache(ConfigApplicationService.CACHE_CONFIG_ENTRY).get("app:site.name"),
                Objects::isNull);
        awaitRedis("写后新值 app:site.name",
                () -> service.getValue("app", "site.name"),
                value -> value.filter("Ainer Boot v2"::equals).isPresent());
        assertThat(service.getValue("app", "site.name")).contains("Ainer Boot v2");
    }

    @Test
    void mainReadPathsDoNotHitDatabaseAgainOnCacheHit() {
        service.setValue("app", "cache.probe", "v1", ConfigValueType.STRING, "probe", null);
        service.setSecret("app", "cache.secret", "s3cret", ConfigValueType.STRING, null, null);
        countingRepository.resetFindCalls();

        // 首次读：缓存未命中 → 两个键各打一次数据库
        assertThat(service.getValue("app", "cache.probe")).contains("v1");
        assertThat(service.getSecret("app", "cache.secret")).contains("s3cret");
        assertThat(countingRepository.findCalls()).isEqualTo(2);

        // 之后任意多次读都命中缓存 → 数据库调用次数不再增长。
        // 修复前 getValue/getSecret 自调用 getEntry 绕过缓存代理，这里的计数会继续增长。
        assertThat(service.getValue("app", "cache.probe")).contains("v1");
        assertThat(service.getValue("app", "cache.probe")).contains("v1");
        assertThat(service.getSecret("app", "cache.secret")).contains("s3cret");
        assertThat(service.getEntry("app", "cache.probe")).isPresent();
        assertThat(countingRepository.findCalls()).isEqualTo(2);

        // 写路径必须绕过缓存去读数据库当前版本（乐观锁判定）：这一次读必然打库
        service.setValue("app", "cache.probe", "v2", ConfigValueType.STRING, "probe", null);
        assertThat(countingRepository.findCalls()).isEqualTo(3);

        // 写后 evict 生效，读回到新值并重新打库。
        // 等待的是「可观察状态」而不是断言本身：拿到 v1 这种不一致状态会继续等待，
        // 窗口耗尽则以明确的超时错误失败；值断言在等待之后只执行一次。
        awaitRedis("写后新值 app:cache.probe",
                () -> service.getValue("app", "cache.probe"),
                value -> value.filter("v2"::equals).isPresent());
        assertThat(service.getValue("app", "cache.probe")).contains("v2");
        assertThat(countingRepository.findCalls()).isEqualTo(4);
    }

    @Test
    void secretStaysEncryptedInRedis() {
        service.setSecret("app", "db.password", "my-secret-db-password", ConfigValueType.STRING,
                "DB password", null);

        // getSecret 解密只在内存里发生；读取已经接通缓存（ConfigEntryLookup）→ 缓存里立刻有条目
        assertThat(service.getSecret("app", "db.password")).contains("my-secret-db-password");

        String cacheKey = "ainer:it:config:config:entry::app:db.password";
        String cachedJson = awaitRedis("缓存值 " + cacheKey,
                () -> redis.opsForValue().get(cacheKey), Objects::nonNull);
        assertThat(cachedJson).contains("ConfigEntry");
        // Redis 中缓存的是密文实体，明文密钥不落缓存
        assertThat(cachedJson).doesNotContain("my-secret-db-password");
    }

    @Test
    void capabilitiesReportRedisBackendAndMultiInstanceSafeLock() {
        assertThat(capabilities.cacheManagerClass()).contains("RedisCacheManager");
        assertThat(capabilities.multiInstanceSafe()).isTrue();
    }

    // ---- 有界等待（只容忍瞬时故障，不吞断言失败）----

    /**
     * 上界 {@link #RETRY_TIMEOUT}（10 秒）、每 {@link #RETRY_INTERVAL}（100ms）探测一次的只读等待。
     *
     * <p><strong>重试条件</strong>：状态尚未就绪，或读取抛出连接/超时类瞬时异常
     * （{@link DataAccessResourceFailureException}，含 {@code RedisConnectionFailureException}；
     * 以及 {@link QueryTimeoutException}）——容器启动与 Lettuce 预热属于这一类。其他异常立即冒泡。
     *
     * <p><strong>等待的是可观察状态，不是断言</strong>：本方法只负责把状态取回来，内容断言由调用方在拿到
     * 状态之后一次性执行（值不对立即失败）。唯一被重试的「状态未就绪」是写后缓存一致性状态
     * （evict 尚未可观察、新值尚未可读）——它由 Redis 命令的落地时序决定，窗口耗尽会抛出明确的
     * 超时错误而不是静默通过。
     */
    private static <T> T awaitRedis(String what, Supplier<T> read, Predicate<T> ready) {
        Instant deadline = Instant.now().plus(RETRY_TIMEOUT);
        while (true) {
            try {
                T value = read.get();
                if (ready.test(value)) {
                    return value;
                }
            } catch (DataAccessResourceFailureException | QueryTimeoutException transientFailure) {
                // 瞬时连接/超时故障：在窗口内继续等待
            }
            if (!Instant.now().isBefore(deadline)) {
                throw new AssertionError("等待 Redis " + what + " 超时（上限 " + RETRY_TIMEOUT + "）");
            }
            LockSupport.parkNanos(RETRY_INTERVAL.toNanos());
        }
    }
}
