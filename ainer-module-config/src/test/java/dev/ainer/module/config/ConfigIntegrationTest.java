package dev.ainer.module.config;

import dev.ainer.module.config.config.application.ConfigApplicationService;
import dev.ainer.module.config.config.domain.ConfigEntry;
import dev.ainer.module.config.config.domain.ConfigHistory;
import dev.ainer.module.config.config.domain.ConfigValueType;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the config module (ADR-0038). Runs against a real PostgreSQL 18.3
 * Testcontainers instance, exercises migration → MyBatis → domain → service path including
 * type-safe retrieval, hot-reload cache, version history and secret separation.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = ConfigIntegrationTest.TestApplication.class,
        properties = {
                "ainer.config.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class ConfigIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_config_test")
                    .withUsername("ainer")
                    .withPassword("ainer");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    ConfigApplicationService service;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM ainer_config_history");
        jdbcTemplate.execute("DELETE FROM ainer_config_entry");
    }

    @Test
    void setValueAndGet() {
        service.setValue("app", "site.name", "Ainer Boot", ConfigValueType.STRING, "Site name", null);

        Optional<String> value = service.getValue("app", "site.name");
        assertThat(value).contains("Ainer Boot");
    }

    @Test
    void getTypedParsesInteger() {
        service.setValue("app", "max.connections", "100", ConfigValueType.INTEGER, null, null);
        Optional<Integer> typed = service.getTyped("app", "max.connections", Integer.class);
        assertThat(typed).contains(100);
    }

    @Test
    void getTypedParsesBoolean() {
        service.setValue("app", "feature.enabled", "true", ConfigValueType.BOOLEAN, null, null);
        Optional<Boolean> typed = service.getTyped("app", "feature.enabled", Boolean.class);
        assertThat(typed).contains(true);
    }

    @Test
    void updateValueRecordsHistoryAndBumpsVersion() {
        service.setValue("app", "timeout", "30", ConfigValueType.INTEGER, null, null);
        service.setValue("app", "timeout", "60", ConfigValueType.INTEGER, null, null);

        Optional<String> value = service.getValue("app", "timeout");
        assertThat(value).contains("60");

        List<ConfigHistory> history = service.getHistory("app", "timeout");
        assertThat(history).hasSize(2);
        // 最新版本在前（DESC）
        assertThat(history.get(0).newValue()).isEqualTo("60");
        assertThat(history.get(0).oldValue()).isEqualTo("30");
        assertThat(history.get(0).newVersion()).isEqualTo(1L);
        assertThat(history.get(0).oldVersion()).isEqualTo(0L);
    }

    @Test
    void secretIsEncryptedAndDecryptedCorrectly() {
        // setSecret 接收明文，内部 AES-GCM 加密后存储
        service.setSecret("app", "db.password", "my-secret-db-password", ConfigValueType.STRING,
                "DB password", null);

        // getValue 不返回 secret（secret 不走明文路径）
        Optional<String> viaGetValue = service.getValue("app", "db.password");
        assertThat(viaGetValue).isEmpty();

        // getSecret 解密后返回明文
        Optional<String> decrypted = service.getSecret("app", "db.password");
        assertThat(decrypted).contains("my-secret-db-password");
    }

    @Test
    void cannotSetPlaintextForExistingSecretKey() {
        service.setSecret("app", "api.key", "my-api-key", ConfigValueType.STRING, null, null);
        assertThatThrownBy(() -> service.setValue("app", "api.key", "plaintext",
                ConfigValueType.STRING, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getByNamespaceReturnsAllEntries() {
        service.setValue("ns", "a", "1", ConfigValueType.STRING, null, null);
        service.setValue("ns", "b", "2", ConfigValueType.STRING, null, null);
        service.setSecret("ns", "c", "my-secret", ConfigValueType.STRING, null, null);

        List<ConfigEntry> entries = service.getByNamespace("ns");
        assertThat(entries).hasSize(3);
        assertThat(entries).extracting(ConfigEntry::key).contains("a", "b", "c");
        assertThat(entries).filteredOn(ConfigEntry::secret).hasSize(1);
    }

    @Test
    void cacheEvictedOnValueUpdate() {
        service.setValue("app", "cached", "v1", ConfigValueType.STRING, null, null);
        assertThat(service.getValue("app", "cached")).contains("v1");

        // 通过 service 更新 → @CacheEvict 生效 → 新值
        service.setValue("app", "cached", "v2", ConfigValueType.STRING, null, null);
        assertThat(service.getValue("app", "cached")).contains("v2");
    }

    @Test
    void getHistoryForMissingKeyReturnsEmpty() {
        List<ConfigHistory> history = service.getHistory("app", "nonexistent");
        assertThat(history).isEmpty();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ConfigModuleConfiguration.class})
    static class TestApplication {
    }
}
