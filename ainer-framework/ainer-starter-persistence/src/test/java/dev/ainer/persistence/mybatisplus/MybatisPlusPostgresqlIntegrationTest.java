package dev.ainer.persistence.mybatisplus;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = MybatisPlusPostgresqlIntegrationTest.TestApplication.class,
        properties = {
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "mybatis-plus.configuration.map-underscore-to-camel-case=true",
                "spring.main.banner-mode=off"
        })
class MybatisPlusPostgresqlIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_persistence_test")
            .withUsername("ainer")
            .withPassword("ainer");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private PersistenceProbeMapper mapper;

    @Test
    void supportsDatabaseGeneratedUuidv7BaseMapperXmlAndPagination() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        PersistenceProbeRow first = new PersistenceProbeRow(tenantA, "alpha");
        PersistenceProbeRow second = new PersistenceProbeRow(tenantA, "beta");
        PersistenceProbeRow otherTenant = new PersistenceProbeRow(tenantB, "gamma");

        assertThat(mapper.insert(first)).isEqualTo(1);
        assertThat(mapper.insert(second)).isEqualTo(1);
        assertThat(mapper.insert(otherTenant)).isEqualTo(1);

        assertThat(first.getId()).isNotNull();
        assertThat(first.getId().version()).isEqualTo(7);
        assertThat(mapper.selectById(first.getId()).getName()).isEqualTo("alpha");
        assertThat(mapper.selectNamesByTenant(tenantA)).containsExactly("alpha", "beta");

        Page<PersistenceProbeRow> page = mapper.selectPage(
                Page.of(1, 1),
                Wrappers.<PersistenceProbeRow>lambdaQuery()
                        .eq(PersistenceProbeRow::getTenantId, tenantA)
                        .orderByAsc(PersistenceProbeRow::getName));

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords())
                .extracting(PersistenceProbeRow::getName)
                .containsExactly("alpha");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = PersistenceProbeMapper.class)
    static class TestApplication {
    }
}
