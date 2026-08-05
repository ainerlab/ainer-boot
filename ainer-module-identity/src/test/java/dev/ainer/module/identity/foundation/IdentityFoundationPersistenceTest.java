package dev.ainer.module.identity.foundation;

import dev.ainer.security.principal.IdentityAuthorityRef;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Greenfield Identity foundation persistence (S1.2 wiring). Verifies the {@code ainer_identity_human_account}
 * / {@code ainer_identity_login_identity} tables are created by Flyway and that the MyBatis repositories
 * implement the foundation ports against PostgreSQL. The {@code TestApp} scans only the foundation package
 * so the legacy {@code account} beans (and their extra config dependencies) are not pulled in.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = IdentityFoundationPersistenceTest.TestApp.class, properties = {
        "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
        "spring.main.banner-mode=off"
})
class IdentityFoundationPersistenceTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_foundation_test")
            .withUsername("ainer")
            .withPassword("ainer");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private HumanAccountRepository accountRepository;

    @Autowired
    private LoginIdentityRepository loginIdentityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanFoundationTables() {
        jdbcTemplate.update("DELETE FROM ainer_identity_login_identity");
        jdbcTemplate.update("DELETE FROM ainer_identity_human_account");
    }

    @Test
    void nextUuidV7ProducesVersion7Identifiers() {
        assertThat(accountRepository.nextUuidV7().version()).isEqualTo(7);
    }

    @Test
    void persistsAccountAndLoginIdentityAndResolvesByCredential() {
        UUID accountId = accountRepository.nextUuidV7();
        IdentityAuthorityRef authority = new IdentityAuthorityRef("https://ainer.example/auth");
        HumanAccount account = new HumanAccount(accountId, authority, AccountStatus.ACTIVE, 0L,
                Instant.parse("2026-08-05T10:00:00Z"));
        accountRepository.save(account);

        UUID loginId = accountRepository.nextUuidV7();
        LoginIdentity login = new LoginIdentity(loginId, accountId, LoginIdentityType.EMAIL,
                authority.issuer(), "foundation@example.com", LoginIdentityStatus.ACTIVE,
                Instant.parse("2026-08-05T09:59:00Z"), Instant.parse("2026-08-05T10:00:00Z"), null);
        loginIdentityRepository.save(login);

        assertThat(accountRepository.findByAccountId(accountId)).contains(account);
        assertThat(loginIdentityRepository.findByTypeAndIdentifier(
                LoginIdentityType.EMAIL, authority.issuer(), "foundation@example.com")).contains(login);
        assertThat(loginIdentityRepository.findByAccount(accountId))
                .hasSize(1)
                .first().isEqualTo(login);
    }

    @Test
    void doesNotResolveRevokedBindingAsActiveCredential() {
        UUID accountId = accountRepository.nextUuidV7();
        IdentityAuthorityRef authority = new IdentityAuthorityRef("https://ainer.example/auth");
        accountRepository.save(new HumanAccount(accountId, authority, AccountStatus.ACTIVE, 0L,
                Instant.parse("2026-08-05T10:00:00Z")));

        UUID loginId = accountRepository.nextUuidV7();
        loginIdentityRepository.save(new LoginIdentity(loginId, accountId, LoginIdentityType.EMAIL,
                authority.issuer(), "revoked@example.com", LoginIdentityStatus.REVOKED,
                Instant.parse("2026-08-05T09:59:00Z"), Instant.parse("2026-08-05T10:00:00Z"), null));

        assertThat(loginIdentityRepository.findByTypeAndIdentifier(
                LoginIdentityType.EMAIL, authority.issuer(), "revoked@example.com")).isEmpty();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = IdentityFoundationMarker.class)
    @MapperScan(basePackageClasses = IdentityFoundationMarker.class, annotationClass = Mapper.class)
    static class TestApp {
    }
}
