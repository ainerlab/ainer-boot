package dev.ainer.module.dictionary;

import dev.ainer.module.dictionary.dictionary.application.DictionaryApplicationService;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryItem;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the dictionary module (ADR-0038). Runs against a real PostgreSQL 18.3
 * Testcontainers instance, exercises the full migration → MyBatis → domain → service path.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = DictionaryIntegrationTest.TestApplication.class,
        properties = {
                "ainer.dictionary.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class DictionaryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_dictionary_test")
                    .withUsername("ainer")
                    .withPassword("ainer");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    DictionaryApplicationService service;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM ainer_dictionary_item");
        jdbcTemplate.execute("DELETE FROM ainer_dictionary_type");
    }

    @Test
    void createTypeAndGetItBack() {
        UUID typeId = service.createType(null, "gender", "性别", "Gender", "Biological gender");

        Optional<DictionaryType> loaded = service.getType(typeId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().code()).isEqualTo("gender");
        assertThat(loaded.get().name()).isEqualTo("性别");
        assertThat(loaded.get().nameEn()).isEqualTo("Gender");
    }

    @Test
    void duplicateTypeCodeFails() {
        service.createType(null, "status", "状态", "Status", null);
        assertThatThrownBy(() -> service.createType(null, "status", "重复", "Dup", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void childTypeRequiresValidParent() {
        assertThatThrownBy(() -> service.createType(
                java.util.UUID.randomUUID(), "child", "子", "Child", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void treeStructureWithParentAndChildren() {
        UUID parentId = service.createType(null, "industry", "行业", "Industry", null);
        service.createType(parentId, "tech", "科技", "Technology", null);
        service.createType(parentId, "finance", "金融", "Finance", null);

        List<DictionaryType> children = service.getChildTypes(parentId);
        assertThat(children).hasSize(2);
        assertThat(children).extracting(DictionaryType::code).contains("tech", "finance");
    }

    @Test
    void createItemAndResolveByTypeCode() {
        UUID typeId = service.createType(null, "order_status", "订单状态", "Order Status", null);
        service.createItem(typeId, "PENDING", "待处理", "Pending", "1", 0, null, null);
        service.createItem(typeId, "PAID", "已支付", "Paid", "2", 1, "text-success", null);
        service.createItem(typeId, "CANCELLED", "已取消", "Cancelled", "3", 2, "text-danger", null);

        List<DictionaryItem> items = service.resolveItemsByTypeCode("order_status");
        assertThat(items).hasSize(3);
        assertThat(items).extracting(DictionaryItem::code).containsExactly("PENDING", "PAID", "CANCELLED");
        assertThat(items.get(1).label()).isEqualTo("已支付");
        assertThat(items.get(1).labelEn()).isEqualTo("Paid");
        assertThat(items.get(1).cssClass()).isEqualTo("text-success");
    }

    @Test
    void resolveByTypeCodeUsesCacheAfterFirstCall() {
        UUID typeId = service.createType(null, "cached_type", "缓存测试", "Cached", null);
        service.createItem(typeId, "A", "A", "A-label", null, 0, null, null);

        // 第一次调用：从 DB 加载并缓存
        List<DictionaryItem> first = service.resolveItemsByTypeCode("cached_type");
        assertThat(first).hasSize(1);

        // 手动插入一条新 item（绕过 service 的缓存失效）
        jdbcTemplate.update("""
                INSERT INTO ainer_dictionary_item (id, type_id, code, label, status, sort_index, version, created_at, updated_at)
                VALUES (?, ?, 'B', 'B', 'ACTIVE', 1, 0, now(), now())
                """, java.util.UUID.randomUUID(), typeId);

        // 第二次调用：应从缓存返回旧数据（不含 B）
        List<DictionaryItem> cached = service.resolveItemsByTypeCode("cached_type");
        assertThat(cached).hasSize(1);
        assertThat(cached).extracting(DictionaryItem::code).doesNotContain("B");

        // 通过 service 创建 item → 缓存失效
        service.createItem(typeId, "C", "C", "C-label", null, 2, null, null);

        // 第三次调用：缓存已失效，重新加载（含 A 和 C，不含手动插入的 B 因 sort_index）
        List<DictionaryItem> reloaded = service.resolveItemsByTypeCode("cached_type");
        assertThat(reloaded).extracting(DictionaryItem::code).contains("A", "C");
    }

    @Test
    void resolveUnknownTypeCodeReturnsEmpty() {
        List<DictionaryItem> items = service.resolveItemsByTypeCode("nonexistent");
        assertThat(items).isEmpty();
    }

    @Test
    void duplicateItemCodeFails() {
        UUID typeId = service.createType(null, "gender", "性别", "Gender", null);
        service.createItem(typeId, "MALE", "男", "Male", null, 0, null, null);
        assertThatThrownBy(() -> service.createItem(typeId, "MALE", "重复", "Dup", null, 1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({DictionaryModuleConfiguration.class})
    static class TestApplication {
    }
}
