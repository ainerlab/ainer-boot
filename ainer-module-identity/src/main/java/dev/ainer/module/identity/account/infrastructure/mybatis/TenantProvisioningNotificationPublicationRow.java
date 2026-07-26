package dev.ainer.module.identity.account.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class TenantProvisioningNotificationPublicationRow {

    private UUID notificationId;
    private String publicationStatus;
    private Instant publishedAt;

    public UUID getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(UUID notificationId) {
        this.notificationId = notificationId;
    }

    public String getPublicationStatus() {
        return publicationStatus;
    }

    public void setPublicationStatus(String publicationStatus) {
        this.publicationStatus = publicationStatus;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
