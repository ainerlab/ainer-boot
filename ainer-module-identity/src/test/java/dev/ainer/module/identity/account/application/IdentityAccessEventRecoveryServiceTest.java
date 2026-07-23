package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.identity.account.domain.IdentityAccessEventType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityAccessEventRecoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-23T04:00:00Z");

    @Test
    void requiresDifferentApproverAndReplaysTheOriginalEventOnlyOnce() {
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        IdentityAccessEventOutboxEntry exhausted = entry(tenantId, eventId, 10);
        InMemoryRecoveryRepository repository = new InMemoryRecoveryRepository(exhausted);
        MutableClock clock = new MutableClock(NOW);
        IdentityAccessEventRecoveryService service = new IdentityAccessEventRecoveryService(repository, clock);

        IdentityAccessEventReplayRequest request = service.requestReplay(
                "operator:request", tenantId, eventId, "INC-2026-0042",
                Duration.ofMinutes(15), 10);

        assertThatThrownBy(() -> service.approveAndExecute(
                "operator:request", tenantId, request.id(), 10))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(IdentityErrorCode.REPLAY_APPROVER_MUST_DIFFER));

        IdentityAccessEventReplayRequest executed = service.approveAndExecute(
                "operator:approve", tenantId, request.id(), 10);

        assertThat(executed.status()).isEqualTo("EXECUTED");
        assertThat(repository.event)
                .extracting(
                        IdentityAccessEventOutboxEntry::eventId,
                        IdentityAccessEventOutboxEntry::eventType,
                        IdentityAccessEventOutboxEntry::tenantId,
                        IdentityAccessEventOutboxEntry::subjectId,
                        IdentityAccessEventOutboxEntry::payloadVersion,
                        IdentityAccessEventOutboxEntry::occurredAt)
                .containsExactly(
                        exhausted.eventId(), exhausted.eventType(), exhausted.tenantId(),
                        exhausted.subjectId(), exhausted.payloadVersion(), exhausted.occurredAt());
        assertThat(repository.event.attemptCount()).isZero();
        assertThat(repository.event.publicationStatus()).isEqualTo("PENDING");
        assertThat(repository.auditPhases).containsExactly("REQUESTED", "EXECUTED");
        assertThatThrownBy(() -> service.approveAndExecute(
                "operator:other", tenantId, request.id(), 10))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(IdentityErrorCode.REPLAY_REQUEST_CONFLICT));
    }

    @Test
    void rejectsNonExhaustedWrongTenantAndExpiredRequests() {
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        InMemoryRecoveryRepository repository = new InMemoryRecoveryRepository(entry(tenantId, eventId, 9));
        MutableClock clock = new MutableClock(NOW);
        IdentityAccessEventRecoveryService service = new IdentityAccessEventRecoveryService(repository, clock);

        assertThatThrownBy(() -> service.requestReplay(
                "operator:request", tenantId, eventId, "INC-1", Duration.ofMinutes(1), 10))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(IdentityErrorCode.ACCESS_EVENT_NOT_EXHAUSTED));

        repository.event = entry(tenantId, eventId, 10);
        IdentityAccessEventReplayRequest request = service.requestReplay(
                "operator:request", tenantId, eventId, "INC-2", Duration.ofMinutes(1), 10);
        assertThatThrownBy(() -> service.approveAndExecute(
                "operator:approve", UUID.randomUUID(), request.id(), 10))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(IdentityErrorCode.REPLAY_REQUEST_NOT_FOUND));

        clock.instant = NOW.plus(Duration.ofMinutes(1));
        assertThatThrownBy(() -> service.approveAndExecute(
                "operator:approve", tenantId, request.id(), 10))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(IdentityErrorCode.REPLAY_REQUEST_EXPIRED));
        assertThat(repository.event.attemptCount()).isEqualTo(10);
    }

    private static IdentityAccessEventOutboxEntry entry(UUID tenantId, UUID eventId, int attempts) {
        return new IdentityAccessEventOutboxEntry(
                eventId,
                IdentityAccessEventType.IDENTITY_USER_DISABLED,
                tenantId,
                UUID.randomUUID(),
                1,
                NOW.minusSeconds(300),
                "FAILED",
                attempts,
                NOW.minusSeconds(60),
                null,
                "AINER.IDENTITY.TEST_FAILURE");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class InMemoryRecoveryRepository
            implements IdentityAccessEventRecoveryRepository {
        private IdentityAccessEventOutboxEntry event;
        private IdentityAccessEventReplayRequest request;
        private final List<String> auditPhases = new ArrayList<>();

        private InMemoryRecoveryRepository(IdentityAccessEventOutboxEntry event) {
            this.event = event;
        }

        @Override
        public IdentityAccessEventOutboxPage findExhausted(
                UUID tenantId, Instant now, int maxAttempts, int page, int size, long offset) {
            List<IdentityAccessEventOutboxEntry> items = isExhausted(tenantId, event.eventId(), now, maxAttempts)
                    ? List.of(event) : List.of();
            return new IdentityAccessEventOutboxPage(items, page, size, items.size());
        }

        @Override
        public Optional<IdentityAccessEventOutboxEntry> findExhaustedForUpdate(
                UUID tenantId, UUID eventId, Instant now, int maxAttempts) {
            return isExhausted(tenantId, eventId, now, maxAttempts)
                    ? Optional.of(event) : Optional.empty();
        }

        @Override
        public void expireOpenRequests(UUID eventId, Instant now) {
            if (request != null && request.eventId().equals(eventId)
                    && "REQUESTED".equals(request.status()) && !now.isBefore(request.expiresAt())) {
                request = new IdentityAccessEventReplayRequest(
                        request.id(), request.eventId(), request.tenantId(), request.requestedBy(),
                        null, request.incidentReference(), "EXPIRED", request.requestedAt(),
                        request.expiresAt(), null);
            }
        }

        @Override
        public void insertReplayRequest(IdentityAccessEventReplayRequest request) {
            this.request = request;
        }

        @Override
        public Optional<IdentityAccessEventReplayRequest> findReplayRequestForUpdate(
                UUID tenantId, UUID requestId) {
            return request != null && request.id().equals(requestId) && request.tenantId().equals(tenantId)
                    ? Optional.of(request) : Optional.empty();
        }

        @Override
        public boolean resetExhaustedEvent(UUID tenantId, UUID eventId, Instant now, int maxAttempts) {
            if (!isExhausted(tenantId, eventId, now, maxAttempts)) {
                return false;
            }
            event = new IdentityAccessEventOutboxEntry(
                    event.eventId(), event.eventType(), event.tenantId(), event.subjectId(),
                    event.payloadVersion(), event.occurredAt(), "PENDING", 0, now,
                    null, null);
            return true;
        }

        @Override
        public boolean markReplayExecuted(UUID requestId, String approvedBy, Instant executedAt) {
            if (request == null || !request.id().equals(requestId) || !"REQUESTED".equals(request.status())) {
                return false;
            }
            request = new IdentityAccessEventReplayRequest(
                    request.id(), request.eventId(), request.tenantId(), request.requestedBy(), approvedBy,
                    request.incidentReference(), "EXECUTED", request.requestedAt(),
                    request.expiresAt(), executedAt);
            return true;
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
            auditPhases.add(phase);
        }

        private boolean isExhausted(UUID tenantId, UUID eventId, Instant now, int maxAttempts) {
            return event.tenantId().equals(tenantId)
                    && event.eventId().equals(eventId)
                    && event.attemptCount() >= maxAttempts
                    && (event.leaseUntil() == null || !event.leaseUntil().isAfter(now))
                    && ("PENDING".equals(event.publicationStatus())
                    || "FAILED".equals(event.publicationStatus()));
        }
    }
}
