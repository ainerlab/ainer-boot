package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class PlatformIdentityQueryService {

    private static final Pattern SAFE_IDENTIFIER =
            Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    private final PlatformIdentityQueryRepository repository;

    public PlatformIdentityQueryService(PlatformIdentityQueryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PlatformIdentityTenantPage tenants(
            PlatformProvisioningActor actor,
            int page,
            int size) {
        requireActor(actor);
        long offset = offset(page, size);
        return new PlatformIdentityTenantPage(
                repository.findTenants(offset, size),
                page,
                size,
                repository.countTenants());
    }

    @Transactional(readOnly = true)
    public PlatformIdentityUserPage users(
            PlatformProvisioningActor actor,
            int page,
            int size) {
        requireActor(actor);
        long offset = offset(page, size);
        return new PlatformIdentityUserPage(
                repository.findUsers(offset, size),
                page,
                size,
                repository.countUsers());
    }

    private long offset(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(IdentityErrorCode.INVALID_DIRECTORY_QUERY);
        }
        try {
            return Math.multiplyExact((long) page - 1, size);
        } catch (ArithmeticException exception) {
            throw new BusinessException(IdentityErrorCode.INVALID_DIRECTORY_QUERY);
        }
    }

    private void requireActor(PlatformProvisioningActor actor) {
        if (actor == null
                || actor.tenantId() != null
                || !safe(actor.serviceId())
                || !safe(actor.requestId())) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    private boolean safe(String value) {
        return value != null && SAFE_IDENTIFIER.matcher(value).matches();
    }
}
