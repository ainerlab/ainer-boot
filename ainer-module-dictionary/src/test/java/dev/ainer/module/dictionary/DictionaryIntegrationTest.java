package dev.ainer.module.dictionary;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.dictionary.dictionary.application.DictionaryApplicationService;
import dev.ainer.module.dictionary.dictionary.application.DictionaryAuthorities;
import dev.ainer.module.dictionary.dictionary.application.DictionaryErrorCode;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryItem;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryStatus;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryType;
import dev.ainer.security.principal.HumanSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.security.token.TokenProfile;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the dictionary module (ADR-0040 management hardening). Real PostgreSQL
 * 18.3; exercises migration → MyBatis → domain → service including optimistic-locked updates,
 * status transitions, pagination, same-transaction audit and scope enforcement.
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

    private static final IdentityAuthorityRef AUTHORITY =
            new IdentityAuthorityRef("https://auth.ainer.test");

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

    private final AuthenticatedPrincipal manager = principal(
            DictionaryAuthorities.READ, DictionaryAuthorities.MANAGE);
    private final AuthenticatedPrincipal reader = principal(DictionaryAuthorities.READ);

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM ainer_dictionary_audit");
        jdbcTemplate.execute("DELETE FROM ainer_dictionary_item");
        jdbcTemplate.execute("DELETE FROM ainer_dictionary_type");
    }

    @Test
    void createTypeWritesAuditAndReadsBack() {
        UUID typeId = service.createType(manager, "req-1", null, "gender", "性别", "Gender", null);

        Optional<DictionaryType> loaded = service.getType(manager, typeId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().code()).isEqualTo("gender");
        assertThat(loaded.get().status()).isEqualTo(DictionaryStatus.ACTIVE);
        assertThat(loaded.get().id().version()).isEqualTo(7);

        Integer audits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_dictionary_audit WHERE operation = 'TYPE_CREATED'",
                Integer.class);
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void duplicateTypeCodeFails() {
        service.createType(manager, null, null, "status", "状态", "Status", null);
        assertThatThrownBy(() -> service.createType(manager, null, null, "status", "重复", "Dup", null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(DictionaryErrorCode.TYPE_ALREADY_EXISTS));
    }

    @Test
    void childTypeRequiresValidParent() {
        assertThatThrownBy(() -> service.createType(
                manager, null, UUID.randomUUID(), "child", "子", "Child", null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(DictionaryErrorCode.PARENT_NOT_FOUND));
    }

    @Test
    void treeStructureWithParentAndChildren() {
        UUID parentId = service.createType(manager, null, null, "industry", "行业", "Industry", null);
        service.createType(manager, null, parentId, "tech", "科技", "Technology", null);
        service.createType(manager, null, parentId, "finance", "金融", "Finance", null);

        List<DictionaryType> children = service.getChildTypes(manager, parentId);
        assertThat(children).hasSize(2);
        assertThat(children).extracting(DictionaryType::code).contains("tech", "finance");
    }

    @Test
    void updateTypeUsesOptimisticLockAndBumpsVersion() {
        UUID typeId = service.createType(manager, null, null, "region", "区域", "Region", null);

        DictionaryType updated = service.updateType(
                manager, null, typeId, "大区", "Region v2", null, 5, 0);
        assertThat(updated.name()).isEqualTo("大区");
        assertThat(updated.sortIndex()).isEqualTo(5);
        assertThat(updated.version()).isEqualTo(1L);

        // stale version is rejected with 409
        assertThatThrownBy(() -> service.updateType(
                manager, null, typeId, "再改", null, null, null, 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(DictionaryErrorCode.CONCURRENT_MODIFICATION));
    }

    @Test
    void changeTypeStatusDisablesAndWritesAudit() {
        UUID typeId = service.createType(manager, null, null, "legacy", "旧类型", "Legacy", null);

        DictionaryType disabled = service.changeTypeStatus(
                manager, null, typeId, DictionaryStatus.DISABLED, 0);
        assertThat(disabled.status()).isEqualTo(DictionaryStatus.DISABLED);

        String operation = jdbcTemplate.queryForObject(
                "SELECT operation FROM ainer_dictionary_audit WHERE target_kind = 'TYPE' "
                        + "AND operation = 'TYPE_STATUS_CHANGED'", String.class);
        assertThat(operation).isEqualTo("TYPE_STATUS_CHANGED");
    }

    @Test
    void pageTypesFiltersByStatus() {
        service.createType(manager, null, null, "a", "A", null, null);
        UUID b = service.createType(manager, null, null, "b", "B", null, null);
        service.changeTypeStatus(manager, null, b, DictionaryStatus.DISABLED, 0);

        var active = service.pageTypes(manager, "ACTIVE", 1, 20);
        assertThat(active.total()).isEqualTo(1);
        assertThat(active.items().get(0).code()).isEqualTo("a");

        var all = service.pageTypes(manager, null, 1, 20);
        assertThat(all.total()).isEqualTo(2);
    }

    @Test
    void itemLifecycleWithUpdateStatusAndPagination() {
        UUID typeId = service.createType(manager, null, null, "order_status", "订单状态", null, null);
        UUID first = service.createItem(manager, null, typeId, "PENDING", "待处理", "Pending", "1", 0, null, null);
        service.createItem(manager, null, typeId, "PAID", "已支付", "Paid", "2", 1, null, null);

        DictionaryItem updated = service.updateItem(
                manager, null, first, "待处理(新)", null, "0", null, null, null, 0);
        assertThat(updated.label()).isEqualTo("待处理(新)");
        assertThat(updated.version()).isEqualTo(1L);

        DictionaryItem disabled = service.changeItemStatus(
                manager, null, first, DictionaryStatus.DISABLED, 1);
        assertThat(disabled.status()).isEqualTo(DictionaryStatus.DISABLED);

        var page = service.pageItems(manager, typeId, 1, 20);
        assertThat(page.total()).isEqualTo(2);
        // resolve (active-only projection) no longer sees the disabled item
        List<DictionaryItem> resolved = service.resolveItemsByTypeCode("order_status");
        assertThat(resolved).extracting(DictionaryItem::code).containsExactly("PAID");
    }

    @Test
    void duplicateItemCodeFails() {
        UUID typeId = service.createType(manager, null, null, "gender", "性别", "Gender", null);
        service.createItem(manager, null, typeId, "MALE", "男", "Male", null, 0, null, null);
        assertThatThrownBy(() -> service.createItem(manager, null, typeId, "MALE", "重复", "Dup", null, 1, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(DictionaryErrorCode.ITEM_ALREADY_EXISTS));
    }

    @Test
    void cacheEvictedOnItemCreate() {
        UUID typeId = service.createType(manager, null, null, "cached_type", "缓存测试", "Cached", null);
        service.createItem(manager, null, typeId, "A", "A", "A-label", null, 0, null, null);

        List<DictionaryItem> first = service.resolveItemsByTypeCode("cached_type");
        assertThat(first).hasSize(1);

        service.createItem(manager, null, typeId, "B", "B", "B-label", null, 1, null, null);

        List<DictionaryItem> reloaded = service.resolveItemsByTypeCode("cached_type");
        assertThat(reloaded).extracting(DictionaryItem::code).contains("A", "B");
    }

    @Test
    void resolveUnknownTypeCodeReturnsEmpty() {
        List<DictionaryItem> items = service.resolveItemsByTypeCode("nonexistent");
        assertThat(items).isEmpty();
    }

    @Test
    void manageWithoutScopeIsForbidden() {
        service.createType(manager, null, null, "x", "X", null, null); // manager is allowed
        assertThatThrownBy(() -> service.createType(reader, null, null, "y", "Y", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(StandardErrorCode.FORBIDDEN));
        service.pageTypes(reader, null, 1, 20); // read scope is sufficient for queries
    }

    @Test
    void invalidPageSizeIsRejected() {
        assertThatThrownBy(() -> service.pageTypes(manager, null, 1, 101))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(DictionaryErrorCode.INVALID_PAGE));
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
    @Import({DictionaryModuleConfiguration.class})
    static class TestApplication {
    }

    /** Satisfies the controller's resolver dependency without enabling the resource-server chain. */
    @TestConfiguration
    static class PrincipalFixture {

        @Bean
        AuthenticatedPrincipalResolver integrationTestPrincipalResolver() {
            return () -> new AuthenticatedPrincipal(
                    new HumanSubjectRef(AUTHORITY, "account:1"),
                    AUTHORITY,
                    TokenProfile.USER_NEUTRAL_V1,
                    "1",
                    Set.of("ainer-api"),
                    Set.of(DictionaryAuthorities.READ, DictionaryAuthorities.MANAGE),
                    "pwd",
                    null,
                    0L);
        }
    }
}
