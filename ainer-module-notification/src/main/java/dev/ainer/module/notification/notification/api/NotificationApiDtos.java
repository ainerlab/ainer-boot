package dev.ainer.module.notification.notification.api;

import dev.ainer.module.notification.notification.application.NotificationPageSlice;
import dev.ainer.module.notification.notification.domain.NotificationRecord;
import dev.ainer.module.notification.notification.domain.NotificationTemplate;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 通知管理面的 API 模型（ADR-0040）。 */
public final class NotificationApiDtos {

    private NotificationApiDtos() {
    }

    public record CreateTemplateRequest(
            String code,
            String channel,
            String titleTemplate,
            String bodyTemplate,
            @Nullable Map<String, Object> variablesSchema) {
    }

    public record UpdateTemplateRequest(
            @Nullable String titleTemplate,
            @Nullable String bodyTemplate,
            @Nullable Map<String, Object> variablesSchema,
            long expectedVersion) {
    }

    public record StatusChangeRequest(String status, long expectedVersion) {
    }

    public record SubmitDirectRequest(
            String channel,
            String recipient,
            String title,
            String body,
            @Nullable Map<String, Object> payload) {
    }

    public record NotificationTemplateResponse(
            UUID id,
            String code,
            String channel,
            String titleTemplate,
            String bodyTemplate,
            Map<String, Object> variablesSchema,
            String status,
            long version) {

        public static NotificationTemplateResponse from(NotificationTemplate template) {
            return new NotificationTemplateResponse(
                    template.id(), template.code(), template.channel().name(),
                    template.titleTemplate(), template.bodyTemplate(), template.variablesSchema(),
                    template.status().name(), template.version());
        }
    }

    public record NotificationTemplatePageResponse(
            List<NotificationTemplateResponse> items, int page, int size, long total) {

        public static NotificationTemplatePageResponse from(
                NotificationPageSlice<NotificationTemplate> slice, int page, int size) {
            return new NotificationTemplatePageResponse(
                    slice.items().stream().map(NotificationTemplateResponse::from).toList(),
                    page, size, slice.total());
        }
    }

    /**
     * 面向运维的投递记录投影。省略已渲染消息的 title/body——
     * 收件人与内容属于 PII，不进入列表。
     */
    public record NotificationRecordResponse(
            UUID id,
            @Nullable String templateCode,
            String channel,
            String recipient,
            String status,
            int retryCount,
            int maxRetries,
            @Nullable Instant nextRetryAt,
            @Nullable String errorMessage,
            @Nullable Instant sentAt,
            Instant createdAt) {

        public static NotificationRecordResponse from(NotificationRecord record) {
            return new NotificationRecordResponse(
                    record.id(), record.templateCode(), record.channel().name(),
                    record.recipient(), record.status().name(), record.retryCount(),
                    record.maxRetries(), record.nextRetryAt(), record.errorMessage(),
                    record.sentAt(), record.createdAt());
        }
    }

    public record NotificationRecordPageResponse(
            List<NotificationRecordResponse> items, int page, int size, long total) {

        public static NotificationRecordPageResponse from(
                NotificationPageSlice<NotificationRecord> slice, int page, int size) {
            return new NotificationRecordPageResponse(
                    slice.items().stream().map(NotificationRecordResponse::from).toList(),
                    page, size, slice.total());
        }
    }
}
