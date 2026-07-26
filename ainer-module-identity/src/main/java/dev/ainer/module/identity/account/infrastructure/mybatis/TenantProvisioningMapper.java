package dev.ainer.module.identity.account.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TenantProvisioningMapper {

    int acquireProvisioningLock(@Param("lockKey") String lockKey);

    TenantProvisioningRequestRow selectByIdempotencyForUpdate(
            @Param("serviceId") String serviceId,
            @Param("idempotencyKey") String idempotencyKey);

    TenantProvisioningRequestRow selectByIdForUpdate(@Param("requestId") UUID requestId);

    TenantActivationGrantRow selectActivationGrantByIdForUpdate(
            @Param("grantId") UUID grantId);

    List<TenantProvisioningRequestRow> selectOpenReservationsForUpdate(
            @Param("tenantCode") String tenantCode,
            @Param("ownerUsername") String ownerUsername);

    ProvisioningUserRow selectUserByUsernameForUpdate(@Param("username") String username);

    ProvisioningUserRow selectUserBySubjectIdForUpdate(@Param("subjectId") UUID subjectId);

    int countTenantByCode(@Param("tenantCode") String tenantCode);

    UUID selectUuidV7();

    int insertRequest(TenantProvisioningRequestRow request);

    int insertActivationGrant(TenantActivationGrantRow grant);

    int insertNotification(
            @Param("notification") TenantProvisioningNotificationOutboxRow notification,
            @Param("availableAt") Instant availableAt);

    int markExpired(
            @Param("requestId") UUID requestId,
            @Param("version") long version,
            @Param("completedAt") Instant completedAt);

    int markActivated(
            @Param("requestId") UUID requestId,
            @Param("version") long version,
            @Param("completedAt") Instant completedAt);

    int markCancelled(
            @Param("requestId") UUID requestId,
            @Param("version") long version,
            @Param("completedAt") Instant completedAt);

    int markActivationGrantExpired(
            @Param("grantId") UUID grantId,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("lastAttemptAt") Instant lastAttemptAt);

    int expireActivationGrantByRequest(
            @Param("requestId") UUID requestId,
            @Param("lastAttemptAt") Instant lastAttemptAt);

    int cancelActivationGrantByRequest(
            @Param("requestId") UUID requestId,
            @Param("cancelledAt") Instant cancelledAt);

    int recordFailedActivationAttempt(
            @Param("grantId") UUID grantId,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("lastAttemptAt") Instant lastAttemptAt,
            @Param("lockGrant") boolean lockGrant);

    int markActivationGrantConsumed(
            @Param("grantId") UUID grantId,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("consumedAt") Instant consumedAt);

    int cancelPendingNotificationByRequest(@Param("requestId") UUID requestId);

    int insertAudit(TenantProvisioningAuditRow audit);
}
