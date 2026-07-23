package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.domain.IdentityTenant;
import dev.ainer.module.identity.account.domain.IdentityAccessEvent;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import dev.ainer.module.identity.account.domain.IdentityUser;
import dev.ainer.module.identity.account.domain.TenantMembership;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentityRepository {

    void insertTenant(IdentityTenant tenant);

    void insertUser(IdentityUser user);

    void insertMembership(TenantMembership membership);

    Optional<IdentityAccount> findAccountByUsername(String normalizedUsername);

    Optional<IdentityDirectoryEntry> findActiveDirectoryEntry(UUID tenantId, UUID subjectId);

    List<IdentityDirectoryEntry> searchActiveDirectory(UUID tenantId, String likePattern, int limit);

    Optional<IdentityStatus> findUserStatusForUpdate(UUID subjectId);

    List<UUID> findActiveMembershipTenantIds(UUID subjectId);

    boolean updateUserStatus(
            UUID subjectId, IdentityStatus expectedStatus, IdentityStatus newStatus, Instant updatedAt);

    Optional<TenantMembership> findMembershipForUpdate(UUID tenantId, UUID subjectId);

    boolean updateMembershipStatus(
            UUID tenantId,
            UUID subjectId,
            IdentityStatus expectedStatus,
            IdentityStatus newStatus,
            Instant updatedAt);

    void insertAccessEvent(IdentityAccessEvent event);
}
