package dev.ainer.module.identity.foundation;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.identity.account.application.IdentityErrorCode;
import dev.ainer.security.principal.IdentityAuthorityRef;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Application core for the Greenfield Identity model (ADR-0033 Greenfield §3-§4, S1.2 spine, S2
 * credential store).
 *
 * <p>Exercises {@link HumanAccount} + {@link LoginIdentity} + {@link Credential} + {@link HumanProfile}
 * end-to-end via the foundation repository ports. This is the working registration core that the destructive
 * cutover wires into the Authorization Server; it is deliberately decoupled from the legacy tenant-bound
 * services and does not touch them.
 *
 * <p>Collision and state failures throw {@link BusinessException} with dedicated
 * {@link IdentityErrorCode foundation error codes}. Identifier equality never auto-merges accounts — a
 * duplicate {@code (type, providerAuthority, normalizedIdentifier)} is a hard conflict. Credential material
 * is encoded with the project's delegating {@link PasswordEncoder} before it reaches the store; the
 * ACTIVE-only uniqueness of {@code (account, type)} is enforced by the credential store.
 *
 * <p>Not annotated {@code @Service}: the {@code Supplier<UUID>} id source is bound to the foundation
 * repository's {@code nextUuidV7()} in {@code IdentityModuleConfiguration}, so the bean is declared explicitly
 * there rather than auto-wired with an ambiguous {@code Supplier}.
 */
public class IdentityFoundationService {

    private final HumanAccountRepository accountRepository;
    private final LoginIdentityRepository loginIdentityRepository;
    private final CredentialRepository credentialRepository;
    private final HumanProfileRepository humanProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Supplier<UUID> idSource;

    public IdentityFoundationService(
            HumanAccountRepository accountRepository,
            LoginIdentityRepository loginIdentityRepository,
            CredentialRepository credentialRepository,
            HumanProfileRepository humanProfileRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            Supplier<UUID> idSource) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.loginIdentityRepository = Objects.requireNonNull(loginIdentityRepository, "loginIdentityRepository");
        this.credentialRepository = Objects.requireNonNull(credentialRepository, "credentialRepository");
        this.humanProfileRepository = Objects.requireNonNull(humanProfileRepository, "humanProfileRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
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

    /**
     * Register a new HumanAccount with its first verified LoginIdentity and an ACTIVE password credential.
     * Fails closed if the binding already exists, and encodes the raw password with the delegating
     * {@link PasswordEncoder} before it reaches the store. Returns a read-only projection for the caller.
     */
    public RegisteredAccount registerHumanAccountWithPassword(
            IdentityAuthorityRef authority,
            LoginIdentityType type,
            String providerAuthority,
            String normalizedIdentifier,
            String rawPassword) {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(type, "type");
        requireNonBlank(providerAuthority, "providerAuthority");
        requireNonBlank(normalizedIdentifier, "normalizedIdentifier");
        requireNonNullNonBlankRawPassword(rawPassword);

        RegisteredAccount registered = registerHumanAccount(authority, type, providerAuthority, normalizedIdentifier);
        storePasswordCredential(registered.account(), rawPassword);
        return registered;
    }

    /**
     * Resolve the ACTIVE password credential for an account via its LoginIdentity, together with the owning
     * account. Empty when the binding or the credential is missing or revoked — the caller decides how to
     * surface that as an authentication failure.
     */
    public Optional<CredentialLookup> findPasswordCredentialForLogin(
            LoginIdentityType type,
            String providerAuthority,
            String normalizedIdentifier) {
        return findLogin(type, providerAuthority, normalizedIdentifier)
                .filter(LoginIdentity::isActive)
                .flatMap(login -> accountRepository.findByAccountId(login.accountId()))
                .filter(account -> account.status().canAuthenticate())
                .flatMap(account -> credentialRepository.findActive(account.accountId(), CredentialType.PASSWORD)
                        .map(credential -> new CredentialLookup(account, credential)));
    }

    /**
     * Rotate the ACTIVE password credential for an existing account: revokes the current material and stores
     * a fresh ACTIVE one. Fails closed for unknown accounts, accounts that cannot authenticate, and accounts
     * that carry no ACTIVE password credential to rotate.
     */
    public Credential rotatePassword(UUID accountId, String rawPassword) {
        Objects.requireNonNull(accountId, "accountId");
        requireNonNullRawPassword(rawPassword);

        HumanAccount account = requireAuthenticatable(accountId);
        if (credentialRepository.findActive(accountId, CredentialType.PASSWORD).isEmpty()) {
            throw new BusinessException(IdentityErrorCode.CREDENTIAL_NOT_FOUND);
        }
        return storePasswordCredential(account, rawPassword);
    }

    /**
     * Upsert the display profile of an account. Fails closed for unknown accounts.
     */
    public HumanProfile updateProfile(UUID accountId, String displayName, String avatarUrl) {
        Objects.requireNonNull(accountId, "accountId");
        requireAccount(accountId);

        Instant now = clock.instant();
        HumanProfile profile = new HumanProfile(accountId, displayName, avatarUrl, now);
        humanProfileRepository.upsert(profile);
        return profile;
    }

    private void requireNoCollision(
            LoginIdentityType type, String providerAuthority, String normalizedIdentifier) {
        if (loginIdentityRepository.findByTypeAndIdentifier(type, providerAuthority, normalizedIdentifier)
                .isPresent()) {
            throw new BusinessException(IdentityErrorCode.LOGIN_IDENTITY_ALREADY_EXISTS);
        }
    }

    private HumanAccount requireAccount(UUID accountId) {
        return accountRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.HUMAN_ACCOUNT_NOT_FOUND));
    }

    private HumanAccount requireAuthenticatable(UUID accountId) {
        HumanAccount account = requireAccount(accountId);
        if (!account.status().canAuthenticate()) {
            throw new BusinessException(IdentityErrorCode.HUMAN_ACCOUNT_NOT_ACTIVE);
        }
        return account;
    }

    private Credential storePasswordCredential(HumanAccount account, String rawPassword) {
        credentialRepository.revokeActive(account.accountId(), CredentialType.PASSWORD, clock.instant());
        Instant now = clock.instant();
        Credential credential = new Credential(
                idSource.get(),
                account.accountId(),
                CredentialType.PASSWORD,
                passwordEncoder.encode(rawPassword),
                CredentialStatus.ACTIVE,
                now,
                null);
        credentialRepository.insert(credential);
        return credential;
    }

    private static void requireNonNullRawPassword(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword");
    }

    private static void requireNonNullNonBlankRawPassword(String rawPassword) {
        requireNonNullRawPassword(rawPassword);
        if (rawPassword.isBlank()) {
            throw new IllegalArgumentException("rawPassword must be non-blank");
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

    /**
     * An ACTIVE password credential resolved for an account, used by the authentication path.
     */
    public record CredentialLookup(HumanAccount account, Credential credential) {
        public CredentialLookup {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(credential, "credential");
        }
    }
}
