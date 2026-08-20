package dev.ainer.module.notification.notification.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 通知模板管理的只追加变更审计（ADR-0040）。投递本身由 {@code ainer_notification_record}
 * 事实记录审计；本表记录谁改了哪个模板，与业务变更同事务写入。
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
