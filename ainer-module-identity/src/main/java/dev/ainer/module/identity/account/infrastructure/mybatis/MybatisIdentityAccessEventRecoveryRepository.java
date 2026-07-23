package dev.ainer.module.identity.account.infrastructure.mybatis;

import dev.ainer.module.identity.account.application.IdentityAccessEventOutboxEntry;
import dev.ainer.module.identity.account.application.IdentityAccessEventOutboxPage;
import dev.ainer.module.identity.account.application.IdentityAccessEventRecoveryRepository;
import dev.ainer.module.identity.account.application.IdentityAccessEventReplayRequest;
import dev.ainer.module.identity.account.application.IdentityErrorCode;
import dev.ainer.core.error.BusinessException;
import dev.ainer.module.identity.account.domain.IdentityAccessEventType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MybatisIdentityAccessEventRecoveryRepository
        implements IdentityAccessEventRecoveryRepository {

    private final IdentityAccessEventRecoveryMapper mapper;

    public MybatisIdentityAccessEventRecoveryRepository(IdentityAccessEventRecoveryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public IdentityAccessEventOutboxPage findExhausted(
            UUID tenantId, Instant now, int maxAttempts, int page, int size, long offset) {
        return new IdentityAccessEventOutboxPage(
                mapper.selectExhausted(tenantId, now, maxAttempts, size, offset)
                        .stream().map(this::toEntry).toList(),
                page,
                size,
                mapper.countExhausted(tenantId, now, maxAttempts));
    }

    @Override
    public Optional<IdentityAccessEventOutboxEntry> findExhaustedForUpdate(
            UUID tenantId, UUID eventId, Instant now, int maxAttempts) {
        return Optional.ofNullable(mapper.selectExhaustedForUpdate(
                tenantId, eventId, now, maxAttempts)).map(this::toEntry);
    }

    @Override
    public void expireOpenRequests(UUID eventId, Instant now) {
        mapper.expireOpenRequests(eventId, now);
    }

    @Override
    public void insertReplayRequest(IdentityAccessEventReplayRequest request) {
        IdentityAccessEventReplayRequestRow row = new IdentityAccessEventReplayRequestRow();
        row.setId(request.id());
        row.setEventId(request.eventId());
        row.setTenantId(request.tenantId());
        row.setRequestedBy(request.requestedBy());
        row.setApprovedBy(request.approvedBy());
        row.setIncidentReference(request.incidentReference());
        row.setStatus(request.status());
        row.setRequestedAt(request.requestedAt());
        row.setExpiresAt(request.expiresAt());
        row.setExecutedAt(request.executedAt());
        try {
            if (mapper.insertReplayRequest(row) != 1) {
                throw new IllegalStateException("Identity replay request insert affected an unexpected row count");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(IdentityErrorCode.REPLAY_REQUEST_CONFLICT);
        }
    }

    @Override
    public Optional<IdentityAccessEventReplayRequest> findReplayRequestForUpdate(
            UUID tenantId, UUID requestId) {
        return Optional.ofNullable(mapper.selectReplayRequestForUpdate(tenantId, requestId))
                .map(this::toRequest);
    }

    @Override
    public boolean resetExhaustedEvent(UUID tenantId, UUID eventId, Instant now, int maxAttempts) {
        return mapper.resetExhaustedEvent(tenantId, eventId, now, maxAttempts) == 1;
    }

    @Override
    public boolean markReplayExecuted(UUID requestId, String approvedBy, Instant executedAt) {
        return mapper.markReplayExecuted(requestId, approvedBy, executedAt) == 1;
    }

    @Override
    public void insertOperationAudit(
            UUID operationId,
            UUID tenantId,
            UUID targetId,
            String phase,
            String actorServiceId,
            String incidentReference,
            Instant occurredAt) {
        if (mapper.insertOperationAudit(
                UUID.randomUUID(), operationId, tenantId, targetId, phase,
                actorServiceId, incidentReference, occurredAt) != 1) {
            throw new IllegalStateException("Identity security operation audit insert failed");
        }
    }

    private IdentityAccessEventOutboxEntry toEntry(IdentityAccessEventRecoveryRow row) {
        return new IdentityAccessEventOutboxEntry(
                row.getId(), IdentityAccessEventType.valueOf(row.getEventType()),
                row.getTenantId(), row.getSubjectId(), row.getPayloadVersion(),
                row.getOccurredAt(), row.getPublicationStatus(), row.getAttemptCount(),
                row.getAvailableAt(), row.getLeaseUntil(), row.getLastErrorCode());
    }

    private IdentityAccessEventReplayRequest toRequest(IdentityAccessEventReplayRequestRow row) {
        return new IdentityAccessEventReplayRequest(
                row.getId(), row.getEventId(), row.getTenantId(), row.getRequestedBy(),
                row.getApprovedBy(), row.getIncidentReference(), row.getStatus(),
                row.getRequestedAt(), row.getExpiresAt(), row.getExecutedAt());
    }
}
