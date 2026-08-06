package dev.ainer.authorizationserver.admin;

import dev.ainer.authorizationserver.config.AinerAuthorizationServerProperties;
import dev.ainer.module.identity.foundation.Credential;
import dev.ainer.module.identity.foundation.CredentialRepository;
import dev.ainer.module.identity.foundation.CredentialStatus;
import dev.ainer.module.identity.foundation.CredentialType;
import dev.ainer.module.identity.foundation.HumanAccount;
import dev.ainer.module.identity.foundation.HumanAccountRepository;
import dev.ainer.module.identity.foundation.HumanProfile;
import dev.ainer.module.identity.foundation.HumanProfileRepository;
import dev.ainer.module.identity.foundation.IdentityFoundationService;
import dev.ainer.module.identity.foundation.LoginIdentity;
import dev.ainer.module.identity.foundation.LoginIdentityRepository;
import dev.ainer.module.identity.foundation.LoginIdentityStatus;
import dev.ainer.module.identity.foundation.LoginIdentityType;
import dev.ainer.security.principal.IdentityAuthorityRef;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AinerAdminDevFixtureRunnerTest {

    private static final String ISSUER = "https://auth.ainer.test";

    @Test
    void provisionsTwoFoundationAccountsAndProfiles() {
        Fixture fixture = new Fixture();

        new AinerAdminDevFixtureRunner(properties(), fixture.authorizationProperties, fixture.service)
                .run(new DefaultApplicationArguments(new String[0]));

        assertThat(fixture.logins.findByTypeAndIdentifier(
                LoginIdentityType.USERNAME, ISSUER, "owner@ainer.test")).isPresent();
        assertThat(fixture.logins.findByTypeAndIdentifier(
                LoginIdentityType.USERNAME, ISSUER, "member@ainer.test")).isPresent();
        assertThat(fixture.profiles.values()).extracting(HumanProfile::displayName)
                .containsExactlyInAnyOrder("Ainer Admin Owner", "Ainer Admin Member");
        assertThat(fixture.credentials.values()).allMatch(Credential::isActive);
    }

    @Test
    void equalUsernamesAndMissingSecretsFailBeforeWriting() {
        Fixture equalFixture = new Fixture();
        AinerAdminDevBootstrapProperties equal = properties(" OWNER@AINER.TEST ", "owner-password-2026");

        assertThatThrownBy(() -> new AinerAdminDevFixtureRunner(
                equal, equalFixture.authorizationProperties, equalFixture.service)
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be different");
        assertThat(equalFixture.accounts.size()).isZero();

        Fixture missingFixture = new Fixture();
        AinerAdminDevBootstrapProperties missing = properties("member@ainer.test", "");
        assertThatThrownBy(() -> new AinerAdminDevFixtureRunner(
                missing, missingFixture.authorizationProperties, missingFixture.service)
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner password");
        assertThat(missingFixture.accounts.size()).isZero();
    }

    private static AinerAdminDevBootstrapProperties properties() {
        return properties("member@ainer.test", "owner-password-2026");
    }

    private static AinerAdminDevBootstrapProperties properties(
            String memberUsername, String ownerPassword) {
        return new AinerAdminDevBootstrapProperties(
                true,
                "owner@ainer.test",
                ownerPassword,
                "Ainer Admin Owner",
                memberUsername,
                "member-password-2026",
                "Ainer Admin Member");
    }

    private static AinerAuthorizationServerProperties authorizationProperties() {
        return new AinerAuthorizationServerProperties(
                ISSUER, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static final class Fixture {

        private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        private final InMemoryLoginIdentityRepository logins = new InMemoryLoginIdentityRepository();
        private final InMemoryCredentialRepository credentials = new InMemoryCredentialRepository();
        private final InMemoryHumanProfileRepository profiles = new InMemoryHumanProfileRepository();
        private final AinerAuthorizationServerProperties authorizationProperties = authorizationProperties();
        private final IdentityFoundationService service = new IdentityFoundationService(
                accounts,
                logins,
                credentials,
                profiles,
                PasswordEncoderFactories.createDelegatingPasswordEncoder(),
                Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC),
                sequentialIds());

        private static Supplier<UUID> sequentialIds() {
            AtomicLong counter = new AtomicLong(1);
            return () -> new UUID(0L, counter.getAndIncrement());
        }
    }

    private static final class InMemoryAccountRepository implements HumanAccountRepository {

        private final Map<UUID, HumanAccount> values = new HashMap<>();

        @Override
        public void save(HumanAccount account) {
            values.put(account.accountId(), account);
        }

        @Override
        public Optional<HumanAccount> findByAccountId(UUID accountId) {
            return Optional.ofNullable(values.get(accountId));
        }

        @Override
        public UUID nextUuidV7() {
            return UUID.randomUUID();
        }

        private int size() {
            return values.size();
        }
    }

    private static final class InMemoryLoginIdentityRepository implements LoginIdentityRepository {

        private final Map<UUID, LoginIdentity> values = new HashMap<>();

        @Override
        public void save(LoginIdentity identity) {
            values.put(identity.identityId(), identity);
        }

        @Override
        public Optional<LoginIdentity> findByTypeAndIdentifier(
                LoginIdentityType type, String providerAuthority, String normalizedIdentifier) {
            return values.values().stream()
                    .filter(identity -> identity.type() == type
                            && identity.providerAuthority().equals(providerAuthority)
                            && identity.normalizedIdentifier().equals(normalizedIdentifier)
                            && identity.status() == LoginIdentityStatus.ACTIVE)
                    .findFirst();
        }

        @Override
        public List<LoginIdentity> findByAccount(UUID accountId) {
            return values.values().stream()
                    .filter(identity -> identity.accountId().equals(accountId))
                    .toList();
        }
    }

    private static final class InMemoryCredentialRepository implements CredentialRepository {

        private final Map<UUID, Credential> values = new HashMap<>();

        @Override
        public void insert(Credential credential) {
            values.put(credential.credentialId(), credential);
        }

        @Override
        public Optional<Credential> findActive(UUID accountId, CredentialType type) {
            return values.values().stream()
                    .filter(credential -> credential.accountId().equals(accountId)
                            && credential.type() == type
                            && credential.status() == CredentialStatus.ACTIVE)
                    .findFirst();
        }

        @Override
        public int revokeActive(UUID accountId, CredentialType type, Instant rotatedAt) {
            return 0;
        }

        @Override
        public UUID nextUuidV7() {
            return UUID.randomUUID();
        }

        private List<Credential> values() {
            return values.values().stream().toList();
        }
    }

    private static final class InMemoryHumanProfileRepository implements HumanProfileRepository {

        private final Map<UUID, HumanProfile> values = new HashMap<>();

        @Override
        public Optional<HumanProfile> findByAccountId(UUID accountId) {
            return Optional.ofNullable(values.get(accountId));
        }

        @Override
        public void upsert(HumanProfile profile) {
            values.put(profile.accountId(), profile);
        }

        @Override
        public void update(HumanProfile profile) {
            values.put(profile.accountId(), profile);
        }

        private List<HumanProfile> values() {
            return values.values().stream().toList();
        }
    }
}
