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

    List<TenantContextEntry> findActiveMembershipsBySubject(UUID subjectId);

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

    void insertOwnershipTransfer(OwnershipTransfer transfer);

    Optional<OwnershipTransfer> findOwnershipTransfer(UUID id);

    Optional<OwnershipTransfer> findOwnershipTransferForUpdate(UUID id);

    boolean completeOwnershipTransfer(
            UUID id, UUID tenantId, UUID executedBySubjectId,
            java.time.Instant executedAt, java.time.Instant updatedAt);

    boolean cancelOwnershipTransfer(UUID id, UUID tenantId, java.time.Instant updatedAt);

    void insertOwnershipRecovery(OwnershipRecovery recovery);

    java.util.Optional<OwnershipRecovery> findOwnershipRecovery(UUID id);

    java.util.Optional<OwnershipRecovery> findOwnershipRecoveryForUpdate(UUID id);

    boolean executeOwnershipRecovery(
            UUID id, UUID tenantId, String approverServiceId,
            java.time.Instant executedAt, java.time.Instant updatedAt);

    boolean cancelOwnershipRecovery(UUID id, UUID tenantId, java.time.Instant updatedAt);

    void insertSecurityOperationAudit(
            UUID operationId, UUID tenantId, UUID targetId, String operationType,
            String phase, String actorServiceId, String incidentReference, Instant occurredAt);
}
