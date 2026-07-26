package dev.ainer.module.identity.account.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class TenantProvisioningRequestRow {

    private UUID id;
    private UUID tenantId;
    private String tenantCode;
    private String tenantName;
    private UUID ownerSubjectId;
    private String ownerUsername;
    private String ownerDisplayName;
    private boolean ownerUserExists;
    private String status;
    private String idempotencyKey;
    private String requestFingerprint;
    private String requestedByServiceId;
    private String requestId;
    private String changeReference;
    private Instant requestedAt;
    private Instant expiresAt;
    private Instant completedAt;
    private long version;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public UUID getOwnerSubjectId() {
        return ownerSubjectId;
    }

    public void setOwnerSubjectId(UUID ownerSubjectId) {
        this.ownerSubjectId = ownerSubjectId;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public String getOwnerDisplayName() {
        return ownerDisplayName;
    }

    public void setOwnerDisplayName(String ownerDisplayName) {
        this.ownerDisplayName = ownerDisplayName;
    }

    public boolean isOwnerUserExists() {
        return ownerUserExists;
    }

    public void setOwnerUserExists(boolean ownerUserExists) {
        this.ownerUserExists = ownerUserExists;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public String getRequestedByServiceId() {
        return requestedByServiceId;
    }

    public void setRequestedByServiceId(String requestedByServiceId) {
        this.requestedByServiceId = requestedByServiceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getChangeReference() {
        return changeReference;
    }

    public void setChangeReference(String changeReference) {
        this.changeReference = changeReference;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
