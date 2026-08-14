package dev.ainer.module.notification.notification.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only change audit for notification template management (ADR-0040). Delivery itself is
 * audited by the {@code ainer_notification_record} facts; this table records who changed which
 * template, in the same transaction as the mutation.
 */
public record NotificationAudit(
        UUID id,
        String operation,
        UUID templateId,
        String actorIssuer,
        String actorType,
        String actorId,
        @Nullable String requestId,
        Instant occurredAt) {

    public static final String OPERATION_TEMPLATE_CREATED = "TEMPLATE_CREATED";
    public static final String OPERATION_TEMPLATE_UPDATED = "TEMPLATE_UPDATED";
    public static final String OPERATION_TEMPLATE_STATUS_CHANGED = "TEMPLATE_STATUS_CHANGED";

    public NotificationAudit {
        Objects.requireNonNull(id, "id");
        operation = requireText(operation, "operation");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(actorIssuer, "actorIssuer");
        Objects.requireNonNull(actorId, "actorId");
        if (!"USER".equals(actorType) && !"SERVICE".equals(actorType)) {
            throw new IllegalArgumentException("actorType must be USER or SERVICE");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return stripped;
    }
}
