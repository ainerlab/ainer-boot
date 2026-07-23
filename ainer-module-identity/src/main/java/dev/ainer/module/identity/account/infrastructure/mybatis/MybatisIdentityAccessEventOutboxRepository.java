package dev.ainer.module.identity.account.infrastructure.mybatis;

import dev.ainer.module.identity.account.application.IdentityAccessEventDelivery;
import dev.ainer.module.identity.account.application.IdentityAccessEventOutboxRepository;
import dev.ainer.module.identity.account.application.IdentityAccessEventOutboxStatus;
import dev.ainer.module.identity.account.domain.IdentityAccessEvent;
import dev.ainer.module.identity.account.domain.IdentityAccessEventType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Repository
public class MybatisIdentityAccessEventOutboxRepository
        implements IdentityAccessEventOutboxRepository {

    private final IdentityAccessEventOutboxMapper mapper;

    public MybatisIdentityAccessEventOutboxRepository(IdentityAccessEventOutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<IdentityAccessEventDelivery> claimBatch(
            String leaseOwner,
            Instant now,
            Instant leaseUntil,
            int maxAttempts,
            int limit) {
        return mapper.claimBatch(leaseOwner, now, leaseUntil, maxAttempts, limit)
                .stream()
                .map(this::toDelivery)
                .sorted(Comparator
                        .comparing((IdentityAccessEventDelivery delivery) -> delivery.event().occurredAt())
                        .thenComparing(delivery -> delivery.event().id()))
                .toList();
    }

    @Override
    public boolean markPublished(UUID eventId, String leaseOwner, Instant publishedAt) {
        return mapper.markPublished(eventId, leaseOwner, publishedAt) == 1;
    }

    @Override
    public boolean markFailed(
            UUID eventId,
            String leaseOwner,
            Instant availableAt,
            String errorCode) {
        return mapper.markFailed(eventId, leaseOwner, availableAt, errorCode) == 1;
    }

    @Override
    public IdentityAccessEventOutboxStatus status(int maxAttempts) {
        IdentityAccessEventOutboxStatusRow row = mapper.selectStatus(maxAttempts);
        return new IdentityAccessEventOutboxStatus(
                row.getPending(), row.getFailed(), row.getExhausted(),
                row.getPublished(), row.getOldestReadyAt());
    }

    private IdentityAccessEventDelivery toDelivery(IdentityAccessEventRow row) {
        IdentityAccessEvent event = new IdentityAccessEvent(
                row.getId(),
                IdentityAccessEventType.valueOf(row.getEventType()),
                row.getTenantId(),
                row.getSubjectId(),
                row.getPayloadVersion(),
                row.getOccurredAt());
        return new IdentityAccessEventDelivery(event, row.getAttemptCount());
    }
}
