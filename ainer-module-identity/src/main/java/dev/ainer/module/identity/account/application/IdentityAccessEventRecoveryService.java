package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class IdentityAccessEventRecoveryService {

    private static final Pattern SAFE_REFERENCE = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    private final IdentityAccessEventRecoveryRepository repository;
    private final Clock clock;

    public IdentityAccessEventRecoveryService(
            IdentityAccessEventRecoveryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public IdentityAccessEventOutboxPage findExhausted(
            UUID tenantId, int page, int size, int maxAttempts) {
        requirePage(page, size);
        requireMaxAttempts(maxAttempts);
        return repository.findExhausted(
                Objects.requireNonNull(tenantId, "tenantId"),
                clock.instant(),
                maxAttempts,
                page,
                size,
                Math.multiplyExact((long) page - 1, size));
    }

    @Transactional
    public IdentityAccessEventReplayRequest requestReplay(
            String requesterServiceId,
            UUID tenantId,
            UUID eventId,
            String incidentReference,
            Duration approvalTtl,
            int maxAttempts) {
        requesterServiceId = requireSafe(requesterServiceId, "requester service id");
        incidentReference = requireSafe(incidentReference, "incident reference");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(eventId, "eventId");
        requireSettings(approvalTtl, maxAttempts);
        Instant now = clock.instant();
        repository.expireOpenRequests(eventId, now);
        repository.findExhaustedForUpdate(tenantId, eventId, now, maxAttempts)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ACCESS_EVENT_NOT_EXHAUSTED));
        IdentityAccessEventReplayRequest request = new IdentityAccessEventReplayRequest(
                UUID.randomUUID(), eventId, tenantId, requesterServiceId, null,
                incidentReference, "REQUESTED", now, now.plus(approvalTtl), null);
        repository.insertReplayRequest(request);
        repository.insertOperationAudit(
                request.id(), tenantId, eventId, "REQUESTED", requesterServiceId,
                incidentReference, now);
        return request;
    }

    @Transactional
    public IdentityAccessEventReplayRequest approveAndExecute(
            String approverServiceId,
            UUID tenantId,
            UUID requestId,
            int maxAttempts) {
        approverServiceId = requireSafe(approverServiceId, "approver service id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(requestId, "requestId");
        requireMaxAttempts(maxAttempts);
        IdentityAccessEventReplayRequest request = repository
                .findReplayRequestForUpdate(tenantId, requestId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.REPLAY_REQUEST_NOT_FOUND));
        if (!"REQUESTED".equals(request.status())) {
            throw new BusinessException(IdentityErrorCode.REPLAY_REQUEST_CONFLICT);
        }
        if (request.requestedBy().equals(approverServiceId)) {
            throw new BusinessException(IdentityErrorCode.REPLAY_APPROVER_MUST_DIFFER);
        }
        Instant now = clock.instant();
        if (!now.isBefore(request.expiresAt())) {
            throw new BusinessException(IdentityErrorCode.REPLAY_REQUEST_EXPIRED);
        }
        if (!repository.resetExhaustedEvent(tenantId, request.eventId(), now, maxAttempts)
                || !repository.markReplayExecuted(request.id(), approverServiceId, now)) {
            throw new BusinessException(IdentityErrorCode.REPLAY_REQUEST_CONFLICT);
        }
        repository.insertOperationAudit(
                request.id(), tenantId, request.eventId(), "EXECUTED", approverServiceId,
                request.incidentReference(), now);
        return new IdentityAccessEventReplayRequest(
                request.id(), request.eventId(), request.tenantId(), request.requestedBy(),
                approverServiceId, request.incidentReference(), "EXECUTED",
                request.requestedAt(), request.expiresAt(), now);
    }

    private void requirePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(IdentityErrorCode.INVALID_RECOVERY_REQUEST);
        }
    }

    private void requireSettings(Duration approvalTtl, int maxAttempts) {
        if (approvalTtl == null || !approvalTtl.isPositive() || approvalTtl.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("Identity replay approval TTL is invalid");
        }
        requireMaxAttempts(maxAttempts);
    }

    private void requireMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Identity replay maximum attempts must be positive");
        }
    }

    private String requireSafe(String value, String name) {
        value = Objects.requireNonNull(value, name).trim();
        if (!SAFE_REFERENCE.matcher(value).matches()) {
            throw new BusinessException(IdentityErrorCode.INVALID_RECOVERY_REQUEST);
        }
        return value;
    }
}
