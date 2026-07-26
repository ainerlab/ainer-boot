package dev.ainer.module.identity.account.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantProvisioningRepository {

    void acquireLocks(
            String serviceId,
            String idempotencyKey,
            String tenantCode,
            String ownerUsername);

    Optional<TenantProvisioningRequest> findByIdempotencyForUpdate(
            String serviceId,
            String idempotencyKey);

    Optional<TenantProvisioningRequest> findByIdForUpdate(UUID requestId);

    Optional<TenantActivationGrant> findActivationGrantByIdForUpdate(UUID grantId);

    List<TenantProvisioningRequest> findOpenReservationsForUpdate(
            String tenantCode,
            String ownerUsername);

    Optional<ProvisioningUserReference> findUserByUsernameForUpdate(String username);

    Optional<ProvisioningUserReference> findUserBySubjectIdForUpdate(UUID subjectId);

    boolean tenantExistsByCode(String tenantCode);

    UUID nextUuidV7();

    void insertRequest(TenantProvisioningRequest request);

    void insertActivationGrant(TenantActivationGrant grant);

    void insertNotification(
            TenantProvisioningNotificationOutboxEntry notification,
            Instant availableAt);

    boolean markExpired(UUID requestId, long version, Instant completedAt);

    boolean markActivated(UUID requestId, long version, Instant completedAt);

    boolean markCancelled(UUID requestId, long version, Instant completedAt);

    boolean markActivationGrantExpired(
            UUID grantId,
            int expectedAttemptCount,
            Instant lastAttemptAt);

    void expireActivationGrantByRequest(UUID requestId, Instant lastAttemptAt);

    boolean cancelActivationGrantByRequest(UUID requestId, Instant cancelledAt);

    boolean recordFailedActivationAttempt(
            UUID grantId,
            int expectedAttemptCount,
            Instant lastAttemptAt,
            boolean lockGrant);

    boolean markActivationGrantConsumed(
            UUID grantId,
            int expectedAttemptCount,
            Instant consumedAt);

    void cancelPendingNotificationByRequest(UUID requestId);

    void insertAudit(
            UUID operationId,
            UUID tenantId,
            UUID targetSubjectId,
            String phase,
            String actorType,
            String actorId,
            String requestId,
            String changeReference,
            Instant occurredAt);
}
