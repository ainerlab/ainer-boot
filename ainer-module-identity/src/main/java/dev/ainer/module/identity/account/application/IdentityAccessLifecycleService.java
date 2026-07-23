package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.identity.account.domain.IdentityAccessEvent;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import dev.ainer.module.identity.account.domain.TenantMembership;
import dev.ainer.module.identity.account.domain.TenantRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class IdentityAccessLifecycleService {

    private final IdentityRepository repository;
    private final Clock clock;

    public IdentityAccessLifecycleService(IdentityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public int disableUser(UUID subjectId) {
        if (subjectId == null) {
            throw new BusinessException(IdentityErrorCode.INVALID_IDENTITY_REFERENCE);
        }
        IdentityStatus currentStatus = repository.findUserStatusForUpdate(subjectId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ACCOUNT_NOT_FOUND));
        if (currentStatus == IdentityStatus.DISABLED) {
            return 0;
        }

        Instant now = clock.instant();
        List<UUID> activeTenantIds = repository.findActiveMembershipTenantIds(subjectId);
        if (!repository.updateUserStatus(subjectId, currentStatus, IdentityStatus.DISABLED, now)) {
            throw new BusinessException(IdentityErrorCode.ACCESS_STATE_CONFLICT);
        }
        activeTenantIds.forEach(tenantId -> repository.insertAccessEvent(
                IdentityAccessEvent.userDisabled(tenantId, subjectId, now)));
        return activeTenantIds.size();
    }

    @Transactional
    public boolean revokeMembership(UUID tenantId, UUID subjectId) {
        if (tenantId == null || subjectId == null) {
            throw new BusinessException(IdentityErrorCode.INVALID_IDENTITY_REFERENCE);
        }
        TenantMembership membership = repository.findMembershipForUpdate(tenantId, subjectId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.MEMBERSHIP_NOT_FOUND));
        if (membership.status() == IdentityStatus.DISABLED) {
            return false;
        }
        if (membership.role() == TenantRole.OWNER) {
            throw new BusinessException(IdentityErrorCode.OWNER_REVOCATION_REQUIRES_TRANSFER);
        }

        Instant now = clock.instant();
        if (!repository.updateMembershipStatus(
                tenantId, subjectId, membership.status(), IdentityStatus.DISABLED, now)) {
            throw new BusinessException(IdentityErrorCode.ACCESS_STATE_CONFLICT);
        }
        repository.insertAccessEvent(IdentityAccessEvent.membershipRevoked(tenantId, subjectId, now));
        return true;
    }
}
