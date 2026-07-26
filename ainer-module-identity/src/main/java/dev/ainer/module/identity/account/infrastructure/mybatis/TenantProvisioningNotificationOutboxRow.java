package dev.ainer.module.identity.account.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class TenantProvisioningNotificationOutboxRow {

    private UUID id;
    private UUID provisioningRequestId;
    private UUID tenantId;
    private UUID subjectId;
    private String notificationType;
    private int templateVersion;
    private String payloadKeyVersion;
    private byte[] protectedPayload;
    private int attemptCount;
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProvisioningRequestId() {
        return provisioningRequestId;
    }

    public void setProvisioningRequestId(UUID provisioningRequestId) {
        this.provisioningRequestId = provisioningRequestId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public int getTemplateVersion() {
        return templateVersion;
    }

    public void setTemplateVersion(int templateVersion) {
        this.templateVersion = templateVersion;
    }

    public String getPayloadKeyVersion() {
        return payloadKeyVersion;
    }

    public void setPayloadKeyVersion(String payloadKeyVersion) {
        this.payloadKeyVersion = payloadKeyVersion;
    }

    public byte[] getProtectedPayload() {
        return protectedPayload == null ? null : protectedPayload.clone();
    }

    public void setProtectedPayload(byte[] protectedPayload) {
        this.protectedPayload =
                protectedPayload == null ? null : protectedPayload.clone();
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
