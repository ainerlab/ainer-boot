package dev.ainer.module.identity.foundation;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.identity.account.application.IdentityErrorCode;
import dev.ainer.security.principal.IdentityAuthorityRef;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Application core for the Greenfield Identity model (ADR-0033 Greenfield §3-§4, S1.2 spine).
 *
 * <p>Exercises {@link HumanAccount} + {@link LoginIdentity} end-to-end via the {@link HumanAccountRepository}
 * / {@link LoginIdentityRepository} ports. This is the working registration core that the destructive cutover
 * wires into the Authorization Server; it is deliberately decoupled from the legacy tenant-bound services and
 * does not touch them.
 *
 * <p>Collision and state failures throw {@link BusinessException} with dedicated
 * {@link IdentityErrorCode foundation error codes}. Identifier equality never auto-merges accounts — a
 * duplicate {@code (type, providerAuthority, normalizedIdentifier)} is a hard conflict.
 *
 * <p>Not annotated {@code @Service}: the {@code Supplier<UUID>} id source is bound to the foundation
 * repository's {@code nextUuidV7()} in {@code IdentityModuleConfiguration}, so the bean is declared explicitly
 * there rather than auto-wired with an ambiguous {@code Supplier}.
 */
public class IdentityFoundationService {

    private final HumanAccountRepository accountRepository;
    private final LoginIdentityRepository loginIdentityRepository;
    private final Clock clock;
    private final Supplier<UUID> idSource;

    public IdentityFoundationService(
            HumanAccountRepository accountRepository,
            LoginIdentityRepository loginIdentityRepository,
            Clock clock,
            Supplier<UUID> idSource) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.loginIdentityRepository = Objects.requireNonNull(loginIdentityRepository, "loginIdentityRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idSource = Objects.requireNonNull(idSource, "idSource");
    }

    /**
     * Register a new HumanAccount together with its first verified LoginIdentity. Fails closed if the
     * binding already exists; never merges into an existing account.
     */
    public RegisteredAccount registerHumanAccount(
            IdentityAuthorityRef authority,
            LoginIdentityType type,
            String providerAuthority,
            String normalizedIdentifier) {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(type, "type");
        requireNonBlank(providerAuthority, "providerAuthority");
        requireNonBlank(normalizedIdentifier, "normalizedIdentifier");
        requireNoCollision(type, providerAuthority, normalizedIdentifier);

        Instant now = clock.instant();
        UUID accountId = idSource.get();
        UUID loginId = idSource.get();
        HumanAccount account = new HumanAccount(accountId, authority, AccountStatus.ACTIVE, 0L, now);
        LoginIdentity login = new LoginIdentity(
                loginId, accountId, type, providerAuthority, normalizedIdentifier,
                LoginIdentityStatus.ACTIVE, now, now, null);
        accountRepository.save(account);
        loginIdentityRepository.save(login);
        return new RegisteredAccount(account, login);
    }

    /**
     * Attach an additional verified LoginIdentity to an existing ACTIVE HumanAccount. The account must be
     * authenticatable; a duplicate binding is a hard conflict.
     */
    public LoginIdentity linkLoginIdentity(
            UUID accountId,
            LoginIdentityType type,
            String providerAuthority,
            String normalizedIdentifier) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        requireNonBlank(providerAuthority, "providerAuthority");
        requireNonBlank(normalizedIdentifier, "normalizedIdentifier");

        HumanAccount account = accountRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.HUMAN_ACCOUNT_NOT_FOUND));
        if (!account.status().canAuthenticate()) {
            throw new BusinessException(IdentityErrorCode.HUMAN_ACCOUNT_NOT_ACTIVE);
        }
        requireNoCollision(type, providerAuthority, normalizedIdentifier);

        Instant now = clock.instant();
        LoginIdentity login = new LoginIdentity(
                idSource.get(), accountId, type, providerAuthority, normalizedIdentifier,
                LoginIdentityStatus.ACTIVE, now, now, null);
        loginIdentityRepository.save(login);
        return login;
    }

    /** Resolve a credential to its binding, if any. Used by the authentication path (subject to account epoch). */
    public Optional<LoginIdentity> findLogin(
            LoginIdentityType type,
            String providerAuthority,
            String normalizedIdentifier) {
        return loginIdentityRepository.findByTypeAndIdentifier(type, providerAuthority, normalizedIdentifier);
    }

    private void requireNoCollision(
            LoginIdentityType type, String providerAuthority, String normalizedIdentifier) {
        if (loginIdentityRepository.findByTypeAndIdentifier(type, providerAuthority, normalizedIdentifier)
                .isPresent()) {
            throw new BusinessException(IdentityErrorCode.LOGIN_IDENTITY_ALREADY_EXISTS);
        }
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
    }

    /**
     * Result of registering a HumanAccount: the new account together with its primary LoginIdentity.
     */
    public record RegisteredAccount(HumanAccount account, LoginIdentity primaryLogin) {
        public RegisteredAccount {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(primaryLogin, "primaryLogin");
        }
    }
}
