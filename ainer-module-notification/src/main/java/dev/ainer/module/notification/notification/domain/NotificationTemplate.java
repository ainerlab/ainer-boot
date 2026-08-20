package dev.ainer.module.notification.notification.domain;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 带 JSONB 变量 schema 的可复用通知模板（ADR-0038）。
 *
 * @param id             数据库 UUIDv7
 * @param code           唯一模板编码（如 "welcome_email"、"order_sms"）
 * @param channel        目标渠道
 * @param titleTemplate  含 {@code {variable}} 占位符的标题
 * @param bodyTemplate   含 {@code {variable}} 占位符的正文
 * @param variablesSchema 描述预期变量的 JSONB schema（用于校验）
 * @param status         ACTIVE 或 DISABLED
 * @param version        乐观并发版本
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
