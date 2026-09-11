package dev.ainer.module.config;

import dev.ainer.cache.autoconfigure.AinerCacheCapabilities;
import dev.ainer.module.config.config.application.ConfigApplicationService;
import dev.ainer.module.config.config.domain.ConfigEntry;
import dev.ainer.module.config.config.domain.ConfigValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * ADR-0039 的 Redis 缓存端到端验证：用<strong>真实被缓存类型</strong>
 * {@code Optional<ConfigEntry>}（{@code ConfigApplicationService#getEntry} 的返回类型）走一遍
 * PostgreSQL → MyBatis → Spring Cache → Redis → 反序列化 → 调用方 的完整链路。
 *
 * <p>为什么必须测：{@code RedisCache} 反序列化时只知道目标类型是 {@code Object}，
 * 完全依赖 JSON 里的 {@code @class} 类型标记。实测发现 Jackson 会丢掉 {@code Optional} 包装
 * （读回来是 {@code ConfigEntry}，调用方 {@code .map(...)} 直接 ClassCastException），因此
 * 缓存值序列化器对根值做了归一化（见
 * {@code AinerRedisCacheAutoConfiguration#redisCacheValueSerializer()}）。本测试断言
 * 「缓存命中返回的仍是 {@code Optional<ConfigEntry>}」以及「值确实来自 Redis 而不是数据库」。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = ConfigIntegrationTest.TestApplication.class,
        properties = {
                "ainer.config.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off",
                "ainer.cache.type=redis",
                "ainer.cache.redis.key-prefix=ainer:it:config:"
        })
class ConfigCacheRedisIntegrationTest {

    private static final String ENTRY_CACHE_KEY = "ainer:it:config:config:entry::app:site.name";

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

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM ainer_config_history");
        jdbcTemplate.execute("DELETE FROM ainer_config_entry");
        cacheManager.getCache(ConfigApplicationService.CACHE_CONFIG_ENTRY).clear();
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

        // Redis 里是带类型标记的 JSON：Spring Cache 已把 Optional 拆包，缓存的是 ConfigEntry 本体。
        // 用 await 容忍 Lettuce 偶发重连（容器/网络抖动），不掩盖"缓存从未写入"的失败。
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String cachedJson = redis.opsForValue().get(ENTRY_CACHE_KEY);
            assertThat(cachedJson).isNotNull().contains("ConfigEntry");
            assertThat(redis.getExpire(ENTRY_CACHE_KEY)).isPositive();
        });

        // service 写入触发 @CacheEvict → 缓存被真实删除，数据库新值可见
        service.setValue("app", "site.name", "Ainer Boot v2", ConfigValueType.STRING, "Site name", null);
        assertThat(service.getValue("app", "site.name")).contains("Ainer Boot v2");
    }

    @Test
    void secretStaysEncryptedInRedis() {
        service.setSecret("app", "db.password", "my-secret-db-password", ConfigValueType.STRING,
                "DB password", null);

        // getSecret 解密只在内存里发生（明文不落缓存）
        assertThat(service.getSecret("app", "db.password")).contains("my-secret-db-password");

        // 已知边界：getSecret/getValue 内部自调用 getEntry，绕过缓存代理，因此这里 Redis 仍无条目
        String cacheKey = "ainer:it:config:config:entry::app:db.password";
        assertThat(redis.opsForValue().get(cacheKey)).isNull();

        // 外部调用 getEntry 才经过缓存代理：缓存里是密文实体，明文密钥不落缓存
        assertThat(service.getEntry("app", "db.password")).isPresent();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String cachedJson = redis.opsForValue().get(cacheKey);
            assertThat(cachedJson).isNotNull().contains("ConfigEntry");
            assertThat(cachedJson).doesNotContain("my-secret-db-password");
        });
    }

    @Test
    void capabilitiesReportRedisBackendAndMultiInstanceSafeLock() {
        assertThat(capabilities.cacheManagerClass()).contains("RedisCacheManager");
        assertThat(capabilities.multiInstanceSafe()).isTrue();
    }
}
