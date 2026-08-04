package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TenantProvisioningNotificationReceiptService {

    private static final Duration FUTURE_CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Pattern SAFE_IDENTIFIER =
            Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");
    private static final Pattern FAILURE_CODE =
            Pattern.compile("[A-Z0-9][A-Z0-9._:-]{0,95}");

    private final TenantProvisioningNotificationReceiptRepository repository;
    private final Clock clock;

    public TenantProvisioningNotificationReceiptService(
            TenantProvisioningNotificationReceiptRepository repository,
            Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public TenantProvisioningNotificationReceiptResult record(
            TenantProvisioningNotificationReceiptCommand command,
            NotificationGatewayActor actor) {
        NormalizedReceipt normalized = normalize(command);
        requireActor(actor);
        Instant receivedAt = clock.instant();
        if (normalized.occurredAt().isAfter(receivedAt.plus(FUTURE_CLOCK_SKEW))) {
            throw new BusinessException(
                    IdentityErrorCode.INVALID_NOTIFICATION_RECEIPT);
        }

        repository.acquireLocks(
                actor.serviceId(),
                normalized.eventId(),
                normalized.notificationId());

        TenantProvisioningNotificationReceipt byEvent = repository
                .findByGatewayEvent(actor.serviceId(), normalized.eventId())
                .orElse(null);
        if (byEvent != null) {
            return replayOrConflict(byEvent, normalized, actor.serviceId(), true);
        }

        TenantProvisioningNotificationReceipt byNotification = repository
                .findByNotification(normalized.notificationId())
                .orElse(null);
        if (byNotification != null) {
            return replayOrConflict(
                    byNotification, normalized, actor.serviceId(), false);
        }

        TenantProvisioningNotificationPublication publication = repository
                .findPublicationForUpdate(normalized.notificationId())
                .orElseThrow(() -> new BusinessException(
                        IdentityErrorCode.NOTIFICATION_RECEIPT_NOT_FOUND));
        if (!"PUBLISHED".equals(publication.publicationStatus())
                || publication.publishedAt() == null) {
            throw new BusinessException(
                    IdentityErrorCode.NOTIFICATION_RECEIPT_STATE_CONFLICT);
        }

        TenantProvisioningNotificationReceipt receipt =
                new TenantProvisioningNotificationReceipt(
                        repository.nextUuidV7(),
                        normalized.notificationId(),
                        actor.serviceId(),
                        normalized.eventId(),
                        normalized.status(),
                        normalized.failureCode(),
                        normalized.occurredAt(),
                        receivedAt,
                        actor.requestId());
        try {
            repository.insert(receipt);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    IdentityErrorCode.NOTIFICATION_RECEIPT_IDEMPOTENCY_CONFLICT);
        }
        return new TenantProvisioningNotificationReceiptResult(receipt, true);
    }

    private TenantProvisioningNotificationReceiptResult replayOrConflict(
            TenantProvisioningNotificationReceipt receipt,
            NormalizedReceipt normalized,
            String gatewayClientId,
            boolean requireSameEvent) {
        boolean same = receipt.gatewayClientId().equals(gatewayClientId)
                && receipt.notificationId().equals(normalized.notificationId())
                && receipt.status() == normalized.status()
                && Objects.equals(receipt.failureCode(), normalized.failureCode())
                && receipt.occurredAt().equals(normalized.occurredAt())
                && (!requireSameEvent
                        || receipt.gatewayEventId().equals(normalized.eventId()));
        if (!same) {
            throw new BusinessException(
                    IdentityErrorCode.NOTIFICATION_RECEIPT_IDEMPOTENCY_CONFLICT);
        }
        return new TenantProvisioningNotificationReceiptResult(receipt, false);
    }

    private NormalizedReceipt normalize(
            TenantProvisioningNotificationReceiptCommand command) {
        if (command == null
                || !safe(command.eventId())
                || command.notificationId() == null
                || command.notificationId().version() != 7
                || command.status() == null
                || command.occurredAt() == null) {
            throw new BusinessException(
                    IdentityErrorCode.INVALID_NOTIFICATION_RECEIPT);
        }
        String failureCode = normalizeFailureCode(command.failureCode());
        if ((command.status()
                        == TenantProvisioningNotificationDeliveryStatus.DELIVERED
                    && failureCode != null)
                || (command.status()
                        == TenantProvisioningNotificationDeliveryStatus.FAILED
                    && failureCode == null)) {
            throw new BusinessException(
                    IdentityErrorCode.INVALID_NOTIFICATION_RECEIPT);
        }
        return new NormalizedReceipt(
                command.eventId(),
                command.notificationId(),
                command.status(),
                command.occurredAt().truncatedTo(ChronoUnit.MICROS),
                failureCode);
    }

    private String normalizeFailureCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!FAILURE_CODE.matcher(normalized).matches()) {
            throw new BusinessException(
                    IdentityErrorCode.INVALID_NOTIFICATION_RECEIPT);
        }
        return normalized;
    }

    private void requireActor(NotificationGatewayActor actor) {
        if (actor == null
                || actor.tenantId() != null
                || !safe(actor.serviceId())
                || !safe(actor.requestId())) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    private boolean safe(String value) {
        return value != null && SAFE_IDENTIFIER.matcher(value).matches();
    }

    private record NormalizedReceipt(
            String eventId,
            UUID notificationId,
            TenantProvisioningNotificationDeliveryStatus status,
            Instant occurredAt,
            String failureCode) {
    }
}
