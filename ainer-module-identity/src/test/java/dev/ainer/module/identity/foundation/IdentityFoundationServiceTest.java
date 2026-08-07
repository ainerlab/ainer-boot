package dev.ainer.module.identity.foundation;

import dev.ainer.core.error.BusinessException;
import dev.ainer.security.principal.IdentityAuthorityRef;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

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

/**
 * Exercises the Greenfield Identity registration core end-to-end without a database (S1.2 spine). Covers
 * account + primary login creation, the no-auto-merge collision invariant, multi-binding link and the
 * authority isolation mandated by ADR-0033 Greenfield §3-§5.
 */
class IdentityFoundationServiceTest {

    private static final IdentityAuthorityRef AINER =
            new IdentityAuthorityRef("https://ainer.example/auth");
    private static final IdentityAuthorityRef CORP =
            new IdentityAuthorityRef("https://corp.example");

    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final InMemoryLoginIdentityRepository logins = new InMemoryLoginIdentityRepository();
    private final InMemoryCredentialRepository credentials = new InMemoryCredentialRepository();
    private final InMemoryHumanProfileRepository profiles = new InMemoryHumanProfileRepository();
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final Clock clock =
            Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC);
    private final Supplier<UUID> ids = sequentialIds();

    private final IdentityFoundationService service =
            new IdentityFoundationService(accounts, logins, credentials, profiles, encoder, clock, ids);

    @Test
    void registerCreatesActiveAccountWithPrimaryLogin() {
        IdentityFoundationService.RegisteredAccount registered = service.registerHumanAccount(
                AINER, LoginIdentityType.EMAIL, AINER.issuer(), "creator@example.com");

        assertThat(registered.account().status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(registered.account().securityEpoch()).isZero();
        assertThat(registered.primaryLogin().accountId()).isEqualTo(registered.account().accountId());
        assertThat(registered.primaryLogin().isActive()).isTrue();
        assertThat(registered.primaryLogin().hasBeenUsed()).isFalse();

        assertThat(service.findLogin(LoginIdentityType.EMAIL, AINER.issuer(), "creator@example.com"))
                .contains(registered.primaryLogin());
    }

    @Test
    void registerRejectsDuplicateIdentifierWithoutMerging() {
        service.registerHumanAccount(AINER, LoginIdentityType.EMAIL, AINER.issuer(), "dup@example.com");

        assertThatThrownBy(() ->
                service.registerHumanAccount(AINER, LoginIdentityType.EMAIL, AINER.issuer(), "dup@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.LOGIN_IDENTITY_ALREADY_EXISTS);
        assertThat(accounts.count()).isEqualTo(1);
    }

    @Test
    void registerAcrossAuthoritiesDoesNotMerge() {
        var first = service.registerHumanAccount(AINER, LoginIdentityType.EMAIL, AINER.issuer(), "shared@example.com");
        var second = service.registerHumanAccount(CORP, LoginIdentityType.EMAIL, CORP.issuer(), "shared@example.com");

        assertThat(first.account().accountId()).isNotEqualTo(second.account().accountId());
        assertThat(service.findLogin(LoginIdentityType.EMAIL, CORP.issuer(), "shared@example.com"))
                .contains(second.primaryLogin());
        assertThat(accounts.count()).isEqualTo(2);
    }

    @Test
    void linkAddsSecondIdentityToActiveAccount() {
        var registered = service.registerHumanAccount(
                AINER, LoginIdentityType.EMAIL, AINER.issuer(), "owner@example.com");

        LoginIdentity linked = service.linkLoginIdentity(
                registered.account().accountId(),
                LoginIdentityType.PHONE, AINER.issuer(), "+86-10000000000");

        assertThat(linked.accountId()).isEqualTo(registered.account().accountId());
        List<LoginIdentity> bindings = logins.findByAccount(registered.account().accountId());
        assertThat(bindings).hasSize(2);
    }

    @Test
    void linkRejectsUnknownAccount() {
        assertThatThrownBy(() -> service.linkLoginIdentity(
                UUID.randomUUID(), LoginIdentityType.PHONE, AINER.issuer(), "+86-20000000000"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.HUMAN_ACCOUNT_NOT_FOUND);
    }

    @Test
    void linkRejectsDuplicateBinding() {
        var registered = service.registerHumanAccount(
                AINER, LoginIdentityType.EMAIL, AINER.issuer(), "owner@example.com");

        assertThatThrownBy(() -> service.linkLoginIdentity(
                registered.account().accountId(),
                LoginIdentityType.EMAIL, AINER.issuer(), "owner@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.LOGIN_IDENTITY_ALREADY_EXISTS);
    }

    @Test
    void registerWithPasswordStoresEncodedCredential() {
        var registered = service.registerHumanAccountWithPassword(
                AINER, LoginIdentityType.USERNAME, AINER.issuer(), "pw-user", "S3cret-pw-1");

        assertThat(registered.primaryLogin().isActive()).isTrue();
        Optional<IdentityFoundationService.CredentialLookup> lookup =
                service.findPasswordCredentialForLogin(
                        LoginIdentityType.USERNAME, AINER.issuer(), "pw-user");
        assertThat(lookup).isPresent();
        Credential credential = lookup.orElseThrow().credential();
        assertThat(credential.type()).isEqualTo(CredentialType.PASSWORD);
        assertThat(credential.isActive()).isTrue();
        assertThat(credential.credentialData())
                .startsWith("{bcrypt}")
                .isNotEqualTo("S3cret-pw-1");
        assertThat(encoder.matches("S3cret-pw-1", credential.credentialData())).isTrue();
        assertThat(lookup.orElseThrow().account().accountId()).isEqualTo(registered.account().accountId());
    }

    @Test
    void registerWithPasswordRejectsBlankPassword() {
        assertThatThrownBy(() -> service.registerHumanAccountWithPassword(
                AINER, LoginIdentityType.USERNAME, AINER.issuer(), "blank-pw", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findPasswordCredentialReturnsEmptyForMissingOrRevoked() {
        assertThat(service.findPasswordCredentialForLogin(
                LoginIdentityType.EMAIL, AINER.issuer(), "ghost@example.com")).isEmpty();

        var registered = service.registerHumanAccount(
                AINER, LoginIdentityType.EMAIL, AINER.issuer(), "no-pw@example.com");
        assertThat(service.findPasswordCredentialForLogin(
                LoginIdentityType.EMAIL, AINER.issuer(), "no-pw@example.com")).isEmpty();
        assertThat(registered.primaryLogin().isActive()).isTrue();
    }

    @Test
    void rotatePasswordRevokesOldAndIssuesFreshActiveMaterial() {
        var registered = service.registerHumanAccountWithPassword(
                AINER, LoginIdentityType.USERNAME, AINER.issuer(), "rotate-user", "old-password");
        Credential before = credentials.findByCredentialId(service
                .findPasswordCredentialForLogin(LoginIdentityType.USERNAME, AINER.issuer(), "rotate-user")
                .orElseThrow().credential().credentialId()).orElseThrow();

        Credential rotated = service.rotatePassword(registered.account().accountId(), "new-password");

        assertThat(rotated.isActive()).isTrue();
        assertThat(rotated.credentialId()).isNotEqualTo(before.credentialId());
        assertThat(credentials.findByCredentialId(before.credentialId()).orElseThrow().isActive()).isFalse();
        assertThat(credentials.findByCredentialId(before.credentialId()).orElseThrow().rotatedAt())
                .isEqualTo(clock.instant());
        Credential active = service.findPasswordCredentialForLogin(
                LoginIdentityType.USERNAME, AINER.issuer(), "rotate-user").orElseThrow().credential();
        assertThat(active.credentialId()).isEqualTo(rotated.credentialId());
        assertThat(encoder.matches("new-password", active.credentialData())).isTrue();
        assertThat(encoder.matches("old-password", active.credentialData())).isFalse();
    }

    @Test
    void rotatePasswordFailsClosedWithoutActiveCredential() {
        var registered = service.registerHumanAccount(
                AINER, LoginIdentityType.EMAIL, AINER.issuer(), "never-pw@example.com");

        assertThatThrownBy(() -> service.rotatePassword(registered.account().accountId(), "any-password"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.CREDENTIAL_NOT_FOUND);
    }

    @Test
    void updateProfileUpsertsProfileForExistingAccount() {
        var registered = service.registerHumanAccount(
                AINER, LoginIdentityType.EMAIL, AINER.issuer(), "profile@example.com");

        HumanProfile created = service.updateProfile(
                registered.account().accountId(), "Ainer User", "https://cdn.example/avatar.png");
        assertThat(created.displayName()).isEqualTo("Ainer User");
        assertThat(created.avatarUrl()).isEqualTo("https://cdn.example/avatar.png");
        assertThat(profiles.findByAccountId(registered.account().accountId())).contains(created);

        HumanProfile updated = service.updateProfile(
                registered.account().accountId(), "Renamed", null);
        assertThat(updated.displayName()).isEqualTo("Renamed");
        assertThat(updated.avatarUrl()).isNull();
    }

    @Test
    void updateProfileFailsClosedForUnknownAccount() {
        assertThatThrownBy(() -> service.updateProfile(
                UUID.randomUUID(), "Ghost", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.HUMAN_ACCOUNT_NOT_FOUND);
    }

    private static Supplier<UUID> sequentialIds() {
        AtomicLong counter = new AtomicLong(1);
        return () -> new UUID(0L, counter.getAndIncrement());
    }

    private static final class InMemoryAccountRepository implements HumanAccountRepository {
        private final Map<UUID, HumanAccount> store = new HashMap<>();

        @Override
        public void save(HumanAccount account) {
            store.put(account.accountId(), account);
        }

        @Override
        public Optional<HumanAccount> findByAccountId(UUID accountId) {
            return Optional.ofNullable(store.get(accountId));
        }

        @Override
        public UUID nextUuidV7() {
            return UUID.randomUUID();
        }

        int count() {
            return store.size();
        }
    }

    private static final class InMemoryLoginIdentityRepository implements LoginIdentityRepository {
        private final Map<UUID, LoginIdentity> store = new HashMap<>();

        @Override
        public void save(LoginIdentity identity) {
            store.put(identity.identityId(), identity);
        }

        @Override
        public Optional<LoginIdentity> findByTypeAndIdentifier(
                LoginIdentityType type, String providerAuthority, String normalizedIdentifier) {
            return store.values().stream()
                    .filter(l -> l.type() == type
                            && l.providerAuthority().equals(providerAuthority)
                            && l.normalizedIdentifier().equals(normalizedIdentifier)
                            && l.status() == LoginIdentityStatus.ACTIVE)
                    .findFirst();
        }

        @Override
        public List<LoginIdentity> findByAccount(UUID accountId) {
            return store.values().stream()
                    .filter(l -> l.accountId().equals(accountId))
                    .toList();
        }
    }

    private static final class InMemoryCredentialRepository implements CredentialRepository {
        private final Map<UUID, Credential> store = new HashMap<>();

        @Override
        public void insert(Credential credential) {
            store.put(credential.credentialId(), credential);
        }

        @Override
        public Optional<Credential> findActive(UUID accountId, CredentialType type) {
            return store.values().stream()
                    .filter(c -> c.accountId().equals(accountId)
                            && c.type() == type
                            && c.isActive())
                    .findFirst();
        }

        @Override
        public int revokeActive(UUID accountId, CredentialType type, java.time.Instant rotatedAt) {
            Optional<Credential> active = findActive(accountId, type);
            active.ifPresent(current -> store.put(current.credentialId(), new Credential(
                    current.credentialId(), current.accountId(), current.type(),
                    current.credentialData(), CredentialStatus.REVOKED,
                    current.createdAt(), rotatedAt)));
            return active.isPresent() ? 1 : 0;
        }

        @Override
        public UUID nextUuidV7() {
            return UUID.randomUUID();
        }

        Optional<Credential> findByCredentialId(UUID credentialId) {
            return Optional.ofNullable(store.get(credentialId));
        }
    }

    private static final class InMemoryHumanProfileRepository implements HumanProfileRepository {
        private final Map<UUID, HumanProfile> store = new HashMap<>();

        @Override
        public Optional<HumanProfile> findByAccountId(UUID accountId) {
            return Optional.ofNullable(store.get(accountId));
        }

        @Override
        public void upsert(HumanProfile profile) {
            store.put(profile.accountId(), profile);
        }

        @Override
        public void update(HumanProfile profile) {
            store.put(profile.accountId(), profile);
        }
    }
}
