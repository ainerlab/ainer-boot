package dev.ainer.module.notification.notification.domain;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Reusable notification template with JSONB variable schema (ADR-0038).
 *
 * @param id             database UUIDv7
 * @param code           unique template code (e.g. "welcome_email", "order_sms")
 * @param channel        target channel
 * @param titleTemplate  title with {@code {variable}} placeholders
 * @param bodyTemplate   body with {@code {variable}} placeholders
 * @param variablesSchema JSONB schema describing expected variables (for validation)
 * @param status         ACTIVE or DISABLED
 * @param version        optimistic-concurrency version
 */
public record NotificationTemplate(
        UUID id,
        String code,
        NotificationChannel channel,
        String titleTemplate,
        String bodyTemplate,
        Map<String, Object> variablesSchema,
        NotificationTemplateStatus status,
        long version) {

    public enum NotificationTemplateStatus { ACTIVE, DISABLED }

    public NotificationTemplate {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(titleTemplate, "titleTemplate");
        Objects.requireNonNull(bodyTemplate, "bodyTemplate");
        Objects.requireNonNull(status, "status");
        variablesSchema = variablesSchema == null ? Map.of() : Map.copyOf(variablesSchema);
    }
}
