package dev.ainer.module.identity.foundation;

import dev.ainer.core.error.BusinessException;
import dev.ainer.security.principal.IdentityAuthorityRef;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Greenfield Identity 模型的应用核心（ADR-0033 Greenfield §3-§4、S1.2 主干、S2 凭证存储）。
 *
 * <p>通过 foundation 仓库端口端到端驱动 {@link HumanAccount} + {@link LoginIdentity}
 * + {@link Credential} + {@link HumanProfile}。这是授权服务器使用的注册与认证核心，
 * 不会以副作用创建 Workspace 成员关系。
 *
 * <p>冲突与状态失败抛出携带专属 {@link IdentityErrorCode foundation 错误码} 的
 * {@link BusinessException}。标识符相等绝不自动合并账号——重复的
 * {@code (type, providerAuthority, normalizedIdentifier)} 是硬冲突。凭证材料在进入存储前
 * 先用项目的委托式 {@link PasswordEncoder} 编码；{@code (account, type)} 的
 * ACTIVE 唯一性由凭证存储强制。
 *
 * <p>未标注 {@code @Service}：{@code Supplier<UUID>} ID 来源在
 * {@code IdentityModuleConfiguration} 中绑定到 foundation 仓库的 {@code nextUuidV7()}，
 * 因此 bean 在那里显式声明，而不是注入语义含糊的 {@code Supplier}。
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
     * 注册一个新 HumanAccount 及其首个已验证 LoginIdentity。绑定已存在时失败关闭
     * （fail-closed）；绝不合并进既有账号。
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
     * 向既有 ACTIVE HumanAccount 附加一个额外的已验证 LoginIdentity。账号必须可认证；
     * 重复绑定是硬冲突。
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

    /** 把凭证解析到其绑定（如存在）。供认证路径使用（还需校验账号 epoch）。 */
    public Optional<LoginIdentity> findLogin(
            LoginIdentityType type,
            String providerAuthority,
            String normalizedIdentifier) {
        return loginIdentityRepository.findByTypeAndIdentifier(type, providerAuthority, normalizedIdentifier);
    }

    /**
     * 注册一个新 HumanAccount，包含首个已验证 LoginIdentity 与一份 ACTIVE 密码凭证。
     * 绑定已存在时失败关闭（fail-closed）；原始密码经委托式 {@link PasswordEncoder}
     * 编码后才进入存储。返回只读投影供调用方使用。
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
     * 经 LoginIdentity 解析账号的 ACTIVE 密码凭证，连同所属账号一起返回。绑定或凭证
     * 缺失、已吊销时返回 empty——由调用方决定如何把它呈现为认证失败。
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
     * 轮换既有账号的 ACTIVE 密码凭证：吊销当前材料并存入新的 ACTIVE 材料。对未知账号、
     * 不可认证的账号、没有可轮换 ACTIVE 密码凭证的账号一律失败关闭（fail-closed）。
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
     * upsert 账号的展示档案。未知账号时失败关闭（fail-closed）。
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
     * 注册 HumanAccount 的结果：新账号与其主 LoginIdentity。
     */
    public record RegisteredAccount(HumanAccount account, LoginIdentity primaryLogin) {
        public RegisteredAccount {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(primaryLogin, "primaryLogin");
        }
    }

    /**
     * 为账号解析出的 ACTIVE 密码凭证，供认证路径使用。
     */
    public record CredentialLookup(HumanAccount account, Credential credential) {
        public CredentialLookup {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(credential, "credential");
        }
    }
}
