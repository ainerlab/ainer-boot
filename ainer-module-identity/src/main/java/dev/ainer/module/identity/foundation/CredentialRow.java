package dev.ainer.module.identity.foundation;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * {@link Credential} 的可变持久化行（MyBatis 普通 POJO 映射，对应表
 * {@code ainer_identity_credential}）。{@code rotatedAt} 可为 null。
 */
public class CredentialRow {

    private UUID id;
    private UUID accountId;
    private String type;
    private String credentialData;
    private String status;
    private Instant createdAt;
    private Instant rotatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCredentialData() {
        return credentialData;
    }

    public void setCredentialData(String credentialData) {
        this.credentialData = credentialData;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Nullable
    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public void setRotatedAt(@Nullable Instant rotatedAt) {
        this.rotatedAt = rotatedAt;
    }
}