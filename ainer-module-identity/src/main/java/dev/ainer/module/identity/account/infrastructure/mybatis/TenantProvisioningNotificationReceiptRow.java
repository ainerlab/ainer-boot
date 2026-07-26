package dev.ainer.module.identity.account.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class TenantProvisioningNotificationReceiptRow {

    private UUID id;
    private UUID notificationId;
    private String gatewayClientId;
    private String gatewayEventId;
    private String deliveryStatus;
    private String failureCode;
    private Instant occurredAt;
    private Instant receivedAt;
    private String requestId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(UUID notificationId) {
        this.notificationId = notificationId;
    }

    public String getGatewayClientId() {
        return gatewayClientId;
    }

    public void setGatewayClientId(String gatewayClientId) {
        this.gatewayClientId = gatewayClientId;
    }

    public String getGatewayEventId() {
        return gatewayEventId;
    }

    public void setGatewayEventId(String gatewayEventId) {
        this.gatewayEventId = gatewayEventId;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
