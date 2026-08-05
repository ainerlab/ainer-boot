package dev.ainer.module.identity.foundation;

import dev.ainer.security.principal.IdentityAuthorityRef;
import org.junit.jupiter.api.Test;

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
    private final Clock clock =
            Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC);
    private final Supplier<UUID> ids = sequentialIds();

    private final IdentityFoundationService service =
            new IdentityFoundationService(accounts, logins, clock, ids);

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
                .isInstanceOf(IllegalStateException.class);
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
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void linkRejectsDuplicateBinding() {
        var registered = service.registerHumanAccount(
                AINER, LoginIdentityType.EMAIL, AINER.issuer(), "owner@example.com");

        assertThatThrownBy(() -> service.linkLoginIdentity(
                registered.account().accountId(),
                LoginIdentityType.EMAIL, AINER.issuer(), "owner@example.com"))
                .isInstanceOf(IllegalStateException.class);
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
}
