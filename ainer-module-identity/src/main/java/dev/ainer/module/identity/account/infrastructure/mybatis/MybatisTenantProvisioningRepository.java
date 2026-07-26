package dev.ainer.module.identity.account.infrastructure.mybatis;

import dev.ainer.module.identity.account.application.ProvisioningUserReference;
import dev.ainer.module.identity.account.application.TenantActivationGrant;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationOutboxEntry;
import dev.ainer.module.identity.account.application.TenantProvisioningRepository;
import dev.ainer.module.identity.account.application.TenantProvisioningRequest;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MybatisTenantProvisioningRepository implements TenantProvisioningRepository {

    private final TenantProvisioningMapper mapper;

    public MybatisTenantProvisioningRepository(TenantProvisioningMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void acquireLocks(
            String serviceId,
            String idempotencyKey,
            String tenantCode,
            String ownerUsername) {
        mapper.acquireProvisioningLock(
                "tenant-provisioning:idempotency:" + serviceId + '\u001f' + idempotencyKey);
        mapper.acquireProvisioningLock("identity:tenant-code:" + tenantCode);
        mapper.acquireProvisioningLock("identity:username:" + ownerUsername);
    }

    @Override
    public Optional<TenantProvisioningRequest> findByIdempotencyForUpdate(
            String serviceId,
            String idempotencyKey) {
        return Optional.ofNullable(
                        mapper.selectByIdempotencyForUpdate(serviceId, idempotencyKey))
                .map(this::toRequest);
    }

    @Override
    public Optional<TenantProvisioningRequest> findByIdForUpdate(UUID requestId) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(requestId)).map(this::toRequest);
    }

    @Override
    public Optional<TenantActivationGrant> findActivationGrantByIdForUpdate(UUID grantId) {
        return Optional.ofNullable(mapper.selectActivationGrantByIdForUpdate(grantId))
                .map(this::toGrant);
    }

    @Override
    public List<TenantProvisioningRequest> findOpenReservationsForUpdate(
            String tenantCode,
            String ownerUsername) {
        return mapper.selectOpenReservationsForUpdate(tenantCode, ownerUsername)
                .stream()
                .map(this::toRequest)
                .toList();
    }

    @Override
    public Optional<ProvisioningUserReference> findUserByUsernameForUpdate(String username) {
        return Optional.ofNullable(mapper.selectUserByUsernameForUpdate(username))
                .map(this::toUserReference);
    }

    @Override
    public Optional<ProvisioningUserReference> findUserBySubjectIdForUpdate(UUID subjectId) {
        return Optional.ofNullable(mapper.selectUserBySubjectIdForUpdate(subjectId))
                .map(this::toUserReference);
    }

    @Override
    public boolean tenantExistsByCode(String tenantCode) {
        return mapper.countTenantByCode(tenantCode) > 0;
    }

    @Override
    public UUID nextUuidV7() {
        return mapper.selectUuidV7();
    }

    @Override
    public void insertRequest(TenantProvisioningRequest request) {
        TenantProvisioningRequestRow row = new TenantProvisioningRequestRow();
        row.setId(request.id());
        row.setTenantId(request.tenantId());
        row.setTenantCode(request.tenantCode());
        row.setTenantName(request.tenantName());
        row.setOwnerSubjectId(request.ownerSubjectId());
        row.setOwnerUsername(request.ownerUsername());
        row.setOwnerDisplayName(request.ownerDisplayName());
        row.setOwnerUserExists(request.ownerUserExists());
        row.setStatus(request.status());
        row.setIdempotencyKey(request.idempotencyKey());
        row.setRequestFingerprint(request.requestFingerprint());
        row.setRequestedByServiceId(request.requestedByServiceId());
        row.setRequestId(request.requestId());
        row.setChangeReference(request.changeReference());
        row.setRequestedAt(request.requestedAt());
        row.setExpiresAt(request.expiresAt());
        row.setCompletedAt(request.completedAt());
        row.setVersion(request.version());
        requireSingleRow(mapper.insertRequest(row), "request");
    }

    @Override
    public void insertActivationGrant(TenantActivationGrant grant) {
        TenantActivationGrantRow row = new TenantActivationGrantRow();
        row.setId(grant.id());
        row.setProvisioningRequestId(grant.provisioningRequestId());
        row.setTenantId(grant.tenantId());
        row.setSubjectId(grant.subjectId());
        row.setSecretHash(grant.secretHash());
        row.setStatus(grant.status());
        row.setAttemptCount(grant.attemptCount());
        row.setMaxAttempts(grant.maxAttempts());
        row.setCreatedAt(grant.createdAt());
        row.setExpiresAt(grant.expiresAt());
        row.setLastAttemptAt(grant.lastAttemptAt());
        row.setConsumedAt(grant.consumedAt());
        requireSingleRow(mapper.insertActivationGrant(row), "activation grant");
    }

    @Override
    public void insertNotification(
            TenantProvisioningNotificationOutboxEntry notification,
            Instant availableAt) {
        TenantProvisioningNotificationOutboxRow row =
                new TenantProvisioningNotificationOutboxRow();
        row.setId(notification.id());
        row.setProvisioningRequestId(notification.provisioningRequestId());
        row.setTenantId(notification.tenantId());
        row.setSubjectId(notification.subjectId());
        row.setNotificationType(notification.type().name());
        row.setTemplateVersion(notification.templateVersion());
        row.setPayloadKeyVersion(notification.protectedNotification().keyVersion());
        row.setProtectedPayload(notification.protectedNotification().payload());
        row.setAttemptCount(notification.attemptCount());
        row.setCreatedAt(notification.createdAt());
        requireSingleRow(mapper.insertNotification(row, availableAt), "notification outbox");
    }

    @Override
    public boolean markExpired(UUID requestId, long version, Instant completedAt) {
        return mapper.markExpired(requestId, version, completedAt) == 1;
    }

    @Override
    public boolean markActivated(UUID requestId, long version, Instant completedAt) {
        return mapper.markActivated(requestId, version, completedAt) == 1;
    }

    @Override
    public boolean markCancelled(UUID requestId, long version, Instant completedAt) {
        return mapper.markCancelled(requestId, version, completedAt) == 1;
    }

    @Override
    public boolean markActivationGrantExpired(
            UUID grantId,
            int expectedAttemptCount,
            Instant lastAttemptAt) {
        return mapper.markActivationGrantExpired(
                grantId, expectedAttemptCount, lastAttemptAt) == 1;
    }

    @Override
    public void expireActivationGrantByRequest(UUID requestId, Instant lastAttemptAt) {
        mapper.expireActivationGrantByRequest(requestId, lastAttemptAt);
    }

    @Override
    public boolean cancelActivationGrantByRequest(UUID requestId, Instant cancelledAt) {
        return mapper.cancelActivationGrantByRequest(requestId, cancelledAt) == 1;
    }

    @Override
    public boolean recordFailedActivationAttempt(
            UUID grantId,
            int expectedAttemptCount,
            Instant lastAttemptAt,
            boolean lockGrant) {
        return mapper.recordFailedActivationAttempt(
                grantId, expectedAttemptCount, lastAttemptAt, lockGrant) == 1;
    }

    @Override
    public boolean markActivationGrantConsumed(
            UUID grantId,
            int expectedAttemptCount,
            Instant consumedAt) {
        return mapper.markActivationGrantConsumed(
                grantId, expectedAttemptCount, consumedAt) == 1;
    }

    @Override
    public void cancelPendingNotificationByRequest(UUID requestId) {
        mapper.cancelPendingNotificationByRequest(requestId);
    }

    @Override
    public void insertAudit(
            UUID operationId,
            UUID tenantId,
            UUID targetSubjectId,
            String phase,
            String actorType,
            String actorId,
            String requestId,
            String changeReference,
            Instant occurredAt) {
        TenantProvisioningAuditRow row = new TenantProvisioningAuditRow();
        row.setId(nextUuidV7());
        row.setOperationId(operationId);
        row.setTenantId(tenantId);
        row.setTargetSubjectId(targetSubjectId);
        row.setPhase(phase);
        row.setActorType(actorType);
        row.setActorId(actorId);
        row.setRequestId(requestId);
        row.setChangeReference(changeReference);
        row.setOccurredAt(occurredAt);
        requireSingleRow(mapper.insertAudit(row), "audit");
    }

    private TenantProvisioningRequest toRequest(TenantProvisioningRequestRow row) {
        return new TenantProvisioningRequest(
                row.getId(),
                row.getTenantId(),
                row.getTenantCode(),
                row.getTenantName(),
                row.getOwnerSubjectId(),
                row.getOwnerUsername(),
                row.getOwnerDisplayName(),
                row.isOwnerUserExists(),
                row.getStatus(),
                row.getIdempotencyKey(),
                row.getRequestFingerprint(),
                row.getRequestedByServiceId(),
                row.getRequestId(),
                row.getChangeReference(),
                row.getRequestedAt(),
                row.getExpiresAt(),
                row.getCompletedAt(),
                row.getVersion());
    }

    private TenantActivationGrant toGrant(TenantActivationGrantRow row) {
        return new TenantActivationGrant(
                row.getId(),
                row.getProvisioningRequestId(),
                row.getTenantId(),
                row.getSubjectId(),
                row.getSecretHash(),
                row.getStatus(),
                row.getAttemptCount(),
                row.getMaxAttempts(),
                row.getCreatedAt(),
                row.getExpiresAt(),
                row.getLastAttemptAt(),
                row.getConsumedAt());
    }

    private ProvisioningUserReference toUserReference(ProvisioningUserRow row) {
        return new ProvisioningUserReference(
                row.getSubjectId(),
                row.getUsername(),
                row.getDisplayName(),
                IdentityStatus.valueOf(row.getStatus()));
    }

    private void requireSingleRow(int affectedRows, String aggregate) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "Identity tenant provisioning %s insert affected an unexpected number of rows"
                            .formatted(aggregate));
        }
    }
}
