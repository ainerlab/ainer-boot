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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private ServicePrincipalRepository servicePrincipalRepository;

    @Autowired
    private OAuthClientBindingRepository oauthClientBindingRepository;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private HumanProfileRepository humanProfileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanFoundationTables() {
        jdbcTemplate.update("DELETE FROM ainer_identity_oauth_client_binding");
        jdbcTemplate.update("DELETE FROM ainer_identity_service_principal");
        jdbcTemplate.update("DELETE FROM ainer_identity_credential");
        jdbcTemplate.update("DELETE FROM ainer_identity_human_profile");
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

    @Test
    void persistsServicePrincipalAndResolvesByActiveClientId() {
        IdentityAuthorityRef authority = new IdentityAuthorityRef("https://ainer.example/auth");
        UUID principalId = servicePrincipalRepository.nextUuidV7();
        servicePrincipalRepository.save(new ServicePrincipal(principalId, authority,
                ServicePrincipalStatus.ACTIVE, 0L, Instant.parse("2026-08-06T10:00:00Z")));

        UUID bindingId = oauthClientBindingRepository.nextUuidV7();
        oauthClientBindingRepository.save(new OAuthClientBinding(bindingId, principalId,
                "machine-client-1", OAuthClientBindingStatus.ACTIVE,
                Instant.parse("2026-08-06T10:00:00Z"), null));

        assertThat(servicePrincipalRepository.findByPrincipalId(principalId))
                .hasValueSatisfying(p -> assertThat(p.status()).isEqualTo(ServicePrincipalStatus.ACTIVE));
        assertThat(servicePrincipalRepository.findByActiveClientId("machine-client-1"))
                .hasValueSatisfying(p -> assertThat(p.principalId()).isEqualTo(principalId));
        assertThat(oauthClientBindingRepository.findActiveByClientId("machine-client-1"))
                .hasValueSatisfying(b -> assertThat(b.principalId()).isEqualTo(principalId));
    }

    @Test
    void doesNotResolveRetiredClientBindingAsActive() {
        IdentityAuthorityRef authority = new IdentityAuthorityRef("https://ainer.example/auth");
        UUID principalId = servicePrincipalRepository.nextUuidV7();
        servicePrincipalRepository.save(new ServicePrincipal(principalId, authority,
                ServicePrincipalStatus.ACTIVE, 0L, Instant.parse("2026-08-06T10:00:00Z")));

        UUID bindingId = oauthClientBindingRepository.nextUuidV7();
        oauthClientBindingRepository.save(new OAuthClientBinding(bindingId, principalId,
                "retired-client", OAuthClientBindingStatus.RETIRED,
                Instant.parse("2026-08-06T10:00:00Z"), Instant.parse("2026-08-06T11:00:00Z")));

        assertThat(servicePrincipalRepository.findByActiveClientId("retired-client")).isEmpty();
        assertThat(oauthClientBindingRepository.findActiveByClientId("retired-client")).isEmpty();
    }

    @Test
    void persistsActiveCredentialAndResolvesByAccountAndType() {
        UUID accountId = accountRepository.nextUuidV7();
        IdentityAuthorityRef authority = new IdentityAuthorityRef("https://ainer.example/auth");
        accountRepository.save(new HumanAccount(accountId, authority, AccountStatus.ACTIVE, 0L,
                Instant.parse("2026-08-06T10:00:00Z")));

        UUID credentialId = credentialRepository.nextUuidV7();
        credentialRepository.insert(new Credential(credentialId, accountId, CredentialType.PASSWORD,
                "{bcrypt}encoded-hash-material", CredentialStatus.ACTIVE,
                Instant.parse("2026-08-06T10:00:00Z"), null));

        assertThat(credentialRepository.findActive(accountId, CredentialType.PASSWORD))
                .hasValueSatisfying(c -> {
                    assertThat(c.credentialId()).isEqualTo(credentialId);
                    assertThat(c.isActive()).isTrue();
                    assertThat(c.rotatedAt()).isNull();
                });
        assertThat(credentialRepository.findActive(accountId, CredentialType.WEBAUTHN_PUBLIC_KEY)).isEmpty();
    }

    @Test
    void revokingActiveCredentialMarksRotatedAtAndStopsResolving() {
        UUID accountId = accountRepository.nextUuidV7();
        IdentityAuthorityRef authority = new IdentityAuthorityRef("https://ainer.example/auth");
        accountRepository.save(new HumanAccount(accountId, authority, AccountStatus.ACTIVE, 0L,
                Instant.parse("2026-08-06T10:00:00Z")));

        UUID credentialId = credentialRepository.nextUuidV7();
        credentialRepository.insert(new Credential(credentialId, accountId, CredentialType.PASSWORD,
                "{bcrypt}first-hash", CredentialStatus.ACTIVE,
                Instant.parse("2026-08-06T10:00:00Z"), null));

        Instant rotatedAt = Instant.parse("2026-08-06T11:00:00Z");
        assertThat(credentialRepository.revokeActive(accountId, CredentialType.PASSWORD, rotatedAt)).isEqualTo(1);
        assertThat(credentialRepository.findActive(accountId, CredentialType.PASSWORD)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT rotated_at FROM ainer_identity_credential WHERE id = ?",
                java.sql.Timestamp.class, credentialId)).isEqualTo(java.sql.Timestamp.from(rotatedAt));
        assertThat(credentialRepository.revokeActive(accountId, CredentialType.PASSWORD, rotatedAt)).isZero();
    }

    @Test
    void rejectsSecondActiveCredentialForSameAccountAndType() {
        UUID accountId = accountRepository.nextUuidV7();
        IdentityAuthorityRef authority = new IdentityAuthorityRef("https://ainer.example/auth");
        accountRepository.save(new HumanAccount(accountId, authority, AccountStatus.ACTIVE, 0L,
                Instant.parse("2026-08-06T10:00:00Z")));

        UUID first = credentialRepository.nextUuidV7();
        credentialRepository.insert(new Credential(first, accountId, CredentialType.PASSWORD,
                "{bcrypt}first-hash", CredentialStatus.ACTIVE,
                Instant.parse("2026-08-06T10:00:00Z"), null));

        UUID second = credentialRepository.nextUuidV7();
        assertThatThrownBy(() -> credentialRepository.insert(new Credential(
                second, accountId, CredentialType.PASSWORD, "{bcrypt}second-hash",
                CredentialStatus.ACTIVE, Instant.parse("2026-08-06T10:01:00Z"), null)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void persistsAndUpsertsHumanProfile() {
        UUID accountId = accountRepository.nextUuidV7();
        IdentityAuthorityRef authority = new IdentityAuthorityRef("https://ainer.example/auth");
        accountRepository.save(new HumanAccount(accountId, authority, AccountStatus.ACTIVE, 0L,
                Instant.parse("2026-08-06T10:00:00Z")));

        humanProfileRepository.upsert(new HumanProfile(accountId, "Ainer User",
                "https://cdn.example/avatar.png", Instant.parse("2026-08-06T10:00:00Z")));
        assertThat(humanProfileRepository.findByAccountId(accountId))
                .hasValueSatisfying(p -> {
                    assertThat(p.displayName()).isEqualTo("Ainer User");
                    assertThat(p.avatarUrl()).isEqualTo("https://cdn.example/avatar.png");
                });

        humanProfileRepository.upsert(new HumanProfile(accountId, "Renamed", null,
                Instant.parse("2026-08-06T10:05:00Z")));
        assertThat(humanProfileRepository.findByAccountId(accountId))
                .hasValueSatisfying(p -> {
                    assertThat(p.displayName()).isEqualTo("Renamed");
                    assertThat(p.avatarUrl()).isNull();
                    assertThat(p.updatedAt()).isEqualTo(Instant.parse("2026-08-06T10:05:00Z"));
                });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_identity_human_profile WHERE account_id = ?",
                Integer.class, accountId)).isEqualTo(1);
    }

    @Test
    void doesNotResolveProfileForUnknownAccount() {
        assertThat(humanProfileRepository.findByAccountId(UUID.randomUUID())).isEmpty();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = IdentityFoundationMarker.class)
    @MapperScan(basePackageClasses = IdentityFoundationMarker.class, annotationClass = Mapper.class)
    static class TestApp {

        @org.springframework.context.annotation.Bean
        java.time.Clock clock() {
            return java.time.Clock.systemUTC();
        }
    }
}
