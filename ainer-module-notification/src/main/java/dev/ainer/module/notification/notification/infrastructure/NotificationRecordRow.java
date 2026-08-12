package dev.ainer.module.notification.notification.infrastructure;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/** Row mapping for {@code ainer_notification_record}. */
public class NotificationRecordRow {
    private UUID id;
    private @Nullable String templateCode;
    private String channel;
    private String recipient;
    private @Nullable String title;
    private @Nullable String body;
    private String payload;
    private String status;
    private int retryCount;
    private int maxRetries;
    private @Nullable Instant nextRetryAt;
    private @Nullable String errorMessage;
    private @Nullable Instant sentAt;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public @Nullable String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public @Nullable String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public @Nullable String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public @Nullable Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public @Nullable String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public @Nullable Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
