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

    Optional<IdentityAccount> findAccountBySubjectId(UUID subjectId);

    Optional<IdentityDirectoryEntry> findActiveDirectoryEntry(UUID tenantId, UUID subjectId);

    Optional<IdentityDirectoryEntry> findActiveDirectoryEntryForUpdate(UUID tenantId, UUID subjectId);

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

    List<IdentityDirectoryEntry> listMembersByTenant(UUID tenantId, int offset, int limit);

    int countMembersByTenant(UUID tenantId);

    boolean updateMembershipRole(UUID tenantId, UUID subjectId, String newRole, Instant updatedAt);

    boolean reactivateMembership(
            UUID tenantId,
            UUID subjectId,
            IdentityStatus expectedStatus,
            String newRole,
            Instant updatedAt);

    void insertMemberAudit(
            UUID tenantId, UUID actorSubjectId, UUID targetSubjectId,
            String operation, String role, String reasonCode, String requestId, Instant occurredAt);

    Optional<IdentityDirectoryEntry> findActiveDefaultOwner(String tenantCode, String normalizedUsername);

    boolean tenantExistsByCode(String tenantCode);

    boolean userExistsByUsername(String normalizedUsername);

    void acquireIdentityLock(String lockKey);

    boolean openProvisioningReservationExists(
            String tenantCode,
            String normalizedUsername);

    void insertAccessEvent(IdentityAccessEvent event);
}
