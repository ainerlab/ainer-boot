package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import dev.ainer.module.identity.account.domain.IdentityTenant;
import dev.ainer.module.identity.account.domain.IdentityUser;
import dev.ainer.module.identity.account.domain.TenantMembership;
import dev.ainer.module.identity.account.domain.TenantRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Service
public class IdentityApplicationService {

    private final IdentityRepository repository;
    private final PasswordHashingPort passwordHashingPort;
    private final Clock clock;

    public IdentityApplicationService(
            IdentityRepository repository,
            PasswordHashingPort passwordHashingPort,
            Clock clock) {
        this.repository = repository;
        this.passwordHashingPort = passwordHashingPort;
        this.clock = clock;
    }

    @Transactional
    public ProvisionedIdentity provisionTenantOwner(ProvisionTenantOwnerCommand command) {
        try {
            Objects.requireNonNull(command, "command");
            String tenantCode = normalize(command.tenantCode());
            String username = normalize(command.username());
            acquireProvisioningLocks(tenantCode, username);
            requireNoOpenProvisioningReservation(tenantCode, username);
            return provisionValidated(command, tenantCode, username);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(IdentityErrorCode.ALREADY_EXISTS);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(IdentityErrorCode.INVALID_PROVISIONING_REQUEST);
        }
    }

    /**
     * 启动期幂等引导。相同租户代码和用户名已经构成 ACTIVE 默认 OWNER 时安全返回；任何部分占用或
     * 状态漂移都失败，避免把“用户存在”误判为初始化成功。事务级 advisory lock 负责多实例并发串行化。
     */
    @Transactional
    public TenantOwnerBootstrapResult ensureTenantOwner(ProvisionTenantOwnerCommand command) {
        try {
            Objects.requireNonNull(command, "command");
            validateRawPassword(command.rawPassword());
            String tenantCode = normalize(command.tenantCode());
            String username = normalize(command.username());
            acquireProvisioningLocks(tenantCode, username);

            Optional<IdentityDirectoryEntry> existing =
                    repository.findActiveDefaultOwner(tenantCode, username);
            if (existing.isPresent()) {
                IdentityDirectoryEntry owner = existing.get();
                return new TenantOwnerBootstrapResult(
                        new ProvisionedIdentity(owner.tenantId(), owner.subjectId(), owner.username()),
                        false);
            }
            if (repository.tenantExistsByCode(tenantCode)
                    || repository.userExistsByUsername(username)
                    || repository.openProvisioningReservationExists(
                            tenantCode, username)) {
                throw new BusinessException(IdentityErrorCode.TENANT_BOOTSTRAP_STATE_CONFLICT);
            }
            return new TenantOwnerBootstrapResult(
                    provisionValidated(command, tenantCode, username),
                    true);
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(IdentityErrorCode.TENANT_BOOTSTRAP_STATE_CONFLICT);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(IdentityErrorCode.INVALID_PROVISIONING_REQUEST);
        }
    }

    @Transactional(readOnly = true)
    public Optional<IdentityAccount> findAccountByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return repository.findAccountByUsername(normalize(username));
    }

    /**
     * 列出指定主体当前所有 ACTIVE membership 安全投影（tenant/user/membership 均 ACTIVE）。
     *
     * <p>用于租户上下文选择与 {@code GET /api/me/tenants}；默认租户排在首位。
     */
    @Transactional(readOnly = true)
    public List<TenantContextEntry> findActiveMemberships(UUID subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        return repository.findActiveMembershipsBySubject(subjectId);
    }

    /**
     * 实时校验 (tenantId, subjectId) 是否构成 ACTIVE membership 并返回安全投影。
     *
     * <p>Token customizer 在签发人员 access token 前调用，确保 tenant claim 来自 Identity 实时
     * 关系而非登录时缓存的 principal。
     */
    @Transactional(readOnly = true)
    public Optional<IdentityDirectoryEntry> findActiveMembership(UUID tenantId, UUID subjectId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        return repository.findActiveDirectoryEntry(tenantId, subjectId);
    }

    private String normalize(String value) {
        return Objects.requireNonNull(value, "value").trim().toLowerCase(Locale.ROOT);
    }

    private ProvisionedIdentity provisionValidated(
            ProvisionTenantOwnerCommand command,
            String tenantCode,
            String username) {
        validateRawPassword(command.rawPassword());
        Instant now = clock.instant();
        UUID tenantId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        IdentityTenant tenant = new IdentityTenant(
                tenantId, tenantCode, command.tenantName(), IdentityStatus.ACTIVE, now, now);
        IdentityUser user = new IdentityUser(
                subjectId, username, passwordHashingPort.hash(command.rawPassword()), command.displayName(),
                IdentityStatus.ACTIVE, now, now);
        TenantMembership membership = new TenantMembership(
                tenantId, subjectId, TenantRole.OWNER, true, IdentityStatus.ACTIVE, now, now);

        repository.insertTenant(tenant);
        repository.insertUser(user);
        repository.insertMembership(membership);
        return new ProvisionedIdentity(tenantId, subjectId, username);
    }

    private void validateRawPassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new IllegalArgumentException("password length is invalid");
        }
    }

    private void acquireProvisioningLocks(String tenantCode, String username) {
        repository.acquireIdentityLock("identity:tenant-code:" + tenantCode);
        repository.acquireIdentityLock("identity:username:" + username);
    }

    private void requireNoOpenProvisioningReservation(
            String tenantCode,
            String username) {
        if (repository.openProvisioningReservationExists(tenantCode, username)) {
            throw new BusinessException(IdentityErrorCode.ALREADY_EXISTS);
        }
    }
}
