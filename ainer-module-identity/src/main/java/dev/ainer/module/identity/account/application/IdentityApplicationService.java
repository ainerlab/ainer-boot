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
        Objects.requireNonNull(command, "command");
        try {
            validateRawPassword(command.rawPassword());
            Instant now = clock.instant();
            UUID tenantId = UUID.randomUUID();
            UUID subjectId = UUID.randomUUID();
            String username = normalize(command.username());
            IdentityTenant tenant = new IdentityTenant(
                    tenantId, normalize(command.tenantCode()), command.tenantName(), IdentityStatus.ACTIVE, now, now);
            IdentityUser user = new IdentityUser(
                    subjectId, username, passwordHashingPort.hash(command.rawPassword()), command.displayName(),
                    IdentityStatus.ACTIVE, now, now);
            TenantMembership membership = new TenantMembership(
                    tenantId, subjectId, TenantRole.OWNER, true, IdentityStatus.ACTIVE, now, now);

            repository.insertTenant(tenant);
            repository.insertUser(user);
            repository.insertMembership(membership);
            return new ProvisionedIdentity(tenantId, subjectId, username);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(IdentityErrorCode.ALREADY_EXISTS);
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

    private String normalize(String value) {
        return Objects.requireNonNull(value, "value").trim().toLowerCase(Locale.ROOT);
    }

    private void validateRawPassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new IllegalArgumentException("password length is invalid");
        }
    }
}
